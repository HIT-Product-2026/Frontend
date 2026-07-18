package com.pando.app.features.home.ui.camera

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
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.auth0.android.jwt.Claim
import com.auth0.android.jwt.DecodeException
import com.auth0.android.jwt.JWT
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.network.TokenManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.ui.UiState
import com.pando.app.databinding.FragmentCameraBinding
import com.pando.app.features.home.data.model.entity.CurrentUser
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import jakarta.inject.Inject
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : BaseFragment<FragmentCameraBinding>(FragmentCameraBinding::inflate) {
    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var userSession: UserSession
    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val viewModel: CameraViewModel by viewModels()
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var savedPhotoFile: File? = null

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var orientationEventListener: OrientationEventListener? = null
    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted) {
            startCamera()
        } else {
            Snackbar.make(binding.root, "Cần cấp quyền Camera!", Snackbar.LENGTH_SHORT).show()
        }

        if (!locationGranted) {
            Toast.makeText(
                requireContext(),
                "Hãy cấp quyền Vị trí để ghim tọa độ ảnh!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun initData() {
        if (userSession.getCurrentUser() == null) {
            decodeToken(tokenManager.getAccessToken())
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        initOrientationListener()

        val hasCamera = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED


        if (hasCamera && hasLocation) {
            startCamera()
        } else {
            multiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }

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
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                }
            }
        }
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
            captureLocation()
            takePhoto()
        }

        binding.chatBtn.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_chatMenuFragment)
        }

        binding.btnCancel.setOnClickListener {
            binding.captionET.text?.clear()
            savedPhotoFile?.delete()
            currentLat = null
            currentLng = null
            switchToCaptureMode()
        }

        binding.friendBtn.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_friendFragment)
        }

        binding.btnSend.setOnClickListener {
            val caption = binding.captionET.text.toString()

            viewModel.sendPost(caption, currentLng, currentLat)
        }

        binding.profileIcon.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_settingFragment)
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Idle -> {}
                        is UiState.Loading -> {
                            binding.btnSend.isEnabled = false
                        }

                        is UiState.Success -> {
                            binding.btnSend.isEnabled = true
                            Toast.makeText(requireContext(), "Đã gửi!", Toast.LENGTH_SHORT).show()
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

    override fun onStart() {
        super.onStart()
        orientationEventListener?.enable()
    }

    override fun onStop() {
        super.onStop()
        orientationEventListener?.disable()
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

                val viewPort = ViewPort.Builder(
                    Rational(1, 1),
                    binding.viewFinder.display.rotation
                ).build()

                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture!!)
                    .setViewPort(viewPort)
                    .build()

                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, useCaseGroup
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

    private fun captureLocation() {
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

                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageCapture?.targetRotation = rotation
            }
        }
    }

    fun decodeToken(token: String?) {
        if (token.isNullOrEmpty()) {
            Log.e("JWT_DECODE", "Token null")
            return
        }

        try {
            val jwt = JWT(token)

            val isTokenExpired = jwt.isExpired(10)
            if (isTokenExpired) {
                Log.e("JWT_DECODE", "Token đã hết hạn sử dụng. Vui lòng đăng nhập lại")
                return
            }

            val id = jwt.getClaim("id").asString()
            val userName = jwt.getClaim("username").asString()
            val displayName = jwt.getClaim("displayName").asString()
            val userMode = jwt.getClaim("mode").asString()

            val uuid = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                Log.e("JWT_DECODE", "ID từ Token không đúng định dạng UUID: $id")
                return
            }

            val mode: UserMode? = try {
                if (userMode != null) {
                    UserMode.valueOf(userMode.uppercase())
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                Log.e("JWT_DECODE", "mode từ Token không đúng định dạng: $userMode")
                null
            }

            val currentAvatar = userSession.getCurrentUser()
                ?.takeIf { it.id == uuid }
                ?.avatar

            userSession.setCurrentUser(
                CurrentUser(
                    id = uuid,
                    username = userName,
                    displayName = displayName,
                    mode = mode,
                    avatar = currentAvatar
                )
            )

            if (currentAvatar == null) {
                avatarViewModel.loadAvatar(uuid)
            }
            Log.d("JWT_DECODE", "Cập nhật User thành công")

        } catch (e : DecodeException) {
            Log.e("JWT_DECODE", "Token không hợp lệ hoặc bị lỗi cấu trúc: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}