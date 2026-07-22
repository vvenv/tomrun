package com.vvenv.tomrun

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 纯游戏逻辑（不含渲染）：三跑道无尽跑酷。
 * update() 在 GL 线程调用；滑动输入来自 UI 线程，方法内加锁。
 * HUD 只读取 @Volatile 字段 / 只读列表快照。
 */
class Game {

    enum class State { READY, RUNNING, DEAD }

    companion object {
        const val OBST_LOW = 0
        const val OBST_BAR = 1
        const val OBST_BLOCK = 2
        const val COIN = 3
        const val P_MAGNET = 4
        const val P_HELMET = 5
        const val P_DOUBLE = 6
        const val OBST_RAMP = 7
        const val P_BOOST = 8

        val LANE_X = floatArrayOf(-2.2f, 0f, 2.2f)
        const val SPAWN_Z = -150f
        const val GRAVITY = 24f
        const val JUMP_V = 8.6f
        const val SLIDE_TIME = 0.75f
        const val RAMP_LENGTH = 9f
        const val RAMP_HEIGHT = 2.4f
        const val CABLE_H = 5.4f
        const val RIDE_Y = 3.0f
        /** 滑索悬挂高度：上滑升高、下滑降低 */
        const val RIDE_Y_MIN = 0.5f
        const val RIDE_Y_MAX = 3.6f
        const val RIDE_STEP = 0.9f

        // 速度：慢起步，约 110 秒接近上限（整体节奏偏慢，便于看清藏品/妖怪）
        const val SPEED_START = 10f
        const val SPEED_MAX = 21f
        const val SPEED_RAMP = 0.18f
        const val SPEED_RAMP_EASE_SECS = 110f
        const val BOOST_MULT = 1.22f
        /** 拾取/击倒等奖励瞬间：短暂减速让玩家读清反馈 */
        const val PACE_SLOW_MULT = 0.68f

        // 连击阈值：x2/x3/x4/x5
        const val COMBO_WINDOW = 1.6f
        val COMBO_THRESH = intArrayOf(5, 12, 22, 35)
        const val COMBO_MAX_MULT = 5

        const val MAGNET_BASE = 8f
        const val DOUBLE_BASE = 10f
        const val BOOST_BASE = 6f
        const val MAGNET_CAP = 16f
        const val DOUBLE_CAP = 20f
        const val BOOST_CAP = 12f
        const val HELMET_MAX = 2
        /**
         * 磁铁吸附范围：身前多少个单位内的金币会被吸走（跨车道，不看左右距离）。
         *
         * 参照物：整条跑道左右总宽只有 4.4（三道间距 2.2），波间距 18~58。
         * 范围过大会一次吸空一整波还搭上下一波，吃磁铁的几秒里走位完全不重要，
         * 且三条道的金币同时朝屏幕中间飞，画面最闹。
         * 14 约等于 0.7~1.4 秒路程，跨车道的爽感还在，但一次大致只覆盖一波。
         */
        const val MAGNET_RANGE = 14f
        /** 边道再往外拨的抖动反馈时长 */
        const val EDGE_BUMP_DURATION = 0.26f

        // 任务类型
        const val Q_COINS = 0
        const val Q_DIST = 1
        const val Q_COMBO = 2
        const val Q_JUMP = 3
        const val Q_SLIDE = 4
        const val Q_SMASH = 5
        const val Q_PORTAL = 6
        const val Q_RELIC = 7
        const val Q_BATTLE = 8

        // 随机妖怪追击（跑酷中追上击倒）
        const val CHASE_FIRST = 380f
        const val CHASE_CATCH_Z = -1.2f
        const val CHASE_RELIC_CHANCE = 0.25f
        /** 各宇宙妖怪：名称 + 描述（每宇宙 2 种） */
        val YOKAI_NAMES = arrayOf(
            arrayOf("疾风妖狼", "草妖"),
            arrayOf("深海妖鱿", "晶壳妖蟹"),
            arrayOf("雷鸟妖", "云鲸妖"),
            arrayOf("熔岩妖兽", "火羽妖"),
            arrayOf("糖妖", "巧妖龙"),
            arrayOf("星外妖", "猎光妖")
        )
        val YOKAI_DESC = arrayOf(
            arrayOf("在草原狂奔的狼形妖怪", "躲在草里的调皮小妖"),
            arrayOf("触手乱舞的深海妖", "硬壳飞逃的蟹妖"),
            arrayOf("电光翅膀的猛禽妖", "在云里游动的巨妖"),
            arrayOf("浑身岩浆的兽妖", "浴火逃窜的鸟妖"),
            arrayOf("圆滚滚弹跳的糖妖", "甜气逼人的龙妖"),
            arrayOf("来自星空的异形妖", "追光而行的猎妖")
        )
        val YOKAI_COLORS = arrayOf(
            intArrayOf(0xFF8B6914.toInt(), 0xFF4DE8A0.toInt()),
            intArrayOf(0xFF3A5FCD.toInt(), 0xFF66CCFF.toInt()),
            intArrayOf(0xFFFFD426.toInt(), 0xFFB0C4FF.toInt()),
            intArrayOf(0xFFFF5722.toInt(), 0xFFFF7043.toInt()),
            intArrayOf(0xFFFF69B4.toInt(), 0xFF6D4C2A.toInt()),
            intArrayOf(0xFF9C27B0.toInt(), 0xFF00E5FF.toInt())
        )
        /** 各宇宙妖怪的「母语」呼喊（跑逃时随机冒出） */
        val YOKAI_SHOUTS = arrayOf(
            arrayOf("嗷呜·咻咻!", "呜咔——嗷?", "嘎嗷·呼呼!"),
            arrayOf("噗噜~咕叽!", "哗啦·啵啵!", "咕叽咕叽·唰!"),
            arrayOf("噼咔·雷嗷!", "滋滋~云呜!", "嘎嘎·冲呀!"),
            arrayOf("轰隆·烫烫!", "咕嘟咕嘟·嗷!", "呼呼·逃咯!"),
            arrayOf("蹦蹦·糖哒!", "咕噜~甜嗷!", "啪嗒啪嗒·咿!"),
            arrayOf("哔啵·星呜!", "嗡——咔嚓?", "咻咻·猎光!")
        )
        // 文物收集：跑道上稀有刷出，收进博物馆图鉴（寓教于乐）
        const val RELIC_COMMON = 0
        const val RELIC_RARE = 1
        const val RELIC_LEGEND = 2
        val RELIC_RARITY_NAMES = arrayOf("普通", "稀有", "传说")
        val RELIC_SCORE = intArrayOf(100, 250, 500)
        val RELIC_WALLET = intArrayOf(5, 15, 40)
        val RELIC_NAMES = arrayOf(
            "彩陶盆", "甲骨文", "青铜爵", "竹简", "秦半两", "汉瓦当", "唐三彩", "青花瓷",
            "后母戊鼎", "越王勾践剑", "曾侯乙编钟", "兵马俑", "铜奔马", "兰亭集序",
            "四羊方尊", "金缕玉衣", "清明上河图", "敦煌飞天",
            // 新增（追加末尾，保持旧存档 bitmask 下标不变）
            "玉琮", "铜镜", "算盘", "司南", "长信宫灯", "马王堆帛画", "三星堆面具", "莲鹤方壶",
            "红山玉龙", "太阳神鸟", "何尊", "击鼓说唱俑", "虎符", "铜车马"
        )
        val RELIC_ERAS = arrayOf(
            "新石器时代", "商代", "商周", "战国", "秦代", "汉代", "唐代", "元明",
            "商代", "春秋", "战国", "秦代", "汉代", "东晋",
            "商代", "汉代", "北宋", "唐代",
            "良渚", "汉代", "明清", "战国", "汉代", "汉代", "商代", "春秋",
            "红山文化", "古蜀", "西周", "汉代", "战国", "秦代"
        )
        val RELIC_FACTS = arrayOf(
            "半坡遗址出土，画着人面鱼纹",
            "刻在龟甲兽骨上的最早汉字",
            "古人宴饮用的三足酒杯",
            "纸发明之前，字写在竹片上",
            "统一天下后的圆形方孔钱",
            "屋檐上刻着吉祥话的瓦头",
            "黄绿白三彩釉的骆驼与骏马",
            "白底蓝花，名扬海上丝绸之路",
            "现存最重的青铜器，约832公斤",
            "埋藏两千多年依然锋利如新",
            "65件铜钟能演奏完整乐曲",
            "守卫秦始皇陵的地下军团",
            "马踏飞燕，中国旅游的标志",
            "王羲之写下的天下第一行书",
            "四角各立一只卷角羊的国宝",
            "金丝串起两千多片玉的葬服",
            "五米长卷画尽北宋都城繁华",
            "莫高窟壁画里的飞舞仙女",
            "内圆外方，象征天地相通",
            "背面常铸神兽与吉祥铭文",
            "一拨珠子就能快速算账",
            "最早的指南工具，勺子指南",
            "宫女跪捧，烟气吸入袖中",
            "覆盖棺盖的T形升仙图",
            "纵目巨耳的青铜神面",
            "壶盖立着展翅欲飞的仙鹤",
            "C形碧玉龙，中华第一龙",
            "四鸟绕日的金箔神徽",
            "铭文最早出现「中国」二字",
            "开怀大笑的说唱艺人陶俑",
            "剖开两半才能调兵的信物",
            "始皇陵出土的彩绘青铜御车"
        )
        val RELIC_RARITY = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1,
            2, 2, 2, 2,
            0, 0, 0, 1, 1, 1, 2, 2,
            0, 2, 1, 0, 1, 2
        )
        val RELIC_COUNT = RELIC_NAMES.size
        // 文物图鉴集齐一次性大奖（随件数上调）
        const val MUSEUM_REWARD = 2200
        const val RELIC_HUD_MAX = 8

        // 平行宇宙
        const val UNI_MEADOW = 0
        const val UNI_WATER = 1
        const val UNI_SKY = 2
        const val UNI_LAVA = 3
        const val UNI_CANDY = 4
        const val UNI_SPACE = 5
        const val UNIVERSE_COUNT = 6
        val UNIVERSE_NAMES = arrayOf("草原世界", "水下世界", "天空世界", "熔岩世界", "糖果世界", "星空世界")
        val UNIVERSE_PERKS = arrayOf("", "水中漂浮跳", "跳得更高", "金币分数x2", "钱包金币x2", "超低重力")
        val UNI_GRAVITY = floatArrayOf(1f, 0.45f, 0.85f, 1f, 1f, 0.35f)
        val UNI_JUMP = floatArrayOf(1f, 0.82f, 1.15f, 1f, 1f, 0.80f)
        const val PORTAL_FIRST = 320f
        // 宇宙图鉴集齐一次性大奖
        const val CODEX_REWARD = 1000

        // 成就类别（每类铜银金三级）
        const val A_COINS = 0
        const val A_DIST = 1
        const val A_QUESTS = 2
        const val A_COMBO = 3
        const val A_SCORE = 4
        const val A_PORTAL = 5
        const val A_UNIVERSE = 6
        const val A_RELIC = 7
        const val A_STAR = 8
        const val A_HOME = 9
        const val ACHIEVE_CATS = 10
        const val ACHIEVE_TIERS_PER = 3
        const val ACHIEVE_MAX = ACHIEVE_CATS * ACHIEVE_TIERS_PER
        val ACHIEVE_TARGETS = arrayOf(
            intArrayOf(200, 1000, 5000),       // 累计金币
            intArrayOf(2000, 10000, 50000),    // 累计距离
            intArrayOf(5, 25, 100),            // 任务数
            intArrayOf(30, 100, 250),          // 最高连击
            intArrayOf(3000, 8000, 20000),     // 最高分
            intArrayOf(3, 15, 50),             // 穿越次数
            intArrayOf(2, 4, 6),               // 探索宇宙数
            intArrayOf(5, 13, RELIC_COUNT),    // 文物图鉴
            intArrayOf(1, 3, 5),               // 观星手册
            intArrayOf(1, 2, 3)                // 小屋能量等级
        )
        val ACHIEVE_NAMES = arrayOf(
            "金币收藏家", "长跑健将", "任务达人", "连击大师", "得分王",
            "平行旅人", "宇宙旅者", "考古少年", "观星少年", "筑巢达人"
        )
        val ACHIEVE_TIERS = arrayOf("铜", "银", "金")
        val ACHIEVE_REWARDS = intArrayOf(100, 250, 500)

        // 外观：4 色 + 4 双脚光迹（0 免费）
        const val CAT_COLOR_COUNT = 4
        const val TRAIL_COUNT = 4
        val COLOR_PRICES = intArrayOf(0, 300, 800, 1500)
        val TRAIL_PRICES = intArrayOf(0, 500, 1200, 2500)
        val COLOR_NAMES = arrayOf("蓝灰", "橘黄", "乌黑", "粉红")
        val TRAIL_NAMES = arrayOf("无光迹", "青色", "金色", "彩虹")
        // 围巾（跑动时飘动）与帽子（吃到头盔时暂被头盔遮住）
        const val SCARF_COUNT = 4
        const val HAT_COUNT = 4
        val SCARF_NAMES = arrayOf("无围巾", "火红围巾", "天青围巾", "星紫围巾")
        val SCARF_PRICES = intArrayOf(0, 400, 1000, 2200)
        val HAT_NAMES = arrayOf("无帽子", "红棒球帽", "青草帽", "金皇冠")
        val HAT_PRICES = intArrayOf(0, 600, 1500, 2800)
        // 小屋标签页：房屋 / 屋顶 / 庭院 / 猫装扮（同时可用于庭院）
        val HOUSE_NAMES = arrayOf("小木屋", "砖瓦房", "双层小楼", "梦幻城堡")
        val HOUSE_PRICES = intArrayOf(0, 800, 2000, 4500)
        val ROOF_NAMES = arrayOf("红屋顶", "青屋顶", "紫屋顶", "金屋顶")
        val ROOF_PRICES = intArrayOf(0, 250, 600, 1200)
        val DECO_NAMES = arrayOf("花坛", "木栅栏", "信箱", "秋千", "猫爬架", "小泳池", "望远镜", "彩旗")
        val DECO_PRICES = intArrayOf(150, 200, 250, 400, 550, 700, 900, 1200)
        const val DECO_TELESCOPE = 6
        /** 望月镜可观测天体；telescopeLevel 0~4 对应已解锁的最高索引 */
        const val TELESCOPE_MAX_LEVEL = 4
        val TELESCOPE_BODY_NAMES = arrayOf("月亮", "火星", "土星", "木星", "深空星云")
        /** 从当前等级升到下一级所需金币 */
        val TELESCOPE_UPGRADE_PRICES = intArrayOf(400, 800, 1500, 2500)
        const val STARGAZE_AGE_PRIMARY = 0
        const val STARGAZE_AGE_MIDDLE = 1
        const val HOME_TAB_HOUSE = 0
        const val HOME_TAB_ROOF = 1
        const val HOME_TAB_DECO = 2
        const val HOME_TAB_COLOR = 3
        const val HOME_TAB_TRAIL = 4
        const val HOME_TAB_SCARF = 5
        const val HOME_TAB_HAT = 6
        const val HOME_TAB_COUNT = 7
        // 小屋能量等级门槛（按已购总价值）与开局奖励
        val HOME_LEVEL_SCORE = intArrayOf(600, 2200, 5000)
        const val DEFAULT_CHARACTER_NAME = "汤姆"
        const val CHARACTER_NAME_MAX_LENGTH = 8

        // 音效事件
        const val EV_JUMP = 0
        const val EV_SLIDE = 1
        const val EV_COIN = 2
        const val EV_POWER = 3
        const val EV_SHIELD = 4
        const val EV_DIE = 5
        const val EV_ZIP = 6
        const val EV_RECORD = 7
        const val EV_COMBO = 8
        const val EV_BOOST = 9
        const val EV_SMASH = 10
        const val EV_QUEST = 11
        const val EV_ACHIEVE = 12
        const val EV_PORTAL = 13
        const val EV_BUY = 14
        const val EV_PET = 15
        const val EV_STARGAZE = 16
        const val EV_BATTLE = 17
        const val EV_BATTLE_WIN = 18
        const val EV_BATTLE_HIT = 19

        const val W_SUNNY = 0
        const val W_RAIN = 1
        const val W_SNOW = 2

        const val HAPTIC_LIGHT = 1
        const val HAPTIC_MED = 2
        const val HAPTIC_HEAVY = 3

        // 菜单面板
        const val PANEL_MAIN = 0
        const val PANEL_HOME = 2
    }

    class Entity(val kind: Int, val lane: Int, var z: Float, var y: Float = 0f) {
        var x = LANE_X[lane]
        var taken = false
        var spin = Random.nextFloat() * 360f
        /** 已被磁铁吸入，效果结束后仍继续飞向猫直到拾取 */
        var magneted = false
        /** 文物金币：-1 表示普通金币，否则为 RELIC_NAMES 下标 */
        var relicId: Int = -1
        val isRelic get() = relicId >= 0
    }

    class Zip(val lane: Int, var entryZ: Float, val length: Float) {
        val exitZ get() = entryZ - length
    }

    class FloatText(val text: String, val color: Int, var life: Float = 2.0f, var y: Float = 0f)

    class Particle(
        var x: Float, var y: Float, var z: Float,
        var vx: Float, var vy: Float, var vz: Float,
        var life: Float, val color: FloatArray, val size: Float
    )

    class Quest(
        val type: Int,
        val target: Int,
        val rewardScore: Int,
        val rewardWallet: Int,
        val label: String
    ) {
        @Volatile var progress = 0
        @Volatile var done = false
    }

    /** 跑酷提示统一走这里排队分行，避免连击 / 横幅 / 纪录叠字 */
    val notices = Notices()

    @Volatile var state = State.READY
    @Volatile var score = 0
    @Volatile var coins = 0          // 本局拾取（计分用）
    @Volatile var highScore = 0
    @Volatile var highDistance = 0
    @Volatile var runNewDistRecord = false
    @Volatile var runNewScoreRecord = false
    @Volatile var deadTime = 0f
    @Volatile var immortalMode = false
        private set

    // 永久钱包（外观货币）与本局净赚
    @Volatile var wallet = 0
    @Volatile var runWalletEarn = 0

    @Volatile var magnetTime = 0f
    @Volatile var doubleTime = 0f
    @Volatile var boostTime = 0f
    @Volatile var helmetLayers = 0
    @Volatile var riding: Zip? = null
    val helmet get() = helmetLayers > 0
    val boosting get() = boostTime > 0f

    @Volatile var combo = 0
    @Volatile var comboMult = 1
    @Volatile var comboNextAt = 5   // 距离下一级所需连击数
    private var comboTimer = 0f
    private var comboScore = 0

    // 文物收集：本局发现数 + 持久图鉴位掩码
    @Volatile var runRelics = 0
    @Volatile var relicsFound = 0
    private var relicMask = 0
    private var museumRewarded = false
    @Volatile var totalRelicPickups = 0
    private var nextRelicAt = 0f

    val quests = ArrayList<Quest>(3)
    @Volatile var bestComboRun = 0
    private var missionBonus = 0
    private var runJumps = 0
    private var runSlides = 0
    private var runSmashes = 0

    // 累计统计
    @Volatile var totalCoins = 0
    @Volatile var totalDistance = 0
    @Volatile var totalQuests = 0
    @Volatile var bestComboEver = 0

    // 成就：每类 0~3 级
    val achieveLevels = IntArray(ACHIEVE_CATS)
    @Volatile var achieveCount = 0   // 已完成级数总和 / ACHIEVE_MAX

    // 外观
    @Volatile var catColor = 0
    @Volatile var trailStyle = 0
    @Volatile var scarfStyle = 0
    @Volatile var hatStyle = 0
    private var ownedColors = 1   // bit0 免费
    private var ownedTrails = 1
    private var ownedScarves = 1
    private var ownedHats = 1

    // 暂停（仅 RUNNING 中有效）
    @Volatile var paused = false

    // 妖怪追击（跑酷不中断：换道/冲刺追上前方妖怪）
    @Volatile var chaseActive = false
    @Volatile var yokaiName = ""
    @Volatile var yokaiDesc = ""
    @Volatile var yokaiColor = 0xFFFF5722.toInt()
    @Volatile var yokaiKind = 0
    @Volatile var yokaiZ = 0f
    @Volatile var yokaiLane = 1
    @Volatile var yokaiSpawnZ = -55f
    @Volatile var yokaiRunPhase = 0f
    @Volatile var yokaiShout = ""
    @Volatile var yokaiShoutFlash = 0f
    @Volatile var yokaiHudX = 0.5f
    @Volatile var yokaiHudY = 0.35f
    @Volatile var yokaiHudVisible = false
    @Volatile var chaseTimeLeft = 0f
    @Volatile var chaseTimeMax = 15f
    @Volatile var chaseRelicDrop = -1
    private var nextChaseAt = 0f
    private var runChases = 0
    private var yokaiLaneTimer = 0f
    private var yokaiShoutTimer = 0f
    private var paceSlowUntil = 0f

    // 平行宇宙
    @Volatile var universe = UNI_MEADOW
    @Volatile var universePrev = UNI_MEADOW
    @Volatile var universeBlend = 1f
    @Volatile var portalActive = false
    @Volatile var portalZ = 0f
    @Volatile var portalTarget = UNI_WATER
    @Volatile var portalFlash = 0f
    @Volatile var totalPortals = 0
    @Volatile var universesSeen = 1
    private var seenMask = 1          // bit0 草原
    private var codexRewarded = false
    private var portalGap = PORTAL_FIRST
    private var runPortals = 0

    // 小屋
    @Volatile var houseStyle = 0
    @Volatile var roofStyle = 0
    private var ownedHouses = 1       // bit0 免费小木屋
    private var ownedRoofs = 1
    private var ownedDecos = 0
    @Volatile var homeTab = HOME_TAB_HOUSE
    @Volatile var homeBrowseHouse = 0
    @Volatile var homeBrowseRoof = 0
    @Volatile var homeBrowseDeco = 0
    /** 望月镜等级 0~4：决定可观测的最高天体索引 */
    @Volatile var telescopeLevel = 0
    /** 观测卡已读位掩码（bit i = 天体 i 至少读过一次） */
    private var stargazeReadMask = 0
    /** 今日是否已读过一张「新卡」；跨日重置 */
    private var stargazeDailyNewDone = false
    private var stargazeDayKey = ""
    /** 小学 / 初中文案档位 */
    @Volatile var stargazeAgeMode = STARGAZE_AGE_PRIMARY
    @Volatile var characterName = DEFAULT_CHARACTER_NAME
        private set
    @Volatile var hasChosenCharacterName = false
        private set

    // 菜单
    @Volatile var menuPanel = PANEL_MAIN
    @Volatile var shopBrowseColor = 0
    @Volatile var shopBrowseTrail = 0
    @Volatile var shopBrowseScarf = 0
    @Volatile var shopBrowseHat = 0

    val floatTexts = ArrayList<FloatText>()
    val particles = ArrayList<Particle>()
    @Volatile var shake = 0f
    @Volatile var hapticPulse = 0
    @Volatile var lastFloat = ""
    @Volatile var lastFloatColor = 0xFFFFD54A.toInt()
    @Volatile var floatFlash = 0f

    @Volatile var onEvent: ((Int) -> Unit)? = null
    private var recordDone = false
    private var settled = false

    @Volatile var weather = W_SUNNY
    @Volatile var weatherPrev = W_SUNNY
    @Volatile var weatherBlend = 1f
    private var weatherTimer = 18f + Random.nextFloat() * 15f

    @Volatile var dayPhase = 0.12f + Random.nextFloat() * 0.2f
    private var daySpeed = 1f / 90f

    var lane = 1
    /** 已在边道时再往外拨：短暂计时驱动猫身抖一下再弹回，给个「到头了」的反馈 */
    @Volatile var edgeBumpTime = 0f
    @Volatile var edgeBumpDir = 0f
    var catX = 0f
    var catY = 0f
    var velY = 0f
    var slideTimer = 0f
    var runPhase = 0f
    private var groundY = 0f
    /** 滑索目标悬挂高度（手势上下调节） */
    private var rideTargetY = RIDE_Y
    val sliding get() = slideTimer > 0f
    val onGround get() = catY <= groundY + 0.001f

    var speed = SPEED_START
    var baseSpeed = SPEED_START
    var distance = 0f
    private var gapRemaining = 0f
    private var zipGap = 100f
    private var scoreBoost = 0f
    private var invulnTime = 0f
    private var wavesSincePower = 0
    private var runTime = 0f
    /** 本波金币串铺到的最深 z（最后一枚）；用于给下一波障碍留出间距 */
    private var waveCoinMinZ = Float.POSITIVE_INFINITY

    val entities = ArrayList<Entity>()
    val ziplines = ArrayList<Zip>()

    /** 文物屏幕标签（由 Renderer 投影，HudView 绘制），坐标为 0~1 */
    val relicHudX = FloatArray(RELIC_HUD_MAX)
    val relicHudY = FloatArray(RELIC_HUD_MAX)
    val relicHudScale = FloatArray(RELIC_HUD_MAX)
    val relicHudId = IntArray(RELIC_HUD_MAX)
    @Volatile var relicHudCount = 0

    private var prefs: SharedPreferences? = null
    private var sessionPickupCoins = 0  // 本局拾取计入累计统计

    fun attachPrefs(p: SharedPreferences) {
        prefs = p
        highScore = p.getInt("high3d", 0)
        highDistance = p.getInt("highDist", 0)
        totalCoins = p.getInt("totalCoins", 0)
        totalDistance = p.getInt("totalDist", 0)
        totalQuests = p.getInt("totalQuests", 0)
        bestComboEver = p.getInt("bestCombo", 0)
        wallet = p.getInt("wallet", 0)
        hasChosenCharacterName = p.contains("characterName")
        characterName = p.getString("characterName", DEFAULT_CHARACTER_NAME)
            ?.trim()
            ?.take(CHARACTER_NAME_MAX_LENGTH)
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CHARACTER_NAME

        // 兼容旧 achieveMask → 迁移为三级成就
        if (p.contains("achieveLv0")) {
            for (i in 0 until ACHIEVE_CATS) {
                achieveLevels[i] = p.getInt("achieveLv$i", 0).coerceIn(0, ACHIEVE_TIERS_PER)
            }
        } else {
            val mask = p.getInt("achieveMask", 0)
            if (mask and 1 != 0) achieveLevels[A_COINS] = 1
            if (mask and 2 != 0) achieveLevels[A_DIST] = 1
            if (mask and 4 != 0) achieveLevels[A_QUESTS] = 1
            if (mask and 8 != 0) achieveLevels[A_COMBO] = 1
        }
        achieveCount = achieveLevels.sum()

        ownedColors = p.getInt("ownedColors", 1) or 1
        ownedTrails = p.getInt("ownedTrails", 1) or 1
        catColor = p.getInt("catColor", 0).coerceIn(0, CAT_COLOR_COUNT - 1)
        trailStyle = p.getInt("trailStyle", 0).coerceIn(0, TRAIL_COUNT - 1)
        if (!ownsColor(catColor)) catColor = 0
        if (!ownsTrail(trailStyle)) trailStyle = 0
        shopBrowseColor = catColor
        shopBrowseTrail = trailStyle

        ownedScarves = p.getInt("ownedScarves", 1) or 1
        ownedHats = p.getInt("ownedHats", 1) or 1
        scarfStyle = p.getInt("scarfStyle", 0).coerceIn(0, SCARF_COUNT - 1)
        hatStyle = p.getInt("hatStyle", 0).coerceIn(0, HAT_COUNT - 1)
        if (!ownsScarf(scarfStyle)) scarfStyle = 0
        if (!ownsHat(hatStyle)) hatStyle = 0
        shopBrowseScarf = scarfStyle
        shopBrowseHat = hatStyle

        totalPortals = p.getInt("totalPortals", 0)
        seenMask = p.getInt("seenUniverses", 1) or 1
        universesSeen = Integer.bitCount(seenMask)
        codexRewarded = p.getBoolean("codexRewarded", false)

        relicMask = p.getInt("relicMask", 0)
        // 只统计当前图鉴范围内的位，避免旧存档高位脏数据。
        // 注意 Int 移位会把位数取模 32：RELIC_COUNT 满 32 时 (1 shl 32) - 1 == 0，
        // 会把整份图鉴清空，所以满 32 位时直接用全 1。
        relicMask = relicMask and if (RELIC_COUNT >= Int.SIZE_BITS) -1 else (1 shl RELIC_COUNT) - 1
        relicsFound = Integer.bitCount(relicMask)
        museumRewarded = p.getBoolean("museumRewarded", false)
        // 图鉴扩容后：未集齐新件数则允许再次领取全收集奖
        if (museumRewarded && relicsFound < RELIC_COUNT) museumRewarded = false
        totalRelicPickups = p.getInt("totalRelicPickups", 0)

        ownedHouses = p.getInt("ownedHouses", 1) or 1
        ownedRoofs = p.getInt("ownedRoofs", 1) or 1
        ownedDecos = p.getInt("ownedDecos", 0)
        houseStyle = p.getInt("houseStyle", 0).coerceIn(0, HOUSE_NAMES.size - 1)
        roofStyle = p.getInt("roofStyle", 0).coerceIn(0, ROOF_NAMES.size - 1)
        if (!ownsHouse(houseStyle)) houseStyle = 0
        if (!ownsRoof(roofStyle)) roofStyle = 0
        homeBrowseHouse = houseStyle
        homeBrowseRoof = roofStyle
        telescopeLevel = p.getInt("telescopeLevel", 0).coerceIn(0, TELESCOPE_MAX_LEVEL)
        stargazeReadMask = p.getInt("stargazeReadMask", 0)
        stargazeDayKey = p.getString("stargazeDayKey", "") ?: ""
        stargazeDailyNewDone = p.getBoolean("stargazeDailyNewDone", false)
        stargazeAgeMode = p.getInt("stargazeAgeMode", STARGAZE_AGE_PRIMARY)
            .coerceIn(STARGAZE_AGE_PRIMARY, STARGAZE_AGE_MIDDLE)
        refreshStargazeDay()
        // 新类别可能已达标（旧存档），启动时静默补发解锁与奖励
        tryUnlockAchievements(persist = true, quiet = true)
    }

    enum class StargazeViewResult { REVIEW, NEW_READ, BLOCKED_NEW }

    fun stargazeCardRead(body: Int): Boolean =
        body in 0..TELESCOPE_MAX_LEVEL && (stargazeReadMask and (1 shl body)) != 0

    fun stargazeReadCount(): Int {
        var n = 0
        for (i in 0..telescopeLevel) if (stargazeCardRead(i)) n++
        return n
    }

    fun canUnlockNewStargazeToday(): Boolean {
        refreshStargazeDay()
        return !stargazeDailyNewDone
    }

    /** 第一个尚未阅读、且已在镜筒等级内解锁的天体；-1 表示全部已读 */
    fun firstUnreadStargazeBody(): Int {
        for (i in 0..telescopeLevel) if (!stargazeCardRead(i)) return i
        return -1
    }

    fun toggleStargazeAgeMode(): Int {
        stargazeAgeMode = if (stargazeAgeMode == STARGAZE_AGE_PRIMARY) {
            STARGAZE_AGE_MIDDLE
        } else {
            STARGAZE_AGE_PRIMARY
        }
        persistStargaze()
        return stargazeAgeMode
    }

    fun stargazeAgeLabel(): String =
        if (stargazeAgeMode == STARGAZE_AGE_MIDDLE) "初中" else "小学"

    /** 切换或打开某张观测卡；每日最多标记一张新卡为已读 */
    @Synchronized fun onStargazeView(body: Int): StargazeViewResult {
        if (!canUseTelescope() || body < 0 || body > telescopeLevel) return StargazeViewResult.REVIEW
        refreshStargazeDay()
        if (stargazeCardRead(body)) return StargazeViewResult.REVIEW
        if (stargazeDailyNewDone) return StargazeViewResult.BLOCKED_NEW
        stargazeReadMask = stargazeReadMask or (1 shl body)
        stargazeDailyNewDone = true
        persistStargaze()
        tryUnlockAchievements(persist = true)
        return StargazeViewResult.NEW_READ
    }

    fun stargazeStatusLine(): String {
        refreshStargazeDay()
        val read = stargazeReadCount()
        val total = telescopeLevel + 1
        return when {
            read >= total && !telescopeCanUpgrade() ->
                "观测手册 ${read}/${total} · 今日已完成"
            !canUnlockNewStargazeToday() ->
                "观测手册 ${read}/${total} · 今日新卡已读，可复习旧卡"
            firstUnreadStargazeBody() >= 0 ->
                "观测手册 ${read}/${total} · 今日还可读 1 张新卡"
            else ->
                "观测手册 ${read}/${total} · 复习模式"
        }
    }

    private fun refreshStargazeDay() {
        val today = todayKey()
        if (stargazeDayKey != today) {
            stargazeDayKey = today
            stargazeDailyNewDone = false
        }
    }

    private fun todayKey(): String {
        val c = java.util.Calendar.getInstance()
        val y = c.get(java.util.Calendar.YEAR)
        val m = c.get(java.util.Calendar.MONTH) + 1
        val d = c.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d%02d%02d".format(y, m, d)
    }

    private fun persistStargaze() {
        refreshStargazeDay()
        prefs?.edit()
            ?.putInt("stargazeReadMask", stargazeReadMask)
            ?.putString("stargazeDayKey", stargazeDayKey)
            ?.putBoolean("stargazeDailyNewDone", stargazeDailyNewDone)
            ?.putInt("stargazeAgeMode", stargazeAgeMode)
            ?.apply()
    }

    fun canUseTelescope() = ownsDeco(DECO_TELESCOPE)

    fun telescopeCanUpgrade() = canUseTelescope() && telescopeLevel < TELESCOPE_MAX_LEVEL

    fun telescopeUpgradePrice(): Int =
        if (telescopeCanUpgrade()) TELESCOPE_UPGRADE_PRICES[telescopeLevel] else 0

    /** 升级望月镜，解锁下一个天体 */
    @Synchronized fun upgradeTelescope(): String {
        if (state == State.RUNNING) return ""
        if (!canUseTelescope()) return "需要先在装饰里购买望远镜"
        if (!telescopeCanUpgrade()) return "望月镜已达最高级"
        val price = telescopeUpgradePrice()
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        telescopeLevel++
        persistHome()
        emit(EV_BUY, HAPTIC_MED)
        tryUnlockAchievements(persist = true)
        return "升级成功！可观测 ${TELESCOPE_BODY_NAMES[telescopeLevel]}"
    }

    /** 打开望月镜时的反馈 */
    fun yardStargaze() {
        emit(EV_STARGAZE, HAPTIC_LIGHT)
    }

    fun ownsColor(i: Int) = (ownedColors and (1 shl i)) != 0
    fun ownsTrail(i: Int) = (ownedTrails and (1 shl i)) != 0
    fun ownsScarf(i: Int) = (ownedScarves and (1 shl i)) != 0
    fun ownsHat(i: Int) = (ownedHats and (1 shl i)) != 0
    fun ownsHouse(i: Int) = (ownedHouses and (1 shl i)) != 0
    fun ownsRoof(i: Int) = (ownedRoofs and (1 shl i)) != 0
    fun ownsDeco(i: Int) = (ownedDecos and (1 shl i)) != 0
    fun seenUniverse(i: Int) = (seenMask and (1 shl i)) != 0
    fun codexComplete() = universesSeen >= UNIVERSE_COUNT
    fun relicCollected(i: Int) = (relicMask and (1 shl i)) != 0
    fun museumComplete() = relicsFound >= RELIC_COUNT

    fun decoOwnedCount(): Int = Integer.bitCount(ownedDecos)

    @Synchronized fun renameCharacter(rawName: String): Boolean {
        if (state == State.RUNNING) return false
        val name = rawName.trim()
        if (name.isEmpty() || name.length > CHARACTER_NAME_MAX_LENGTH) return false
        characterName = name
        hasChosenCharacterName = true
        prefs?.edit()?.putString("characterName", name)?.apply()
        return true
    }

    /** 小屋繁荣值：已购项目总价值（含猫装扮与庭院） */
    fun homeScore(): Int {
        var sum = 0
        for (i in 1 until HOUSE_PRICES.size) if (ownsHouse(i)) sum += HOUSE_PRICES[i]
        for (i in 1 until ROOF_PRICES.size) if (ownsRoof(i)) sum += ROOF_PRICES[i]
        for (i in DECO_PRICES.indices) if (ownsDeco(i)) sum += DECO_PRICES[i]
        for (i in 0 until telescopeLevel) sum += TELESCOPE_UPGRADE_PRICES[i]
        for (i in 1 until CAT_COLOR_COUNT) if (ownsColor(i)) sum += COLOR_PRICES[i]
        for (i in 1 until TRAIL_COUNT) if (ownsTrail(i)) sum += TRAIL_PRICES[i]
        for (i in 1 until SCARF_COUNT) if (ownsScarf(i)) sum += SCARF_PRICES[i]
        for (i in 1 until HAT_COUNT) if (ownsHat(i)) sum += HAT_PRICES[i]
        return sum
    }

    /** 小屋能量 0~3 级：开局分别赠送 磁铁 / +头盔 / +加倍 */
    fun homeLevel(): Int {
        val sc = homeScore()
        var lv = 0
        for (t in HOME_LEVEL_SCORE) if (sc >= t) lv++
        return lv
    }

    fun homeLevelDesc(lv: Int = homeLevel()): String = when (lv) {
        0 -> "装扮家与庭院可获开局奖励"
        1 -> "开局奖励：磁铁5s"
        2 -> "开局奖励：磁铁+头盔"
        else -> "开局奖励：磁铁+头盔+加倍"
    }

    fun isCatHomeTab(tab: Int = homeTab) = tab in HOME_TAB_COLOR..HOME_TAB_HAT

    @Synchronized fun switchMenuPanel(panel: Int) {
        if (state == State.RUNNING) return
        menuPanel = panel
    }

    @Synchronized fun browseColor(delta: Int) {
        if (state == State.RUNNING) return
        shopBrowseColor = (shopBrowseColor + delta + CAT_COLOR_COUNT) % CAT_COLOR_COUNT
    }

    @Synchronized fun browseTrail(delta: Int) {
        if (state == State.RUNNING) return
        shopBrowseTrail = (shopBrowseTrail + delta + TRAIL_COUNT) % TRAIL_COUNT
    }

    /** 购买或装备当前浏览的配色；返回提示文案 */
    @Synchronized fun buyOrEquipColor(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseColor
        if (ownsColor(i)) {
            catColor = i
            persistCosmetics()
            return "已装备 ${COLOR_NAMES[i]}（猫与庭院）"
        }
        val price = COLOR_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedColors = ownedColors or (1 shl i)
        catColor = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        tryUnlockAchievements(persist = true)
        return "购买成功：${COLOR_NAMES[i]}（猫与庭院）"
    }

    @Synchronized fun buyOrEquipTrail(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseTrail
        if (ownsTrail(i)) {
            trailStyle = i
            persistCosmetics()
            return "已装备 ${TRAIL_NAMES[i]}（猫与庭院）"
        }
        val price = TRAIL_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedTrails = ownedTrails or (1 shl i)
        trailStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        tryUnlockAchievements(persist = true)
        return "购买成功：${TRAIL_NAMES[i]}（猫与庭院）"
    }

    @Synchronized fun buyOrEquipScarf(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseScarf
        if (ownsScarf(i)) {
            scarfStyle = i
            persistCosmetics()
            return "已戴上 ${SCARF_NAMES[i]}（猫与庭院）"
        }
        val price = SCARF_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedScarves = ownedScarves or (1 shl i)
        scarfStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        tryUnlockAchievements(persist = true)
        return "购买成功：${SCARF_NAMES[i]}（猫与庭院）"
    }

    @Synchronized fun buyOrEquipHat(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseHat
        if (ownsHat(i)) {
            hatStyle = i
            persistCosmetics()
            return "已戴上 ${HAT_NAMES[i]}（猫与庭院）"
        }
        val price = HAT_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedHats = ownedHats or (1 shl i)
        hatStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        tryUnlockAchievements(persist = true)
        return "购买成功：${HAT_NAMES[i]}（猫与庭院）"
    }

    // ---------- 小屋（含猫装扮与庭院） ----------
    @Synchronized fun switchHomeTab(tab: Int) {
        if (state == State.RUNNING) return
        homeTab = tab.coerceIn(HOME_TAB_HOUSE, HOME_TAB_HAT)
    }

    @Synchronized fun browseHome(delta: Int) {
        if (state == State.RUNNING) return
        when (homeTab) {
            HOME_TAB_HOUSE -> homeBrowseHouse =
                (homeBrowseHouse + delta + HOUSE_NAMES.size) % HOUSE_NAMES.size
            HOME_TAB_ROOF -> homeBrowseRoof =
                (homeBrowseRoof + delta + ROOF_NAMES.size) % ROOF_NAMES.size
            HOME_TAB_DECO -> homeBrowseDeco =
                (homeBrowseDeco + delta + DECO_NAMES.size) % DECO_NAMES.size
            HOME_TAB_COLOR -> browseColor(delta)
            HOME_TAB_TRAIL -> browseTrail(delta)
            HOME_TAB_SCARF -> {
                shopBrowseScarf = (shopBrowseScarf + delta + SCARF_COUNT) % SCARF_COUNT
            }
            else -> {
                shopBrowseHat = (shopBrowseHat + delta + HAT_COUNT) % HAT_COUNT
            }
        }
    }

    /** 购买 / 装备当前浏览的小屋或装扮项目；返回提示文案 */
    @Synchronized fun buyOrEquipHome(): String {
        if (state == State.RUNNING) return ""
        when (homeTab) {
            HOME_TAB_HOUSE -> {
                val i = homeBrowseHouse
                if (ownsHouse(i)) {
                    houseStyle = i
                    persistHome()
                    return "已入住 ${HOUSE_NAMES[i]}"
                }
                val price = HOUSE_PRICES[i]
                if (wallet < price) return "金币不足（需 $price）"
                wallet -= price
                ownedHouses = ownedHouses or (1 shl i)
                houseStyle = i
                persistHome()
                emit(EV_BUY, HAPTIC_MED)
                tryUnlockAchievements(persist = true)
                return "乔迁新居：${HOUSE_NAMES[i]}！"
            }
            HOME_TAB_ROOF -> {
                val i = homeBrowseRoof
                if (ownsRoof(i)) {
                    roofStyle = i
                    persistHome()
                    return "已换上 ${ROOF_NAMES[i]}"
                }
                val price = ROOF_PRICES[i]
                if (wallet < price) return "金币不足（需 $price）"
                wallet -= price
                ownedRoofs = ownedRoofs or (1 shl i)
                roofStyle = i
                persistHome()
                emit(EV_BUY, HAPTIC_MED)
                tryUnlockAchievements(persist = true)
                return "购买成功：${ROOF_NAMES[i]}"
            }
            HOME_TAB_DECO -> {
                val i = homeBrowseDeco
                if (ownsDeco(i)) return "${DECO_NAMES[i]} 已摆放在院子里"
                val price = DECO_PRICES[i]
                if (wallet < price) return "金币不足（需 $price）"
                wallet -= price
                ownedDecos = ownedDecos or (1 shl i)
                persistHome()
                emit(EV_BUY, HAPTIC_MED)
                tryUnlockAchievements(persist = true)
                return "已摆上：${DECO_NAMES[i]}"
            }
            HOME_TAB_COLOR -> return buyOrEquipColor()
            HOME_TAB_TRAIL -> return buyOrEquipTrail()
            HOME_TAB_SCARF -> return buyOrEquipScarf()
            else -> return buyOrEquipHat()
        }
    }

    // ---------- 暂停 ----------
    @Synchronized fun pauseGame() {
        if (state == State.RUNNING) paused = true
    }

    @Synchronized fun resumeGame() {
        paused = false
    }

    /** 暂停菜单里放弃本局：正常结算但不播死亡音效 */
    @Synchronized fun quitRun() {
        if (state != State.RUNNING) return
        paused = false
        settleRun()
        state = State.DEAD
        deadTime = 1f   // 跳过死亡冷却，直接可交互
    }

    /** 庭院随机事件奖励：只进入永久钱包，不计入跑酷局内收入。 */
    @Synchronized fun grantYardCoins(amount: Int, message: String? = null): String {
        if (amount <= 0) return ""
        wallet += amount
        persistHome()
        emit(EV_COIN, HAPTIC_LIGHT)
        return message ?: "小猫在庭院里捡到了 $amount 枚金币！"
    }

    fun consumeHaptic(): Int {
        val h = hapticPulse
        hapticPulse = 0
        return h
    }

    /** 庭院里抚摸小猫时的反馈音效 */
    fun yardPet() {
        emit(EV_PET, HAPTIC_LIGHT)
    }

    private fun emit(event: Int, haptic: Int = 0) {
        if (haptic > hapticPulse) hapticPulse = haptic
        onEvent?.invoke(event)
    }

    fun magnetLeft() = ceil(magnetTime).toInt()
    fun doubleLeft() = ceil(doubleTime).toInt()
    fun boostLeft() = ceil(boostTime).toInt()

    fun comboToNext(): Int {
        if (comboMult >= COMBO_MAX_MULT) return 0
        val need = COMBO_THRESH[comboMult - 1]
        return (need - combo).coerceAtLeast(0)
    }

    fun nightAmount(): Float {
        val p = dayPhase
        return when {
            p < 0.20f -> 0f
            p < 0.35f -> (p - 0.20f) / 0.15f
            p < 0.65f -> 1f
            p < 0.80f -> 1f - (p - 0.65f) / 0.15f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    fun duskAmount(): Float {
        val p = dayPhase
        return when {
            p in 0.18f..0.32f -> 1f - abs(p - 0.25f) / 0.07f
            p in 0.68f..0.82f -> 1f - abs(p - 0.75f) / 0.07f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    fun playerTier(): Int = when {
        totalDistance < 3000 -> 0
        totalDistance < 15000 -> 1
        else -> 2
    }

    fun nextAchieveHint(): String {
        var bestCat = -1
        var bestNeed = Int.MAX_VALUE
        var bestPct = 0f
        for (c in 0 until ACHIEVE_CATS) {
            val lv = achieveLevels[c]
            if (lv >= ACHIEVE_TIERS_PER) continue
            val target = ACHIEVE_TARGETS[c][lv]
            val cur = achieveProgress(c)
            val need = target - cur
            if (need < bestNeed) {
                bestNeed = need
                bestCat = c
                bestPct = (cur.toFloat() / target).coerceIn(0f, 1f)
            }
        }
        if (bestCat < 0) return "荣誉已全部解锁"
        val lv = achieveLevels[bestCat]
        val target = ACHIEVE_TARGETS[bestCat][lv]
        val cur = achieveProgress(bestCat)
        return "${ACHIEVE_NAMES[bestCat]} ${ACHIEVE_TIERS[lv]} $cur/$target"
    }

    fun achieveProgress(cat: Int): Int = when (cat) {
        // 未结算时计入本局进度，避免死亡结算重复累加后再判
        A_COINS -> totalCoins + if (settled) 0 else sessionPickupCoins
        A_DIST -> totalDistance + if (settled) 0 else distance.toInt()
        A_QUESTS -> totalQuests
        A_COMBO -> maxOf(bestComboEver, bestComboRun)
        A_SCORE -> maxOf(highScore, score)
        A_PORTAL -> totalPortals
        A_UNIVERSE -> universesSeen
        A_RELIC -> relicsFound
        A_STAR -> stargazeReadCount()
        A_HOME -> homeLevel()
        else -> 0
    }

    // ---------- 输入 ----------
    /** Debug 测试开关（不死 + 藏品全览）：仅当前进程内有效，重启后自动关闭。 */
    @Synchronized fun toggleImmortalMode(): Boolean {
        if (!BuildConfig.DEBUG) {
            immortalMode = false
            return false
        }
        if (state == State.RUNNING) return immortalMode
        immortalMode = !immortalMode
        return immortalMode
    }

    @Synchronized fun onTap() {
        when (state) {
            State.READY -> {
                if (menuPanel != PANEL_MAIN) { menuPanel = PANEL_MAIN; return }
                reset(); state = State.RUNNING
            }
            State.RUNNING -> jump()
            State.DEAD -> {
                if (deadTime <= 0.6f) return
                if (menuPanel != PANEL_MAIN) { menuPanel = PANEL_MAIN; return }
                reset(); state = State.RUNNING
            }
        }
    }

    @Synchronized fun onSwipeUp() {
        if (state != State.RUNNING) return
        if (riding != null) {
            rideTargetY = (rideTargetY + RIDE_STEP).coerceAtMost(RIDE_Y_MAX)
            return
        }
        jump()
    }

    @Synchronized fun onSwipeDown() {
        if (state != State.RUNNING) return
        if (riding != null) {
            // 已贴最低仍下滑 → 松手落地；否则沿缆绳下降
            if (rideTargetY <= RIDE_Y_MIN + 0.01f) {
                riding = null
                velY = -14f
            } else {
                rideTargetY = (rideTargetY - RIDE_STEP).coerceAtLeast(RIDE_Y_MIN)
            }
            return
        }
        slideTimer = SLIDE_TIME
        if (!onGround) velY = -14f
        runSlides++
        bumpQuest(Q_SLIDE, 1)
        emit(EV_SLIDE, HAPTIC_LIGHT)
    }

    @Synchronized fun onSwipeLeft() {
        if (state != State.RUNNING) return
        if (lane > 0) { lane--; riding = null } else bumpEdge(-1f)
    }

    @Synchronized fun onSwipeRight() {
        if (state != State.RUNNING) return
        if (lane < 2) { lane++; riding = null } else bumpEdge(1f)
    }

    private fun bumpEdge(dir: Float) {
        edgeBumpDir = dir
        edgeBumpTime = EDGE_BUMP_DURATION
    }

    private fun jump() {
        if (riding != null) return
        if (onGround) {
            velY = JUMP_V * UNI_JUMP[universe]
            slideTimer = 0f
            runJumps++
            bumpQuest(Q_JUMP, 1)
            emit(EV_JUMP, HAPTIC_LIGHT)
        }
    }

    @Synchronized fun reset() {
        entities.clear()
        ziplines.clear()
        floatTexts.clear()
        particles.clear()
        notices.clear()
        quests.clear()
        lane = 1; catX = 0f; catY = 0f; velY = 0f
        groundY = 0f; rideTargetY = RIDE_Y
        slideTimer = 0f; runPhase = 0f
        baseSpeed = SPEED_START; speed = SPEED_START
        distance = 0f; score = 0; coins = 0; runTime = 0f
        deadTime = 0f
        magnetTime = 0f; doubleTime = 0f; boostTime = 0f; helmetLayers = 0
        riding = null; scoreBoost = 0f; invulnTime = 0f
        recordDone = false; settled = false
        runNewDistRecord = false; runNewScoreRecord = false
        combo = 0; comboMult = 1; comboTimer = 0f; comboScore = 0
        comboNextAt = COMBO_THRESH[0]
        bestComboRun = 0; missionBonus = 0
        runJumps = 0; runSlides = 0; runSmashes = 0
        runWalletEarn = 0; sessionPickupCoins = 0
        runRelics = 0
        relicHudCount = 0
        // 首件文物约 180 米后出现，之后每 280~520 米一件
        nextRelicAt = 180f + Random.nextFloat() * 120f
        shake = 0f; hapticPulse = 0; floatFlash = 0f; lastFloat = ""
        wavesSincePower = 0
        menuPanel = PANEL_MAIN
        paused = false
        zipGap = 90f + Random.nextFloat() * 80f
        universe = UNI_MEADOW
        universePrev = UNI_MEADOW
        universeBlend = 1f
        portalActive = false
        portalFlash = 0f
        portalGap = PORTAL_FIRST + Random.nextFloat() * 120f
        runPortals = 0
        chaseActive = false
        yokaiShout = ""
        yokaiShoutFlash = 0f
        yokaiHudVisible = false
        paceSlowUntil = 0f
        nextChaseAt = CHASE_FIRST + Random.nextFloat() * 180f
        runChases = 0
        rollQuests()
        // 小屋能量：开局按等级赠送 buff
        val hl = homeLevel()
        if (hl >= 1) magnetTime = 5f
        if (hl >= 2) helmetLayers = 1
        if (hl >= 3) doubleTime = 5f
        if (hl >= 1) enqueueBanner(homeLevelDesc(hl), 0xFF7DEBA0.toInt(), 2.0f)
        if (!museumComplete()) {
            enqueueBanner("留意路上的文物，收进藏品（$relicsFound/$RELIC_COUNT）", 0xFFC77DFF.toInt(), 1.8f)
        }
        // 开局最近一波也要留足反应距离，避免一开始就从近处"冒出"
        var z = -72f
        while (z > SPAWN_Z) {
            spawnWave(z, early = true)
            // 开局同样要给金币串留尾距，否则第一波障碍就压在金币串末尾
            z -= max(26f + Random.nextFloat() * 14f, (z - waveCoinMinZ) + coinObstClear())
        }
        gapRemaining = nextGap()
    }

    /** 反应时间间距：前期 ~2.1s，后期 ~1.05s；前 500m 略放宽 */
    private fun nextGap(): Float {
        val t = (distance / 2500f).coerceIn(0f, 1f)
        val earlyEase = if (distance < 500f) 1f + 0.15f * (1f - distance / 500f) else 1f
        val react = (2.1f - t * 0.85f) * earlyEase
        val jitter = 0.88f + Random.nextFloat() * 0.28f
        return (baseSpeed * react * jitter).coerceIn(18f, 58f)
    }

    /**
     * 金币串与障碍之间的最小前后间距。
     *
     * 吃金币时视线是锁在金币上的，金币串的头尾都要留出「先看清障碍再决定」的余量，
     * 否则孩子顺着金币冲过去，障碍已经到脸上了。按当前速度折算成约 0.6 秒反应时间，
     * 比波间距（1.25~2.1 秒）短，但足够从「盯金币」切回「看路」。
     */
    private fun coinObstClear(): Float = (speed * 0.6f).coerceIn(10f, 18f)

    /**
     * 波间距：除了 [nextGap] 的反应时间，还要保证下一波障碍
     * 离**本波最后一枚金币**有 [coinObstClear] 的余量。
     *
     * 纯金币波的第二列会按变道时间往后拉很远（可达 26 个单位），
     * 只按 [nextGap] 排下一波的话，障碍会正好落在第二列金币的开头。
     */
    private fun gapAfterWave(zBase: Float): Float {
        val gap = nextGap()
        if (!waveCoinMinZ.isFinite()) return gap
        return max(gap, (zBase - waveCoinMinZ) + coinObstClear())
    }

    private fun triggerPaceSlow(secs: Float) {
        paceSlowUntil = paceSlowUntil.coerceAtLeast(secs)
    }

    // ---------- 主更新 ----------
    @Synchronized fun update(dt: Float) {
        tickDayNight(dt)
        tickFeedback(dt)
        notices.tick(dt)
        if (shake > 0f) shake = (shake - dt * 5f).coerceAtLeast(0f)
        if (edgeBumpTime > 0f) edgeBumpTime = (edgeBumpTime - dt).coerceAtLeast(0f)
        if (portalFlash > 0f) portalFlash -= dt
        if (universeBlend < 1f) universeBlend = min(1f, universeBlend + dt / 1.5f)
        if (state == State.DEAD) {
            deadTime += dt
            return
        }
        if (paused) return
        tickWeather(dt)
        runPhase += dt * speed * 0.9f
        if (state != State.RUNNING) return

        runTime += dt
        baseSpeed = min(SPEED_START + SPEED_RAMP * runTime, SPEED_MAX)
        // 前期缓加速（ease-out）：90s 内渐近上限，30s 时约 65% 而非 82%
        if (runTime < SPEED_RAMP_EASE_SECS) {
            val u = runTime / SPEED_RAMP_EASE_SECS
            val eased = 1f - (1f - u) * (1f - u)
            baseSpeed = SPEED_START + (SPEED_MAX - SPEED_START) * eased
        }
        speed = if (boosting) baseSpeed * BOOST_MULT else baseSpeed
        paceSlowUntil = (paceSlowUntil - dt).coerceAtLeast(0f)
        val paceMult = if (paceSlowUntil > 0f) PACE_SLOW_MULT else 1f
        val dz = speed * dt * paceMult
        distance += dz

        if (magnetTime > 0f) magnetTime = (magnetTime - dt).coerceAtLeast(0f)
        if (doubleTime > 0f) {
            doubleTime = (doubleTime - dt).coerceAtLeast(0f)
            scoreBoost += dz
        }
        if (boostTime > 0f) boostTime = (boostTime - dt).coerceAtLeast(0f)
        if (invulnTime > 0f) invulnTime = (invulnTime - dt).coerceAtLeast(0f)

        if (combo > 0) {
            comboTimer -= dt
            if (comboTimer <= 0f) resetCombo()
        }
        if (floatFlash > 0f) floatFlash -= dt

        val targetX = LANE_X[lane]
        catX += (targetX - catX) * min(1f, dt * 12f)

        val wasGrounded = onGround && velY <= 0f
        val nextGroundY = rampSurfaceAtPlayer(dz)
        groundY = nextGroundY

        for (zip in ziplines) zip.entryZ += dz
        ziplines.removeAll { it.exitZ > 12f }

        val r = riding
        if (r != null) {
            catY += (rideTargetY - catY) * min(1f, dt * 8f)
            velY = 0f
            slideTimer = 0f
            if (r.exitZ >= 0f || r !in ziplines) {
                riding = null
                dismountGrace()
            }
        } else {
            if (wasGrounded && nextGroundY >= catY - 0.08f) {
                catY = nextGroundY
                velY = 0f
            } else if (!onGround || velY > 0f) {
                velY -= GRAVITY * UNI_GRAVITY[universe] * dt
                catY += velY * dt
                if (catY <= groundY) { catY = groundY; velY = 0f }
            }
            if (slideTimer > 0f) slideTimer -= dt
            for (zip in ziplines) {
                if (zip.lane == lane && catY < 0.3f &&
                    zip.entryZ >= 0f && zip.entryZ - dz < 0f &&
                    abs(catX - LANE_X[lane]) < 0.6f
                ) {
                    riding = zip
                    rideTargetY = RIDE_Y
                    emit(EV_ZIP, HAPTIC_MED)
                    break
                }
            }
        }

        val it = entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.z += dz
            if (e.kind == COIN && !e.taken && e.z > -MAGNET_RANGE) {
                if (magnetTime > 0f) e.magneted = true
                if (e.magneted) {
                    val pull = min(1f, dt * 8f)
                    e.x += (catX - e.x) * pull
                    e.y += (catY + 1f - e.y) * pull
                    e.z += (0f - e.z) * min(1f, dt * 4f)
                }
            }
            // 同道（含被磁铁吸引）但没收到的金币，过身后直接清掉，避免在相机前堆成巨大光晕；
            // 其它道上错过的金币保留，直到像普通实体一样滚出可视范围再清
            if (e.kind == COIN && !e.taken && e.z > 2.2f && (e.lane == lane || e.magneted)) {
                it.remove()
                continue
            }
            if (e.z > 8f || e.taken) it.remove()
        }

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            if (p.life <= 0f) { pit.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt + dz
            p.vy -= 18f * dt
            if (p.y < 0.05f) { p.y = 0.05f; p.vy *= -0.3f }
        }

        gapRemaining -= dz
        while (gapRemaining <= 0f) {
            // 卡顿过冲时 gapRemaining 为负，会把波次生成得更近；钳制最小距离避免"眼前刷怪"
            val minDist = (speed * 3.9f).coerceIn(90f, 210f)
            val z = (SPAWN_Z - gapRemaining).coerceAtMost(-minDist)
            spawnWave(z, early = false)
            gapRemaining += gapAfterWave(z)
        }
        zipGap -= dz
        if (zipGap <= 0f && ziplines.isEmpty() && distance > 400f) {
            spawnZipline()
            zipGap = 160f + Random.nextFloat() * 160f
        }

        // 传送门：随世界前移，穿过即切换平行宇宙
        if (portalActive) {
            portalZ += dz
            if (portalZ >= 0.4f) enterPortal()
        } else {
            portalGap -= dz
            if (portalGap <= 0f) {
                portalActive = true
                portalZ = SPAWN_Z
                portalTarget = rollUniverse()
            }
        }

        // 妖怪追击 / 随机触发
        if (chaseActive) tickChase(dt, dz)
        else if (!portalActive && distance >= nextChaseAt) startChase()

        checkCollision()
        syncQuestProgress()
        // 计分：里程 + 加倍里程 + 金币基础分 + 连击额外 + 任务分
        score = (distance + scoreBoost).toInt() + coins * 10 + comboScore + missionBonus

        val beatScore = highScore > 0 && score > highScore
        val beatDist = highDistance > 0 && distance.toInt() > highDistance
        if (!recordDone && (beatScore || beatDist)) {
            recordDone = true
            notices.push(
                "新纪录！", 0xFFFFD426.toInt(), 2.6f,
                Notices.Style.RECORD, Notices.P_RECORD, Notices.KEY_RECORD
            )
            emit(EV_RECORD, HAPTIC_MED)
        }
    }

    private fun tickDayNight(dt: Float) {
        dayPhase += daySpeed * dt
        if (dayPhase >= 1f) dayPhase -= 1f
    }

    private fun tickFeedback(dt: Float) {
        val fit = floatTexts.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.life -= dt
            f.y += dt * 40f
            if (f.life <= 0f) fit.remove()
        }
    }

    private fun enqueueBanner(text: String, color: Int, life: Float = 3.2f) {
        notices.push(text, color, life, Notices.Style.BANNER, Notices.P_BANNER)
    }

    private fun tickWeather(dt: Float) {
        weatherTimer -= dt
        if (weatherTimer <= 0f) {
            weatherPrev = weather
            var next = Random.nextInt(3)
            if (next == weather) next = (next + 1) % 3
            weather = next
            weatherBlend = 0f
            weatherTimer = 25f + Random.nextFloat() * 20f
        }
        if (weatherBlend < 1f) weatherBlend = min(1f, weatherBlend + dt / 2.5f)
    }

    // ---------- 平行宇宙 ----------
    /** 随机挑一个不同于当前的宇宙 */
    private fun rollUniverse(): Int {
        var next = Random.nextInt(UNIVERSE_COUNT)
        if (next == universe) next = (next + 1 + Random.nextInt(UNIVERSE_COUNT - 1)) % UNIVERSE_COUNT
        return next
    }

    private fun enterPortal() {
        portalActive = false
        universePrev = universe
        universe = portalTarget
        universeBlend = 0f
        portalFlash = 0.55f
        // 刚进新宇宙：闪光 + 换景需要适应，前方一段距离不留障碍
        clearObstaclesAhead((speed * 2.5f).coerceAtLeast(55f))
        // 白闪期间仍在跑，给短暂无敌，避免闪完眼前突然撞上障碍
        invulnTime = invulnTime.coerceAtLeast(1.0f)
        portalGap = 480f + Random.nextFloat() * 260f
        runPortals++
        totalPortals++
        val perk = UNIVERSE_PERKS[universe]
        val text = if (perk.isEmpty()) "回到${UNIVERSE_NAMES[universe]}！"
        else "穿越到${UNIVERSE_NAMES[universe]}！$perk"
        enqueueBanner(text, 0xFF4DE8FF.toInt(), 2.6f)
        if (seenMask and (1 shl universe) == 0) {
            seenMask = seenMask or (1 shl universe)
            universesSeen = Integer.bitCount(seenMask)
            enqueueBanner("宇宙图鉴 +1：${UNIVERSE_NAMES[universe]}（$universesSeen/$UNIVERSE_COUNT）", 0xFFFFD426.toInt(), 2.6f)
            // 集齐 6 大宇宙：一次性大奖
            if (!codexRewarded && universesSeen >= UNIVERSE_COUNT) {
                codexRewarded = true
                grantWallet(CODEX_REWARD)
                enqueueBanner(
                    "六大宇宙全部探索完成！大奖 +$CODEX_REWARD 金币！",
                    0xFFFFD426.toInt(), 3.6f
                )
                pushFloat("+$CODEX_REWARD 金币！", 0xFFFFD426.toInt())
                // 彩色庆祝礼花
                spawnBurst(catX, 2.0f, 0f, floatArrayOf(1f, 0.84f, 0.10f, 1f), 14)
                spawnBurst(catX - 1.2f, 1.5f, -1f, floatArrayOf(1f, 0.40f, 0.45f, 1f), 10)
                spawnBurst(catX + 1.2f, 1.5f, -1f, floatArrayOf(0.35f, 0.70f, 1f, 1f), 10)
                emit(EV_ACHIEVE, HAPTIC_HEAVY)
                persistAll()   // 立即落盘，大奖不丢
            }
        }
        pushFloat("穿越！", 0xFF4DE8FF.toInt())
        shake = 0.30f
        spawnBurst(catX, 1.4f, 0f, floatArrayOf(0.6f, 0.85f, 1f, 1f), 16)
        emit(EV_PORTAL, HAPTIC_HEAVY)
        tryUnlockAchievements(persist = false)
    }

    // ---------- 妖怪追击 ----------
    /** 追击进度 0~1，越接近 1 越快要追上 */
    fun chaseProgress(): Float {
        if (!chaseActive) return 0f
        val start = yokaiSpawnZ
        val end = CHASE_CATCH_Z
        if (start <= end) return 0f
        return ((start - yokaiZ) / (start - end)).coerceIn(0f, 1f)
    }

    /** 是否与妖怪在同一道（击倒必要条件） */
    fun chaseSameLane(): Boolean =
        chaseActive && lane == yokaiLane && abs(catX - LANE_X[yokaiLane]) < 0.65f

    private fun startChase() {
        val uni = universe.coerceIn(0, UNIVERSE_COUNT - 1)
        val kind = Random.nextInt(YOKAI_NAMES[uni].size)
        yokaiKind = kind
        yokaiName = YOKAI_NAMES[uni][kind]
        yokaiDesc = YOKAI_DESC[uni][kind]
        yokaiColor = YOKAI_COLORS[uni][kind]
        yokaiLane = Random.nextInt(3)
        yokaiSpawnZ = -78f - Random.nextFloat() * 22f
        yokaiZ = yokaiSpawnZ
        yokaiRunPhase = 0f
        chaseTimeMax = 26f + Random.nextFloat() * 8f
        chaseTimeLeft = chaseTimeMax
        yokaiLaneTimer = 3.2f + Random.nextFloat() * 2.0f
        yokaiShoutTimer = 0.6f
        yokaiShout = ""
        yokaiShoutFlash = 0f
        chaseRelicDrop = -1
        chaseActive = true
        nextChaseAt = distance + 400f + Random.nextFloat() * 320f
        clearObstaclesAhead((speed * 2.5f).coerceAtLeast(55f))
        invulnTime = invulnTime.coerceAtLeast(1.2f)
        triggerPaceSlow(2.4f)
        // 换道提示已经常驻在追击面板里，这里只报出场，避免信息重复
        enqueueBanner("妖怪出没！$yokaiName 追来了！", yokaiColor, 3.0f)
        emit(EV_BATTLE, HAPTIC_HEAVY)
    }

    private fun tickChase(dt: Float, dz: Float) {
        chaseTimeLeft -= dt
        yokaiRunPhase += dt * speed * 0.92f
        yokaiShoutFlash = (yokaiShoutFlash - dt).coerceAtLeast(0f)
        yokaiShoutTimer -= dt
        if (yokaiShoutTimer <= 0f) {
            val uni = universe.coerceIn(0, UNIVERSE_COUNT - 1)
            val pool = YOKAI_SHOUTS[uni]
            yokaiShout = pool[Random.nextInt(pool.size)]
            yokaiShoutFlash = 3.4f
            yokaiShoutTimer = 3.8f + Random.nextFloat() * 2.8f
        }
        yokaiLaneTimer -= dt
        if (yokaiLaneTimer <= 0f) {
            var next = Random.nextInt(3)
            if (next == yokaiLane) next = (next + 1) % 3
            yokaiLane = next
            yokaiLaneTimer = 3.0f + Random.nextFloat() * 2.5f
        }
        var yokaiDz = dz * 0.80f
        if (lane == yokaiLane) yokaiDz -= dz * 0.32f
        else yokaiDz += dz * 0.08f
        if (boosting) yokaiDz -= dz * 0.22f
        yokaiZ += yokaiDz

        if (yokaiZ >= CHASE_CATCH_Z) {
            val sameLane = lane == yokaiLane && abs(catX - LANE_X[yokaiLane]) < 0.65f
            finishChase(won = sameLane, missedLane = !sameLane)
        } else if (chaseTimeLeft <= 0f || yokaiZ < yokaiSpawnZ - 70f) {
            finishChase(won = false)
        }
    }

    private fun finishChase(won: Boolean, missedLane: Boolean = false) {
        if (!chaseActive) return
        chaseActive = false
        yokaiShoutFlash = 0f
        yokaiHudVisible = false
        runChases++
        if (won) {
            triggerPaceSlow(2.8f)
            val tier = ((distance - CHASE_FIRST) / 500f).coerceIn(0f, 3f).toInt()
            val scoreBonus = 140 + tier * 45 + (chaseTimeLeft * 8f).toInt()
            val walletBonus = 22 + tier * 8 + (chaseTimeLeft * 2f).toInt()
            val lavaExtra = if (universe == UNI_LAVA) scoreBonus / 2 else 0
            val candyExtra = if (universe == UNI_CANDY) walletBonus / 2 else 0
            missionBonus += scoreBonus + lavaExtra
            grantWallet(walletBonus + candyExtra)
            if (Random.nextFloat() < CHASE_RELIC_CHANCE) {
                chaseRelicDrop = pickRelicForSpawn()
                awardRelic(chaseRelicDrop)
            }
            bumpQuest(Q_BATTLE, 1)
            enqueueBanner(
                "击倒 $yokaiName！+$scoreBonus 分 · 钱包+${walletBonus + candyExtra}",
                0xFF7DEBA0.toInt(), 3.8f
            )
            pushFloat("击倒!", 0xFF7DEBA0.toInt())
            shake = 0.32f
            spawnBurst(LANE_X[yokaiLane], 1.8f, 0f, floatArrayOf(0.35f, 0.92f, 0.55f, 1f), 14)
            emit(EV_BATTLE_WIN, HAPTIC_HEAVY)
            clearObstaclesAhead((speed * 2.0f).coerceAtLeast(48f))
            invulnTime = invulnTime.coerceAtLeast(1.5f)
        } else {
            val msg = if (missedLane) "不同道！$yokaiName 溜走了" else "$yokaiName 逃走了…"
            enqueueBanner(msg, if (missedLane) 0xFFFFD426.toInt() else 0xFFAAAAAA.toInt(), 2.4f)
            pushFloat(if (missedLane) "须同道!" else "逃走了", if (missedLane) 0xFFFFD426.toInt() else 0xFFAAAAAA.toInt())
        }
        score = (distance + scoreBoost).toInt() + coins * 10 + comboScore + missionBonus
    }

    /** 追击胜利掉落藏品（逻辑同跑道拾取，无实体） */
    private fun awardRelic(id: Int) {
        if (id !in 0 until RELIC_COUNT) return
        runRelics++
        totalRelicPickups++
        val rarity = RELIC_RARITY[id]
        val scoreBonus = RELIC_SCORE[rarity]
        missionBonus += scoreBonus
        grantWallet(RELIC_WALLET[rarity])
        val color = relicBannerColor(rarity)
        enqueueBanner(
            "战利品·${RELIC_RARITY_NAMES[rarity]}文物：${RELIC_NAMES[id]}（${RELIC_ERAS[id]}）+$scoreBonus",
            color, 3.6f
        )
        // 润物细无声：不加「小知识」这类说教前缀，只像展签一样把话说出来；
        // 拾取时已 triggerPaceSlow，停留久一点刚好够读完一句。
        enqueueBanner(RELIC_FACTS[id], 0xFFAAD5FF.toInt(), 5.5f)
        pushFloat(RELIC_NAMES[id], color)
        spawnBurst(catX, 2.2f, -2f, floatArrayOf(0.78f, 0.45f, 1f, 1f), 8)
        if (!relicCollected(id)) {
            relicMask = relicMask or (1 shl id)
            relicsFound = Integer.bitCount(relicMask)
            enqueueBanner("文物图鉴 +1（$relicsFound/$RELIC_COUNT）", 0xFFFFD426.toInt(), 2.2f)
            if (!museumRewarded && museumComplete()) {
                museumRewarded = true
                grantWallet(MUSEUM_REWARD)
                enqueueBanner(
                    "藏品全收集！大奖 +$MUSEUM_REWARD 金币！",
                    0xFFFFD426.toInt(), 3.6f
                )
                pushFloat("+$MUSEUM_REWARD 金币！", 0xFFFFD426.toInt())
                spawnBurst(catX, 2.0f, 0f, floatArrayOf(1f, 0.84f, 0.10f, 1f), 14)
                emit(EV_ACHIEVE, HAPTIC_HEAVY)
                persistAll()
            } else {
                emit(EV_ACHIEVE, HAPTIC_MED)
            }
        }
        tryUnlockAchievements(persist = false)
    }

    // ---------- 生成 ----------
    /** 安全缓冲：清掉前方一段距离内的障碍物，给玩家留足反应时间 */
    private fun clearObstaclesAhead(dist: Float) {
        entities.removeAll {
            it.z > -dist && (it.kind == OBST_LOW || it.kind == OBST_BAR ||
                it.kind == OBST_BLOCK || it.kind == OBST_RAMP)
        }
    }

    /** 下滑索缓冲：落点附近不留障碍，避免刚落地反应不及 */
    private fun dismountGrace() {
        clearObstaclesAhead((speed * 1.7f).coerceAtLeast(40f))
    }

    private fun spawnZipline() {
        val laneZ = Random.nextInt(3)
        val length = 30f + Random.nextFloat() * 50f
        ziplines.add(Zip(laneZ, SPAWN_Z, length))
        // 高低交错，滑索上需上滑/下滑够到
        val coinYs = floatArrayOf(CABLE_H - 1.1f, CABLE_H - 2.2f, CABLE_H - 3.3f)
        var cz = SPAWN_Z - 8f
        var i = 0
        while (cz > SPAWN_Z - length + 4f) {
            entities.add(makeCoin(laneZ, cz, coinYs[i % coinYs.size]))
            i++
            cz -= 4f
        }
    }

    private fun spawnWave(zBase: Float, early: Boolean) {
        wavesSincePower++
        waveCoinMinZ = Float.POSITIVE_INFINITY
        val freeLanes = mutableListOf(0, 1, 2)
        val barOpen = distance > 180f && !early
        val rampOpen = distance > 450f && !early
        val dense = distance > 1200f
        val relaxed = distance < 500f && !early
        val blockCut = if (relaxed) 0.48f else 0.55f
        val lowCut = if (relaxed) 0.72f else 0.82f

        val r = Random.nextFloat()
        when {
            rampOpen && r < 0.14f -> spawnRampWave(zBase)
            barOpen && r < (if (dense) 0.38f else 0.32f) -> {
                val l = Random.nextInt(3)
                entities.add(Entity(OBST_BAR, l, zBase))
                // 金币与横杆同道：串起点退到横杆之后，先铲滑过去再吃，
                // 否则金币等于把人直接引到必须铲滑的位置上，没有反应时间
                coinRow(l, zBase - coinObstClear())
            }
            r < blockCut -> {
                // 最多堵 2 道，始终留一条可走
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_BLOCK, freeLanes[i], zBase))
                coinRow(freeLanes.last(), zBase)
            }
            r < lowCut -> {
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_LOW, freeLanes[i], zBase))
                coinArc(freeLanes[0], zBase)
            }
            else -> {
                // 两列金币：同道可密排；分道则按变道时间拉开 Z，避免来不及换道
                val laneA = Random.nextInt(3)
                val laneB = Random.nextInt(3)
                coinRow(laneA, zBase, 5)
                coinRow(laneB, zBase - coinLaneGap(laneA, laneB, 5), 5)
            }
        }

        // 道具：随机 + 保底（约每 7 波）
        val pity = wavesSincePower >= 7
        if (pity || Random.nextFloat() < 0.15f) {
            val kinds = intArrayOf(P_MAGNET, P_HELMET, P_DOUBLE, P_BOOST)
            entities.add(Entity(kinds[Random.nextInt(kinds.size)], Random.nextInt(3), zBase - 10f, 1.2f))
            wavesSincePower = 0
        }
    }

    private fun spawnRampWave(zBase: Float) {
        val rampLane = Random.nextInt(3)
        entities.add(Entity(OBST_RAMP, rampLane, zBase))
        for (l in 0..2) {
            if (l != rampLane) entities.add(Entity(OBST_BLOCK, l, zBase))
        }
        val half = RAMP_LENGTH / 2f
        for (i in 0 until 5) {
            val localZ = half - 0.8f - i * (RAMP_LENGTH - 1.6f) / 4f
            val surfaceY = RAMP_HEIGHT * (half - localZ) / RAMP_LENGTH
            entities.add(makeCoin(rampLane, zBase + localZ, surfaceY + 1f))
        }
    }

    private fun rampSurfaceAtPlayer(dz: Float): Float {
        val half = RAMP_LENGTH / 2f
        for (e in entities) {
            if (e.kind != OBST_RAMP || e.lane != lane) continue
            val nextZ = e.z + dz
            if (nextZ in -half..half && abs(e.x - catX) < 1.1f) {
                return RAMP_HEIGHT * (nextZ + half) / RAMP_LENGTH
            }
        }
        return 0f
    }

    private fun coinRow(lane: Int, zBase: Float, count: Int = 4) {
        for (i in 0 until count) entities.add(makeCoin(lane, zBase - 1.6f * i, 1.0f))
    }

    /** 两列金币起点间距：同道紧凑；分道按当前速度留足变道时间 */
    private fun coinLaneGap(laneA: Int, laneB: Int, count: Int = 4): Float {
        val rowSpan = 1.6f * (count - 1).coerceAtLeast(1).toFloat()
        val laneDelta = abs(laneA - laneB)
        if (laneDelta == 0) return rowSpan + 1.6f // 同道：约 8f，与旧行为一致
        // 变道插值约 τ=1/12s，邻道 ~0.35s、跨两道 ~0.55s，再加少许反应余量
        val switchSecs = if (laneDelta == 1) 0.45f else 0.65f
        val clearance = (speed * switchSecs).coerceAtLeast(if (laneDelta == 1) 10f else 14f)
        return rowSpan + clearance
    }

    /**
     * 矮障碍跳跃金币弧：仅障碍上方/后方排弧，前方不放低金币以免挡住矮障碍。
     * 玩家在障碍 z≈-2 起跳；拾取窗 abs(z)<1.2、半径 1.15，Y 随跳跃升高（末枚仍在空中）。
     */
    private fun coinArc(lane: Int, zBase: Float) {
        val dz = floatArrayOf(0.0f, -1.0f, -2.0f, -3.0f, -4.0f)
        val ys = floatArrayOf(1.8f, 2.1f, 2.3f, 2.45f, 2.5f)
        for (i in dz.indices) entities.add(makeCoin(lane, zBase + dz[i], ys[i]))
    }

    private fun makeCoin(lane: Int, z: Float, y: Float): Entity {
        waveCoinMinZ = min(waveCoinMinZ, z)
        val e = Entity(COIN, lane, z, y)
        if (distance >= nextRelicAt) {
            e.relicId = pickRelicForSpawn()
            nextRelicAt = distance + 280f + Random.nextFloat() * 240f
        }
        return e
    }

    /** 先按稀有度掷骰（传说需 600 米后），再优先未收集的文物 */
    private fun pickRelicForSpawn(): Int {
        val roll = Random.nextFloat()
        val rarity = when {
            roll < 0.06f && distance > 600f -> RELIC_LEGEND
            roll < 0.30f -> RELIC_RARE
            else -> RELIC_COMMON
        }
        val pool = ArrayList<Int>(RELIC_COUNT)
        for (i in 0 until RELIC_COUNT) {
            if (RELIC_RARITY[i] == rarity && !relicCollected(i)) pool.add(i)
        }
        if (pool.isEmpty()) {
            for (i in 0 until RELIC_COUNT) if (RELIC_RARITY[i] == rarity) pool.add(i)
        }
        return pool[Random.nextInt(pool.size)]
    }

    fun relicBannerColor(rarity: Int): Int = when (rarity) {
        RELIC_LEGEND -> 0xFFFFD426.toInt()
        RELIC_RARE -> 0xFF4DE8FF.toInt()
        else -> 0xFF7DEBA0.toInt()
    }

    private fun collectRelic(e: Entity) {
        val id = e.relicId
        if (id !in 0 until RELIC_COUNT) return
        runRelics++
        totalRelicPickups++
        val rarity = RELIC_RARITY[id]
        val scoreBonus = RELIC_SCORE[rarity]
        missionBonus += scoreBonus
        grantWallet(RELIC_WALLET[rarity])
        val color = relicBannerColor(rarity)
        triggerPaceSlow(2.4f)
        enqueueBanner(
            "发现${RELIC_RARITY_NAMES[rarity]}文物：${RELIC_NAMES[id]}（${RELIC_ERAS[id]}）+$scoreBonus",
            color, 3.6f
        )
        // 寓教于乐：跟一条小知识横幅
        // 润物细无声：不加「小知识」这类说教前缀，只像展签一样把话说出来；
        // 拾取时已 triggerPaceSlow，停留久一点刚好够读完一句。
        enqueueBanner(RELIC_FACTS[id], 0xFFAAD5FF.toInt(), 5.5f)
        pushFloat(RELIC_NAMES[id], color)
        spawnBurst(e.x, e.y, e.z, floatArrayOf(0.78f, 0.45f, 1f, 1f), 6)
        if (!relicCollected(id)) {
            relicMask = relicMask or (1 shl id)
            relicsFound = Integer.bitCount(relicMask)
            enqueueBanner("文物图鉴 +1（$relicsFound/$RELIC_COUNT）", 0xFFFFD426.toInt(), 2.2f)
            if (!museumRewarded && museumComplete()) {
                museumRewarded = true
                grantWallet(MUSEUM_REWARD)
                enqueueBanner(
                    "藏品全收集！大奖 +$MUSEUM_REWARD 金币！",
                    0xFFFFD426.toInt(), 3.6f
                )
                pushFloat("+$MUSEUM_REWARD 金币！", 0xFFFFD426.toInt())
                spawnBurst(catX, 2.0f, 0f, floatArrayOf(1f, 0.84f, 0.10f, 1f), 14)
                spawnBurst(catX - 1.2f, 1.5f, -1f, floatArrayOf(1f, 0.40f, 0.45f, 1f), 10)
                spawnBurst(catX + 1.2f, 1.5f, -1f, floatArrayOf(0.35f, 0.70f, 1f, 1f), 10)
                emit(EV_ACHIEVE, HAPTIC_HEAVY)
                persistAll()   // 立即落盘，大奖不丢
            } else {
                emit(EV_ACHIEVE, HAPTIC_MED)
            }
        } else {
            emit(EV_QUEST, HAPTIC_MED)
        }
        tryUnlockAchievements(persist = false)
    }

    // ---------- 碰撞 / 拾取 ----------
    private fun checkCollision() {
        val catCenterY = catY + (if (sliding) 0.4f else 1.0f)
        val onZip = riding != null
        for (e in entities) {
            if (e.taken) continue
            when (e.kind) {
                COIN -> {
                    val magnetPull = magnetTime > 0f || e.magneted
                    val radius = if (magnetPull) 1.8f else 1.15f
                    val zSlop = if (magnetPull) 5.5f else 1.2f
                    val dx = e.x - catX
                    val dy = e.y - catCenterY
                    if (abs(e.z) < zSlop && dx * dx + dy * dy < radius * radius) {
                        e.taken = true
                        collectCoin(e)
                    }
                }
                P_MAGNET, P_HELMET, P_DOUBLE, P_BOOST -> {
                    if (abs(e.z) < 1.0f && abs(e.x - catX) < 1.1f && abs(e.y - catCenterY) < 1.3f) {
                        e.taken = true
                        pickupPower(e)
                    }
                }
                else -> {
                    if (onZip || invulnTime > 0f) continue
                    if (abs(e.z) > 0.9f) continue
                    if (abs(e.x - catX) >= 1.1f) continue
                    val hit = when (e.kind) {
                        OBST_LOW -> catY < 0.55f
                        OBST_BAR -> !sliding || catY > 0.3f
                        OBST_RAMP -> false
                        else -> true
                    }
                    if (!hit) continue
                    if (boosting && e.kind != OBST_RAMP) {
                        e.taken = true
                        smashObstacle(e)
                        continue
                    }
                    if (!immortalMode && helmetLayers > 0) {
                        helmetLayers--
                        invulnTime = 1f
                        shake = 0.45f
                        resetCombo()
                        spawnBurst(e.x, catY + 1f, e.z, floatArrayOf(1f, 0.76f, 0.12f, 1f), 8)
                        emit(EV_SHIELD, HAPTIC_HEAVY)
                    } else {
                        die()
                        return
                    }
                }
            }
        }
    }

    private fun collectCoin(e: Entity) {
        // 加倍只影响本局计分金币数量，钱包按 1:1 拾取计入本局赚取
        // 熔岩世界：计分金币 x2；糖果世界：钱包金币 x2
        var scoreGained = if (doubleTime > 0f) 2 else 1
        if (universe == UNI_LAVA) scoreGained *= 2
        coins += scoreGained
        sessionPickupCoins += 1
        grantWallet(if (universe == UNI_CANDY) 2 else 1)

        val prevMult = comboMult
        combo++
        comboTimer = COMBO_WINDOW
        if (combo > bestComboRun) bestComboRun = combo
        if (bestComboRun > bestComboEver) bestComboEver = bestComboRun
        comboMult = multForCombo(combo)
        comboNextAt = if (comboMult >= COMBO_MAX_MULT) combo else COMBO_THRESH[comboMult - 1]

        val basePts = 10 * scoreGained
        val comboExtra = basePts * (comboMult - 1)
        comboScore += comboExtra

        val show = if (comboMult > 1) "+${basePts + comboExtra} x$comboMult" else "+$basePts"
        pushFloat(show, 0xFFFFD54A.toInt())
        spawnBurst(e.x, e.y, e.z, floatArrayOf(1f, 0.84f, 0.10f, 1f), 5)

        if (comboMult > prevMult) {
            notices.push(
                "连击 x$comboMult！", 0xFFFFC21F.toInt(), 1.4f,
                Notices.Style.COMBO, Notices.P_COMBO, Notices.KEY_COMBO
            )
            emit(EV_COMBO, HAPTIC_MED)
        } else {
            emit(EV_COIN, HAPTIC_LIGHT)
        }
        bumpQuest(Q_COINS, 1)
        bumpQuest(Q_COMBO, 0) // 用 sync 刷新
        if (e.isRelic) collectRelic(e)
    }

    private fun multForCombo(c: Int): Int {
        var m = 1
        for (t in COMBO_THRESH) {
            if (c >= t) m++ else break
        }
        return min(COMBO_MAX_MULT, m)
    }

    private fun pickupPower(e: Entity) {
        when (e.kind) {
            P_MAGNET -> {
                magnetTime = min(MAGNET_CAP, magnetTime + MAGNET_BASE)
                pushFloat("磁铁 +${MAGNET_BASE.toInt()}s", 0xFFFF6B6B.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_HELMET -> {
                helmetLayers = min(HELMET_MAX, helmetLayers + 1)
                pushFloat(if (helmetLayers >= 2) "头盔 x2" else "头盔", 0xFFFFC21F.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_DOUBLE -> {
                doubleTime = min(DOUBLE_CAP, doubleTime + DOUBLE_BASE)
                pushFloat("加倍 +${DOUBLE_BASE.toInt()}s", 0xFFC77DFF.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_BOOST -> {
                boostTime = min(BOOST_CAP, boostTime + BOOST_BASE)
                pushFloat("冲刺！", 0xFF4DE8FF.toInt())
                emit(EV_BOOST, HAPTIC_MED)
            }
        }
        spawnBurst(e.x, e.y, e.z, floatArrayOf(0.9f, 0.5f, 1f, 1f), 10)
    }

    private fun smashObstacle(e: Entity) {
        missionBonus += 25
        runSmashes++
        bumpQuest(Q_SMASH, 1)
        pushFloat("+25 撞碎", 0xFF4DE8FF.toInt())
        shake = 0.35f
        val col = when (e.kind) {
            OBST_BAR -> floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
            else -> floatArrayOf(0.82f, 0.52f, 0.20f, 1f)
        }
        spawnBurst(e.x, 1.0f, e.z, col, 14)
        emit(EV_SMASH, HAPTIC_MED)
    }

    private fun resetCombo() {
        combo = 0
        comboMult = 1
        comboTimer = 0f
        comboNextAt = COMBO_THRESH[0]
    }

    // ---------- 动态任务 ----------
    private fun rollQuests() {
        quests.clear()
        val tier = playerTier()
        val pool = mutableListOf(
            Q_COINS, Q_DIST, Q_COMBO, Q_JUMP, Q_SLIDE, Q_SMASH, Q_PORTAL, Q_RELIC, Q_BATTLE
        )
        pool.shuffle()
        for (i in 0 until 3) {
            quests.add(makeQuest(pool[i], tier))
        }
    }

    private fun makeQuest(type: Int, tier: Int): Quest {
        // 目标随阶段缩放
        fun scale(a: Int, b: Int, c: Int) = when (tier) { 0 -> a; 1 -> b; else -> c }
        fun scoreR(a: Int, b: Int, c: Int) = scale(a, b, c)
        fun walletR(a: Int, b: Int, c: Int) = scale(a, b, c)
        return when (type) {
            Q_COINS -> Quest(type, scale(30, 55, 90), scoreR(150, 220, 320), walletR(15, 25, 40), "收集金币")
            Q_DIST -> Quest(type, scale(400, 800, 1400), scoreR(180, 280, 400), walletR(18, 30, 50), "奔跑距离")
            Q_COMBO -> Quest(type, scale(20, 40, 70), scoreR(200, 300, 450), walletR(20, 35, 55), "最高连击")
            Q_JUMP -> Quest(type, scale(8, 15, 25), scoreR(120, 180, 260), walletR(12, 20, 35), "跳跃次数")
            Q_SLIDE -> Quest(type, scale(5, 10, 16), scoreR(120, 180, 260), walletR(12, 20, 35), "铲滑次数")
            Q_PORTAL -> Quest(type, scale(1, 2, 3), scoreR(150, 240, 360), walletR(15, 28, 45), "穿越传送门")
            Q_RELIC -> Quest(type, scale(1, 2, 3), scoreR(180, 280, 400), walletR(18, 30, 50), "发现文物")
            Q_BATTLE -> Quest(type, scale(1, 2, 4), scoreR(200, 320, 480), walletR(20, 35, 55), "击倒妖怪")
            else -> Quest(type, scale(3, 6, 12), scoreR(160, 240, 360), walletR(16, 28, 45), "撞碎障碍")
        }
    }

    private fun bumpQuest(type: Int, add: Int) {
        for (q in quests) {
            if (q.done || q.type != type) continue
            if (type == Q_COMBO) continue // 由 sync 处理
            q.progress = (q.progress + add).coerceAtMost(q.target)
            if (q.progress >= q.target) completeQuest(q)
        }
    }

    private fun syncQuestProgress() {
        for (q in quests) {
            if (q.done) continue
            val cur = when (q.type) {
                Q_COINS -> sessionPickupCoins
                Q_DIST -> distance.toInt()
                Q_COMBO -> bestComboRun
                Q_JUMP -> runJumps
                Q_SLIDE -> runSlides
                Q_SMASH -> runSmashes
                Q_PORTAL -> runPortals
                Q_RELIC -> runRelics
                Q_BATTLE -> runChases
                else -> 0
            }
            q.progress = cur.coerceAtMost(q.target)
            if (q.progress >= q.target) completeQuest(q)
        }
    }

    private fun completeQuest(q: Quest) {
        if (q.done) return
        q.done = true
        q.progress = q.target
        missionBonus += q.rewardScore
        totalQuests++
        grantWallet(q.rewardWallet)
        enqueueBanner("任务完成：${q.label} +${q.rewardScore}", 0xFF7DEBA0.toInt(), 2.2f)
        pushFloat("+${q.rewardScore} / 钱包+${q.rewardWallet}", 0xFF7DEBA0.toInt())
        emit(EV_QUEST, HAPTIC_MED)
        // 任务数成就可在局中解锁（奖励进钱包）；累计统计在 settle 时一并持久化
        tryUnlockAchievements(persist = false)
    }

    private fun grantWallet(amount: Int) {
        if (amount <= 0) return
        wallet += amount
        runWalletEarn += amount
    }

    // ---------- 成就 ----------
    private fun tryUnlockAchievements(persist: Boolean, quiet: Boolean = false) {
        var unlocked = false
        for (c in 0 until ACHIEVE_CATS) {
            while (achieveLevels[c] < ACHIEVE_TIERS_PER) {
                val lv = achieveLevels[c]
                val target = ACHIEVE_TARGETS[c][lv]
                if (achieveProgress(c) < target) break
                achieveLevels[c] = lv + 1
                achieveCount = achieveLevels.sum()
                val reward = ACHIEVE_REWARDS[lv]
                grantWallet(reward)
                if (!quiet) {
                    enqueueBanner(
                        "荣誉：${ACHIEVE_NAMES[c]}·${ACHIEVE_TIERS[lv]} +$reward",
                        0xFFFFD426.toInt(), 2.8f
                    )
                    emit(EV_ACHIEVE, HAPTIC_MED)
                }
                unlocked = true
            }
        }
        if (unlocked && persist) persistAll()
    }

    // ---------- 反馈辅助 ----------
    private fun pushFloat(text: String, color: Int) {
        floatTexts.add(FloatText(text, color))
        if (floatTexts.size > 8) floatTexts.removeAt(0)
        lastFloat = text
        lastFloatColor = color
        floatFlash = 1.7f
    }

    private fun spawnBurst(x: Float, y: Float, z: Float, color: FloatArray, n: Int) {
        for (i in 0 until n) {
            particles.add(
                Particle(
                    x, y, z,
                    (Random.nextFloat() - 0.5f) * 6f,
                    Random.nextFloat() * 5f + 2f,
                    (Random.nextFloat() - 0.5f) * 4f,
                    0.45f + Random.nextFloat() * 0.4f,
                    color,
                    0.12f + Random.nextFloat() * 0.12f
                )
            )
        }
        if (particles.size > 80) particles.subList(0, particles.size - 80).clear()
    }

    private fun persistCosmetics() {
        prefs?.edit()
            ?.putInt("wallet", wallet)
            ?.putInt("ownedColors", ownedColors)
            ?.putInt("ownedTrails", ownedTrails)
            ?.putInt("ownedScarves", ownedScarves)
            ?.putInt("ownedHats", ownedHats)
            ?.putInt("catColor", catColor)
            ?.putInt("trailStyle", trailStyle)
            ?.putInt("scarfStyle", scarfStyle)
            ?.putInt("hatStyle", hatStyle)
            ?.apply()
    }

    private fun persistHome() {
        prefs?.edit()
            ?.putInt("wallet", wallet)
            ?.putInt("ownedHouses", ownedHouses)
            ?.putInt("ownedRoofs", ownedRoofs)
            ?.putInt("ownedDecos", ownedDecos)
            ?.putInt("houseStyle", houseStyle)
            ?.putInt("roofStyle", roofStyle)
            ?.putInt("telescopeLevel", telescopeLevel)
            ?.putInt("stargazeReadMask", stargazeReadMask)
            ?.putString("stargazeDayKey", stargazeDayKey)
            ?.putBoolean("stargazeDailyNewDone", stargazeDailyNewDone)
            ?.putInt("stargazeAgeMode", stargazeAgeMode)
            ?.apply()
    }

    private fun persistAll() {
        val p = prefs ?: return
        val ed = p.edit()
            .putInt("high3d", highScore)
            .putInt("highDist", highDistance)
            .putInt("totalCoins", totalCoins)
            .putInt("totalDist", totalDistance)
            .putInt("totalQuests", totalQuests)
            .putInt("bestCombo", bestComboEver)
            .putInt("wallet", wallet)
            .putInt("ownedColors", ownedColors)
            .putInt("ownedTrails", ownedTrails)
            .putInt("ownedScarves", ownedScarves)
            .putInt("ownedHats", ownedHats)
            .putInt("catColor", catColor)
            .putInt("trailStyle", trailStyle)
            .putInt("scarfStyle", scarfStyle)
            .putInt("hatStyle", hatStyle)
            .putInt("totalPortals", totalPortals)
            .putInt("seenUniverses", seenMask)
            .putBoolean("codexRewarded", codexRewarded)
            .putInt("relicMask", relicMask)
            .putBoolean("museumRewarded", museumRewarded)
            .putInt("totalRelicPickups", totalRelicPickups)
            .putInt("ownedHouses", ownedHouses)
            .putInt("ownedRoofs", ownedRoofs)
            .putInt("ownedDecos", ownedDecos)
            .putInt("houseStyle", houseStyle)
            .putInt("roofStyle", roofStyle)
            .putInt("telescopeLevel", telescopeLevel)
            .putInt("stargazeReadMask", stargazeReadMask)
            .putString("stargazeDayKey", stargazeDayKey)
            .putBoolean("stargazeDailyNewDone", stargazeDailyNewDone)
            .putInt("stargazeAgeMode", stargazeAgeMode)
            .putString("characterName", characterName)
        for (i in 0 until ACHIEVE_CATS) ed.putInt("achieveLv$i", achieveLevels[i])
        ed.apply()
    }

    /** 死亡时一次性结算，避免重复累加 */
    private fun settleRun() {
        if (settled) return
        totalCoins += sessionPickupCoins
        totalDistance += distance.toInt()
        if (bestComboRun > bestComboEver) bestComboEver = bestComboRun
        runNewDistRecord = distance.toInt() > highDistance
        runNewScoreRecord = score > highScore
        if (runNewDistRecord) highDistance = distance.toInt()
        if (runNewScoreRecord) highScore = score
        settled = true
        tryUnlockAchievements(persist = false)
        persistAll()
    }

    private fun die() {
        if (immortalMode && BuildConfig.DEBUG) {
            invulnTime = 1f
            shake = 0.45f
            resetCombo()
            spawnBurst(catX, catY + 1f, 0f, floatArrayOf(0.3f, 0.95f, 1f, 1f), 8)
            pushFloat("不死", 0xFF4DE8FF.toInt())
            emit(EV_SHIELD, HAPTIC_HEAVY)
            return
        }
        state = State.DEAD
        deadTime = 0f
        shake = 0.28f
        resetCombo()
        emit(EV_DIE, HAPTIC_HEAVY)
        settleRun()
    }
}
