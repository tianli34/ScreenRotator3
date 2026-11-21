package com.screenrotator3.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 磁贴悬浮窗里的一行方向选项。
 *
 * 与主界面 [ModeCardView] 表达同一件事：图标的朝向、摄像头锚点、选中配色一致，
 * 只是从「卡片」收窄成「列表行」——弹出窗要在半屏内放下五个选项，卡片网格太高。
 *
 * 选中态不是换一张静态图，而是三处同时变：整行底色染成主题色淡层、图标块填充主题色、
 * 文字转成主题色，右侧再补一个勾。这样即使色觉不敏感，也能靠勾选标记确认当前方向。
 */
class ModeMenuRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val iconTile: FrameLayout
    private val glyph: RotationGlyphView
    private val titleView: TextView
    private val taglineView: TextView
    private val checkView: ImageView
    private val tileDrawable = GradientDrawable()

    private val cAccent = context.getColor(R.color.accent)
    private val cOnAccent = context.getColor(R.color.on_accent)
    private val cTextPrimary = context.getColor(R.color.text_primary)
    private val cTextSecondary = context.getColor(R.color.text_secondary)
    private val cAccentSoft = context.getColor(R.color.accent_soft)

    var mode: RotationMode = RotationMode.AUTO
        private set

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), context.dp(8), context.dp(10), context.dp(8))
        minimumHeight = context.dp(58)
        isClickable = true
        isFocusable = true

        LayoutInflater.from(context).inflate(R.layout.view_mode_menu_row, this, true)

        iconTile = findViewById(R.id.iconTile)
        glyph = findViewById(R.id.glyph)
        titleView = findViewById(R.id.title)
        taglineView = findViewById(R.id.tagline)
        checkView = findViewById(R.id.check)

        tileDrawable.shape = GradientDrawable.RECTANGLE
        tileDrawable.cornerRadius = context.dp(13).toFloat()
        iconTile.background = tileDrawable
    }

    /**
     * 横屏时的紧凑形态：收起副标题、压低行高。
     *
     * 横屏可用高度只有 ~393dp，五行带副标题要 380dp 以上，最后一行会被推到折叠线以下，
     * 用户得先滚动才知道还有「反向横屏」。收起朝向说明后 315dp 就能全部露出 ——
     * 图标本身已经用摄像头锚点表达了朝向，副标题在这个尺寸下是冗余的。
     */
    fun useCompact(compact: Boolean) {
        taglineView.visibility = if (compact) View.GONE else View.VISIBLE
        minimumHeight = context.dp(if (compact) 48 else 58)
        val vertical = context.dp(if (compact) 5 else 8)
        setPadding(context.dp(8), vertical, context.dp(10), vertical)

        val tileSize = context.dp(if (compact) 34 else 38)
        val tileParams = iconTile.layoutParams
        tileParams.width = tileSize
        tileParams.height = tileSize
        iconTile.layoutParams = tileParams
    }

    /** 绑定方向与选中态；[active] 表示这一行就是 [RotationPrefs] 里记着的那一次选择。 */
    fun bind(mode: RotationMode, active: Boolean) {
        this.mode = mode
        this.isSelected = active

        glyph.mode = mode
        titleView.text = context.getString(mode.labelRes)
        taglineView.text = context.getString(mode.taglineRes)

        val label = context.getString(mode.labelRes)
        contentDescription = if (active) {
            context.getString(R.string.tile_menu_row_selected, label)
        } else {
            label
        }

        background = rowBackground(active)
        tileDrawable.setColor(if (active) cAccent else cAccentSoft)
        glyph.outlineColor = if (active) cOnAccent else cTextSecondary
        glyph.anchorColor = if (active) cOnAccent else cAccent
        titleView.setTextColor(if (active) cAccent else cTextPrimary)
        taglineView.setTextColor(cTextSecondary)
        checkView.visibility = if (active) View.VISIBLE else View.GONE
    }

    /**
     * 行底色：未选中是纯水波纹（面板本身已有 surface 底），选中是一层主题色淡底 + 细描边。
     * 两种情况都套 RippleDrawable，保证选中行同样有按下的触感反馈。
     */
    private fun rowBackground(active: Boolean): RippleDrawable {
        val content = if (active) {
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(16).toFloat()
                setColor(cAccentSoft)
                setStroke(
                    context.dp(1),
                    Color.argb(
                        0x59,
                        Color.red(cAccent),
                        Color.green(cAccent),
                        Color.blue(cAccent)
                    )
                )
            }
        } else {
            null
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(16).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(cAccentSoft), content, mask)
    }
}
