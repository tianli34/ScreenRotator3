package com.screenrotator3.app

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.screenrotator3.app.databinding.DialogSettingsSheetBinding

/**
 * 主界面唯一的「菜单」：贴底弹出的设置面板。
 *
 * 主界面只保留五个方向按钮，其余功能（权限、通知栏控制条、悬浮球、快捷设置磁贴、
 * 主题、说明）全部收进这里。面板是 [Dialog] 而不是全屏 Activity：
 * 它只服务主界面、不需要跨进程拉起，窗口本身就只有面板那么大，
 * 面板之外的点击由 WindowManager 判定为「窗口外」直接关闭，不必自己铺一层遮罩 View。
 *
 * 所有实际操作（开关、申请权限、跳系统设置、切主题）都交回 [Host] —— 那些逻辑本来就属于
 * MainActivity，面板只负责把它们摆出来并如实反映当前状态。
 */
class SettingsSheet(
    private val activity: Activity,
    private val host: Host
) {

    /** 面板需要的全部能力。全部由 MainActivity 实现，面板自身不持有任何业务状态。 */
    interface Host {
        fun hasOverlayPermission(): Boolean
        fun notificationBarOn(): Boolean
        fun notificationsAllowed(): Boolean
        fun canAskNotificationPermission(): Boolean
        fun setNotificationBarEnabled(enabled: Boolean)
        fun setBallEnabled(enabled: Boolean)
        fun requestAddTile()
        fun openNotificationSettings()
        fun openOverlaySettings()
        fun themeMode(): ThemeMode
        fun applyTheme(mode: ThemeMode)
        fun language(): AppLanguage
        fun applyLanguage(language: AppLanguage)
    }

    private var dialog: Dialog? = null
    private var binding: DialogSettingsSheetBinding? = null

    val isShowing: Boolean get() = dialog?.isShowing == true

    fun show() {
        if (isShowing) return

        val b = DialogSettingsSheetBinding.inflate(activity.layoutInflater)
        val d = Dialog(activity, R.style.Theme_ScreenRotator_Sheet).apply {
            setContentView(b.panel)
            setCanceledOnTouchOutside(true)
        }
        binding = b
        dialog = d

        d.setOnDismissListener {
            dialog = null
            binding = null
        }

        bindRows(b)
        applyInsets(b.panel)
        d.show()

        d.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0.42f)
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            // 自己吃系统栏：面板才能一直铺到屏幕下沿，圆角上沿才是「贴底」而不是「浮在中间」。
            // 被系统栏占掉的那几段高度由 applyInsets 里的 padding 补回来。
            window.setDecorFitsSystemWindows(false)
            window.navigationBarColor = Color.TRANSPARENT
            window.setNavigationBarContrastEnforced(false)
            val night = (activity.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val light = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else light, light)
        }

        refresh()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    /** 由主界面在状态变化后调用：开关与副标题必须跟着真实能力走，不能出现「开着却没生效」。 */
    fun refresh() {
        val b = binding ?: return
        b.cardPermission.visibility =
            if (host.hasOverlayPermission()) View.GONE else View.VISIBLE

        val barOn = host.notificationBarOn()
        b.rowNotification.useSwitch(barOn) { host.setNotificationBarEnabled(it) }
        b.rowNotification.setSubtitle(
            activity.getString(
                when {
                    barOn -> R.string.row_notification_sub
                    // 通知发不出去、系统也不肯再弹窗：只能由用户自己去设置里改
                    !host.notificationsAllowed() && !host.canAskNotificationPermission() ->
                        R.string.row_notification_sub_blocked

                    !host.notificationsAllowed() -> R.string.row_notification_sub_need_permission
                    else -> R.string.row_notification_sub_off
                }
            )
        )

        // 与通知栏控制条同一套算法：偏好开着但权限被收回，开关就显示「关」，
        // 不让界面上出现一个「开着却看不到球」的开关。
        b.rowBall.useSwitch(FloatingBallPrefs.isEnabled(activity) && host.hasOverlayPermission()) {
            host.setBallEnabled(it)
        }
        renderTheme(host.themeMode())
        renderLanguage(host.language())
    }

    private fun bindRows(b: DialogSettingsSheetBinding) {
        b.rowNotification.configure(
            R.drawable.ic_bell,
            activity.getString(R.string.row_notification_title),
            activity.getString(R.string.row_notification_sub_off)
        )
        b.rowBall.configure(
            R.drawable.ic_ball,
            activity.getString(R.string.row_ball_title),
            activity.getString(R.string.row_ball_sub)
        )
        b.rowTile.configure(
            R.drawable.ic_tile_grid,
            activity.getString(R.string.row_tile_title),
            activity.getString(R.string.row_tile_sub)
        )
        b.rowTile.useAction(activity.getString(R.string.row_tile_action)) { host.requestAddTile() }

        b.btnGrant.setOnClickListener {
            host.openOverlaySettings()
            dismiss()
        }

        themeSegments(b).forEach { (view, mode) ->
            view.setOnClickListener {
                if (host.themeMode() == mode) return@setOnClickListener
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                host.applyTheme(mode)
            }
        }

        // 与主题完全同构：点已选中的那一段不做任何事，避免多余的 recreate 闪一下
        languageSegments(b).forEach { (view, language) ->
            view.setOnClickListener {
                if (host.language() == language) return@setOnClickListener
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                host.applyLanguage(language)
            }
        }
    }

    private fun themeSegments(
        b: DialogSettingsSheetBinding
    ): List<Pair<TextView, ThemeMode>> = listOf(
        b.segSystem to ThemeMode.SYSTEM,
        b.segLight to ThemeMode.LIGHT,
        b.segDark to ThemeMode.DARK
    )

    private fun languageSegments(
        b: DialogSettingsSheetBinding
    ): List<Pair<TextView, AppLanguage>> = listOf(
        b.segLangSystem to AppLanguage.SYSTEM,
        b.segLangEn to AppLanguage.ENGLISH,
        b.segLangZh to AppLanguage.CHINESE
    )

    /**
     * 选中段整块填充主题色，与方向卡片的选中态用同一套语言（不是描边、不是加粗）。
     * 外观与语言两排选择器共用这一份绘制。
     */
    private fun renderSegment(view: TextView, active: Boolean) {
        view.isSelected = active
        view.background = if (active) {
            ContextCompat.getDrawable(activity, R.drawable.bg_segment_active)
        } else {
            null
        }
        view.setTextColor(
            ContextCompat.getColor(
                activity,
                if (active) R.color.on_accent else R.color.text_secondary
            )
        )
    }

    private fun renderTheme(current: ThemeMode) {
        val b = binding ?: return
        themeSegments(b).forEach { (view, mode) -> renderSegment(view, mode == current) }
    }

    private fun renderLanguage(current: AppLanguage) {
        val b = binding ?: return
        languageSegments(b).forEach { (view, language) ->
            renderSegment(view, language == current)
        }
    }

    /**
     * 面板贴底，窗口又不吃系统栏：左右要给横屏的挖孔/导航栏让位，底部要给手势条让位。
     * 这些内边距加在 XML 里那层基础 padding 之上。
     */
    private fun applyInsets(panel: View) {
        val start = activity.dp(16)
        val top = activity.dp(10)
        val end = activity.dp(16)
        val bottom = activity.dp(14)
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                start + bars.left,
                top,
                end + bars.right,
                bottom + bars.bottom
            )
            insets
        }
    }
}
