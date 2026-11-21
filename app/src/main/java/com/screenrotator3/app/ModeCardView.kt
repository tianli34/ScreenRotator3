package com.screenrotator3.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

/**
 * Bento 方向卡片。
 *
 * 三种形态共用一套状态绘制：
 * - 窄卡（2×2 网格）：图标在上、文案在下。
 * - 宽卡（竖屏下的自动）：图标块在左、文案居中、右侧勾选指示。
 * - 竖条（横屏下的自动）：纵向排布、撑满整列高度，图标更大，勾选退到右上角。
 *
 * 选中态不是简单的“换背景”，而是整卡填充主题色、图标与文字同步转白、
 * 并配合一层同色阴影做轻微发光，保证夜里一眼能看出当前锁在哪个方向。
 */
class ModeCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val wide: Boolean
    private val tall: Boolean
    private lateinit var glyph: RotationGlyphView
    private lateinit var titleView: TextView
    private lateinit var taglineView: TextView
    private var checkView: ImageView? = null
    private var glyphTile: FrameLayout? = null
    private var tileDrawable: GradientDrawable? = null

    private val cardBackground = GradientDrawable()
    private val cSurface = context.getColor(R.color.surface)
    private val cStroke = context.getColor(R.color.stroke)
    private val cAccent = context.getColor(R.color.accent)
    private val cOnAccent = context.getColor(R.color.on_accent)
    private val cOnAccentSecondary = context.getColor(R.color.on_accent_secondary)
    private val cTextPrimary = context.getColor(R.color.text_primary)
    private val cTextSecondary = context.getColor(R.color.text_secondary)
    private val cTextTertiary = context.getColor(R.color.text_tertiary)

    private var mode: RotationMode = RotationMode.AUTO
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var everBound = false

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ModeCardView)
        wide = ta.getBoolean(R.styleable.ModeCardView_cardWide, false)
        tall = !wide && ta.getBoolean(R.styleable.ModeCardView_cardTall, false)
        ta.recycle()

        cardBackground.shape = GradientDrawable.RECTANGLE
        cardBackground.cornerRadius =
            context.dp(if (wide) 24 else if (tall) 26 else 22).toFloat()
        background = cardBackground

        outlineAmbientShadowColor = cAccent
        outlineSpotShadowColor = cAccent

        when {
            wide -> {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(16), context.dp(14), context.dp(14), context.dp(14))
                minimumHeight = context.dp(78)
                LayoutInflater.from(context).inflate(R.layout.view_mode_card_wide, this, true)
                checkView = findViewById<ImageView>(R.id.check)
                glyphTile = findViewById<FrameLayout>(R.id.glyphTile).also { tile ->
                    tileDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = context.dp(14).toFloat()
                    }
                    tile.background = tileDrawable
                }
            }

            tall -> {
                // 高度由父布局的权重给出（这一列和右侧网格等高），所以这里不设 minimumHeight
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(14), context.dp(16), context.dp(14), context.dp(16))
                minimumHeight = 0
                LayoutInflater.from(context).inflate(R.layout.view_mode_card_tall, this, true)
                checkView = findViewById(R.id.check)
            }

            else -> {
                orientation = VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(context.dp(10), context.dp(16), context.dp(10), context.dp(14))
                minimumHeight = context.dp(120)
                LayoutInflater.from(context).inflate(R.layout.view_mode_card, this, true)
            }
        }

        glyph = findViewById(R.id.glyph)
        titleView = findViewById(R.id.title)
        taglineView = findViewById(R.id.tagline)

        render()
    }

    fun bind(mode: RotationMode) {
        this.mode = mode
        glyph.mode = mode
        titleView.text = context.getString(mode.labelRes)
        taglineView.text = context.getString(
            when {
                // 宽卡 / 竖条这两种「自动」形态横向或纵向空间都紧，说明文案另外写过
                wide -> R.string.tagline_auto_wide
                tall && mode.isAuto -> R.string.tagline_auto_tall
                else -> mode.taglineRes
            }
        )
        contentDescription = context.getString(mode.labelRes)
    }

    fun kick() = glyph.kick()

    fun setActive(target: Boolean, animate: Boolean = true) {
        val targetProgress = if (target) 1f else 0f
        if (everBound && progress == targetProgress) return
        everBound = true
        animator?.cancel()
        if (!animate) {
            progress = targetProgress
            render()
            return
        }
        animator = ValueAnimator.ofFloat(progress, targetProgress).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                render()
            }
            start()
        }
    }

    private fun render() {
        val p = progress
        cardBackground.setColor(blendColor(cSurface, cAccent, p))
        cardBackground.setStroke(context.dp(1), blendColor(cStroke, cAccent, p))

        titleView.setTextColor(blendColor(cTextPrimary, cOnAccent, p))
        taglineView.setTextColor(blendColor(cTextSecondary, cOnAccentSecondary, p))
        glyph.outlineColor = blendColor(cTextSecondary, cOnAccent, p)
        glyph.anchorColor = blendColor(cAccent, cOnAccent, p)

        tileDrawable?.let { tile ->
            val idle = blendColor(cSurface, cAccent, 0.16f)
            val selected = blendColor(cAccent, Color.WHITE, 0.24f)
            tile.setColor(blendColor(idle, selected, p))
        }

        checkView?.let { check ->
            check.setImageResource(
                if (p > 0.5f) R.drawable.ic_check_circle else R.drawable.ic_circle_outline
            )
            check.imageTintList =
                ColorStateList.valueOf(blendColor(cTextTertiary, cOnAccent, p))
        }

        elevation = context.dp(10).toFloat() * p
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate()
                .scaleX(0.965f).scaleY(0.965f).setDuration(90).start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate()
                .scaleX(1f).scaleY(1f).setDuration(200).start()
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    /** 供无障碍服务读取当前是否选中。 */
    override fun getAccessibilityClassName(): CharSequence = ModeCardView::class.java.name
}
