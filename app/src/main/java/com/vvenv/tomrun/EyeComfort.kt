package com.vvenv.tomrun

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

/**
 * 全局护眼调色：面向中小学玩家，画面要能长时间看而不累。
 *
 * 一条公式，三件事：
 * 1. **降饱和**——把霓虹拉回写实像素的泥土 / 草木 / 石灰调，画面不再「花」；
 * 2. **抬黑压白**——最暗不到纯黑、最亮不到纯白，去掉刺眼的极端对比；
 * 3. **轻微暖偏**——削一点蓝光，整体偏暖更耐看。
 *
 * 三条出图路径都走这里，保证赛道、HUD、藏品图三者观感一致：
 * - 3D 世界：[GameRenderer] 在 emit / 天空 / 雾处调 [grade]
 * - HUD：`HudView.EyeSafePaint` 覆盖 setColor 调 [eyeSafe]
 * - 藏品位图：[RelicIcons] 给 blitPaint 挂 [colorFilter]
 *
 * 各处元素原有的色相与明暗关系（如「铜褐避开草绿」）都是手工调过的，
 * 所以这里只做统一分级，不改单个色值常量。想整体加减刺激度，改下面四个数即可。
 */
object EyeComfort {

    /** 保留多少饱和度：越低越接近写实泥土调 */
    const val SAT = 0.74f
    /** 抬黑：最暗也不到纯黑，暗部不再「死死一块」 */
    const val LIFT = 0.07f
    /** 压白：最亮不到纯白，去掉晃眼的高光 */
    const val CEIL = 0.90f
    /** 逐通道暖偏：削一点蓝光 */
    val WARM = floatArrayOf(1.02f, 1.0f, 0.94f)

    private const val LUM_R = 0.299f
    private const val LUM_G = 0.587f
    private const val LUM_B = 0.114f

    /** 归一化 RGBA 分级，写入 [out] 并返回它；alpha 原样保留。 */
    fun grade(src: FloatArray, out: FloatArray): FloatArray {
        val lum = src[0] * LUM_R + src[1] * LUM_G + src[2] * LUM_B
        for (i in 0..2) {
            val v = (lum + (src[i] - lum) * SAT) * WARM[i]
            out[i] = (LIFT + v * (CEIL - LIFT)).coerceIn(0f, 1f)
        }
        out[3] = src[3]
        return out
    }

    /** ARGB 整数分级；alpha 原样保留。 */
    fun eyeSafe(c: Int): Int {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val lum = r * LUM_R + g * LUM_G + b * LUM_B
        var out = c and 0xFF000000.toInt()
        for (i in 0..2) {
            val src = when (i) {
                0 -> r
                1 -> g
                else -> b
            }
            val v = (lum + (src - lum) * SAT) * WARM[i]
            val graded = (LIFT + v * (CEIL - LIFT)).coerceIn(0f, 1f)
            out = out or ((graded * 255f + 0.5f).toInt().coerceIn(0, 255) shl ((2 - i) * 8))
        }
        return out
    }

    /**
     * 同一条公式的矩阵形式，供位图绘制使用。
     *
     * 分级全部是线性运算，可精确写成色彩矩阵：
     * `out_i = LIFT*255 + (CEIL-LIFT)*WARM_i * (SAT*src_i + (1-SAT)*lum)`
     */
    fun colorFilter(): ColorMatrixColorFilter {
        val m = FloatArray(20)
        val lumW = floatArrayOf(LUM_R, LUM_G, LUM_B)
        for (i in 0..2) {
            val k = (CEIL - LIFT) * WARM[i]
            for (j in 0..2) {
                m[i * 5 + j] = k * ((1f - SAT) * lumW[j] + if (i == j) SAT else 0f)
            }
            m[i * 5 + 4] = LIFT * 255f
        }
        m[18] = 1f // alpha 直通
        return ColorMatrixColorFilter(ColorMatrix(m))
    }
}
