package com.screenrotator3.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.provider.Settings
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 常驻前台服务：持有方向控制窗口（[OverlayController]）与可选的悬浮球
 * （[FloatingBallController]），并通过通知栏提供五个方向的快捷切换。
 *
 * 之所以需要前台服务：悬浮窗需要长期挂在 WindowManager 上，
 * 由前台服务承担后台持续运行的合规要求（Android 12+ 的后台启动 / 前台服务限制）。
 *
 * 运行条件：锁定了某个方向、开启了悬浮球、或打开了通知栏控制条。三者都不满足时立刻退出。
 */
class RotationService : Service() {

    private lateinit var overlay: OverlayController
    private lateinit var ball: FloatingBallController

    /**
     * 用户可见文案统一走这个 Context。
     *
     * 不能用 this.getString：Service 的 base context 在 attach 时就定死了，
     * 应用内切语言不会重建服务（重建等于把悬浮窗拆了重挂），this.resources 永远是旧语言。
     */
    private lateinit var uiContext: Context

    private var currentMode = RotationMode.AUTO
    private var ballEnabled = false
    private var barEnabled = false

    override fun onCreate() {
        super.onCreate()
        running = true
        // applicationContext 未经任何包装，是干净 base；语言由 DisplayContext 重新算
        uiContext = DisplayContext.wrapLocale(applicationContext)
        overlay = OverlayController(this)
        ball = FloatingBallController(this) { mode -> applyMode(mode) }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 语言变化后只重建文案，绝不重跑 applyState（那会重挂一次悬浮窗，
        // 甚至把刚释放的方向又锁回去）。必须放在下面「该不该退出」的判断之前。
        if (intent?.action == ACTION_REFRESH_DISPLAY) {
            if (!shouldStayRunning()) return START_NOT_STICKY
            refreshDisplay()
            return START_NOT_STICKY
        }

        val requested = if (intent?.hasExtra(EXTRA_MODE) == true) {
            RotationMode.fromKey(intent.getStringExtra(EXTRA_MODE))
        } else {
            RotationPrefs.load(this)
        }
        ballEnabled = if (intent?.hasExtra(EXTRA_BALL) == true) {
            intent.getBooleanExtra(EXTRA_BALL, false)
        } else {
            FloatingBallPrefs.isEnabled(this)
        }
        barEnabled = NotificationBarPrefs.isEnabled(this)

        // 既没锁方向、也没开悬浮球、也没要控制条 —— 没有理由常驻
        if (requested.isAuto && !ballEnabled && !barEnabled) {
            release()
            return START_NOT_STICKY
        }

        // 通过 startForegroundService 拉起后必须尽快 startForeground，否则判定超时
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(requested),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，停止服务")
            release()
            return START_NOT_STICKY
        }

        applyState(requested)
        // 不用 START_STICKY：进程若被系统回收，静默拉起会按旧偏好重新锁方向，
        // 与“从最近任务划掉即释放”的语义冲突。宁可等用户手动再选一次。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        overlay.detach()
        ball.detach()
        RotationRuntime.onReleased()
        super.onDestroy()
    }

    /**
     * 用户从最近任务划掉本应用时，等同于点“自动（跟随系统）”：释放锁定。
     *
     * 注意：部分厂商 ROM 划卡会直接杀进程、不回调此方法，那种情况下悬浮窗随进程销毁，
     * 释放结果相同，只是 [RotationPrefs] 里的旧模式不会被清成 auto。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        release()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?) = null

    /** 悬浮球选中某个方向后走这里：服务已经在跑，只需换锁定状态。 */
    private fun applyMode(mode: RotationMode) {
        applyState(mode)
    }

    private fun applyState(mode: RotationMode) {
        if (mode.isAuto && !ballEnabled && !barEnabled) {
            release()
            return
        }

        currentMode = mode
        RotationPrefs.save(this, mode)
        FloatingBallPrefs.setEnabled(this, ballEnabled)

        if (mode.isAuto) {
            overlay.detach()
            RotationRuntime.onReleased()
        } else if (overlay.apply(mode)) {
            // 只有窗口真的挂上去了，才承认“方向由我们持有”
            RotationRuntime.onApplied(mode)
        } else {
            Log.w(TAG, "方向控制窗口创建失败，停止服务")
            RotationRuntime.onReleased()
            release()
            return
        }

        ball.setMode(mode)
        if (ballEnabled) {
            if (!ball.isAttached && !ball.attach()) {
                Log.w(TAG, "悬浮球创建失败，回退为关闭")
                ballEnabled = false
                FloatingBallPrefs.setEnabled(this, false)
            }
        } else {
            ball.detach()
        }

        updateNotification()
        RotationTileService.requestRefresh(this)
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    /** 移除悬浮窗、退出前台，交还系统决定方向。 */
    private fun release() {
        Log.i(TAG, "释放心方向盘：当前 ${currentMode.key}")
        RotationPrefs.save(this, RotationMode.AUTO)
        RotationRuntime.onReleased()
        overlay.detach()
        ball.detach()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    private fun updateNotification() {
        val notification = buildNotification(currentMode)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    /** 服务此刻还该不该活着：三种常驻理由（锁方向 / 悬浮球 / 控制条）都不成立就该退出。 */
    private fun shouldStayRunning(): Boolean =
        !RotationPrefs.load(this).isAuto ||
            FloatingBallPrefs.isEnabled(this) ||
            NotificationBarPrefs.isEnabled(this)

    /**
     * 应用内切语言后重建文案：通知标题/正文、控制条按钮的无障碍描述、通知通道名称。
     * 不碰 [applyState] —— 方向锁定与悬浮窗保持原样，屏幕不该闪一下。
     */
    private fun refreshDisplay() {
        uiContext = DisplayContext.wrapLocale(applicationContext)
        updateChannelText()
        updateNotification()
        RotationTileService.requestRefresh(this)
    }

    /**
     * 同 id 重建通道可以更新 name/description（重要性只能降不能升，那也不影响这里）。
     * 取回系统里的通道对象再改字段，避免把用户改过的音效/震动重置回默认值。
     */
    private fun updateChannelText() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return
        channel.name = uiContext.getString(R.string.notify_channel_name)
        channel.description = uiContext.getString(R.string.notify_channel_desc)
        manager.createNotificationChannel(channel)
    }

    /**
     * 创建常驻通知的通道。
     *
     * 重要性用 [NotificationManager.IMPORTANCE_DEFAULT] 而不是 LOW：LOW 会被 Android 12+
     * 归入「静默通知」，折叠后 [Notification.DecoratedCustomViewStyle] 的自定义视图不再渲染，
     * 五个方向按钮就跟看不见了。这里用 DEFAULT 但把提示音、震动、灯全部关掉，体验仍是静默。
     *
     * 不能用「把 IMPORTANCE_LOW 改成 DEFAULT」的方式升级：Android 8 起通道一旦创建，
     * 重要性只能由 App 下调、不能上调，重复 create 传更高的值会被系统静默忽略。
     * 所以走换 id 迁移——建新通道，删旧通道，避免设置里留两条同名条目。
     *
     * 注意（真机实测，Redmi / HyperOS）：通道重要性提到 DEFAULT 甚至 HIGH、恢复默认提示音，系统仍然把
     * 这条通知收进「更多通知」。原因不在重要性，而是 MIUI 按「自定义通知」（自带 RemoteViews 布局）
     * 这个维度收纳，且没有对应的 App API 可以关闭。用户侧的一次性解法：
     * 展开「更多通知」→ 长按本通知 → 「不收纳」→ 确定（提示「自定义通知不收纳到"更多通知"」），
     * 设置后控制条直接常驻通知中心主区域，重装后需重做。详见 README「已知限制」。
     */
    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        for (legacy in LEGACY_CHANNEL_IDS) {
            if (manager.getNotificationChannel(legacy) != null) {
                manager.deleteNotificationChannel(legacy)
            }
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            uiContext.getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = uiContext.getString(R.string.notify_channel_desc)
            setShowBadge(false)
            // 常驻控制条不该打扰：静音、不震动、不亮灯
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(current: RotationMode): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            // 不再用字符串拼接：语序、标点、空格都交给翻译
            !current.isAuto -> uiContext.getString(
                R.string.notify_title_mode,
                uiContext.getString(current.labelRes)
            )

            ballEnabled -> uiContext.getString(R.string.notify_title_auto)
            else -> uiContext.getString(R.string.notify_title_auto_plain)
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rotation)
            .setContentTitle(title)
            .setContentIntent(contentIntent)
            .setColor(getColor(R.color.accent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            // 默认是 DEFERRED：首次挂到前台后会延迟约 10 秒才展示，
            // 这段「空窗期」里通知容易被系统或 ROM 归入折叠区。立即展示更符合常驻控制条的预期。
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (!barEnabled) {
            // 控制条被关掉，但前台服务仍然需要一条通知。退化成最朴素的一条文本，
            // 不给它挂 RemoteViews，也不留任何可点的方向按钮。
            return builder
                .setContentText(uiContext.getString(R.string.notify_text_bar_off))
                .build()
        }

        // 通知栏里只放图标、不放文字：下拉栏高度有限，五个带方向锚点的图标已经足够表达
        val views = RemoteViews(packageName, R.layout.notification_controls)
        for ((viewId, mode) in SLOT_IDS) {
            views.setInt(
                viewId,
                "setBackgroundResource",
                if (mode == current) R.drawable.bg_notif_slot_active else R.drawable.bg_notif_slot
            )
            views.setOnClickPendingIntent(viewId, modePendingIntent(mode))
            // RemoteViews 布局里的 android:contentDescription 由宿主进程按「系统语言」解析，
            // 应用内切语言管不到它；只有代码写进去的文本才跟随应用内语言。
            views.setContentDescription(viewId, uiContext.getString(mode.labelRes))
        }

        return builder
            .setContentText(uiContext.getString(current.labelRes))
            .setCustomContentView(views)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .build()
    }

    private fun modePendingIntent(mode: RotationMode): PendingIntent {
        val intent = Intent(this, RotationService::class.java)
            .setAction(ACTION_SET_MODE)
            .putExtra(EXTRA_MODE, mode.key)
        // 用 getForegroundService：即使服务已被系统回收，点通知栏图标也能合法拉起前台服务
        return PendingIntent.getForegroundService(
            this,
            100 + mode.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val TAG = "RotationService"

        /**
         * 通道 id 带版本后缀：重要性从 LOW 提到 DEFAULT 时，旧通道无法原地提级，
         * 只能换 id 重建。以后再要改重要性，同样改这里的后缀并把旧 id 加进
         * [LEGACY_CHANNEL_IDS]。
         */
        private const val CHANNEL_ID = "screen_rotation_v2"

        /** 1.0 用的旧通道（IMPORTANCE_LOW），创建新通道时顺手删掉，免得设置里出现两条同名项。 */
        private val LEGACY_CHANNEL_IDS = listOf("screen_rotation")

        private const val NOTIFICATION_ID = 1001

        /** 广播：方向或悬浮球状态发生变化，界面用它刷新。 */
        const val ACTION_STATE_CHANGED = "com.screenrotator3.app.action.STATE_CHANGED"

        private const val ACTION_SET_MODE = "com.screenrotator3.app.action.SET_MODE"

        /** 应用内切语言后由界面投递：只刷新通知与通道文案，不重挂悬浮窗。 */
        private const val ACTION_REFRESH_DISPLAY = "com.screenrotator3.app.action.REFRESH_DISPLAY"

        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_BALL = "extra_ball"

        private val SLOT_IDS = linkedMapOf(
            R.id.slot_auto to RotationMode.AUTO,
            R.id.slot_portrait to RotationMode.PORTRAIT,
            R.id.slot_landscape to RotationMode.LANDSCAPE,
            R.id.slot_reverse_portrait to RotationMode.REVERSE_PORTRAIT,
            R.id.slot_reverse_landscape to RotationMode.REVERSE_LANDSCAPE
        )

        /**
         * 服务进程内是否活着。进程被杀时随之归零，因此它表达的是「此刻」而不是「上次」。
         * 界面用它判断要不要为「只开了控制条」这种情况补拉一次服务。
         */
        @Volatile
        private var running = false

        fun isRunning(): Boolean = running

        /**
         * 语言变化后同步常驻通知与通道文案。
         *
         * 服务没在跑时绝不为刷文案把它拉起来 —— 只更新「已经存在」的通道，也不新建通道，
         * 免得从没开过控制条的用户在系统设置里凭空多出一条。
         */
        fun onDisplayChanged(context: Context) {
            if (running) {
                runCatching {
                    // 已经在前台的服务再投递一次即可。不用 startForegroundService：
                    // 对没在跑的服务用它会强制 5 秒内 startForeground，这里不需要冒那个险。
                    context.startService(
                        Intent(context, RotationService::class.java)
                            .setAction(ACTION_REFRESH_DISPLAY)
                    )
                }
                return
            }
            refreshChannelTextIfExists(context)
        }

        private fun refreshChannelTextIfExists(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return
            val ui = DisplayContext.wrapLocale(context.applicationContext)
            channel.name = ui.getString(R.string.notify_channel_name)
            channel.description = ui.getString(R.string.notify_channel_desc)
            manager.createNotificationChannel(channel)
        }

        /** 切换到指定方向；[RotationMode.AUTO] 表示释放锁定。返回是否成功交给服务处理。 */
        fun setMode(context: Context, mode: RotationMode): Boolean {
            // 先落盘：界面/磁贴立刻读到的就是新状态，避免与进程退出竞态
            RotationPrefs.save(context, mode)
            val intent = Intent(context, RotationService::class.java)
                .setAction(ACTION_SET_MODE)
                .putExtra(EXTRA_MODE, mode.key)

            if (mode.isAuto && !FloatingBallPrefs.isEnabled(context) &&
                !NotificationBarPrefs.isEnabled(context)
            ) {
                context.stopService(intent)
                context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
                return true
            }
            return startSafely(context, intent)
        }

        /**
         * 打开/关闭通知栏控制条。
         *
         * 打开就是让服务活起来 —— 前台服务一挂，通知（含那排五个按钮）立刻可见；
         * 关闭时若还有别的理由常驻（方向锁定、悬浮球），服务不退出，只是把通知降级成纯文本。
         */
        fun setNotificationBarEnabled(context: Context, enabled: Boolean): Boolean {
            NotificationBarPrefs.setEnabled(context, enabled)
            val mode = RotationPrefs.load(context)
            val intent = Intent(context, RotationService::class.java)
                .setAction(ACTION_SET_MODE)
                .putExtra(EXTRA_MODE, mode.key)

            if (!enabled && mode.isAuto && !FloatingBallPrefs.isEnabled(context)) {
                context.stopService(intent)
                context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
                return true
            }
            return startSafely(context, intent)
        }

        /** 打开/关闭悬浮球；关闭后若没有锁定方向与控制条，服务随之退出。 */
        fun setBallEnabled(context: Context, enabled: Boolean): Boolean {
            FloatingBallPrefs.setEnabled(context, enabled)
            val mode = RotationPrefs.load(context)
            val intent = Intent(context, RotationService::class.java)
                .setAction(ACTION_SET_MODE)
                .putExtra(EXTRA_MODE, mode.key)
                .putExtra(EXTRA_BALL, enabled)

            if (!enabled && mode.isAuto && !NotificationBarPrefs.isEnabled(context)) {
                context.stopService(intent)
                context.sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(context.packageName))
                return true
            }
            return startSafely(context, intent)
        }

        /** 后台启动前台服务受限时返回 false，调用方（如磁贴）可回退到打开界面。 */
        private fun startSafely(context: Context, intent: Intent): Boolean =
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w("RotationService", "启动前台服务失败", it) }
                .isSuccess
    }
}
