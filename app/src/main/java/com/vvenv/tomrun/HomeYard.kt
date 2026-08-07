package com.vvenv.tomrun

import android.graphics.RectF
import android.opengl.Matrix

/**
 * 家园 3D 场景的**共享账本**：布局锚点（世界单位）、跨线程的投影结果、庭院生物的状态。
 *
 * 家页面从 Canvas 2D 改成与跑酷同一套体素 3D 之后，画面由 [GameRenderer] 在 GL 线程出，
 * 交互仍然由 [HudView] 在 UI 线程处理——两边必须对同一份坐标说话，这里就是那份坐标：
 *
 *  - **摆放**：每件东西有一个世界坐标锚点 (x, z)，玩家拖动只改相对锚点的偏移
 *    （存在 [PlayerHomeLayout]，单位就是世界单位，不再是屏幕 s 单位）。
 *  - **命中**：GL 线程每帧把每件东西的包围盒投影成屏幕矩形放进 [rects]，HudView 直接拿来
 *    做热区——热区永远跟着 3D 里看到的位置走，不用在 UI 侧复刻一遍相机。
 *  - **拖动**：GL 线程同时发布逆 view-projection 矩阵，UI 侧用 [screenToGround] 把手指
 *    打到 y=0 的地面上，换算成世界坐标。屏幕上拖多远、院子里就走多远，透视自动生效。
 *  - **庭院猫 / 蝴蝶**：AI 仍在 HudView（它要弹提示、发金币、接手势），位置写进这里给渲染读。
 *
 * y 轴向上、z 轴朝镜头为正（与跑酷一致：镜头在 +z 往 -z 看），房屋在院子深处。
 */
object HomeYard {

    // ---------- 尺度 ----------
    /**
     * 院子（台地）半宽。
     *
     * 这个数是被**竖屏**卡出来的：竖屏水平视野只有 ±19°，镜头拉到 25 单位外时院子中段
     * 也就看得下 ±10 左右，再宽两侧就切出画外了。所有摆件的锚点都收在这个宽度里，
     * 横屏富余的横向空间留给远景。
     */
    const val HALF_X = 9.5f
    /** 台地前缘（靠镜头）与后缘 */
    const val FRONT_Z = 8.5f
    const val BACK_Z = -13f
    /** 台地高出地面的厚度 */
    const val DECK_Y = 0.45f
    /** 猫可以走到的范围（比台地略收，别走到栏杆里） */
    const val WALK_HALF_X = 7.6f
    const val WALK_NEAR_Z = 6.4f
    const val WALK_FAR_Z = -1.4f

    // ---------- 元素 ----------
    const val HOUSE = "house"
    const val DOOR = "door"
    const val MUSEUM = "museum"
    const val HONOR = "honor"
    const val GARDEN = "garden"
    const val FENCE = "fence"
    const val MAILBOX = "mailbox"
    const val SWING = "swing"
    const val PERCH = "perch"
    const val POOL = "pool"
    const val TELESCOPE = "telescope"
    const val CAT = "cat"
    const val BUTTERFLY = "butterfly"

    /** 可命中元素表；下标即 [rects] 的槽位 */
    val HIT_IDS = arrayOf(
        HOUSE, DOOR, MUSEUM, HONOR, GARDEN, FENCE, MAILBOX,
        SWING, PERCH, POOL, TELESCOPE, CAT, BUTTERFLY
    )

    /** 装饰下标（[Game.DECO_NAMES]）→ 元素 id */
    fun decoId(deco: Int): String? = when (deco) {
        0 -> GARDEN
        1 -> FENCE
        2 -> MAILBOX
        3 -> SWING
        4 -> PERCH
        5 -> POOL
        6 -> TELESCOPE
        else -> null      // 7 彩旗挂在房檐上，跟着房子走，不单独摆
    }

    /**
     * 各元素的默认锚点 (x, z)。
     *
     * 构图纪律沿用重构时定下的三条（见 memory: home-scene-redesign）：主角是房屋，
     * 藏馆偏置在右后方当远景地标，其余摆件贴着院子两侧留出中间那条从门口到镜头的路。
     */
    fun anchorX(id: String): Float = when (id) {
        HOUSE -> 0f
        // 藏馆压在房屋右后方更深处：竖屏里它离得越远、越靠中，越不会被切出画外
        MUSEUM -> 9.6f
        GARDEN -> -5.6f
        FENCE -> 0f
        MAILBOX -> 4.8f
        SWING -> -7.6f
        PERCH -> 6.2f
        POOL -> -3.6f
        TELESCOPE -> -8.0f
        else -> 0f
    }

    fun anchorZ(id: String): Float = when (id) {
        HOUSE -> -6.2f
        MUSEUM -> -19f
        GARDEN -> 1.6f
        FENCE -> 0f
        MAILBOX -> 5.4f
        SWING -> -2.2f
        PERCH -> 0.4f
        POOL -> 4.6f
        TELESCOPE -> 3.2f
        else -> 0f
    }

    /** 玩家偏移之后的最终位置 */
    fun posX(game: Game, id: String) = anchorX(id) + game.homeYardOffX(id)
    fun posZ(game: Game, id: String) = anchorZ(id) + game.homeYardOffZ(id)

    /** 拖动夹取范围：别把东西拖出台地 */
    fun clampX(v: Float) = v.coerceIn(-13.5f, 15.5f)
    fun clampZ(v: Float) = v.coerceIn(-16f, 9f)

    // ---------- 跨线程：投影出来的屏幕热区 ----------
    /** 每个元素 4 个数：l, t, r, b，归一化到 0~1（原点左上）；宽<=0 表示这帧不可见 */
    private val rects = FloatArray(HIT_IDS.size * 4)
    private val vpInv = FloatArray(16)
    private val rayTmp = FloatArray(4)
    private val rayTmp2 = FloatArray(4)
    @Volatile private var hasFrame = false

    @Synchronized fun beginFrame() {
        java.util.Arrays.fill(rects, 0f)
    }

    @Synchronized fun putRect(slot: Int, l: Float, t: Float, r: Float, b: Float) {
        val i = slot * 4
        rects[i] = l; rects[i + 1] = t; rects[i + 2] = r; rects[i + 3] = b
    }

    @Synchronized fun putInverseVp(m: FloatArray) {
        System.arraycopy(m, 0, vpInv, 0, 16)
        hasFrame = true
    }

    fun ready() = hasFrame

    /** 取某元素的屏幕热区（像素）；这帧没画到就返回 false 并清空 [out] */
    @Synchronized fun hit(id: String, viewW: Float, viewH: Float, out: RectF): Boolean {
        val slot = HIT_IDS.indexOf(id)
        if (slot < 0) { out.setEmpty(); return false }
        val i = slot * 4
        if (rects[i + 2] - rects[i] <= 0f) { out.setEmpty(); return false }
        out.set(rects[i] * viewW, rects[i + 1] * viewH, rects[i + 2] * viewW, rects[i + 3] * viewH)
        return true
    }

    /**
     * 屏幕点 → 地面 (y=[planeY]) 上的世界坐标。
     *
     * 拖动摆件、双击叫猫都走这条：把屏幕点反投影成一条射线，和地面求交。
     * 相机是斜俯视的，所以同样的手指位移在近处对应的世界距离小、远处大——这正是想要的，
     * 拖起来才「跟手」。射线几乎与地面平行（拖到天上去了）时返回 false。
     */
    @Synchronized fun screenToGround(
        px: Float, py: Float, viewW: Float, viewH: Float, out: FloatArray, planeY: Float = DECK_Y
    ): Boolean {
        if (!hasFrame || viewW <= 0f || viewH <= 0f) return false
        val ndcX = px / viewW * 2f - 1f
        val ndcY = 1f - py / viewH * 2f
        rayTmp[0] = ndcX; rayTmp[1] = ndcY; rayTmp[2] = -1f; rayTmp[3] = 1f
        Matrix.multiplyMV(rayTmp2, 0, vpInv, 0, rayTmp, 0)
        if (rayTmp2[3] == 0f) return false
        val nx = rayTmp2[0] / rayTmp2[3]; val ny = rayTmp2[1] / rayTmp2[3]; val nz = rayTmp2[2] / rayTmp2[3]
        rayTmp[0] = ndcX; rayTmp[1] = ndcY; rayTmp[2] = 1f; rayTmp[3] = 1f
        Matrix.multiplyMV(rayTmp2, 0, vpInv, 0, rayTmp, 0)
        if (rayTmp2[3] == 0f) return false
        val fx = rayTmp2[0] / rayTmp2[3]; val fy = rayTmp2[1] / rayTmp2[3]; val fz = rayTmp2[2] / rayTmp2[3]
        val dy = fy - ny
        if (kotlin.math.abs(dy) < 1e-4f) return false
        val t = (planeY - ny) / dy
        if (t < 0f) return false
        out[0] = nx + (fx - nx) * t
        out[1] = nz + (fz - nz) * t
        return true
    }

    // ---------- 庭院猫（AI 在 HudView，位置在这儿） ----------
    @Volatile var catX = -3.5f
    @Volatile var catZ = 4.2f
    @Volatile var catY = 0f
    /** 朝向角（度，0 = 面朝镜头 +z） */
    @Volatile var catYaw = 0f
    @Volatile var catPose = 0
    @Volatile var catSwimT = 0f

    @Volatile var butterflyOn = false
    @Volatile var butterflyX = 0f
    @Volatile var butterflyY = 2.2f
    @Volatile var butterflyZ = 4f

    /** 双击叫猫的落点标记 */
    @Volatile var handLife = 0f
    @Volatile var handX = 0f
    @Volatile var handZ = 0f

    // ---------- 在家期间定格的天色 ----------
    /**
     * 天色在进门那一刻取一次就不动了：`dayPhase` 是 90 秒一整天，在家待几分钟的话
     * 全场光色每分半轮一圈，就成了「全员都在动」。定格之后仍然是「傍晚跑完回家，家就是傍晚」。
     */
    @Volatile var lightDusk = -1f
    @Volatile var lightNight = 0f

    fun ensureLight(game: Game) {
        if (lightDusk < 0f) {
            lightDusk = game.duskAmount()
            lightNight = game.nightAmount()
        }
    }

    /** 离开家页面时清掉，下次进门重新取当时的天色 */
    fun resetLight() {
        lightDusk = -1f
    }
}
