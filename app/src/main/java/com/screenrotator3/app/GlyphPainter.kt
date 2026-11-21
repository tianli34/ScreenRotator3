package com.screenrotator3.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.PathParser

/**
 * 方向图标的具体画法，供主界面的 [RotationGlyphView] 与悬浮球共用，
 * 保证两处图标语言完全一致。
 *
 * 内部坐标系为 100×100，[size] 决定实际边长，([cx],[cy]) 为几何中心。
 */
class GlyphPainter {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val body = RectF(29f, 9f, 71f, 91f)

    /**
     * 启动图标 ic_launcher.xml 中的 Material screen_rotation 路径（原始 24×24 坐标系）。
     * 自动模式直接复用它，保证应用内与桌面图标使用同一套图案。
     */
    private val autoIconPath: Path = requireNotNull(
        PathParser.createPathFromPathData(
            "M16.48 2.52c3.27 1.55 5.61 4.72 5.97 8.48h1.5C23.44 4.84 18.29 0 12 0l-0.66 0.03 " +
                "3.81 3.81 1.33 -1.32zM10.23 1.75c-0.59 -0.59 -1.54 -0.59 -2.12 0L1.75 8.11c-0.59 0.59 " +
                "-0.59 1.54 0 2.12l12.02 12.02c0.59 0.59 1.54 0.59 2.12 0l6.36 -6.36c0.59 -0.59 " +
                "0.59 -1.54 0 -2.12L10.23 1.75zM14.83 21.19L2.81 9.17l6.36 -6.36 12.02 12.02 -6.36 6.36z" +
                "M7.52 21.48C4.25 19.94 1.91 16.76 1.55 13H0.05C0.56 19.16 5.71 24 12 24l0.66 -0.03 " +
                "-3.81 -3.81 -1.33 1.33z"
        )
    ) { "Invalid screen_rotation path data" }

    fun draw(
        canvas: Canvas,
        mode: RotationMode,
        cx: Float,
        cy: Float,
        size: Float,
        outlineColor: Int,
        anchorColor: Int,
        spin: Float = 0f
    ) {
        val s = size / 100f
        if (s <= 0f) return
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(s, s)
        if (mode.isAuto) {
            drawAuto(canvas, outlineColor)
        } else {
            drawPhone(canvas, mode.glyphRotation + spin, outlineColor, anchorColor)
        }
        canvas.restore()
    }

    private fun drawPhone(canvas: Canvas, degrees: Float, outline: Int, anchor: Int) {
        canvas.save()
        canvas.rotate(degrees, 50f, 50f)

        stroke.color = outline
        stroke.strokeWidth = 5.6f
        canvas.drawRoundRect(body, 10.5f, 10.5f, stroke)

        stroke.strokeWidth = 3.8f
        canvas.drawLine(42.5f, 82.5f, 57.5f, 82.5f, stroke)

        fill.color = anchor
        canvas.drawCircle(50f, 17.5f, 4.2f, fill)

        canvas.restore()
    }

    /**
     * 自动模式：直接使用启动图标 ic_launcher.xml 的 Material screen_rotation 图案。
     *
     * 原始路径是 24×24 坐标系，这里放大 3.5 倍并居中到 100×100 的内部画布，
     * 外框正好是 84×84，与旧版自动图标的弧线外径一致。
     */
    private fun drawAuto(canvas: Canvas, outline: Int) {
        fill.color = outline
        canvas.save()
        canvas.translate(8f, 8f)
        canvas.scale(3.5f, 3.5f)
        canvas.drawPath(autoIconPath, fill)
        canvas.restore()
    }
}
