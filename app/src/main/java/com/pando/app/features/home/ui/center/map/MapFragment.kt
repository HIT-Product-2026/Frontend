package com.pando.app.features.home.ui.center.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.pando.app.BuildConfig
import com.pando.app.R
import com.pando.app.core.base.BaseFragment
import com.pando.app.core.extensions.loadAvatar
import com.pando.app.core.location.LocationNavigationViewModel
import com.pando.app.core.session.UserSession
import com.pando.app.databinding.FragmentMapBinding
import com.pando.app.features.home.ui.center.CenterFragment
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : BaseFragment<FragmentMapBinding>(FragmentMapBinding::inflate) {
    companion object {
        private const val FRIEND_FOCUS_ZOOM = 16.0
    }

    @Inject
    lateinit var userSession: UserSession

    private val avatarViewModel: AvatarViewModel by activityViewModels()
    private val locationNavigationViewModel: LocationNavigationViewModel by activityViewModels()

    // MapViewModel giữ snapshot bạn bè và socket subscription khi Fragment bị recreate.
    private val mapViewModel: MapViewModel by activityViewModels()
    private val markerRenderer = MapMarkerRenderer(this)

    private val styleUrl: String
        get() {
            val region = "ap-southeast-1"
            val style = "Standard"
            val apiKey = BuildConfig.AWS_LOCATION_API_KEY

            return "https://maps.geo.$region.amazonaws.com/v2/styles/$style/descriptor?key=$apiKey"
        }

    private lateinit var locationController: MapLocationController
    private var currentLat: Double? = null
    private var currentLng: Double? = null
    private var avatarMap: Map<UUID, String> = emptyMap()
    private var currentUserId: UUID? = null
    private var currentUserName: String = "Bạn"
    private var currentUserAvatar: String? = null

    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null
    private var focusedFriendZoom: Double? = null
    private var isAnimatingToFriend = false

    private val multiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationGranted =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            } else {
                true
            }

        if (!cameraGranted) {
            Snackbar.make(binding.root, "Cần cấp quyền Camera!", Snackbar.LENGTH_SHORT).show()
        }

        if (!(fineGranted || coarseGranted)) {
            Toast.makeText(
                requireContext(),
                "Cần quyền vị trí để sử dụng bản đồ",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (!notificationGranted) {
            Toast.makeText(
                requireContext(),
                "Cần quyền thông báo để chạy chia sẻ vị trí",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun initData() {
        requestMissingPermissions()

        locationController = MapLocationController(
            context = requireContext(),
            onLocationUpdate = ::handleLocationUpdate,
            onCapturedLocation = ::handleCapturedLocation,
            onPermissionDenied = {
                currentLat = null
                currentLng = null
            },
            onBearingChanged = { bearing ->
                markerRenderer.updateBearing(loadedStyle, bearing)
            }
        )
    }

    override fun initView() {
        observeCurrentUser()
        setupMap()
    }

    override fun initActionView() {
        binding.btnCapture.setOnClickListener {
            (parentFragment as? CenterFragment)?.openCamera()
        }

        binding.profileIcon.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_settingFragment)
        }

        binding.chatBtn.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_chatMenuFragment)
        }

        binding.btnCurrentLocation.setOnClickListener {
            if (currentLat == null || currentLng == null) {
                captureLocation()
            } else {
                moveCameraToCurrentLocation()
            }
        }

        binding.friendBtn.setOnClickListener {
            findNavController().navigate(R.id.action_centerFragment_to_friendFragment)
        }

        observeMapData()
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        if (::locationController.isInitialized) {
            locationController.startLocationUpdates()
            if (locationController.hasAnyLocationPermission()) {
                locationController.requestCurrentLocation()
            }
            locationController.registerBearingUpdates()
        }

        // Chỉ tải friendship list lần đầu; khi quay lại chỉ lấy snapshot vị trí mới.
        mapViewModel.refreshForMapResume()
        renderMapState()
    }

    override fun onPause() {
        if (::locationController.isInitialized) {
            locationController.stopLocationUpdates()
            locationController.unregisterBearingUpdates()
        }

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
        markerRenderer.clearStyle(loadedStyle)
        mapLibreMap = null
        loadedStyle = null

        binding.mapView.onDestroy()
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (::locationController.isInitialized) {
            locationController.release()
        }
        super.onDestroy()
    }

    private fun requestMissingPermissions() {
        val hasCamera = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotification = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasLocation && hasNotification) return

        multiplePermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun observeCurrentUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                userSession.currentUser.collect { user ->
                    currentUserId = user?.id
                    currentUserName = user?.displayName
                        ?.takeIf(String::isNotBlank)
                        ?: user?.username
                            ?.takeIf(String::isNotBlank)
                        ?: "Bạn"
                    currentUserAvatar = user?.avatar?.toString()
                    binding.profileIcon.loadAvatar(user?.avatar)
                    renderFriendsState()
                }
            }
        }
    }

    private fun setupMap() {
        binding.mapView.getMapAsync { map ->
            mapLibreMap = map
            setupMapInteractions(map)

            map.setStyle(styleUrl) { style ->
                loadedStyle = style
                map.uiSettings.isAttributionEnabled = true

                markerRenderer.setVietnameseLabels(style)
                markerRenderer.addDirectionIcon(style)
                renderMapState()
                focusCurrentLocationFromNotificationIfReady()
            }
        }
    }

    private fun setupMapInteractions(map: MapLibreMap) {
        map.addOnMapClickListener { latLng ->
            val screenPoint = map.projection.toScreenLocation(latLng)
            val feature = map.queryRenderedFeatures(
                screenPoint,
                *markerRenderer.interactiveLayerIds
            ).firstOrNull()

            if (feature == null) {
                setFocusedFriendMarker(null)
                return@addOnMapClickListener false
            }

            val markerType = feature.getStringProperty("markerType")

            if (
                feature.getNumberProperty("point_count") != null ||
                markerType == "doubleProxy"
            ) {
                zoomIntoCluster(map, feature)
                return@addOnMapClickListener true
            }

            if (markerType == "double") {
                focusDoubleFriendMarker(map, feature)
                return@addOnMapClickListener true
            }

            focusFriendMarker(map, feature)
            true
        }

        map.addOnCameraIdleListener {
            loadedStyle?.let { style ->
                markerRenderer.updateDoubleMarkerVisibility(map, style)
            }

            if (isAnimatingToFriend) {
                isAnimatingToFriend = false
                return@addOnCameraIdleListener
            }

            val focusZoom = focusedFriendZoom ?: return@addOnCameraIdleListener
            if (map.cameraPosition.zoom < focusZoom) {
                setFocusedFriendMarker(null)
            }
        }
    }

    private fun zoomIntoCluster(map: MapLibreMap, feature: Feature) {
        setFocusedFriendMarker(null)

        val clusterPoint = feature.geometry() as? Point ?: return
        val nextZoom = (map.cameraPosition.zoom + 2.0).coerceAtMost(FRIEND_FOCUS_ZOOM)

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(clusterPoint.latitude(), clusterPoint.longitude()),
                zoom = nextZoom
            ),
            350
        )
    }

    private fun focusFriendMarker(map: MapLibreMap, feature: Feature) {
        val friendId = feature.getStringProperty("id")
        val friendName = feature.getStringProperty("name")

        setFocusedFriendMarker(friendId)

        val friendPoint = feature.geometry() as? Point
        if (friendPoint != null) {
            focusedFriendZoom = FRIEND_FOCUS_ZOOM
            isAnimatingToFriend = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(friendPoint.latitude(), friendPoint.longitude()),
                    zoom = FRIEND_FOCUS_ZOOM
                ),
                500
            )
        }

        Toast.makeText(
            requireContext(),
            "Bạn vừa chọn $friendName",
            Toast.LENGTH_SHORT
        ).show()
        Log.d("FRIEND_MARKER", "id=$friendId, name=$friendName")
    }

    private fun focusDoubleFriendMarker(map: MapLibreMap, feature: Feature) {
        setFocusedFriendMarker(null)

        val point = feature.geometry() as? Point
        if (point != null) {
            focusedFriendZoom = FRIEND_FOCUS_ZOOM
            isAnimatingToFriend = true
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(point.latitude(), point.longitude()),
                    zoom = FRIEND_FOCUS_ZOOM
                ),
                500
            )
        }

        Toast.makeText(
            requireContext(),
            "Bạn vừa chọn ${feature.getStringProperty("name")}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun observeMapData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    locationNavigationViewModel.focusCurrentLocation.collect { shouldFocus ->
                        if (shouldFocus) focusCurrentLocationFromNotificationIfReady()
                    }
                }

                launch {
                    combine(
                        avatarViewModel.avatars,
                        mapViewModel.friends
                    ) { avatars, friends ->
                        avatarMap = avatars
                        friends.map { friend ->
                            friend.copy(avatarUrl = avatars[friend.id])
                        }
                    }.collect { friends ->
                        renderFriendsState(friends)
                    }
                }

                launch {
                    mapViewModel.friends.collect { friends ->
                        avatarViewModel.loadAvatars(friends.map { it.id })
                    }
                }
            }
        }
    }

    private fun handleLocationUpdate(location: Location) {
        val latitude = location.latitude
        val longitude = location.longitude

        if (currentLat == latitude && currentLng == longitude) return

        currentLat = latitude
        currentLng = longitude
        updateCurrentLocationPoint()
        renderFriendsState()
    }

    private fun handleCapturedLocation(location: Location, fromCache: Boolean) {
        // Cached location chỉ dùng để hiển thị/camera ngay; không ghi đè live location.
        if (fromCache && currentLat != null && currentLng != null) return

        currentLat = location.latitude
        currentLng = location.longitude
        updateCurrentLocationPoint()
        renderFriendsState()
        moveCameraToCurrentLocation(animate = false)
    }

    private fun updateCurrentLocationPoint() {
        val style = loadedStyle ?: return
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        markerRenderer.renderCurrentLocation(style, latitude, longitude)
    }

    private fun renderMapState() {
        val style = loadedStyle ?: return

        updateCurrentLocationPoint()
        renderFriendsState()

        // Khi quay lại Fragment, camera luôn cố gắng focus vị trí hiện tại.
        moveCameraToCurrentLocation(animate = false)
    }

    private fun renderFriendsState(
        synchronizedFriends: List<com.pando.app.features.home.data.model.entity.FriendItemModel>? = null
    ) {
        val style = loadedStyle ?: return
        val friends = synchronizedFriends ?: mapViewModel.friends.value.map { friend ->
            friend.copy(avatarUrl = avatarMap[friend.id])
        }

        markerRenderer.renderFriends(
            style = style,
            friends = friends,
            currentUser = currentUserMapMarker()
        )
        mapLibreMap?.let { map ->
            markerRenderer.updateDoubleMarkerVisibility(map, style)
        }
    }

    private fun currentUserMapMarker(): CurrentUserMapMarker? {
        val id = currentUserId ?: return null
        val latitude = currentLat ?: return null
        val longitude = currentLng ?: return null

        return CurrentUserMapMarker(
            id = id,
            name = currentUserName,
            avatarUrl = currentUserAvatar,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun setFocusedFriendMarker(friendId: String?) {
        if (friendId == null) {
            focusedFriendZoom = null
            isAnimatingToFriend = false
        }
        markerRenderer.setFocusedFriendMarker(loadedStyle, friendId)
    }

    private fun moveCameraToCurrentLocation(animate: Boolean = true) {
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
            LatLng(latitude, longitude),
            zoom = FRIEND_FOCUS_ZOOM
        )

        mapLibreMap?.let { map ->
            if (animate) {
                map.animateCamera(cameraUpdate, 500)
            } else {
                map.moveCamera(cameraUpdate)
            }
        }
    }

    private fun focusCurrentLocationFromNotificationIfReady() {
        if (!locationNavigationViewModel.focusCurrentLocation.value) return
        if (mapLibreMap == null || loadedStyle == null) return

        if (currentLat == null || currentLng == null) {
            captureLocation()
        } else {
            updateCurrentLocationPoint()
            moveCameraToCurrentLocation()
        }

        locationNavigationViewModel.currentLocationFocused()
    }

    private fun captureLocation() {
        if (!::locationController.isInitialized) return
        locationController.requestCurrentLocation()
    }
}
