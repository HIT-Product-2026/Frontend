package com.pando.app.features.home.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.network.TokenManager
import com.pando.app.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : BaseFragment<FragmentCameraBinding>(FragmentCameraBinding::inflate) {

    @Inject
    lateinit var tokenManager: TokenManager

    private val viewModel : CameraViewModel by viewModels()
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var savedPhotoFile: File? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Snackbar.make(binding.root, "Cần cấp quyền Camera!", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun initData() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun initView() {
        switchToCaptureMode()
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
            takePhoto()
        }

        binding.btnCancel.setOnClickListener {
            savedPhotoFile?.delete()
            switchToCaptureMode()
        }

        binding.btnSend.setOnClickListener {
            tokenManager.clear()

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()

            findNavController().navigate(
                R.id.startFragment,
                null,
                navOptions
            )
        }
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.viewFinder.surfaceProvider
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Khởi tạo camera thất bại", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

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
                    switchToSendMode(Uri.fromFile(photoFile))
                }
            }
        )
    }

    private fun switchToSendMode(imageUri: Uri) {
        binding.viewFinder.visibility = View.INVISIBLE
        binding.imgPreviewCaptured.visibility = View.VISIBLE

        Glide.with(this).load(imageUri).into(binding.imgPreviewCaptured)

        binding.functionsBar.visibility = View.GONE
        binding.switchModeContainer.visibility = View.GONE

        binding.sendFunctionsBar.visibility = View.VISIBLE
    }

    private fun switchToCaptureMode() {
        binding.viewFinder.visibility = View.VISIBLE
        binding.imgPreviewCaptured.visibility = View.GONE

        binding.functionsBar.visibility = View.VISIBLE
        binding.switchModeContainer.visibility = View.VISIBLE

        binding.sendFunctionsBar.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}