package com.example.screenrotator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * 常驻前台服务：持有方向控制窗口（见 [OverlayController]），
 * 并通过通知提供四个固定方向与“自动”的快速切换。
 *
 * 之所以需要前台服务：悬浮窗需要长期挂在 WindowManager 上，
 * 由前台服务承担后台持续运行的合规要求（Android 12+ 的后台启动 / 前台服务限制）。
 */
class RotationService : Service() {

    private lateinit var overlay: OverlayController

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayController(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 明确指定模式则使用它；否则恢复上次选择（例如服务被系统重启后）
        val requested = if (intent?.hasExtra(EXTRA_MODE) == true) {
            RotationMode.fromKey(intent.getStringExtra(EXTRA_MODE))
        } else {
            RotationPrefs.load(this)
        }

        // 先进入前台：通过 startForegroundService 启动后必须尽快调用 startForeground，
        // 否则系统会判定为前台服务启动超时。
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(requested),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        if (requested == RotationMode.AUTO) {
            release()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "悬浮窗权限未授予，停止服务")
            release()
            return START_NOT_STICKY
        }

        RotationPrefs.save(this, requested)

        if (!overlay.apply(requested)) {
            Log.w(TAG, "方向控制窗口创建失败，停止服务")
            release()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlay.detach()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    /** 移除悬浮窗、退出前台，交还系统决定方向。 */
    private fun release() {
        RotationPrefs.save(this, RotationMode.AUTO)
        overlay.detach()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notify_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(current: RotationMode): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rotation)
            .setContentTitle(getString(R.string.notify_title))
            .setContentText(modeLabel(current))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .apply {
                for (candidate in RotationMode.entries) {
                    addAction(
                        Notification.Action.Builder(
                            Icon.createWithResource(this@RotationService, R.drawable.ic_rotation),
                            modeLabel(candidate),
                            modePendingIntent(candidate)
                        ).build()
                    )
                }
            }
            .build()
    }

    private fun modePendingIntent(mode: RotationMode): PendingIntent {
        val intent = Intent(this, RotationService::class.java).putExtra(EXTRA_MODE, mode.key)
        return PendingIntent.getService(
            this,
            mode.ordinal + 1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun modeLabel(mode: RotationMode): String = when (mode) {
        RotationMode.AUTO -> getString(R.string.mode_auto)
        RotationMode.PORTRAIT -> getString(R.string.mode_portrait)
        RotationMode.LANDSCAPE -> getString(R.string.mode_landscape)
        RotationMode.REVERSE_PORTRAIT -> getString(R.string.mode_reverse_portrait)
        RotationMode.REVERSE_LANDSCAPE -> getString(R.string.mode_reverse_landscape)
    }

    companion object {
        private const val TAG = "RotationService"
        private const val CHANNEL_ID = "screen_rotation"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_MODE = "extra_mode"

        /** 切换到指定方向；[RotationMode.AUTO] 表示释放控制。 */
        fun setMode(context: Context, mode: RotationMode) {
            if (mode == RotationMode.AUTO) {
                release(context)
                return
            }
            val intent = Intent(context, RotationService::class.java)
                .putExtra(EXTRA_MODE, mode.key)
            ContextCompat.startForegroundService(context, intent)
        }

        /** 释放控制并停止服务（无需启动前台服务，直接 stopService）。 */
        fun release(context: Context) {
            context.stopService(Intent(context, RotationService::class.java))
        }
    }
}
