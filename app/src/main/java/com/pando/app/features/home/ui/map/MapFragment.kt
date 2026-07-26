package com.pando.app.features.home.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.auth0.android.jwt.DecodeException
import com.auth0.android.jwt.JWT
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.pando.app.BuildConfig
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.network.api.TokenManager
import com.pando.app.core.session.UserSession
import com.pando.app.core.state.SocketConnectionState
import com.pando.app.databinding.FragmentMapBinding
import com.pando.app.features.home.data.model.entity.CurrentUser
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : BaseFragment<FragmentMapBinding>(FragmentMapBinding::inflate) {
    companion object {
        private const val TAG = "SOCKET_CONNECTION"
    }

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var userSession: UserSession

    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val mapViewModel: MapViewModel by viewModels()

    private val styleUrl: String
        get() {
            val region = "ap-southeast-1"
            val style = "Standard"
            val apiKey = BuildConfig.AWS_LOCATION_API_KEY

            return "https://maps.geo.$region.amazonaws.com/v2/styles/$style/descriptor?key=$apiKey"
        }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    // lưu map và style sau khi tải xong
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null

    // để lấy source và layer marker
    private val currentLocationSourceId = "current-location-source"
    private val currentLocationLayerId = "current-location-layer"

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (!cameraGranted) {
            Snackbar.make(binding.root, "Cần cấp quyền Camera!", Snackbar.LENGTH_SHORT).show()
        }

        if (locationGranted) {
            captureLocation()
        } else {
            Toast.makeText(
                requireContext(), "Hãy cấp quyền Vị trí để ghim tọa độ ảnh!", Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mapView.onCreate(savedInstanceState)
    }

    override fun initData() {
        if (userSession.getCurrentUser() == null) {
            decodeToken(tokenManager.getAccessToken())
        }

        val hasCamera = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!(hasCamera && hasLocation)) {
            multiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

    }

    override fun initView() {
        mapViewModel.socketConnect()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    binding.profileIcon.loadAvatar(user?.avatar)
                }
            }
        }

        binding.mapView.getMapAsync { map ->
            mapLibreMap = map

            map.setStyle(styleUrl) { style ->
                loadedStyle = style

                // credit góc dưới bên trái
                map.uiSettings.isAttributionEnabled = true

                setVietnameseLabels(style)

                showLocationIfReady()
            }
        }
        captureLocation()
    }

    override fun initActionView() {
        binding.btnCapture.setOnClickListener {
            findNavController().navigate(R.id.action_mapFragment_to_cameraFragment)
        }

        binding.profileIcon.setOnClickListener {
            findNavController().navigate(R.id.action_mapFragment_to_settingFragment)
        }

        binding.chatBtn.setOnClickListener {
            findNavController().navigate(R.id.action_mapFragment_to_chatMenuFragment)
        }

        binding.btnCurrentLocation.setOnClickListener {
            captureLocation()
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mapViewModel.connectionState.collect { state ->
                        when (state) {
                            SocketConnectionState.Connecting -> {
                                Log.d(TAG, "Đang kết nối")
                            }

                            SocketConnectionState.Connected -> {
                                Log.d(TAG, "Đã kết nối")
                            }

                            SocketConnectionState.Disconnected -> {
                                Log.d(TAG, "Đã ngắt kết nối")
                            }

                            is SocketConnectionState.Error -> {
                                Log.e(TAG, state.message)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    override fun onDestroyView() {
        mapLibreMap = null
        loadedStyle = null
        currentLat = null
        currentLng = null

        binding.mapView.onDestroy()
        super.onDestroyView()
    }

    private fun setVietnameseLabels(style: Style) {
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            val hasTextField = layer.textField.expression != null

            if (hasTextField) {
                layer.setProperties(
                    textField(coalesce(
                            get("name:vi"),
                            get("name_vi"),
                            get("name"),
                            get("name:en")
                        )
                    )
                )
            }
        }
    }

    private fun showLocationIfReady() {
        val style = loadedStyle ?: return
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        showCurrentLocationPoint(
            style = style,
            latitude = latitude,
            longitude = longitude
        )

        mapLibreMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(latitude, longitude),
                zoom = 16.0
            ),
            800
        )
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
                    showLocationIfReady()
                } else {
                    Log.d("LOCATION", "Không thể lấy tọa độ (Có thể do đang ở trong nhà quá kín)")
                }
            }
        } else {
            currentLat = null
            currentLng = null
        }
    }

    private fun showCurrentLocationPoint(
        style: Style,
        latitude: Double,
        longitude: Double
    ) {
        val feature = Feature.fromGeometry(
            Point.fromLngLat(longitude, latitude)
        )

        val source = style.getSourceAs<GeoJsonSource>(currentLocationSourceId)

        if (source == null) {
            style.addSource(GeoJsonSource(currentLocationSourceId, feature))
        } else {
            source.setGeoJson(feature)
        }

        if (style.getLayer(currentLocationLayerId) == null) {
            style.addLayer(
                CircleLayer(
                    currentLocationLayerId,
                    currentLocationSourceId
                ).withProperties(
                    circleRadius(8f),
                    circleColor("#1976D2"),
                    circleStrokeWidth(3f),
                    circleStrokeColor("#FFFFFF")
                )
            )
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

        } catch (e: DecodeException) {
            Log.e("JWT_DECODE", "Token không hợp lệ hoặc bị lỗi cấu trúc: ${e.message}")
        }
    }
}