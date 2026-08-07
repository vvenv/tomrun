package com.vvenv.tomrun

import kotlin.math.min

/**
 * 昼夜调制：黄昏加暖橙、夜里压暗偏蓝紫。
 *
 * 这条公式原本只长在 [GameRenderer] 里，于是跑酷天空会从正午走到黄昏再入夜，
 * 家页面却永远停在大晴天正午——同一个世界的两个地方，亮度差了将近一倍
 * （实测草地 L=70 对 L=160），看上去像两款游戏。
 *
 * 现在两条出图路径共用这里：
 * - 3D 世界：[GameRenderer.updateWeatherColors] 对天空 / 草地 / 路面调 [apply]
 * - 家园场景：`HudView.EyeSafePaint` 在画庭院期间对每个颜色调 [tint]
 *
 * 顺序固定是**先昼夜、后护眼分级**（[EyeComfort]），与 GL 侧一致；反过来会让
 * 夜色被分级的抬黑重新提亮，压不下去。
 *
 * 强度由 [Game.duskAmount] / [Game.nightAmount] 给出，二者都读同一个 `dayPhase`，
 * 而 `dayPhase` 在 `Game.update` 的第一行就无条件推进——在家发呆时天色照样会变。
 */
object DayNight {

    /** 归一化 RGB 原地调制，供 GL 侧使用；alpha 不动。 */
    fun apply(c: FloatArray, dusk: Float, night: Float) {
        if (dusk > 0.01f) {
            c[0] = c[0] * (1f - 0.15f * dusk) + 1.0f * 0.15f * dusk
            c[1] = c[1] * (1f - 0.25f * dusk) + 0.55f * 0.15f * dusk
            c[2] = c[2] * (1f - 0.45f * dusk)
        }
        if (night > 0.01f) {
            val dim = 1f - 0.55f * night
            c[0] *= dim * (1f - 0.15f * night)
            c[1] *= dim * (1f - 0.05f * night)
            c[2] = min(1f, c[2] * dim + 0.08f * night)
        }
    }

    /** 同一条公式的 ARGB 整数形式，供 Canvas 侧使用；alpha 原样保留。 */
    fun tint(c: Int, dusk: Float, night: Float): Int {
        if (dusk <= 0.01f && night <= 0.01f) return c
        var r = ((c shr 16) and 0xFF) / 255f
        var g = ((c shr 8) and 0xFF) / 255f
        var b = (c and 0xFF) / 255f
        if (dusk > 0.01f) {
            r = r * (1f - 0.15f * dusk) + 1.0f * 0.15f * dusk
            g = g * (1f - 0.25f * dusk) + 0.55f * 0.15f * dusk
            b = b * (1f - 0.45f * dusk)
        }
        if (night > 0.01f) {
            val dim = 1f - 0.55f * night
            r *= dim * (1f - 0.15f * night)
            g *= dim * (1f - 0.05f * night)
            b = min(1f, b * dim + 0.08f * night)
        }
        fun q(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (c and 0xFF000000.toInt()) or (q(r) shl 16) or (q(g) shl 8) or q(b)
    }
}
