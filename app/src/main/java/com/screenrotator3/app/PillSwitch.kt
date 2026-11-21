package com.screenrotator3.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/** 极简胶囊开关，跟随主题色，点击带触感反馈。 */
class PillSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var checked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateTo(value)
        }

    var onCheckedChange: ((Boolean) -> Unit)? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val trackOff = context.getColor(R.color.switch_track)
    private val trackOn = context.getColor(R.color.accent)

    private var fraction = 0f
    private var animator: ValueAnimator? = null

    private val trackHeight = context.dp(26).toFloat()
    private val widthPx = context.dp(46).toFloat()
    private val thumbRadius = context.dp(10).toFloat()

    init {
        isClickable = true
        isFocusable = true
    }

    private fun animateTo(value: Boolean) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(fraction, if (value) 1f else 0f).apply {
            duration = 170
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthPx.toInt(), trackHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = trackHeight
        val r = h / 2f
        trackPaint.color = blendColor(trackOff, trackOn, fraction)
        canvas.drawRoundRect(0f, 0f, widthPx, h, r, r, trackPaint)

        val travel = widthPx - h
        val cx = r + travel * fraction
        canvas.drawCircle(cx, r, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (isEnabled) {
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    checked = !checked
                    onCheckedChange?.invoke(checked)
                    performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
