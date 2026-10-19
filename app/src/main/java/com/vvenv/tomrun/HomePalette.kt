package com.vvenv.tomrun

/**
 * 家园页各世界的**唯一配色来源**。
 *
 * 每个世界一条 [Ramp]：7 个材质角色 + 天空 / 地面 / 远景三段渐变。所有房屋、装饰、
 * 地标、氛围物的颜色都从这里取，不再各自硬编码——这是「整体设计感」的来源。
 *
 * 角色分工（光从上/暖，影在下/冷，与跑酷层一致）：
 *  - [Ramp.deep]    深影：接地阴影、暗面、缝隙
 *  - [Ramp.body]    主体：主要建材
 *  - [Ramp.light]   亮面：受光顶沿、台面
 *  - [Ramp.accent]  强调：本世界的签名色（灯光 / 花 / 能量）
 *  - [Ramp.accent2] 次强调：与 accent 成一组的第二色，只用在需要区分的小面积
 *  - [Ramp.hi]      高光：最亮点、窗内光、白点
 *  - [Ramp.organic] 生长物：茎叶 / 藤 / 焦枝
 *
 * 纪律：**一屏内 accent2 的面积不超过 accent 的三分之一**，高光只点不铺。
 */
object HomePalette {

    class Ramp(
        val deep: Int,
        val body: Int,
        val light: Int,
        val accent: Int,
        val accent2: Int,
        val hi: Int,
        val organic: Int,
        /** 天空渐变：天顶 → 中段 → 地平线 */
        val sky: IntArray,
        /** 近景地面：渐变上 / 渐变下 / 地平线压边 */
        val ground: IntArray,
        /** 远景地面：填色 / 压边 */
        val far: IntArray,
        /** 窗内灯光 */
        val win: Int,
        /** 池水渐变：深 / 浅 */
        val water: IntArray
    ) {
        /** 地平线色——远景做大气透视时往这个颜色靠 */
        val horizon get() = sky[2]
    }

    private val RAMPS = arrayOf(
        // 0 草原：暖色黄金时刻，木与麦
        Ramp(
            deep = 0xFF4A3A28.toInt(), body = 0xFFB07A45.toInt(), light = 0xFFE4C48E.toInt(),
            accent = 0xFFF2A33C.toInt(), accent2 = 0xFFE0574F.toInt(), hi = 0xFFFFF3D0.toInt(),
            organic = 0xFF6FAE4E.toInt(),
            sky = intArrayOf(0xFF6EB6E6.toInt(), 0xFF9AD1EF.toInt(), 0xFFF1E8C6.toInt()),
            ground = intArrayOf(0xFF8FC85F.toInt(), 0xFF77B94E.toInt(), 0xFF5FA845.toInt()),
            far = intArrayOf(0xFF97CE6E.toInt(), 0xFF74B052.toInt()),
            win = 0xFFFFE9A8.toInt(),
            water = intArrayOf(0xFF2E86BE.toInt(), 0xFF74C8EE.toInt())
        ),
        // 1 水下：礁青为主，珊瑚橙是唯一暖强调
        Ramp(
            deep = 0xFF0C3A4E.toInt(), body = 0xFF3E8C8A.toInt(), light = 0xFF7FC8BE.toInt(),
            accent = 0xFFE8836E.toInt(), accent2 = 0xFF6EE8D8.toInt(), hi = 0xFFCFF2FF.toInt(),
            organic = 0xFF2E8A6E.toInt(),
            sky = intArrayOf(0xFF07293F.toInt(), 0xFF0E4A66.toInt(), 0xFF1A6B84.toInt()),
            ground = intArrayOf(0xFFC9B678.toInt(), 0xFFAE9C5F.toInt(), 0xFF8D7B44.toInt()),
            far = intArrayOf(0xFFD5C68B.toInt(), 0xFFB3A167.toInt()),
            win = 0xFFBFF2E8.toInt(),
            water = intArrayOf(0xFF1E9E8E.toInt(), 0xFF5EDCC8.toInt())
        ),
        // 2 天空：云白 + 金，靠明度而非色相拉开层次。hi / 地面不到纯白，教室灯下才不晃。
        Ramp(
            deep = 0xFF8FA8C4.toInt(), body = 0xFFE0E8F0.toInt(), light = 0xFFE8F0F6.toInt(),
            accent = 0xFFF2C14E.toInt(), accent2 = 0xFF9FC8EE.toInt(), hi = 0xFFE8F2FA.toInt(),
            organic = 0xFF7AA86B.toInt(),
            sky = intArrayOf(0xFF4A8ED4.toInt(), 0xFF7AB6E6.toInt(), 0xFFD4E8F6.toInt()),
            ground = intArrayOf(0xFFD4E4F0.toInt(), 0xFFB6D0E4.toInt(), 0xFF96B8D0.toInt()),
            far = intArrayOf(0xFFE4EEF6.toInt(), 0xFFC8DCEC.toInt()),
            win = 0xFFFFE9A8.toInt(),
            water = intArrayOf(0xFF57B6E8.toInt(), 0xFFA8DCEC.toInt())
        ),
        // 3 熔岩：焦黑基底，只有裂缝在发光。台地边用焦土，不整圈铺橙。
        Ramp(
            deep = 0xFF241713.toInt(), body = 0xFF4E3730.toInt(), light = 0xFF7A5A4A.toInt(),
            accent = 0xFFFF7A2A.toInt(), accent2 = 0xFFE03A28.toInt(), hi = 0xFFFFD9A0.toInt(),
            organic = 0xFF6E4A3A.toInt(),
            sky = intArrayOf(0xFF2A0F0E.toInt(), 0xFF5A1E14.toInt(), 0xFF7A3818.toInt()),
            ground = intArrayOf(0xFF4A3B36.toInt(), 0xFF352A27.toInt(), 0xFF3E2C28.toInt()),
            far = intArrayOf(0xFF564540.toInt(), 0xFF3E312D.toInt()),
            win = 0xFFFFC48A.toInt(),
            water = intArrayOf(0xFFC85A1E.toInt(), 0xFFFF9A4A.toInt())
        ),
        // 4 糖果：粉收在小屋和花上，天空偏奶油，整屏才不「糖霜呛眼」
        Ramp(
            deep = 0xFF6B4230.toInt(), body = 0xFFF2A0BC.toInt(), light = 0xFFFFD9E8.toInt(),
            accent = 0xFFFFD54A.toInt(), accent2 = 0xFFFF8FBE.toInt(), hi = 0xFFFFF8F0.toInt(),
            organic = 0xFF7ADBC8.toInt(),
            sky = intArrayOf(0xFFE090B0.toInt(), 0xFFF0C8D4.toInt(), 0xFFF8EAD8.toInt()),
            ground = intArrayOf(0xFF98DE7A.toInt(), 0xFF7CC75F.toInt(), 0xFF6B4230.toInt()),
            far = intArrayOf(0xFFA8E68C.toInt(), 0xFF86CE69.toInt()),
            win = 0xFFFFE2F0.toInt(),
            water = intArrayOf(0xFFE85A8E.toInt(), 0xFFFFB8D4.toInt())
        ),
        // 5 星空：靛紫夜幕，霓虹青是唯一光源
        Ramp(
            deep = 0xFF1A1636.toInt(), body = 0xFF4A4470.toInt(), light = 0xFF8A86B8.toInt(),
            accent = 0xFF4DE8FF.toInt(), accent2 = 0xFFB48CFF.toInt(), hi = 0xFFEAF6FF.toInt(),
            organic = 0xFF3E8A8A.toInt(),
            sky = intArrayOf(0xFF060714.toInt(), 0xFF12142E.toInt(), 0xFF2A1F52.toInt()),
            ground = intArrayOf(0xFF2E2650.toInt(), 0xFF201A3C.toInt(), 0xFF7C5CD6.toInt()),
            far = intArrayOf(0xFF383060.toInt(), 0xFF272148.toInt()),
            win = 0xFF9FE8FF.toInt(),
            water = intArrayOf(0xFF3E2E8E.toInt(), 0xFF7C5CD6.toInt())
        )
    )

    fun of(world: Int): Ramp = RAMPS[world.coerceIn(0, RAMPS.size - 1)]

    /**
     * 屋顶四色：玩家买的是「屋顶」这件商品，颜色跟着商品走、不随世界换——
     * 它是这一屏里唯一由玩家指定的色相，所以放在 ramp 之外。
     */
    val ROOF_CHIPS = intArrayOf(
        0xFFD9483B.toInt(), 0xFF3FA9A5.toInt(), 0xFF8C6BD9.toInt(), 0xFFF2C14E.toInt()
    )

    // ---------- 取色工具 ----------

    /** 线性插值两色（忽略 alpha，输出不透明） */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = ((a ushr 16 and 0xFF) + ((b ushr 16 and 0xFF) - (a ushr 16 and 0xFF)) * k).toInt()
        val g = ((a ushr 8 and 0xFF) + ((b ushr 8 and 0xFF) - (a ushr 8 and 0xFF)) * k).toInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** f < 1 压暗，f > 1 提亮（提亮走向白，不会溢出成灰） */
    fun shade(c: Int, f: Float): Int =
        if (f <= 1f) {
            val r = ((c ushr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
            val g = ((c ushr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) * f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        } else mix(c, 0xFFFFFFFF.toInt(), (f - 1f).coerceIn(0f, 1f))

    fun alpha(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)

    /**
     * 大气透视：离得越远，颜色越往地平线靠、对比越低。
     * t=0 原色，t=1 完全融进地平线。
     */
    fun lift(c: Int, world: Int, t: Float): Int = mix(c, of(world).horizon, t)

    /**
     * 远景该褪多少——按地平线亮度给量。
     *
     * 亮天空（草原/天空/糖果）下，浅色远景本来就贴着天空，褪三成就够；暗天空（熔岩/星空）
     * 下同样的浅色石材会变成全场最亮的东西，把视线从主角房屋上抢走，所以要褪到一半以上。
     * 固定一个常数做不到这件事，这也是重构前远景地标在暗世界里格外扎眼的原因。
     */
    fun horizonLuma(world: Int): Float {
        val h = of(world).horizon
        val luma = (0.299f * (h ushr 16 and 0xFF) + 0.587f * (h ushr 8 and 0xFF) + 0.114f * (h and 0xFF)) / 255f
        // 亮地平线 → 0.32，暗地平线 → 0.58
        return 0.58f - 0.26f * luma.coerceIn(0f, 1f)
    }

    // ---------- 从 ramp 派生的成组配色 ----------

    /** 花坛三朵花：同一家族的两个强调色 + 一个过渡色，不再是三个不相干的色相 */
    fun flowers(world: Int): IntArray {
        val p = of(world)
        return intArrayOf(p.accent, p.accent2, mix(p.accent, p.hi, 0.45f))
    }

    /** 彩旗五色：沿 ramp 走一圈，读起来是一套而不是一把糖豆 */
    fun flags(world: Int): IntArray {
        val p = of(world)
        return intArrayOf(p.accent, p.hi, p.accent2, p.light, mix(p.accent, p.accent2, 0.5f))
    }

    /** 栅栏：立柱（亮面）/ 横杆（主体） */
    fun fence(world: Int): IntArray {
        val p = of(world)
        return intArrayOf(p.light, p.body)
    }

    /** 房屋材质 [style] = 主墙 / 暗部 / 点缀。四档房子共用 ramp，靠明度分级 */
    /**
     * 搭房子的材料色：主体 / 暗面 / 镶边。
     * 1..4 是孩子一眼认得的木 / 砖 / 灰泥 / 石，各自固定一个颜色，只往本世界的主色靠两成，
     * 所以在哪个世界都还是「红砖」，又不会跳出那个世界的调子；
     * 5..9 是五个宇宙的特产，颜色固定，搬到别的世界也认得出是从哪儿带回来的。
     */
    fun buildMaterial(world: Int, mat: Int): IntArray = when (mat) {
        in 1..4 -> {
            val base = BUILD_BASE[mat - 1]
            val body = mix(base, of(world).body, 0.18f)
            intArrayOf(body, shade(body, 0.78f), BUILD_TRIM[mat - 1])
        }
        5 -> intArrayOf(0xFFE98A7A.toInt(), 0xFFB85E55.toInt(), 0xFFFFD6C2.toInt())   // 珊瑚
        6 -> intArrayOf(0xFFEAF2FA.toInt(), 0xFFB9CBE0.toInt(), 0xFFFFFFFF.toInt())   // 云絮
        7 -> intArrayOf(0xFF3A2E40.toInt(), 0xFF241C2A.toInt(), 0xFFFF8A3D.toInt())   // 黑曜石（镶边是熔岩缝）
        8 -> intArrayOf(0xFFF7A8C8.toInt(), 0xFFD97BA4.toInt(), 0xFFB8F0D8.toInt())   // 糖块
        else -> intArrayOf(0xFF7FD8F0.toInt(), 0xFF4BA3C7.toInt(), 0xFFE6FBFF.toInt()) // 晶体
    }

    private val BUILD_BASE = intArrayOf(
        0xFFB07A45.toInt(),  // 木
        0xFFC0573F.toInt(),  // 砖
        0xFFE8DCC0.toInt(),  // 灰泥
        0xFF9C9A92.toInt()   // 石
    )
    private val BUILD_TRIM = intArrayOf(
        0xFFF2DDB0.toInt(), 0xFFF0E2C8.toInt(), 0xFF8A6A48.toInt(), 0xFFE6E2D6.toInt()
    )

    fun house(world: Int, style: Int): IntArray {
        val p = of(world)
        return when (style.coerceIn(0, 3)) {
            0 -> intArrayOf(p.body, shade(p.body, 0.82f), p.accent)
            1 -> intArrayOf(mix(p.body, p.accent2, 0.30f), shade(mix(p.body, p.accent2, 0.30f), 0.80f), p.accent)
            2 -> intArrayOf(p.light, shade(p.light, 0.84f), p.accent)
            else -> intArrayOf(mix(p.light, p.hi, 0.55f), shade(mix(p.light, p.hi, 0.55f), 0.86f), p.accent2)
        }
    }

    // ---------- 家页 HUD：从 ramp 派生木牌，不再用跑酷菜单的石板灰 ----------

    fun luma(c: Int): Float {
        val r = (c ushr 16 and 0xFF)
        val g = (c ushr 8 and 0xFF)
        val b = (c and 0xFF)
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** 铭牌 / 出门 / 钱包的底：压暗的建材，半透明让院子透一点 */
    fun hudFill(world: Int): Int {
        val p = of(world)
        return alpha(mix(shade(p.body, 0.72f), p.deep, 0.22f), 0xE8)
    }

    /** 木牌上的字：亮底用深墨，暗底用浅象牙 */
    fun hudInk(world: Int): Int {
        val p = of(world)
        val fill = mix(shade(p.body, 0.72f), p.deep, 0.22f)
        return if (luma(fill) > 0.48f) mix(p.deep, 0xFF1A1410.toInt(), 0.35f)
        else mix(p.hi, p.light, 0.28f)
    }

    fun hudMuted(world: Int): Int = alpha(hudInk(world), 0x99)

    fun hudAccent(world: Int): Int = of(world).accent

    fun hudEdge(world: Int): Int = alpha(of(world).light, 0x55)
}
