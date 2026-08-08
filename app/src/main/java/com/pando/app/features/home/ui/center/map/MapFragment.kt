package com.pando.app.features.home.ui.center.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.RectF
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
import com.pando.app.core.location.LocationSnapshot
import com.pando.app.core.location.LocationSnapshotStore
import com.pando.app.core.location.LocationTrackingController
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
        private const val FRIEND_MARKER_HIT_RADIUS_DP = 48f
        private const val FOLLOW_CAMERA_ANIMATION_MILLIS = 250
        private const val MAX_LOCATION_SNAPSHOT_AGE_MILLIS = 30_000L
    }

    @Inject
    lateinit var userSession: UserSession

    @Inject
    lateinit var locationSnapshotStore: LocationSnapshotStore

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
    private data class FollowTarget(val personIds: Set<String>)
    private var followTarget: FollowTarget? = null
    private var lastFollowLocation: MarkerFollowLocation? = null

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
            locationSnapshotStore = locationSnapshotStore,
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
            // The foreground service already owns the high-accuracy stream
            // while sharing is enabled. The map observes the shared snapshot
            // instead of opening a second GPS request.
            if (!LocationTrackingController.isServiceRunning()) {
                locationController.startLocationUpdates()
            }
            if (locationController.hasAnyLocationPermission()) {
                locationController.requestCurrentLocation()
            }
            locationController.registerBearingUpdates()
        }

        applyLocationSnapshot(locationSnapshotStore.fresh(MAX_LOCATION_SNAPSHOT_AGE_MILLIS))

        // Chỉ tải friendship list lần đầu; khi quay lại chỉ lấy snapshot vị trí mới.
        mapViewModel.refreshForMapResume()
        renderMapState()
    }

    override fun onPause() {
        clearFollowTarget()

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
        clearFollowTarget()
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
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        val hasNotification = if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasCamera && hasLocation && hasNotification) return

        val requestedPermissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestedPermissions += Manifest.permission.POST_NOTIFICATIONS
        }
        multiplePermissionsLauncher.launch(requestedPermissions.toTypedArray())
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
                    updateCurrentLocationPoint()
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
            val feature = queryFriendFeature(map, screenPoint)

            if (feature == null) {
                setFocusedFriendMarker(null)
                clearFollowTarget()
                return@addOnMapClickListener false
            }

            val markerType = feature.getStringProperty("markerType")

            if (markerType == "current") {
                focusCurrentMarker(map, feature)
                return@addOnMapClickListener true
            }

            if (
                feature.getNumberProperty("point_count") != null ||
                markerType == "nearbyGroupProxy"
            ) {
                zoomIntoCluster(map, feature)
                return@addOnMapClickListener true
            }

            if (markerType == "nearbyGroup") {
                focusNearbyGroupMarker(map, feature)
                return@addOnMapClickListener true
            }

            focusFriendMarker(map, feature)
            true
        }

        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                // Pan, pinch and rotate are explicit user intent. Stop only
                // the automatic camera-follow; keep the visual marker focus.
                clearFollowTarget()
            }
        }

        map.addOnCameraIdleListener {
            loadedStyle?.let { style ->
                markerRenderer.updateNearbyGroupVisibility(map, style)
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

    /**
     * Marker icons are anchored at their pointer, while the visible avatar is
     * rendered above it. Query a small screen rectangle instead of a single
     * pixel so tapping the avatar body still resolves the marker. When more
     * than one layer returns the same/nearby feature, prefer the closest
     * feature to the tap point.
     */
    private fun queryFriendFeature(
        map: MapLibreMap,
        screenPoint: PointF
    ): Feature? {
        val hitRadius = FRIEND_MARKER_HIT_RADIUS_DP * resources.displayMetrics.density
        val bounds = RectF(
            screenPoint.x - hitRadius,
            screenPoint.y - hitRadius,
            screenPoint.x + hitRadius,
            screenPoint.y + hitRadius
        )

        return map.queryRenderedFeatures(
            bounds,
            *markerRenderer.interactiveLayerIds
        ).minByOrNull { feature ->
            val point = feature.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
            val featureScreenPoint = map.projection.toScreenLocation(
                LatLng(point.latitude(), point.longitude())
            )
            val dx = featureScreenPoint.x - screenPoint.x
            val dy = featureScreenPoint.y - screenPoint.y
            dx * dx + dy * dy
        }
    }

    private fun zoomIntoCluster(map: MapLibreMap, feature: Feature) {
        setFollowTarget(
            loadedStyle?.let { style -> markerRenderer.personIdsForFeature(style, feature) }
                .orEmpty()
        )
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
        val personIds = loadedStyle?.let { style ->
            markerRenderer.personIdsForFeature(style, feature)
        }.orEmpty()

        setFollowTarget(personIds)
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

    private fun focusNearbyGroupMarker(map: MapLibreMap, feature: Feature) {
        val personIds = loadedStyle?.let { style ->
            markerRenderer.personIdsForFeature(style, feature)
        }.orEmpty()

        setFollowTarget(personIds)
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

    private fun focusCurrentMarker(map: MapLibreMap, feature: Feature) {
        val currentId = feature.getStringProperty("id")
            .takeIf(String::isNotBlank)
            ?: currentUserId?.toString()
            ?: return

        setFollowTarget(setOf(currentId))
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
            "Bạn vừa chọn ${feature.getStringProperty("name") ?: currentUserName}",
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
                            friend.copy(avatarUrl = avatars[friend.id] ?: friend.avatarUrl)
                        }
                    }.collect { friends ->
                        renderFriendsState(friends)
                    }
                }

                launch {
                    locationSnapshotStore.snapshot.collect { snapshot ->
                        if (LocationTrackingController.isServiceRunning() &&
                            ::locationController.isInitialized
                        ) {
                            locationController.stopLocationUpdates()
                        }
                        applyLocationSnapshot(snapshot?.takeIf {
                            snapshotAgeMillis(it) <= MAX_LOCATION_SNAPSHOT_AGE_MILLIS
                        })
                    }
                }

                launch {
                    mapViewModel.friends.collect { friends ->
                        // DTO mới đã có URL; chỉ fallback API cho bạn cũ chưa có URL.
                        avatarViewModel.loadAvatars(
                            friends
                                .filter { it.avatarUrl.isNullOrBlank() }
                                .map { it.id }
                        )
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

    private fun applyLocationSnapshot(snapshot: LocationSnapshot?) {
        snapshot ?: return
        if (currentLat == snapshot.latitude && currentLng == snapshot.longitude) return

        currentLat = snapshot.latitude
        currentLng = snapshot.longitude
        updateCurrentLocationPoint()
        renderFriendsState()
    }

    private fun snapshotAgeMillis(snapshot: LocationSnapshot): Long {
        return android.os.SystemClock.elapsedRealtime() - snapshot.capturedAtElapsedMillis
    }

    private fun handleCapturedLocation(location: Location, fromCache: Boolean) {
        // Cached location chỉ dùng để hiển thị/camera ngay; không ghi đè live location.
        if (fromCache && currentLat != null && currentLng != null) return

        currentLat = location.latitude
        currentLng = location.longitude
        updateCurrentLocationPoint()
        renderFriendsState()
        if (followTarget == null) {
            moveCameraToCurrentLocation(animate = false)
        }
    }

    private fun updateCurrentLocationPoint() {
        val style = loadedStyle ?: return
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        markerRenderer.renderCurrentLocation(
            style = style,
            latitude = latitude,
            longitude = longitude,
            currentUserId = currentUserId?.toString(),
            currentUserName = currentUserName
        )
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
            friend.copy(avatarUrl = avatarMap[friend.id] ?: friend.avatarUrl)
        }

        markerRenderer.renderFriends(
            style = style,
            friends = friends,
            currentUser = currentUserMapMarker()
        )
        mapLibreMap?.let { map ->
            markerRenderer.updateNearbyGroupVisibility(map, style)
        }
        updateCameraForFollowTarget()
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

    private fun setFollowTarget(personIds: Set<String>) {
        if (personIds.isEmpty()) {
            clearFollowTarget()
            return
        }

        followTarget = FollowTarget(personIds)
        lastFollowLocation = null
    }

    private fun clearFollowTarget() {
        followTarget = null
        lastFollowLocation = null
    }

    private fun updateCameraForFollowTarget() {
        val target = followTarget ?: return
        val map = mapLibreMap ?: return
        val location = markerRenderer.resolveFollowLocation(target.personIds)

        if (location == null) {
            clearFollowTarget()
            return
        }

        if (location == lastFollowLocation) return
        lastFollowLocation = location

        map.animateCamera(
            CameraUpdateFactory.newLatLng(
                LatLng(location.latitude, location.longitude)
            ),
            FOLLOW_CAMERA_ANIMATION_MILLIS
        )
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
