package com.pando.app.features.home.ui.center.map

import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A location used while building the logical markers shown on the map.
 *
 * This type deliberately contains no Android or MapLibre classes so the
 * nearby grouping rules can be tested on the JVM as well as used by the map
 * renderer.
 */
data class NearbyMarkerPerson(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val isCurrentUser: Boolean
)

data class NearbyMarkerGroup(
    val people: List<NearbyMarkerPerson>,
    val latitude: Double,
    val longitude: Double
) {
    val isGroup: Boolean
        get() = people.size >= 2

    val containsCurrentUser: Boolean
        get() = people.any(NearbyMarkerPerson::isCurrentUser)

    val personCount: Int
        get() = people.size
}

data class MarkerScreenPoint(
    val id: String,
    val x: Float,
    val y: Float
)

data class MarkerFollowLocation(
    val latitude: Double,
    val longitude: Double
)

data class NearbyCirclePoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Builds connected components using a geographic radius.  Connected
 * components are intentional here: if A is within 30m of B and B is within
 * 30m of C, all three belong to the same nearby marker even when A and C are
 * slightly farther apart.
 */
object MapMarkerGrouping {
    const val NEARBY_RADIUS_METERS = 30.0
    const val NEARBY_SPLIT_THRESHOLD_DP = 48.0

    /**
     * Returns the label used for a rendered cluster. A cluster is only
     * created when at least two logical proxies touch; an isolated nearby
     * group therefore returns null and keeps its stacked-avatar marker.
     */
    fun clusterLabel(personCounts: List<Int>): String? {
        if (personCounts.size < 2) return null
        return clusterLabelForPersonCount(personCounts.sum())
    }

    fun clusterLabelForPersonCount(personCount: Int): String {
        return "+${(personCount - 1).coerceAtLeast(1)}"
    }

    /**
     * Returns true when at least two members no longer touch on screen. The
     * geographic nearby group is intentionally kept intact; this only changes
     * how it is rendered at the current zoom level.
     */
    fun shouldSplitNearbyGroup(
        points: List<MarkerScreenPoint>,
        thresholdPx: Float
    ): Boolean {
        if (points.size < 2) return false

        points.indices.forEach { firstIndex ->
            ((firstIndex + 1) until points.size).forEach { secondIndex ->
                val first = points[firstIndex]
                val second = points[secondIndex]
                val dx = first.x - second.x
                val dy = first.y - second.y
                if (sqrt((dx * dx + dy * dy).toDouble()) > thresholdPx) {
                    return true
                }
            }
        }

        return false
    }

    fun resolveFollowLocation(
        people: Collection<NearbyMarkerPerson>,
        personIds: Set<String>
    ): MarkerFollowLocation? {
        if (personIds.isEmpty()) return null

        val matchedPeople = people.filter { it.id in personIds }
        if (matchedPeople.isEmpty()) return null

        return MarkerFollowLocation(
            latitude = matchedPeople.map(NearbyMarkerPerson::latitude).average(),
            longitude = matchedPeople.map(NearbyMarkerPerson::longitude).average()
        )
    }

    fun personIdsFromMarkerId(markerId: String): Set<String> {
        return markerId
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
    }

    /** Returns a closed ring representing a geodesic circle on the map. */
    fun circlePoints(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = NEARBY_RADIUS_METERS,
        segments: Int = 48
    ): List<NearbyCirclePoint> {
        require(segments >= 8) { "A circle needs at least eight segments" }

        val earthRadiusMeters = 6_371_000.0
        val angularDistance = radiusMeters / earthRadiusMeters
        val centerLatitude = Math.toRadians(latitude)
        val centerLongitude = Math.toRadians(longitude)

        return (0..segments).map { index ->
            val bearing = 2.0 * Math.PI * index / segments
            val pointLatitude = asin(
                sin(centerLatitude) * cos(angularDistance) +
                    cos(centerLatitude) * sin(angularDistance) * cos(bearing)
            )
            val pointLongitude = centerLongitude + atan2(
                sin(bearing) * sin(angularDistance) * cos(centerLatitude),
                cos(angularDistance) - sin(centerLatitude) * sin(pointLatitude)
            )

            NearbyCirclePoint(
                latitude = Math.toDegrees(pointLatitude),
                longitude = Math.toDegrees(pointLongitude)
            )
        }
    }

    fun group(
        people: List<NearbyMarkerPerson>,
        radiusMeters: Double = NEARBY_RADIUS_METERS
    ): List<NearbyMarkerGroup> {
        if (people.isEmpty()) return emptyList()

        val visited = BooleanArray(people.size)
        val groups = mutableListOf<NearbyMarkerGroup>()

        people.indices.forEach { startIndex ->
            if (visited[startIndex]) return@forEach

            val component = mutableListOf<Int>()
            val pending = ArrayDeque<Int>().apply { add(startIndex) }
            visited[startIndex] = true

            while (pending.isNotEmpty()) {
                val currentIndex = pending.removeFirst()
                component += currentIndex

                people.indices.forEach { candidateIndex ->
                    if (visited[candidateIndex]) return@forEach

                    if (
                        distanceMeters(
                            people[currentIndex].latitude,
                            people[currentIndex].longitude,
                            people[candidateIndex].latitude,
                            people[candidateIndex].longitude
                        ) <= radiusMeters
                    ) {
                        visited[candidateIndex] = true
                        pending.add(candidateIndex)
                    }
                }
            }

            val groupedPeople = component
                .map(people::get)
                // Stable ordering keeps the generated icon/cache key stable
                // when a WebSocket snapshot changes the input order.
                .sortedBy(NearbyMarkerPerson::id)

            groups += NearbyMarkerGroup(
                people = groupedPeople,
                latitude = groupedPeople.map(NearbyMarkerPerson::latitude).average(),
                longitude = groupedPeople.map(NearbyMarkerPerson::longitude).average()
            )
        }

        return groups
    }

    fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val firstLatitudeRadians = Math.toRadians(firstLatitude)
        val secondLatitudeRadians = Math.toRadians(secondLatitude)
        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)

        val normalizedHaversine = haversine.coerceIn(0.0, 1.0)
        return earthRadiusMeters * 2 * atan2(
            sqrt(normalizedHaversine),
            sqrt(1 - normalizedHaversine)
        )
    }
}
