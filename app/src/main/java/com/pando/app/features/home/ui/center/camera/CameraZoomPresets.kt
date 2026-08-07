package com.pando.app.features.home.ui.center.camera

import kotlin.math.abs

/**
 * Creates the zoom stops exposed by the camera button.
 *
 * A logical multi-camera may expose a ratio below 1.0 for its ultra-wide
 * lens. In that case the repeated 1x entry is intentional: it represents
 * the camera's normal lens before and after switching to the wide lens.
 */
internal object CameraZoomPresets {
    fun build(minZoomRatio: Float, maxZoomRatio: Float): List<Float> {
        val min = minZoomRatio.coerceAtLeast(0f)
        val max = maxZoomRatio.coerceAtLeast(min)
        val normalRatio = 1f.coerceIn(min, max)
        val stops = mutableListOf(normalRatio)

        if (min < 1f && max >= 1f) {
            stops += min
            stops += normalRatio
        }

        listOf(2f, 3f).forEach { ratio ->
            if (ratio in min..max) {
                stops += ratio
            }
        }

        return stops
    }

    fun nearestIndex(stops: List<Float>, ratio: Float): Int {
        if (stops.isEmpty()) return 0

        return stops.indices.minByOrNull { index ->
            abs(stops[index] - ratio)
        } ?: 0
    }
}
