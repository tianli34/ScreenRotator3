package com.screenrotator3.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

/**
 * 屏幕方向图标。
 *
 * 设计要点是“方向锚点”：每个图标都是一台带摄像头圆点 + 底部 Home 条的手机，
 * 只是整机旋转角度不同。摄像头圆点默认用主题色高亮，于是：
 *
 * - 竖屏：圆点在顶部
 * - 反向竖屏：圆点在底部
 * - 横屏：圆点在左侧（逆时针 90°）
 * - 反向横屏：圆点在右侧
 *
 * 具体画法见 [GlyphPainter]，与悬浮球保持同一套图形语言。
 */
class RotationGlyphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var mode: RotationMode = RotationMode.AUTO
        set(value) {
            if (field != value) {
                field = value
                spin = 0f
                invalidate()
            }
        }

    /** 线条颜色（机身轮廓、Home 条、自动模式的旋转箭头）。 */
    var outlineColor: Int = Color.GRAY
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 方向锚点（摄像头圆点）的颜色。 */
    var anchorColor: Int = Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val painter = GlyphPainter()
    private var spin = 0f
    private var spinAnimator: ValueAnimator? = null

    /** 点击时的“小旋转”过渡：从目标角度偏回来一点，再弹回去。 */
    fun kick() {
        if (mode.isAuto) return
        spinAnimator?.cancel()
        spinAnimator = ValueAnimator.ofFloat(-20f, 0f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener {
                spin = it.animatedValue as Float
                invalidate()
            }
        }
        spinAnimator?.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        painter.draw(
            canvas, mode,
            width / 2f, height / 2f, size,
            outlineColor, anchorColor, spin
        )
    }

    override fun onDetachedFromWindow() {
        spinAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
