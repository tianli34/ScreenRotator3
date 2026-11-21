package com.screenrotator3.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * 快捷设置磁贴：点一下弹出五个方向的悬浮选择窗（[TileMenuActivity]），磁贴本身显示当前方向。
 * 需要用户在控制中心手动添加（主界面提供一键入口）。
 *
 * 弹出的窗口由 `startActivityAndCollapse` 拉起 —— 这是磁贴唯一能合法带出界面的通道，
 * 它同时负责收起控制中心。Android 14 起必须换成 PendingIntent 版本：
 * Intent 版本在 targetSdk 34+ 上会被系统拦掉（后台启动 Activity 限制），只留一个“点了没反应”。
 *
 * 万一窗口没能弹出（极少数 ROM 会拦截），退回旧的「循环切换」：宁可少一个选择，
 * 也不能让用户点完什么也没发生。
 */
class RotationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // 先刷新一次：控制中心收起的过程中磁贴还在屏幕上，副标题不该是旧的
        updateTile()
        if (openMenu()) return
        cycleMode()
    }

    /** 弹出选择窗；返回是否成功交给了系统。 */
    private fun openMenu(): Boolean {
        val intent = Intent(this, TileMenuActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    REQUEST_MENU,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { Log.w(TAG, "弹出方向选择窗失败，退回循环切换", it) }.isSuccess
    }

    /** 兜底路径：自动 → 竖屏 → 横屏 → 反向竖屏 → 反向横屏 → 自动。 */
    private fun cycleMode() {
        val next = RotationMode.next(RotationPrefs.load(this))
        val handled = RotationService.setMode(this, next)

        // 极少数系统会拦截「后台启动前台服务」；此时把用户带回界面完成同一次操作
        if (!handled) {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PENDING_MODE, next.key)
            runCatching { startActivity(intent) }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        // 磁贴服务实例可能长期存活，文案不能缓存在字段里：每次刷新都按当前语言现取。
        // 这里频率极低（onStartListening / onClick），现场包一次可以接受。
        val ui = DisplayContext.wrapLocale(this)
        val mode = RotationPrefs.load(this)
        // 偏好里记的是“上次选择”，磁贴只在该方向真的被持有的时候才点亮
        val holding = mode.isAuto || RotationRuntime.isHolding(mode)
        tile.state = if (holding && !mode.isAuto) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = ui.getString(R.string.tile_label)
        // 副标题与主界面那颗状态胶囊同源（status_auto / status_locked / status_pending），
        // 磁贴、主界面、磁贴弹出的选择窗三处对「已锁定 / 未生效」只有一种写法。
        tile.subtitle = when {
            mode.isAuto -> ui.getString(R.string.status_auto)
            holding -> ui.getString(R.string.status_locked, ui.getString(mode.labelRes))
            else -> ui.getString(R.string.status_pending, ui.getString(mode.labelRes))
        }
        // 图标跟着当前方向走，与选择窗里的五行、通知栏控制条共用同一套「手机 + 摄像头圆点」；
        // 原来固定用 ic_tile_service（Material screen_rotation），磁贴是唯一不分方向的入口。
        tile.icon = Icon.createWithResource(this, mode.tileIconRes)
        tile.updateTile()
    }

    companion object {
        private const val TAG = "RotationTileService"

        /** 弹出选择窗的 PendingIntent 请求码，与其它 PendingIntent 区分开。 */
        private const val REQUEST_MENU = 200

        /** 磁贴点击未能直接生效时，带在 Intent 里让主界面补上。 */
        const val EXTRA_PENDING_MODE = "extra_pending_mode"

        /** 方向变化后请求系统刷新磁贴（仅在磁贴存在时有效）。 */
        fun requestRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, RotationTileService::class.java)
                )
            }
        }
    }
}
