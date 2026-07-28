package com.pando.app.features.home.ui.center.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.auth0.android.jwt.DecodeException
import com.auth0.android.jwt.JWT
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
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
import com.pando.app.core.state.UiState
import com.pando.app.databinding.FragmentMapBinding
import com.pando.app.features.home.data.model.entity.CurrentUser
import com.pando.app.features.home.data.model.entity.DataFriendItem
import com.pando.app.features.home.data.model.entity.FriendItemModel
import com.pando.app.features.home.data.model.entity.enumEntity.UserMode
import com.pando.app.features.home.ui.center.CenterFragment
import com.pando.app.features.shared.AvatarViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
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

    private var avatarMap: Map<UUID, String> = emptyMap()
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
    private lateinit var locationCallback: LocationCallback
    private var currentLat: Double? = null
    private var currentLng: Double? = null

    // lưu map và style sau khi tải xong
    private var mapLibreMap: MapLibreMap? = null
    private var loadedStyle: Style? = null

    // để lấy source và layer marker
    private val currentLocationSourceId = "current-location-source"
    private val currentLocationLayerId = "current-location-layer"

    private val friendLocationSourceId = "friend-location-source"
    private val friendLocationLayerId = "friend-location-layer"

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

    private val locationRequest = LocationRequest
        .Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateIntervalMillis(2_000L)
        .build()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun initData() {
        if (userSession.getCurrentUser() == null) {
            decodeToken(tokenManager.getAccessToken())
        }

        mapViewModel.getFriendList()

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

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(
                locationResult: LocationResult
            ) {
                val location = locationResult.lastLocation ?: return

                currentLat = location.latitude
                currentLng = location.longitude

                Log.d("LOCATION_UPDATE", "Lat=$currentLat, Lng=$currentLng")

                updateCurrentLocationPoint()
                mapViewModel.sendLocation(currentLng, currentLat)
            }
        }
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

            map.addOnMapClickListener { latLng ->

                val screenPoint =
                    map.projection.toScreenLocation(latLng)

                val features = map.queryRenderedFeatures(
                    screenPoint,
                    friendLocationLayerId
                )

                val feature = features.firstOrNull()

                if (feature != null) {
                    val friendId =
                        feature.getStringProperty("id")

                    val friendName =
                        feature.getStringProperty("name")

                    Toast.makeText(
                        requireContext(),
                        "Bạn vừa chọn $friendName",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.d(
                        "FRIEND_MARKER",
                        "id=$friendId, name=$friendName"
                    )

                    true
                } else {
                    false
                }
            }

            map.setStyle(styleUrl) { style ->
                loadedStyle = style

                // credit góc dưới bên trái
                map.uiSettings.isAttributionEnabled = true

                setVietnameseLabels(style)

                updateCurrentLocationPoint()

                val defaultAvatarDrawable = AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.ic_default_avatar
                )

                val defaultAvatarBitmap = defaultAvatarDrawable?.toBitmap(
                    width = 96,
                    height = 96,
                    config = Bitmap.Config.ARGB_8888
                )

                if (defaultAvatarBitmap != null) {
                    style.addImage(
                        "friend-avatar-default",
                        createAvatarMarkerBitmap(defaultAvatarBitmap)
                    )
                } else {
                    Log.e("MAP_MARKER", "Không thể chuyển avatar mặc định thành Bitmap")
                }

                showFriendLocations(
                    style = style,
                    friends = DataFriendItem.data.toList()
                )
            }
        }
        captureLocation()
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
            if (currentLat == null && currentLng == null) {
                captureLocation()
            } else {
                moveCameraToCurrentLocation()
            }
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        mapViewModel.connectionState,
                        mapViewModel.friendState,
                        avatarViewModel.avatars
                    ) { connectionState, friendState, avatarState ->
                        Triple(connectionState, friendState, avatarState)
                    }.collect { (connectionState, friendState, avatarState) ->
                        when (connectionState) {
                            SocketConnectionState.Connecting -> {
                                Log.d(TAG, "Đang kết nối")
                            }

                            SocketConnectionState.Connected -> {
                                Log.d(TAG, "Đã kết nối")
                                when (friendState) {
                                    is UiState.Loading -> {}
                                    is UiState.Success -> {
                                        val avatarMap = avatarState
                                        val friendList = DataFriendItem.data.toList()

                                        val updatedFriendList = friendList.map { item ->
                                            item.copy(avatarUrl = if (avatarMap.containsKey(item.id)) avatarMap[item.id] else null)
                                        }

                                        DataFriendItem.data.apply {
                                            clear()
                                            addAll(updatedFriendList)
                                        }

                                        mapViewModel.subscribeLocationTopic(updatedFriendList)
                                    }

                                    is UiState.Error -> {}
                                    is UiState.Idle -> {}
                                }
                            }

                            SocketConnectionState.Disconnected -> {
                                mapViewModel.unsubscribeAllLocationTopic()
                                mapViewModel.socketDisconnect()
                                Log.d(TAG, "Đã ngắt kết nối")
                            }

                            is SocketConnectionState.Error -> {
                                mapViewModel.unsubscribeAllLocationTopic()
                                mapViewModel.socketDisconnect()
                                Log.e(TAG, connectionState.message)
                            }
                        }
                    }
                }
                launch {
                    avatarViewModel.avatars.collect { avatars ->
                        avatarMap = avatars
                    }
                }
                launch {
                    mapViewModel.friends.collect { friends ->
                        avatarViewModel.loadAvatars(
                            friends.map { it.id }
                        )

                        loadedStyle?.let { style ->
                            showFriendLocations(style, friends)
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

        if (::locationCallback.isInitialized) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
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
        mapLibreMap = null
        loadedStyle = null
        currentLat = null
        currentLng = null

        binding.mapView.onDestroy()
        super.onDestroyView()
    }

    private fun setVietnameseLabels(style: Style) {
        style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
            if (layer.textField.expression == null) return@forEach

            val originalName = coalesce(
                get("name:vi"),
                get("name_vi"),
                get("name"),
                get("name:en")
            )

            val displayName = match(
                originalName,
                originalName,
                stop(
                    "Paracel Islands",
                    literal("Quần đảo Hoàng Sa")
                ),
                stop(
                    "Paracel Is.",
                    literal("Quần đảo Hoàng Sa")
                ),
                stop(
                    "Spratly Islands",
                    literal("Quần đảo Trường Sa")
                ),
                stop(
                    "Spratly Is.",
                    literal("Quần đảo Trường Sa")
                )
            )

            layer.setProperties(
                textField(displayName)
            )
        }
    }

    private fun updateCurrentLocationPoint() {
        val style = loadedStyle ?: return
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        showCurrentLocationPoint(
            style = style,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun moveCameraToCurrentLocation() {
        val latitude = currentLat ?: return
        val longitude = currentLng ?: return

        mapLibreMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom = 16.0),
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
                    updateCurrentLocationPoint()
                    moveCameraToCurrentLocation()
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
                CircleLayer(currentLocationLayerId, currentLocationSourceId).withProperties(
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

    private fun startLocationUpdates() {
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun showFriendLocations(
        style: Style,
        friends: List<FriendItemModel>
    ) {
        val features = friends.mapNotNull { friend ->
            val longitude = friend.longitude
            val latitude = friend.latitude

            if (longitude == null || latitude == null) return@mapNotNull null

            Feature.fromGeometry(
                Point.fromLngLat(
                    longitude,
                    latitude
                )
            ).apply {
                addStringProperty("id", friend.id.toString())
                addStringProperty("name", friend.name)
                addStringProperty(
                    "iconId",
                    "friend-avatar-default"
                )
            }
        }

        val featureCollection = FeatureCollection.fromFeatures(features)

        val existingSource = style.getSourceAs<GeoJsonSource>(friendLocationSourceId)

        if (existingSource == null) {
            style.addSource(GeoJsonSource(friendLocationSourceId, featureCollection))
        } else {
            existingSource.setGeoJson(featureCollection)
        }

        if (style.getLayer(friendLocationLayerId) == null) {
            style.addLayer(
                SymbolLayer(friendLocationLayerId, friendLocationSourceId)
                    .withProperties(
                        iconImage(get("iconId")),
                        iconSize(0.7f),
                        iconAnchor(ICON_ANCHOR_BOTTOM),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
            )
        }

        loadFriendAvatarImages(style, friends)
    }

    private fun createAvatarMarkerBitmap(
        avatarBitmap: Bitmap
    ): Bitmap {
        val size = 120
        val circleRadius = 46f
        val circleCenterX = size / 2f
        val circleCenterY = 50f

        val result = createBitmap(size, size)

        val canvas = Canvas(result)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = "#ED9B15".toColorInt()
            style = Paint.Style.FILL
        }

        // Phần đuôi marker.
        val markerPath = android.graphics.Path().apply {
            moveTo(circleCenterX - 18f, 82f)
            lineTo(circleCenterX + 18f, 82f)
            lineTo(circleCenterX, 112f)
            close()
        }

        canvas.drawPath(markerPath, markerPaint)

        // Viền ngoài.
        canvas.drawCircle(
            circleCenterX,
            circleCenterY,
            circleRadius + 6f,
            markerPaint
        )

        canvas.drawCircle(
            circleCenterX,
            circleCenterY,
            circleRadius,
            borderPaint
        )

        val scaledAvatar =
            avatarBitmap.scale((circleRadius * 2).toInt(), (circleRadius * 2).toInt())

        val avatarShader = android.graphics.BitmapShader(
            scaledAvatar,
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP
        )

        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = avatarShader
        }

        canvas.drawCircle(
            circleCenterX,
            circleCenterY,
            circleRadius - 4f,
            avatarPaint
        )

        return result
    }

    private fun loadFriendAvatarImages(
        style: Style,
        friends: List<FriendItemModel>
    ) {
        friends.forEach { friend ->
            val iconId = "friend-avatar-${friend.id}"

            if (style.getImage(iconId) != null) {
                return@forEach
            }

            Glide.with(this)
                .asBitmap()
                .load(friend.avatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .into(
                    object : CustomTarget<Bitmap>() {

                        override fun onResourceReady(
                            resource: Bitmap,
                            transition: Transition<in Bitmap>?
                        ) {
                            if (!isAdded || view == null) return

                            val currentStyle = loadedStyle ?: return

                            if (currentStyle !== style) return

                            val markerBitmap =
                                createAvatarMarkerBitmap(resource)

                            if (currentStyle.getImage(iconId) == null) {
                                currentStyle.addImage(
                                    iconId,
                                    markerBitmap
                                )
                            }
                        }

                        override fun onLoadCleared(
                            placeholder: android.graphics.drawable.Drawable?
                        ) = Unit
                    }
                )
        }
    }
}