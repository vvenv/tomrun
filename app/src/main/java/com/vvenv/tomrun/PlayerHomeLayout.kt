package com.vvenv.tomrun

import org.json.JSONObject

/**
 * 玩家在家园页自由摆放各元素的偏移（相对 [LayoutConfig] 默认值）。
 * 庭院/建筑/顶栏文字：s 单位；UI 按钮：屏幕像素。
 * 横竖屏各一套，持久化到 SharedPreferences。
 */
object PlayerHomeLayout {

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
        var honorDX = 0f; var honorDY = 0f
        var nameDX = 0f; var nameDY = 0f
        var energyDX = 0f; var energyDY = 0f
        var rewardDX = 0f; var rewardDY = 0f
        var catDX = 0f; var catDY = 0f
        var scarfDX = 0f; var scarfDY = 0f
        val uiDX = HashMap<String, Float>()
        val uiDY = HashMap<String, Float>()
    }

    val UI_IDS = arrayOf(
        "leave", "wallet", "tabColor", "tabTrail", "tabScarf", "tabHat",
        "arrowL", "arrowR", "buy", "worldL", "worldR"
    )

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
        put("honorDX", s.honorDX); put("honorDY", s.honorDY)
        put("nameDX", s.nameDX); put("nameDY", s.nameDY)
        put("energyDX", s.energyDX); put("energyDY", s.energyDY)
        put("rewardDX", s.rewardDX); put("rewardDY", s.rewardDY)
        put("catDX", s.catDX); put("catDY", s.catDY)
        put("scarfDX", s.scarfDX); put("scarfDY", s.scarfDY)
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
        into.honorDX = j.optDouble("honorDX", 0.0).toFloat()
        into.honorDY = j.optDouble("honorDY", 0.0).toFloat()
        into.nameDX = j.optDouble("nameDX", 0.0).toFloat()
        into.nameDY = j.optDouble("nameDY", 0.0).toFloat()
        into.energyDX = j.optDouble("energyDX", 0.0).toFloat()
        into.energyDY = j.optDouble("energyDY", 0.0).toFloat()
        into.rewardDX = j.optDouble("rewardDX", 0.0).toFloat()
        into.rewardDY = j.optDouble("rewardDY", 0.0).toFloat()
        into.catDX = j.optDouble("catDX", 0.0).toFloat()
        into.catDY = j.optDouble("catDY", 0.0).toFloat()
        into.scarfDX = j.optDouble("scarfDX", 0.0).toFloat()
        into.scarfDY = j.optDouble("scarfDY", 0.0).toFloat()
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
