package com.pando.app.features.home.ui.center.camera

import android.annotation.SuppressLint
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.util.Rational
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
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
import com.google.android.material.textfield.TextInputEditText
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.showComingSoon
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentCameraBinding
import com.pando.app.features.home.data.model.entity.PostReelItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.TypePost
import com.pando.app.features.home.data.store.PostFeedStore
import com.pando.app.features.home.ui.center.CenterFragment
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class CameraFragment : BaseFragment<FragmentCameraBinding>(FragmentCameraBinding::inflate) {
    private val viewModel: CameraViewModel by viewModels()

    @Inject
    lateinit var postFeedStore: PostFeedStore

    private var imageCapture: ImageCapture? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var videoPreviewPlayer: ExoPlayer? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    private var orientationEventListener: OrientationEventListener? = null
    private var rotation = Surface.ROTATION_0

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var captureMode = CaptureMode.PHOTO
    private var isFlashEnabled = false
    private var currentZoomRatio = 1f
    private var zoomStops = listOf(1f)
    private var zoomStopIndex = 0
    private var zoomGestureDetector: ScaleGestureDetector? = null
    private var zoomAnimator: ValueAnimator? = null
    private var videoIndicatorAnimator: ObjectAnimator? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isStartingRecording = false

    private var savedMediaFile: File? = null
    private var shouldKeepCaptionFocus = false
    private var isCaptionImeVisible = false
    private var captionFocusRecovery: Runnable? = null

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            startVideoRecording(enableAudio = granted)
        }

    override fun initData() {
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
        setupCaptionKeyboardFocus()
        setupOutsideFocusDismissal()
        setupZoomGesture()
        syncCaptureModeUi()
        updateFlashButton()
        updateZoomButton()
        binding.switchModeContainer.doOnLayout {
            syncCaptureModeUi()
        }
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

        binding.btnFlash.setOnClickListener {
            dismissCaptionFocusFromOutside()
            toggleFlash()
        }

        binding.btnZoom.setOnClickListener {
            dismissCaptionFocusFromOutside()
            cycleZoom()
        }

        binding.btnSwitchCamera.setOnClickListener {
            binding.btnSwitchCamera.animate()
                .rotationBy(360f)
                .setDuration(450L)
                .setInterpolator(LinearInterpolator())
                .withLayer()
                .start()

            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        binding.btnCapture.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when (captureMode) {
                    CaptureMode.PHOTO -> {
                        captureLocation()
                        takePhoto()
                    }

                    CaptureMode.VIDEO -> {
                        if (activeRecording != null) {
                            // Cho phép bấm lần hai để dừng trước 10 giây
                            activeRecording?.stop()
                        } else if (!isStartingRecording) {
                            // Không chờ lấy vị trí trước khi bắt đầu quay.
                            // Chặn các lần bấm liên tiếp trong lúc CameraX khởi tạo.
                            isStartingRecording = true
                            binding.btnCapture.isEnabled = false
                            requestAudioAndRecord()

                            currentLat = null
                            currentLng = null
                            viewLifecycleOwner.lifecycleScope.launch {
                                captureLocation()
                            }
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

        binding.captionET.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !shouldKeepCaptionFocus) {
                openCaptionComposer()
            }

            false
        }

        binding.captionET.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus &&
                shouldKeepCaptionFocus &&
                captionFocusRecovery == null
            ) {
                scheduleCaptionFocusRecovery()
            }
        }

        binding.captionET.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (shouldKeepCaptionFocus &&
                isCaptionImeVisible &&
                !binding.captionET.hasFocus() &&
                captionFocusRecovery == null
            ) {
                scheduleCaptionFocusRecovery()
            }
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
                                    imageUrl = post.urlImage,
                                    locationName = post.locationName,
                                    modeLocation = post.modeLocation,
                                    type = post.type,
                                    nsfw = post.nsfw,
                                    conversationId = post.conversation?.id,
                                    createdAt = post.createAt?.toLocalDateTime()
                                )

                                postFeedStore.addPost(newPost)

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
        syncCaptureModeUi()
        binding.switchModeContainer.doOnLayout {
            syncCaptureModeUi()
        }
        startCamera()
    }

    override fun onPause() {
        closeCaptionEditor()
        releaseVideoPreview()
        zoomAnimator?.cancel()
        orientationEventListener?.disable()
        binding.cameraContainer.visibility = View.GONE

        activeRecording?.stop()
        activeRecording = null

        cameraProvider?.unbindAll()
        camera = null
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

                camera = provider.bindToLifecycle(viewLifecycleOwner, cameraSelector, useCaseGroup)
                applyCameraControls()
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

        // Không tiếp tục thay đổi zoom khi preview ảnh/video gửi đang hiển thị.
        zoomAnimator?.cancel()
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
        binding.btnFlash.visibility = View.GONE
        binding.btnZoom.visibility = View.GONE

        binding.sendFunctionsBar.visibility = View.VISIBLE
        binding.historyBtn.visibility = View.GONE

        binding.captionLayout.visibility = View.VISIBLE
        binding.captionLayout.doOnLayout {
            freezeCaptionWidth()
        }
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
        binding.btnFlash.visibility = View.VISIBLE
        binding.btnZoom.visibility = View.VISIBLE

        // View có thể vừa được tạo lại sau khi quay sang Fragment khác;
        // đồng bộ thumb theo mode thật mà CameraX đang dùng.
        syncCaptureModeUi()

        binding.sendFunctionsBar.visibility = View.GONE
        binding.historyBtn.visibility = View.VISIBLE

        closeCaptionEditor()
        binding.captionLayout.visibility = View.GONE
        updateCaptureButton()
    }

    private fun setupCaptionKeyboardFocus() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            isCaptionImeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            if (shouldKeepCaptionFocus &&
                isCaptionImeVisible &&
                !binding.captionET.hasFocus() &&
                captionFocusRecovery == null
            ) {
                scheduleCaptionFocusRecovery()
            }

            windowInsets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupOutsideFocusDismissal() {
        val outsideViews = listOf(
            binding.root,
            binding.imgPreviewCaptured,
            binding.videoPreviewCaptured,
            binding.switchModeContainer,
            binding.btnTabCamera,
            binding.btnTabVideo,
            binding.allFunctionsBar,
            binding.btnGallery,
            binding.captureButtonContainer,
            binding.btnCapture,
            binding.btnSwitchCamera,
            binding.btnFlash,
            binding.btnZoom,
            binding.btnCancel,
            binding.btnSend,
            binding.historyBtn
        )

        outsideViews.forEach { outsideView ->
            outsideView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    dismissCaptionFocusFromOutside()
                }

                false
            }
        }
    }

    private fun setupZoomGesture() {
        val detector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    zoomAnimator?.cancel()
                    return camera != null && binding.viewFinder.visibility == View.VISIBLE
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    applyZoomRatio(
                        // ScaleGestureDetector reports the factor since the
                        // previous event, so accumulate it ourselves instead
                        // of reading CameraX's asynchronously updated state.
                        ratio = currentZoomRatio * detector.scaleFactor,
                        updatePresetIndex = true
                    )
                    return true
                }
            }
        )

        zoomGestureDetector = detector
        binding.viewFinder.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dismissCaptionFocusFromOutside()
            }

            detector.onTouchEvent(event)
            // Giữ toàn bộ pointer stream để detector nhận được
            // ACTION_POINTER_* và ACTION_UP.
            true
        }
    }

    private fun dismissCaptionFocusFromOutside() {
        val captionEditor = binding.captionET
        if (!shouldKeepCaptionFocus && !captionEditor.hasFocus()) return

        cancelCaptionFocusRecovery()
        shouldKeepCaptionFocus = false
        isCaptionImeVisible = false
        captionEditor.clearFocus()

        ViewCompat.getWindowInsetsController(binding.root)
            ?.hide(WindowInsetsCompat.Type.ime())

        val inputMethodManager = context
            ?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(captionEditor.windowToken, 0)
    }

    private fun openCaptionComposer() {
        shouldKeepCaptionFocus = true
        binding.captionET.doOnLayout {
            requestCaptionEditorFocus(showKeyboard = true)
            if (!binding.captionET.hasFocus()) {
                scheduleCaptionFocusRecovery()
            }
        }
    }

    private fun scheduleCaptionFocusRecovery() {
        val captionEditor = binding.captionET
        captionFocusRecovery?.let(captionEditor::removeCallbacks)

        var attempts = 0
        lateinit var recovery: Runnable
        recovery = Runnable {
            if (!shouldKeepCaptionFocus ||
                binding.captionLayout.visibility != View.VISIBLE ||
                captionEditor.hasFocus()
            ) {
                if (captionFocusRecovery === recovery) {
                    captionFocusRecovery = null
                }
                return@Runnable
            }

            requestCaptionEditorFocus(showKeyboard = false, restartInput = true)
            attempts++

            if (captionEditor.hasFocus() || attempts >= 3) {
                if (captionFocusRecovery === recovery) {
                    captionFocusRecovery = null
                }
            } else {
                captionEditor.postDelayed(recovery, 180L)
            }
        }

        captionFocusRecovery = recovery
        captionEditor.post(recovery)
    }

    private fun cancelCaptionFocusRecovery() {
        captionFocusRecovery?.let(binding.captionET::removeCallbacks)
        captionFocusRecovery = null
    }

    private fun requestCaptionEditorFocus(
        showKeyboard: Boolean,
        restartInput: Boolean = false
    ) {
        if (!shouldKeepCaptionFocus || binding.captionLayout.visibility != View.VISIBLE) {
            return
        }

        val captionEditor = binding.captionET
        if (!captionEditor.isAttachedToWindow) return

        captionEditor.isFocusableInTouchMode = true
        captionEditor.isCursorVisible = true
        if (!captionEditor.hasFocus()) {
            focusCaptionEditor(captionEditor)
        }
        captionEditor.setSelection(captionEditor.text?.length ?: 0)

        captionEditor.post {
            if (!shouldKeepCaptionFocus ||
                !captionEditor.isAttachedToWindow ||
                binding.captionLayout.visibility != View.VISIBLE
            ) {
                return@post
            }

            if (!captionEditor.hasFocus()) {
                focusCaptionEditor(captionEditor)
            }
            captionEditor.isCursorVisible = true
            captionEditor.setSelection(captionEditor.text?.length ?: 0)

            val inputMethodManager = context
                ?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return@post

            if (showKeyboard) {
                ViewCompat.getWindowInsetsController(binding.root)
                    ?.show(WindowInsetsCompat.Type.ime())
                inputMethodManager.showSoftInput(
                    captionEditor,
                    InputMethodManager.SHOW_IMPLICIT
                )
            } else if (restartInput && captionEditor.hasFocus()) {
                inputMethodManager.restartInput(captionEditor)
            }
        }
    }

    private fun focusCaptionEditor(captionEditor: TextInputEditText): Boolean {
        captionEditor.isFocusable = true
        captionEditor.isFocusableInTouchMode = true
        val focusedFromTouch = captionEditor.requestFocusFromTouch()
        val focused = captionEditor.requestFocus()
        return focusedFromTouch || focused || captionEditor.hasFocus()
    }

    private fun freezeCaptionWidth() {
        val captionEditor = binding.captionET
        val measuredWidth = captionEditor.width
        if (measuredWidth <= 0) return

        val layoutParams = captionEditor.layoutParams
        if (layoutParams.width != measuredWidth) {
            layoutParams.width = measuredWidth
            captionEditor.layoutParams = layoutParams
        }
    }

    private fun closeCaptionEditor() {
        cancelCaptionFocusRecovery()
        shouldKeepCaptionFocus = false
        isCaptionImeVisible = false

        val captionEditor = binding.captionET
        captionEditor.clearFocus()
        ViewCompat.getWindowInsetsController(binding.root)
            ?.hide(WindowInsetsCompat.Type.ime())
        (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(captionEditor.windowToken, 0)
    }

    private fun updateCaptureButton(
        isRecording: Boolean = activeRecording != null,
        animateVideoIndicator: Boolean = false
    ) {
        val captureDrawable = when {
            isRecording -> R.drawable.capture_recording_btn
            captureMode == CaptureMode.VIDEO -> R.drawable.capture_video_btn
            else -> R.drawable.capture_btn
        }

        binding.btnCapture.setImageResource(captureDrawable)

        if (captureMode == CaptureMode.VIDEO && !isRecording) {
            if (animateVideoIndicator) {
                animateVideoIndicator()
            } else {
                showVideoIndicator()
            }
        } else {
            hideVideoIndicator()
        }
    }

    private fun toggleFlash() {
        if (camera?.cameraInfo?.hasFlashUnit() != true) {
            updateFlashButton()
            return
        }

        isFlashEnabled = !isFlashEnabled
        applyFlashMode()
    }

    private fun applyFlashMode() {
        val currentCamera = camera
        val hasFlashUnit = currentCamera?.cameraInfo?.hasFlashUnit() == true

        binding.btnFlash.isEnabled = hasFlashUnit
        if (hasFlashUnit) {
            // Video dùng torch liên tục; ảnh dùng flashMode để chớp lúc chụp,
            // tránh làm đèn sáng liên tục trong lúc chỉ đang ngắm ảnh.
            currentCamera?.cameraControl?.enableTorch(
                isFlashEnabled && captureMode == CaptureMode.VIDEO
            )
        } else {
            isFlashEnabled = false
        }

        imageCapture?.flashMode = if (isFlashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        updateFlashButton()
    }

    private fun updateFlashButton() {
        binding.btnFlash.setImageResource(
            if (isFlashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
        binding.btnFlash.contentDescription = if (isFlashEnabled) {
            "Tắt flash"
        } else {
            "Bật flash"
        }
        binding.btnFlash.alpha = if (binding.btnFlash.isEnabled) 1f else 0.45f
    }

    private fun cycleZoom() {
        if (zoomStops.isEmpty() || camera == null) return

        zoomStopIndex = (zoomStopIndex + 1) % zoomStops.size
        animateZoomTo(zoomStops[zoomStopIndex])
    }

    private fun rebuildZoomStops() {
        val zoomState = camera?.cameraInfo?.zoomState?.value
            ?: return

        zoomStops = CameraZoomPresets.build(
            minZoomRatio = zoomState.minZoomRatio,
            maxZoomRatio = zoomState.maxZoomRatio
        )
        currentZoomRatio = currentZoomRatio.coerceIn(
            zoomState.minZoomRatio,
            zoomState.maxZoomRatio
        )
        zoomStopIndex = CameraZoomPresets.nearestIndex(zoomStops, currentZoomRatio)
    }

    private fun animateZoomTo(targetRatio: Float) {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val clampedTarget = targetRatio.coerceIn(
            zoomState.minZoomRatio,
            zoomState.maxZoomRatio
        )
        val startRatio = currentZoomRatio.coerceIn(
            zoomState.minZoomRatio,
            zoomState.maxZoomRatio
        )

        zoomAnimator?.cancel()

        if (kotlin.math.abs(startRatio - clampedTarget) < ZOOM_EPSILON) {
            applyZoomRatio(clampedTarget, updatePresetIndex = false)
            return
        }

        zoomAnimator = ValueAnimator.ofFloat(startRatio, clampedTarget).apply {
            duration = ZOOM_ANIMATION_DURATION_MILLIS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                applyZoomRatio(
                    ratio = animator.animatedValue as Float,
                    updatePresetIndex = false
                )
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (zoomAnimator !== animation) return

                    zoomAnimator = null
                    applyZoomRatio(clampedTarget, updatePresetIndex = false)
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (zoomAnimator === animation) {
                        zoomAnimator = null
                    }
                }
            })
            start()
        }
    }

    private fun applyZoomRatio(ratio: Float, updatePresetIndex: Boolean) {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val appliedRatio = ratio.coerceIn(
            zoomState.minZoomRatio,
            zoomState.maxZoomRatio
        )

        camera?.cameraControl?.setZoomRatio(appliedRatio)
        currentZoomRatio = appliedRatio
        if (updatePresetIndex) {
            zoomStopIndex = CameraZoomPresets.nearestIndex(zoomStops, appliedRatio)
        }
        updateZoomButton(appliedRatio)
    }

    private fun updateZoomButton(ratio: Float = currentZoomRatio) {
        val displayedRatio = ratio
        val label = formatZoomRatio(displayedRatio)
        binding.btnZoom.text = label
        binding.btnZoom.contentDescription = "Thu phóng $label"
    }

    private fun applyCameraControls() {
        applyFlashMode()
        rebuildZoomStops()
        applyZoomRatio(currentZoomRatio, updatePresetIndex = false)
    }

    private fun formatZoomRatio(ratio: Float): String {
        return if (ratio % 1f == 0f) {
            "${ratio.toInt()}x"
        } else {
            "${"%.1f".format(java.util.Locale.US, ratio)}x"
        }
    }

    private fun animateVideoIndicator() {
        videoIndicatorAnimator?.cancel()
        binding.videoIndicatorDot.apply {
            visibility = View.VISIBLE
            alpha = 0.55f
            scaleX = 0.75f
            scaleY = 0.75f
        }

        videoIndicatorAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.videoIndicatorDot,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.75f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.75f, 1f)
        ).apply {
            duration = 650L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun showVideoIndicator() {
        videoIndicatorAnimator?.cancel()
        videoIndicatorAnimator = null
        binding.videoIndicatorDot.visibility = View.VISIBLE
        binding.videoIndicatorDot.alpha = 1f
        binding.videoIndicatorDot.scaleX = 1f
        binding.videoIndicatorDot.scaleY = 1f
    }

    private fun hideVideoIndicator() {
        videoIndicatorAnimator?.cancel()
        videoIndicatorAnimator = null
        binding.videoIndicatorDot.visibility = View.GONE
        binding.videoIndicatorDot.alpha = 1f
        binding.videoIndicatorDot.scaleX = 1f
        binding.videoIndicatorDot.scaleY = 1f
    }

    private fun animateCaptureButtonTransition() {
        binding.captureButtonContainer.animate().cancel()
        binding.captureButtonContainer.apply {
            alpha = 0.75f
            scaleX = 0.82f
            scaleY = 0.82f
        }
        binding.captureButtonContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withLayer()
            .start()
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

    private suspend fun captureLocation() {
        val context = requireContext()
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            currentLat = null
            currentLng = null
            return
        }

        val location = try {
            withContext(Dispatchers.IO) {
                fusedLocationClient.getCurrentLocation(
                    if (fineGranted) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    },
                    null
                ).await()
            }
        } catch (securityException: SecurityException) {
            Log.w("Camera", "Không thể lấy quyền vị trí", securityException)
            null
        }

        currentLat = location?.latitude
        currentLng = location?.longitude
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
        cancelCaptionFocusRecovery()
        shouldKeepCaptionFocus = false
        isCaptionImeVisible = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, null)

        videoIndicatorAnimator?.cancel()
        videoIndicatorAnimator = null
        releaseVideoPreview()
        zoomAnimator?.cancel()
        zoomAnimator = null
        zoomGestureDetector = null
        binding.viewFinder.setOnTouchListener(null)
        orientationEventListener?.disable()
        orientationEventListener = null

        activeRecording?.stop()
        activeRecording = null
        videoCapture = null

        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
//        viewModel.socketDisconnect()

        super.onDestroyView()
    }

    private fun selectCaptureMode(mode: CaptureMode) {
        if (captureMode == mode || activeRecording != null) return

        captureMode = mode
        syncCaptureModeUi(animate = true)
        animateCaptureButtonTransition()

        startCamera()
    }

    /**
     * Giữ switch và nút chụp khớp với captureMode.
     * ViewPager có thể tạo lại view của CameraFragment trong khi instance
     * Fragment vẫn giữ mode VIDEO, khiến thumb quay về vị trí PHOTO mặc định.
     */
    private fun syncCaptureModeUi(animate: Boolean = false) {
        val targetTranslation = if (captureMode == CaptureMode.VIDEO) {
            binding.btnTabVideo.x - binding.btnTabCamera.x
        } else {
            0f
        }

        binding.viewThumb.animate().cancel()
        if (animate) {
            binding.viewThumb.animate()
                .translationX(targetTranslation)
                .setDuration(200L)
                .start()
        } else {
            binding.viewThumb.translationX = targetTranslation
        }

        updateCaptureButton(
            animateVideoIndicator = animate && captureMode == CaptureMode.VIDEO
        )
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

    @SuppressLint("MissingPermission")
    private fun startVideoRecording(enableAudio: Boolean) {
        val videoCapture = videoCapture ?: run {
            isStartingRecording = false
            binding.btnCapture.isEnabled = true
            return
        }
        val context = requireContext()

        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (enableAudio && !audioGranted) {
            isStartingRecording = false
            binding.btnCapture.isEnabled = true
            Toast.makeText(
                context,
                "Chưa được cấp quyền micro, video sẽ không có âm thanh",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val videoFile = File(
            context.cacheDir,
            "Pando_${System.currentTimeMillis()}.mp4"
        )

        val outputOptions = FileOutputOptions.Builder(videoFile)
            .setDurationLimitMillis(MAX_RECORDING_DURATION_MILLIS)
            .build()

        try {
            var pendingRecording = videoCapture.output
                .prepareRecording(context, outputOptions)

            if (enableAudio) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(
                ContextCompat.getMainExecutor(context)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isStartingRecording = false
                        binding.btnCapture.isEnabled = true
                        updateCaptureButton(isRecording = true)

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
                        val progress =
                            recordedNanos.toFloat() / MAX_RECORDING_DURATION_NANOS
                        binding.recordingBorderView.setProgress(progress)
                    }

                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        isStartingRecording = false
                        binding.btnCapture.isEnabled = true
                        updateCaptureButton()

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
                                event.error ==
                                VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED

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

            isStartingRecording = false
            binding.btnCapture.isEnabled = true
            updateCaptureButton(isRecording = true)
        } catch (securityException: SecurityException) {
            videoFile.delete()
            isStartingRecording = false
            binding.btnCapture.isEnabled = true
            Toast.makeText(
                context,
                "Không thể bật micro để quay video",
                Toast.LENGTH_SHORT
            ).show()
            Log.w("CameraX", "Không thể cấp quyền ghi âm cho CameraX", securityException)
        } catch (illegalStateException: IllegalStateException) {
            videoFile.delete()
            isStartingRecording = false
            binding.btnCapture.isEnabled = true
            updateCaptureButton()
            Toast.makeText(
                context,
                "Camera đang xử lý video trước đó, vui lòng thử lại",
                Toast.LENGTH_SHORT
            ).show()
            Log.w("CameraX", "Không thể bắt đầu recording mới", illegalStateException)
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
        private const val ZOOM_ANIMATION_DURATION_MILLIS = 220L
        private const val ZOOM_EPSILON = 0.01f
    }
}
