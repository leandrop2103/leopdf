package ai.storyteller.pdfcraft.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * A RecyclerView that smoothly supports pinch-to-zoom (1.0x to 4.0x),
 * animated double-tap zoom (fitting text comfortably to the screen width),
 * horizontal panning when zoomed, and left-edge vertical brightness gestures.
 */
class ZoomableRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var scaleFactor = 1.0f
    private var translationXVal = 0.0f

    private var lastTouchX = 0.0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // Brightness gesture state (left 15% edge)
    private var isBrightnessGestureActive = false
    private var gestureStartY = 0f

    private var zoomAnimator: ValueAnimator? = null

    var onSingleTapListener: (() -> Unit)? = null
    var onBrightnessDeltaListener: ((deltaFraction: Float) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previousScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(1.0f, 4.0f)

            if (scaleFactor <= 1.02f) {
                scaleFactor = 1.0f
                translationXVal = 0f
            } else {
                val focusX = detector.focusX
                val ratio = scaleFactor / previousScale
                translationXVal = focusX - (focusX - translationXVal) * ratio
                clampTranslation()
            }

            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onSingleTapListener?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scaleFactor > 1.25f) {
                // Reset zoom to normal 1.0x (full page width)
                animateZoom(1.0f, width / 2.0f)
            } else {
                // Zoom in 2.2x to fit text column to screen width
                animateZoom(2.2f, e.x)
            }
            return true
        }
    })

    /**
     * Smoothly animates zoom to target scale, focusing on focusX coordinate.
     */
    fun animateZoom(targetScale: Float, focusX: Float) {
        zoomAnimator?.cancel()

        val startScale = scaleFactor
        val startTransX = translationXVal

        val maxTargetTransX = (width * (targetScale - 1.0f)) / 2.0f
        val targetTransX = if (targetScale <= 1.05f) {
            0.0f
        } else {
            ((width / 2.0f - focusX) * 1.3f).coerceIn(-maxTargetTransX, maxTargetTransX)
        }

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                scaleFactor = startScale + (targetScale - startScale) * fraction
                translationXVal = startTransX + (targetTransX - startTransX) * fraction
                clampTranslation()
                invalidate()
            }
            start()
        }
    }

    fun resetZoom() {
        animateZoom(1.0f, width / 2.0f)
    }

    private fun clampTranslation() {
        if (scaleFactor <= 1.01f) {
            translationXVal = 0f
            return
        }
        val maxTransX = (width * (scaleFactor - 1.0f)) / 2.0f
        translationXVal = translationXVal.coerceIn(-maxTransX, maxTransX)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        if (scaleFactor > 1.0f) {
            canvas.translate(translationXVal, 0f)
            canvas.scale(scaleFactor, scaleFactor, width / 2.0f, height / 2.0f)
        }
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val startX = ev.x
                gestureStartY = ev.y
                if (scaleFactor <= 1.05f && startX < width * 0.15f) {
                    isBrightnessGestureActive = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isBrightnessGestureActive && !scaleDetector.isInProgress) {
                    val currentY = ev.y
                    val deltaY = gestureStartY - currentY
                    if (abs(deltaY) > 8f) {
                        val deltaFraction = deltaY / (height.toFloat() * 0.7f)
                        onBrightnessDeltaListener?.invoke(deltaFraction)
                        gestureStartY = currentY
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isBrightnessGestureActive = false
            }
        }

        // Horizontal panning when zoomed in
        if (scaleFactor > 1.05f) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val idx = ev.actionIndex
                    lastTouchX = ev.getX(idx)
                    activePointerId = ev.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    val pIndex = ev.findPointerIndex(activePointerId)
                    if (pIndex != -1 && !scaleDetector.isInProgress) {
                        val x = ev.getX(pIndex)
                        val dx = x - lastTouchX

                        if (abs(dx) > 2f) {
                            translationXVal += dx
                            clampTranslation()
                            invalidate()
                        }

                        lastTouchX = x
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }
}
