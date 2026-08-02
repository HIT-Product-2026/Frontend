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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.toLocalDateTime
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentCameraBinding
import com.pando.app.features.home.data.model.entity.DataPostReelItem
import com.pando.app.features.home.data.model.entity.PostReelItemModel
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
    private var savedPhotoFile: File? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    private var orientationEventListener: OrientationEventListener? = null
    private var rotation = Surface.ROTATION_0

    private var cameraProvider: ProcessCameraProvider? = null

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
                            switchToSendMode(Uri.fromFile(mode.photoFile))
                        }
                    }
                }
            }
        }
    }

    override fun initView() {
    }

    override fun initActionView() {
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
                takePhoto()
            }
        }

        binding.btnCancel.setOnClickListener {
            binding.captionET.text?.clear()
            savedPhotoFile?.delete()
            currentLat = null
            currentLng = null
            switchToCaptureMode()
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
        orientationEventListener?.disable()
        binding.cameraContainer.visibility = View.GONE
        cameraProvider?.unbindAll()
        imageCapture = null
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

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .build()

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
                    .addUseCase(imageCapture!!)
                    .setViewPort(viewPort)
                    .build()

                provider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, useCaseGroup
                )
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
                    savedPhotoFile = photoFile
                    viewModel.setSendMode(photoFile)
                }
            }
        )
    }

    private fun switchToSendMode(imageUri: Uri) {
        binding.viewFinder.visibility = View.INVISIBLE
        binding.imgPreviewCaptured.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUri)
            .into(binding.imgPreviewCaptured)

        binding.functionsBar.visibility = View.GONE
        binding.switchModeContainer.visibility = View.GONE

        binding.sendFunctionsBar.visibility = View.VISIBLE
        binding.historyBtn.visibility = View.GONE

        binding.captionLayout.visibility = View.VISIBLE
    }

    private fun switchToCaptureMode() {
        binding.viewFinder.visibility = View.VISIBLE
        binding.imgPreviewCaptured.visibility = View.GONE

        binding.functionsBar.visibility = View.VISIBLE
        binding.switchModeContainer.visibility = View.VISIBLE

        binding.sendFunctionsBar.visibility = View.GONE
        binding.historyBtn.visibility = View.VISIBLE

        binding.captionLayout.visibility = View.GONE
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
            }
        }
    }

    override fun onDestroyView() {
        orientationEventListener?.disable()
        orientationEventListener = null

        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
        cameraExecutor.shutdown()
//        viewModel.socketDisconnect()

        super.onDestroyView()
    }
}
