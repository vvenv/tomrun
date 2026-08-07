package com.vvenv.tomrun

/**
 * 猫本体与装扮的配色——跑酷里的猫（[GameRenderer.drawCat]）和家里那只
 * （[HomeScene3D.drawYardCat]）是同一只猫，颜色只能有一份。
 *
 * 值是归一化 RGBA，直接喂给 GL；出图前统一走 [EyeComfort.grade]。
 */
object CatPalette {

    /** 蓝灰 / 橘黄 / 乌黑 / 粉红 / 奶白 / 青绿 / 紫罗兰 / 棕褐 */
    val MAIN = arrayOf(
        floatArrayOf(0.52f, 0.58f, 0.70f, 1f),
        floatArrayOf(0.95f, 0.62f, 0.26f, 1f),
        floatArrayOf(0.30f, 0.30f, 0.35f, 1f),
        floatArrayOf(0.96f, 0.66f, 0.76f, 1f),
        floatArrayOf(0.93f, 0.90f, 0.82f, 1f),
        floatArrayOf(0.35f, 0.72f, 0.65f, 1f),
        floatArrayOf(0.62f, 0.50f, 0.82f, 1f),
        floatArrayOf(0.62f, 0.44f, 0.30f, 1f)
    )
    val DARK = arrayOf(
        floatArrayOf(0.36f, 0.42f, 0.55f, 1f),
        floatArrayOf(0.76f, 0.42f, 0.12f, 1f),
        floatArrayOf(0.16f, 0.16f, 0.21f, 1f),
        floatArrayOf(0.82f, 0.47f, 0.60f, 1f),
        floatArrayOf(0.72f, 0.67f, 0.56f, 1f),
        floatArrayOf(0.20f, 0.50f, 0.45f, 1f),
        floatArrayOf(0.44f, 0.33f, 0.62f, 1f),
        floatArrayOf(0.42f, 0.28f, 0.18f, 1f)
    )
    val WHITE = floatArrayOf(0.95f, 0.95f, 0.92f, 1f)
    val PINK = floatArrayOf(0.96f, 0.62f, 0.72f, 1f)
    val NOSE = floatArrayOf(0.92f, 0.42f, 0.52f, 1f)
    val EYE = floatArrayOf(0.12f, 0.12f, 0.14f, 1f)
    val EYE_HL = floatArrayOf(1f, 1f, 1f, 1f)

    /** 围巾（下标 0 为「无」，占位） */
    val SCARF = arrayOf(
        floatArrayOf(0f, 0f, 0f, 0f),
        floatArrayOf(0.95f, 0.25f, 0.25f, 1f),
        floatArrayOf(0.30f, 0.85f, 0.95f, 1f),
        floatArrayOf(0.65f, 0.45f, 1.0f, 1f)
    )
    val HAT_RED = floatArrayOf(0.88f, 0.27f, 0.27f, 1f)
    val HAT_RED_DK = floatArrayOf(0.65f, 0.17f, 0.17f, 1f)
    val STRAW = floatArrayOf(0.85f, 0.77f, 0.42f, 1f)
    val STRAW_BAND = floatArrayOf(0.30f, 0.62f, 0.30f, 1f)
    val CROWN_GOLD = floatArrayOf(0.95f, 0.78f, 0.20f, 1f)
    val CROWN_RUBY = floatArrayOf(0.90f, 0.20f, 0.35f, 1f)

    /** 相对世界的体型；与 [GameRenderer] 里跑酷猫同一档，家里那只不能忽大忽小 */
    const val SCALE = 0.80f
}
