package com.pando.app.features.home.ui.center.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
import org.maplibre.android.style.layers.FillLayer
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
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
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
import org.maplibre.geojson.Polygon
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max

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
        private const val FRIEND_CLUSTER_RADIUS = 30
        private const val FRIEND_CLUSTER_MAX_ZOOM = 15
        private const val NEARBY_GROUP_RADIUS_METERS = 30.0
        private const val NEARBY_GROUP_CIRCLE_SEGMENTS = 48
        private const val PERSON_COUNT_PROPERTY = "personCount"
        private const val GROUP_MARKER_BASE_WIDTH_DP = 144
        private const val GROUP_MARKER_HEIGHT_DP = 86
        private const val GROUP_AVATAR_SIZE_DP = 60
        private const val GROUP_AVATAR_OVERLAP_DP = 16
        private const val GROUP_AVATAR_HORIZONTAL_PADDING_DP = 20
        private const val MAX_VISIBLE_GROUP_AVATARS = 5
    }

    private val currentLocationSourceId = "current-location-source"
    private val currentLocationLayerId = "current-location-layer"
    private val currentDirectionIconId = "current-location-direction-icon"
    private val currentDirectionLayerId = "current-location-direction-layer"

    private val friendLocationSourceId = "friend-location-source"
    private val friendLocationLayerId = "friend-location-layer"
    private val nearbyGroupLocationSourceId = "nearby-group-location-source"
    private val nearbyGroupLocationLayerId = "nearby-group-location-layer"
    private val nearbyGroupMemberSourceId = "nearby-group-member-source"
    private val nearbyGroupMemberLayerId = "nearby-group-member-layer"
    private val nearbyGroupRadiusSourceId = "nearby-group-radius-source"
    private val nearbyGroupRadiusLayerId = "nearby-group-radius-layer"
    private val friendClusterCircleLayerId = "friend-location-cluster-circle-layer"
    private val friendClusterCountLayerId = "friend-location-cluster-count-layer"

    val interactiveLayerIds = arrayOf(
        currentLocationLayerId,
        nearbyGroupMemberLayerId,
        nearbyGroupLocationLayerId,
        friendClusterCircleLayerId,
        friendClusterCountLayerId,
        friendLocationLayerId
    )

    private data class CachedMarkerBitmap(
        val avatarUrl: String?,
        val bitmap: Bitmap
    )

    private data class CachedGroupMarkerBitmap(
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

    private data class NearbyGroup(
        val people: List<MarkerPerson>,
        val latitude: Double,
        val longitude: Double
    ) {
        val isNearbyGroup: Boolean
            get() = people.size >= 2

        val containsCurrentUser: Boolean
            get() = people.any(MarkerPerson::isCurrentUser)

        val iconId: String
            get() = if (isNearbyGroup) {
                "friend-double-avatar-${people.joinToString("-") { it.id }}"
            } else {
                "friend-avatar-${people.first().id}"
            }

        val markerId: String
            get() = people.joinToString(",") { it.id }

        val name: String
            get() = if (people.size <= 2) {
                people.joinToString(" & ") { it.name }
            } else {
                "${people.first().name} và ${people.size - 1} người khác"
            }

        val personCount: Int
            get() = people.size
    }

    /** One point fed into MapLibre's cluster source for one logical marker. */
    private data class MarkerProxy(
        val marker: NearbyGroup,
        val personCount: Int,
        val markerType: String
    )

    private val markerBitmapCache = mutableMapOf<String, CachedMarkerBitmap>()
    private val nearbyGroupBitmapCache = mutableMapOf<String, CachedGroupMarkerBitmap>()
    private val pendingNearbyGroupLoads = mutableSetOf<String>()
    private var activeStyle: Style? = null
    private var currentBearing = 0f
    private var focusedFriendId: String? = null
    private var currentMarkerHidden = false
    private var currentLocationFeature: Feature? = null
    private var renderedSingleMarkers: List<NearbyGroup> = emptyList()
    private var renderedNearbyGroups: List<NearbyGroup> = emptyList()
    private var renderedStandaloneCurrentMarker: NearbyGroup? = null
    private var renderedPeople: Map<String, MarkerPerson> = emptyMap()
    private var visibleNearbyGroupIds: Set<String>? = null
    private var visibleNearbyMemberIds: Set<String>? = null
    private var visibleNearbyRadiusIds: Set<String>? = null

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
        longitude: Double,
        currentUserId: String? = null,
        currentUserName: String? = null
    ) {
        activeStyle = style

        currentLocationFeature = Feature.fromGeometry(
            Point.fromLngLat(longitude, latitude)
        ).apply {
            addStringProperty("markerType", "current")
            currentUserId?.let { addStringProperty("id", it) }
            currentUserName?.let { addStringProperty("name", it) }
        }
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
        if (currentMarkerHidden == hidden && currentLocationFeature != null) return
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

        val markerGroups = buildNearbyGroups(friends, currentUser)
        val nearbyGroups = markerGroups.filter(NearbyGroup::isNearbyGroup)
        val singleMarkers = markerGroups.filterNot(NearbyGroup::isNearbyGroup)
        val singleFriendMarkers = singleMarkers.filterNot(NearbyGroup::containsCurrentUser)
        val standaloneCurrentMarker = singleMarkers.firstOrNull(NearbyGroup::containsCurrentUser)

        renderedSingleMarkers = singleFriendMarkers
        renderedNearbyGroups = nearbyGroups
        renderedStandaloneCurrentMarker = standaloneCurrentMarker
        renderedPeople = markerGroups
            .flatMap(NearbyGroup::people)
            .associateBy(MarkerPerson::id)
        visibleNearbyGroupIds = null
        visibleNearbyMemberIds = null
        visibleNearbyRadiusIds = null

        renderSingleFriendMarkers(
            style = style,
            markers = singleFriendMarkers,
            nearbyGroups = nearbyGroups,
            standaloneCurrentMarker = standaloneCurrentMarker
        )
        renderNearbyGroupMarkers(style, nearbyGroups)
        loadFriendAvatarImages(style, singleFriendMarkers + nearbyGroups)
        loadNearbyGroupAvatarImages(style, nearbyGroups)
    }

    private fun renderSingleFriendMarkers(
        style: Style,
        markers: List<NearbyGroup>,
        nearbyGroups: List<NearbyGroup>,
        standaloneCurrentMarker: NearbyGroup?
    ) {
        val proxies = markers.map {
            MarkerProxy(it, it.personCount, markerType = "single")
        } + nearbyGroups.map {
            MarkerProxy(it, it.personCount, markerType = "nearbyGroupProxy")
        } + listOfNotNull(
            // The blue current-location marker has its own source. This
            // weighted, icon-less proxy only lets a far-zoom cluster count the
            // current user when it touches another logical marker.
            standaloneCurrentMarker?.let {
                MarkerProxy(it, it.personCount, markerType = "currentProxy")
            }
        )
        val featureCollection = FeatureCollection.fromFeatures(
            proxies.map(::createMarkerProxyFeature)
        )
        val existingSource = style.getSourceAs<GeoJsonSource>(friendLocationSourceId)

        if (existingSource == null) {
            val sourceOptions = GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(FRIEND_CLUSTER_RADIUS)
                .withClusterMaxZoom(FRIEND_CLUSTER_MAX_ZOOM)
                .withClusterProperty(
                    PERSON_COUNT_PROPERTY,
                    literal("+"),
                    get(PERSON_COUNT_PROPERTY)
                )

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
                    friendClusterFilter()
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
                                    coalesce(
                                        get(PERSON_COUNT_PROPERTY),
                                        get("point_count"),
                                        literal(2)
                                    ),
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
                    friendClusterFilter()
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
                            neq(get("markerType"), literal("nearbyGroupProxy")),
                            neq(get("markerType"), literal("currentProxy"))
                        )
                    )
            )
        }
    }

    private fun friendClusterFilter() = all(
        has("point_count"),
        gt(get("point_count"), literal(1))
    )

    private fun renderNearbyGroupMarkers(
        style: Style,
        markers: List<NearbyGroup>
    ) {
        val featureCollection = FeatureCollection.fromFeatures(
            markers.map { createNearbyGroupFeature(it) }
        )
        val existingSource = style.getSourceAs<GeoJsonSource>(nearbyGroupLocationSourceId)

        if (existingSource == null) {
            style.addSource(
                GeoJsonSource(
                    nearbyGroupLocationSourceId,
                    featureCollection
                )
            )
        } else {
            existingSource.setGeoJson(featureCollection)
        }

        if (style.getLayer(nearbyGroupLocationLayerId) == null) {
            style.addLayer(
                SymbolLayer(
                    nearbyGroupLocationLayerId,
                    nearbyGroupLocationSourceId
                ).withProperties(
                    iconImage(get("iconId")),
                    iconSize(literal(FRIEND_MARKER_SIZE)),
                    iconAnchor(ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }

        val memberFeatureCollection = FeatureCollection.fromFeatures(emptyList())
        val memberSource = style.getSourceAs<GeoJsonSource>(nearbyGroupMemberSourceId)
        if (memberSource == null) {
            style.addSource(GeoJsonSource(nearbyGroupMemberSourceId, memberFeatureCollection))
        } else {
            memberSource.setGeoJson(memberFeatureCollection)
        }

        if (style.getLayer(nearbyGroupMemberLayerId) == null) {
            style.addLayer(
                SymbolLayer(
                    nearbyGroupMemberLayerId,
                    nearbyGroupMemberSourceId
                ).withProperties(
                    iconImage(get("iconId")),
                    iconSize(friendMarkerSizeExpression()),
                    iconAnchor(ICON_ANCHOR_BOTTOM),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true)
                )
            )
        }

        val radiusFeatureCollection = FeatureCollection.fromFeatures(emptyList())
        val radiusSource = style.getSourceAs<GeoJsonSource>(nearbyGroupRadiusSourceId)
        if (radiusSource == null) {
            style.addSource(GeoJsonSource(nearbyGroupRadiusSourceId, radiusFeatureCollection))
        } else {
            radiusSource.setGeoJson(radiusFeatureCollection)
        }

        if (style.getLayer(nearbyGroupRadiusLayerId) == null) {
            val radiusLayer = FillLayer(
                nearbyGroupRadiusLayerId,
                nearbyGroupRadiusSourceId
            ).withProperties(
                fillColor("#3F8CFF"),
                fillOpacity(0.08f),
                fillOutlineColor("#3F8CFF")
            )
            val layerBelow = if (style.getLayer(currentLocationLayerId) != null) {
                currentLocationLayerId
            } else {
                nearbyGroupLocationLayerId
            }
            style.addLayerBelow(radiusLayer, layerBelow)
        }

        // Visibility is updated from the current camera so an isolated group
        // stays visible at close zoom, while a group that joins another
        // marker is hidden and represented by the clustered proxy count.
        visibleNearbyGroupIds = null
        visibleNearbyMemberIds = null
        visibleNearbyRadiusIds = null
    }

    fun updateNearbyGroupVisibility(
        map: MapLibreMap,
        style: Style
    ) {
        val groupSource = style.getSourceAs<GeoJsonSource>(nearbyGroupLocationSourceId)
            ?: return
        val memberSource = style.getSourceAs<GeoJsonSource>(nearbyGroupMemberSourceId)
            ?: return
        val radiusSource = style.getSourceAs<GeoJsonSource>(nearbyGroupRadiusSourceId)
            ?: return

        val isCloseZoom = map.cameraPosition.zoom > FRIEND_CLUSTER_MAX_ZOOM
        val splitGroups = if (isCloseZoom) {
            renderedNearbyGroups.filter { nearbyGroupShouldSplit(map, it) }
        } else {
            emptyList()
        }
        val splitGroupIds = splitGroups.mapTo(mutableSetOf(), NearbyGroup::markerId)

        val visibleMarkers = if (isCloseZoom) {
            renderedNearbyGroups
        } else {
            renderedNearbyGroups.filterNot { marker ->
                // Group size alone must never hide a nearby marker. It is
                // hidden only when its screen footprint touches another
                // logical marker and the proxy can take over as a cluster.
                markerTouchesAnotherMarker(map, marker)
            }
        }

        val stackMarkers = visibleMarkers.filterNot { it.markerId in splitGroupIds }
        val memberMarkers = splitGroups
            .flatMap { group ->
                group.people.filterNot(MarkerPerson::isCurrentUser).map { person ->
                    group to person
                }
            }
        val radiusMarkers = splitGroups

        val visibleIds = stackMarkers.mapTo(mutableSetOf(), NearbyGroup::markerId)
        val visibleMemberIds = memberMarkers.mapTo(mutableSetOf()) { (_, person) -> person.id }
        val visibleRadiusIds = radiusMarkers.mapTo(mutableSetOf(), NearbyGroup::markerId)

        val shouldHideCurrentMarker = if (isCloseZoom) {
            stackMarkers.any(NearbyGroup::containsCurrentUser)
        } else {
            renderedNearbyGroups.any(NearbyGroup::containsCurrentUser)
        }
        setCurrentMarkerHidden(style, shouldHideCurrentMarker)

        if (
            visibleIds == visibleNearbyGroupIds &&
            visibleMemberIds == visibleNearbyMemberIds &&
            visibleRadiusIds == visibleNearbyRadiusIds
        ) {
            return
        }

        visibleNearbyGroupIds = visibleIds
        visibleNearbyMemberIds = visibleMemberIds
        visibleNearbyRadiusIds = visibleRadiusIds

        groupSource.setGeoJson(
            FeatureCollection.fromFeatures(
                stackMarkers.map(::createNearbyGroupFeature)
            )
        )
        memberSource.setGeoJson(
            FeatureCollection.fromFeatures(
                memberMarkers.map { (group, person) ->
                    createNearbyGroupMemberFeature(group, person)
                }
            )
        )
        radiusSource.setGeoJson(
            FeatureCollection.fromFeatures(
                radiusMarkers.map(::createNearbyGroupRadiusFeature)
            )
        )
    }

    private fun nearbyGroupShouldSplit(
        map: MapLibreMap,
        marker: NearbyGroup
    ): Boolean {
        val screenPoints = marker.people.map { person ->
            val point = map.projection.toScreenLocation(
                LatLng(person.latitude, person.longitude)
            )
            MarkerScreenPoint(
                id = person.id,
                x = point.x,
                y = point.y
            )
        }

        return MapMarkerGrouping.shouldSplitNearbyGroup(
            points = screenPoints,
            thresholdPx = MapMarkerGrouping.NEARBY_SPLIT_THRESHOLD_DP
                .toInt()
                .dpToPx()
                .toFloat()
        )
    }

    private fun markerTouchesAnotherMarker(
        map: MapLibreMap,
        marker: NearbyGroup
    ): Boolean {
        val markerPoint = map.projection.toScreenLocation(
            LatLng(marker.latitude, marker.longitude)
        )

        // Do not call MapLibre's queryRenderedFeatures here. It is a synchronous
        // native round-trip that waits for the renderer and can block the UI
        // thread for several seconds while a style or tile is being rendered.
        // The source already contains every marker, so projected screen
        // distances are enough to decide whether the group visually touches a
        // neighboring marker.
        val touchesSingleMarker = renderedSingleMarkers.any { otherMarker ->
            screenDistance(map, markerPoint, otherMarker) <= FRIEND_CLUSTER_RADIUS
        }
        if (touchesSingleMarker) return true

        renderedStandaloneCurrentMarker?.let { currentMarker ->
            if (screenDistance(map, markerPoint, currentMarker) <= FRIEND_CLUSTER_RADIUS) {
                return true
            }
        }

        return renderedNearbyGroups.any { otherMarker ->
            otherMarker.markerId != marker.markerId &&
                screenDistance(map, markerPoint, otherMarker) <= FRIEND_CLUSTER_RADIUS
        }
    }

    private fun screenDistance(
        map: MapLibreMap,
        markerPoint: PointF,
        otherMarker: NearbyGroup
    ): Float {
        val otherPoint = map.projection.toScreenLocation(
            LatLng(otherMarker.latitude, otherMarker.longitude)
        )
        return hypot(
            (markerPoint.x - otherPoint.x).toDouble(),
            (markerPoint.y - otherPoint.y).toDouble()
        ).toFloat()
    }

    private fun createMarkerProxyFeature(proxy: MarkerProxy): Feature {
        val marker = proxy.marker
        return Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude)
        ).apply {
            addStringProperty("id", marker.markerId)
            addStringProperty("name", marker.name)
            addStringProperty("iconId", marker.iconId)
            addStringProperty("markerType", proxy.markerType)
            addNumberProperty(PERSON_COUNT_PROPERTY, proxy.personCount)
        }
    }

    private fun createNearbyGroupFeature(
        marker: NearbyGroup
    ): Feature {
        return Feature.fromGeometry(
            Point.fromLngLat(marker.longitude, marker.latitude)
        ).apply {
            addStringProperty("id", marker.markerId)
            addStringProperty("name", marker.name)
            addStringProperty("iconId", marker.iconId)
            addStringProperty("markerType", "nearbyGroup")
            addNumberProperty(PERSON_COUNT_PROPERTY, marker.personCount)
        }
    }

    private fun createNearbyGroupMemberFeature(
        group: NearbyGroup,
        person: MarkerPerson
    ): Feature {
        return Feature.fromGeometry(
            Point.fromLngLat(person.longitude, person.latitude)
        ).apply {
            addStringProperty("id", person.id)
            addStringProperty("name", person.name)
            addStringProperty("iconId", "friend-avatar-${person.id}")
            addStringProperty("groupId", group.markerId)
            addStringProperty("markerType", "nearbyGroupMember")
        }
    }

    private fun createNearbyGroupRadiusFeature(
        marker: NearbyGroup
    ): Feature {
        val ring = MapMarkerGrouping.circlePoints(
            latitude = marker.latitude,
            longitude = marker.longitude,
            radiusMeters = NEARBY_GROUP_RADIUS_METERS,
            segments = NEARBY_GROUP_CIRCLE_SEGMENTS
        ).map { point ->
            Point.fromLngLat(point.longitude, point.latitude)
        }

        return Feature.fromGeometry(
            Polygon.fromLngLats(listOf(ring))
        ).apply {
            addStringProperty("id", marker.markerId)
            addNumberProperty(PERSON_COUNT_PROPERTY, marker.personCount)
            addStringProperty("markerType", "nearbyGroupRadius")
        }
    }

    fun resolveFollowLocation(personIds: Set<String>): MarkerFollowLocation? {
        val people = renderedPeople.values.map { person ->
            NearbyMarkerPerson(
                id = person.id,
                name = person.name,
                avatarUrl = person.avatarUrl,
                latitude = person.latitude,
                longitude = person.longitude,
                isCurrentUser = person.isCurrentUser
            )
        }
        return MapMarkerGrouping.resolveFollowLocation(people, personIds)
    }

    fun personIdsForFeature(style: Style, feature: Feature): Set<String> {
        if (feature.getNumberProperty("point_count") != null) {
            val source = style.getSourceAs<GeoJsonSource>(friendLocationSourceId)
                ?: return emptySet()
            return clusterPersonIds(source, feature)
        }

        return MapMarkerGrouping.personIdsFromMarkerId(
            feature.getStringProperty("id") ?: ""
        )
    }

    private fun clusterPersonIds(
        source: GeoJsonSource,
        cluster: Feature
    ): Set<String> {
        val pointCount = cluster.getNumberProperty("point_count")?.toLong() ?: return emptySet()
        val leaves = runCatching {
            source.getClusterLeaves(cluster, pointCount, 0)
        }.getOrNull()?.features() ?: return emptySet()

        return leaves.flatMap { leaf ->
            if (leaf.getNumberProperty("point_count") != null) {
                clusterPersonIds(source, leaf)
            } else {
                MapMarkerGrouping.personIdsFromMarkerId(
                    leaf.getStringProperty("id") ?: ""
                )
            }
        }.toSet()
    }

    /**
     * Groups people who are within the nearby radius. A group can contain the
     * current user and any number of friends. A group is represented by one
     * avatar marker at high zoom and one weighted proxy in the cluster source.
     */
    private fun buildNearbyGroups(
        friends: List<FriendItemModel>,
        currentUser: CurrentUserMapMarker?
    ): List<NearbyGroup> {
        val people = friends
            .filter { friend -> friend.id.toString() != currentUser?.id?.toString() }
            .mapNotNull { friend ->
                val latitude = friend.latitude ?: return@mapNotNull null
                val longitude = friend.longitude ?: return@mapNotNull null
                NearbyMarkerPerson(
                    id = friend.id.toString(),
                    name = friend.name,
                    avatarUrl = friend.avatarUrl,
                    latitude = latitude,
                    longitude = longitude,
                    isCurrentUser = false
                )
            }
            .toMutableList()

        if (currentUser != null) {
            people += NearbyMarkerPerson(
                id = currentUser.id.toString(),
                name = currentUser.name,
                avatarUrl = currentUser.avatarUrl,
                latitude = currentUser.latitude,
                longitude = currentUser.longitude,
                isCurrentUser = true
            )
        }

        return MapMarkerGrouping.group(people).map { group ->
            NearbyGroup(
                people = group.people.map { person ->
                    MarkerPerson(
                        id = person.id,
                        name = person.name,
                        avatarUrl = person.avatarUrl,
                        latitude = person.latitude,
                        longitude = person.longitude,
                        isCurrentUser = person.isCurrentUser
                    )
                },
                latitude = group.latitude,
                longitude = group.longitude
            )
        }
    }

    private fun loadNearbyGroupAvatarImages(
        style: Style,
        markers: List<NearbyGroup>
    ) {
        markers.filter(NearbyGroup::isNearbyGroup).forEach { marker ->
            val iconId = marker.iconId
            val avatarUrls = marker.people.map(MarkerPerson::avatarUrl)
            val cachedMarker = nearbyGroupBitmapCache[iconId]

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
                    createGroupFriendMarkerBitmap(
                        avatars = List(marker.people.size) { defaultAvatar }
                    )
                )
            } else if (cachedMarker != null) {
                style.removeImage(iconId)
            }

            val loadKey = "$iconId|${avatarUrls.joinToString("|")}"
            if (!pendingNearbyGroupLoads.add(loadKey)) return@forEach

            val loadedBitmaps = arrayOfNulls<Bitmap>(marker.people.size)
            marker.people.forEachIndexed { index, person ->
                loadFriendAvatarBitmap(
                    person = person,
                    onReady = { bitmap ->
                        loadedBitmaps[index] = bitmap
                        if (loadedBitmaps.any { it == null }) return@loadFriendAvatarBitmap

                        pendingNearbyGroupLoads.remove(loadKey)
                        val currentStyle = activeStyle ?: return@loadFriendAvatarBitmap
                        if (currentStyle !== style) return@loadFriendAvatarBitmap

                        val markerBitmap = createGroupFriendMarkerBitmap(
                            avatars = loadedBitmaps.map { it!! }
                        )
                        nearbyGroupBitmapCache[iconId] = CachedGroupMarkerBitmap(
                            avatarUrls = avatarUrls,
                            bitmap = markerBitmap
                        )
                        currentStyle.addImage(iconId, markerBitmap)
                    },
                    onCleared = {
                        pendingNearbyGroupLoads.remove(loadKey)
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

    private fun createGroupFriendMarkerBitmap(
        avatars: List<Bitmap>
    ): Bitmap {
        val markerBinding = LayoutDoubleFriendMarkerBinding.inflate(
            LayoutInflater.from(fragment.requireContext())
        )

        val markerView = markerBinding.root
        val avatarSize = GROUP_AVATAR_SIZE_DP.dpToPx()
        val overlap = GROUP_AVATAR_OVERLAP_DP.dpToPx()
        val displayedAvatars = avatars.take(MAX_VISIBLE_GROUP_AVATARS)
        val contentWidth = avatarSize +
            (displayedAvatars.size - 1).coerceAtLeast(0) * (avatarSize - overlap)
        val width = max(
            GROUP_MARKER_BASE_WIDTH_DP.dpToPx(),
            contentWidth + GROUP_AVATAR_HORIZONTAL_PADDING_DP.dpToPx()
        )
        val height = GROUP_MARKER_HEIGHT_DP.dpToPx()

        markerBinding.avatarContainer.layoutParams = FrameLayout.LayoutParams(
            contentWidth,
            avatarSize
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 7.dpToPx()
        }

        displayedAvatars.forEachIndexed { index, avatar ->
            val imageView = ImageView(fragment.requireContext()).apply {
                setImageBitmap(avatar)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            }
            markerBinding.avatarContainer.addView(
                imageView,
                FrameLayout.LayoutParams(avatarSize, avatarSize).apply {
                    leftMargin = index * (avatarSize - overlap)
                }
            )
        }

        val hiddenAvatarCount = avatars.size - displayedAvatars.size
        if (hiddenAvatarCount > 0) {
            val badgeSize = 30.dpToPx()
            val badge = TextView(fragment.requireContext()).apply {
                text = "+$hiddenAvatarCount"
                setTextColor(Color.WHITE)
                setTextSize(11f)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#3F8CFF"))
                    setStroke(2.dpToPx(), Color.WHITE)
                }
            }
            markerView.addView(
                badge,
                FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = 2.dpToPx()
                    marginEnd = 4.dpToPx()
                }
            )
        }

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
        style?.getLayerAs<SymbolLayer>(friendLocationLayerId)
            ?.setProperties(iconSize(friendMarkerSizeExpression()))
        style?.getLayerAs<SymbolLayer>(nearbyGroupMemberLayerId)
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
            pendingNearbyGroupLoads.clear()
            currentMarkerHidden = false
            currentLocationFeature = null
            renderedSingleMarkers = emptyList()
            renderedNearbyGroups = emptyList()
            renderedStandaloneCurrentMarker = null
            renderedPeople = emptyMap()
            visibleNearbyGroupIds = null
            visibleNearbyMemberIds = null
            visibleNearbyRadiusIds = null
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
        markers: List<NearbyGroup>
    ) {
        markers
            .flatMap(NearbyGroup::people)
            .filterNot(MarkerPerson::isCurrentUser)
            .distinctBy(MarkerPerson::id)
            .forEach { person ->
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
