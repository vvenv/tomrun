package com.vvenv.tomrun

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject

/**
 * 家页面布局的单一事实来源，**横竖屏各一套、完全隔离**。
 *
 * 每个元素都可配「位置 + 大小」：
 *  - 庭院道具（望远镜/花坛/秋千/猫爬架/信箱）：X/Y 偏移 + 缩放 s
 *  - 栅栏：Y 偏移 + 缩放
 *  - 泳池：L/R/T/B 自由矩形
 *  - 建筑（房子/藏品馆/荣誉墙）：基线微调 dx/dy + 缩放
 *  - 顶栏文字（家名称/家能量/开局奖励）：基线微调 dx/dy + 字号缩放
 *  - 猫：缩放（位置不可配——它在庭院里自由游走并与道具互动）
 *  - UI 按钮：屏幕比例位置 + s 单位尺寸（覆盖式，未动的走原公式）
 *
 * 与 `tools/yard-editor.html` 共享同一 JSON（v2：顶层 portrait/landscape）。绘制前调 [select]。
 */
object LayoutConfig {

    class Pool(var l: Float, var r: Float, var t: Float, var b: Float) {
        val cx get() = (l + r) / 2f
        val edge get() = l - 14f
        val surface get() = t + 20f
        val swimHalf get() = 46f
    }

    class UiRect(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun resolve(vw: Float, vh: Float, s: Float) = RectF(x * vw, y * vh, x * vw + w * s, y * vh + h * s)
    }

    /** 单个朝向的完整布局。`*S` 为缩放（1 = 原始大小）。 */
    class Set {
        var telescopeX = -176f; var telescopeY = 0f; var telescopeS = 1f
        var gardenX = -232f; var gardenY = 0f; var gardenS = 1f
        var swingX = -280f; var swingY = 0f; var swingS = 1f
        var perchX = 158f; var perchY = 0f; var perchS = 1f
        var mailboxX = 210f; var mailboxY = 0f; var mailboxS = 1f
        var fenceY = 0f; var fenceS = 1f
        val pool = Pool(0f, 264f, 72f, 192f)
        var catS = 1f
        var houseDX = 0f; var houseDY = 0f; var houseS = 1f
        var museumDX = 0f; var museumDY = 0f; var museumS = 1f
        var honorDX = 0f; var honorDY = 0f; var honorS = 1f
        var nameDX = 0f; var nameDY = 0f; var nameS = 1f
        var energyDX = 0f; var energyDY = 0f; var energyS = 1f
        var rewardDX = 0f; var rewardDY = 0f; var rewardS = 1f
        val ui = HashMap<String, UiRect>()
    }

    val portrait = Set()
    val landscape = Set()
    private var active: Set = portrait

    /** 绘制家页面前按当前屏幕方向切换（h > w 为竖屏）。 */
    fun select(isPortrait: Boolean) { active = if (isPortrait) portrait else landscape }
    val cur get() = active

    fun uiOverride(id: String): UiRect? = active.ui[id]

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val txt = runCatching {
            context.assets.open("home_layout.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        runCatching {
            val root = JSONObject(txt)
            val p = root.optJSONObject("portrait")
            val l = root.optJSONObject("landscape")
            if (p != null || l != null) {                       // v2：分朝向
                p?.let { apply(portrait, it.optJSONObject("yard"), it.optJSONObject("text"), it.optJSONObject("ui")) }
                l?.let { apply(landscape, it.optJSONObject("yard"), it.optJSONObject("text"), it.optJSONObject("ui")) }
            } else {                                            // v1：共享值灌入两个朝向，ui 仍分朝向
                val y = root.optJSONObject("yard"); val t = root.optJSONObject("text")
                val u = root.optJSONObject("ui")
                apply(portrait, y, t, u?.optJSONObject("portrait"))
                apply(landscape, y, t, u?.optJSONObject("landscape"))
            }
        }
    }

    private fun apply(s: Set, yard: JSONObject?, text: JSONObject?, ui: JSONObject? = null) {
        yard?.let { y ->
            s.telescopeX = y.optFloat("telescopeX", s.telescopeX); s.telescopeY = y.optFloat("telescopeY", s.telescopeY); s.telescopeS = y.optFloat("telescopeS", s.telescopeS)
            s.gardenX = y.optFloat("gardenX", s.gardenX); s.gardenY = y.optFloat("gardenY", s.gardenY); s.gardenS = y.optFloat("gardenS", s.gardenS)
            s.swingX = y.optFloat("swingX", s.swingX); s.swingY = y.optFloat("swingY", s.swingY); s.swingS = y.optFloat("swingS", s.swingS)
            s.perchX = y.optFloat("perchX", s.perchX); s.perchY = y.optFloat("perchY", s.perchY); s.perchS = y.optFloat("perchS", s.perchS)
            s.mailboxX = y.optFloat("mailboxX", s.mailboxX); s.mailboxY = y.optFloat("mailboxY", s.mailboxY); s.mailboxS = y.optFloat("mailboxS", s.mailboxS)
            s.fenceY = y.optFloat("fenceY", s.fenceY); s.fenceS = y.optFloat("fenceS", s.fenceS)
            s.catS = y.optFloat("catS", s.catS)
            s.houseDX = y.optFloat("houseDX", s.houseDX); s.houseDY = y.optFloat("houseDY", s.houseDY); s.houseS = y.optFloat("houseS", s.houseS)
            s.museumDX = y.optFloat("museumDX", s.museumDX); s.museumDY = y.optFloat("museumDY", s.museumDY); s.museumS = y.optFloat("museumS", s.museumS)
            s.honorDX = y.optFloat("honorDX", s.honorDX); s.honorDY = y.optFloat("honorDY", s.honorDY); s.honorS = y.optFloat("honorS", s.honorS)
            y.optJSONObject("pool")?.let { p ->
                s.pool.l = p.optFloat("l", s.pool.l); s.pool.r = p.optFloat("r", s.pool.r)
                s.pool.t = p.optFloat("t", s.pool.t); s.pool.b = p.optFloat("b", s.pool.b)
            }
        }
        text?.let { t ->
            s.nameDX = t.optFloat("nameDX", s.nameDX); s.nameDY = t.optFloat("nameDY", s.nameDY); s.nameS = t.optFloat("nameS", s.nameS)
            s.energyDX = t.optFloat("energyDX", s.energyDX); s.energyDY = t.optFloat("energyDY", s.energyDY); s.energyS = t.optFloat("energyS", s.energyS)
            s.rewardDX = t.optFloat("rewardDX", s.rewardDX); s.rewardDY = t.optFloat("rewardDY", s.rewardDY); s.rewardS = t.optFloat("rewardS", s.rewardS)
        }
        ui?.let { u ->
            val it = u.keys()
            while (it.hasNext()) {
                val id = it.next()
                u.optJSONObject(id)?.let { r ->
                    s.ui[id] = UiRect(r.optFloat("x", 0f), r.optFloat("y", 0f), r.optFloat("w", 0f), r.optFloat("h", 0f))
                }
            }
        }
    }

    private fun JSONObject.optFloat(key: String, def: Float): Float =
        optDouble(key, def.toDouble()).toFloat()
}
