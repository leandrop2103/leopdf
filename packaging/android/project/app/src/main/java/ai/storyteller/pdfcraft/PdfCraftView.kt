package ai.storyteller.pdfcraft

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * An interactive view for viewing rendered PDF pages with pinch-to-zoom and pan support.
 */
class PdfCraftView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pageBitmap: Bitmap? = null
    private val renderMatrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var scaleFactor = 1.0f
    private var posX = 0.0f
    private var posY = 0.0f
    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetZoom()
            return true
        }
    })

    fun setPageBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        resetZoom()
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        posX = 0.0f
        posY = 0.0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = pageBitmap ?: return

        canvas.save()
        // Center the page bitmap within view dimensions
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val fitScale = (viewW / bmpW).coerceAtMost(viewH / bmpH)
        val effectiveScale = fitScale * scaleFactor

        val offsetX = (viewW - bmpW * effectiveScale) / 2.0f + posX
        val offsetY = (viewH - bmpH * effectiveScale) / 2.0f + posY

        renderMatrix.reset()
        renderMatrix.postScale(effectiveScale, effectiveScale)
        renderMatrix.postTranslate(offsetX, offsetY)

        canvas.drawBitmap(bmp, renderMatrix, paint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                lastTouchX = event.getX(pointerIndex)
                lastTouchY = event.getY(pointerIndex)
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)

                    if (!scaleDetector.isInProgress) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        posX += dx
                        posY += dy
                        invalidate()
                    }

                    lastTouchX = x
                    lastTouchY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    lastTouchY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }
        return true
    }
}
