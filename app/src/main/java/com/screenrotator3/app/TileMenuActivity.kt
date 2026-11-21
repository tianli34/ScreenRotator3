package com.screenrotator3.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.screenrotator3.app.databinding.ActivityTileMenuBinding
import kotlin.math.min

/**
 * 快捷设置磁贴点击后弹出的悬浮选择窗。
 *
 * 为什么是 Activity 而不是 Dialog / 悬浮窗：
 * - 磁贴属于 SystemUI 进程，只有 `TileService.startActivityAndCollapse` 能合法地把界面带到前台；
 *   它能拉起的只有 Activity。
 * - 用悬浮窗要在后台申请 `SYSTEM_ALERT_WINDOW` 之上的输入窗口，等于把「点一下换个方向」
 *   变成常驻窗口的服务，代价和风险都不对等。
 *
 * 因此这里是一个「全屏透明、自带暗遮罩」的 Activity：面板贴着屏幕底部升起（拇指够得到的区域），
 * 点面板外任意位置或返回键即关闭，选中某个方向后立刻落盘、拉起服务并收起。清单里不给它声明
 * `screenOrientation` —— 它只是方向仲裁里的被动参与者，方向仍由 1×1 悬浮窗持有
 * （否则会和主界面一样出现互相顶）。
 */
class TileMenuActivity : Activity() {

    private lateinit var binding: ActivityTileMenuBinding
    private val rows = LinkedHashMap<RotationMode, ModeMenuRow>()

    /** 退场动画期间不再接受新的操作，避免「再点一下又触发一次切换」。 */
    private var dismissing = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplayContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTileMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clampPanelWidth()
        applyInsets()
        buildRows()
        playEntrance()

        val dismissOnTap = View.OnClickListener { dismiss() }
        binding.root.setOnClickListener(dismissOnTap)
        binding.scrim.setOnClickListener(dismissOnTap)
    }

    // ---------------------------------------------------------------- 动画

    /**
     * 抽屉式进场：面板从下方 32dp 处升上来，遮罩由窗口动画单独淡入。
     *
     * 位移只加在面板上，不加在窗口上 —— 窗口动画会连同全屏遮罩一起平移，屏幕上会露出一条
     * 没被遮住的亮边。所以窗口动画只做淡入（见 `anim/tile_menu_in.xml`），位移在这里做。
     */
    private fun playEntrance() {
        binding.panel.translationY = dp(32f).toFloat()
        binding.panel.alpha = 0.2f
        binding.panel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(230)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    private fun playExit(onEnd: () -> Unit) {
        binding.panel.animate()
            .translationY(dp(26f).toFloat())
            .alpha(0f)
            .setDuration(140)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction(onEnd)
            .start()
    }

    /** 磁贴被连点两次时复用同一个实例，避免面板叠成两层。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        buildRows()
    }

    // ---------------------------------------------------------------- 布局

    /**
     * 面板从底部升起（类底部抽屉）：单手拿手机时，拇指的活动范围就在屏幕下半部分，
     * 而控制中心收起后手指本来也停在下方。贴顶的卡片会让五行全部落在最不好够的区域。
     *
     * 左右留 16dp 让面板浮起来，平板上再收一个宽度上限，避免拉成一条长带。
     */
    private fun clampPanelWidth() {
        val target = min(resources.displayMetrics.widthPixels - dp(32f), dp(430f))
        binding.panel.layoutParams = FrameLayout.LayoutParams(
            target,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
    }

    private fun applyInsets() {
        binding.root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            binding.panelHost.setPadding(
                // 顶部只留一道安全边：面板贴底，这层 padding 只用来保证它不会顶进状态栏
                bars.left + dp(16f),
                bars.top + dp(12f),
                bars.right + dp(16f),
                bars.bottom + dp(16f)
            )
            insets
        }
    }

    // ---------------------------------------------------------------- 选项

    /**
     * 五个方向按「竖一族 → 横一族」排列，与主界面 2×2 网格的阅读顺序一致；
     * 自动放最前，因为它是解锁的出口，也是多数人只想点一下的那个。
     *
     * 选中项永远跟着 [RotationPrefs] 走（那是「上次选择」，也是再点一次就能复用的目标），
     * 但「有没有真的生效」由顶部那行说明承担 —— 面板里出现一个选了却没锁住的静止画面，
     * 比没有选中项更容易让人误判。
     */
    private fun buildRows() {
        binding.rowContainer.removeAllViews()
        rows.clear()

        val current = RotationPrefs.load(this)
        val holding = current.isAuto || RotationRuntime.isHolding(current)
        // 横屏可用高度只有 393dp 左右，带副标题的五行放不下（见 ModeMenuRow.useCompact）
        val compact = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        binding.tvHint.text = when {
            current.isAuto -> getString(R.string.tile_menu_hint)
            holding -> getString(R.string.tile_menu_state_locked, getString(current.labelRes))
            else -> getString(R.string.tile_menu_state_pending, getString(current.labelRes))
        }
        binding.tvHint.setTextColor(
            getColor(if (!current.isAuto && !holding) R.color.warn else R.color.text_secondary)
        )

        for (mode in ORDER) {
            val row = ModeMenuRow(this)
            row.useCompact(compact)
            row.bind(mode, mode == current)
            row.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                pick(mode)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (rows.isNotEmpty()) params.topMargin = dp(2f)
            binding.rowContainer.addView(row, params)
            rows[mode] = row
        }
    }

    private fun pick(mode: RotationMode) {
        if (dismissing) return

        // 锁方向靠的还是那个 1×1 悬浮窗，缺权限时先把人送去授权，别让他以为点了没反应
        if (!mode.isAuto && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            dismiss()
            return
        }

        val handled = RotationService.setMode(this, mode)
        // 方向停在「自动」且服务随之退出时，没有别的地方会刷新磁贴
        RotationTileService.requestRefresh(this)

        if (!handled) {
            // 后台启动前台服务被系统拦下时，把这一次选择带回主界面完成
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(RotationTileService.EXTRA_PENDING_MODE, mode.key)
            )
        }
        dismiss()
    }

    /** 收起面板再去 finish：抽屉滑回去的那一下比直接消失更容易让人确认「选择已提交」。 */
    private fun dismiss() {
        if (dismissing || isFinishing) return
        dismissing = true
        playExit { finish() }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private companion object {
        val ORDER = listOf(
            RotationMode.AUTO,
            RotationMode.PORTRAIT,
            RotationMode.REVERSE_PORTRAIT,
            RotationMode.LANDSCAPE,
            RotationMode.REVERSE_LANDSCAPE
        )
    }
}
