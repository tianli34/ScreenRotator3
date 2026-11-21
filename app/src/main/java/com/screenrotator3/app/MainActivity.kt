package com.screenrotator3.app

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.screenrotator3.app.databinding.ActivityMainBinding

/**
 * 主界面：五个方向按钮 + 一个菜单。
 *
 * 方向按钮是这一屏唯一的操作，其余所有功能（权限、通知栏控制条、悬浮球、磁贴、
 * 主题、说明）都收在右上角菜单里（见 [SettingsSheet]）。
 */
class MainActivity : Activity(), SettingsSheet.Host {

    private lateinit var binding: ActivityMainBinding
    private val cards = LinkedHashMap<RotationMode, ModeCardView>()
    private var sheet: SettingsSheet? = null
    private var lastState: ChipState? = null
    private var dotPulse: ObjectAnimator? = null

    /** 顶部状态芯片表达的三种事实：跟随系统 / 真的锁住了 / 选了但没生效。 */
    private enum class ChipState { AUTO, LOCKED, PENDING }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh(animate = true)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplayContext.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        window.setNavigationBarContrastEnforced(false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        updateSystemBarAppearance()
        bindCards()
        bindHeader()

        if (savedInstanceState == null) {
            playEntrance()
            // 缺悬浮窗权限时，过去靠顶部横幅提示；现在它就是菜单里的第一行，
            // 所以首次打开直接把菜单摆出来，否则五张卡片点了都不会有反应。
            if (!hasOverlayPermission()) binding.root.post { openSheet() }
        }

        intent?.getStringExtra(RotationTileService.EXTRA_PENDING_MODE)?.let { key ->
            // 磁贴在后台没能直接拉起服务，落到界面里补这一次切换
            val pending = RotationMode.fromKey(key)
            binding.root.post {
                RotationService.setMode(this, pending)
                refresh(animate = true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(RotationTileService.EXTRA_PENDING_MODE)?.let { key ->
            RotationService.setMode(this, RotationMode.fromKey(key))
            syncRequestedOrientation()
            refresh(animate = true)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(RotationService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ensureApplied()
        refresh(animate = false)
    }

    override fun onResume() {
        super.onResume()
        syncRequestedOrientation()
        refresh(animate = false)
    }

    override fun onDestroy() {
        // Dialog 挂在 Activity 上，不主动收会漏窗
        sheet?.dismiss()
        sheet = null
        super.onDestroy()
    }

    /**
     * 让界面自己请求的方向与锁定状态保持一致。
     *
     * 界面 Activity 的方向声明本身就是系统方向仲裁里的一个请求方：只要它和 1×1 悬浮窗
     * 请求的方向不一致，两者就会互顶。真机实测（Redmi / HyperOS）表现是「屏幕转一下又弹
     * 回来」——悬浮窗的请求先被采纳，随后界面按自己的方向把显示拉回去；界面每次旋转都会
     * 重建、再参与一次仲裁，于是来回震荡，状态栏最终停在「未生效」。
     *
     * 所以界面不能旁观：锁了什么方向，界面就请求什么方向；没有锁定时（AUTO）交还系统。
     * 这样两个请求方同向，系统不需要在它们之间做取舍，方向才稳定。
     */
    private fun syncRequestedOrientation() {
        val mode = RotationPrefs.load(this)
        val target = mode.screenOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != target) requestedOrientation = target
    }

    override fun onStop() {
        runCatching { unregisterReceiver(stateReceiver) }
        super.onStop()
    }

    // ---------------------------------------------------------------- 顶部

    private fun applyInsets() {
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun updateSystemBarAppearance() {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window.insetsController?.setSystemBarsAppearance(if (night) 0 else light, light)
    }

    private fun bindHeader() {
        binding.btnMenu.setOnClickListener { openSheet() }
    }

    private fun openSheet() {
        binding.btnMenu.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (sheet == null) sheet = SettingsSheet(this, this)
        sheet?.show()
    }

    // ---------------------------------------------------------------- 方向卡片

    private fun bindCards() {
        bindCard(binding.cardAuto, RotationMode.AUTO)
        bindCard(binding.cardPortrait, RotationMode.PORTRAIT)
        bindCard(binding.cardReversePortrait, RotationMode.REVERSE_PORTRAIT)
        bindCard(binding.cardLandscape, RotationMode.LANDSCAPE)
        bindCard(binding.cardReverseLandscape, RotationMode.REVERSE_LANDSCAPE)
    }

    private fun bindCard(card: ModeCardView, mode: RotationMode) {
        card.bind(mode)
        card.setOnClickListener { pick(mode, card) }
        cards[mode] = card
    }

    private fun pick(mode: RotationMode, card: ModeCardView) {
        card.performHapticFeedback(HapticFeedbackConstants.CONFIRM)

        if (!mode.isAuto && !hasOverlayPermission()) {
            Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }

        // 通知权限只在用户主动打开「通知栏控制条」时申请（见 onNotificationBarToggled），
        // 选方向时不弹窗：方向锁定本身不需要通知，突如其来的授权框只会打断操作。
        // 先让图标做一个 200ms 的小旋转，再真正把方向交给系统
        card.kick()
        RotationService.setMode(this, mode)
        // 界面自己的方向请求同步跟上，避免界面与悬浮窗两个请求方互相顶回去
        syncRequestedOrientation()
        refresh(animate = true)
        binding.chipMode.animate().scaleX(1.06f).scaleY(1.06f).setDuration(110)
            .withEndAction {
                binding.chipMode.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            .start()
    }

    // ---------------------------------------------------------------- 菜单里的事

    override fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    /** 开关的字面状态：偏好开着、通知发得出去、控制条真的能生效，三者都成立才算「开」。 */
    override fun notificationBarOn(): Boolean =
        NotificationBarPrefs.isEnabled(this) && notificationsAllowed() && hasOverlayPermission()

    override fun setBallEnabled(enabled: Boolean) {
        if (enabled && !hasOverlayPermission()) {
            Toast.makeText(this, R.string.toast_ball_needs_permission, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            refresh(animate = true)
            return
        }
        RotationService.setBallEnabled(this, enabled)
        refresh(animate = true)
    }

    override fun requestAddTile() = addQuickSettingTile()

    override fun themeMode(): ThemeMode = ThemePrefs.load(this)

    override fun applyTheme(mode: ThemeMode) {
        ThemePrefs.save(this, mode)
        // 界面要按新主题重建，面板属于旧界面，先收掉再重建
        sheet?.dismiss()
        Toast.makeText(this, themeToast(mode), Toast.LENGTH_SHORT).show()
        recreate()
    }

    private fun themeToast(mode: ThemeMode): Int = when (mode) {
        ThemeMode.SYSTEM -> R.string.toast_theme_system
        ThemeMode.LIGHT -> R.string.toast_theme_light
        ThemeMode.DARK -> R.string.toast_theme_dark
    }

    override fun language(): AppLanguage = LanguagePrefs.load(this)

    /**
     * 应用内切语言。
     *
     * 界面靠 [recreate] 重新走 attachBaseContext 拿到新 locale；通知、磁贴、通知通道
     * 不会随 Activity 重建，所以要显式刷一次文案（服务没在跑时不会把它拉起来）。
     * 顺序与 [applyTheme] 一致：先收面板，再落盘、刷新、重建。
     */
    override fun applyLanguage(language: AppLanguage) {
        if (LanguagePrefs.load(this) == language) return
        LanguagePrefs.save(this, language)

        // 面板属于旧语言的界面，先收掉，避免 recreate 时窗口泄漏
        sheet?.dismiss()

        RotationService.onDisplayChanged(this)
        RotationTileService.requestRefresh(this)

        // 这一条 Toast 必须用「新语言」的资源取：this.resources 此刻仍是旧 locale。
        // applicationContext 未经包装，正好作为干净 base。
        Toast.makeText(
            DisplayContext.wrapLocale(applicationContext),
            languageToast(language),
            Toast.LENGTH_SHORT
        ).show()

        // 新实例从 attachBaseContext 拿到新语言；savedInstanceState 非空，
        // 所以不会重播入场动画，也不会自动再弹面板（与切换主题一致）
        recreate()
    }

    private fun languageToast(language: AppLanguage): Int = when (language) {
        AppLanguage.SYSTEM -> R.string.toast_language_system
        AppLanguage.ENGLISH -> R.string.toast_language_en
        AppLanguage.CHINESE -> R.string.toast_language_zh
    }

    // ---------------------------------------------------------------- 通知栏控制条

    /**
     * 通知栏控制条的总开关。
     *
     * 打开时要同时满足两件事：悬浮窗权限（控制条上那五个按钮最终还是靠悬浮窗提交方向）
     * 和通知权限（没有它整条通知都不会出现）。所以这里按顺序过三道门：
     *
     * 1. 缺悬浮窗权限 → 直接去系统设置，开关回弹；
     * 2. Android 13+ 还没通知权限 → 调 `requestPermissions`，由系统在本页面之上弹原生
     *    「是否允许「屏幕旋转」发送通知？」。允许 → 立刻拉起前台服务，控制条当场出现；
     *    拒绝 → 开关回弹，不留下「开着却没有」的假象；
     * 3. 权限在但通知总开关被关掉（或已被系统判定不再询问）→ 只能去应用通知设置里改。
     */
    override fun setNotificationBarEnabled(enabled: Boolean) = onNotificationBarToggled(enabled)

    private fun onNotificationBarToggled(enable: Boolean) {
        if (!enable) {
            NotificationBarPrefs.setEnabled(this, false)
            RotationService.setNotificationBarEnabled(this, false)
            Toast.makeText(this, R.string.toast_bar_off, Toast.LENGTH_SHORT).show()
            refresh(animate = true)
            return
        }

        if (!hasOverlayPermission()) {
            Toast.makeText(this, R.string.toast_permission_required, Toast.LENGTH_LONG).show()
            openOverlaySettings()
            refresh(animate = true)
            return
        }

        if (notificationsAllowed()) {
            enableNotificationBar()
            return
        }

        val canAsk = canAskNotificationPermission()

        if (!canAsk) {
            NotificationBarPrefs.setEnabled(this, false)
            Toast.makeText(this, R.string.toast_notification_blocked, Toast.LENGTH_LONG).show()
            openNotificationSettings()
            refresh(animate = true)
            return
        }

        // 先记下意图：授权成功后由 onRequestPermissionsResult 直接接上，不用用户再点一次。
        // 授权框还开着的时候开关保持「开」，拒绝的回弹交给回调处理。
        NotificationBarPrefs.setEnabled(this, true)
        NotificationBarPrefs.markRequested(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    /** 真正把控制条挂到通知栏：落盘偏好 + 拉起前台服务，通知立即展示。 */
    private fun enableNotificationBar() {
        NotificationBarPrefs.setEnabled(this, true)
        RotationService.setNotificationBarEnabled(this, true)
        Toast.makeText(this, R.string.toast_bar_on, Toast.LENGTH_SHORT).show()
        refresh(animate = true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            enableNotificationBar()
        } else {
            NotificationBarPrefs.setEnabled(this, false)
            Toast.makeText(this, R.string.toast_notification_denied, Toast.LENGTH_LONG).show()
            refresh(animate = true)
        }
    }

    /**
     * 系统是否还愿意为通知权限弹那个原生窗口。
     *
     * Android 13+ 的运行时弹窗最多出现两次，之后 `requestPermissions` 会静默返回失败（不给任何回调），
     * 所以「问过」且「不再需要说明理由」就等于系统不会再弹了。
     */
    override fun canAskNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val willNotShowAgain = NotificationBarPrefs.hasRequested(this) &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        return !willNotShowAgain
    }

    /**
     * 通知此刻是否真的能发出去：Android 13+ 要运行时权限，之后还要看应用总开关。
     * 两者缺一，通知栏里都不会有控制条。
     */
    override fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    override fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }
            .onFailure { openOverlaySettings() }
    }

    private fun addQuickSettingTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.toast_tile_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        requestTile()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestTile() {
        val manager = getSystemService(StatusBarManager::class.java)
        runCatching {
            manager.requestAddTileService(
                ComponentName(this, RotationTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile_service),
                mainExecutor
            ) { result ->
                val added = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                val message = if (added) {
                    R.string.toast_tile_added
                } else {
                    R.string.toast_tile_unavailable
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, R.string.toast_tile_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- 状态刷新

    /**
     * 打开界面时把上次锁定的方向重新建立起来。
     *
     * 偏好（[RotationPrefs]）会跨进程死亡、覆盖安装、重启一直保留，但真正锁住方向的悬浮窗
     * 只活在服务进程里。所以进程重启后屏幕回到竖屏、偏好却还写着「横屏」。
     * 用户主动打开本应用，就当作他想恢复上次的锁定：这里补建一次。
     *
     * 已经持有同一方向（服务还在跑）时不做任何事，避免重复拉起。
     */
    private fun ensureApplied() {
        // 没有权限时不硬拉服务，菜单里的权限行会引导用户
        if (!hasOverlayPermission()) return
        val mode = RotationPrefs.load(this)
        if (!mode.isAuto) {
            if (!RotationRuntime.isHolding(mode)) RotationService.setMode(this, mode)
            return
        }
        // 只开了控制条、方向仍是「自动」：服务可能已被划掉，补拉一次让控制条回到通知栏。
        // 这是用户显式打开过的功能，所以按「打开即恢复」处理；默认关闭时这里不会触发。
        if (notificationBarOn() && !RotationService.isRunning()) {
            RotationService.setNotificationBarEnabled(this, true)
        }
    }

    private fun refresh(animate: Boolean) {
        val granted = hasOverlayPermission()
        val mode = RotationPrefs.load(this)
        val state = chipState(mode)
        // 只有真的锁住了，才把卡片点亮；否则宁可留白，也不做“已生效”的假象
        val highlight = mode.isAuto || state == ChipState.LOCKED
        for ((cardMode, card) in cards) {
            card.setActive(cardMode == mode && highlight, animate)
        }

        binding.tvChip.text = when (state) {
            ChipState.AUTO -> getString(R.string.status_auto)
            ChipState.LOCKED -> getString(R.string.status_locked, getString(mode.labelRes))
            ChipState.PENDING -> getString(R.string.status_pending, getString(mode.labelRes))
        }
        binding.tvChip.setTextColor(
            getColor(
                when (state) {
                    ChipState.AUTO -> R.color.text_secondary
                    ChipState.LOCKED -> R.color.accent
                    ChipState.PENDING -> R.color.warn
                }
            )
        )
        if (lastState != state) {
            lastState = state
            updateChipDot(state)
        }

        // 菜单里「有事要处理」时，按钮上点一个小圆点，否则功能被收起来就等于被藏起来了
        val attention = !granted || (NotificationBarPrefs.isEnabled(this) && !notificationBarOn())
        binding.menuBadge.visibility = if (attention) View.VISIBLE else View.GONE
        binding.btnMenu.contentDescription = getString(
            if (attention) R.string.menu_open_attention else R.string.menu_open
        )

        sheet?.refresh()
    }

    /**
     * 判定当前真实状态：偏好的模式 + 悬浮窗是否持有 + 屏幕实际朝向是否一致。
     *
     * 三者都成立才算「已锁定」；任一不成立就说明界面不能替系统答“已生效”。
     */
    private fun chipState(mode: RotationMode): ChipState = when {
        mode.isAuto -> ChipState.AUTO
        RotationRuntime.isHolding(mode) && axisMatches(mode) -> ChipState.LOCKED
        else -> ChipState.PENDING
    }

    /** 屏幕当前实际是横还是竖（不考虑正反向，反向无法从 Configuration 区分）。 */
    private fun axisMatches(mode: RotationMode): Boolean {
        val landscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return landscape == mode.isLandscape
    }

    private fun updateChipDot(state: ChipState) {
        dotPulse?.cancel()
        val color = getColor(
            when (state) {
                ChipState.LOCKED -> R.color.accent
                ChipState.PENDING -> R.color.warn
                ChipState.AUTO -> R.color.text_tertiary
            }
        )
        binding.chipDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        binding.chipDot.alpha = 1f
        if (state == ChipState.LOCKED) {
            dotPulse = ObjectAnimator.ofFloat(binding.chipDot, View.ALPHA, 1f, 0.35f).apply {
                duration = 900
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    // ---------------------------------------------------------------- 杂项

    private fun playEntrance() {
        val views = listOf(
            binding.header,
            binding.cardAuto,
            binding.cardPortrait,
            binding.cardReversePortrait,
            binding.cardLandscape,
            binding.cardReverseLandscape
        )
        val offset = 18f * resources.displayMetrics.density
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = offset
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(50L + index * 45L)
                .setDuration(320)
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    override fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { startActivity(intent) }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 200
    }
}
