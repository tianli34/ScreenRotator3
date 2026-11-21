package com.screenrotator3.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * 悬浮球窗口管理：负责吸附、拖动、展开/收起时的窗口尺寸与位置，
 * 图形与命中判定交给 [FloatingBallView]。
 *
 * 展开/收起是「一个窗口、一条曲线」：窗口矩形（x / y / 边长）和内容的展开进度
 * 由同一个 ValueAnimator 逐帧推进。
 *
 * 上一版是两段拼出来的：内容先滑到一个「锚点」（= 球心 − 被推进屏幕后的盘心），
 * 动画结束后再把窗口从 200dp 缩回 54dp，最后还补一次贴边。
 * 于是收起时球走过的位移 = 盘面为了不超出屏幕被推动的距离：贴左右边是横向一段，
 * 靠上/靠下还要多一段纵向的。左右两侧被推的量不同、方向合成也不同，
 * 看起来就是一边笔直往下掉、一边斜着掉，而且滑完还要再跳一下。
 *
 * 现在落点直接算成「已经贴好边」的球位，收缩和贴边一次走完，没有第二段。
 * 球的位置只信两个相对量：贴哪一边（[onLeft]）、纵向相对高度（[yRatio]），
 * 屏幕尺寸变化时按这两个量重算像素位置，球不会留在新屏幕外。
 */
class FloatingBallController(
    private val context: Context,
    private val onSelect: (RotationMode) -> Unit
) {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)
    private val displayManager: DisplayManager =
        context.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var params: WindowManager.LayoutParams? = null
    private var ballView: FloatingBallView? = null
    private var frameAnimator: ValueAnimator? = null
    private var expanded = false
    private var dragging = false
    private var currentMode = RotationMode.AUTO

    /** 球贴左边还是右边。转屏后靠它把球放回同一条边。 */
    private var onLeft = false

    /** 球心的纵向相对高度（0..1）。转屏后靠它把球放回同一高度。 */
    private var yRatio = DEFAULT_Y_RATIO

    /** 上一次已知的屏幕尺寸，用来过滤「尺寸没变」的 display 回调（刷新率变化也会回调）。 */
    private var knownWidth = 0
    private var knownHeight = 0

    /** 收起播完后要执行的动作（选中方向 = 等收起播完再转屏）。动画被取消也会执行。 */
    private var afterCollapse: (() -> Unit)? = null

    val isAttached: Boolean get() = ballView != null

    private val autoCollapse = Runnable { collapse() }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) settleIntoBounds()
        }
    }

    fun setMode(mode: RotationMode) {
        currentMode = mode
        ballView?.mode = mode
    }

    fun attach(): Boolean {
        if (ballView != null) return true
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "缺少悬浮窗权限，无法创建悬浮球")
            return false
        }
        val frame = restFrame()
        val p = WindowManager.LayoutParams(
            frame.size,
            frame.size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = frame.x
            y = frame.y
        }

        val view = FloatingBallView(context)
        view.mode = currentMode
        view.callbacks = object : FloatingBallView.Callbacks {
            override fun onTap() = expand()

            override fun onDragBy(dx: Float, dy: Float) {
                dragging = true
                // 手指压过来就掐掉吸附动画，否则两处都在改 x，球会被来回拽
                cancelFrameAnimation()
                val lp = params ?: return
                val b = windowManager.currentWindowMetrics.bounds
                lp.x = (lp.x + dx).roundToInt().coerceIn(0, (b.width() - lp.width).coerceAtLeast(0))
                lp.y = (lp.y + dy).roundToInt().coerceIn(0, (b.height() - lp.height).coerceAtLeast(0))
                update(view, lp)
            }

            override fun onDragEnd() {
                dragging = false
                snapToEdge()
            }

            override fun onSelect(mode: RotationMode) {
                // 等收起动画播完再换方向：转屏会把屏幕尺寸换掉，收在半途就转，
                // 动画剩下的每一帧都指向旧屏幕的坐标，看着就是乱飞。
                collapse {
                    // 必须显式指向构造参数里的回调：直接写 onSelect(mode) 会解析成
                    // 这个匿名对象自己的方法，把自己递归调用成 StackOverflowError。
                    this@FloatingBallController.onSelect(mode)
                }
            }
        }

        return try {
            windowManager.addView(view, p)
            params = p
            ballView = view
            displayManager.registerDisplayListener(displayListener, handler)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "添加悬浮球失败", t)
            false
        }
    }

    fun detach() {
        handler.removeCallbacks(autoCollapse)
        // 窗口都要撤了，别再补一次「收起后转屏」
        afterCollapse = null
        cancelFrameAnimation()
        val view = ballView ?: return
        ballView = null
        params = null
        expanded = false
        dragging = false
        displayManager.unregisterDisplayListener(displayListener)
        runCatching { windowManager.removeView(view) }
            .onFailure { Log.w(TAG, "移除悬浮球失败", it) }
    }

    private fun expand() {
        val p = params ?: return
        val view = ballView ?: return
        if (expanded) return
        expanded = true
        view.expanded = true

        // 面板以球心为中心展开，贴边时由 panelFrameFor 把整块面板推进屏幕内
        val target = panelFrameFor(p.x + p.width / 2f, p.y + p.height / 2f)
        animateFrame(target, 1f, EXPAND_MS)

        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, AUTO_COLLAPSE_DELAY_MS)
    }

    private fun collapse(after: (() -> Unit)? = null) {
        val view = ballView ?: return
        if (!expanded) {
            after?.invoke()
            return
        }
        expanded = false
        view.expanded = false
        handler.removeCallbacks(autoCollapse)
        // 落点直接算成「已经贴好边」的球位，收紧过程里一次走完，
        // 不再等动画结束再补一次贴边位移（那一下就是看得见的「跳」）
        afterCollapse = after
        animateFrame(restFrame(), 0f, COLLAPSE_MS) { runAfterCollapse() }
    }

    private fun runAfterCollapse() {
        val action = afterCollapse ?: return
        afterCollapse = null
        action()
    }

    /** 拖动结束：记住贴哪边、多高，然后把球吸到边距上。 */
    private fun snapToEdge() {
        val p = params ?: return
        if (expanded) return
        rememberPosition(p.x + p.width / 2f, p.y + p.height / 2f)
        // 一定是收起态：中途被拖动打断时 fraction 可能停在半开，
        // 这里必须收到 0，否则球尺寸的窗口里会留下半个盘
        animateFrame(restFrame(), 0f, SNAP_MS)
    }

    /**
     * 屏幕尺寸变了（转屏 / 分辨率切换）。
     *
     * 正在跑的动画必须掐掉：它剩下的每一帧都还指向旧方向的像素坐标。
     * 掐掉之后按相对位置把球摆回新屏幕，转屏本身已经把画面盖住，看不出断点。
     */
    private fun settleIntoBounds() {
        if (ballView == null) return
        // 拖动中不插手：手指还在拖着，坐标由 onDragBy 负责
        if (dragging) return
        val bounds = windowManager.currentWindowMetrics.bounds
        if (bounds.width() == knownWidth && bounds.height() == knownHeight) return
        knownWidth = bounds.width()
        knownHeight = bounds.height()
        cancelFrameAnimation()
        if (expanded) {
            val home = restFrame()
            applyFrame(panelFrameFor(home.cx, home.cy), 1f)
        } else {
            applyFrame(restFrame(), 0f)
        }
    }

    private fun rememberPosition(cx: Float, cy: Float) {
        val bounds = windowManager.currentWindowMetrics.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        onLeft = cx < bounds.width() / 2f
        yRatio = (cy / bounds.height()).coerceIn(0f, 1f)
    }

    /** 收起状态：球贴住左/右边距，纵向停在 [yRatio] 处。 */
    private fun restFrame(): Frame {
        val bounds = windowManager.currentWindowMetrics.bounds
        val ball = context.dp(BALL_DP)
        val margin = context.dp(EDGE_MARGIN_DP)
        val cx = if (onLeft) margin + ball / 2f else bounds.width() - margin - ball / 2f
        val cy = bounds.height() * yRatio
        return Frame(
            x = (cx - ball / 2f).roundToInt().coerceIn(0, (bounds.width() - ball).coerceAtLeast(0)),
            y = (cy - ball / 2f).roundToInt().coerceIn(0, (bounds.height() - ball).coerceAtLeast(0)),
            size = ball
        )
    }

    /** 展开状态：尽量以 [cx] / [cy] 为中心，但整块面板必须留在屏幕内。 */
    private fun panelFrameFor(cx: Float, cy: Float): Frame {
        val bounds = windowManager.currentWindowMetrics.bounds
        val panel = context.dp(PANEL_DP)
        return Frame(
            x = (cx - panel / 2f).roundToInt().coerceIn(0, (bounds.width() - panel).coerceAtLeast(0)),
            y = (cy - panel / 2f).roundToInt().coerceIn(0, (bounds.height() - panel).coerceAtLeast(0)),
            size = panel
        )
    }

    private fun applyFrame(frame: Frame, fraction: Float) {
        val p = params ?: return
        val view = ballView ?: return
        p.x = frame.x
        p.y = frame.y
        p.width = frame.size
        p.height = frame.size
        view.fraction = fraction
        update(view, p)
    }

    /**
     * 让「窗口矩形 + 展开进度」沿同一条曲线一起走。
     *
     * 两者必须同步：窗口先缩、内容后收会画出半个面板；内容先收、窗口后缩，
     * 球就会在终点跳一下。用同一个 t 驱动就不会有这类时序问题。
     */
    private fun animateFrame(
        target: Frame,
        targetFraction: Float,
        duration: Long,
        onEnd: (() -> Unit)? = null
    ) {
        val p = params ?: return
        val view = ballView ?: return
        cancelFrameAnimation()

        val fromX = p.x
        val fromY = p.y
        val fromSize = p.width
        val fromFraction = view.fraction

        val render: (Float) -> Unit = { t ->
            view.fraction = fromFraction + (targetFraction - fromFraction) * t
            val size = (fromSize + (target.size - fromSize) * t).roundToInt()
            val x = (fromX + (target.x - fromX) * t).roundToInt()
            val y = (fromY + (target.y - fromY) * t).roundToInt()
            if (x != p.x || y != p.y || size != p.width) {
                p.x = x
                p.y = y
                p.width = size
                p.height = size
                update(view, p)
            }
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = duration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { render(it.animatedValue as Float) }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
                if (frameAnimator === animator) frameAnimator = null
                // 被取消（拖动、换目标、转屏）不补最后那一帧，避免把状态钉回旧目标；
                // 但挂在动画后面的动作（比如「收起后再转屏」）仍需执行，不然方向就丢了。
                onEnd?.invoke()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                if (frameAnimator === animator) frameAnimator = null
                render(1f)
                onEnd?.invoke()
            }
        })
        frameAnimator = animator
        animator.start()
    }

    private fun cancelFrameAnimation() {
        val animator = frameAnimator ?: return
        frameAnimator = null
        animator.cancel()
    }

    private fun update(view: FloatingBallView, p: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(view, p) }
    }

    /** 窗口矩形：左上角坐标 + 边长（球与面板都是正方形）。 */
    private data class Frame(val x: Int, val y: Int, val size: Int) {
        val cx: Float get() = x + size / 2f
        val cy: Float get() = y + size / 2f
    }

    private companion object {
        const val TAG = "FloatingBall"
        const val BALL_DP = 54
        const val PANEL_DP = 200
        const val EDGE_MARGIN_DP = 4

        /** 球心初始的纵向相对高度 */
        const val DEFAULT_Y_RATIO = 0.45f

        const val EXPAND_MS = 210L
        const val COLLAPSE_MS = 160L
        const val SNAP_MS = 200L
        const val AUTO_COLLAPSE_DELAY_MS = 5_000L
    }
}
