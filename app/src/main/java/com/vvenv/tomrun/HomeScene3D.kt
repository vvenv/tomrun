package com.vvenv.tomrun

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 家园的**体素 3D 造型**——和跑酷同一套语言：只有立方体、同一组光照、同一条雾。
 *
 * 之前的家是 Canvas 2D 画的一张立面图（`HomeWorldDraw`），跑酷是 3D，两边站在一起就是
 * 两款游戏。现在整个院子搬进 [GameRenderer] 的管线：同样的黄金时刻打光（暖直射 + 冷环境）、
 * 同样的距离雾、同样的护眼分级，猫也还是跑酷里那只猫（[CatPalette]）。
 *
 * **构图**（沿用重构时定下的三条纪律，见 memory: home-scene-redesign）：
 *  1. 配色只有 [HomePalette] 一个来源，这里不写颜色字面量，只从 ramp 取或派生。
 *  2. 层次靠静止的剪影：远景山脊 / 珊瑚脊 / 云堤压住地平线，氛围物每个世界最多两样。
 *  3. 只有「可交互且有新东西」才发光（藏馆的未读提示），其余安静。
 *
 * **新增的一条**：院子前面有一条马路，笔直伸向雾里——那就是跑酷的赛道。
 * 家和跑道是同一个世界的两端，「出门」这个动作因此有了去处。
 *
 * 坐标：y 向上，z 朝镜头为正；房屋在 -z 深处，镜头在 +z 俯视。
 */
object HomeScene3D {

    /** GL 侧的画笔，由 [GameRenderer] 实现——这个文件不碰 GLES，只描述形状 */
    interface Painter {
        /** 世界坐标独立方块（不受当前矩阵影响） */
        fun box(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, c: FloatArray)
        /** 当前矩阵下的方块 */
        fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, c: FloatArray)
        fun push(x: Float, y: Float, z: Float)
        fun pop()
        fun scale(sx: Float, sy: Float, sz: Float)
        fun rotY(deg: Float)
        fun rotX(deg: Float)
        fun rotZ(deg: Float)
        /** 0 常规光照 / 1 软阴影 / 2 无光照 / 3 纯自发光 */
        fun mode(m: Int)
        /** 接地软阴影（贴地圆片） */
        fun shadow(x: Float, z: Float, r: Float, y: Float = 0.02f)
    }

    // ---------- 姿势（HudView 的猫 AI 写、这里读） ----------
    const val POSE_STAND = 0
    const val POSE_WALK = 1
    const val POSE_SIT = 2
    const val POSE_NAP = 3
    const val POSE_SNIFF = 4
    const val POSE_LOOKUP = 5
    const val POSE_JUMP = 6
    const val POSE_SWIM = 7
    const val POSE_PET = 8

    // ---------- 房屋尺寸表：造型与热区共用同一份，热区永远贴着看到的形状 ----------
    private val BODY_W = floatArrayOf(6.0f, 7.4f, 7.0f, 6.6f)
    private val BODY_D = floatArrayOf(5.0f, 5.6f, 5.2f, 5.4f)
    private val BODY_H = floatArrayOf(3.0f, 3.4f, 6.0f, 6.2f)
    /** 含屋顶（城堡含塔）的总高，热区与藏馆避让都按它算 */
    private val TOTAL_H = floatArrayOf(5.0f, 5.6f, 7.9f, 9.8f)
    /** 城堡两侧塔楼让剪影更宽 */
    private val TOTAL_W = floatArrayOf(6.4f, 7.8f, 7.4f, 10.4f)
    private val DOOR_X = floatArrayOf(1.3f, -1.8f, 0f, 0f)
    private const val DOOR_W = 1.5f
    private const val DOOR_H = 2.3f

    fun houseHeight(style: Int) = TOTAL_H[style.coerceIn(0, 3)]

    // ---------- 取色 ----------
    /**
     * 颜色池：ARGB → 归一化 RGBA，顺带做昼夜调制。
     *
     * 每帧要出几百个方块，逐个 new FloatArray 会把 GC 压在渲染线程上；而 GL 侧
     * [GameRenderer.emit] 拿到数组后立刻分级并上传，用完即弃，所以循环复用是安全的。
     */
    private val bank = Array(64) { FloatArray(4) }
    private var bankIdx = 0
    private var dusk = 0f
    private var night = 0f

    private fun c(argb: Int, alpha: Float = 1f): FloatArray {
        val out = bank[bankIdx]
        bankIdx = (bankIdx + 1) and 63
        out[0] = ((argb ushr 16) and 0xFF) / 255f
        out[1] = ((argb ushr 8) and 0xFF) / 255f
        out[2] = (argb and 0xFF) / 255f
        DayNight.apply(out, dusk, night)
        out[3] = alpha
        return out
    }

    private fun shade(argb: Int, f: Float, alpha: Float = 1f) = c(HomePalette.shade(argb, f), alpha)
    private fun mix(a: Int, b: Int, t: Float, alpha: Float = 1f) = c(HomePalette.mix(a, b, t), alpha)

    // ---------- 入口 ----------

    /**
     * 画整个院子。
     *
     * @param phase 家页面的动画时钟（秒），炊烟 / 秋千 / 水波都吃它
     * @param ghostDeco 正在预览、尚未买下的摆件（半透明幽灵），-1 为无
     */
    fun draw(
        p: Painter, game: Game, phase: Float,
        house: Int, roof: Int, ghostDeco: Int,
        catColor: Int, catScarf: Int, catHat: Int
    ) {
        val world = game.homeWorld.coerceIn(0, Game.UNIVERSE_COUNT - 1)
        val pal = HomePalette.of(world)
        dusk = HomeYard.lightDusk.coerceAtLeast(0f)
        night = HomeYard.lightNight

        drawTerrain(p, world, pal, phase)
        drawHorizon(p, world, pal, phase)

        val hx = HomeYard.posX(game, HomeYard.HOUSE)
        val hz = HomeYard.posZ(game, HomeYard.HOUSE)
        if (game.homeElVisible(HomeYard.HOUSE)) {
            drawHouse(p, world, pal, house, roof, hx, hz, phase, LayoutConfig.cur.houseS)
            if (game.homeWorldsUnlockedCount() > 1) {
                val st = house.coerceIn(0, 3)
                val hs = LayoutConfig.cur.houseS
                drawDoorMat(p, pal, hx + DOOR_X[st] * hs, hz + BODY_D[st] * 0.5f * hs, phase)
            }
        }
        if (game.homeElVisible(HomeYard.MUSEUM)) {
            drawMuseum(
                p, game, world, pal,
                HomeYard.posX(game, HomeYard.MUSEUM), HomeYard.posZ(game, HomeYard.MUSEUM),
                phase, LayoutConfig.cur.museumS, game.homeElVisible(HomeYard.HONOR)
            )
        }

        for (deco in Game.DECO_NAMES.indices) {
            if (!HomeWorldContent.decoAvailable(world, deco)) continue
            val owned = game.ownsDeco(deco)
            if (!owned && deco != ghostDeco) continue
            val id = HomeYard.decoId(deco)
            if (id != null && !game.homeElVisible(id)) continue
            drawDeco(p, game, world, pal, deco, phase, if (owned) 1f else 0.45f, house, hx, hz)
        }

        if (game.homeElVisible(HomeYard.CAT)) {
            drawYardCat(p, game, phase, catColor, catScarf, catHat)
        }
        drawButterfly(p, pal, phase)
        drawHandMark(p, pal, phase)
        drawAmbient(p, world, pal, phase)
    }

    // ---------- 地面：草坪台地 + 门前小路 + 那条通向跑酷的马路 ----------
    private fun drawTerrain(p: Painter, world: Int, pal: HomePalette.Ramp, phase: Float) {
        p.mode(0)
        // 大地：铺到远远超出雾距的地方。地面盒子的**远边缘必须落在雾外**，
        // 否则那条边会在天上留下一道生硬的横线——雾最多吃掉 78%，剩下两成足够看见。
        p.box(0f, -0.6f, -320f, 900f, 1f, 900f, c(pal.ground[1]))
        // 远景地台：贴着地平线的一段浅色，把纵深拉开
        p.box(0f, -0.55f, -130f, 500f, 1f, 110f, c(HomePalette.lift(pal.far[0], world, 0.35f)))

        // 院子台地：抬高半格 + 一圈压深的边，房屋与摆件都站在它上面
        val cz = (HomeYard.FRONT_Z + HomeYard.BACK_Z) / 2f
        val dz = HomeYard.FRONT_Z - HomeYard.BACK_Z
        p.box(0f, HomeYard.DECK_Y * 0.5f - 0.08f, cz, HomeYard.HALF_X * 2f + 0.9f, HomeYard.DECK_Y, dz + 0.9f, c(pal.ground[2]))
        p.box(0f, HomeYard.DECK_Y * 0.5f + 0.04f, cz, HomeYard.HALF_X * 2f, HomeYard.DECK_Y, dz, c(pal.ground[0]))

        // 门前小路：从台地前缘一路铺到马路边，石板一块块错着摆
        val pathC = mix(pal.light, pal.body, 0.35f)
        val pathD = shade(HomePalette.mix(pal.light, pal.body, 0.35f), 0.82f)
        var pz = HomeYard.FRONT_Z - 1f
        var i = 0
        while (pz < 11.4f) {
            val w = if (i % 2 == 0) 2.6f else 2.2f
            p.box(0.25f * sin(i * 1.7f), HomeYard.DECK_Y * 0.5f, pz, w, HomeYard.DECK_Y + 0.06f, 1.5f, if (i % 2 == 0) pathC else pathD)
            pz += 1.9f
            i++
        }

        // 马路：家门口这条就是跑酷的赛道，横在院前伸进两边的雾里。
        // 「出门」不再是一个抽象按钮——路在那儿，人从这儿跑出去。
        val roadY = -0.06f
        val road = mix(pal.deep, pal.body, 0.30f)
        val shoulder = mix(pal.light, pal.body, 0.55f)
        p.box(0f, roadY, 14.2f, 320f, 0.9f, 8.2f, road)
        p.box(0f, roadY + 0.06f, 10.3f, 320f, 0.9f, 0.5f, shoulder)
        p.box(0f, roadY + 0.06f, 18.1f, 320f, 0.9f, 0.5f, shoulder)
        val dash = c(pal.hi)
        var dx = -60f
        while (dx < 60f) {
            p.box(dx, roadY + 0.5f, 14.2f, 2.4f, 0.06f, 0.22f, dash)
            dx += 6f
        }
    }

    // ---------- 远景剪影：每个世界一套形状语言 ----------
    /**
     * 远景剪影。
     *
     * 有一条铁律：**山头不能高过镜头**（相机在 y≈12），否则它就不是「远处的山」，
     * 而是压在院子后面的一堵墙——一屏的天空会被吃掉大半，主角房屋也失去了背景。
     * 所以这里的高度一律压在 11 以下、距离推到 110~170，只留一条低低的轮廓。
     */
    private fun drawHorizon(p: Painter, world: Int, pal: HomePalette.Ramp, phase: Float) {
        p.mode(0)
        val t = HomePalette.horizonLuma(world)
        fun far(argb: Int, k: Float = 1f) = c(HomePalette.lift(argb, world, (t * k).coerceIn(0f, 0.92f)))

        when (world) {
            Game.UNI_WATER -> {
                // 礁脊 + 一丛丛珊瑚
                for (k in 0 until 11) {
                    val x = -150f + k * 30f + (k % 3) * 8f
                    val h = 5f + (k % 4) * 2.6f
                    val z = -125f - (k % 3) * 16f
                    p.box(x, h * 0.4f, z, 30f, h, 22f, far(pal.body, 0.9f))
                    p.box(x + 6f, h * 0.8f, z, 12f, h * 0.5f, 13f, far(pal.light, 1.1f))
                }
                for (k in 0 until 6) {
                    val x = -52f + k * 21f
                    p.push(x, 0f, -60f - (k % 2) * 11f)
                    p.part(0f, 2.2f, 0f, 1.2f, 4.4f, 1.2f, far(pal.accent, 0.55f))
                    p.part(-1.4f, 3.4f, 0f, 1.0f, 2.4f, 1.0f, far(pal.accent, 0.6f))
                    p.part(1.5f, 4.0f, 0.4f, 0.9f, 2.0f, 0.9f, far(pal.accent2, 0.6f))
                    p.pop()
                }
            }
            Game.UNI_SKY -> {
                // 云堤：一层层横着堆，越远越淡
                for (k in 0 until 12) {
                    val x = -150f + k * 28f + (k % 4) * 7f
                    val y = 2.5f + (k % 3) * 2.4f
                    val z = -120f - (k % 4) * 18f
                    p.box(x, y, z, 34f, 5f, 20f, far(pal.body, 0.75f))
                    p.box(x + 7f, y + 3.4f, z, 20f, 4f, 15f, far(pal.light, 0.8f))
                }
            }
            Game.UNI_LAVA -> {
                // 火山锥：口沿一道亮橙，其余压成剪影
                for (k in 0 until 6) {
                    val x = -130f + k * 46f + (k % 2) * 13f
                    val h = 9f + (k % 3) * 3.5f
                    val z = -128f - (k % 3) * 18f
                    p.push(x, 0f, z)
                    p.part(0f, h * 0.25f, 0f, h * 1.9f, h * 0.5f, h * 1.2f, far(pal.deep, 0.7f))
                    p.part(0f, h * 0.66f, 0f, h * 1.0f, h * 0.35f, h * 0.7f, far(pal.body, 0.8f))
                    if (k % 2 == 0) {
                        p.mode(2)
                        p.part(0f, h * 0.86f, 0f, h * 0.42f, h * 0.06f, h * 0.3f, far(pal.accent, 0.25f))
                        p.mode(0)
                    }
                    p.pop()
                }
            }
            Game.UNI_CANDY -> {
                // 奶油丘：圆润的三段叠层
                for (k in 0 until 9) {
                    val x = -135f + k * 32f + (k % 3) * 9f
                    val h = 5.5f + (k % 3) * 2.4f
                    val z = -120f - (k % 3) * 16f
                    p.push(x, 0f, z)
                    p.part(0f, h * 0.3f, 0f, h * 2.4f, h * 0.6f, h * 1.6f, far(pal.body, 0.85f))
                    p.part(0f, h * 0.72f, 0f, h * 1.5f, h * 0.42f, h * 1.1f, far(pal.light, 0.95f))
                    p.part(0f, h * 0.98f, 0f, h * 0.7f, h * 0.3f, h * 0.6f, far(pal.hi, 1.1f))
                    p.pop()
                }
            }
            Game.UNI_SPACE -> {
                // 晶簇尖峰：细高、有疏密，夜色里只留轮廓
                for (k in 0 until 11) {
                    val x = -145f + k * 29f + (k % 3) * 10f
                    val h = 7f + (k % 4) * 3.5f
                    val z = -122f - (k % 3) * 18f
                    p.push(x, 0f, z)
                    p.rotZ(if (k % 2 == 0) 5f else -6f)
                    p.part(0f, h * 0.5f, 0f, 5.5f, h, 5.5f, far(pal.body, 0.8f))
                    p.part(0f, h * 0.98f, 0f, 2.6f, h * 0.3f, 2.6f, far(pal.accent, 0.45f))
                    p.pop()
                }
            }
            else -> {
                // 草原：起伏的丘陵 + 针叶林剪影
                for (k in 0 until 9) {
                    val x = -140f + k * 34f + (k % 3) * 9f
                    val h = 6f + (k % 4) * 2.5f
                    val z = -118f - (k % 3) * 18f
                    p.push(x, 0f, z)
                    p.part(0f, h * 0.32f, 0f, h * 5.0f, h * 0.65f, h * 2.2f, far(pal.organic, 0.85f))
                    p.part(0f, h * 0.72f, 0f, h * 2.8f, h * 0.4f, h * 1.4f, far(pal.organic, 0.7f))
                    p.pop()
                }
                for (k in 0 until 16) {
                    val side = if (k % 2 == 0) -1f else 1f
                    val x = side * (17f + (k * 13 % 52))
                    val z = -26f - (k * 17 % 62)
                    val h = 5f + (k % 3) * 2.4f
                    drawConifer(p, x, z, h, far(pal.organic, 0.3f), far(pal.deep, 0.25f))
                }
            }
        }
    }

    private fun drawConifer(p: Painter, x: Float, z: Float, h: Float, leaf: FloatArray, trunk: FloatArray) {
        p.push(x, 0f, z)
        p.part(0f, h * 0.16f, 0f, h * 0.16f, h * 0.32f, h * 0.16f, trunk)
        p.part(0f, h * 0.45f, 0f, h * 0.62f, h * 0.34f, h * 0.62f, leaf)
        p.part(0f, h * 0.72f, 0f, h * 0.44f, h * 0.3f, h * 0.44f, leaf)
        p.part(0f, h * 0.94f, 0f, h * 0.24f, h * 0.24f, h * 0.24f, leaf)
        p.pop()
    }

    // ---------- 房屋 ----------

    /** 屋顶：一层层收窄的板，从正面看就是那个熟悉的三角山墙 */
    private fun gableRoof(
        p: Painter, y: Float, halfW: Float, halfD: Float, h: Float,
        top: FloatArray, side: FloatArray, steps: Int = 5
    ) {
        for (s in 0 until steps) {
            val k = s / steps.toFloat()
            val hw = halfW * (1f - k * 0.86f)
            val yy = y + h * (s + 0.5f) / steps
            p.part(0f, yy, 0f, hw * 2f, h / steps, halfD * 2f, if (s == steps - 1) top else side)
        }
    }

    /** 四坡尖顶：塔楼 / 门廊用，两个方向一起收 */
    private fun pyramidRoof(
        p: Painter, y: Float, half: Float, h: Float, top: FloatArray, side: FloatArray, steps: Int = 4
    ) {
        for (s in 0 until steps) {
            val k = s / steps.toFloat()
            val hw = half * (1f - k * 0.9f)
            p.part(0f, y + h * (s + 0.5f) / steps, 0f, hw * 2f, h / steps, hw * 2f, if (s == 0) side else top)
        }
    }

    /** 圆顶：水下世界的房子不该有尖角 */
    private fun domeRoof(p: Painter, y: Float, half: Float, h: Float, top: FloatArray, side: FloatArray) {
        val steps = 5
        for (s in 0 until steps) {
            val k = (s + 0.5f) / steps
            val hw = half * cos(k * 1.35f)
            p.part(0f, y + h * k, 0f, hw * 2f, h / steps * 1.3f, hw * 2f, if (s < 2) side else top)
        }
    }

    private fun windowAt(p: Painter, x: Float, y: Float, z: Float, w: Float, h: Float, frame: FloatArray, glass: FloatArray) {
        p.part(x, y, z, w + 0.26f, h + 0.26f, 0.14f, frame)
        p.mode(2)
        p.part(x, y, z + 0.06f, w, h, 0.12f, glass)
        p.mode(0)
        p.part(x, y, z + 0.13f, w + 0.02f, 0.08f, 0.06f, frame)
        p.part(x, y, z + 0.13f, 0.08f, h + 0.02f, 0.06f, frame)
    }

    private fun doorAt(p: Painter, x: Float, z: Float, deep: FloatArray, lamp: FloatArray, trim: FloatArray) {
        p.part(x, DOOR_H * 0.5f, z, DOOR_W + 0.3f, DOOR_H + 0.2f, 0.16f, trim)
        p.part(x, DOOR_H * 0.5f, z + 0.1f, DOOR_W, DOOR_H, 0.16f, deep)
        // 门内透出来的一线暖光：明确探出门板，别和门板前脸同深度（会闪）
        p.mode(2)
        p.part(x, DOOR_H * 0.42f, z + 0.26f, DOOR_W * 0.55f, DOOR_H * 0.62f, 0.1f, lamp)
        p.mode(0)
        p.part(x + DOOR_W * 0.32f, DOOR_H * 0.5f, z + 0.34f, 0.14f, 0.14f, 0.14f, trim)
        // 门口台阶
        p.part(x, 0.12f, z + 0.9f, DOOR_W + 0.9f, 0.24f, 1.4f, trim)
    }

    /**
     * 门口地垫 + 一个指向门的箭头。
     *
     * 「点门 = 换一个世界的家」是这一屏里唯一说不出口的交互：门长得跟别的装饰一样。
     * 解锁了两个以上世界才画——只有一个家的时候门后面没别处可去，画了就是噪声。
     */
    private fun drawDoorMat(p: Painter, pal: HomePalette.Ramp, x: Float, z: Float, phase: Float) {
        p.push(x, HomeYard.DECK_Y, z + 2.1f)
        p.part(0f, 0.06f, 0f, 2.2f, 0.12f, 1.3f, c(pal.accent))
        p.part(0f, 0.13f, 0f, 1.8f, 0.06f, 1.0f, c(pal.hi, 0.75f))
        val bob = 0.12f + 0.12f * sin(phase * 2.2f)
        p.mode(3)
        for (k in 0 until 3) {
            val w = 0.8f - k * 0.22f
            p.part(0f, 1.5f + bob + k * 0.16f, 0.1f, w, 0.12f, 0.12f, c(pal.hi, 0.75f - k * 0.2f))
        }
        p.mode(0)
        p.pop()
    }

    /** 三团循环上升的烟：越高越淡越大 */
    private fun smoke(p: Painter, x: Float, y: Float, z: Float, phase: Float, tint: Int) {
        p.mode(2)
        for (i in 0 until 3) {
            val t = (phase * 0.28f + i * 0.34f) % 1f
            val s = 0.5f + t * 0.9f
            p.box(
                x + sin(phase * 1.2f + i * 2.1f) * 0.5f + t * 0.8f, y + t * 4.2f, z,
                s, s, s, c(tint, (1f - t) * 0.5f)
            )
        }
        p.mode(0)
    }

    private fun drawHouse(
        p: Painter, world: Int, pal: HomePalette.Ramp,
        style: Int, roof: Int, hx: Float, hz: Float, phase: Float, scale: Float
    ) {
        val st = style.coerceIn(0, 3)
        val hp = HomePalette.house(world, st)
        val body = c(hp[0]); val bodyD = c(hp[1]); val trim = c(hp[2])
        val roofArgb = HomePalette.ROOF_CHIPS[roof.coerceIn(0, HomePalette.ROOF_CHIPS.size - 1)]
        val roofC = c(roofArgb); val roofD = shade(roofArgb, 0.78f)
        val glass = c(pal.win)
        val frame = shade(hp[1], 0.9f)
        val deep = c(pal.deep)
        val w = BODY_W[st]; val d = BODY_D[st]; val bh = BODY_H[st]

        p.shadow(hx, hz + 0.4f, TOTAL_W[st] * 0.55f * scale)
        p.push(hx, HomeYard.DECK_Y, hz)
        p.scale(scale, scale, scale)
        p.mode(0)

        // 主体 + 墙脚
        p.part(0f, bh * 0.5f, 0f, w, bh, d, body)
        p.part(0f, 0.18f, 0f, w + 0.35f, 0.36f, d + 0.35f, bodyD)
        val fz = d * 0.5f

        when (st) {
            0 -> {
                // 小木屋：横向木板 + 烟囱炊烟
                for (r in 0 until 4) {
                    p.part(0f, 0.5f + r * 0.75f, fz + 0.01f, w - 0.2f, 0.1f, 0.06f, bodyD)
                }
                gableRoof(p, bh, w * 0.58f, d * 0.62f, 1.9f, roofC, roofD)
                p.part(w * 0.3f, bh + 1.5f, -d * 0.18f, 0.7f, 1.9f, 0.7f, bodyD)
                p.part(w * 0.3f, bh + 2.5f, -d * 0.18f, 0.9f, 0.24f, 0.9f, trim)
                smoke(p, hx + w * 0.3f, HomeYard.DECK_Y + bh + 3f, hz - d * 0.18f, phase, pal.hi)
                windowAt(p, -1.5f, bh * 0.62f, fz, 1.1f, 1.0f, frame, glass)
                doorAt(p, DOOR_X[st], fz, deep, c(pal.win), trim)
                // 窗下花箱
                p.part(-1.5f, bh * 0.62f - 0.75f, fz + 0.18f, 1.4f, 0.28f, 0.3f, bodyD)
                for (k in -1..1) {
                    p.part(-1.5f + k * 0.42f, bh * 0.62f - 0.5f, fz + 0.18f, 0.2f, 0.24f, 0.2f,
                        c(HomePalette.flowers(world)[(k + 1) % 3]))
                }
            }
            1 -> {
                // 砖瓦房：错缝砖纹 + 门口雨棚
                for (r in 0 until 5) for (col in 0 until 6) {
                    val bx = -w * 0.5f + 0.55f + col * 1.2f + if (r % 2 == 0) 0f else 0.6f
                    if (abs(bx - DOOR_X[st]) < 1.2f && r < 2) continue
                    p.part(bx, 0.55f + r * 0.62f, fz + 0.01f, 0.85f, 0.1f, 0.06f, bodyD)
                }
                gableRoof(p, bh, w * 0.57f, d * 0.62f, 2.1f, roofC, roofD)
                windowAt(p, 1.1f, bh * 0.6f, fz, 1.0f, 1.0f, frame, glass)
                windowAt(p, 2.9f, bh * 0.6f, fz, 1.0f, 1.0f, frame, glass)
                doorAt(p, DOOR_X[st], fz, deep, c(pal.win), trim)
                p.part(DOOR_X[st], DOOR_H + 0.35f, fz + 0.55f, 2.4f, 0.16f, 1.2f, roofC)
                p.part(DOOR_X[st], DOOR_H + 0.2f, fz + 1.05f, 2.2f, 0.14f, 0.16f, roofD)
            }
            2 -> {
                // 双层小楼：腰线 + 阳台 + 阁楼圆窗
                p.part(0f, bh * 0.5f, fz + 0.01f, w - 0.1f, 0.16f, 0.06f, bodyD)
                gableRoof(p, bh, w * 0.56f, d * 0.62f, 1.9f, roofC, roofD)
                windowAt(p, -2.1f, 1.9f, fz, 1.0f, 1.0f, frame, glass)
                windowAt(p, 2.1f, 1.9f, fz, 1.0f, 1.0f, frame, glass)
                windowAt(p, -2.1f, 4.6f, fz, 1.0f, 1.1f, frame, glass)
                windowAt(p, 2.1f, 4.6f, fz, 1.0f, 1.1f, frame, glass)
                doorAt(p, DOOR_X[st], fz, deep, c(pal.win), trim)
                // 阳台
                p.part(0f, 3.35f, fz + 0.6f, 3.6f, 0.16f, 1.2f, trim)
                for (k in -3..3) p.part(k * 0.5f, 3.7f, fz + 1.15f, 0.12f, 0.6f, 0.12f, trim)
                p.part(0f, 4.0f, fz + 1.15f, 3.6f, 0.14f, 0.16f, trim)
                p.mode(2)
                p.part(0f, bh + 1.1f, fz - 0.2f, 0.66f, 0.66f, 0.2f, glass)
                p.mode(0)
            }
            else -> {
                // 城堡：主楼 + 双塔 + 城垛 + 旗
                p.part(0f, bh + 0.22f, 0f, w + 0.5f, 0.44f, d + 0.5f, bodyD)
                for (k in -2..2) {
                    p.part(k * 1.4f, bh + 0.7f, 0f, 0.7f, 0.6f, d + 0.5f, body)
                }
                for (side in intArrayOf(-1, 1)) {
                    p.push(side * (w * 0.5f + 0.9f), 0f, 0.3f)
                    p.part(0f, 4.1f, 0f, 2.3f, 8.2f, 2.3f, body)
                    p.part(0f, 8.35f, 0f, 2.8f, 0.4f, 2.8f, bodyD)
                    pyramidRoof(p, 8.55f, 1.5f, 1.9f, roofC, roofD)
                    p.mode(2)
                    p.part(0f, 5.6f, 1.2f, 0.5f, 0.8f, 0.1f, glass)
                    p.mode(0)
                    // 塔尖小旗
                    p.part(0f, 10.9f, 0f, 0.1f, 1.4f, 0.1f, trim)
                    p.part(0.45f + sin(phase * 2f + side) * 0.05f, 11.3f, 0f, 0.8f, 0.45f, 0.06f, c(pal.accent))
                    p.pop()
                }
                doorAt(p, DOOR_X[st], fz, deep, c(pal.win), trim)
                p.part(DOOR_X[st], DOOR_H + 0.45f, fz + 0.02f, DOOR_W + 0.3f, 0.5f, 0.16f, trim)
                windowAt(p, -1.9f, 4.2f, fz, 0.8f, 1.2f, frame, glass)
                windowAt(p, 1.9f, 4.2f, fz, 0.8f, 1.2f, frame, glass)
            }
        }

        // 世界专属小装点：同一栋房子在六个世界里长出当地的东西
        when (world) {
            Game.UNI_WATER -> {
                domeRoof(p, bh + 0.1f, w * 0.42f, 1.6f, c(pal.light), c(pal.body))
                for (side in intArrayOf(-1, 1)) {
                    for (k in 0 until 3) {
                        val sway = sin(phase * 1.4f + k * 0.8f + side) * (0.1f + k * 0.12f)
                        p.part(side * (w * 0.5f + 0.7f) + sway, 0.4f + k * 0.7f, fz - 0.6f, 0.22f, 0.7f, 0.22f, c(pal.organic))
                    }
                }
            }
            Game.UNI_SKY -> for (k in 0 until 3) {
                val fx = (k - 1) * (w * 0.42f)
                p.part(fx, -0.35f + sin(phase * 0.9f + k) * 0.08f, 0.4f, 2.2f, 0.7f, 2.0f, c(pal.light, 0.85f))
            }
            Game.UNI_LAVA -> {
                p.mode(2)
                for (k in 0 until 3) {
                    p.part(-w * 0.4f + k * w * 0.4f, 0.14f, fz + 0.5f, 0.5f, 0.06f, 1.2f, c(pal.accent, 0.8f))
                }
                p.mode(0)
            }
            Game.UNI_CANDY -> for (k in 0 until 5) {
                p.part(-w * 0.4f + k * w * 0.2f, bh + 0.06f, fz - 0.1f, 0.5f, 0.5f, 0.5f, c(pal.hi))
            }
            Game.UNI_SPACE -> {
                p.part(w * 0.36f, bh + 1.4f, -d * 0.2f, 0.1f, 2.6f, 0.1f, c(pal.light))
                p.mode(3)
                p.part(w * 0.36f, bh + 2.8f, -d * 0.2f, 0.28f, 0.28f, 0.28f,
                    c(pal.accent, 0.6f + 0.4f * sin(phase * 3f)))
                p.mode(0)
            }
        }
        p.pop()
    }

    // ---------- 藏馆：藏品门廊 + 荣誉侧翼 ----------
    private fun drawMuseum(
        p: Painter, game: Game, world: Int, pal: HomePalette.Ramp,
        mx: Float, mz: Float, phase: Float, scale: Float, showHonor: Boolean
    ) {
        val t = HomePalette.horizonLuma(world) * 0.55f
        fun far(argb: Int, k: Float = 1f) = c(HomePalette.lift(argb, world, (t * k).coerceIn(0f, 0.8f)))
        val stoneArgb = HomePalette.mix(pal.light, pal.hi, 0.4f)
        val stone = far(stoneArgb)
        val stoneD = far(HomePalette.shade(stoneArgb, 0.8f), 0.9f)
        val stoneL = far(pal.hi, 1.2f)
        val inner = far(HomePalette.shade(pal.body, 0.62f), 0.75f)
        val cave = c(pal.deep)

        val k = scale * MUSEUM_K
        p.shadow(mx, mz + 0.6f, 6.5f * k)
        p.push(mx, HomeYard.DECK_Y * 0.4f, mz)
        p.scale(k, k, k)
        p.rotY(-18f)     // 侧过身朝向院子，剪影更立体，也不跟主角房屋抢正面
        p.mode(0)

        // 台基
        p.part(0f, 0.22f, 0f, 13.5f, 0.45f, 8.4f, stoneD)
        p.part(0f, 0.55f, 0f, 12.6f, 0.3f, 7.6f, stone)

        // 右半：荣誉侧翼墙（先画，门廊压在前面，接缝自然）
        p.push(3.6f, 0.7f, 0f)
        p.part(0f, 1.8f, 0f, 5.6f, 3.6f, 5.2f, inner)
        p.part(0f, 3.75f, 0f, 6.1f, 0.36f, 5.7f, stone)
        p.part(0f, 3.95f, 0f, 6.1f, 0.14f, 5.7f, stoneL)
        // 铜 / 银 / 金 / 钻四枚等级章：任一类别摸到该档就点亮
        val tierOn = BooleanArray(Game.ACHIEVE_TIERS_PER)
        for (cat in 0 until Game.ACHIEVE_CATS) {
            val lv = game.achieveLevels[cat].coerceIn(0, Game.ACHIEVE_TIERS_PER)
            for (k in 0 until lv) tierOn[k] = true
        }
        for (k in 0 until Game.ACHIEVE_TIERS_PER) {
            val bx = -2.0f + k * 1.34f
            p.part(bx, 2.0f, 2.62f, 1.05f, 1.05f, 0.14f, far(HomePalette.shade(pal.body, 0.46f), 0.6f))
            if (!showHonor) continue
            val on = tierOn[k]
            p.part(bx, 2.0f, 2.74f, 0.86f, 0.86f, 0.16f, c(if (on) MEDAL_FACE[k] else MEDAL_OFF))
            p.mode(if (on) 2 else 0)
            p.part(bx, 2.0f, 2.92f, 0.44f, 0.44f, 0.12f, c(if (on) MEDAL_GLOW[k] else MEDAL_OFF_IN))
            p.mode(0)
        }
        p.pop()

        // 左半：藏品门廊（三角山墙 + 立柱 + 透着暖光的门洞）
        p.push(-3.4f, 0.7f, 0f)
        p.part(0f, 2.6f, -1.2f, 6.4f, 5.2f, 3.2f, inner)
        p.part(0f, 1.5f, 0.4f, 1.8f, 3.0f, 0.6f, cave)
        // 门内的暖光要**明确地探出门洞前脸**：贴着同一个深度画会 z-fighting，
        // 远景 + 镜头微微漂移下就是「门在飞快地闪」——用户报的正是这个。
        p.mode(2)
        p.part(0f, 1.35f, 0.86f, 1.2f, 2.4f, 0.2f, c(pal.win))
        p.mode(0)
        for (colX in floatArrayOf(-2.5f, 2.5f)) {
            p.part(colX, 2.6f, 2.2f, 0.72f, 5.2f, 0.72f, stone)
            p.part(colX, 5.35f, 2.2f, 1.0f, 0.4f, 1.0f, stoneL)
            p.part(colX, 0.2f, 2.2f, 1.0f, 0.4f, 1.0f, stoneL)
        }
        p.part(0f, 5.75f, 1.0f, 6.8f, 0.6f, 3.2f, stone)
        p.part(0f, 6.0f, 1.0f, 6.8f, 0.16f, 3.2f, stoneL)
        gableRoof(p, 6.05f, 3.2f, 1.7f, 1.5f, stoneL, stone, 4)
        p.pop()

        // 只有还没翻开看过的新解锁才发光——平时整栋是安静的
        // 呼吸光是「这里有没看过的新东西」的提示：慢、稳、贴在建筑前方，
        // 插进墙体的话会与墙面互相抢深度，读起来是刺眼的高频闪
        if (game.hasUnseenRelics()) {
            val pulse = 0.5f + 0.5f * sin(phase * 1.1f)
            p.mode(3)
            p.part(-3.4f, 1.8f, 1.5f, 3.0f, 3.0f, 0.3f, c(pal.win, 0.08f + 0.12f * pulse))
            p.mode(0)
        }
        if (showHonor && game.hasUnseenHonors()) {
            val pulse = 0.5f + 0.5f * sin(phase * 1.1f + 1.2f)
            p.mode(3)
            p.part(3.6f, 2.0f, 3.15f, 6.0f, 2.0f, 0.3f, c(pal.accent, 0.08f + 0.12f * pulse))
            p.mode(0)
        }
        p.pop()
    }

    /**
     * 藏馆的整体尺度。
     *
     * 它是**远景**地标：竖屏水平视野只有 ±19°，一栋按原尺寸摆在房屋右后方的建筑会直接
     * 顶出画外。压到 0.72 之后，它的剪影正好落在房屋右缘与画面右缘之间那条缝里——
     * 「退后一步、让主角站出来」这件事靠的是位置和尺寸，不是把它画淡。
     */
    private const val MUSEUM_K = 0.72f

    /** 奖章档位色是语义色（金牌就该是金的），不随世界换 */
    private val MEDAL_FACE = intArrayOf(0xFFC87A3A.toInt(), 0xFFAEB6C4.toInt(), 0xFFF2C14E.toInt(), 0xFF6FD3E0.toInt())
    private val MEDAL_GLOW = intArrayOf(0xFFE8AE74.toInt(), 0xFFDCE3EC.toInt(), 0xFFFFEDB0.toInt(), 0xFFCBF3F9.toInt())
    private const val MEDAL_OFF = 0xFF6B5B4B.toInt()
    private const val MEDAL_OFF_IN = 0xFF564A3E.toInt()

    // ---------- 庭院摆件 ----------
    private fun drawDeco(
        p: Painter, game: Game, world: Int, pal: HomePalette.Ramp,
        deco: Int, phase: Float, alpha: Float, house: Int, hx: Float, hz: Float
    ) {
        val id = HomeYard.decoId(deco)
        val x = if (id != null) HomeYard.posX(game, id) else 0f
        val z = if (id != null) HomeYard.posZ(game, id) else 0f
        val y = HomeYard.DECK_Y
        p.mode(0)
        when (deco) {
            0 -> {  // 花坛
                val flowers = HomePalette.flowers(world)
                p.shadow(x, z, 1.5f, y + 0.02f)
                p.push(x, y, z)
                p.part(0f, 0.28f, 0f, 3.0f, 0.56f, 1.8f, c(pal.body, alpha))
                p.part(0f, 0.5f, 0f, 2.6f, 0.2f, 1.4f, c(pal.deep, alpha))
                for (k in 0 until 5) {
                    val fx = -1.0f + k * 0.5f
                    val fz = if (k % 2 == 0) -0.25f else 0.25f
                    val sway = sin(phase * 1.3f + k) * 0.06f
                    p.part(fx + sway, 0.85f, fz, 0.09f, 0.5f, 0.09f, c(pal.organic, alpha))
                    p.part(fx + sway * 1.6f, 1.16f, fz, 0.34f, 0.3f, 0.34f, c(flowers[k % 3], alpha))
                    p.part(fx + sway * 1.6f, 1.24f, fz, 0.14f, 0.16f, 0.14f, c(pal.hi, alpha))
                }
                p.pop()
            }
            1 -> drawFence(p, game, pal, z, alpha)
            2 -> {  // 信箱
                p.shadow(x, z, 0.7f, y + 0.02f)
                p.push(x, y, z)
                p.part(0f, 0.7f, 0f, 0.22f, 1.4f, 0.22f, c(pal.body, alpha))
                p.part(0f, 1.55f, 0f, 1.1f, 0.6f, 0.8f, c(pal.accent, alpha))
                p.part(0f, 1.85f, 0f, 1.0f, 0.2f, 0.7f, c(pal.light, alpha))
                p.part(0.6f, 1.75f, 0f, 0.1f, 0.5f, 0.28f, c(pal.accent2, alpha))
                p.pop()
            }
            3 -> {  // 秋千
                p.shadow(x, z, 1.6f, y + 0.02f)
                p.push(x, y, z)
                for (side in intArrayOf(-1, 1)) {
                    p.push(side * 1.4f, 0f, 0f)
                    p.rotZ(side * 9f)
                    p.part(0f, 1.3f, -0.7f, 0.2f, 2.6f, 0.2f, c(pal.body, alpha))
                    p.part(0f, 1.3f, 0.7f, 0.2f, 2.6f, 0.2f, c(pal.body, alpha))
                    p.pop()
                }
                p.part(0f, 2.55f, 0f, 3.4f, 0.22f, 0.22f, c(pal.light, alpha))
                val ang = sin(phase * 1.6f) * 16f
                p.push(0f, 2.5f, 0f)
                p.rotX(ang)
                p.part(0f, -0.75f, 0f, 1.3f, 0.12f, 0.6f, c(pal.accent, alpha))
                p.part(-0.55f, -0.4f, 0f, 0.07f, 0.8f, 0.07f, c(pal.light, alpha))
                p.part(0.55f, -0.4f, 0f, 0.07f, 0.8f, 0.07f, c(pal.light, alpha))
                p.pop()
                p.pop()
            }
            4 -> {  // 猫爬架
                p.shadow(x, z, 1.1f, y + 0.02f)
                p.push(x, y, z)
                p.part(0f, 0.1f, 0f, 1.8f, 0.2f, 1.8f, c(pal.body, alpha))
                p.part(0f, 1.4f, 0f, 0.4f, 2.6f, 0.4f, c(pal.light, alpha))
                p.part(-0.45f, 1.25f, 0f, 1.5f, 0.2f, 1.4f, c(pal.accent, alpha))
                p.part(0.35f, 2.6f, 0f, 1.6f, 0.22f, 1.5f, c(pal.accent, alpha))
                val ball = sin(phase * 2.2f) * 0.25f
                p.part(-0.8f + ball, 1.9f, 0.2f, 0.28f, 0.28f, 0.28f, c(pal.accent2, alpha))
                p.pop()
            }
            5 -> {  // 泳池：嵌进台地的一池水，池沿是围着水的一圈，不是盖在水上的一块板
                p.push(x, y, z)
                p.part(0f, -0.3f, 0f, 4.8f, 0.6f, 3.2f, c(pal.deep, alpha))          // 池底
                p.mode(2)
                p.part(0f, 0.0f, 0f, 4.7f, 0.12f, 3.1f, c(pal.water[0], alpha * 0.95f))  // 水面
                for (k in 0 until 3) {                                                    // 水纹
                    val rw = 1.3f + sin(phase * 1.1f + k * 2f) * 0.5f
                    p.part(-1.2f + k * 1.2f, 0.07f, sin(phase * 0.7f + k) * 0.7f, rw, 0.05f, 0.22f,
                        c(pal.water[1], alpha * 0.6f))
                }
                p.mode(0)
                for (sx in floatArrayOf(-2.7f, 2.7f)) {
                    p.part(sx, 0.06f, 0f, 0.6f, 0.3f, 3.8f, c(pal.light, alpha))
                }
                for (sz in floatArrayOf(-1.9f, 1.9f)) {
                    p.part(0f, 0.06f, sz, 5.4f, 0.3f, 0.6f, c(pal.light, alpha))
                }
                p.pop()
            }
            6 -> {  // 望远镜
                p.shadow(x, z, 0.9f, y + 0.02f)
                p.push(x, y, z)
                for (k in 0 until 3) {
                    p.push(0f, 0f, 0f)
                    p.rotY(k * 120f)
                    p.rotZ(14f)
                    p.part(0f, 0.7f, 0f, 0.14f, 1.5f, 0.14f, c(pal.body, alpha))
                    p.pop()
                }
                p.push(0f, 1.5f, 0f)
                p.rotY(sin(phase * 0.3f) * 20f)
                p.rotX(-34f)
                p.part(0f, 0.1f, 0f, 0.5f, 0.5f, 2.2f, c(pal.light, alpha))
                p.part(0f, 0.1f, -1.2f, 0.62f, 0.62f, 0.4f, c(pal.accent, alpha))
                p.mode(2)
                p.part(0f, 0.1f, -1.42f, 0.4f, 0.4f, 0.06f, c(pal.hi, alpha))
                p.mode(0)
                p.pop()
                p.pop()
            }
            else -> {  // 彩旗：从房檐拉到院角的一串
                val flags = HomePalette.flags(world)
                val ax = hx - BODY_W[house.coerceIn(0, 3)] * 0.5f
                val ay = HomeYard.DECK_Y + BODY_H[house.coerceIn(0, 3)] + 0.6f
                val az = hz + BODY_D[house.coerceIn(0, 3)] * 0.5f
                val bx = -HomeYard.HALF_X + 1.5f
                val by = HomeYard.DECK_Y + 2.6f
                val bz = HomeYard.FRONT_Z - 2f
                p.part(bx, HomeYard.DECK_Y + 1.3f, bz, 0.16f, 2.6f, 0.16f, c(pal.body, alpha))
                for (k in 0 until 9) {
                    val t = (k + 0.5f) / 9f
                    val sag = sin(t * Math.PI.toFloat()) * 0.9f
                    p.part(
                        ax + (bx - ax) * t, ay + (by - ay) * t - sag + sin(phase * 2f + k) * 0.05f,
                        az + (bz - az) * t, 0.42f, 0.42f, 0.06f, c(flags[k % flags.size], alpha)
                    )
                }
            }
        }
    }

    /** 栅栏：围着台地三面，正中让开门前那条路 */
    private fun drawFence(p: Painter, game: Game, pal: HomePalette.Ramp, frontZ: Float, alpha: Float) {
        val f = HomePalette.fence(game.homeWorld.coerceIn(0, Game.UNIVERSE_COUNT - 1))
        val post = c(f[0], alpha)
        val rail = c(f[1], alpha)
        val y = HomeYard.DECK_Y
        val z = HomeYard.FRONT_Z + (frontZ - HomeYard.anchorZ(HomeYard.FENCE))
        fun seg(x0: Float, z0: Float, x1: Float, z1: Float) {
            val dx = x1 - x0; val dz = z1 - z0
            val len = kotlin.math.hypot(dx, dz)
            val n = (len / 1.6f).toInt().coerceAtLeast(1)
            for (k in 0..n) {
                val t = k / n.toFloat()
                p.part(x0 + dx * t, y + 0.55f, z0 + dz * t, 0.2f, 1.1f, 0.2f, post)
            }
            p.push(x0 + dx * 0.5f, y + 0.85f, z0 + dz * 0.5f)
            p.rotY(Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat())
            p.part(0f, 0f, 0f, 0.1f, 0.16f, len, rail)
            p.part(0f, -0.38f, 0f, 0.1f, 0.16f, len, rail)
            p.pop()
        }
        val hx = HomeYard.HALF_X - 0.3f
        seg(-hx, z, -2.2f, z)
        seg(2.2f, z, hx, z)
        seg(-hx, z, -hx, HomeYard.BACK_Z + 2f)
        seg(hx, z, hx, HomeYard.BACK_Z + 2f)
    }

    // ---------- 庭院猫 ----------
    fun drawYardCat(p: Painter, game: Game, phase: Float, colorIdx: Int, scarf: Int, hat: Int) {
        val pose = HomeYard.catPose
        val cm = CatPalette.MAIN[colorIdx % CatPalette.MAIN.size]
        val cd = CatPalette.DARK[colorIdx % CatPalette.DARK.size]
        val k = CatPalette.SCALE * LayoutConfig.cur.catS
        val walk = phase * 7f

        if (pose != POSE_JUMP && pose != POSE_SWIM) {
            p.shadow(HomeYard.catX, HomeYard.catZ, 0.85f * k, HomeYard.DECK_Y + 0.02f)
        }
        p.push(HomeYard.catX, HomeYard.DECK_Y + HomeYard.catY, HomeYard.catZ)
        p.rotY(HomeYard.catYaw)
        p.mode(0)
        // 统一体型：与跑酷那只同一档，家里的猫不能忽大忽小
        p.push(0f, 0f, 0f)
        val squash = when (pose) {
            POSE_NAP -> 0.55f
            POSE_PET -> 0.82f
            POSE_SIT -> 0.95f
            else -> 1f
        }

        fun part(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: FloatArray) =
            p.part(x * k, y * k * squash, z * k, sx * k, sy * k * squash, sz * k, col)

        val lift = when (pose) {
            POSE_NAP -> -0.18f
            POSE_SIT -> -0.05f
            else -> 0f
        }
        val bob = if (pose == POSE_WALK) abs(sin(walk)) * 0.06f else 0f

        // 尾巴
        val wagAmt = when (pose) {
            POSE_PET -> 0.3f
            POSE_SIT -> 0.22f
            else -> 0.12f
        }
        val wag = sin(phase * (if (pose == POSE_PET) 5f else 1.6f)) * wagAmt
        part(wag, 0.95f + lift + bob, 0.72f, 0.22f, 0.22f, 0.28f, cm)
        part(wag * 1.6f, 1.25f + lift + bob, 0.85f, 0.20f, 0.28f, 0.20f, cm)
        part(wag * 2.2f, 1.55f + lift + bob, 0.95f, 0.22f, 0.26f, 0.22f, cd)

        // 四条腿
        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val lx = if (left) -0.38f else 0.38f
            val lz = if (front) -0.42f else 0.42f
            val phaseOff = walk + if (i == 0 || i == 3) 0f else Math.PI.toFloat()
            val swing = when (pose) {
                POSE_WALK -> sin(phaseOff) * 30f
                POSE_JUMP -> if (front) -38f else 32f
                POSE_SWIM -> sin(walk * 1.6f + i) * 45f
                POSE_SIT, POSE_NAP, POSE_PET -> if (front) 0f else 72f
                else -> 0f
            }
            p.push(lx * k, (0.52f + lift + bob) * k * squash, lz * k)
            p.rotX(swing)
            p.part(0f, -0.22f * k * squash, 0f, 0.26f * k, 0.48f * k * squash, 0.26f * k, if (left) cm else cd)
            p.part(0f, -0.50f * k * squash, 0.02f * k, 0.28f * k, 0.14f * k * squash, 0.30f * k, CatPalette.WHITE)
            p.pop()
        }

        // 身体
        part(0f, 0.72f + lift + bob, 0f, 1.05f, 0.72f, 1.45f, cm)
        part(0f, 0.48f + lift + bob, -0.05f, 0.78f, 0.36f, 1.1f, CatPalette.WHITE)
        if (scarf > 0) {
            val sc = CatPalette.SCARF[scarf % CatPalette.SCARF.size]
            part(0f, 1.12f + lift + bob, -0.35f, 1.05f, 0.20f, 0.34f, sc)
            part(0.22f, 1.06f + lift + bob, 0.28f, 0.24f, 0.16f, 0.55f, sc)
        }

        // 头
        val headNod = when (pose) {
            POSE_SNIFF -> 34f
            POSE_LOOKUP -> -30f
            POSE_NAP -> 18f
            POSE_PET -> 10f
            POSE_WALK -> sin(walk * 2f) * 3f
            else -> sin(phase * 0.9f) * 2f
        }
        val headYaw = if (pose == POSE_STAND) sin(phase * 0.5f) * 12f else 0f
        p.push(0f, (1.48f + lift + bob) * k * squash, -0.55f * k)
        p.rotX(headNod)
        p.rotY(headYaw)
        fun hp(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: FloatArray) =
            p.part(x * k, y * k, z * k, sx * k, sy * k, sz * k, col)
        hp(0f, 0f, 0f, 0.92f, 0.78f, 0.88f, cm)
        hp(-0.40f, -0.06f, -0.15f, 0.20f, 0.30f, 0.36f, CatPalette.WHITE)
        hp(0.40f, -0.06f, -0.15f, 0.20f, 0.30f, 0.36f, CatPalette.WHITE)
        hp(0f, -0.16f, -0.42f, 0.46f, 0.32f, 0.16f, CatPalette.WHITE)
        hp(0f, -0.10f, -0.50f, 0.14f, 0.10f, 0.08f, CatPalette.NOSE)
        val eyeH = if (pose == POSE_NAP || pose == POSE_PET) 0.06f else 0.18f
        hp(-0.22f, 0.08f, -0.40f, 0.18f, eyeH, 0.08f, CatPalette.EYE)
        hp(0.22f, 0.08f, -0.40f, 0.18f, eyeH, 0.08f, CatPalette.EYE)
        if (eyeH > 0.1f) {
            hp(-0.18f, 0.12f, -0.44f, 0.06f, 0.06f, 0.04f, CatPalette.EYE_HL)
            hp(0.26f, 0.12f, -0.44f, 0.06f, 0.06f, 0.04f, CatPalette.EYE_HL)
        }
        // 耳朵：被撸的时候往后压，这是「舒服」的信号
        val earBack = if (pose == POSE_PET) 26f else 0f
        val earWiggle = if (pose == POSE_WALK) sin(walk * 2.4f) * 5f else 0f
        for (side in intArrayOf(-1, 1)) {
            p.push(side * 0.32f * k, 0.42f * k, 0.05f * k)
            p.rotZ(side * (18f + earWiggle))
            p.rotX(earBack)
            p.part(0f, 0.16f * k, 0f, 0.28f * k, 0.42f * k, 0.18f * k, cd)
            p.part(0f, 0.12f * k, -0.05f * k, 0.14f * k, 0.26f * k, 0.08f * k, CatPalette.PINK)
            p.pop()
        }
        if (hat > 0) {
            when (hat) {
                1 -> {
                    hp(0f, 0.47f, 0.05f, 0.82f, 0.26f, 0.75f, CatPalette.HAT_RED)
                    hp(0f, 0.40f, -0.55f, 0.66f, 0.09f, 0.5f, CatPalette.HAT_RED)
                    hp(0f, 0.63f, 0.05f, 0.16f, 0.1f, 0.16f, CatPalette.HAT_RED_DK)
                }
                2 -> {
                    hp(0f, 0.42f, 0f, 1.35f, 0.08f, 1.3f, CatPalette.STRAW)
                    hp(0f, 0.56f, 0f, 0.72f, 0.24f, 0.7f, CatPalette.STRAW)
                    hp(0f, 0.49f, 0f, 0.76f, 0.07f, 0.74f, CatPalette.STRAW_BAND)
                }
                else -> {
                    hp(0f, 0.50f, 0f, 0.66f, 0.16f, 0.64f, CatPalette.CROWN_GOLD)
                    for (i in -1..1) hp(i * 0.22f, 0.64f, 0f, 0.12f, 0.14f, 0.12f, CatPalette.CROWN_GOLD)
                    hp(0f, 0.52f, -0.34f, 0.12f, 0.12f, 0.06f, CatPalette.CROWN_RUBY)
                }
            }
        }
        p.pop()   // 头
        p.pop()   // 体型
        p.pop()   // 位置
    }

    // ---------- 蝴蝶 / 手势标记 / 氛围 ----------
    private fun drawButterfly(p: Painter, pal: HomePalette.Ramp, phase: Float) {
        if (!HomeYard.butterflyOn) return
        val wing = if (sin(phase * 12f) > 0f) 0.42f else 0.2f
        p.mode(2)
        p.push(HomeYard.butterflyX, HomeYard.butterflyY, HomeYard.butterflyZ)
        p.rotY(sin(phase * 1.5f) * 30f)
        p.part(-0.2f, 0f, 0f, wing, 0.34f, 0.1f, c(pal.accent2))
        p.part(0.2f, 0f, 0f, wing, 0.34f, 0.1f, c(pal.accent2))
        p.part(0f, 0f, 0f, 0.1f, 0.28f, 0.16f, c(pal.accent))
        p.pop()
        p.mode(0)
    }

    private fun drawHandMark(p: Painter, pal: HomePalette.Ramp, phase: Float) {
        val life = HomeYard.handLife
        if (life <= 0f) return
        val a = (life / 2.5f).coerceIn(0f, 1f)
        val r = 0.9f + 0.35f * sin(phase * 6f)
        p.mode(3)
        p.box(HomeYard.handX, HomeYard.DECK_Y + 0.06f, HomeYard.handZ, r * 2f, 0.05f, r * 2f, c(pal.hi, a * 0.5f))
        p.box(HomeYard.handX, HomeYard.DECK_Y + 0.4f + 0.2f * sin(phase * 5f), HomeYard.handZ, 0.22f, 0.5f, 0.22f, c(pal.accent, a))
        p.mode(0)
    }

    /** 每个世界最多两样氛围物、一个招牌动效——层次靠远景剪影，不靠数量 */
    private fun drawAmbient(p: Painter, world: Int, pal: HomePalette.Ramp, phase: Float) {
        p.mode(3)
        when (world) {
            Game.UNI_WATER -> for (k in 0 until 7) {
                val t = (phase * 0.25f + k * 0.14f) % 1f
                p.box(-9f + k * 3.1f + sin(phase + k) * 0.6f, 0.5f + t * 11f, 2f - (k % 3) * 4f,
                    0.28f, 0.28f, 0.28f, c(pal.hi, (1f - t) * 0.5f))
            }
            Game.UNI_LAVA -> for (k in 0 until 8) {
                val t = (phase * 0.4f + k * 0.12f) % 1f
                p.box(-12f + k * 3.2f + sin(phase * 1.6f + k) * 1.1f, 0.4f + t * 9f, -2f + (k % 4) * 3f,
                    0.2f, 0.2f, 0.2f, c(pal.accent, (1f - t) * 0.8f))
            }
            Game.UNI_CANDY -> for (k in 0 until 7) {
                val t = (phase * 0.18f + k * 0.15f) % 1f
                p.box(-11f + k * 3.4f + sin(phase * 0.8f + k) * 1.6f, 9f - t * 8.4f, 1f - (k % 3) * 3f,
                    0.26f, 0.26f, 0.26f, c(if (k % 2 == 0) pal.accent2 else pal.hi, 0.75f))
            }
            Game.UNI_SPACE -> for (k in 0 until 10) {
                val tw = 0.5f + 0.5f * sin(phase * 2.4f + k)
                p.box(-14f + k * 3.1f, 2.5f + (k * 7 % 9), -8f - (k % 3) * 5f,
                    0.16f, 0.16f, 0.16f, c(pal.accent, 0.35f + 0.45f * tw))
            }
            Game.UNI_SKY -> for (k in 0 until 4) {
                val x = ((phase * 1.4f + k * 22f) % 90f) - 45f
                p.box(x, 11f + (k % 2) * 4f, -30f - k * 8f, 7f, 2f, 4f, c(pal.light, 0.55f))
            }
            else -> {
                // 草原：白天两朵慢云，入夜换成萤火——招牌动效只有一个
                if (night > 0.35f) {
                    for (k in 0 until 8) {
                        val fx = sin(phase * 0.6f + k * 1.7f) * 7f + (k % 4 - 1.5f) * 4f
                        val fy = 1.2f + abs(sin(phase * 0.9f + k)) * 1.6f
                        val fz = 3f + (k % 4) * 1.6f
                        p.box(fx, HomeYard.DECK_Y + fy, fz, 0.16f, 0.16f, 0.16f,
                            c(pal.accent, 0.35f + 0.55f * (0.5f + 0.5f * sin(phase * 3f + k))))
                    }
                } else {
                    for (k in 0 until 2) {
                        val x = ((phase * 1.1f + k * 40f) % 80f) - 40f
                        p.box(x, 13f + k * 3f, -34f - k * 10f, 8f, 2.2f, 4.5f, c(pal.hi, 0.55f))
                    }
                }
            }
        }
        p.mode(0)
    }

    // ---------- 热区包围盒：渲染层据此投影出屏幕矩形 ----------

    /** 填入 cx, cy, cz, hx, hy, hz（中心 + 半尺寸）；不该出现时返回 false */
    fun bounds(id: String, game: Game, house: Int, out: FloatArray): Boolean {
        val st = house.coerceIn(0, 3)
        val hx = HomeYard.posX(game, HomeYard.HOUSE)
        val hz = HomeYard.posZ(game, HomeYard.HOUSE)
        val hs = LayoutConfig.cur.houseS
        when (id) {
            HomeYard.HOUSE -> {
                if (!game.homeElVisible(HomeYard.HOUSE)) return false
                // 只裹住墙体：包围盒若含屋顶与塔尖，那一大片天空点下去会莫名弹出「翻修房子」
                set(out, hx, HomeYard.DECK_Y + BODY_H[st] * 0.5f * hs, hz,
                    BODY_W[st] * 0.55f * hs, BODY_H[st] * 0.5f * hs, BODY_D[st] * 0.5f * hs)
            }
            HomeYard.DOOR -> {
                if (!game.homeElVisible(HomeYard.HOUSE)) return false
                // 热区比门板本身宽一圈、并向前吃掉门口地垫那一块：门在远处只有十几个像素，
                // 手指偏一点就落到房子上，「点门换世界」就成了「翻修一下房子」。
                set(out, hx + DOOR_X[st] * hs, HomeYard.DECK_Y + DOOR_H * 0.55f * hs,
                    hz + (BODY_D[st] * 0.5f + 1.1f) * hs, DOOR_W * 1.0f * hs, DOOR_H * 0.75f * hs, 1.5f)
            }
            HomeYard.MUSEUM -> {
                if (!game.homeElVisible(HomeYard.MUSEUM)) return false
                val s = LayoutConfig.cur.museumS * MUSEUM_K
                set(out, HomeYard.posX(game, HomeYard.MUSEUM) - 3.3f * s, 3.2f * s,
                    HomeYard.posZ(game, HomeYard.MUSEUM) + 1.2f * s, 3.4f * s, 3.0f * s, 2.4f * s)
            }
            HomeYard.HONOR -> {
                if (!game.homeElVisible(HomeYard.MUSEUM) || !game.homeElVisible(HomeYard.HONOR)) return false
                val s = LayoutConfig.cur.museumS * MUSEUM_K
                // 只圈住四枚奖章那一条：藏馆整栋是「藏品」，奖章墙是「荣誉」，
                // 两个热区必须能分得开，靠的是这条比整栋小得多
                set(out, HomeYard.posX(game, HomeYard.MUSEUM) + 4.4f * s, 2.7f * s,
                    HomeYard.posZ(game, HomeYard.MUSEUM) + 2.2f * s, 2.8f * s, 1.3f * s, 1.0f * s)
            }
            HomeYard.CAT -> {
                if (!game.homeElVisible(HomeYard.CAT)) return false
                // 猫是全场唯一会走的目标，热区比模型再放宽一圈：手指按下去的那一刻它已经挪了半步
                val k = CatPalette.SCALE * LayoutConfig.cur.catS
                set(out, HomeYard.catX, HomeYard.DECK_Y + HomeYard.catY + 1.1f * k, HomeYard.catZ,
                    1.5f * k, 1.6f * k, 1.5f * k)
            }
            HomeYard.BUTTERFLY -> {
                if (!HomeYard.butterflyOn) return false
                set(out, HomeYard.butterflyX, HomeYard.butterflyY, HomeYard.butterflyZ, 0.8f, 0.8f, 0.8f)
            }
            HomeYard.FENCE -> {
                if (!game.ownsDeco(1) || !game.homeElVisible(HomeYard.FENCE)) return false
                val z = HomeYard.posZ(game, HomeYard.FENCE) + HomeYard.FRONT_Z - HomeYard.anchorZ(HomeYard.FENCE)
                set(out, 0f, HomeYard.DECK_Y + 0.6f, z, HomeYard.HALF_X, 0.9f, 0.7f)
            }
            HomeYard.GARDEN -> return decoBox(game, 0, id, 1.7f, 0.9f, 1.2f, out)
            HomeYard.MAILBOX -> return decoBox(game, 2, id, 0.8f, 1.2f, 0.7f, out)
            HomeYard.SWING -> return decoBox(game, 3, id, 1.9f, 1.5f, 1.1f, out)
            HomeYard.PERCH -> return decoBox(game, 4, id, 1.1f, 1.5f, 1.1f, out)
            HomeYard.POOL -> return decoBox(game, 5, id, 2.9f, 0.5f, 2.1f, out)
            HomeYard.TELESCOPE -> return decoBox(game, 6, id, 1.2f, 1.2f, 1.2f, out)
            else -> return false
        }
        return true
    }

    private fun decoBox(game: Game, deco: Int, id: String, hx: Float, hy: Float, hz: Float, out: FloatArray): Boolean {
        if (!game.ownsDeco(deco) || !game.homeElVisible(id)) return false
        set(out, HomeYard.posX(game, id), HomeYard.DECK_Y + hy, HomeYard.posZ(game, id), hx, hy, hz)
        return true
    }

    private fun set(out: FloatArray, cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float) {
        out[0] = cx; out[1] = cy; out[2] = cz; out[3] = hx; out[4] = hy; out[5] = hz
    }
}
