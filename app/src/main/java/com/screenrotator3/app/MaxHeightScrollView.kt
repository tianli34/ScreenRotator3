package com.screenrotator3.app

import android.content.Context
import android.util.AttributeSet
import android.widget.ScrollView

/**
 * 高度受限的 ScrollView。
 *
 * 设置面板是「固定表头 + 可滚动内容 + 贴底」的组合：横屏时可用高度只有 ~390dp，
 * 整块面板必须留在屏幕内、并且表头不能被顶出屏幕上沿，所以滚动区需要一个明确的上限。
 * 比例按物理屏幕高度算（面板本身还有表头与底部安全区，故只取 62%），
 * 竖屏正常内容根本达不到这个高度，滚动条自然不出现。
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    var maxHeightFraction: Float = 0.62f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
        )
    }
}
