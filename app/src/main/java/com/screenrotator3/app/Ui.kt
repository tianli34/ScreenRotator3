package com.screenrotator3.app

import android.content.Context
import android.graphics.Color
import kotlin.math.roundToInt

/** 线性混合两个 ARGB 颜色（含 alpha），用于状态切换时的颜色过渡。 */
fun blendColor(from: Int, to: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun mix(a: Int, b: Int) = (a + (b - a) * f).roundToInt()
    return Color.argb(
        mix(Color.alpha(from), Color.alpha(to)),
        mix(Color.red(from), Color.red(to)),
        mix(Color.green(from), Color.green(to)),
        mix(Color.blue(from), Color.blue(to))
    )
}

fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density).roundToInt()

fun Context.dp(value: Int): Int = dp(value.toFloat())
