package com.vvenv.tomrun

import kotlin.math.abs
import kotlin.math.floor

/**
 * 亲手搭房子：Townscaper 式的「点哪儿长哪儿」。
 *
 * 玩家只决定**哪一格有一块**，长成什么样由邻居决定——顶上空着就长屋顶，
 * 旁边有更高的楼就变露台，底下悬空就长出拱廊立柱，一楼最靠前那块自动开门。
 * 怎么点都好看、不会失败，孩子的注意力全在「我想要它多高、往哪边伸」上。
 *
 * 这个文件只管数据和几何（格子、材料、蓝图、射线拾取），不碰 GLES 也不碰 Canvas：
 * 造型在 [HomeScene3D.drawBuild]，交互在 [HudView]。
 *
 * 格子坐标：x 0..8（4 居中）、z 0..6（6 最靠镜头）、y 0..6（0 贴地）。
 * 每个世界一份，存成 441 字节，每字节是材料号（0 = 空）。
 */
object HomeBuild {
    const val SX = 9
    const val SY = 7
    const val SZ = 7
    const val SIZE = SX * SY * SZ
    /** 一格的世界尺寸：两层楼约等于原来小木屋的墙高 */
    const val CELL = 1.2f

    // ---------- 材料 ----------
    /** 1..4 取本世界的房屋色（随世界换装）；5..9 是五个宇宙的特产，去过那个宇宙才能用 */
    const val MAT_COUNT = 9
    val MAT_NAMES = arrayOf("", "木", "砖", "灰泥", "石", "珊瑚", "云絮", "黑曜石", "糖块", "晶体")
    /** 特产材料对应的宇宙（[Game.UNI_WATER]..[Game.UNI_SPACE]），基础材料为 -1 */
    fun matUniverse(mat: Int): Int = if (mat in 5..9) mat - 4 else -1
    fun matCost(mat: Int): Int = if (mat in 5..9) 25 else 10
    /** 黑曜石的熔岩缝、晶体的内光：入夜也要亮着 */
    fun matGlows(mat: Int): Boolean = mat == 7 || mat == 9

    fun idx(x: Int, y: Int, z: Int) = (y * SZ + z) * SX + x
    fun inBounds(x: Int, y: Int, z: Int) = x in 0 until SX && y in 0 until SY && z in 0 until SZ

    fun at(g: ByteArray, x: Int, y: Int, z: Int): Int =
        if (inBounds(x, y, z)) g[idx(x, y, z)].toInt() else 0

    fun filled(g: ByteArray, x: Int, y: Int, z: Int) = at(g, x, y, z) != 0

    fun count(g: ByteArray): Int {
        var n = 0
        for (b in g) if (b.toInt() != 0) n++
        return n
    }

    fun value(g: ByteArray): Int {
        var v = 0
        for (b in g) if (b.toInt() != 0) v += matCost(b.toInt())
        return v
    }

    // ---------- 蓝图：原来的四档房型，现在只是起步的样子 ----------
    /**
     * 四档房屋改成四套起步蓝图。老玩家买过的房型直接换成对应蓝图——花过的钱看得见。
     * 新玩家从小木屋（12 块）开始往外长。
     */
    fun template(tier: Int): ByteArray {
        val g = ByteArray(SIZE)
        fun fill(x0: Int, x1: Int, z0: Int, z1: Int, y0: Int, y1: Int, mat: Int) {
            for (y in y0..y1) for (z in z0..z1) for (x in x0..x1) g[idx(x, y, z)] = mat.toByte()
        }
        when (tier.coerceIn(0, 3)) {
            0 -> fill(3, 5, 3, 4, 0, 1, 1)
            1 -> fill(2, 5, 2, 4, 0, 1, 2)
            2 -> fill(2, 6, 2, 4, 0, 2, 3)
            else -> {
                fill(2, 6, 2, 4, 0, 2, 4)
                fill(1, 1, 3, 3, 0, 4, 4)
                fill(7, 7, 3, 3, 0, 4, 4)
            }
        }
        return g
    }

    fun encode(g: ByteArray): String = android.util.Base64.encodeToString(g, android.util.Base64.NO_WRAP)

    fun decode(s: String?): ByteArray? {
        if (s.isNullOrEmpty()) return null
        return try {
            val g = android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
            if (g.size != SIZE) return null
            for (i in g.indices) if (g[i] !in 0..MAT_COUNT) g[i] = 0
            g
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    // ---------- 规则 ----------
    /** 新的一块只能贴地或贴着已有的块：房子从地上长出来，不会凭空漂着 */
    fun canPlace(g: ByteArray, x: Int, y: Int, z: Int): Boolean {
        if (!inBounds(x, y, z) || filled(g, x, y, z)) return false
        if (y == 0) return true
        return filled(g, x - 1, y, z) || filled(g, x + 1, y, z) ||
            filled(g, x, y - 1, z) || filled(g, x, y + 1, z) ||
            filled(g, x, y, z - 1) || filled(g, x, y, z + 1)
    }

    /**
     * 门开在哪一格：一楼、正面露在外面、最靠前，同样靠前取最居中的。
     * 返回 x + z * SX；没有一楼的块时返回 -1。
     */
    fun doorCell(g: ByteArray): Int {
        var best = -1
        var bestZ = -1
        var bestDx = Int.MAX_VALUE
        for (z in 0 until SZ) for (x in 0 until SX) {
            if (!filled(g, x, 0, z) || filled(g, x, 0, z + 1)) continue
            val dx = abs(x - SX / 2)
            if (z > bestZ || (z == bestZ && dx < bestDx)) {
                best = x + z * SX; bestZ = z; bestDx = dx
            }
        }
        return best
    }

    /** 墙体包围盒（格子坐标，含）：minX, maxX, maxY, minZ, maxZ；空房子返回 false */
    fun extents(g: ByteArray, out: IntArray): Boolean {
        var minX = SX; var maxX = -1; var maxY = -1; var minZ = SZ; var maxZ = -1
        for (y in 0 until SY) for (z in 0 until SZ) for (x in 0 until SX) {
            if (!filled(g, x, y, z)) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        if (maxX < 0) return false
        out[0] = minX; out[1] = maxX; out[2] = maxY; out[3] = minZ; out[4] = maxZ
        return true
    }

    // ---------- 造型：搭出来就认得，第一次认出来给奖励 ----------
    /**
     * 给「随便搭」一个方向：每种造型都对应一条孩子听得懂的提示，面板上永远挂着下一个没解锁的。
     * 规则全看格子本身，和 [HomeScene3D] 长出来的样子是同一套判断（顶上空着、旁边更高 = 露台……）。
     */
    val SHAPE_NAMES = arrayOf("小楼", "瞭望塔", "拱廊", "露台", "天井", "双塔", "混搭", "大宅")
    val SHAPE_HINTS = arrayOf(
        "把房子搭到 3 层",
        "把一根柱子单独搭到 5 层高",
        "从墙上往外伸出 3 块，下面空着",
        "在高楼旁边留出 3 块平顶",
        "用房子把一块空地四面围起来",
        "搭两座分开的尖塔（4 层以上）",
        "用上 4 种不同的材料",
        "一共搭到 60 块"
    )
    val SHAPE_REWARD = intArrayOf(60, 150, 120, 100, 200, 200, 120, 300)

    /** 顶块：上面空着 */
    private fun isTop(g: ByteArray, x: Int, y: Int, z: Int) = filled(g, x, y, z) && !filled(g, x, y + 1, z)

    /** 孤立的顶：同一高度四周都没有块，长出来就是尖顶 */
    private fun isSpire(g: ByteArray, x: Int, y: Int, z: Int) = isTop(g, x, y, z) &&
        !filled(g, x - 1, y, z) && !filled(g, x + 1, y, z) && !filled(g, x, y, z - 1) && !filled(g, x, y, z + 1)

    fun shapes(g: ByteArray): Int {
        var mask = 0
        var maxY = -1
        var overhang = 0
        var terrace = 0
        var spires = 0
        var tower = false
        var courtyard = false
        var mats = 0
        var n = 0
        for (y in 0 until SY) for (z in 0 until SZ) for (x in 0 until SX) {
            val m = at(g, x, y, z)
            if (m == 0) {
                if (y == 0 && filled(g, x - 1, 0, z) && filled(g, x + 1, 0, z) &&
                    filled(g, x, 0, z - 1) && filled(g, x, 0, z + 1)
                ) courtyard = true
                continue
            }
            n++
            mats = mats or (1 shl m)
            if (y > maxY) maxY = y
            if (y > 0 && !filled(g, x, y - 1, z)) overhang++
            if (isTop(g, x, y, z) && (filled(g, x - 1, y + 1, z) || filled(g, x + 1, y + 1, z) ||
                    filled(g, x, y + 1, z - 1) || filled(g, x, y + 1, z + 1))
            ) terrace++
            if (isSpire(g, x, y, z)) {
                if (y >= 3) spires++
                if (y >= 4) tower = true
            }
        }
        if (maxY >= 2) mask = mask or 1
        if (tower) mask = mask or (1 shl 1)
        if (overhang >= 3) mask = mask or (1 shl 2)
        if (terrace >= 3) mask = mask or (1 shl 3)
        if (courtyard) mask = mask or (1 shl 4)
        if (spires >= 2) mask = mask or (1 shl 5)
        if (Integer.bitCount(mats) >= 4) mask = mask or (1 shl 6)
        if (n >= 60) mask = mask or (1 shl 7)
        return mask
    }

    // ---------- 猫能去的地方：搭出来的结构就是它的去处 ----------
    const val SPOT_TERRACE = 0   // 露台：蹦上去晒太阳
    const val SPOT_DOOR = 1      // 门口台阶：坐着看院子
    const val SPOT_ARCADE = 2    // 拱廊底下：钻进阴凉打盹

    /** 编码：kind * 512 + 格子下标。露台给的是那块顶块，拱廊给的是地面上那个空格 */
    fun spotKind(code: Int) = code / 512
    fun spotCell(code: Int) = code % 512

    fun catSpots(g: ByteArray, out: IntArray): Int {
        var n = 0
        fun add(kind: Int, cell: Int) { if (n < out.size) out[n++] = kind * 512 + cell }
        val door = doorCell(g)
        if (door >= 0) add(SPOT_DOOR, door)
        for (y in 0 until SY) for (z in 0 until SZ) for (x in 0 until SX) {
            if (isTerraceTop(g, x, y, z)) add(SPOT_TERRACE, idx(x, y, z))
            if (y == 0 && !filled(g, x, 0, z)) {
                var roofed = false
                for (yy in 1 until SY) if (filled(g, x, yy, z)) { roofed = true; break }
                if (roofed) add(SPOT_ARCADE, idx(x, 0, z))
            }
        }
        return n
    }

    /** 露台：顶上空着，旁边有更高的楼 */
    fun isTerraceTop(g: ByteArray, x: Int, y: Int, z: Int) = isTop(g, x, y, z) &&
        (filled(g, x - 1, y + 1, z) || filled(g, x + 1, y + 1, z) ||
            filled(g, x, y + 1, z - 1) || filled(g, x, y + 1, z + 1))

    // ---------- 格子 ↔ 世界 ----------
    /** 格子中心的世界坐标；房子锚点 (hx, hz) 对着格子中央，y 从台地面起算 */
    fun cellX(hx: Float, x: Int) = hx + (x - SX / 2) * CELL
    fun cellY(y: Int) = HomeYard.DECK_Y + (y + 0.5f) * CELL
    fun cellZ(hz: Float, z: Int) = hz + (z - SZ / 2) * CELL

    /**
     * 射线拾取：沿手指那条射线往里走，第一个碰到的实心格就是「点中的」，
     * 碰到它之前的那个空格就是「要加在哪」。一路没碰到房子但落到了地上，就加在地上那一格。
     *
     * 用细步长步进而不是严格的体素遍历：441 格、每次点击走几百步，手机上不到一毫秒，
     * 换来的是代码一眼能看懂。
     *
     * @param out 0 = 要加的格（-1 无），1 = 点中的格（-1 无）
     */
    fun pick(g: ByteArray, near: FloatArray, far: FloatArray, hx: Float, hz: Float, out: IntArray) {
        out[0] = -1; out[1] = -1
        // 世界 → 格子连续坐标（格子 i 覆盖 [i, i+1)）
        fun gx(wx: Float) = (wx - hx) / CELL + SX / 2 + 0.5f
        fun gy(wy: Float) = (wy - HomeYard.DECK_Y) / CELL
        fun gz(wz: Float) = (wz - hz) / CELL + SZ / 2 + 0.5f
        val ax = gx(near[0]); val ay = gy(near[1]); val az = gz(near[2])
        val bx = gx(far[0]); val by = gy(far[1]); val bz = gz(far[2])
        val len = kotlin.math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az))
        if (len < 1e-4f) return
        val step = 0.04f
        val n = (len / step).toInt().coerceAtMost(40_000)
        var prev = -1
        for (i in 0..n) {
            val t = i * step / len
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            val pz = az + (bz - az) * t
            if (py < 0f) {
                // 落地：在地面这一格加（前提是它在格子范围里、还空着）
                val cx = floor(px).toInt(); val cz = floor(pz).toInt()
                if (inBounds(cx, 0, cz) && !filled(g, cx, 0, cz)) out[0] = idx(cx, 0, cz)
                return
            }
            val cx = floor(px).toInt(); val cy = floor(py).toInt(); val cz = floor(pz).toInt()
            if (!inBounds(cx, cy, cz)) { prev = -1; continue }
            val id = idx(cx, cy, cz)
            if (g[id].toInt() != 0) {
                out[1] = id
                out[0] = prev
                return
            }
            // 屋顶不在格子里，但孩子点的就是屋顶：顶块上方这一整格都算点中了顶面（尖顶能高出一格多）
            if (cy > 0 && filled(g, cx, cy - 1, cz)) {
                out[1] = idx(cx, cy - 1, cz)
                out[0] = id
                return
            }
            prev = id
        }
    }
}
