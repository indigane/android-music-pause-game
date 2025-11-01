package home.musical_chair_statues

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

class TimerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint()
    private val rect = RectF()
    private var sweepAngle = 0f

    init {
        paint.apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = context.resources.getDimension(R.dimen.timer_stroke_width)
            color = ContextCompat.getColor(context, R.color.timer_view_stroke_color)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = min(width, height) - (2 * paint.strokeWidth)
        val left = (width - diameter) / 2
        val top = (height - diameter) / 2
        rect.set(left, top, left + diameter, top + diameter)
        canvas.drawArc(rect, -90f, sweepAngle, false, paint)
    }

    fun setProgress(progress: Float) {
        sweepAngle = 360 * progress
        invalidate()
    }
}
