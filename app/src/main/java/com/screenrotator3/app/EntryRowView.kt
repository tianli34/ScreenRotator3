package com.screenrotator3.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes

/**
 * 「快捷入口」列表行：左侧图标块 + 标题/说明 + 右侧（开关 / 文字按钮 / 箭头）。
 * 三选一由 [useSwitch] / [useAction] / [useChevron] 决定。
 */
@SuppressLint("ClickableViewAccessibility")
class EntryRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var iconTile: FrameLayout
    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var actionView: TextView
    private lateinit var chevronView: ImageView
    private lateinit var switchView: PillSwitch

    /** 当前标题：开关的无障碍描述跟着它走，切语言后不能还是旧文案。 */
    private var titleText: CharSequence = ""

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        minimumHeight = context.dp(66)
        background = context.getDrawable(R.drawable.bg_row)

        LayoutInflater.from(context).inflate(R.layout.view_entry_row, this, true)

        iconTile = findViewById(R.id.iconTile)
        iconView = findViewById(R.id.icon)
        titleView = findViewById(R.id.title)
        subtitleView = findViewById(R.id.subtitle)
        actionView = findViewById(R.id.action)
        chevronView = findViewById(R.id.chevron)
        switchView = findViewById(R.id.switchWidget)
    }

    fun configure(@DrawableRes icon: Int, title: String, subtitle: String) {
        iconView.setImageResource(icon)
        titleView.text = title
        titleText = title
        subtitleView.text = subtitle
        // 标题会随语言变化，描述在这里就同步，别只在 useSwitch 里读一次快照
        switchView.contentDescription = title
    }

    /** 只换副标题：开关的状态说明会随权限、锁定状态变化。 */
    fun setSubtitle(subtitle: String) {
        subtitleView.text = subtitle
    }

    /** 右侧显示开关；开关本身负责触感与动画，整行点击不额外响应。 */
    fun useSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
        actionView.visibility = View.GONE
        chevronView.visibility = View.GONE
        switchView.visibility = View.VISIBLE
        switchView.checked = checked
        switchView.contentDescription = titleText
        switchView.onCheckedChange = onChange
        isClickable = false
    }

    fun useAction(text: String, onClick: () -> Unit) {
        switchView.visibility = View.GONE
        chevronView.visibility = View.GONE
        actionView.visibility = View.VISIBLE
        actionView.text = text
        actionView.setOnClickListener { onClick() }
    }

    fun useChevron(onClick: (() -> Unit)? = null) {
        switchView.visibility = View.GONE
        actionView.visibility = View.GONE
        chevronView.visibility = View.VISIBLE
        if (onClick != null) setOnClickListener { onClick() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate()
                .scaleX(0.985f).scaleY(0.985f).setDuration(90).start()

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animate()
                .scaleX(1f).scaleY(1f).setDuration(200).start()
        }
        return super.onTouchEvent(event)
    }
}
