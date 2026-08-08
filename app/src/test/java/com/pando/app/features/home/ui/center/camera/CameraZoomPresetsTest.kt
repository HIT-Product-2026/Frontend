package com.pando.app.features.home.ui.center.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomPresetsTest {
    @Test
    fun ultraWideCameraIncludesWideLensAroundNormalLens() {
        assertEquals(
            listOf(1f, 0.6f, 1f, 2f, 3f),
            CameraZoomPresets.build(minZoomRatio = 0.6f, maxZoomRatio = 3f)
        )
    }

    @Test
    fun regularCameraDoesNotExposeWideLensStop() {
        assertEquals(
            listOf(1f, 2f, 3f),
            CameraZoomPresets.build(minZoomRatio = 1f, maxZoomRatio = 3f)
        )
    }

    @Test
    fun unsupportedHighZoomStopsAreRemoved() {
        assertEquals(
            listOf(1f, 0.7f, 1f),
            CameraZoomPresets.build(minZoomRatio = 0.7f, maxZoomRatio = 1.5f)
        )
    }

    @Test
    fun nearestIndexKeepsFirstNormalStopForInitialWideLensCycle() {
        val stops = CameraZoomPresets.build(0.6f, 3f)

        assertEquals(0, CameraZoomPresets.nearestIndex(stops, 1f))
        assertEquals(1, CameraZoomPresets.nearestIndex(stops, 0.65f))
        assertEquals(3, CameraZoomPresets.nearestIndex(stops, 2.1f))
    }
}
