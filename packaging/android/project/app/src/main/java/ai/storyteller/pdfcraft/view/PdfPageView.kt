package ai.storyteller.pdfcraft.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/**
 * High-performance View for displaying a single PDF page inside the continuous scroll reader.
 * Pre-computes and holds exact aspect ratio to guarantee 0 scroll jitter.
 * Features Smart Content Boundary Detection (Automatic margin trimming + 3mm safety margin).
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var originalAspectRatio: Float = 595f / 842f // Default A4 ratio
    private var aspectRatio: Float = 595f / 842f
    private var pageBitmap: Bitmap? = null
    private var contentCropRect: Rect? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint().apply { color = Color.WHITE }
    private var currentMode = PdfReadingView.ReadingMode.NORMAL

    var isCropMarginsEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateEffectiveAspectRatio()
                invalidate()
            }
        }

    fun setAspectRatio(ratio: Float) {
        if (ratio > 0.1f) {
            originalAspectRatio = ratio
            updateEffectiveAspectRatio()
        }
    }

    private fun updateEffectiveAspectRatio() {
        val targetRatio = if (isCropMarginsEnabled && contentCropRect != null) {
            val r = contentCropRect!!
            if (r.height() > 0) r.width().toFloat() / r.height().toFloat() else originalAspectRatio
        } else {
            originalAspectRatio
        }

        if (abs(aspectRatio - targetRatio) > 0.01f && targetRatio > 0.1f) {
            aspectRatio = targetRatio
            requestLayout()
        }
    }

    fun setPageBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap
        if (bitmap != null && !bitmap.isRecycled) {
            contentCropRect = calculateContentBounds(bitmap)
            updateEffectiveAspectRatio()
        } else {
            contentCropRect = null
        }
        invalidate()
    }

    fun setReadingMode(mode: PdfReadingView.ReadingMode) {
        currentMode = mode
        updateColorFilter()
        invalidate()
    }

    private fun updateColorFilter() {
        when (currentMode) {
            PdfReadingView.ReadingMode.NORMAL -> {
                bitmapPaint.colorFilter = null
                bgPaint.color = Color.WHITE
            }
            PdfReadingView.ReadingMode.KINDLE_EINK -> {
                // Pure Kindle E-Ink paper effect: desaturate to monochrome + enhance contrast for crisp letters
                val einkMatrix = ColorMatrix().apply {
                    setSaturation(0f)
                    val contrast = 1.25f
                    val translate = (-0.5f * contrast + 0.5f) * 255f
                    postConcat(ColorMatrix(floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )))
                }
                bitmapPaint.colorFilter = ColorMatrixColorFilter(einkMatrix)
                bgPaint.color = Color.parseColor("#F4F4F0")
            }
            PdfReadingView.ReadingMode.NIGHT -> {
                // True Adobe Acrobat Night Mode:
                // White page (#FFFFFF) becomes pure OLED Black (#000000)
                // Black text (#000000) becomes crisp clean White (#FFFFFF)
                val invertMatrix = ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                bitmapPaint.colorFilter = ColorMatrixColorFilter(invertMatrix)
                bgPaint.color = Color.BLACK
            }
            PdfReadingView.ReadingMode.SEPIA -> {
                val sepiaMatrix = ColorMatrix().apply {
                    setScale(0.95f, 0.90f, 0.78f, 1.0f)
                }
                bitmapPaint.colorFilter = ColorMatrixColorFilter(sepiaMatrix)
                bgPaint.color = Color.parseColor("#FCF6EB")
            }
        }
    }

    /**
     * Scans bitmap pixels to find actual text/ink boundaries and adds a 3mm breathing margin.
     */
    private fun calculateContentBounds(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 10 || h <= 10) return Rect(0, 0, w, h)

        val rowPixels = IntArray(w)
        val step = 4 // Sample every 4th pixel for sub-millisecond execution

        var minX = w
        var maxX = 0
        var minY = h
        var maxY = 0

        for (y in 0 until h step step) {
            bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
            var rowHasInk = false

            for (x in 0 until w step step) {
                val pixel = rowPixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Check if pixel is darker than off-white background (threshold < 235)
                if (r < 235 || g < 235 || b < 235) {
                    rowHasInk = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }

            if (rowHasInk) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        // If page is blank or borders invalid, fallback to full page
        if (minX >= maxX || minY >= maxY) {
            return Rect(0, 0, w, h)
        }

        // ~3mm safety margin (~3.5% of horizontal width, ~2.5% of vertical height)
        val marginX = (w * 0.035f).toInt().coerceAtLeast(8)
        val marginY = (h * 0.025f).toInt().coerceAtLeast(8)

        val left = (minX - marginX).coerceAtLeast(0)
        val right = (maxX + marginX).coerceAtMost(w)
        val top = (minY - marginY).coerceAtLeast(0)
        val bottom = (maxY + marginY).coerceAtMost(h)

        return Rect(left, top, right, bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val calculatedHeight = if (width > 0 && aspectRatio > 0) {
            (width / aspectRatio).toInt()
        } else {
            MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(width, calculatedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val bmp = pageBitmap ?: return
        if (bmp.isRecycled) return

        if (isCropMarginsEnabled && contentCropRect != null) {
            val src = contentCropRect!!
            canvas.drawBitmap(bmp, src, Rect(0, 0, width, height), bitmapPaint)
        } else {
            canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), bitmapPaint)
        }
    }
}
