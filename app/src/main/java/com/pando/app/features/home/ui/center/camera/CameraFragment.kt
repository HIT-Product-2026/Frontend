package com.pando.app.features.home.ui.center.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.util.Rational
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.showComingSoon
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentCameraBinding
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.ui.center.CenterFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : BaseFragment<FragmentCameraBinding>(FragmentCameraBinding::inflate) {
    private val viewModel: CameraViewModel by viewModels()

    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var videoPreviewPlayer: ExoPlayer? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    private var orientationEventListener: OrientationEventListener? = null
    private var rotation = Surface.ROTATION_0

    private var cameraProvider: ProcessCameraProvider? = null

    private var captureMode = CaptureMode.PHOTO

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var savedMediaFile: File? = null

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            startVideoRecording(enableAudio = granted)
        }

    override fun initData() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initOrientationListener()

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cameraViewMode.collect { mode ->
                    when (mode) {
                        is CameraViewMode.Capture -> {
                            switchToCaptureMode()
                        }

                        is CameraViewMode.Send -> {
                            switchToSendMode(Uri.fromFile(mode.mediaFile), mode.type)
                        }
                    }
                }
            }
        }
    }

    override fun initView() {
    }

    override fun initActionView() {
        binding.btnGallery.setOnClickListener {
            requireContext().showComingSoon()
        }

        binding.btnTabVideo.setOnClickListener {
            selectCaptureMode(CaptureMode.VIDEO)
        }

        binding.btnTabCamera.setOnClickListener {
            selectCaptureMode(CaptureMode.PHOTO)
        }

        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        binding.btnCapture.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                captureLocation()

                when (captureMode) {
                    CaptureMode.PHOTO -> takePhoto()

                    CaptureMode.VIDEO -> {
                        if (activeRecording != null) {
                            // Cho phép bấm lần hai để dừng trước 10 giây
                            activeRecording?.stop()
                        } else {
                            requestAudioAndRecord()
                        }
                    }
                }
            }
        }

        binding.btnCancel.setOnClickListener {
            binding.captionET.text?.clear()
            savedMediaFile?.delete()
            currentLat = null
            currentLng = null
            viewModel.setCaptureMode()
        }

        binding.historyBtn.setOnClickListener {
            (parentFragment as? CenterFragment)?.openPostReel()
        }

        binding.btnSend.setOnClickListener {
            val caption = binding.captionET.text.toString()
                .trim()
                .takeIf { it.isNotEmpty() }

            viewModel.sendPost(caption, currentLng, currentLat)
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Idle -> {}
                            is UiState.Loading -> {
                                binding.btnSend.isEnabled = false
                            }

                            is UiState.Success -> {
                                binding.btnSend.isEnabled = true

                                val post = state.data.data
                                val newPost = PostReelItemModel(
                                    id = post.id,
                                    user = post.user,
                                    caption = post.caption,
                                    latitude = post.latitude,
                                    longitude = post.longitude,
                                    modeLocation = post.modeLocation,
                                    type = post.type,
                                    nsfw = post.nsfw,
                                    conversationId = post.conversation?.id,
                                    createdAt = post.createAt?.toLocalDateTime()
                                )

                                DataPostReelItem.data.add(newPost)

                                Toast.makeText(requireContext(), "Đã gửi!", Toast.LENGTH_SHORT)
                                    .show()
                                switchToCaptureMode()
                                viewModel.clearResult()
                                binding.captionET.text?.clear()
                            }

                            is UiState.Error -> {
                                binding.btnSend.isEnabled = true
                                Log.e("Camera", state.message)
                                Toast.makeText(
                                    requireContext(),
                                    "Lỗi: ${state.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewModel.clearResult()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
        binding.cameraContainer.visibility = View.VISIBLE
        startCamera()
    }

    override fun onPause() {
        releaseVideoPreview()
        orientationEventListener?.disable()
        binding.cameraContainer.visibility = View.GONE

        activeRecording?.stop()
        activeRecording = null

        cameraProvider?.unbindAll()
        imageCapture = null
        videoCapture = null

        super.onPause()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            if (!isAdded ||
                view == null ||
                !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return@addListener
            }
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build().also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

//            imageCapture = ImageCapture.Builder()
//                .setTargetRotation(rotation)
//                .build()

            val captureUseCase: UseCase = when (captureMode) {
                CaptureMode.PHOTO -> {
                    videoCapture = null

                    ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { imageCapture = it }
                }

                CaptureMode.VIDEO -> {
                    imageCapture = null

                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(Quality.HD))
                        .build()

                    VideoCapture.Builder(recorder)
                        .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                        .setTargetRotation(rotation)
                        .build()
                        .also {
                            videoCapture = it
                        }
                }
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                provider.unbindAll()

                val viewPort = ViewPort.Builder(
                    Rational(1, 1),
                    Surface.ROTATION_0
                ).build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(captureUseCase)
                    .setViewPort(viewPort)
                    .build()

                provider.bindToLifecycle(viewLifecycleOwner, cameraSelector, useCaseGroup)
            } catch (exc: Exception) {
                Log.e("CameraX", "Khởi tạo camera thất bại", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.targetRotation = rotation

        val photoFile = File(
            requireContext().cacheDir,
            "Pando_${System.currentTimeMillis()}.jpg"
        )

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(photoFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Chụp ảnh lỗi!", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    savedMediaFile = photoFile
                    viewModel.setSendMode(photoFile, TypePost.IMAGE)
                }
            }
        )
    }

    private fun switchToSendMode(mediaUri: Uri, type: TypePost) {
        (parentFragment as? CenterFragment)?.setCameraSendMode(true)

        binding.viewFinder.visibility = View.INVISIBLE

        when (type) {
            TypePost.IMAGE -> {
                releaseVideoPreview()

                binding.videoPreviewCaptured.visibility = View.GONE
                binding.imgPreviewCaptured.visibility = View.VISIBLE

                Glide.with(this)
                    .load(mediaUri)
                    .into(binding.imgPreviewCaptured)
            }

            TypePost.VIDEO -> {
                binding.imgPreviewCaptured.visibility = View.GONE
                binding.videoPreviewCaptured.visibility = View.VISIBLE

                playCapturedVideo(mediaUri)
            }
        }

        binding.functionsBar.visibility = View.GONE
        binding.switchModeContainer.visibility = View.GONE

        binding.sendFunctionsBar.visibility = View.VISIBLE
        binding.historyBtn.visibility = View.GONE

        binding.captionLayout.visibility = View.VISIBLE
    }

    private fun switchToCaptureMode() {
        (parentFragment as? CenterFragment)?.setCameraSendMode(false)

        releaseVideoPreview()

        binding.videoPreviewCaptured.visibility = View.GONE
        binding.imgPreviewCaptured.visibility = View.GONE
        binding.viewFinder.visibility = View.VISIBLE

        Glide.with(this).clear(binding.imgPreviewCaptured)

        binding.viewFinder.visibility = View.VISIBLE
        binding.imgPreviewCaptured.visibility = View.GONE

        binding.functionsBar.visibility = View.VISIBLE
        binding.switchModeContainer.visibility = View.VISIBLE

        binding.sendFunctionsBar.visibility = View.GONE
        binding.historyBtn.visibility = View.VISIBLE

        binding.captionLayout.visibility = View.GONE
    }

    private fun playCapturedVideo(videoUri: Uri) {
        // Giải phóng player cũ nếu có
        releaseVideoPreview()

        videoPreviewPlayer = ExoPlayer.Builder(requireContext())
            .build()
            .also { player ->
                binding.videoPreviewCaptured.player = player

                player.setMediaItem(MediaItem.fromUri(videoUri))

                // Phát lặp lại video vừa quay
                player.repeatMode = Player.REPEAT_MODE_ONE

                player.prepare()
                player.playWhenReady = true
            }
    }

    private fun releaseVideoPreview() {
        binding.videoPreviewCaptured.player = null

        videoPreviewPlayer?.release()
        videoPreviewPlayer = null
    }

    private suspend fun captureLocation() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                if (location != null) {
                    currentLat = location.latitude
                    currentLng = location.longitude
                    Log.d("LOCATION", "Đã lấy tọa độ: Lat=$currentLat, Lng=$currentLng")
                } else {
                    Log.d("LOCATION", "Không thể lấy tọa độ (Có thể do đang ở trong nhà quá kín)")
                }
            }
        } else {
            currentLat = null
            currentLng = null
        }
    }

    private fun initOrientationListener() {
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture?.targetRotation = rotation
                videoCapture?.targetRotation = rotation
            }
        }
    }

    override fun onDestroyView() {
        releaseVideoPreview()
        orientationEventListener?.disable()
        orientationEventListener = null

        activeRecording?.stop()
        activeRecording = null
        videoCapture = null

        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        cameraExecutor.shutdown()
//        viewModel.socketDisconnect()

        super.onDestroyView()
    }

    private fun selectCaptureMode(mode: CaptureMode) {
        if (captureMode == mode || activeRecording != null) return

        captureMode = mode

        val thumbTranslation = if (mode == CaptureMode.VIDEO) {
            binding.btnTabVideo.x - binding.btnTabCamera.x
        } else {
            0f
        }

        binding.viewThumb.animate()
            .translationX(thumbTranslation)
            .setDuration(200L)
            .start()

        startCamera()
    }

    private fun requestAudioAndRecord() {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            startVideoRecording(enableAudio = true)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVideoRecording(enableAudio: Boolean) {
        val videoCapture = videoCapture ?: return

        val videoFile = File(
            requireContext().cacheDir,
            "Pando_${System.currentTimeMillis()}.mp4"
        )

        val outputOptions = FileOutputOptions.Builder(videoFile)
            .setDurationLimitMillis(MAX_RECORDING_DURATION_MILLIS)
            .build()

        var pendingRecording = videoCapture.output
            .prepareRecording(requireContext(), outputOptions)

        if (enableAudio) {
            pendingRecording = pendingRecording.withAudioEnabled()
        }

        activeRecording = pendingRecording.start(
            ContextCompat.getMainExecutor(requireContext())
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    binding.btnTabCamera.isEnabled = false
                    binding.btnTabVideo.isEnabled = false
                    binding.btnSwitchCamera.isEnabled = false

                    binding.recordingBorderView.setProgress(0f)
                    binding.recordingBorderView.visibility = View.VISIBLE

                    Toast.makeText(
                        requireContext(),
                        "Đang quay...",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is VideoRecordEvent.Status -> {
                    val recordedNanos = event.recordingStats.recordedDurationNanos
                    val recordedSeconds = recordedNanos / 1_000_000_000L

                    val progress = recordedNanos.toFloat() / MAX_RECORDING_DURATION_NANOS
                    binding.recordingBorderView.setProgress(progress)

                    Log.d("CameraX", "Đã quay: ${recordedSeconds}s")
                }

                is VideoRecordEvent.Finalize -> {
                    activeRecording = null

                    binding.recordingBorderView.visibility = View.GONE
                    binding.recordingBorderView.setProgress(0f)

                    binding.btnTabCamera.isEnabled = true
                    binding.btnTabVideo.isEnabled = true
                    binding.btnSwitchCamera.isEnabled = true

                    /*
                     * Khi đạt giới hạn 10 giây, CameraX trả về
                     * ERROR_DURATION_LIMIT_REACHED nhưng file MP4 vẫn hợp lệ.
                     */
                    val isUsableVideo =
                        event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                                event.error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED

                    if (isUsableVideo && videoFile.exists() && videoFile.length() > 0L) {
                        savedMediaFile = videoFile
                        viewModel.setSendMode(videoFile, TypePost.VIDEO)
                    } else {
                        videoFile.delete()

                        Toast.makeText(
                            requireContext(),
                            "Quay video thất bại",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.e(
                            "CameraX",
                            "Video error=${event.error}",
                            event.cause
                        )
                    }
                }
            }
        }
    }

    private enum class CaptureMode {
        PHOTO,
        VIDEO
    }

    companion object {
        private const val MAX_RECORDING_DURATION_MILLIS = 10_000L
        private const val MAX_RECORDING_DURATION_NANOS =
            MAX_RECORDING_DURATION_MILLIS * 1_000_000L
    }
}
