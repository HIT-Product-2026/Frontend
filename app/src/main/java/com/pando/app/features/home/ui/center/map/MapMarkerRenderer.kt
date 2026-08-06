package com.pando.app.features.home.ui.center.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.location.Location
import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.pando.app.R
import com.pando.app.databinding.LayoutDoubleFriendMarkerBinding
import com.pando.app.databinding.LayoutMarkerBinding
import com.pando.app.features.home.data.model.entity.FriendItemModel
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gt
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.neq
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.subtract
import org.maplibre.android.style.expressions.Expression.toString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import org.maplibre.android.style.layers.Property.TEXT_ANCHOR_CENTER
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.UUID
import kotlin.math.hypot

data class CurrentUserMapMarker(
    val id: UUID,
    val name: String,
    val avatarUrl: String?,
    val latitude: Double,
    val longitude: Double
)

class MapMarkerRenderer(
    private val fragment: Fragment
) {
    companion object {
        private const val FRIEND_MARKER_SIZE = 0.75f
        private const val FOCUSED_FRIEND_MARKER_SIZE = 0.85f
        private const val DOUBLE_FRIEND_DISTANCE_METERS = 30f
        private const val FRIEND_CLUSTER_RADIUS = 30
        private const val DOUBLE_MARKER_CLUSTER_QUERY_RADIUS = 72f
        private const val FRIEND_CLUSTER_MAX_ZOOM = 15
    }

    private val currentLocationSourceId = "current-location-source"
    private val currentLocationLayerId = "current-location-layer"
    private val currentDirectionIconId = "current-location-direction-icon"
    private val currentDirectionLayerId = "current-location-direction-layer"

    private val friendLocationSourceId = "friend-location-source"
    private val friendLocationLayerId = "friend-location-layer"
    private val doubleFriendLocationSourceId = "double-friend-location-source"
    private val doubleFriendLocationLayerId = "double-friend-location-layer"
    private val friendClusterCircleLayerId = "friend-location-cluster-circle-layer"
    private val friendClusterCountLayerId = "friend-location-cluster-count-layer"

    val interactiveLayerIds = arrayOf(
        doubleFriendLocationLayerId,
        friendClusterCircleLayerId,
        friendClusterCountLayerId,
        friendLocationLayerId
    )

    private data class CachedMarkerBitmap(
        val avatarUrl: String?,
        val bitmap: Bitmap
    )

    private data class CachedDoubleMarkerBitmap(
        val avatarUrls: List<String?>,
        val bitmap: Bitmap
    )

    private data class MarkerPerson(
        val id: String,
        val name: String,
        val avatarUrl: String?,
        val latitude: Double,
        val longitude: Double,
        val isCurrentUser: Boolean
    )

    private data class LocatedFriend(
        val person: MarkerPerson,
        val latitude: Double,
        val longitude: Double
    )

    private data class FriendMarker(
        val people: List<MarkerPerson>,
        val latitude: Double,
        val longitude: Double
    ) {
        val isDouble: Boolean
            get() = people.size == 2

        val containsCurrentUser: Boolean
            get() = people.any(MarkerPerson::isCurrentUser)

        val iconId: String
            get() = if (isDouble) {
                "friend-double-avatar-${people.joinToString("-") { it.id }}"
            } else {
                "friend-avatar-${people.first().id}"
            }

        val markerId: String
            get() = people.joinToString(",") { it.id }

        val name: String
            get() = people.joinToString(" & ") { it.name }
    }

    private val markerBitmapCache = mutableMapOf<String, CachedMarkerBitmap>()
    private val doubleMarkerBitmapCache = mutableMapOf<String, CachedDoubleMarkerBitmap>()
    private val pendingDoubleMarkerLoads = mutableSetOf<String>()
    private var activeStyle: Style? = null
    private var currentBearing = 0f
    private var focusedFriendId: String? = null
    private var currentMarkerHidden = false
    private var currentLocationFeature: Feature? = null
    private var renderedSingleMarkers: List<FriendMarker> = emptyList()
    private var renderedDoubleMarkers: List<FriendMarker> = emptyList()
    private var visibleDoubleMarkerIds: Set<String>? = null

    fun addDirectionIcon(style: Style) {
        if (style.getImage(currentDirectionIconId) != null) return

        val drawable = AppCompatResources.getDrawable(
            fragment.requireContext(),
            R.drawable.ic_location_direction
        ) ?: return

        style.addImage(
            currentDirectionIconId,
            drawable.toBitmap(
                width = 300,
                height = 300,
                config = Bitmap.Config.ARGB_8888
            )
        )
    }

    fun setVietnameseLabels(style: Style) {
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
                stop("Paracel Islands", literal("Quần đảo Hoàng Sa")),
                stop("Paracel Is.", literal("Quần đảo Hoàng Sa")),
                stop("Spratly Islands", literal("Quần đảo Trường Sa")),
                stop("Spratly Is.", literal("Quần đảo Trường Sa"))
            )

            layer.setProperties(textField(displayName))
        }
    }

    fun renderCurrentLocation(
        style: Style,
        latitude: Double,
        longitude: Double
    ) {
        activeStyle = style

        currentLocationFeature = Feature.fromGeometry(
            Point.fromLngLat(longitude, latitude)
        )
        val featureCollection = if (currentMarkerHidden) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeature(currentLocationFeature!!)
        }

        val source = style.getSourceAs<GeoJsonSource>(currentLocationSourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(currentLocationSourceId, featureCollection))
        } else {
            source.setGeoJson(featureCollection)
        }

        if (style.getLayer(currentDirectionLayerId) == null) {
            style.addLayer(
                SymbolLayer(
                    currentDirectionLayerId,
                    currentLocationSourceId
                ).withProperties(
                    iconImage(currentDirectionIconId),
                    iconSize(1f),
                    iconAnchor(Property.ICON_ANCHOR_CENTER),
                    iconRotate(currentBearing),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }

        if (style.getLayer(currentLocationLayerId) == null) {
            style.addLayer(
                CircleLayer(currentLocationLayerId, currentLocationSourceId).withProperties(
                    circleRadius(8f),
                    circleColor("#6A9BFF"),
                    circleStrokeWidth(3f),
                    circleStrokeColor("#FFFFFF")
                )
            )
        }
    }

    private fun setCurrentMarkerHidden(style: Style, hidden: Boolean) {
        currentMarkerHidden = hidden

        val source = style.getSourceAs<GeoJsonSource>(currentLocationSourceId)
            ?: return
        val featureCollection = if (hidden) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            currentLocationFeature?.let(FeatureCollection::fromFeature)
                ?: FeatureCollection.fromFeatures(emptyList())
        }
        source.setGeoJson(featureCollection)
    }

    fun renderFriends(
        style: Style,
        friends: List<FriendItemModel>,
        currentUser: CurrentUserMapMarker? = null
    ) {
        activeStyle = style

        val friendMarkers = buildFriendMarkers(friends, currentUser)
        val singleMarkers = friendMarkers.filterNot(FriendMarker::isDouble)
        val singleFriendMarkers = singleMarkers.filterNot(FriendMarker::containsCurrentUser)
        val doubleMarkers = friendMarkers.filter(FriendMarker::isDouble)

        renderedSingleMarkers = singleFriendMarkers
        renderedDoubleMarkers = doubleMarkers
        visibleDoubleMarkerIds = null

        setCurrentMarkerHidden(
            style,
            doubleMarkers.any(FriendMarker::containsCurrentUser)
        )
        renderSingleFriendMarkers(style, singleFriendMarkers, doubleMarkers)
        renderDoubleFriendMarkers(style, doubleMarkers)
        loadFriendAvatarImages(style, singleFriendMarkers)
        loadDoubleFriendAvatarImages(style, doubleMarkers)
    }

    private fun renderSingleFriendMarkers(
        style: Style,
        markers: List<FriendMarker>,
        doubleMarkers: List<FriendMarker>
    ) {
        val featureCollection = FeatureCollection.fromFeatures(
            markers.map(::createFriendFeature) + doubleMarkers.flatMap(::createDoubleProxyFeatures)
        )
        val existingSource = style.getSourceAs<GeoJsonSource>(friendLocationSourceId)

        if (existingSource == null) {
            val sourceOptions = GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(FRIEND_CLUSTER_RADIUS)
                .withClusterMaxZoom(FRIEND_CLUSTER_MAX_ZOOM)

            style.addSource(
                GeoJsonSource(
                    friendLocationSourceId,
                    featureCollection,
                    sourceOptions
                )
            )
        } else {
            existingSource.setGeoJson(featureCollection)
        }

        if (style.getLayer(friendClusterCircleLayerId) == null) {
            // A double marker contributes two proxy points. Do not render a
            // standalone pair as "+1"; render a cluster only after another
            // person joins it (3+ points means a pair + someone else). The
            // standalone double marker is kept in its own source below.
            style.addLayer(
                CircleLayer(
                    friendClusterCircleLayerId,
                    friendLocationSourceId
                ).withProperties(
                    circleRadius(18f),
                    circleColor("#3F8CFF"),
                    circleStrokeWidth(3f),
                    circleStrokeColor("#FFFFFF")
                ).withFilter(
                    all(
                        has("point_count"),
                        gt(get("point_count"), literal(2))
                    )
                )
            )
        }

        if (style.getLayer(friendClusterCountLayerId) == null) {
            style.addLayer(
                SymbolLayer(
                    friendClusterCountLayerId,
                    friendLocationSourceId
                ).withProperties(
                    textField(
                        concat(
                            literal("+"),
                            toString(
                                subtract(
                                    coalesce(get("point_count"), literal(2)),
                                    literal(1)
                                )
                            )
                        )
                    ),
                    textSize(12f),
                    textColor("#FFFFFF"),
                    textAnchor(TEXT_ANCHOR_CENTER),
                    textAllowOverlap(true),
                    textIgnorePlacement(true)
                ).withFilter(
                    all(
                        has("point_count"),
                        gt(get("point_count"), literal(2))
                    )
                )
            )
        }

        style.getLayerAs<CircleLayer>(friendClusterCircleLayerId)
            ?.setMaxZoom(FRIEND_CLUSTER_MAX_ZOOM + 0.01f)
        style.getLayerAs<SymbolLayer>(friendClusterCountLayerId)
            ?.setMaxZoom(FRIEND_CLUSTER_MAX_ZOOM + 0.01f)

        if (style.getLayer(friendLocationLayerId) == null) {
            style.addLayer(
                SymbolLayer(friendLocationLayerId, friendLocationSourceId)
                    .withProperties(
                        iconImage(get("iconId")),
                        iconSize(friendMarkerSizeExpression()),
                        iconAnchor(ICON_ANCHOR_BOTTOM),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                    )
                    .withFilter(
                        all(
                            not(has("point_count")),
                            neq(get("markerType"), literal("doubleProxy"))
                        )
                    )
            )
        }
    }

    private fun renderDoubleFriendMarkers(
        style: Style,
        markers: List<FriendMarker>
    ) {
        val featureCollection = FeatureCollection.fromFeatures(
            markers.map(::createFriendFeature)
        )
        val existingSource = style.getSourceAs<GeoJsonSource>(doubleFriendLocationSourceId)

        if (existingSource == null) {
            style.addSource(
                GeoJsonSource(
                    doubleFriendLocationSourceId,
                    featureCollection
                )
            )
        } else {
            existingSource.setGeoJson(featureCollection)
        }

        if (style.getLayer(doubleFriendLocationLayerId) == null) {
            style.addLayer(
                SymbolLayer(
                    doubleFriendLocationLayerId,
                    doubleFriendLocationSourceId
                ).withProperties(
                    iconImage(get("iconId")),
                    iconSize(literal(FRIEND_MARKER_SIZE)),
                    iconAnchor(ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }

        // Visibility is updated from the current camera so an isolated pair
        // stays visible at every zoom, while a pair that touches another
        // marker is hidden and represented by the clustered proxy count.
        visibleDoubleMarkerIds = null
    }

    fun updateDoubleMarkerVisibility(
        map: MapLibreMap,
        style: Style
    ) {
        val source = style.getSourceAs<GeoJsonSource>(doubleFriendLocationSourceId)
            ?: return

        val visibleMarkers = if (map.cameraPosition.zoom > FRIEND_CLUSTER_MAX_ZOOM) {
            renderedDoubleMarkers
        } else {
            renderedDoubleMarkers.filterNot { marker ->
                markerTouchesAnotherMarker(map, marker)
            }
        }
        val visibleIds = visibleMarkers.mapTo(mutableSetOf(), FriendMarker::markerId)
        if (visibleIds == visibleDoubleMarkerIds) return

        visibleDoubleMarkerIds = visibleIds
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                visibleMarkers.map(::createFriendFeature)
            )
        )
    }

    private fun markerTouchesAnotherMarker(
        map: MapLibreMap,
        marker: FriendMarker
    ): Boolean {
        val markerPoint = map.projection.toScreenLocation(
            LatLng(marker.latitude, marker.longitude)
        )

        // Query the actual rendered cluster as well as the source positions.
        // A double marker is wider than a normal marker, so its anchor can be
        // farther from the cluster center even though the visuals touch.
        val clusterBounds = RectF(
            markerPoint.x - DOUBLE_MARKER_CLUSTER_QUERY_RADIUS,
            markerPoint.y - DOUBLE_MARKER_CLUSTER_QUERY_RADIUS,
            markerPoint.x + DOUBLE_MARKER_CLUSTER_QUERY_RADIUS,
            markerPoint.y + DOUBLE_MARKER_CLUSTER_QUERY_RADIUS
        )
        val hasNearbyCluster = map.queryRenderedFeatures(
            clusterBounds,
            friendClusterCircleLayerId,
            friendClusterCountLayerId
        ).any { feature ->
            (feature.getNumberProperty("point_count")?.toInt() ?: 0) > 2
        }
        if (hasNearbyCluster) return true

        val touchesSingleMarker = renderedSingleMarkers.any { otherMarker ->
            screenDistance(map, markerPoint, otherMarker) <= FRIEND_CLUSTER_RADIUS
        }
        if (touchesSingleMarker) return true

        return renderedDoubleMarkers.any { otherMarker ->
            otherMarker.markerId != marker.markerId &&
                screenDistance(map, markerPoint, otherMarker) <= FRIEND_CLUSTER_RADIUS
        }
    }

    private fun screenDistance(
        map: MapLibreMap,
        markerPoint: PointF,
        otherMarker: FriendMarker
    ): Float {
        val otherPoint = map.projection.toScreenLocation(
            LatLng(otherMarker.latitude, otherMarker.longitude)
        )
        return hypot(
            (markerPoint.x - otherPoint.x).toDouble(),
            (markerPoint.y - otherPoint.y).toDouble()
        ).toFloat()
    }

    private fun createFriendFeature(
        marker: FriendMarker,
        markerType: String = if (marker.isDouble) "double" else "single",
        id: String = marker.markerId
    ): Feature {
        return Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude)
        ).apply {
            addStringProperty("id", id)
            addStringProperty("name", marker.name)
            addStringProperty("iconId", marker.iconId)
            addStringProperty("markerType", markerType)
        }
    }

    private fun createDoubleProxyFeatures(marker: FriendMarker): List<Feature> {
        return marker.people.mapIndexed { index, _ ->
            createFriendFeature(
                marker = marker,
                markerType = "doubleProxy",
                id = "${marker.markerId}:doubleProxy:$index"
            )
        }
    }

    /**
     * Groups only connected proximity sets of exactly two people (the current
     * user can be one of them). A set of three or more remains as individual
     * features so MapLibre can still cluster it normally at lower zoom levels.
     */
    private fun buildFriendMarkers(
        friends: List<FriendItemModel>,
        currentUser: CurrentUserMapMarker?
    ): List<FriendMarker> {
        val locatedFriends = friends
            .filter { friend -> friend.id.toString() != currentUser?.id?.toString() }
            .mapNotNull { friend ->
                val latitude = friend.latitude ?: return@mapNotNull null
                val longitude = friend.longitude ?: return@mapNotNull null
                LocatedFriend(
                    person = MarkerPerson(
                        id = friend.id.toString(),
                        name = friend.name,
                        avatarUrl = friend.avatarUrl,
                        latitude = latitude,
                        longitude = longitude,
                        isCurrentUser = false
                    ),
                    latitude = latitude,
                    longitude = longitude
                )
            }
            .toMutableList()

        if (currentUser != null) {
            locatedFriends += LocatedFriend(
                person = MarkerPerson(
                    id = currentUser.id.toString(),
                    name = currentUser.name,
                    avatarUrl = currentUser.avatarUrl,
                    latitude = currentUser.latitude,
                    longitude = currentUser.longitude,
                    isCurrentUser = true
                ),
                latitude = currentUser.latitude,
                longitude = currentUser.longitude
            )
        }

        val visited = BooleanArray(locatedFriends.size)
        val markers = mutableListOf<FriendMarker>()

        locatedFriends.indices.forEach { startIndex ->
            if (visited[startIndex]) return@forEach

            val component = mutableListOf<Int>()
            val pending = ArrayDeque<Int>().apply { add(startIndex) }
            visited[startIndex] = true

            while (pending.isNotEmpty()) {
                val currentIndex = pending.removeFirst()
                component += currentIndex

                locatedFriends.indices.forEach { candidateIndex ->
                    if (visited[candidateIndex]) return@forEach

                    val distance = distanceMeters(
                        locatedFriends[currentIndex],
                        locatedFriends[candidateIndex]
                    )
                    if (distance <= DOUBLE_FRIEND_DISTANCE_METERS) {
                        visited[candidateIndex] = true
                        pending.add(candidateIndex)
                    }
                }
            }

            if (component.size == 2) {
                val first = locatedFriends[component[0]]
                val second = locatedFriends[component[1]]
                markers += FriendMarker(
                    people = listOf(first.person, second.person),
                    latitude = (first.latitude + second.latitude) / 2.0,
                    longitude = (first.longitude + second.longitude) / 2.0
                )
            } else {
                component.forEach { index ->
                    val locatedFriend = locatedFriends[index]
                    markers += FriendMarker(
                        people = listOf(locatedFriend.person),
                        latitude = locatedFriend.latitude,
                        longitude = locatedFriend.longitude
                    )
                }
            }
        }

        return markers
    }

    private fun distanceMeters(first: LocatedFriend, second: LocatedFriend): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            first.latitude,
            first.longitude,
            second.latitude,
            second.longitude,
            result
        )
        return result[0]
    }

    private fun loadDoubleFriendAvatarImages(
        style: Style,
        markers: List<FriendMarker>
    ) {
        markers.filter(FriendMarker::isDouble).forEach { marker ->
            val iconId = marker.iconId
            val avatarUrls = marker.people.map(MarkerPerson::avatarUrl)
            val cachedMarker = doubleMarkerBitmapCache[iconId]

            if (cachedMarker?.avatarUrls == avatarUrls) {
                if (style.getImage(iconId) == null) {
                    style.addImage(iconId, cachedMarker.bitmap)
                }
                return@forEach
            }

            if (style.getImage(iconId) == null) {
                val defaultAvatar = defaultAvatarBitmap()
                style.addImage(
                    iconId,
                    createDoubleFriendMarkerBitmap(defaultAvatar, defaultAvatar)
                )
            } else if (cachedMarker != null) {
                style.removeImage(iconId)
            }

            val loadKey = "$iconId|${avatarUrls.joinToString("|")}"
            if (!pendingDoubleMarkerLoads.add(loadKey)) return@forEach

            val loadedBitmaps = arrayOfNulls<Bitmap>(2)
            marker.people.forEachIndexed { index, person ->
                loadFriendAvatarBitmap(
                    person = person,
                    onReady = { bitmap ->
                        loadedBitmaps[index] = bitmap
                        if (loadedBitmaps.any { it == null }) return@loadFriendAvatarBitmap

                        pendingDoubleMarkerLoads.remove(loadKey)
                        val currentStyle = activeStyle ?: return@loadFriendAvatarBitmap
                        if (currentStyle !== style) return@loadFriendAvatarBitmap

                        val markerBitmap = createDoubleFriendMarkerBitmap(
                            loadedBitmaps[0]!!,
                            loadedBitmaps[1]!!
                        )
                        doubleMarkerBitmapCache[iconId] = CachedDoubleMarkerBitmap(
                            avatarUrls = avatarUrls,
                            bitmap = markerBitmap
                        )
                        currentStyle.addImage(iconId, markerBitmap)
                    },
                    onCleared = {
                        pendingDoubleMarkerLoads.remove(loadKey)
                    }
                )
            }
        }
    }

    private fun loadFriendAvatarBitmap(
        person: MarkerPerson,
        onReady: (Bitmap) -> Unit,
        onCleared: () -> Unit
    ) {
        val avatarSize = 60.dpToPx()
        val cornerRadius = 15.dpToPx()

        Glide.with(fragment)
            .asBitmap()
            .load(person.avatarUrl)
            .override(avatarSize, avatarSize)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))
            .placeholder(R.drawable.ic_default_avatar_rectangle)
            .error(R.drawable.ic_default_avatar_rectangle)
            .fallback(R.drawable.ic_default_avatar_rectangle)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    if (!fragment.isAdded || fragment.view == null) return
                    onReady(resource)
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    if (!fragment.isAdded || fragment.view == null) return
                    onReady(errorDrawable?.toBitmap(avatarSize, avatarSize) ?: defaultAvatarBitmap())
                }

                override fun onLoadCleared(
                    placeholder: android.graphics.drawable.Drawable?
                ) {
                    onCleared()
                }
            })
    }

    private fun defaultAvatarBitmap(): Bitmap {
        val drawable = AppCompatResources.getDrawable(
            fragment.requireContext(),
            R.drawable.ic_default_avatar
        )

        return drawable?.toBitmap(
            width = 60.dpToPx(),
            height = 60.dpToPx(),
            config = Bitmap.Config.ARGB_8888
        ) ?: createBitmap(60.dpToPx(), 60.dpToPx())
    }

    private fun createDoubleFriendMarkerBitmap(
        firstAvatar: Bitmap,
        secondAvatar: Bitmap
    ): Bitmap {
        val markerBinding = LayoutDoubleFriendMarkerBinding.inflate(
            LayoutInflater.from(fragment.requireContext())
        )
        markerBinding.avatarLeft.setImageBitmap(firstAvatar)
        markerBinding.avatarRight.setImageBitmap(secondAvatar)

        val markerView = markerBinding.root
        val width = 144.dpToPx()
        val height = 86.dpToPx()

        markerView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                width,
                android.view.View.MeasureSpec.EXACTLY
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                height,
                android.view.View.MeasureSpec.EXACTLY
            )
        )
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

        return createBitmap(markerView.measuredWidth, markerView.measuredHeight)
            .also { bitmap -> markerView.draw(Canvas(bitmap)) }
    }

    fun setFocusedFriendMarker(style: Style?, friendId: String?) {
        focusedFriendId = friendId
        style
            ?.getLayerAs<SymbolLayer>(friendLocationLayerId)
            ?.setProperties(iconSize(friendMarkerSizeExpression()))
    }

    fun updateBearing(style: Style?, bearing: Float) {
        currentBearing = bearing
        style
            ?.getLayerAs<SymbolLayer>(currentDirectionLayerId)
            ?.setProperties(iconRotate(currentBearing))
    }

    fun clearStyle(style: Style?) {
        if (style == null || activeStyle === style) {
            activeStyle = null
            pendingDoubleMarkerLoads.clear()
            currentMarkerHidden = false
            currentLocationFeature = null
            renderedSingleMarkers = emptyList()
            renderedDoubleMarkers = emptyList()
            visibleDoubleMarkerIds = null
        }
    }

    private fun friendMarkerSizeExpression() = focusedFriendId?.let { focusedId ->
        match(
            get("id"),
            literal(FRIEND_MARKER_SIZE),
            stop(focusedId, literal(FOCUSED_FRIEND_MARKER_SIZE))
        )
    } ?: literal(FRIEND_MARKER_SIZE)

    private fun loadFriendAvatarImages(
        style: Style,
        markers: List<FriendMarker>
    ) {
        markers
            .filterNot(FriendMarker::isDouble)
            .filterNot(FriendMarker::containsCurrentUser)
            .forEach { marker ->
            val person = marker.people.first()
            val iconId = "friend-avatar-${person.id}"

            if (style.getImage(iconId) != null) return@forEach

            markerBitmapCache[person.id]
                ?.takeIf { it.avatarUrl == person.avatarUrl }
                ?.let { cachedMarker ->
                    style.addImage(iconId, cachedMarker.bitmap)
                    return@forEach
                }

            loadFriendAvatarBitmap(
                person = person,
                onReady = { bitmap ->
                    val currentStyle = activeStyle ?: return@loadFriendAvatarBitmap
                    if (currentStyle !== style) return@loadFriendAvatarBitmap

                    val markerBitmap = createFriendMarkerBitmap(bitmap)
                    markerBitmapCache[person.id] = CachedMarkerBitmap(
                        avatarUrl = person.avatarUrl,
                        bitmap = markerBitmap
                    )
                    currentStyle.addImage(iconId, markerBitmap)
                },
                onCleared = {}
            )
        }
    }

    private fun createFriendMarkerBitmap(avatarBitmap: Bitmap): Bitmap {
        val markerBinding =
            LayoutMarkerBinding.inflate(LayoutInflater.from(fragment.requireContext()))

        markerBinding.avatar.setImageBitmap(avatarBitmap)

        val markerView = markerBinding.root
        val width = 76.dpToPx()
        val height = 88.dpToPx()

        markerView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                width,
                android.view.View.MeasureSpec.EXACTLY
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                height,
                android.view.View.MeasureSpec.EXACTLY
            )
        )
        markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)

        return createBitmap(markerView.measuredWidth, markerView.measuredHeight)
            .also { bitmap -> markerView.draw(Canvas(bitmap)) }
    }

    private fun Int.dpToPx(): Int {
        return (this * fragment.resources.displayMetrics.density).toInt()
    }
}
