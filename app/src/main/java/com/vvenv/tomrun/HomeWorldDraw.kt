package com.vvenv.tomrun

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

    /**
     * 装饰拖放热区（场景单位，相对接地点锚点；左右/上下为 fx/fy 偏移，未乘 s）。
     * 略大于像素造型，避免布局编辑器里大偏移时点对不上。
     */
    fun decoDragBounds(world: Int, deco: Int, half: Float): FloatArray = when (deco) {
        0 -> when (world) {
            Game.UNI_SKY -> floatArrayOf(-48f, -24f, 48f, 34f)
            Game.UNI_SPACE -> floatArrayOf(-42f, -20f, 42f, 36f)
            else -> floatArrayOf(-44f, -18f, 44f, 38f)
        }
        1 -> floatArrayOf(-half, 22f, half, 76f)
        2 -> when (world) {
            Game.UNI_SKY -> floatArrayOf(-22f, -62f, 22f, 8f)
            Game.UNI_WATER -> floatArrayOf(-18f, -56f, 18f, 8f)
            else -> floatArrayOf(-22f, -66f, 28f, 8f)
        }
        3 -> floatArrayOf(-56f, -102f, 56f, 12f)
        4 -> floatArrayOf(-36f, -110f, 36f, 8f)
        5 -> floatArrayOf(0f, 0f, 0f, 0f) // 泳池由 HudView 用 pool 矩形
        6 -> floatArrayOf(-52f, -58f, 56f, 14f)
        else -> floatArrayOf(-40f, -40f, 40f, 40f)
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

    // ---------- 通用小景：炊烟 / 摇摆水草 ----------

    /** 三团循环上升的烟：越高越淡越大，横向随相位轻摆 */
    private fun smoke(g: Gfx, x: Float, topY: Float, tint: Int) {
        val s = g.s
        val aa = g.paint.isAntiAlias; g.paint.isAntiAlias = true
        for (i in 0 until 3) {
            val t = (g.homePhase * 0.30f + i * 0.33f) % 1f
            val py = topY - t * 36f * s
            val px = x + sin(g.homePhase * 1.3f + i * 2.1f) * 4f * s + t * 7f * s
            g.paint.color = g.withAlpha(tint, ((1f - t) * 110).toInt())
            g.canvas.drawCircle(px, py, (3.5f + t * 5f) * s, g.paint)
        }
        g.paint.isAntiAlias = aa
    }

    /** 一株分段水草：底固定，越往上摆幅越大 */
    private fun kelp(g: Gfx, x: Float, gy: Float, h: Float, c: Int) {
        val s = g.s
        for (i in 0 until 4) {
            val sway = sin(g.homePhase * 1.5f + i * 0.8f) * (0.8f + i * 0.9f) * s
            g.rc(x - 2.5f * s + sway, gy - h * (i + 1) / 4f, x + 2.5f * s + sway, gy - h * i / 4f + 1f * s, c)
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
                // 烟囱 + 炊烟
                rc(cx + 44f * s, gy - 152f * s, cx + 60f * s, gy - 116f * s, pal[1])
                rc(cx + 42f * s, gy - 158f * s, cx + 62f * s, gy - 152f * s, pal[0])
                smoke(g, cx + 52f * s, gy - 164f * s, 0xFFF4F0E6.toInt())
                g.door(cx + 34f * s); g.window(cx - 40f * s, gy - 62f * s, win)
                // 窗下花箱
                rc(cx - 58f * s, gy - 42f * s, cx - 22f * s, gy - 34f * s, 0xFF7A4E22.toInt())
                rc(cx - 52f * s, gy - 48f * s, cx - 46f * s, gy - 42f * s, 0xFFF25A5A.toInt())
                rc(cx - 43f * s, gy - 48f * s, cx - 37f * s, gy - 42f * s, 0xFFFFD75E.toInt())
                rc(cx - 34f * s, gy - 48f * s, cx - 28f * s, gy - 42f * s, 0xFFF5A8C1.toInt())
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
                // 门口双层雨棚
                rc(cx - 78f * s, gy - 74f * s, cx - 26f * s, gy - 68f * s, roofC)
                rc(cx - 74f * s, gy - 68f * s, cx - 30f * s, gy - 64f * s, roofD)
            }
            2 -> {
                rc(cx - 100f * s, gy - 190f * s, cx + 100f * s, gy, pal[0])
                rc(cx - 100f * s, gy - 100f * s, cx + 100f * s, gy - 92f * s, pal[1])
                g.boxShade(cx - 100f * s, gy - 190f * s, cx + 100f * s, gy)
                g.pyramidRoof(cx, gy - 190f * s, 126f * s, 48f * s, roofC, roofD)
                g.door(cx); g.window(cx - 64f * s, gy - 52f * s, win); g.window(cx + 64f * s, gy - 52f * s, win)
                g.window(cx - 64f * s, gy - 142f * s, win); g.window(cx + 64f * s, gy - 142f * s, win)
                rc(cx - 30f * s, gy - 126f * s, cx + 30f * s, gy - 118f * s, pal[2])
                // 阁楼圆窗
                val aa = g.paint.isAntiAlias; g.paint.isAntiAlias = true
                g.paint.color = pal[1]; g.canvas.drawCircle(cx, gy - 166f * s, 12f * s, g.paint)
                g.paint.color = win; g.canvas.drawCircle(cx, gy - 166f * s, 8f * s, g.paint)
                g.paint.isAntiAlias = aa
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
                // 圆顶高光 + 门口沙堆 + 两侧摇摆水草
                g.paint.color = 0x55FFFFFF
                g.canvas.drawOval(cx - 48f * s, gy - 112f * s, cx - 8f * s, gy - 94f * s, g.paint)
                g.paint.color = 0xFFD5C68B.toInt()
                g.canvas.drawOval(cx - 80f * s, gy - 8f * s, cx + 80f * s, gy + 6f * s, g.paint)
                kelp(g, cx - 86f * s, gy, 42f * s, 0xFF2E8A6E.toInt())
                kelp(g, cx + 88f * s, gy, 34f * s, 0xFF3EA97E.toInt())
                g.window(cx - 28f * s, gy - 78f * s, win); g.door(cx + 24f * s)
            }
            1 -> {
                rc(cx - 88f * s, gy - 90f * s, cx + 88f * s, gy, pal[0])
                g.paint.color = pal[1]
                g.canvas.drawArc(cx - 96f * s, gy - 130f * s, cx + 96f * s, gy - 20f * s, 200f, 140f, true, g.paint)
                // 贝壳棱线 + 壳顶珍珠
                g.paint.style = Paint.Style.STROKE
                g.paint.strokeWidth = 3f * s
                g.paint.color = pal[0]
                for (i in 1..2) {
                    val inset = i * 20f * s
                    g.canvas.drawArc(
                        cx - 96f * s + inset, gy - 130f * s + inset * 0.6f,
                        cx + 96f * s - inset, gy - 20f * s - inset * 0.3f,
                        205f, 130f, false, g.paint
                    )
                }
                g.paint.style = Paint.Style.FILL
                g.paint.color = 0xFFF8F3E6.toInt(); g.canvas.drawCircle(cx, gy - 122f * s, 7f * s, g.paint)
                g.paint.color = 0xFFFFFFFF.toInt(); g.canvas.drawCircle(cx - 2f * s, gy - 124f * s, 2.5f * s, g.paint)
                kelp(g, cx - 94f * s, gy, 36f * s, 0xFF2E8A6E.toInt())
                g.window(cx, gy - 72f * s, win); g.door(cx - 40f * s)
            }
            2 -> {
                rc(cx - 90f * s, gy - 170f * s, cx + 90f * s, gy, pal[0])
                rc(cx - 90f * s, gy - 96f * s, cx + 90f * s, gy - 88f * s, pal[1])
                g.boxShade(cx - 90f * s, gy - 170f * s, cx + 90f * s, gy)
                g.paint.color = roofC
                g.canvas.drawOval(cx - 40f * s, gy - 188f * s, cx + 40f * s, gy - 158f * s, g.paint)
                // 塔身珊瑚礁架
                rc(cx - 98f * s, gy - 140f * s, cx - 90f * s, gy - 122f * s, 0xFFE86A5A.toInt())
                rc(cx - 104f * s, gy - 134f * s, cx - 90f * s, gy - 128f * s, 0xFFD85A4A.toInt())
                rc(cx + 90f * s, gy - 66f * s, cx + 100f * s, gy - 48f * s, 0xFFFF9A76.toInt())
                kelp(g, cx + 96f * s, gy, 40f * s, 0xFF3EA97E.toInt())
                g.window(cx - 50f * s, gy - 56f * s, win); g.window(cx + 50f * s, gy - 56f * s, win)
                g.window(cx, gy - 140f * s, win); g.door(cx)
            }
            else -> {
                rc(cx - 70f * s, gy - 140f * s, cx + 70f * s, gy, pal[0])
                g.paint.color = g.withAlpha(pal[1], 180)
                g.canvas.drawOval(cx - 100f * s, gy - 200f * s, cx - 30f * s, gy - 120f * s, g.paint)
                g.canvas.drawOval(cx + 30f * s, gy - 200f * s, cx + 100f * s, gy - 120f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 50f * s, gy - 168f * s, cx + 50f * s, gy - 128f * s, g.paint)
                // 水晶内部脉动微光 + 基座碎晶
                val pulse = 0.5f + 0.5f * sin(g.homePhase * 1.8f)
                g.paint.color = g.withAlpha(0xFFFFFFFF.toInt(), (26 + 40 * pulse).toInt())
                g.canvas.drawOval(cx - 88f * s, gy - 192f * s, cx - 42f * s, gy - 132f * s, g.paint)
                g.canvas.drawOval(cx + 42f * s, gy - 192f * s, cx + 88f * s, gy - 132f * s, g.paint)
                rc(cx - 86f * s, gy - 24f * s, cx - 72f * s, gy, pal[1])
                rc(cx - 78f * s, gy - 34f * s, cx - 70f * s, gy - 24f * s, pal[1])
                rc(cx + 74f * s, gy - 18f * s, cx + 86f * s, gy, pal[1])
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
                // 云基两端的绒边云球（微微起伏）
                val bob = sin(g.homePhase * 1.4f) * 2f * s
                g.canvas.drawCircle(cx - 84f * s, gy - 14f * s + bob, 15f * s, g.paint)
                g.canvas.drawCircle(cx + 84f * s, gy - 12f * s - bob, 13f * s, g.paint)
                rc(cx - 70f * s, gy - 100f * s, cx + 70f * s, gy - 20f * s, pal[0])
                g.paint.color = roofC; g.canvas.drawOval(cx - 82f * s, gy - 118f * s, cx + 82f * s, gy - 88f * s, g.paint)
                // 檐口金边
                rc(cx - 66f * s, gy - 92f * s, cx + 66f * s, gy - 88f * s, 0xFFF2C14E.toInt())
                g.door(cx + 20f * s); g.window(cx - 30f * s, gy - 62f * s, win)
            }
            1 -> {
                rc(cx - 88f * s, gy - 118f * s, cx + 88f * s, gy, pal[0])
                g.boxShade(cx - 88f * s, gy - 118f * s, cx + 88f * s, gy)
                rc(cx - 88f * s, gy - 122f * s, cx + 88f * s, gy - 118f * s, 0xFFF2C14E.toInt())
                rc(cx - 8f * s, gy - 168f * s, cx + 8f * s, gy - 118f * s, pal[2])
                g.paint.color = roofC
                g.canvas.drawRect(cx - 28f * s, gy - 178f * s, cx + 28f * s, gy - 158f * s, g.paint)
                // 塔顶旗杆 + 迎风三角旗
                rc(cx - 1.5f * s, gy - 198f * s, cx + 1.5f * s, gy - 178f * s, pal[2])
                val wave = sin(g.homePhase * 2.4f) * 3f * s
                rc(cx + 1.5f * s, gy - 196f * s, cx + 16f * s + wave, gy - 190f * s, 0xFFF2C14E.toInt())
                rc(cx + 1.5f * s, gy - 190f * s, cx + 10f * s + wave * 0.6f, gy - 185f * s, 0xFFF2C14E.toInt())
                for (i in -1..1) g.window(cx + i * 44f * s, gy - 72f * s, win)
                g.door(cx - 40f * s)
            }
            2 -> {
                rc(cx - 82f * s, gy - 178f * s, cx + 82f * s, gy, pal[0])
                rc(cx - 82f * s, gy - 98f * s, cx + 82f * s, gy - 90f * s, pal[1])
                g.boxShade(cx - 82f * s, gy - 178f * s, cx + 82f * s, gy)
                g.pyramidRoof(cx, gy - 178f * s, 100f * s, 44f * s, roofC, roofD)
                // 墙角的云绒球
                g.paint.color = pal[0]
                g.canvas.drawCircle(cx - 80f * s, gy - 8f * s, 13f * s, g.paint)
                g.canvas.drawCircle(cx + 80f * s, gy - 6f * s, 11f * s, g.paint)
                g.door(cx); g.window(cx - 48f * s, gy - 56f * s, win); g.window(cx + 48f * s, gy - 56f * s, win)
                g.window(cx, gy - 140f * s, win)
            }
            else -> {
                rc(cx - 76f * s, gy - 150f * s, cx + 76f * s, gy, pal[0])
                g.boxShade(cx - 76f * s, gy - 150f * s, cx + 76f * s, gy)
                g.paint.color = pal[0]; g.canvas.drawOval(cx - 110f * s, gy - 210f * s, cx - 50f * s, gy - 150f * s, g.paint)
                g.canvas.drawOval(cx + 50f * s, gy - 210f * s, cx + 110f * s, gy - 150f * s, g.paint)
                // 双塔金顶 + 身旁缓缓起伏的小浮云
                g.paint.color = 0xFFF2C14E.toInt()
                g.canvas.drawCircle(cx - 80f * s, gy - 206f * s, 6f * s, g.paint)
                g.canvas.drawCircle(cx + 80f * s, gy - 206f * s, 6f * s, g.paint)
                val bob = sin(g.homePhase * 1.2f) * 4f * s
                g.paint.color = 0xE6FFFFFF.toInt()
                g.canvas.drawCircle(cx - 124f * s, gy - 110f * s + bob, 10f * s, g.paint)
                g.canvas.drawCircle(cx - 112f * s, gy - 106f * s + bob, 8f * s, g.paint)
                g.pyramidRoof(cx, gy - 150f * s, 88f * s, 38f * s, roofC, roofD)
                g.door(cx); g.window(cx - 40f * s, gy - 88f * s, win); g.window(cx + 40f * s, gy - 88f * s, win)
            }
        }
        g.paint.isAntiAlias = aa
    }

    // ---------- 熔岩：焦木 / 黑曜 / 熔楼 / 火山堡 ----------
    private fun drawHouseLava(house: Int, cx: Float, gy: Float, pal: IntArray, roofC: Int, roofD: Int, win: Int, g: Gfx) {
        val s = g.s; val rc = g.rc
        val glow = 0.55f + 0.45f * (0.5f + 0.5f * sin(g.homePhase * 2.6f))
        when (house.coerceIn(0, 3)) {
            0 -> {
                rc(cx - 76f * s, gy - 108f * s, cx + 76f * s, gy, pal[0])
                g.boxShade(cx - 76f * s, gy - 108f * s, cx + 76f * s, gy)
                rc(cx - 76f * s, gy - 10f * s, cx + 76f * s, gy, pal[1])
                rc(cx - 6f * s, gy - 148f * s, cx + 6f * s, gy - 108f * s, 0xFF4A3028.toInt())
                g.pyramidRoof(cx, gy - 108f * s, 98f * s, 44f * s, roofC, roofD)
                // 烟囱冒出的灰烟与忽明忽暗的火星
                smoke(g, cx, gy - 152f * s, 0xFF8A7568.toInt())
                g.paint.color = g.withAlpha(0xFFFFA25A.toInt(), (glow * 220).toInt())
                g.canvas.drawRect(cx - 2f * s, gy - 156f * s, cx + 2f * s, gy - 152f * s, g.paint)
                g.door(cx + 28f * s); g.window(cx - 36f * s, gy - 60f * s, win)
            }
            1 -> {
                rc(cx - 96f * s, gy - 118f * s, cx + 96f * s, gy, pal[0])
                for (i in 0..5) rc(cx - 96f * s + i * 32f * s, gy - 118f * s, cx - 88f * s + i * 32f * s, gy, pal[1])
                g.boxShade(cx - 96f * s, gy - 118f * s, cx + 96f * s, gy)
                rc(cx - 96f * s, gy - 124f * s, cx + 96f * s, gy - 118f * s, pal[1])
                // 柱脚渗出的岩浆缝：随呼吸明暗
                g.paint.color = g.withAlpha(0xFFFF7A2A.toInt(), (glow * 230).toInt())
                for (i in 0..4) {
                    val bx = cx - 80f * s + i * 32f * s
                    g.canvas.drawRect(bx, gy - 4f * s, bx + 16f * s, gy, g.paint)
                }
                g.pyramidRoof(cx, gy - 118f * s, 118f * s, 48f * s, roofC, roofD)
                g.door(cx - 44f * s); g.window(cx + 20f * s, gy - 68f * s, win)
            }
            2 -> {
                rc(cx - 94f * s, gy - 182f * s, cx + 94f * s, gy, pal[0])
                g.boxShade(cx - 94f * s, gy - 182f * s, cx + 94f * s, gy)
                rc(cx - 94f * s, gy - 102f * s, cx + 94f * s, gy - 94f * s, 0xFFFF7A2A.toInt())
                // 熔岩带脉动亮层 + 低处第二道细缝
                g.paint.color = g.withAlpha(0xFFFFC48A.toInt(), (glow * 130).toInt())
                g.canvas.drawRect(cx - 94f * s, gy - 102f * s, cx + 94f * s, gy - 94f * s, g.paint)
                g.paint.color = g.withAlpha(0xFFFF7A2A.toInt(), (glow * 200).toInt())
                g.canvas.drawRect(cx - 94f * s, gy - 30f * s, cx + 94f * s, gy - 27f * s, g.paint)
                g.pyramidRoof(cx, gy - 182f * s, 112f * s, 46f * s, roofC, roofD)
                g.door(cx); g.window(cx - 58f * s, gy - 54f * s, win); g.window(cx + 58f * s, gy - 54f * s, win)
                g.window(cx, gy - 138f * s, win)
            }
            else -> {
                rc(cx - 80f * s, gy - 148f * s, cx + 80f * s, gy, pal[0])
                g.boxShade(cx - 80f * s, gy - 148f * s, cx + 80f * s, gy)
                rc(cx - 120f * s, gy - 198f * s, cx - 76f * s, gy - 60f * s, pal[1])
                rc(cx + 76f * s, gy - 198f * s, cx + 120f * s, gy - 60f * s, pal[1])
                // 侧塔淌下的熔岩滴流
                rc(cx - 104f * s, gy - 198f * s, cx - 100f * s, gy - 168f * s, 0xFFFF7A2A.toInt())
                rc(cx + 92f * s, gy - 198f * s, cx + 96f * s, gy - 172f * s, 0xFFFF7A2A.toInt())
                rc(cx - 10f * s, gy - 210f * s, cx + 10f * s, gy - 148f * s, 0xFFFF5A2A.toInt())
                // 火山口辉光：呼吸的光晕 + 上升的火星
                val aa = g.paint.isAntiAlias
                g.paint.isAntiAlias = true
                g.paint.color = g.withAlpha(0xFFFF7A2A.toInt(), (glow * 90).toInt())
                g.canvas.drawCircle(cx, gy - 210f * s, 24f * s, g.paint)
                g.paint.color = g.withAlpha(0xFFFFC48A.toInt(), (glow * 220).toInt())
                g.canvas.drawCircle(cx, gy - 210f * s, 8f * s, g.paint)
                for (i in 0 until 3) {
                    val t = (g.homePhase * 0.5f + i * 0.33f) % 1f
                    val ex = cx + sin(g.homePhase * 2f + i * 2.4f) * 10f * s
                    g.paint.color = g.withAlpha(0xFFFFA25A.toInt(), ((1f - t) * 200).toInt())
                    g.canvas.drawRect(ex - 1.6f * s, gy - 214f * s - t * 30f * s, ex + 1.6f * s, gy - 211f * s - t * 30f * s, g.paint)
                }
                g.paint.isAntiAlias = aa
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
                g.boxShade(cx - 74f * s, gy - 104f * s, cx + 74f * s, gy)
                rc(cx - 74f * s, gy - 104f * s, cx + 74f * s, gy - 100f * s, 0xFFFFF8F0.toInt())
                g.pyramidRoof(cx, gy - 104f * s, 96f * s, 42f * s, roofC, roofD)
                g.door(cx + 26f * s); g.window(cx - 32f * s, gy - 58f * s, win)
                // 姜饼人式糖豆纽扣
                g.paint.color = 0xFF7ADBC8.toInt(); g.canvas.drawCircle(cx - 2f * s, gy - 84f * s, 5f * s, g.paint)
                g.paint.color = 0xFFFF8FBE.toInt(); g.canvas.drawCircle(cx - 2f * s, gy - 66f * s, 5f * s, g.paint)
                g.paint.color = 0xFFC77DFF.toInt(); g.canvas.drawCircle(cx - 2f * s, gy - 48f * s, 5f * s, g.paint)
            }
            1 -> {
                g.paint.color = pal[0]; g.canvas.drawRoundRect(cx - 70f * s, gy - 120f * s, cx + 70f * s, gy, 18f * s, 18f * s, g.paint)
                g.paint.color = roofC; g.canvas.drawOval(cx - 78f * s, gy - 148f * s, cx + 78f * s, gy - 108f * s, g.paint)
                // 奶油顶上的糖屑与樱桃
                val sp = intArrayOf(0xFFFFF8F0.toInt(), 0xFFFFD54A.toInt(), 0xFF7ADBC8.toInt(), 0xFFC77DFF.toInt(), 0xFFFFF8F0.toInt())
                for (i in sp.indices) {
                    val px = cx + (i - 2) * 26f * s + (i % 2) * 8f * s
                    val py = gy - (132f - (i % 3) * 9f) * s
                    rc(px - 1.8f * s, py - 4f * s, px + 1.8f * s, py + 4f * s, sp[i])
                }
                g.paint.color = 0xFFE84A4A.toInt(); g.canvas.drawCircle(cx, gy - 150f * s, 8f * s, g.paint)
                g.paint.color = 0xFFFF9A9A.toInt(); g.canvas.drawCircle(cx - 2.5f * s, gy - 152.5f * s, 2.8f * s, g.paint)
                g.door(cx - 30f * s); g.window(cx + 24f * s, gy - 72f * s, win)
            }
            2 -> {
                rc(cx - 86f * s, gy - 176f * s, cx + 86f * s, gy, pal[0])
                g.boxShade(cx - 86f * s, gy - 176f * s, cx + 86f * s, gy)
                rc(cx - 86f * s, gy - 98f * s, cx + 86f * s, gy - 90f * s, 0xFFFFF8F0.toInt())
                // 糖霜带往下淌的糖滴
                for (i in 0..4) {
                    val dx = cx - 70f * s + i * 35f * s
                    rc(dx - 2.5f * s, gy - 90f * s, dx + 2.5f * s, gy - (82f - (i % 2) * 4f) * s, 0xFFFFF8F0.toInt())
                }
                g.pyramidRoof(cx, gy - 176f * s, 108f * s, 44f * s, roofC, roofD)
                // 山墙薄荷糖盘
                g.paint.color = 0xFFFFF8F0.toInt(); g.canvas.drawCircle(cx, gy - 136f * s, 13f * s, g.paint)
                g.paint.style = Paint.Style.STROKE
                g.paint.strokeWidth = 3.5f * s
                g.paint.color = 0xFFFF8FBE.toInt()
                g.canvas.drawCircle(cx, gy - 136f * s, 8.5f * s, g.paint)
                g.canvas.drawCircle(cx, gy - 136f * s, 3f * s, g.paint)
                g.paint.style = Paint.Style.FILL
                g.door(cx); g.window(cx - 50f * s, gy - 52f * s, win); g.window(cx + 50f * s, gy - 52f * s, win)
            }
            else -> {
                rc(cx - 78f * s, gy - 142f * s, cx + 78f * s, gy, pal[0])
                g.boxShade(cx - 78f * s, gy - 142f * s, cx + 78f * s, gy)
                g.paint.color = roofC
                g.canvas.drawCircle(cx - 90f * s, gy - 168f * s, 22f * s, g.paint)
                g.canvas.drawCircle(cx + 90f * s, gy - 168f * s, 22f * s, g.paint)
                // 棒棒糖白芯
                g.paint.color = 0xFFFFF8F0.toInt()
                g.canvas.drawCircle(cx - 90f * s, gy - 168f * s, 9f * s, g.paint)
                g.canvas.drawCircle(cx + 90f * s, gy - 168f * s, 9f * s, g.paint)
                g.pyramidRoof(cx, gy - 142f * s, 92f * s, 36f * s, roofC, roofD)
                // 墙角拐杖糖柱：白底 + 斜纹糖圈
                for (side in intArrayOf(-1, 1)) {
                    val px = cx + side * 62f * s
                    rc(px - 5f * s, gy - 40f * s, px + 5f * s, gy, 0xFFFFF8F0.toInt())
                    for (k in 0..2) rc(px - 5f * s, gy - (34f - k * 12f) * s, px + 5f * s, gy - (29f - k * 12f) * s, 0xFFFF6E8E.toInt())
                }
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
                // 舱体面板缝与四角铆钉
                rc(cx - 30f * s, gy - 80f * s, cx - 27f * s, gy, pal[1])
                rc(cx + 44f * s, gy - 80f * s, cx + 47f * s, gy, pal[1])
                g.paint.color = pal[1]
                for (dx in intArrayOf(-60, 60)) for (dy in intArrayOf(-88, -10)) {
                    g.canvas.drawCircle(cx + dx * s, gy + dy * s, 2.4f * s, g.paint)
                }
                // 两侧交替闪烁的航行灯
                val blinkA = sin(g.homePhase * 3f) > 0f
                g.paint.color = if (blinkA) 0xFFFF5A5A.toInt() else 0x55FF5A5A
                g.canvas.drawCircle(cx - 64f * s, gy - 48f * s, 3f * s, g.paint)
                g.paint.color = if (!blinkA) 0xFF6EFF8A.toInt() else 0x556EFF8A
                g.canvas.drawCircle(cx + 64f * s, gy - 48f * s, 3f * s, g.paint)
                g.door(cx + 18f * s); g.window(cx - 28f * s, gy - 58f * s, win)
            }
            1 -> {
                rc(cx - 92f * s, gy - 108f * s, cx + 92f * s, gy, pal[0])
                g.boxShade(cx - 92f * s, gy - 108f * s, cx + 92f * s, gy)
                rc(cx - 92f * s, gy - 56f * s, cx + 92f * s, gy - 53f * s, pal[1])
                rc(cx - 118f * s, gy - 88f * s, cx - 96f * s, gy - 72f * s, pal[2])
                rc(cx + 96f * s, gy - 88f * s, cx + 118f * s, gy - 72f * s, pal[2])
                g.pyramidRoof(cx, gy - 108f * s, 104f * s, 40f * s, roofC, roofD)
                // 屋角雷达碟：斜立的天线锅 + 中心接收点
                rc(cx + 56f * s, gy - 126f * s, cx + 60f * s, gy - 106f * s, pal[2])
                g.paint.color = pal[2]
                g.canvas.save(); g.canvas.rotate(-24f, cx + 58f * s, gy - 128f * s)
                g.canvas.drawOval(cx + 42f * s, gy - 136f * s, cx + 74f * s, gy - 120f * s, g.paint)
                g.canvas.restore()
                g.paint.color = 0xFF4DE8FF.toInt()
                g.canvas.drawCircle(cx + 58f * s, gy - 130f * s, 2.6f * s, g.paint)
                g.door(cx - 40f * s); g.window(cx + 30f * s, gy - 64f * s, win)
            }
            2 -> {
                rc(cx - 88f * s, gy - 168f * s, cx + 88f * s, gy, pal[0])
                g.boxShade(cx - 88f * s, gy - 168f * s, cx + 88f * s, gy)
                rc(cx - 88f * s, gy - 96f * s, cx + 88f * s, gy - 88f * s, 0xFF4DE8FF.toInt())
                rc(cx - 60f * s, gy - 168f * s, cx - 57f * s, gy - 96f * s, pal[1])
                rc(cx + 57f * s, gy - 168f * s, cx + 60f * s, gy - 96f * s, pal[1])
                g.paint.color = roofC; g.canvas.drawOval(cx - 36f * s, gy - 186f * s, cx + 36f * s, gy - 158f * s, g.paint)
                // 灯带上方一排交替闪烁的航标灯
                for (i in -2..2) {
                    val on = sin(g.homePhase * 3.2f + i * 1.1f) > 0f
                    g.paint.color = if (on) 0xFF9FE8FF.toInt() else 0x559FE8FF
                    g.canvas.drawCircle(cx + i * 34f * s, gy - 102f * s, 2.6f * s, g.paint)
                }
                g.door(cx); g.window(cx - 46f * s, gy - 54f * s, win); g.window(cx + 46f * s, gy - 54f * s, win)
            }
            else -> {
                rc(cx - 72f * s, gy - 156f * s, cx + 72f * s, gy, pal[0])
                g.boxShade(cx - 72f * s, gy - 156f * s, cx + 72f * s, gy)
                rc(cx - 108f * s, gy - 204f * s, cx - 68f * s, gy, pal[1])
                rc(cx + 68f * s, gy - 204f * s, cx + 108f * s, gy, pal[1])
                // 侧塔霓虹窗缝
                for (dy in intArrayOf(-186, -152, -118)) {
                    rc(cx - 96f * s, gy + dy * s, cx - 80f * s, gy + (dy + 4) * s, 0xFF4DE8FF.toInt())
                    rc(cx + 80f * s, gy + dy * s, cx + 96f * s, gy + (dy + 4) * s, 0xFF4DE8FF.toInt())
                }
                g.paint.color = roofC; g.canvas.drawCircle(cx, gy - 178f * s, 14f * s, g.paint)
                rc(cx - 4f * s, gy - 210f * s, cx + 4f * s, gy - 178f * s, pal[2])
                // 指挥塔信标：扩散的光环脉冲 + 天线顶闪灯
                val ring = (g.homePhase * 0.7f) % 1f
                g.paint.style = Paint.Style.STROKE
                g.paint.strokeWidth = 2.5f * s
                g.paint.color = g.withAlpha(0xFF4DE8FF.toInt(), ((1f - ring) * 170).toInt())
                g.canvas.drawCircle(cx, gy - 178f * s, (16f + ring * 18f) * s, g.paint)
                g.paint.style = Paint.Style.FILL
                val blink = sin(g.homePhase * 4f) > 0.2f
                g.paint.color = if (blink) 0xFFFF5A5A.toInt() else 0x66FF5A5A
                g.canvas.drawCircle(cx, gy - 212f * s, 3.2f * s, g.paint)
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
        // 主楼旗杆与迎风摆动的三角旗
        rc(cx - 1.5f * s, gy - 214f * s, cx + 1.5f * s, gy - 188f * s, 0xFF6B4A2B.toInt())
        val wave = sin(g.homePhase * 2.2f) * 3f * s
        rc(cx + 1.5f * s, gy - 212f * s, cx + 15f * s + wave, gy - 206f * s, roofC)
        rc(cx + 1.5f * s, gy - 206f * s, cx + 10f * s + wave * 0.6f, gy - 201f * s, roofC)
        rc(cx - 26f * s, gy - 64f * s, cx + 26f * s, gy, 0xFF6B4A2B.toInt())
        rc(cx - 60f * s, gy - 110f * s, cx - 48f * s, gy - 78f * s, pal[2])
        rc(cx + 48f * s, gy - 110f * s, cx + 60f * s, gy - 78f * s, pal[2])
    }

    // ---------- 庭院装饰（按世界换造型） ----------

    /** 半透明色再叠 ghost 预览的 alpha */
    private fun Gfx.ca(c: Int, a: Int, alpha: Int): Int = withAlpha(c, a * alpha / 255)

    private fun drawGarden(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, flowers: IntArray, stem: Int, g: Gfx, gx: Float, gyOff: Float) {
        val fx = cx + gx * s; val fy = gy + gyOff * s; val rc = g.rc
        val aa = g.paint.isAntiAlias
        if (alpha == 255) g.groundShadow(fx, fy + 32f * s, 44f * s)
        when (world) {
            Game.UNI_WATER -> {
                // 陶盆：亮沿 + 盆身 + 底部暗带
                rc(fx - 38f * s, fy + 16f * s, fx + 38f * s, fy + 21f * s, 0xFFE8836E.toInt())
                rc(fx - 36f * s, fy + 21f * s, fx + 36f * s, fy + 32f * s, 0xFFC66A57.toInt())
                rc(fx - 36f * s, fy + 29f * s, fx + 36f * s, fy + 32f * s, 0xFFA85846.toInt())
                g.paint.isAntiAlias = true
                for (i in 0..2) {
                    val px = fx - 22f * s + i * 22f * s
                    // 海葵：底盘 + 三根随水流摆动的触手 + 触尖亮点
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawCircle(px, fy + 10f * s, 7f * s, g.paint)
                    for (k in -1..1) {
                        val sway = sin(g.homePhase * 2f + i * 1.3f + k * 0.9f) * 2.5f * s
                        rc(px + k * 4.5f * s - 1.2f * s + sway, fy - 8f * s, px + k * 4.5f * s + 1.2f * s + sway, fy + 8f * s, flowers[i])
                        g.paint.color = g.ca(0xFFFFFFFF.toInt(), 170, alpha)
                        g.canvas.drawCircle(px + k * 4.5f * s + sway, fy - 8f * s, 1.6f * s, g.paint)
                        g.paint.color = g.ca(flowers[i], 255, alpha)
                    }
                }
                // 盆沿的小气泡
                val t = (g.homePhase * 0.5f) % 1f
                g.paint.color = g.ca(0xFFCFF2FF.toInt(), ((1f - t) * 130).toInt(), alpha)
                g.canvas.drawCircle(fx + 28f * s, fy + 12f * s - t * 22f * s, 2.2f * s, g.paint)
            }
            Game.UNI_SKY -> {
                // 云托盘：椭圆 + 两端绒球 + 高光
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFE8F4FF.toInt(), 255, alpha)
                g.canvas.drawOval(fx - 44f * s, fy + 10f * s, fx + 44f * s, fy + 30f * s, g.paint)
                g.canvas.drawCircle(fx - 38f * s, fy + 17f * s, 8f * s, g.paint)
                g.canvas.drawCircle(fx + 38f * s, fy + 17f * s, 8f * s, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 150, alpha)
                g.canvas.drawOval(fx - 32f * s, fy + 12f * s, fx - 6f * s, fy + 18f * s, g.paint)
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    val bob = sin(g.homePhase * 1.5f + i * 2.1f) * 2.5f * s
                    // 云绒花：花球 + 受光亮斑
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawOval(px - 10f * s, fy - 10f * s + bob, px + 10f * s, fy + 6f * s + bob, g.paint)
                    g.paint.color = g.ca(0xFFFFFFFF.toInt(), 160, alpha)
                    g.canvas.drawOval(px - 6f * s, fy - 8f * s + bob, px + 1f * s, fy - 2f * s + bob, g.paint)
                }
            }
            Game.UNI_LAVA -> {
                // 焦土苗床：亮沿 + 呼吸的岩浆细缝
                rc(fx - 40f * s, fy + 20f * s, fx + 40f * s, fy + 34f * s, 0xFF4A3028.toInt())
                rc(fx - 40f * s, fy + 20f * s, fx + 40f * s, fy + 23f * s, 0xFF5A4038.toInt())
                val glow = 0.5f + 0.5f * sin(g.homePhase * 2.6f)
                g.paint.color = g.ca(0xFFFF7A2A.toInt(), (110 + 120 * glow).toInt(), alpha)
                g.canvas.drawRect(fx - 26f * s, fy + 26f * s, fx - 8f * s, fy + 28f * s, g.paint)
                g.canvas.drawRect(fx + 6f * s, fy + 29f * s, fx + 26f * s, fy + 31f * s, g.paint)
                g.paint.isAntiAlias = true
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    // 焦枝 + 侧杈
                    rc(px - 3f * s, fy + 2f * s, px + 3f * s, fy + 20f * s, 0xFF6E4A3A.toInt())
                    rc(px - 7f * s, fy + 6f * s, px - 3f * s, fy + 9f * s, 0xFF6E4A3A.toInt())
                    // 余烬花：光晕 + 花核 + 亮心，按各自节奏呼吸
                    val fl = 0.5f + 0.5f * sin(g.homePhase * 3f + i * 2.2f)
                    g.paint.color = g.ca(flowers[i], (80 + 60 * fl).toInt(), alpha)
                    g.canvas.drawCircle(px, fy - 4f * s, 9f * s, g.paint)
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawCircle(px, fy - 4f * s, 5f * s, g.paint)
                    g.paint.color = g.ca(0xFFFFE0B0.toInt(), (140 + 110 * fl).toInt(), alpha)
                    g.canvas.drawCircle(px, fy - 4f * s, 2.2f * s, g.paint)
                }
            }
            Game.UNI_CANDY -> {
                // 巧克力花盒 + 糖霜滴边
                rc(fx - 40f * s, fy + 16f * s, fx + 40f * s, fy + 32f * s, 0xFF6B4230.toInt())
                rc(fx - 40f * s, fy + 29f * s, fx + 40f * s, fy + 32f * s, 0xFF54321F.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFFFF8F0.toInt(), 255, alpha)
                var dx = fx - 33f * s
                while (dx < fx + 34f * s) { g.canvas.drawCircle(dx, fy + 18f * s, 3.5f * s, g.paint); dx += 9f * s }
                for (i in 0..2) {
                    val px = fx - 26f * s + i * 26f * s
                    // 棒棒糖花：糖棍 + 糖圈 + 白芯 + 糖点
                    rc(px - 1.5f * s, fy + 2f * s, px + 1.5f * s, fy + 18f * s, 0xFFF2E8D8.toInt())
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawCircle(px, fy - 6f * s, 9f * s, g.paint)
                    g.paint.color = g.ca(0xFFFFF8F0.toInt(), 255, alpha)
                    g.canvas.drawCircle(px, fy - 6f * s, 4.5f * s, g.paint)
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawCircle(px, fy - 6f * s, 1.8f * s, g.paint)
                }
                // 散落的糖豆
                rc(fx - 18f * s, fy + 23f * s, fx - 13f * s, fy + 28f * s, 0xFF7ADBC8.toInt())
                rc(fx + 10f * s, fy + 24f * s, fx + 15f * s, fy + 29f * s, 0xFFFFD54A.toInt())
            }
            Game.UNI_SPACE -> {
                // 金属栽培槽：亮沿 + 呼吸的营养液灯条
                rc(fx - 38f * s, fy + 18f * s, fx + 38f * s, fy + 32f * s, 0xFF565B6E.toInt())
                rc(fx - 38f * s, fy + 18f * s, fx + 38f * s, fy + 21f * s, 0xFF6E7488.toInt())
                val pulse = 0.5f + 0.5f * sin(g.homePhase * 2f)
                g.paint.color = g.ca(0xFF4DE8FF.toInt(), (100 + 110 * pulse).toInt(), alpha)
                g.canvas.drawRect(fx - 34f * s, fy + 25f * s, fx + 34f * s, fy + 27f * s, g.paint)
                g.paint.isAntiAlias = true
                for (i in 0..2) {
                    val px = fx - 24f * s + i * 24f * s
                    rc(px - 2f * s, fy + 4f * s, px + 2f * s, fy + 18f * s, stem)
                    // 晶花：主晶 + 侧晶 + 晶尖闪烁辉光
                    rc(px - 4f * s, fy - 12f * s, px + 4f * s, fy + 6f * s, flowers[i])
                    rc(px - 8f * s, fy - 4f * s, px - 4f * s, fy + 4f * s, flowers[i])
                    rc(px - 2f * s, fy - 8f * s, px + 2f * s, fy - 4f * s, 0xFFFFFFFF.toInt())
                    val tw = 0.5f + 0.5f * sin(g.homePhase * 2.4f + i * 1.8f)
                    g.paint.color = g.ca(0xFFFFFFFF.toInt(), (80 + 140 * tw).toInt(), alpha)
                    g.canvas.drawCircle(px, fy - 13f * s, 2.5f * s, g.paint)
                }
            }
            else -> {
                // 木花箱：亮沿 + 土壤 + 底部暗带
                rc(fx - 40f * s, fy + 16f * s, fx + 40f * s, fy + 32f * s, 0xFF97622F.toInt())
                rc(fx - 40f * s, fy + 16f * s, fx + 40f * s, fy + 19f * s, 0xFFB07A45.toInt())
                rc(fx - 36f * s, fy + 12f * s, fx + 36f * s, fy + 16f * s, 0xFF5A3D22.toInt())
                rc(fx - 40f * s, fy + 29f * s, fx + 40f * s, fy + 32f * s, 0xFF7A4E22.toInt())
                g.paint.isAntiAlias = true
                for (i in 0..2) {
                    val px = fx - 26f * s + i * 26f * s
                    val bob = sin(g.homePhase * 1.6f + i * 1.9f) * 1.5f * s
                    // 花茎 + 侧叶
                    rc(px - 1.5f * s, fy + 2f * s, px + 1.5f * s, fy + 14f * s, stem)
                    rc(px + 1.5f * s, fy + 7f * s, px + 6f * s, fy + 10f * s, stem)
                    // 四瓣花 + 亮花心，随微风轻点头
                    val py = fy - 6f * s + bob
                    g.paint.color = g.ca(flowers[i], 255, alpha)
                    g.canvas.drawCircle(px - 4.5f * s, py, 4f * s, g.paint)
                    g.canvas.drawCircle(px + 4.5f * s, py, 4f * s, g.paint)
                    g.canvas.drawCircle(px, py - 4.5f * s, 4f * s, g.paint)
                    g.canvas.drawCircle(px, py + 4.5f * s, 4f * s, g.paint)
                    g.paint.color = g.ca(0xFFFFF3B8.toInt(), 255, alpha)
                    g.canvas.drawCircle(px, py, 3f * s, g.paint)
                }
            }
        }
        g.paint.isAntiAlias = aa
    }

    private fun drawFence(world: Int, cx: Float, gy: Float, half: Float, s: Float, alpha: Int, fence: IntArray, g: Gfx, fenceY: Float) {
        val fy = gy + fenceY * s; val rc = g.rc
        val aa = g.paint.isAntiAlias
        when (world) {
            Game.UNI_WATER -> {
                // 珊瑚指栏：主枝 + 交错侧芽 + 受光亮面，横杆穿过
                g.paint.isAntiAlias = true
                var px = cx - half + 24f * s
                var i = 0
                while (px < cx + half - 24f * s) {
                    g.paint.color = g.ca(fence[0], 255, alpha)
                    g.canvas.drawOval(px - 8f * s, fy + 30f * s, px + 8f * s, fy + 58f * s, g.paint)
                    g.canvas.drawCircle(px + (if (i % 2 == 0) 9f else -9f) * s, fy + 38f * s, 4.5f * s, g.paint)
                    g.paint.color = g.ca(0xFFFFFFFF.toInt(), 90, alpha)
                    g.canvas.drawOval(px - 4f * s, fy + 33f * s, px + 1f * s, fy + 44f * s, g.paint)
                    px += 36f * s; i++
                }
                rc(cx - half + 10f * s, fy + 44f * s, cx + half - 10f * s, fy + 50f * s, fence[1])
                rc(cx - half + 10f * s, fy + 48f * s, cx + half - 10f * s, fy + 50f * s, fence[0])
            }
            Game.UNI_SKY -> {
                // 云篱：长云带上叠大小绒球，前缘一道高光
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(fence[0], 255, alpha)
                g.canvas.drawOval(cx - half + 20f * s, fy + 38f * s, cx + half - 20f * s, fy + 54f * s, g.paint)
                var px = cx - half + 36f * s
                var i = 0
                while (px < cx + half - 30f * s) {
                    g.canvas.drawCircle(px, fy + 40f * s, (7f + (i % 3) * 3f) * s, g.paint)
                    px += 42f * s; i++
                }
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 120, alpha)
                g.canvas.drawOval(cx - half + 30f * s, fy + 39f * s, cx - half + 92f * s, fy + 46f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                // 黑曜石碎片：一高一矮交错斜插，石间岩浆微光呼吸
                val glow = 0.5f + 0.5f * sin(g.homePhase * 2.6f)
                var px = cx - half + 22f * s
                var i = 0
                while (px < cx + half - 22f * s) {
                    val hgt = if (i % 2 == 0) 34f else 24f
                    g.canvas.save(); g.canvas.rotate(if (i % 2 == 0) 6f else -5f, px, fy + 62f * s)
                    rc(px - 5f * s, fy + (62f - hgt) * s, px + 3f * s, fy + 62f * s, fence[0])
                    rc(px, fy + (62f - hgt) * s, px + 3f * s, fy + 62f * s, fence[1])
                    g.canvas.restore()
                    if (px + 34f * s < cx + half - 22f * s) {
                        g.paint.color = g.ca(0xFFFF7A2A.toInt(), (60 + 90 * glow).toInt(), alpha)
                        g.canvas.drawRect(px + 10f * s, fy + 58f * s, px + 28f * s, fy + 61f * s, g.paint)
                    }
                    px += 38f * s; i++
                }
            }
            Game.UNI_CANDY -> {
                // 拐杖糖栏：白柱 + 斜纹糖圈 + 圆糖顶，奶油横杆
                g.paint.isAntiAlias = true
                var px = cx - half + 20f * s
                while (px < cx + half - 20f * s) {
                    rc(px - 4f * s, fy + 32f * s, px + 4f * s, fy + 62f * s, fence[0])
                    for (k in 0..1) rc(px - 4f * s, fy + (38f + k * 12f) * s, px + 4f * s, fy + (42f + k * 12f) * s, 0xFFFF6E8E.toInt())
                    g.paint.color = g.ca(0xFFFFF8F0.toInt(), 255, alpha)
                    g.canvas.drawCircle(px, fy + 31f * s, 5f * s, g.paint)
                    px += 34f * s
                }
                rc(cx - half + 12f * s, fy + 44f * s, cx + half - 12f * s, fy + 50f * s, fence[1])
                rc(cx - half + 12f * s, fy + 44f * s, cx + half - 12f * s, fy + 46f * s, 0xFFFFF8F0.toInt())
            }
            Game.UNI_SPACE -> {
                // 悬浮光栏：立柱顶着光珠，中间一条呼吸的能量带
                val pulse = 0.5f + 0.5f * sin(g.homePhase * 2f)
                g.paint.isAntiAlias = true
                var px = cx - half + 28f * s
                while (px < cx + half - 28f * s) {
                    rc(px - 2f * s, fy + 30f * s, px + 2f * s, fy + 64f * s, fence[0])
                    rc(px - 2f * s, fy + 30f * s, px, fy + 64f * s, 0xFF7E8498.toInt())
                    g.paint.color = g.ca(0xFF9FE8FF.toInt(), (140 + 110 * pulse).toInt(), alpha)
                    g.canvas.drawCircle(px, fy + 28f * s, 2.6f * s, g.paint)
                    px += 40f * s
                }
                g.paint.color = g.ca(0xFF4DE8FF.toInt(), (55 + 55 * pulse).toInt(), alpha)
                g.canvas.drawRect(cx - half + 22f * s, fy + 36f * s, cx + half - 22f * s, fy + 44f * s, g.paint)
                g.paint.color = g.ca(0xFF9FE8FF.toInt(), (140 + 100 * pulse).toInt(), alpha)
                g.canvas.drawRect(cx - half + 22f * s, fy + 38.5f * s, cx + half - 22f * s, fy + 41.5f * s, g.paint)
            }
            else -> {
                // 尖顶木桩 + 双横杆，右缘留暗边
                var px = cx - half + 20f * s
                while (px < cx + half - 20f * s) {
                    rc(px - 2f * s, fy + 31f * s, px + 2f * s, fy + 36f * s, fence[0])
                    rc(px - 3f * s, fy + 34f * s, px + 3f * s, fy + 64f * s, fence[0])
                    rc(px + 1.5f * s, fy + 36f * s, px + 3f * s, fy + 64f * s, fence[1])
                    px += 34f * s
                }
                rc(cx - half + 12f * s, fy + 40f * s, cx + half - 12f * s, fy + 45f * s, fence[1])
                rc(cx - half + 12f * s, fy + 52f * s, cx + half - 12f * s, fy + 57f * s, fence[1])
            }
        }
        g.paint.isAntiAlias = aa
    }

    private fun drawMailbox(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, mx: Float, my: Float) {
        val x = cx + mx * s; val fy = gy + my * s; val rc = g.rc
        val aa = g.paint.isAntiAlias
        if (alpha == 255) g.groundShadow(x, fy, 18f * s)
        when (world) {
            Game.UNI_WATER -> {
                // 浮标信箱：蓝浮筒 + 红条纹 + 投递舷窗 + 顶部提环
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFF57B6E8.toInt(), 255, alpha)
                g.canvas.drawOval(x - 14f * s, fy - 48f * s, x + 14f * s, fy - 8f * s, g.paint)
                g.paint.color = g.ca(0xFFF25A5A.toInt(), 255, alpha)
                g.canvas.drawRect(x - 13f * s, fy - 34f * s, x + 13f * s, fy - 26f * s, g.paint)
                g.paint.color = g.ca(0xFF64998F.toInt(), 255, alpha)
                g.canvas.drawCircle(x, fy - 30f * s, 6.5f * s, g.paint)
                g.paint.color = g.ca(0xFFE4FAFF.toInt(), 255, alpha)
                g.canvas.drawCircle(x, fy - 30f * s, 4.5f * s, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 110, alpha)
                g.canvas.drawOval(x - 9f * s, fy - 45f * s, x - 2f * s, fy - 36f * s, g.paint)
                g.paint.style = Paint.Style.STROKE
                g.paint.strokeWidth = 2.5f * s
                g.paint.color = g.ca(0xFFC66A57.toInt(), 255, alpha)
                g.canvas.drawCircle(x, fy - 50f * s, 4f * s, g.paint)
                g.paint.style = Paint.Style.FILL
                rc(x - 3f * s, fy - 8f * s, x + 3f * s, fy, 0xFFC66A57.toInt())
            }
            Game.UNI_SKY -> {
                // 云朵信箱：木柱 + 云球箱体 + 金色投递口
                rc(x - 3f * s, fy - 40f * s, x + 3f * s, fy, 0xFFC9A570.toInt())
                rc(x - 1f * s, fy - 40f * s, x + 3f * s, fy, 0xFFB08A50.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFE8F4FF.toInt(), 255, alpha)
                g.canvas.drawOval(x - 18f * s, fy - 58f * s, x + 18f * s, fy - 38f * s, g.paint)
                g.canvas.drawCircle(x - 14f * s, fy - 44f * s, 6f * s, g.paint)
                g.canvas.drawCircle(x + 14f * s, fy - 44f * s, 6f * s, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 150, alpha)
                g.canvas.drawOval(x - 12f * s, fy - 55f * s, x + 2f * s, fy - 49f * s, g.paint)
                g.paint.color = g.ca(0xFFF2C14E.toInt(), 255, alpha)
                g.canvas.drawRect(x - 7f * s, fy - 50f * s, x + 7f * s, fy - 46.5f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                // 石砌信箱：亮顶沿 + 呼吸的投递口熔光 + 顶部火星
                rc(x - 3f * s, fy - 30f * s, x + 3f * s, fy, 0xFF43302A.toInt())
                rc(x - 18f * s, fy - 50f * s, x + 18f * s, fy - 28f * s, 0xFF4A3028.toInt())
                rc(x - 18f * s, fy - 50f * s, x + 18f * s, fy - 47f * s, 0xFF5A4038.toInt())
                val glow = 0.5f + 0.5f * sin(g.homePhase * 2.8f)
                g.paint.color = g.ca(0xFFFF7A2A.toInt(), (140 + 110 * glow).toInt(), alpha)
                g.canvas.drawRect(x - 10f * s, fy - 42f * s, x + 10f * s, fy - 38f * s, g.paint)
                g.paint.color = g.ca(0xFFFFA25A.toInt(), (glow * 220).toInt(), alpha)
                g.canvas.drawRect(x + 10f * s, fy - 58f * s, x + 13f * s, fy - 55f * s, g.paint)
            }
            Game.UNI_CANDY -> {
                // 马卡龙信箱：上下糖壳夹奶油 + 樱桃钮
                rc(x - 3f * s, fy - 26f * s, x + 3f * s, fy, 0xFFA46A3E.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFFF8FBE.toInt(), 255, alpha)
                g.canvas.drawArc(x - 16f * s, fy - 56f * s, x + 16f * s, fy - 32f * s, 180f, 180f, true, g.paint)
                g.canvas.drawArc(x - 16f * s, fy - 44f * s, x + 16f * s, fy - 26f * s, 0f, 180f, true, g.paint)
                g.paint.color = g.ca(0xFFFFF8F0.toInt(), 255, alpha)
                g.canvas.drawRect(x - 15f * s, fy - 45f * s, x + 15f * s, fy - 41f * s, g.paint)
                g.paint.color = g.ca(0xFFFFB8D4.toInt(), 255, alpha)
                g.canvas.drawOval(x - 10f * s, fy - 53f * s, x - 2f * s, fy - 48f * s, g.paint)
                g.paint.color = g.ca(0xFFE84A4A.toInt(), 255, alpha)
                g.canvas.drawCircle(x, fy - 57f * s, 3.5f * s, g.paint)
            }
            Game.UNI_SPACE -> return // 星空世界无信箱槽位
            else -> {
                // 美式圆顶信箱：木柱木纹 + 弧顶箱体 + 投递缝 + 小黄旗
                rc(x - 3f * s, fy - 34f * s, x + 3f * s, fy, 0xFF97622F.toInt())
                rc(x - 1f * s, fy - 30f * s, x, fy - 6f * s, 0xFF7A4E22.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFF25A5A.toInt(), 255, alpha)
                g.canvas.drawRect(x - 16f * s, fy - 44f * s, x + 16f * s, fy - 32f * s, g.paint)
                g.canvas.drawArc(x - 16f * s, fy - 54f * s, x + 16f * s, fy - 34f * s, 180f, 180f, true, g.paint)
                g.paint.color = g.ca(0xFFB23E3E.toInt(), 255, alpha)
                g.canvas.drawRect(x - 10f * s, fy - 41f * s, x + 10f * s, fy - 38f * s, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 100, alpha)
                g.canvas.drawOval(x - 12f * s, fy - 51f * s, x, fy - 45f * s, g.paint)
                rc(x + 14f * s, fy - 62f * s, x + 17f * s, fy - 44f * s, 0xFF97622F.toInt())
                rc(x + 17f * s, fy - 62f * s, x + 26f * s, fy - 55f * s, 0xFFFFD75E.toInt())
            }
        }
        g.paint.isAntiAlias = aa
    }

    private fun drawSwing(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, sx: Float, sy: Float) {
        val x = cx + sx * s; val fy = gy + sy * s; val rc = g.rc
        val sway = sin(g.homePhase * 1.6f) * 10f * s
        val aa = g.paint.isAntiAlias
        if (alpha == 255) g.groundShadow(x, fy, 40f * s)
        when (world) {
            Game.UNI_WATER -> {
                // 沉木架 + 海草吊绳 + 双色海藻坐板
                rc(x - 30f * s, fy - 78f * s, x - 26f * s, fy, 0xFF64998F.toInt())
                rc(x + 26f * s, fy - 78f * s, x + 30f * s, fy, 0xFF64998F.toInt())
                rc(x - 34f * s, fy - 84f * s, x + 34f * s, fy - 76f * s, 0xFF578A80.toInt())
                rc(x - 34f * s, fy - 84f * s, x + 34f * s, fy - 82f * s, 0xFF6EAA9E.toInt())
                rc(x - 13f * s + sway * 0.6f, fy - 76f * s, x - 10f * s + sway, fy - 32f * s, 0xFF2E8A6E.toInt())
                rc(x + 10f * s + sway * 0.6f, fy - 76f * s, x + 13f * s + sway, fy - 32f * s, 0xFF2E8A6E.toInt())
                rc(x - 32f * s + sway, fy - 34f * s, x + 32f * s + sway, fy - 28f * s, 0xFF6EE8D8.toInt())
                rc(x - 32f * s + sway, fy - 30f * s, x + 32f * s + sway, fy - 28f * s, 0xFF4EC8B8.toInt())
            }
            Game.UNI_SKY -> {
                // 悬浮云锚缓缓起伏，银索吊着云绒坐垫
                g.paint.isAntiAlias = true
                val bob = sin(g.homePhase * 1.1f) * 2f * s
                g.paint.color = g.ca(0xFFE8F4FF.toInt(), 255, alpha)
                g.canvas.drawOval(x - 18f * s, fy - 96f * s + bob, x + 18f * s, fy - 80f * s + bob, g.paint)
                g.canvas.drawCircle(x - 13f * s, fy - 85f * s + bob, 6f * s, g.paint)
                g.canvas.drawCircle(x + 13f * s, fy - 85f * s + bob, 6f * s, g.paint)
                rc(x - 12f * s + sway, fy - 84f * s + bob, x - 9f * s + sway * 1.2f, fy - 36f * s, 0xFFB9B9B9.toInt())
                rc(x + 9f * s + sway, fy - 84f * s + bob, x + 12f * s + sway * 1.2f, fy - 36f * s, 0xFFB9B9B9.toInt())
                g.paint.color = g.ca(0xFFE8F4FF.toInt(), 255, alpha)
                g.canvas.drawOval(x - 20f * s + sway, fy - 36f * s, x + 20f * s + sway, fy - 24f * s, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 150, alpha)
                g.canvas.drawOval(x - 14f * s + sway, fy - 35f * s, x - 2f * s + sway, fy - 30f * s, g.paint)
            }
            Game.UNI_LAVA -> {
                // 黑曜石架 + 铁链 + 带余温辉光的坐板
                rc(x - 32f * s, fy - 84f * s, x - 28f * s, fy, 0xFF43302A.toInt())
                rc(x + 28f * s, fy - 84f * s, x + 32f * s, fy, 0xFF43302A.toInt())
                rc(x - 36f * s, fy - 90f * s, x + 36f * s, fy - 82f * s, 0xFF4A3028.toInt())
                rc(x - 36f * s, fy - 90f * s, x + 36f * s, fy - 88f * s, 0xFF5A4038.toInt())
                rc(x - 12f * s + sway * 0.6f, fy - 82f * s, x - 9f * s + sway, fy - 34f * s, 0xFF6E5A50.toInt())
                rc(x + 9f * s + sway * 0.6f, fy - 82f * s, x + 12f * s + sway, fy - 34f * s, 0xFF6E5A50.toInt())
                rc(x - 16f * s + sway, fy - 34f * s, x + 16f * s + sway, fy - 26f * s, 0xFFFF7A2A.toInt())
                val glow = 0.5f + 0.5f * sin(g.homePhase * 2.6f)
                g.paint.color = g.ca(0xFFFFC48A.toInt(), (glow * 140).toInt(), alpha)
                g.canvas.drawRect(x - 16f * s + sway, fy - 34f * s, x + 16f * s + sway, fy - 31f * s, g.paint)
            }
            Game.UNI_CANDY -> {
                // 拐杖糖立柱 + 巧克力横梁 + 甘草绳 + 双色糖坐板
                rc(x - 30f * s, fy - 80f * s, x - 26f * s, fy, 0xFFFFF8F0.toInt())
                rc(x + 26f * s, fy - 80f * s, x + 30f * s, fy, 0xFFFFF8F0.toInt())
                for (k in 0..3) {
                    rc(x - 30f * s, fy - (72f - k * 20f) * s, x - 26f * s, fy - (66f - k * 20f) * s, 0xFFFF6E8E.toInt())
                    rc(x + 26f * s, fy - (72f - k * 20f) * s, x + 30f * s, fy - (66f - k * 20f) * s, 0xFFFF6E8E.toInt())
                }
                rc(x - 34f * s, fy - 86f * s, x + 34f * s, fy - 78f * s, 0xFF6B4230.toInt())
                rc(x - 34f * s, fy - 86f * s, x + 34f * s, fy - 84f * s, 0xFF8A5A40.toInt())
                rc(x - 12f * s + sway * 0.6f, fy - 78f * s, x - 9f * s + sway, fy - 36f * s, 0xFFE87F9F.toInt())
                rc(x + 9f * s + sway * 0.6f, fy - 78f * s, x + 12f * s + sway, fy - 36f * s, 0xFFE87F9F.toInt())
                rc(x - 20f * s + sway, fy - 36f * s, x + 20f * s + sway, fy - 26f * s, 0xFFFF8FBE.toInt())
                rc(x - 20f * s + sway, fy - 29f * s, x + 20f * s + sway, fy - 26f * s, 0xFFD86F9E.toInt())
            }
            Game.UNI_SPACE -> {
                // 金属门架 + 半透明能量吊索 + 发光坐板与顶部航标灯
                rc(x - 34f * s, fy - 86f * s, x - 30f * s, fy, 0xFF565B6E.toInt())
                rc(x + 30f * s, fy - 86f * s, x + 34f * s, fy, 0xFF565B6E.toInt())
                rc(x - 38f * s, fy - 92f * s, x + 38f * s, fy - 84f * s, 0xFF6E7488.toInt())
                val pulse = 0.5f + 0.5f * sin(g.homePhase * 2f)
                g.paint.color = g.ca(0xFF4DE8FF.toInt(), (100 + 90 * pulse).toInt(), alpha)
                g.canvas.drawRect(x - 12f * s + sway * 0.6f, fy - 84f * s, x - 9f * s + sway, fy - 38f * s, g.paint)
                g.canvas.drawRect(x + 9f * s + sway * 0.6f, fy - 84f * s, x + 12f * s + sway, fy - 38f * s, g.paint)
                rc(x - 18f * s + sway, fy - 38f * s, x + 18f * s + sway, fy - 30f * s, 0xFF4DE8FF.toInt())
                g.paint.color = g.ca(0xFF9FE8FF.toInt(), 255, alpha)
                g.canvas.drawRect(x - 18f * s + sway, fy - 38f * s, x + 18f * s + sway, fy - 36f * s, g.paint)
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFFF5A5A.toInt(), if (sin(g.homePhase * 3f) > 0f) 255 else 90, alpha)
                g.canvas.drawCircle(x, fy - 88f * s, 2.5f * s, g.paint)
            }
            else -> {
                // A 字木架（微外倾）+ 双色横梁 + 麻绳 + 木纹坐板
                for (side in intArrayOf(-1, 1)) {
                    g.canvas.save(); g.canvas.rotate(side * 6f, x + side * 31f * s, fy)
                    rc(x + side * 31f * s - 3f * s, fy - 86f * s, x + side * 31f * s + 3f * s, fy, 0xFF97622F.toInt())
                    g.canvas.restore()
                }
                rc(x - 38f * s, fy - 90f * s, x + 38f * s, fy - 82f * s, 0xFF7A4E22.toInt())
                rc(x - 38f * s, fy - 90f * s, x + 38f * s, fy - 87f * s, 0xFF97622F.toInt())
                rc(x - 14f * s + sway, fy - 82f * s, x - 11f * s + sway * 1.2f, fy - 34f * s, 0xFFC9A570.toInt())
                rc(x + 11f * s + sway, fy - 82f * s, x + 14f * s + sway * 1.2f, fy - 34f * s, 0xFFC9A570.toInt())
                rc(x - 18f * s + sway * 1.2f, fy - 34f * s, x + 18f * s + sway * 1.2f, fy - 26f * s, 0xFFFFD75E.toInt())
                rc(x - 18f * s + sway * 1.2f, fy - 29f * s, x + 18f * s + sway * 1.2f, fy - 26f * s, 0xFFD9A82E.toInt())
            }
        }
        g.paint.isAntiAlias = aa
    }

    private fun drawPerch(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, px: Float, py: Float) {
        val x = cx + px * s; val fy = gy + py * s; val rc = g.rc
        val aa = g.paint.isAntiAlias
        if (alpha == 255) g.groundShadow(x, fy, 22f * s)
        when (world) {
            Game.UNI_WATER -> {
                // 沉木爬架：底座 + 双层带沿口平台 + 顶层冒泡
                rc(x - 4f * s, fy - 88f * s, x + 4f * s, fy, 0xFFC66A57.toInt())
                rc(x - 14f * s, fy - 4f * s, x + 14f * s, fy, 0xFFB25A47.toInt())
                rc(x - 28f * s, fy - 58f * s, x + 8f * s, fy - 50f * s, 0xFF64998F.toInt())
                rc(x - 28f * s, fy - 53f * s, x + 8f * s, fy - 50f * s, 0xFF527F76.toInt())
                rc(x - 8f * s, fy - 94f * s, x + 28f * s, fy - 86f * s, 0xFF6EE8D8.toInt())
                rc(x - 8f * s, fy - 89f * s, x + 28f * s, fy - 86f * s, 0xFF54C8B8.toInt())
                g.paint.isAntiAlias = true
                for (i in 0 until 2) {
                    val t = (g.homePhase * 0.4f + i * 0.5f) % 1f
                    g.paint.color = g.ca(0xFFCFF2FF.toInt(), ((1f - t) * 120).toInt(), alpha)
                    g.canvas.drawCircle(x + 8f * s + i * 9f * s, fy - 97f * s - t * 20f * s, 2.4f * s, g.paint)
                }
            }
            Game.UNI_SKY -> {
                // 云端爬架：双层云台反向起伏
                rc(x - 4f * s, fy - 92f * s, x + 4f * s, fy, 0xFFDCD2B4.toInt())
                rc(x - 14f * s, fy - 4f * s, x + 14f * s, fy, 0xFFC9BD9C.toInt())
                g.paint.isAntiAlias = true
                val bob = sin(g.homePhase * 1.4f) * 2f * s
                g.paint.color = g.ca(0xFFE8F4FF.toInt(), 255, alpha)
                g.canvas.drawOval(x - 32f * s, fy - 66f * s + bob, x + 12f * s, fy - 54f * s + bob, g.paint)
                g.canvas.drawOval(x - 12f * s, fy - 104f * s - bob, x + 28f * s, fy - 92f * s - bob, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 150, alpha)
                g.canvas.drawOval(x - 26f * s, fy - 64f * s + bob, x - 10f * s, fy - 59f * s + bob, g.paint)
                g.canvas.drawOval(x - 6f * s, fy - 102f * s - bob, x + 10f * s, fy - 97f * s - bob, g.paint)
            }
            Game.UNI_LAVA -> {
                // 玄武岩爬塔：石座 + 石台 + 顶层余温辉光
                rc(x - 5f * s, fy - 94f * s, x + 5f * s, fy, 0xFF43302A.toInt())
                rc(x - 15f * s, fy - 4f * s, x + 15f * s, fy, 0xFF4A3028.toInt())
                rc(x - 32f * s, fy - 62f * s, x + 10f * s, fy - 52f * s, 0xFF4A3028.toInt())
                rc(x - 32f * s, fy - 55f * s, x + 10f * s, fy - 52f * s, 0xFF3A2622.toInt())
                rc(x - 10f * s, fy - 100f * s, x + 30f * s, fy - 90f * s, 0xFFFF7A2A.toInt())
                val glow = 0.5f + 0.5f * sin(g.homePhase * 2.6f)
                g.paint.color = g.ca(0xFFFFC48A.toInt(), (glow * 150).toInt(), alpha)
                g.canvas.drawRect(x - 10f * s, fy - 100f * s, x + 30f * s, fy - 97f * s, g.paint)
            }
            Game.UNI_CANDY -> {
                // 华夫饼柱 + 薄荷/草莓糖台 + 顶台樱桃
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFFA46A3E.toInt())
                for (k in 0..3) rc(x - 4f * s, fy - (88f - k * 22f) * s, x + 4f * s, fy - (86f - k * 22f) * s, 0xFF8A5A36.toInt())
                rc(x - 15f * s, fy - 4f * s, x + 15f * s, fy, 0xFF6B4230.toInt())
                rc(x - 30f * s, fy - 64f * s, x + 10f * s, fy - 54f * s, 0xFF7ADBC8.toInt())
                rc(x - 30f * s, fy - 57f * s, x + 10f * s, fy - 54f * s, 0xFF5ABBA8.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 92f * s, 0xFFFF8FBE.toInt())
                rc(x - 10f * s, fy - 95f * s, x + 30f * s, fy - 92f * s, 0xFFD86F9E.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFE84A4A.toInt(), 255, alpha)
                g.canvas.drawCircle(x + 22f * s, fy - 105f * s, 3.5f * s, g.paint)
            }
            Game.UNI_SPACE -> {
                // 反重力爬台：平台底缘泛着呼吸的悬浮光
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFF565B6E.toInt())
                rc(x - 15f * s, fy - 4f * s, x + 15f * s, fy, 0xFF474C60.toInt())
                rc(x - 30f * s, fy - 66f * s, x + 10f * s, fy - 58f * s, 0xFF6E7488.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 94f * s, 0xFF4DE8FF.toInt())
                val pulse = 0.5f + 0.5f * sin(g.homePhase * 2.2f)
                g.paint.color = g.ca(0xFF4DE8FF.toInt(), (60 + 80 * pulse).toInt(), alpha)
                g.canvas.drawRect(x - 26f * s, fy - 57f * s, x + 6f * s, fy - 54f * s, g.paint)
                g.canvas.drawRect(x - 6f * s, fy - 93f * s, x + 26f * s, fy - 90f * s, g.paint)
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFF9FE8FF.toInt(), (150 + 100 * pulse).toInt(), alpha)
                g.canvas.drawCircle(x, fy - 100f * s, 2.5f * s, g.paint)
            }
            else -> {
                // 木爬架：剑麻缠绕柱 + 底座 + 带沿口平台 + 晃动的吊球玩具
                rc(x - 4f * s, fy - 96f * s, x + 4f * s, fy, 0xFFC9A570.toInt())
                for (k in 0..2) rc(x - 4f * s, fy - (48f - k * 8f) * s, x + 4f * s, fy - (45f - k * 8f) * s, 0xFFB08A50.toInt())
                rc(x - 16f * s, fy - 4f * s, x + 16f * s, fy, 0xFF97622F.toInt())
                rc(x - 30f * s, fy - 64f * s, x + 10f * s, fy - 54f * s, 0xFF8594B3.toInt())
                rc(x - 30f * s, fy - 57f * s, x + 10f * s, fy - 54f * s, 0xFF6E7C9C.toInt())
                rc(x - 10f * s, fy - 102f * s, x + 30f * s, fy - 92f * s, 0xFFF5A8C1.toInt())
                rc(x - 10f * s, fy - 95f * s, x + 30f * s, fy - 92f * s, 0xFFD98BA6.toInt())
                val sw = sin(g.homePhase * 2.2f) * 3f * s
                rc(x + 18f * s, fy - 92f * s, x + 19.5f * s + sw * 0.4f, fy - 79f * s, 0xFFB9B9B9.toInt())
                g.paint.isAntiAlias = true
                g.paint.color = g.ca(0xFFF25A5A.toInt(), 255, alpha)
                g.canvas.drawCircle(x + 19f * s + sw, fy - 75f * s, 4.5f * s, g.paint)
                g.paint.color = g.ca(0xFFFF9A9A.toInt(), 255, alpha)
                g.canvas.drawCircle(x + 17.5f * s + sw, fy - 76.5f * s, 1.6f * s, g.paint)
            }
        }
        g.paint.isAntiAlias = aa
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
        g.paint.shader = null
        // 岸沿高光 + 池边瓷砖缝
        g.paint.color = g.ca(0xFFFFFFFF.toInt(), 70, alpha)
        g.canvas.drawRoundRect(wl + 3f * s, wt + 2.5f * s, wl + (wr - wl) * 0.45f, wt + 8f * s, 4f * s, 4f * s, g.paint)
        g.paint.color = g.ca(0xFF000000.toInt(), 40, alpha)
        var tx = left + 18f * s
        while (tx < right - 12f * s) {
            g.canvas.drawRect(tx, top + 1f * s, tx + 2f * s, top + 4f * s, g.paint)
            tx += 22f * s
        }
        // 两圈慢慢扩散的波纹
        g.paint.style = Paint.Style.STROKE
        g.paint.strokeWidth = 1.8f * s
        for (i in 0 until 2) {
            val t = (g.homePhase * 0.35f + i * 0.5f) % 1f
            g.paint.color = g.ca(0xFFFFFFFF.toInt(), ((1f - t) * 90).toInt(), alpha)
            val rx = wl + (wr - wl) * (0.32f + i * 0.38f)
            val ry = (wt + wb) * 0.5f
            val rw = (6f + 13f * t) * s
            g.canvas.drawOval(rx - rw, ry - rw * 0.45f, rx + rw, ry + rw * 0.45f, g.paint)
        }
        g.paint.style = Paint.Style.FILL
        // 右岸入水梯
        val lx = right - 24f * s
        g.paint.color = g.ca(0xFFE8ECF2.toInt(), 255, alpha)
        g.canvas.drawRect(lx, top - 8f * s, lx + 2.6f * s, top + 16f * s, g.paint)
        g.canvas.drawRect(lx + 10f * s, top - 8f * s, lx + 12.6f * s, top + 16f * s, g.paint)
        g.canvas.drawRect(lx, top - 5f * s, lx + 12.6f * s, top - 2.6f * s, g.paint)
        g.canvas.drawRect(lx, top + 3f * s, lx + 12.6f * s, top + 5.4f * s, g.paint)
        g.paint.isAntiAlias = aa
    }

    private fun drawTelescope(world: Int, cx: Float, gy: Float, s: Float, alpha: Int, g: Gfx, tx: Float, ty: Float) {
        val x = cx + tx * s; val fy = gy + ty * s; val rc = g.rc
        val topY = fy - 26f * s
        val aa = g.paint.isAntiAlias
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
        val tube = if (world == Game.UNI_WATER) 0xFF2E86BE.toInt() else 0xFF3E4A66.toInt()
        g.paint.color = g.withAlpha(tube, alpha)
        g.canvas.drawRect(x - 12f * s, topY - 8f * s, x + 32f * s, topY + 8f * s, g.paint)
        // 筒身高光 / 箍环 / 目镜
        g.paint.color = g.ca(0xFFFFFFFF.toInt(), 60, alpha)
        g.canvas.drawRect(x - 12f * s, topY - 8f * s, x + 32f * s, topY - 5f * s, g.paint)
        g.paint.color = g.ca(0xFF222B3E.toInt(), 255, alpha)
        g.canvas.drawRect(x + 8f * s, topY - 8f * s, x + 12f * s, topY + 8f * s, g.paint)
        g.paint.color = g.withAlpha(leg, alpha)
        g.canvas.drawRect(x - 17f * s, topY - 4f * s, x - 12f * s, topY + 4f * s, g.paint)
        // 物镜镜片 + 呼吸的镜面反光
        g.paint.color = g.withAlpha(0xFF57B6E8.toInt(), alpha)
        g.canvas.drawRect(x + 28f * s, topY - 6f * s, x + 32f * s, topY + 6f * s, g.paint)
        val glint = 0.5f + 0.5f * sin(g.homePhase * 2.2f)
        g.paint.color = g.ca(0xFFFFFFFF.toInt(), (70 + 130 * glint).toInt(), alpha)
        g.canvas.drawRect(x + 29f * s, topY - 4f * s, x + 31f * s, topY, g.paint)
        g.canvas.restore()
        // 镜筒所指方向闪烁的小星星
        val tw = 0.5f + 0.5f * sin(g.homePhase * 1.7f)
        val sxr = x + 44f * s; val syr = topY - 26f * s
        g.paint.color = g.ca(0xFFFFFFFF.toInt(), (60 + 170 * tw).toInt(), alpha)
        g.canvas.drawRect(sxr - 1.4f * s, syr - 4.5f * s, sxr + 1.4f * s, syr + 4.5f * s, g.paint)
        g.canvas.drawRect(sxr - 4.5f * s, syr - 1.4f * s, sxr + 4.5f * s, syr + 1.4f * s, g.paint)
        g.paint.isAntiAlias = aa
    }

    private val flagPath = Path()

    private fun drawFlags(cx: Float, gy: Float, half: Float, s: Float, alpha: Int, flags: IntArray, g: Gfx) {
        val aa = g.paint.isAntiAlias
        g.paint.isAntiAlias = true
        for (side in intArrayOf(-1, 1)) {
            // 挂旗的绳：沿旗点连成下垂的弧线
            g.paint.style = Paint.Style.STROKE
            g.paint.strokeWidth = 1.8f * s
            g.paint.color = g.ca(0xFFF4F0E6.toInt(), 200, alpha)
            var prevX = cx; var prevY = gy - 200f * s
            for (i in 0..5) {
                val t = (i + 1) / 6f
                val nx = cx + side * t * (half - 30f * s)
                val ny = gy - 200f * s + t * t * 130f * s
                g.canvas.drawLine(prevX, prevY, nx, ny, g.paint)
                prevX = nx; prevY = ny
            }
            g.paint.style = Paint.Style.FILL
            // 三角旗：旗尾随风摆，加一道受光亮边
            for (i in 0..4) {
                val t = (i + 1) / 6f
                val fx = cx + side * t * (half - 30f * s)
                val fy = gy - 200f * s + t * t * 130f * s
                val sway = sin(g.homePhase * 2.2f + i * 1.1f + (if (side < 0) 0.7f else 0f)) * 2.5f * s
                flagPath.reset()
                flagPath.moveTo(fx - 8f * s, fy)
                flagPath.lineTo(fx + 8f * s, fy)
                flagPath.lineTo(fx + sway, fy + 17f * s)
                flagPath.close()
                g.paint.color = g.withAlpha(flags[i], alpha)
                g.canvas.drawPath(flagPath, g.paint)
                g.paint.color = g.ca(0xFFFFFFFF.toInt(), 90, alpha)
                g.canvas.drawRect(fx - 8f * s, fy, fx + 8f * s, fy + 2.5f * s, g.paint)
            }
        }
        g.paint.isAntiAlias = aa
    }
}
