package com.pando.app.features.home.ui.center.map

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.pando.app.databinding.LayoutMarkerBinding
import com.pando.app.features.home.data.model.entity.FriendItemModel
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
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

class MapMarkerRenderer(
    private val fragment: Fragment
) {
    companion object {
        private const val FRIEND_MARKER_SIZE = 0.75f
        private const val FOCUSED_FRIEND_MARKER_SIZE = 0.85f
        private const val FRIEND_CLUSTER_RADIUS = 25
        private const val FRIEND_CLUSTER_MAX_ZOOM = 15
    }

    private val currentLocationSourceId = "current-location-source"
    private val currentLocationLayerId = "current-location-layer"
    private val currentDirectionIconId = "current-location-direction-icon"
    private val currentDirectionLayerId = "current-location-direction-layer"

    private val friendLocationSourceId = "friend-location-source"
    private val friendLocationLayerId = "friend-location-layer"
    private val friendClusterCircleLayerId = "friend-location-cluster-circle-layer"
    private val friendClusterCountLayerId = "friend-location-cluster-count-layer"

    val interactiveLayerIds = arrayOf(
        friendClusterCircleLayerId,
        friendClusterCountLayerId,
        friendLocationLayerId
    )

    private data class CachedMarkerBitmap(
        val avatarUrl: String?,
        val bitmap: Bitmap
    )

    private val markerBitmapCache = mutableMapOf<UUID, CachedMarkerBitmap>()
    private var activeStyle: Style? = null
    private var currentBearing = 0f
    private var focusedFriendId: String? = null

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

        val feature = Feature.fromGeometry(
            Point.fromLngLat(longitude, latitude)
        )

        val source = style.getSourceAs<GeoJsonSource>(currentLocationSourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(currentLocationSourceId, feature))
        } else {
            source.setGeoJson(feature)
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

    fun renderFriends(style: Style, friends: List<FriendItemModel>) {
        activeStyle = style

        val features = friends.mapNotNull { friend ->
            val longitude = friend.longitude
            val latitude = friend.latitude

            if (longitude == null || latitude == null) return@mapNotNull null

            Feature.fromGeometry(
                Point.fromLngLat(longitude, latitude)
            ).apply {
                addStringProperty("id", friend.id.toString())
                addStringProperty("name", friend.name)
                addStringProperty("iconId", "friend-avatar-${friend.id}")
            }
        }

        val featureCollection = FeatureCollection.fromFeatures(features)
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
            style.addLayer(
                CircleLayer(
                    friendClusterCircleLayerId,
                    friendLocationSourceId
                ).withProperties(
                    circleRadius(18f),
                    circleColor("#3F8CFF"),
                    circleStrokeWidth(3f),
                    circleStrokeColor("#FFFFFF")
                ).withFilter(has("point_count"))
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
                                    get("point_count"),
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
                ).withFilter(has("point_count"))
            )
        }

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
                    .withFilter(not(has("point_count")))
            )
        }

        loadFriendAvatarImages(style, friends)
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
        friends: List<FriendItemModel>
    ) {
        friends.forEach { friend ->
            val iconId = "friend-avatar-${friend.id}"

            if (style.getImage(iconId) != null) return@forEach

            markerBitmapCache[friend.id]
                ?.takeIf { it.avatarUrl == friend.avatarUrl }
                ?.let { cachedMarker ->
                    style.addImage(iconId, cachedMarker.bitmap)
                    return@forEach
                }

            val avatarSize = 60.dpToPx()
            val cornerRadius = 15.dpToPx()

            Glide.with(fragment)
                .asBitmap()
                .load(friend.avatarUrl)
                .override(avatarSize, avatarSize)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar)
                .fallback(R.drawable.ic_default_avatar)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        if (!fragment.isAdded || fragment.view == null) return

                        val currentStyle = activeStyle ?: return
                        if (currentStyle !== style) return

                        val markerBitmap = createFriendMarkerBitmap(resource)
                        markerBitmapCache[friend.id] = CachedMarkerBitmap(
                            avatarUrl = friend.avatarUrl,
                            bitmap = markerBitmap
                        )
                        currentStyle.addImage(iconId, markerBitmap)
                    }

                    override fun onLoadCleared(
                        placeholder: android.graphics.drawable.Drawable?
                    ) = Unit
                })
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
