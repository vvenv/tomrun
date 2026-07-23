package com.vvenv.tomrun

/**
 * 望月镜观测卡：图文并茂的短卡。
 * 每卡 = 术语徽章 + 两句钩子 + 四条要点 + 两条「今晚一看」。
 */
object StargazeCards {

    data class Card(
        val term: String,
        val hookPrimary: String,
        val hookMiddle: String,
        val factsPrimary: Array<String>,
        val factsMiddle: Array<String>,
        val tonightPrimary: String,
        val tonightMiddle: String
    )

    private val cards = arrayOf(
        Card(
            term = "潮汐锁定",
            hookPrimary = "月亮永远同一面朝向地球",
            hookMiddle = "自转周期 = 公转周期，所以只见同一面",
            factsPrimary = arrayOf("约 38 万公里", "几乎没有空气"),
            factsMiddle = arrayOf("3.84×10⁵ km", "重力 ≈ 地球 1/6"),
            tonightPrimary = "看亮面在左还是右",
            tonightMiddle = "视直径约 0.5°，对照课本估距离"
        ),
        Card(
            term = "铁锈红",
            hookPrimary = "火星发红，因为石头里的铁生锈了",
            hookMiddle = "铁氧化物铺满表面，不是「着火」",
            factsPrimary = arrayOf("一天约 24.5 小时", "两极有白色冰冠"),
            factsMiddle = arrayOf("大气几乎全是 CO₂", "奥林帕斯山高约 21 km"),
            tonightPrimary = "找偏橙红、不怎么闪的亮点",
            tonightMiddle = "冲日前后它最亮，记一下位置"
        ),
        Card(
            term = "行星环",
            hookPrimary = "土星「耳朵」其实是碎冰带",
            hookMiddle = "环是碎冰与尘埃，不是实心盘子",
            factsPrimary = arrayOf("环很宽、很薄", "主要成分是水冰"),
            factsMiddle = arrayOf("环宽约 28 万公里", "卡西尼缝由引力共振造成"),
            tonightPrimary = "望远镜里看它像带耳朵的椭圆",
            tonightMiddle = "环的形状会随年份慢慢变"
        ),
        Card(
            term = "大红斑",
            hookPrimary = "木星上有个比地球还大的风暴",
            hookMiddle = "南半球反气旋，已转了三百多年",
            factsPrimary = arrayOf("太阳系最大行星", "表面有棕黄云带"),
            factsMiddle = arrayOf("质量 ≈ 地球 318 倍", "自转约 10 小时"),
            tonightPrimary = "找很亮、不怎么闪的那颗",
            tonightMiddle = "双筒镜可见伽利略卫星排成一线"
        ),
        Card(
            term = "恒星摇篮",
            hookPrimary = "星云里正在「生」新的星星",
            hookMiddle = "气体尘埃坍缩，核心点燃核聚变",
            factsPrimary = arrayOf("星云是稀薄气体", "你身体里也有恒星造的元素"),
            factsMiddle = arrayOf("猎户座 M42 是著名星云", "太阳约 46 亿年前诞生"),
            tonightPrimary = "找猎户座腰带下方的朦胧亮斑",
            tonightMiddle = "对比城里和郊外，星星差多少"
        )
    )

    fun card(index: Int): Card = cards[index.coerceIn(0, cards.lastIndex)]

    fun hooks(card: Card): Array<String> = arrayOf(card.hookPrimary, card.hookMiddle)

    fun facts(card: Card): Array<String> = card.factsPrimary + card.factsMiddle

    fun tonights(card: Card): Array<String> = arrayOf(card.tonightPrimary, card.tonightMiddle)
}
