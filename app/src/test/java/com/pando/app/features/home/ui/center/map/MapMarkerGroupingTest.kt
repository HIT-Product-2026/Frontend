package com.pando.app.features.home.ui.center.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMarkerGroupingTest {
    @Test
    fun onePersonStaysAsStandaloneMarker() {
        val groups = MapMarkerGrouping.group(listOf(person("a", 21.0, 105.0)))

        assertEquals(1, groups.size)
        assertEquals(1, groups.single().people.size)
        assertFalse(groups.single().isGroup)
    }

    @Test
    fun peopleWithinThirtyMetersBecomeNearbyGroup() {
        val groups = MapMarkerGrouping.group(
            listOf(
                person("a", 21.0, 105.0),
                person("b", 21.00015, 105.0)
            )
        )

        assertEquals(1, groups.size)
        assertTrue(groups.single().isGroup)
        assertEquals(2, groups.single().people.size)
    }

    @Test
    fun connectedComponentCanContainThreePeopleIncludingCurrentUser() {
        val groups = MapMarkerGrouping.group(
            listOf(
                person("friend-a", 21.0, 105.0),
                person("friend-b", 21.0002, 105.0),
                person("current", 21.0004, 105.0, isCurrentUser = true)
            )
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().people.size)
        assertTrue(groups.single().containsCurrentUser)
    }

    @Test
    fun peopleOutsideThirtyMetersStaySeparate() {
        val groups = MapMarkerGrouping.group(
            listOf(
                person("a", 21.0, 105.0),
                person("b", 21.0004, 105.0)
            )
        )

        assertEquals(2, groups.size)
        assertTrue(groups.all { it.people.size == 1 })
    }

    @Test
    fun clusterLabelCountsPeopleInsteadOfProxyIcons() {
        assertEquals("+1", MapMarkerGrouping.clusterLabel(listOf(1, 1)))
        assertEquals("+2", MapMarkerGrouping.clusterLabel(listOf(2, 1)))
        assertEquals("+2", MapMarkerGrouping.clusterLabelForPersonCount(3))
        assertEquals(null, MapMarkerGrouping.clusterLabel(listOf(3)))
    }

    private fun person(
        id: String,
        latitude: Double,
        longitude: Double,
        isCurrentUser: Boolean = false
    ) = NearbyMarkerPerson(
        id = id,
        name = id,
        avatarUrl = null,
        latitude = latitude,
        longitude = longitude,
        isCurrentUser = isCurrentUser
    )

}
