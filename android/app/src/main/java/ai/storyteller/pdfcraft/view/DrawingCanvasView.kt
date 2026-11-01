package ai.storyteller.pdfcraft.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Overlay canvas view supporting smooth freehand signature, highlighting, and annotations.
 */
class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class Stroke(val path: Path, val paint: Paint)

    private val strokes = mutableListOf<Stroke>()
    private var currentPath = Path()
    private var currentPaint = createPaint(Color.parseColor("#2563EB"), 6f)

    var currentColor: Int = Color.parseColor("#2563EB")
        set(value) {
            field = value
            currentPaint = createPaint(value, currentStrokeWidth)
        }

    var currentStrokeWidth: Float = 6f
        set(value) {
            field = value
            currentPaint = createPaint(currentColor, value)
        }

    private fun createPaint(color: Int, width: Float): Paint {
        return Paint().apply {
            this.color = color
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = width
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            canvas.drawPath(stroke.path, stroke.paint)
        }
        canvas.drawPath(currentPath, currentPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || visibility != VISIBLE) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath.reset()
                currentPath.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                strokes.add(Stroke(Path(currentPath), Paint(currentPaint)))
                currentPath.reset()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clear() {
        strokes.clear()
        currentPath.reset()
        invalidate()
    }

    fun hasStrokes(): Boolean = strokes.isNotEmpty()
}
