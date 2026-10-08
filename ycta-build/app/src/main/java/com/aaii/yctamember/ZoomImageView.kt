package com.aaii.yctamember

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : android.widget.ImageView(context, attrs, defStyleAttr) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    private val drawMatrix = Matrix()
    private var minScale = 1f
    private var maxScale = 4f
    private var scale = 1f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val old = scale
            scale = (scale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = scale / old
            drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = drawMatrix
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (scale > 1.15f) minScale else 2f
            zoomTo(target, e.x, e.y)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || scale > 1.08f) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy) && abs(dx) > width * 0.18f && abs(velocityX) > 450) {
                if (dx < 0) onSwipeLeft?.invoke() else onSwipeRight?.invoke()
                return true
            }
            return false
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetZoom() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = scale > 1.01f
            }
            MotionEvent.ACTION_MOVE -> if (dragging && !scaleDetector.isInProgress) {
                val dx = event.x - lastX
                val dy = event.y - lastY
                drawMatrix.postTranslate(dx, dy)
                fixTranslation()
                imageMatrix = drawMatrix
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    fun resetZoom() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        drawMatrix.reset()
        val sx = width.toFloat() / d.intrinsicWidth
        val sy = height.toFloat() / d.intrinsicHeight
        val fit = min(sx, sy)
        val dx = (width - d.intrinsicWidth * fit) / 2f
        val dy = (height - d.intrinsicHeight * fit) / 2f
        drawMatrix.postScale(fit, fit)
        drawMatrix.postTranslate(dx, dy)
        minScale = 1f
        scale = 1f
        imageMatrix = drawMatrix
    }

    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val newScale = target.coerceIn(minScale, maxScale)
        val factor = newScale / scale
        scale = newScale
        drawMatrix.postScale(factor, factor, focusX, focusY)
        fixTranslation()
        imageMatrix = drawMatrix
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        val values = FloatArray(9)
        drawMatrix.getValues(values)
        val baseW = d.intrinsicWidth * values[Matrix.MSCALE_X]
        val baseH = d.intrinsicHeight * values[Matrix.MSCALE_Y]
        val tx = values[Matrix.MTRANS_X]
        val ty = values[Matrix.MTRANS_Y]

        val minX = if (baseW > width) width - baseW else (width - baseW) / 2f
        val maxX = if (baseW > width) 0f else minX
        val minY = if (baseH > height) height - baseH else (height - baseH) / 2f
        val maxY = if (baseH > height) 0f else minY
        val fixedX = tx.coerceIn(minX, maxX)
        val fixedY = ty.coerceIn(minY, maxY)
        drawMatrix.postTranslate(fixedX - tx, fixedY - ty)
    }
}
