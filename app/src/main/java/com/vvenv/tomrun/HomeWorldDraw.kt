package com.vvenv.tomrun

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.sin

/**
 * 各世界房屋与庭院装饰的像素造型（配色仍由 HudView 的 HOME_* 表提供）。
 */
object HomeWorldDraw {

    class Gfx(
        val canvas: Canvas,
        val paint: Paint,
        val s: Float,
        val homePhase: Float,
        val rc: (Float, Float, Float, Float, Int) -> Unit,
        val boxShade: (Float, Float, Float, Float) -> Unit,
        val pyramidRoof: (Float, Float, Float, Float, Int, Int) -> Unit,
        val door: (Float) -> Unit,
        val window: (Float, Float, Int) -> Unit,
        val groundShadow: (Float, Float, Float) -> Unit,
        val withAlpha: (Int, Int) -> Int
    )

    fun drawHouse(
        world: Int, house: Int, cx: Float, gy: Float,
        pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx
    ) {
        when (world.coerceIn(0, Game.UNIVERSE_COUNT - 1)) {
            Game.UNI_MEADOW -> drawHouseMeadow(house, cx, gy, pal, roofC, roofD, win, g)
            Game.UNI_WATER -> drawHouseWater(house, cx, gy, pal, roofC, roofD, win, g)
            Game.UNI_SKY -> drawHouseSky(house, cx, gy, pal, roofC, roofD, win, g)
            Game.UNI_LAVA -> drawHouseLava(house, cx, gy, pal, roofC, roofD, win, g)
            Game.UNI_CANDY -> drawHouseCandy(house, cx, gy, pal, roofC, roofD, win, g)
            else -> drawHouseSpace(house, cx, gy, pal, roofC, roofD, win, g)
        }
    }

    fun drawDeco(
        world: Int, deco: Int, cx: Float, gy: Float, half: Float, alpha: Int,
        flowers: IntArray, stem: Int, fence: IntArray, poolWater: IntArray, flags: IntArray,
        g: Gfx,
        gardenX: Float, gardenY: Float, fenceY: Float, mailboxX: Float, mailboxY: Float,
        swingX: Float, swingY: Float, perchX: Float, perchY: Float,
        poolL: Float, poolR: Float, poolT: Float, poolB: Float,
        telescopeX: Float, telescopeY: Float
    ) {
        val s = g.s
        when (deco) {
            0 -> drawGarden(world, cx, gy, s, alpha, flowers, stem, g, gardenX, gardenY)
            1 -> drawFence(world, cx, gy, half, s, alpha, fence, g, fenceY)
            2 -> drawMailbox(world, cx, gy, s, alpha, g, mailboxX, mailboxY)
            3 -> drawSwing(world, cx, gy, s, alpha, g, swingX, swingY)
            4 -> drawPerch(world, cx, gy, s, alpha, g, perchX, perchY)
            5 -> drawPool(world, cx, gy, s, alpha, poolWater, g, poolL, poolR, poolT, poolB)
            6 -> drawTelescope(world, cx, gy, s, alpha, g, telescopeX, telescopeY)
            else -> drawFlags(cx, gy, half, s, alpha, flags, g)
        }
    }

    // ---------- 房屋：草原（原版造型） ----------
    private fun drawHouseMeadow(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc
        when (house.coerceIn(0, 3)) {
            0 -> {
                rc(cx - 80f * s, gy - 110f * s, cx + 80f * s, gy, pal[0])
                for (i in 0..3) rc(cx - 80f * s, gy - 110f * s + i * 28f * s, cx + 80f * s, gy - 108f * s + i * 28f * s, pal[1])
                g.boxShade(cx - 80f * s, gy - 110f * s, cx + 80f * s, gy)
                g.pyramidRoof(cx, gy - 110f * s, 104f * s, 46f * s, roofC, roofD)
                g.door(cx + 34f * s); g.window(cx - 40f * s, gy - 62f * s, win)
            }
            1 -> {
                rc(cx - 100f * s, gy - 122f * s, cx + 100f * s, gy, pal[0])
                for (r in 0..4) for (c in 0..5) {
                    val bx = cx - 100f * s + (c * 34f + if (r % 2 == 0) 0f else 17f) * s
                    rc(bx, gy - 122f * s + r * 25f * s, bx + 15f * s, gy - 120f * s + r * 25f * s, pal[1])
                }
                g.boxShade(cx - 100f * s, gy - 122f * s, cx + 100f * s, gy)
                g.pyramidRoof(cx, gy - 122f * s, 126f * s, 50f * s, roofC, roofD)
                g.door(cx - 52f * s); g.window(cx + 14f * s, gy - 66f * s, win); g.window(cx + 58f * s, gy - 66f * s, win)
            }
            2 -> {
                rc(cx - 100f * s, gy - 190f * s, cx + 100f * s, gy, pal[0])
                rc(cx - 100f * s, gy - 100f * s, cx + 100f * s, gy - 92f * s, pal[1])
                g.boxShade(cx - 100f * s, gy - 190f * s, cx + 100f * s, gy)
                g.pyramidRoof(cx, gy - 190f * s, 126f * s, 48f * s, roofC, roofD)
                g.door(cx); g.window(cx - 64f * s, gy - 52f * s, win); g.window(cx + 64f * s, gy - 52f * s, win)
                g.window(cx - 64f * s, gy - 142f * s, win); g.window(cx + 64f * s, gy - 142f * s, win)
                rc(cx - 30f * s, gy - 126f * s, cx + 30f * s, gy - 118f * s, pal[2])
            }
            else -> drawCastle(cx, gy, pal, roofC, roofD, g)
        }
    }

    // ---------- 水下：圆顶 / 贝壳 / 礁塔 / 水晶 ----------
    private fun drawHouseWater(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc; val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true
        when (house.coerceIn(0, 3)) {
            0 -> {
                rc(cx - 72f * s, gy - 48f * s, cx + 72f * s, gy, pal[0])
                g.paint.color = pal[0]; g.canvas.drawOval(cx - 78f * s, gy - 118f * s, cx + 78f * s, gy - 38f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 62f * s, gy - 108f * s, cx + 62f * s, gy - 52f * s, g.paint)
                g.window(cx - 28f * s, gy - 78f * s, win); g.door(cx + 24f * s)
            }
            1 -> {
                rc(cx - 88f * s, gy - 90f * s, cx + 88f * s, gy, pal[0])
                g.paint.color = pal[1]
                g.canvas.drawArc(cx - 96f * s, gy - 130f * s, cx + 96f * s, gy - 20f * s, 200f, 140f, true, g.paint)
                g.window(cx, gy - 72f * s, win); g.door(cx - 40f * s)
            }
            2 -> {
                rc(cx - 90f * s, gy - 170f * s, cx + 90f * s, gy, pal[0])
                rc(cx - 90f * s, gy - 96f * s, cx + 90f * s, gy - 88f * s, pal[1])
                g.paint.color = roofC
                g.canvas.drawOval(cx - 40f * s, gy - 188f * s, cx + 40f * s, gy - 158f * s, g.paint)
                g.window(cx - 50f * s, gy - 56f * s, win); g.window(cx + 50f * s, gy - 56f * s, win)
                g.window(cx, gy - 140f * s, win); g.door(cx)
            }
            else -> {
                rc(cx - 70f * s, gy - 140f * s, cx + 70f * s, gy, pal[0])
                g.paint.color = g.withAlpha(pal[1], 180)
                g.canvas.drawOval(cx - 100f * s, gy - 200f * s, cx - 30f * s, gy - 120f * s, g.paint)
                g.canvas.drawOval(cx + 30f * s, gy - 200f * s, cx + 100f * s, gy - 120f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 50f * s, gy - 168f * s, cx + 50f * s, gy - 128f * s, g.paint)
                g.door(cx); g.window(cx - 36f * s, gy - 90f * s, win); g.window(cx + 36f * s, gy - 90f * s, win)
            }
        }
        g.paint.isAntiAlias = aa
    }

    // ---------- 天空：云基 / 风塔 / 云楼 / 天堡 ----------
    private fun drawHouseSky(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc; val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true
        when (house.coerceIn(0, 3)) {
            0 -> {
                g.paint.color = pal[0]
                g.canvas.drawOval(cx - 90f * s, gy - 28f * s, cx + 90f * s, gy + 10f * s, g.paint)
                rc(cx - 70f * s, gy - 100f * s, cx + 70f * s, gy - 20f * s, pal[0])
                g.paint.color = roofC; g.canvas.drawOval(cx - 82f * s, gy - 118f * s, cx + 82f * s, gy - 88f * s, g.paint)
                g.door(cx + 20f * s); g.window(cx - 30f * s, gy - 62f * s, win)
            }
            1 -> {
                rc(cx - 88f * s, gy - 118f * s, cx + 88f * s, gy, pal[0])
                rc(cx - 8f * s, gy - 168f * s, cx + 8f * s, gy - 118f * s, pal[2])
                g.paint.color = roofC
                g.canvas.drawRect(cx - 28f * s, gy - 178f * s, cx + 28f * s, gy - 158f * s, g.paint)
                for (i in -1..1) g.window(cx + i * 44f * s, gy - 72f * s, win)
                g.door(cx - 40f * s)
            }
            2 -> {
                rc(cx - 82f * s, gy - 178f * s, cx + 82f * s, gy, pal[0])
                rc(cx - 82f * s, gy - 98f * s, cx + 82f * s, gy - 90f * s, pal[1])
                g.pyramidRoof(cx, gy - 178f * s, 100f * s, 44f * s, roofC, roofD)
                g.door(cx); g.window(cx - 48f * s, gy - 56f * s, win); g.window(cx + 48f * s, gy - 56f * s, win)
                g.window(cx, gy - 140f * s, win)
            }
            else -> {
                rc(cx - 76f * s, gy - 150f * s, cx + 76f * s, gy, pal[0])
                g.paint.color = pal[0]; g.canvas.drawOval(cx - 110f * s, gy - 210f * s, cx - 50f * s, gy - 150f * s, g.paint)
                g.canvas.drawOval(cx + 50f * s, gy - 210f * s, cx + 110f * s, gy - 150f * s, g.paint)
                g.pyramidRoof(cx, gy - 150f * s, 88f * s, 38f * s, roofC, roofD)
                g.door(cx); g.window(cx - 40f * s, gy - 88f * s, win); g.window(cx + 40f * s, gy - 88f * s, win)
            }
        }
        g.paint.isAntiAlias = aa
    }

    // ---------- 熔岩：焦木 / 黑曜 / 熔楼 / 火山堡 ----------
    private fun drawHouseLava(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc
        when (house.coerceIn(0, 3)) {
            0 -> {
                rc(cx - 76f * s, gy - 108f * s, cx + 76f * s, gy, pal[0])
                rc(cx - 6f * s, gy - 148f * s, cx + 6f * s, gy - 108f * s, 0xFF4A3028.toInt())
                g.pyramidRoof(cx, gy - 108f * s, 98f * s, 44f * s, roofC, roofD)
                g.door(cx + 28f * s); g.window(cx - 36f * s, gy - 60f * s, win)
            }
            1 -> {
                rc(cx - 96f * s, gy - 118f * s, cx + 96f * s, gy, pal[0])
                for (i in 0..5) rc(cx - 96f * s + i * 32f * s, gy - 118f * s, cx - 88f * s + i * 32f * s, gy, pal[1])
                g.pyramidRoof(cx, gy - 118f * s, 118f * s, 48f * s, roofC, roofD)
                g.door(cx - 44f * s); g.window(cx + 20f * s, gy - 68f * s, win)
            }
            2 -> {
                rc(cx - 94f * s, gy - 182f * s, cx + 94f * s, gy, pal[0])
                rc(cx - 94f * s, gy - 102f * s, cx + 94f * s, gy - 94f * s, 0xFFFF7A2A.toInt())
                g.pyramidRoof(cx, gy - 182f * s, 112f * s, 46f * s, roofC, roofD)
                g.door(cx); g.window(cx - 58f * s, gy - 54f * s, win); g.window(cx + 58f * s, gy - 54f * s, win)
                g.window(cx, gy - 138f * s, win)
            }
            else -> {
                rc(cx - 80f * s, gy - 148f * s, cx + 80f * s, gy, pal[0])
                rc(cx - 120f * s, gy - 198f * s, cx - 76f * s, gy - 60f * s, pal[1])
                rc(cx + 76f * s, gy - 198f * s, cx + 120f * s, gy - 60f * s, pal[1])
                rc(cx - 10f * s, gy - 210f * s, cx + 10f * s, gy - 148f * s, 0xFFFF5A2A.toInt())
                g.door(cx); g.window(cx - 48f * s, gy - 96f * s, win); g.window(cx + 48f * s, gy - 96f * s, win)
            }
        }
    }

    // ---------- 糖果：姜饼 / 纸杯 / 糖楼 / 糖堡 ----------
    private fun drawHouseCandy(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc; val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true
        when (house.coerceIn(0, 3)) {
            0 -> {
                rc(cx - 74f * s, gy - 104f * s, cx + 74f * s, gy, pal[0])
                rc(cx - 74f * s, gy - 104f * s, cx + 74f * s, gy - 100f * s, 0xFFFFF8F0.toInt())
                g.pyramidRoof(cx, gy - 104f * s, 96f * s, 42f * s, roofC, roofD)
                g.door(cx + 26f * s); g.window(cx - 32f * s, gy - 58f * s, win)
            }
            1 -> {
                g.paint.color = pal[0]; g.canvas.drawRoundRect(cx - 70f * s, gy - 120f * s, cx + 70f * s, gy, 18f * s, 18f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 78f * s, gy - 148f * s, cx + 78f * s, gy - 108f * s, g.paint)
                g.door(cx - 30f * s); g.window(cx + 24f * s, gy - 72f * s, win)
            }
            2 -> {
                rc(cx - 86f * s, gy - 176f * s, cx + 86f * s, gy, pal[0])
                rc(cx - 86f * s, gy - 98f * s, cx + 86f * s, gy - 90f * s, 0xFFFFF8F0.toInt())
                g.pyramidRoof(cx, gy - 176f * s, 108f * s, 44f * s, roofC, roofD)
                g.door(cx); g.window(cx - 50f * s, gy - 52f * s, win); g.window(cx + 50f * s, gy - 52f * s, win)
            }
            else -> {
                rc(cx - 78f * s, gy - 142f * s, cx + 78f * s, gy, pal[0])
                g.paint.color = roofC
                g.canvas.drawCircle(cx - 90f * s, gy - 168f * s, 22f * s, g.paint)
                g.canvas.drawCircle(cx + 90f * s, gy - 168f * s, 22f * s, g.paint)
                g.pyramidRoof(cx, gy - 142f * s, 92f * s, 36f * s, roofC, roofD)
                g.door(cx); g.window(cx - 38f * s, gy - 86f * s, win); g.window(cx + 38f * s, gy - 86f * s, win)
            }
        }
        g.paint.isAntiAlias = aa
    }

    // ---------- 星空：居住舱 / 科研舱 / 双层舱 / 指挥塔 ----------
    private fun drawHouseSpace(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc; val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true
        when (house.coerceIn(0, 3)) {
            0 -> {
                g.paint.color = pal[0]; g.canvas.drawRoundRect(cx - 68f * s, gy - 96f * s, cx + 68f * s, gy, 12f * s, 12f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 52f * s, gy - 118f * s, cx + 52f * s, gy - 88f * s, g.paint)
                rc(cx - 52f * s, gy - 88f * s, cx + 52f * s, gy - 80f * s, 0xFF4DE8FF.toInt())
                g.door(cx + 18f * s); g.window(cx - 28f * s, gy - 58f * s, win)
            }
            1 -> {
                rc(cx - 92f * s, gy - 108f * s, cx + 92f * s, gy, pal[0])
                rc(cx - 118f * s, gy - 88f * s, cx - 96f * s, gy - 72f * s, pal[2])
                rc(cx + 96f * s, gy - 88f * s, cx + 118f * s, gy - 72f * s, pal[2])
                g.pyramidRoof(cx, gy - 108f * s, 104f * s, 40f * s, roofC, roofD)
                g.door(cx - 40f * s); g.window(cx + 30f * s, gy - 64f * s, win)
            }
            2 -> {
                rc(cx - 88f * s, gy - 168f * s, cx + 88f * s, gy, pal[0])
                rc(cx - 88f * s, gy - 96f * s, cx + 88f * s, gy - 88f * s, 0xFF4DE8FF.toInt())
                g.paint.color = roofC; g.canvas.drawOval(cx - 36f * s, gy - 186f * s, cx + 36f * s, gy - 158f * s, g.paint)
                g.door(cx); g.window(cx - 46f * s, gy - 54f * s, win); g.window(cx + 46f * s, gy - 54f * s, win)
            }
            else -> {
                rc(cx - 72f * s, gy - 156f * s, cx + 72f * s, gy, pal[0])
                rc(cx - 108f * s, gy - 204f * s, cx - 68f * s, gy, pal[1])
                rc(cx + 68f * s, gy - 204f * s, cx + 108f * s, gy, pal[1])
                g.paint.color = roofC; g.canvas.drawCircle(cx, gy - 178f * s, 14f * s, g.paint)
                rc(cx - 4f * s, gy - 210f * s, cx + 4f * s, gy - 178f * s, pal[2])
                g.door(cx); g.window(cx - 40f * s, gy - 96f * s, win); g.window(cx + 40f * s, gy - 96f * s, win)
            }
        }
        g.paint.isAntiAlias = aa
    }

    private fun drawCastle(cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, g: Gfx) {
        val s = g.s; val rc = g.rc
        rc(cx - 85f * s, gy - 150f * s, cx + 85f * s, gy, pal[0])
        rc(cx - 130f * s, gy - 205f * s, cx - 82f * s, gy, pal[1])
        rc(cx + 82f * s, gy - 205f * s, cx + 130f * s, gy, pal[1])
        g.boxShade(cx - 85f * s, gy - 150f * s, cx + 85f * s, gy)
        g.pyramidRoof(cx - 106f * s, gy - 205f * s, 56f * s, 42f * s, roofC, roofD)
        g.pyramidRoof(cx + 106f * s, gy - 205f * s, 56f * s, 42f * s, roofC, roofD)
        g.pyramidRoof(cx, gy - 150f * s, 96f * s, 40f * s, roofC, roofD)
        rc(cx - 26f * s, gy - 64f * s, cx + 26f * s, gy, 0xFF6B4A2B.toInt())
        rc(cx - 60f * s, gy - 110f * s, cx - 48f * s, gy - 78f * s, pal[2])
        rc(cx + 48f * s, gy - 110f * s, cx + 60f * s, gy - 78f * s, pal[2])
    }

    // ---------- 庭院装饰（按世界换造型） ----------
    private fun drawGarden(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, flowers: IntArray, stem: Int, g: Gfx, gx: Float, gyOff: Float) {
        val fx = cx + gx * s; val fy = gy + gyOff * s; val rc = g.rc
        if (alpha == 255) g.groundShadow(fx, fy + 32f * s, 44f * s)
        when (world) {
            Game.UNI_WATER -> {
                rc(fx - 36f * s, fy + 18f * s, fx + 36f * s, fy + 32f * s, 0xFFC66A57.toInt())
                for (i in 0..2) {
                    val px = fx - 22f * s + i * 22f * s
                    g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(flowers[i], alpha)
                    g.canvas.drawCircle(px, fy - 4f * s, 10f * s, g.paint)
                    rc(px - 2f * s, fy + 2f * s, px + 2f * s, fy + 16f * s, stem)
                }
            }
            Game.UNI_SKY -> {
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(0xFFE8F4FF.toInt(), alpha)
                g.canvas.drawOval(fx - 44f * s, fy + 10f * s, fx + 44f * s, fy + 30f * s, g.paint)
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    g.paint.color = g.withAlpha(flowers[i], alpha)
                    g.canvas.drawOval(px - 10f * s, fy - 10f * s, px + 10f * s, fy + 6f * s, g.paint)
                }
            }
            Game.UNI_LAVA -> {
                rc(fx - 40f * s, fy + 20f * s, fx + 40f * s, fy + 34f * s, 0xFF4A3028.toInt())
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    rc(px - 4f * s, fy + 4f * s, px + 4f * s, fy + 18f * s, 0xFF6E4A3A.toInt())
                    rc(px - 6f * s, fy - 10f * s, px + 6f * s, fy + 2f * s, flowers[i])
                }
            }
            Game.UNI_CANDY -> {
                rc(fx - 40f * s, fy + 16f * s, fx + 40f * s, fy + 32f * s, 0xFFA46A3E.toInt())
                for (i in 0..2) {
                    val px = fx - 26f * s + i * 26f * s
                    rc(px - 2f * s, fy + 2f * s, px + 2f * s, fy + 20f * s, 0xFFF2E8D8.toInt())
                    g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(flowers[i], alpha)
                    g.canvas.drawCircle(px, fy - 6f * s, 9f * s, g.paint)
                }
            }
            Game.UNI_SPACE -> {
                rc(fx - 38f * s, fy + 18f * s, fx + 38f * s, fy + 32f * s, 0xFF565B6E.toInt())
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    rc(px - 3f * s, fy + 2f * s, px + 3f * s, fy + 16f * s, stem)
                    rc(px - 5f * s, fy - 12f * s, px + 5f * s, fy + 2f * s, flowers[i])
                    rc(px - 2f * s, fy - 16f * s, px + 2f * s, fy - 12f * s, 0xFF4DE8FF.toInt())
                }
            }
            else -> {
                rc(fx - 40f * s, fy + 16f * s, fx + 40f * s, fy + 32f * s, 0xFF97622F.toInt())
                for (i in 0..2) {
                    val px = fx - 26f * s + i * 26f * s
                    rc(px - 2f * s, fy + 2f * s, px + 2f * s, fy + 16f * s, stem)
                    rc(px - 6f * s, fy - 8f * s, px + 6f * s, fy + 4f * s, flowers[i])
                }
            }
        }
    }

    private fun drawFence(world: Int, cx: Float, gy: Float, half: Float, s: Float, alpha: Int, fence: IntArray, g: Gfx, fenceY: Float) {
        val fy = gy + fenceY * s; val rc = g.rc
        when (world) {
            Game.UNI_WATER -> {
                var px = cx - half + 24f * s
                while (px < cx + half - 24f * s) {
                    g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(fence[0], alpha)
                    g.canvas.drawOval(px - 8f * s, fy + 30f * s, px + 8f * s, fy + 58f * s, g.paint)
                    px += 36f * s
                }
                rc(cx - half + 10f * s, fy + 44f * s, cx + half - 10f * s, fy + 50f * s, fence[1])
            }
            Game.UNI_SKY -> {
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(fence[0], alpha)
                g.canvas.drawOval(cx - half + 20f * s, fy + 38f * s, cx + half - 20f * s, fy + 54f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                var px = cx - half + 22f * s
                while (px < cx + half - 22f * s) {
                    rc(px - 5f * s, fy + 28f * s, px + 2f * s, fy + 62f * s, fence[0])
                    px += 38f * s
                }
            }
            Game.UNI_CANDY -> {
                var px = cx - half + 20f * s
                while (px < cx + half - 20f * s) {
                    rc(px - 4f * s, fy + 34f * s, px + 4f * s, fy + 62f * s, fence[0])
                    rc(px - 6f * s, fy + 30f * s, px + 6f * s, fy + 36f * s, 0xFFFFF8F0.toInt())
                    px += 34f * s
                }
                rc(cx - half + 12f * s, fy + 44f * s, cx + half - 12f * s, fy + 50f * s, fence[1])
            }
            Game.UNI_SPACE -> {
                var px = cx - half + 28f * s
                while (px < cx + half - 28f * s) {
                    rc(px - 2f * s, fy + 30f * s, px + 2f * s, fy + 64f * s, fence[0])
                    rc(px - 8f * s, fy + 38f * s, px + 8f * s, fy + 42f * s, 0xFF4DE8FF.toInt())
                    px += 40f * s
                }
            }
            else -> {
                var px = cx - half + 20f * s
                while (px < cx + half - 20f * s) {
                    rc(px - 3f * s, fy + 36f * s, px + 3f * s, fy + 64f * s, fence[0])
                    px += 34f * s
                }
                rc(cx - half + 12f * s, fy + 44f * s, cx + half - 12f * s, fy + 50f * s, fence[1])
            }
        }
    }

    private fun drawMailbox(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, mx: Float, my: Float) {
        val x = cx + mx * s; val fy = gy + my * s; val rc = g.rc
        if (alpha == 255) g.groundShadow(x, fy, 18f * s)
        when (world) {
            Game.UNI_WATER -> {
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(0xFF57B6E8.toInt(), alpha)
                g.canvas.drawOval(x - 14f * s, fy - 48f * s, x + 14f * s, fy - 8f * s, g.paint)
                rc(x - 3f * s, fy - 8f * s, x + 3f * s, fy, 0xFFC66A57.toInt())
            }
            Game.UNI_SKY -> {
                rc(x - 3f * s, fy - 40f * s, x + 3f * s, fy, 0xFFC9A570.toInt())
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(0xFFE8F4FF.toInt(), alpha)
                g.canvas.drawOval(x - 18f * s, fy - 58f * s, x + 18f * s, fy - 38f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                rc(x - 18f * s, fy - 50f * s, x + 18f * s, fy - 30f * s, 0xFF4A3028.toInt())
                rc(x - 3f * s, fy - 30f * s, x + 3f * s, fy, 0xFF43302A.toInt())
                rc(x + 10f * s, fy - 56f * s, x + 14f * s, fy - 48f * s, 0xFFFF7A2A.toInt())
            }
            Game.UNI_CANDY -> {
                rc(x - 16f * s, fy - 52f * s, x + 16f * s, fy - 32f * s, 0xFFFF8FBE.toInt())
                rc(x - 16f * s, fy - 32f * s, x + 16f * s, fy - 28f * s, 0xFFFFF8F0.toInt())
                rc(x - 3f * s, fy - 28f * s, x + 3f * s, fy, 0xFFA46A3E.toInt())
            }
            Game.UNI_SPACE -> return // 星空世界无信箱槽位
            else -> {
                rc(x - 3f * s, fy - 34f * s, x + 3f * s, fy, 0xFF97622F.toInt())
                rc(x - 16f * s, fy - 52f * s, x + 16f * s, fy - 32f * s, 0xFFF25A5A.toInt())
                rc(x + 12f * s, fy - 62f * s, x + 16f * s, fy - 50f * s, 0xFFFFD75E.toInt())
            }
        }
    }

    private fun drawSwing(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, sx: Float, sy: Float) {
        val x = cx + sx * s; val fy = gy + sy * s; val rc = g.rc
        val sway = sin(g.homePhase * 1.6f) * 10f * s
        if (alpha == 255) g.groundShadow(x, fy, 40f * s)
        when (world) {
            Game.UNI_WATER -> {
                rc(x - 30f * s, fy - 78f * s, x - 26f * s, fy, 0xFF64998F.toInt())
                rc(x + 26f * s, fy - 78f * s, x + 30f * s, fy, 0xFF64998F.toInt())
                rc(x - 32f * s + sway, fy - 34f * s, x + 32f * s + sway, fy - 28f * s, 0xFF6EE8D8.toInt())
            }
            Game.UNI_SKY -> {
                rc(x - 4f * s, fy - 90f * s, x + 4f * s, fy - 82f * s, 0xFFDCD2B4.toInt())
                rc(x - 12f * s + sway, fy - 82f * s, x - 9f * s + sway * 1.2f, fy - 36f * s, 0xFFB9B9B9.toInt())
                rc(x + 9f * s + sway, fy - 82f * s, x + 12f * s + sway * 1.2f, fy - 36f * s, 0xFFB9B9B9.toInt())
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(0xFFE8F4FF.toInt(), alpha)
                g.canvas.drawOval(x - 20f * s + sway, fy - 36f * s, x + 20f * s + sway, fy - 24f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                rc(x - 32f * s, fy - 84f * s, x - 28f * s, fy, 0xFF43302A.toInt())
                rc(x + 28f * s, fy - 84f * s, x + 32f * s, fy, 0xFF43302A.toInt())
                rc(x - 16f * s + sway, fy - 34f * s, x + 16f * s + sway, fy - 26f * s, 0xFFFF7A2A.toInt())
            }
            Game.UNI_CANDY -> {
                rc(x - 30f * s, fy - 80f * s, x - 26f * s, fy, 0xFFA46A3E.toInt())
                rc(x + 26f * s, fy - 80f * s, x + 30f * s, fy, 0xFFA46A3E.toInt())
                rc(x - 20f * s + sway, fy - 36f * s, x + 20f * s + sway, fy - 26f * s, 0xFFFF8FBE.toInt())
            }
            Game.UNI_SPACE -> {
                rc(x - 34f * s, fy - 86f * s, x - 30f * s, fy, 0xFF565B6E.toInt())
                rc(x + 30f * s, fy - 86f * s, x + 34f * s, fy, 0xFF565B6E.toInt())
                rc(x - 18f * s + sway, fy - 38f * s, x + 18f * s + sway, fy - 30f * s, 0xFF4DE8FF.toInt())
            }
            else -> {
                rc(x - 34f * s, fy - 84f * s, x - 28f * s, fy, 0xFF97622F.toInt())
                rc(x + 28f * s, fy - 84f * s, x + 34f * s, fy, 0xFF97622F.toInt())
                rc(x - 38f * s, fy - 90f * s, x + 38f * s, fy - 82f * s, 0xFF7A4E22.toInt())
                rc(x - 14f * s + sway, fy - 82f * s, x - 11f * s + sway * 1.2f, fy - 34f * s, 0xFFB9B9B9.toInt())
                rc(x + 11f * s + sway, fy - 82f * s, x + 14f * s + sway * 1.2f, fy - 34f * s, 0xFFB9B9B9.toInt())
                rc(x - 18f * s + sway * 1.2f, fy - 34f * s, x + 18f * s + sway * 1.2f, fy - 26f * s, 0xFFFFD75E.toInt())
            }
        }
    }

    private fun drawPerch(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, px: Float, py: Float) {
        val x = cx + px * s; val fy = gy + py * s; val rc = g.rc
        if (alpha == 255) g.groundShadow(x, fy, 22f * s)
        when (world) {
            Game.UNI_WATER -> {
                rc(x - 4f * s, fy - 88f * s, x + 4f * s, fy, 0xFFC66A57.toInt())
                rc(x - 28f * s, fy - 58f * s, x + 8f * s, fy - 50f * s, 0xFF64998F.toInt())
                rc(x - 8f * s, fy - 94f * s, x + 28f * s, fy - 86f * s, 0xFF6EE8D8.toInt())
            }
            Game.UNI_SKY -> {
                rc(x - 4f * s, fy - 92f * s, x + 4f * s, fy, 0xFFDCD2B4.toInt())
                g.paint.isAntiAlias = true; g.paint.color = g.withAlpha(0xFFE8F4FF.toInt(), alpha)
                g.canvas.drawOval(x - 32f * s, fy - 66f * s, x + 12f * s, fy - 54f * s, g.paint)
                g.canvas.drawOval(x - 12f * s, fy - 104f * s, x + 28f * s, fy - 92f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                rc(x - 5f * s, fy - 94f * s, x + 5f * s, fy, 0xFF43302A.toInt())
                rc(x - 32f * s, fy - 62f * s, x + 10f * s, fy - 52f * s, 0xFF4A3028.toInt())
                rc(x - 10f * s, fy - 100f * s, x + 30f * s, fy - 90f * s, 0xFFFF7A2A.toInt())
            }
            Game.UNI_CANDY -> {
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFFA46A3E.toInt())
                rc(x - 30f * s, fy - 64f * s, x + 10f * s, fy - 54f * s, 0xFF7ADBC8.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 92f * s, 0xFFFF8FBE.toInt())
            }
            Game.UNI_SPACE -> {
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFF565B6E.toInt())
                rc(x - 30f * s, fy - 66f * s, x + 10f * s, fy - 58f * s, 0xFF6E7488.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 94f * s, 0xFF4DE8FF.toInt())
            }
            else -> {
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFFC9A570.toInt())
                rc(x - 30f * s, fy - 64f * s, x + 10f * s, fy - 54f * s, 0xFF8594B3.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 92f * s, 0xFFF5A8C1.toInt())
                rc(x + 12f * s, fy - 92f * s, x + 20f * s, fy - 78f * s, 0xFFB9B9B9.toInt())
            }
        }
    }

    private fun drawPool(
        world: Int, cx: Float, gy: Float, s: Float, alpha: Int, poolWater: IntArray, g: Gfx,
        pl: Float, pr: Float, pt: Float, pb: Float
    ) {
        val left = cx + pl * s; val right = cx + pr * s
        val top = gy + pt * s; val bottom = gy + pb * s
        val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true; g.paint.shader = null
        val rad = 16f * s
        if (alpha == 255) {
            g.paint.color = 0x22000000
            g.canvas.drawOval(left - 4f * s, bottom - 12f * s, right + 4f * s, bottom + 8f * s, g.paint)
        }
        val rim = when (world) {
            Game.UNI_WATER -> 0xFF64998F.toInt()
            Game.UNI_LAVA -> 0xFF4A3028.toInt()
            Game.UNI_CANDY -> 0xFFFF8FBE.toInt()
            Game.UNI_SPACE -> 0xFF565B6E.toInt()
            else -> 0xFF9A6E3E.toInt()
        }
        g.paint.color = g.withAlpha(rim, alpha)
        g.canvas.drawRoundRect(left, top + 4f * s, right, bottom, rad, rad, g.paint)
        g.paint.color = g.withAlpha(rim + 0x00202020, alpha)
        g.canvas.drawRoundRect(left, top, right, bottom - 6f * s, rad, rad, g.paint)
        val wl = left + 9f * s; val wt = top + 8f * s; val wr = right - 9f * s; val wb = bottom - 12f * s
        g.paint.shader = LinearGradient(0f, wt, 0f, wb, intArrayOf(g.withAlpha(poolWater[0], alpha), g.withAlpha(poolWater[1], alpha)), null, Shader.TileMode.CLAMP)
        g.canvas.drawRoundRect(wl, wt, wr, wb, 11f * s, 11f * s, g.paint)
        g.paint.shader = null; g.paint.isAntiAlias = aa
    }

    private fun drawTelescope(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, tx: Float, ty: Float) {
        val x = cx + tx * s; val fy = gy + ty * s; val rc = g.rc
        val topY = fy - 26f * s
        if (alpha == 255) g.groundShadow(x, fy, 22f * s)
        val leg = when (world) {
            Game.UNI_WATER -> 0xFF64998F.toInt()
            Game.UNI_SPACE -> 0xFF6E7488.toInt()
            else -> 0xFF5B5B66.toInt()
        }
        rc(x - 3f * s, topY, x + 3f * s, fy, leg)
        g.canvas.save(); g.canvas.rotate(24f, x, topY + 4f * s)
        rc(x - 3f * s, topY + 4f * s, x + 3f * s, fy + 6f * s, leg); g.canvas.restore()
        g.canvas.save(); g.canvas.rotate(-24f, x, topY + 4f * s)
        rc(x - 3f * s, topY + 4f * s, x + 3f * s, fy + 6f * s, leg); g.canvas.restore()
        rc(x - 7f * s, topY - 3f * s, x + 7f * s, topY + 4f * s, leg)
        g.canvas.save(); g.canvas.rotate(if (world == Game.UNI_SPACE) -15f else -30f, x, topY)
        g.paint.color = g.withAlpha(if (world == Game.UNI_WATER) 0xFF2E86BE.toInt() else 0xFF3E4A66.toInt(), alpha)
        g.canvas.drawRect(x - 12f * s, topY - 8f * s, x + 32f * s, topY + 8f * s, g.paint)
        g.paint.color = g.withAlpha(0xFF57B6E8.toInt(), alpha)
        g.canvas.drawRect(x + 28f * s, topY - 6f * s, x + 32f * s, topY + 6f * s, g.paint)
        g.canvas.restore()
    }

    private fun drawFlags(cx: Float, gy: Float, half: Float, s: Float, alpha: Int, flags: IntArray, g: Gfx) {
        val rc = g.rc
        for (side in intArrayOf(-1, 1)) {
            for (i in 0..4) {
                val t = (i + 1) / 6f
                val fx = cx + side * t * (half - 30f * s)
                val fy = gy - 200f * s + t * t * 130f * s
                rc(fx - 7f * s, fy, fx + 7f * s, fy + 16f * s, flags[i])
            }
        }
    }
}
