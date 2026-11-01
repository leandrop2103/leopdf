package ai.storyteller.pdfcraft.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * High-performance, touch-responsive View for PDF page viewing with Reading Modes (Normal, Night, Sepia),
 * pinch-to-zoom, pan, and double-tap zoom.
 */
class PdfReadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ReadingMode {
        NORMAL,
        KINDLE_EINK,
        NIGHT,
        SEPIA
    }

    private var currentMode = ReadingMode.NORMAL
    private var pageBitmap: Bitmap? = null

    private val renderMatrix = Matrix()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()

    // Zoom & pan parameters
    private var scaleFactor = 1.0f
    private var translationX = 0.0f
    private var translationY = 0.0f

    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    var onSingleTapListener: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previousScale = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.8f, 5.0f)

            // Zoom centered on pinch focus point
            val focusX = detector.focusX
            val focusY = detector.focusY
            val scaleRatio = scaleFactor / previousScale
            translationX = focusX - (focusX - translationX) * scaleRatio
            translationY = focusY - (focusY - translationY) * scaleRatio

            clampTranslation()
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
            if (scaleFactor > 1.2f) {
                resetZoom()
            } else {
                // Smart zoom to reading width (2.5x) focused on tap location
                scaleFactor = 2.5f
                val focusX = e.x
                val focusY = e.y
                translationX = width / 2.0f - (focusX - translationX) * 2.5f
                translationY = height / 2.0f - (focusY - translationY) * 2.5f
                clampTranslation()
                invalidate()
            }
            return true
        }
    })

    init {
        updateReadingModeFilter()
    }

    fun setPageBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        resetZoom()
    }

    fun setReadingMode(mode: ReadingMode) {
        currentMode = mode
        updateReadingModeFilter()
        invalidate()
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        translationX = 0.0f
        translationY = 0.0f
        invalidate()
    }

    private fun updateReadingModeFilter() {
        when (currentMode) {
            ReadingMode.NORMAL -> {
                bitmapPaint.colorFilter = null
                bgPaint.color = Color.parseColor("#F1F5F9")
            }
            ReadingMode.NIGHT -> {
                // Adobe-style true pitch-black night mode
                val invertMatrix = ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
                bitmapPaint.colorFilter = ColorMatrixColorFilter(invertMatrix)
                bgPaint.color = Color.BLACK
            }
            ReadingMode.KINDLE_EINK -> {
                // Kindle E-Ink: Pure monochrome desaturation with slight paper contrast
                val satMatrix = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 1.15f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                val contrastMatrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                contrastMatrix.preConcat(satMatrix)
                bitmapPaint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
                bgPaint.color = Color.parseColor("#F4F4F0")
            }
            ReadingMode.SEPIA -> {
                // Warm sepia tint
                val sepiaMatrix = ColorMatrix().apply {
                    setScale(0.95f, 0.90f, 0.78f, 1.0f)
                }
                bitmapPaint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
                bgPaint.color = Color.parseColor("#F5EAD4")
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val bmp = pageBitmap ?: return
        if (bmp.isRecycled) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        if (viewW <= 0f || viewH <= 0f || bmpW <= 0f || bmpH <= 0f) return

        val baseFitScale = (viewW / bmpW).coerceAtMost(viewH / bmpH)
        val finalScale = baseFitScale * scaleFactor

        val baseOffsetX = (viewW - bmpW * finalScale) / 2.0f
        val baseOffsetY = (viewH - bmpH * finalScale) / 2.0f

        canvas.save()
        renderMatrix.reset()
        renderMatrix.postScale(finalScale, finalScale)
        renderMatrix.postTranslate(baseOffsetX + translationX, baseOffsetY + translationY)

        canvas.drawBitmap(bmp, renderMatrix, bitmapPaint)
        canvas.restore()
    }

    private fun clampTranslation() {
        val bmp = pageBitmap ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val baseFitScale = (viewW / bmpW).coerceAtMost(viewH / bmpH)
        val finalScale = baseFitScale * scaleFactor

        val renderedW = bmpW * finalScale
        val renderedH = bmpH * finalScale

        val maxTranslateX = (renderedW - viewW).coerceAtLeast(0f) / 2.0f + 200f
        val maxTranslateY = (renderedH - viewH).coerceAtLeast(0f) / 2.0f + 200f

        translationX = translationX.coerceIn(-maxTranslateX, maxTranslateX)
        translationY = translationY.coerceIn(-maxTranslateY, maxTranslateY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = event.actionIndex
                lastTouchX = event.getX(index)
                lastTouchY = event.getY(index)
                activePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex != -1 && !scaleDetector.isInProgress) {
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)

                    val dx = x - lastTouchX
                    val dy = y - lastTouchY

                    translationX += dx
                    translationY += dy
                    clampTranslation()
                    invalidate()

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
