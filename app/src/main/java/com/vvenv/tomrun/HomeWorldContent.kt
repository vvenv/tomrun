package com.vvenv.tomrun

/**
 * 各平行宇宙家园的专属房屋名、装饰名，以及本世界可购买的庭院装饰位掩码。
 * 装饰槽位 0~7 与 [Game.DECO_NAMES] 下标一致，但名称与造型因世界而异。
 */
object HomeWorldContent {
    const val DECO_COUNT = 8

    /** bit i = 装饰 i 在本世界商店出现 */
    private val DECO_AVAILABLE = intArrayOf(
        0b11111111, // 草原
        0b11111111, // 水下
        0b11111111, // 天空
        0b11111111, // 熔岩
        0b11111111, // 糖果
        0b11111011  // 星空：无传统信箱，改用信标类装饰占其他槽
    )

    private val HOUSE_NAMES = arrayOf(
        arrayOf("小木屋", "砖瓦房", "双层小楼", "梦幻城堡"),
        arrayOf("珊瑚小屋", "海螺屋", "双层礁楼", "水晶宫"),
        arrayOf("云朵小屋", "风塔房", "双层云楼", "天穹堡"),
        arrayOf("焦木小屋", "黑曜屋", "熔岩小楼", "火山堡"),
        arrayOf("姜饼小屋", "杯子蛋糕屋", "双层糖楼", "糖果城堡"),
        arrayOf("居住舱", "科研舱", "双层舱楼", "指挥塔")
    )

    private val DECO_NAMES = arrayOf(
        arrayOf("花坛", "木栅栏", "信箱", "秋千", "猫爬架", "小泳池", "望远镜", "彩旗"),
        arrayOf("海葵丛", "礁石围", "漂流瓶", "海藻秋千", "珊瑚架", "潟湖", "潜水镜", "螺号旗"),
        arrayOf("云绒花坛", "云絮栏", "风铃信箱", "云端秋千", "浮云架", "天池", "观星镜", "天旗帜"),
        arrayOf("熔岩花盆", "玄武桩", "焦木信箱", "灰烬秋千", "火山架", "温泉池", "观火镜", "火星旗"),
        arrayOf("糖果花坛", "威化栅栏", "糖霜信箱", "软糖秋千", "甜甜架", "果汁池", "糖果镜", "彩糖旗"),
        arrayOf("晶体花坛", "光能栏", "信标桩", "轨道秋千", "失重架", "星尘池", "射电镜", "星旗")
    )

    private val decoSlotsCache = Array(Game.UNIVERSE_COUNT) { IntArray(0) }

    init {
        for (w in 0 until Game.UNIVERSE_COUNT) {
            val list = ArrayList<Int>(DECO_COUNT)
            for (d in 0 until DECO_COUNT) if (decoAvailable(w, d)) list.add(d)
            decoSlotsCache[w] = list.toIntArray()
        }
    }

    fun decoAvailable(world: Int, deco: Int): Boolean {
        val w = world.coerceIn(0, Game.UNIVERSE_COUNT - 1)
        val d = deco.coerceIn(0, DECO_COUNT - 1)
        return (DECO_AVAILABLE[w] and (1 shl d)) != 0
    }

    fun decoSlots(world: Int): IntArray = decoSlotsCache[world.coerceIn(0, Game.UNIVERSE_COUNT - 1)]

    fun clampDecoBrowse(world: Int, deco: Int): Int {
        val slots = decoSlots(world)
        if (slots.isEmpty()) return 0
        return if (deco in slots) deco else slots[0]
    }

    fun nextDecoBrowse(world: Int, current: Int, delta: Int): Int {
        val slots = decoSlots(world)
        if (slots.isEmpty()) return 0
        var idx = slots.indexOf(current)
        if (idx < 0) idx = 0
        idx = (idx + delta + slots.size) % slots.size
        return slots[idx]
    }

    fun houseName(world: Int, style: Int): String {
        val w = world.coerceIn(0, Game.UNIVERSE_COUNT - 1)
        val s = style.coerceIn(0, HOUSE_NAMES[w].size - 1)
        return HOUSE_NAMES[w][s]
    }

    fun decoName(world: Int, deco: Int): String {
        val w = world.coerceIn(0, Game.UNIVERSE_COUNT - 1)
        val d = deco.coerceIn(0, DECO_COUNT - 1)
        return DECO_NAMES[w][d]
    }
}
