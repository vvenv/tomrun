package com.vvenv.tomrun

import org.json.JSONArray
import org.json.JSONObject

/**
 * 玩家在家园页自由摆放各元素的偏移（相对 [LayoutConfig] 默认值）。
 * 庭院/建筑/顶栏文字：s 单位；UI 按钮：屏幕像素。
 * 横竖屏各一套，持久化到 SharedPreferences。
 */
object PlayerHomeLayout {

    data class Element(val id: String, val label: String)

    /** 家园可单独显示/隐藏的元素（与 HudView 拖放 id 一致） */
    val ELEMENTS = arrayOf(
        Element("house", "房子"),
        // 藏品馆与荣誉墙已合成一栋「藏馆」：museum = 整栋（可搬可缩放），
        // honor = 侧翼墙上的等级奖章（只能开关，位置跟着整栋走）
        Element("museum", "藏馆"),
        Element("honor", "荣誉奖章"),
        Element("garden", "花坛"),
        Element("fence", "栅栏"),
        Element("mailbox", "信箱"),
        Element("swing", "秋千"),
        Element("perch", "猫爬架"),
        Element("pool", "泳池"),
        Element("telescope", "望远镜"),
        Element("cat", "猫"),
        Element("name", "家名称"),
        Element("energy", "家能量"),
        Element("reward", "开局奖励"),
        Element("homeHint", "摆放提示"),
        Element("museumLabel", "藏馆铭牌"),
        Element("leave", "出门"),
        Element("wallet", "钱包"),
    )

    class Set {
        var gardenX = 0f; var gardenY = 0f
        var fenceY = 0f
        var mailboxX = 0f; var mailboxY = 0f
        var swingX = 0f; var swingY = 0f
        var perchX = 0f; var perchY = 0f
        var telescopeX = 0f; var telescopeY = 0f
        var poolDX = 0f; var poolDY = 0f
        var houseDX = 0f; var houseDY = 0f
        var museumDX = 0f; var museumDY = 0f
        var nameDX = 0f; var nameDY = 0f
        var energyDX = 0f; var energyDY = 0f
        var rewardDX = 0f; var rewardDY = 0f
        var catDX = 0f; var catDY = 0f
        val uiDX = HashMap<String, Float>()
        val uiDY = HashMap<String, Float>()
        val hidden = HashSet<String>()
    }

    /**
     * 屏幕像素坐标的 UI 元素。商店条那批（tab×4 / 翻页×2 / 购买）随选物面板一起删了——
     * 面板是底部整块自排版的，没有可以单独挪位的零件。
     */
    val UI_IDS = arrayOf("leave", "wallet")

    fun labelOf(id: String) = ELEMENTS.firstOrNull { it.id == id }?.label ?: id

    fun toJson(s: Set): JSONObject = JSONObject().apply {
        put("gardenX", s.gardenX); put("gardenY", s.gardenY)
        put("fenceY", s.fenceY)
        put("mailboxX", s.mailboxX); put("mailboxY", s.mailboxY)
        put("swingX", s.swingX); put("swingY", s.swingY)
        put("perchX", s.perchX); put("perchY", s.perchY)
        put("telescopeX", s.telescopeX); put("telescopeY", s.telescopeY)
        put("poolDX", s.poolDX); put("poolDY", s.poolDY)
        put("houseDX", s.houseDX); put("houseDY", s.houseDY)
        put("museumDX", s.museumDX); put("museumDY", s.museumDY)
        put("nameDX", s.nameDX); put("nameDY", s.nameDY)
        put("energyDX", s.energyDX); put("energyDY", s.energyDY)
        put("rewardDX", s.rewardDX); put("rewardDY", s.rewardDY)
        put("catDX", s.catDX); put("catDY", s.catDY)
        if (s.hidden.isNotEmpty()) {
            put("hidden", JSONArray(s.hidden.toList()))
        }
        val ui = JSONObject()
        for (id in UI_IDS) {
            val dx = s.uiDX[id] ?: 0f
            val dy = s.uiDY[id] ?: 0f
            if (dx != 0f || dy != 0f) {
                ui.put(id, JSONObject().put("dx", dx).put("dy", dy))
            }
        }
        if (ui.length() > 0) put("ui", ui)
    }

    fun fromJson(j: JSONObject?, into: Set) {
        if (j == null) return
        into.gardenX = j.optDouble("gardenX", 0.0).toFloat()
        into.gardenY = j.optDouble("gardenY", 0.0).toFloat()
        into.fenceY = j.optDouble("fenceY", 0.0).toFloat()
        into.mailboxX = j.optDouble("mailboxX", 0.0).toFloat()
        into.mailboxY = j.optDouble("mailboxY", 0.0).toFloat()
        into.swingX = j.optDouble("swingX", 0.0).toFloat()
        into.swingY = j.optDouble("swingY", 0.0).toFloat()
        into.perchX = j.optDouble("perchX", 0.0).toFloat()
        into.perchY = j.optDouble("perchY", 0.0).toFloat()
        into.telescopeX = j.optDouble("telescopeX", 0.0).toFloat()
        into.telescopeY = j.optDouble("telescopeY", 0.0).toFloat()
        into.poolDX = j.optDouble("poolDX", 0.0).toFloat()
        into.poolDY = j.optDouble("poolDY", 0.0).toFloat()
        into.houseDX = j.optDouble("houseDX", 0.0).toFloat()
        into.houseDY = j.optDouble("houseDY", 0.0).toFloat()
        into.museumDX = j.optDouble("museumDX", 0.0).toFloat()
        into.museumDY = j.optDouble("museumDY", 0.0).toFloat()
        into.nameDX = j.optDouble("nameDX", 0.0).toFloat()
        into.nameDY = j.optDouble("nameDY", 0.0).toFloat()
        into.energyDX = j.optDouble("energyDX", 0.0).toFloat()
        into.energyDY = j.optDouble("energyDY", 0.0).toFloat()
        into.rewardDX = j.optDouble("rewardDX", 0.0).toFloat()
        into.rewardDY = j.optDouble("rewardDY", 0.0).toFloat()
        into.catDX = j.optDouble("catDX", 0.0).toFloat()
        into.catDY = j.optDouble("catDY", 0.0).toFloat()
        into.hidden.clear()
        j.optJSONArray("hidden")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotEmpty() }?.let { into.hidden.add(it) }
            }
        }
        into.uiDX.clear()
        into.uiDY.clear()
        j.optJSONObject("ui")?.let { ui ->
            val keys = ui.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                ui.optJSONObject(id)?.let { r ->
                    into.uiDX[id] = r.optDouble("dx", 0.0).toFloat()
                    into.uiDY[id] = r.optDouble("dy", 0.0).toFloat()
                }
            }
        }
    }
}
