package com.pando.app.features.home.ui.center.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.intuit.sdp.R as SdpR
import com.pando.app.R

/**
 * Viền quay video vơi dần theo chiều kim đồng hồ, tiến độ được đồng bộ trực tiếp
 * từ recordedDurationNanos của VideoRecordEvent thay vì một timer riêng.
 */
class RecordingBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val borderWidth = resources.getDimension(SdpR.dimen._4sdp)
    private val cornerRadius = resources.getDimension(SdpR.dimen._24sdp)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.accent5)
    }

    private val fullPath = Path()
    private val visiblePath = Path()
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f

    private var progress = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val inset = borderWidth / 2f
        val rect = RectF(inset, inset, w - inset, h - inset)
        val radius = cornerRadius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f)

        fullPath.reset()
        fullPath.moveTo(rect.left + radius, rect.top)
        fullPath.lineTo(rect.right - radius, rect.top)
        fullPath.arcTo(
            RectF(rect.right - 2f * radius, rect.top, rect.right, rect.top + 2f * radius),
            -90f,
            90f
        )
        fullPath.lineTo(rect.right, rect.bottom - radius)
        fullPath.arcTo(
            RectF(rect.right - 2f * radius, rect.bottom - 2f * radius, rect.right, rect.bottom),
            0f,
            90f
        )
        fullPath.lineTo(rect.left + radius, rect.bottom)
        fullPath.arcTo(
            RectF(rect.left, rect.bottom - 2f * radius, rect.left + 2f * radius, rect.bottom),
            90f,
            90f
        )
        fullPath.lineTo(rect.left, rect.top + radius)
        fullPath.arcTo(
            RectF(rect.left, rect.top, rect.left + 2f * radius, rect.top + 2f * radius),
            180f,
            90f
        )
        fullPath.close()

        pathMeasure.setPath(fullPath, false)
        pathLength = pathMeasure.length
    }

    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped == progress) return

        progress = clamped
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pathLength <= 0f || progress >= 1f) return

        visiblePath.reset()
        pathMeasure.getSegment(progress * pathLength, pathLength, visiblePath, true)
        canvas.drawPath(visiblePath, paint)
    }
}
