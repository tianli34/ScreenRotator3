package com.screenrotator3.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.min

/**
 * 悬浮球本体：收起时是一个半透明小圆点，点开后原地展开成「罗盘式」五向控制盘。
 *
 * 方位与主界面方案 B 一致：
 * 上=竖屏，下=反向竖屏，左=横屏，右=反向横屏，中心=自动。
 * 圆心即「自动」，因为它是离开锁定的出口，顺手就能点到。
 *
 * 展开/收起的动画由 [FloatingBallController] 驱动：它同时改窗口矩形和这里的
 * [fraction]，所以内容和窗口永远同一个进度，不需要再让视图自己跑一条动画。
 */
class FloatingBallView(context: Context) : View(context) {

    interface Callbacks {
        fun onTap()
        fun onDragBy(dx: Float, dy: Float)
        fun onDragEnd()
        fun onSelect(mode: RotationMode)
    }

    var callbacks: Callbacks? = null
    var mode: RotationMode = RotationMode.AUTO
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val painter = GlyphPainter()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val accent = context.getColor(R.color.accent)
    private val ballRadius = context.dp(27).toFloat()
    private val buttonRadius = context.dp(24).toFloat()
    private val offset = context.dp(54).toFloat()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * 展开进度：0 = 收起成球，1 = 完全展开。
     *
     * 由 [FloatingBallController] 逐帧写进来，和窗口边长的缩放共用一条曲线；
     * 视图自己不再持有动画，避免两份进度各自跑偏。
     */
    var fraction: Float = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (field == next) return
            field = next
            invalidate()
        }

    /** 是否按「展开」处理触摸（面板可点）。与 [fraction] 分开，展开动画一开始就能响应。 */
    var expanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            pressed = null
            invalidate()
        }

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private var pressed: RotationMode? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val f = fraction

        if (f > 0.02f) {
            val panelRadius = min(width, height) / 2f - context.dp(2)
            bgPaint.color = Color.argb((0xE6 * f).toInt(), 0x17, 0x19, 0x20)
            canvas.drawCircle(cx, cy, panelRadius, bgPaint)
            strokePaint.color = Color.argb((0x24 * f).toInt(), 0xFF, 0xFF, 0xFF)
            strokePaint.strokeWidth = context.dp(1).toFloat()
            canvas.drawCircle(cx, cy, panelRadius, strokePaint)

            for (target in DIRECTIONAL) {
                val x = cx + target.dx * offset * f
                val y = cy + target.dy * offset * f
                drawButton(canvas, target.mode, x, y, f)
                pressed?.let { if (it == target.mode) drawRing(canvas, x, y, f) }
            }
        }

        // 中心球：收起时显示当前方向，展开后变形为「自动」按钮
        val centerMode = if (f > 0.15f) RotationMode.AUTO else mode
        val radius = ballRadius + (buttonRadius - ballRadius) * f
        val active = centerMode == this.mode
        if (active && f > 0.3f) {
            bgPaint.color = Color.argb((0xFF * f).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            bgPaint.color = Color.argb(0xE6, 0x1A, 0x1D, 0x25)
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)
        strokePaint.color = if (active) Color.argb((0x66 * f + 0x33).toInt(), 0xFF, 0xFF, 0xFF)
        else Color.argb(0x40, 0xFF, 0xFF, 0xFF)
        strokePaint.strokeWidth = context.dp(1).toFloat()
        canvas.drawCircle(cx, cy, radius, strokePaint)

        painter.draw(
            canvas, centerMode, cx, cy,
            context.dp(22).toFloat() * (0.85f + 0.15f * f),
            Color.WHITE,
            if (active && f > 0.3f) Color.WHITE else accent
        )

        if (pressed == RotationMode.AUTO) drawRing(canvas, cx, cy, f)
    }

    private fun drawButton(canvas: Canvas, mode: RotationMode, x: Float, y: Float, f: Float) {
        val active = mode == this.mode
        val radius = buttonRadius * (0.55f + 0.45f * f)
        bgPaint.color = if (active) {
            Color.argb((0xF2 * f).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            Color.argb((0x22 * f).toInt(), 0xFF, 0xFF, 0xFF)
        }
        canvas.drawCircle(x, y, radius, bgPaint)
        painter.draw(
            canvas, mode, x, y,
            context.dp(21).toFloat() * (0.7f + 0.3f * f),
            Color.argb((0xE6 * f).toInt(), 0xFF, 0xFF, 0xFF),
            Color.argb((0xFF * f).toInt(), 0xFF, 0xFF, 0xFF)
        )
    }

    private fun drawRing(canvas: Canvas, x: Float, y: Float, f: Float) {
        strokePaint.color = Color.argb((0x99 * f).toInt(), 0xFF, 0xFF, 0xFF)
        strokePaint.strokeWidth = context.dp(2).toFloat()
        canvas.drawCircle(x, y, (buttonRadius + context.dp(4)) * (0.55f + 0.45f * f), strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!expanded) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                    if (dragging) {
                        callbacks?.onDragBy(event.rawX - lastRawX, event.rawY - lastRawY)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) callbacks?.onDragEnd() else callbacks?.onTap()
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) callbacks?.onDragEnd()
                }
            }
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = hitTest(event.x, event.y)
                    invalidate()
                }

                MotionEvent.ACTION_MOVE -> {
                    val current = hitTest(event.x, event.y)
                    if (current != pressed) {
                        pressed = current
                        invalidate()
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val target = hitTest(event.x, event.y)
                    if (target != null && target == pressed) callbacks?.onSelect(target)
                    pressed = null
                    invalidate()
                }

                MotionEvent.ACTION_CANCEL -> {
                    pressed = null
                    invalidate()
                }
            }
        }
        return true
    }

    /**
     * 命中判定跟着绘制位置走：按钮半径和「离圆心多远」都随 [fraction] 缩放。
     * 窗口现在会跟着动画一起缩放，判定如果不缩放，动画中途就会点到看不见的位置上。
     */
    private fun hitTest(x: Float, y: Float): RotationMode? {
        val cx = width / 2f
        val cy = height / 2f
        val f = fraction
        val reach = buttonRadius * (0.55f + 0.45f * f) * 1.2f
        if (hypot(x - cx, y - cy) <= reach) return RotationMode.AUTO
        val distance = offset * f
        var best: RotationMode? = null
        var bestDistance = Float.MAX_VALUE
        for (target in DIRECTIONAL) {
            val d = hypot(x - (cx + target.dx * distance), y - (cy + target.dy * distance))
            if (d <= reach && d < bestDistance) {
                best = target.mode
                bestDistance = d
            }
        }
        return best
    }

    private data class Target(val mode: RotationMode, val dx: Float, val dy: Float)

    private companion object {
        val DIRECTIONAL = listOf(
            Target(RotationMode.PORTRAIT, 0f, -1f),
            Target(RotationMode.REVERSE_PORTRAIT, 0f, 1f),
            Target(RotationMode.LANDSCAPE, -1f, 0f),
            Target(RotationMode.REVERSE_LANDSCAPE, 1f, 0f)
        )
    }
}
