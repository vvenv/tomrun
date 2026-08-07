package com.vvenv.tomrun

import android.content.Context
import android.graphics.RectF
import org.json.JSONObject

/**
 * 家页面**顶栏文字与 UI 按钮**的布局，以及各元素的缩放系数；横竖屏各一套、完全隔离。
 *
 * 原来这里还存着庭院每件道具的 X/Y 坐标（屏幕 s 单位）。家页面改成体素 3D 之后，
 * 庭院里的位置由 [HomeYard] 的世界坐标锚点 + 玩家拖动的偏移决定，这份 JSON 里的
 * `yard` 坐标已不再读取——**`tools/yard-editor.html` 那个 2D 网页编辑器随之作废**，
 * 摆放请直接在游戏里拖（拖的是地面上的真实位置，还有透视）。
 *
 * 仍然生效的是：
 *  - `*S` 缩放：房子 / 藏馆 / 猫 / 顶栏文字（3D 里就是模型整体缩放）
 *  - 顶栏文字（家名称 / 家能量 / 开局奖励）的基线微调
 *  - UI 按钮的覆盖式位置（未被移动过的仍走 HudView 的响应式公式）
 *
 * 绘制前调 [select] 切换朝向。
 */
object LayoutConfig {

    class UiRect(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun resolve(vw: Float, vh: Float, s: Float) = RectF(x * vw, y * vh, x * vw + w * s, y * vh + h * s)
    }

    /** 单个朝向的完整布局。`*S` 为缩放（1 = 原始大小）。 */
    class Set {
        var catS = 1f
        var houseS = 1f
        var museumS = 1f
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
        // yard 段里只剩缩放还有意义：坐标已交给 [HomeYard] 的世界锚点
        yard?.let { y ->
            s.catS = y.optFloat("catS", s.catS)
            s.houseS = y.optFloat("houseS", s.houseS)
            s.museumS = y.optFloat("museumS", s.museumS)
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
