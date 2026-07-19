package com.vvenv.tomrun

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.ceil
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

        // 速度：慢起步，约 70 秒接近上限
        const val SPEED_START = 12f
        const val SPEED_MAX = 28f
        const val SPEED_RAMP = 0.22f     // 每秒加速
        const val BOOST_MULT = 1.25f

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

        // 任务类型
        const val Q_COINS = 0
        const val Q_DIST = 1
        const val Q_COMBO = 2
        const val Q_JUMP = 3
        const val Q_SLIDE = 4
        const val Q_SMASH = 5
        const val Q_PORTAL = 6

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

        // 成就类别
        const val A_COINS = 0
        const val A_DIST = 1
        const val A_QUESTS = 2
        const val A_COMBO = 3
        const val A_SCORE = 4
        const val ACHIEVE_CATS = 5
        val ACHIEVE_TARGETS = arrayOf(
            intArrayOf(200, 1000, 5000),       // 累计金币
            intArrayOf(2000, 10000, 50000),    // 累计距离
            intArrayOf(5, 25, 100),            // 任务数
            intArrayOf(30, 100, 250),          // 最高连击
            intArrayOf(3000, 8000, 20000)      // 最高分
        )
        val ACHIEVE_NAMES = arrayOf("金币收藏家", "长跑健将", "任务达人", "连击大师", "得分王")
        val ACHIEVE_TIERS = arrayOf("铜", "银", "金")
        val ACHIEVE_REWARDS = intArrayOf(100, 250, 500)

        // 外观：4 色 + 4 尾迹（0 免费）
        const val CAT_COLOR_COUNT = 4
        const val TRAIL_COUNT = 4
        val COLOR_PRICES = intArrayOf(0, 300, 800, 1500)
        val TRAIL_PRICES = intArrayOf(0, 500, 1200, 2500)
        val COLOR_NAMES = arrayOf("蓝灰", "橘黄", "乌黑", "粉红")
        val TRAIL_NAMES = arrayOf("无尾迹", "青色", "金色", "彩虹")
        // 围巾（跑动时飘动）与帽子（吃到头盔时暂被头盔遮住）
        const val SCARF_COUNT = 4
        const val HAT_COUNT = 4
        val SCARF_NAMES = arrayOf("无围巾", "火红围巾", "天青围巾", "星紫围巾")
        val SCARF_PRICES = intArrayOf(0, 400, 1000, 2200)
        val HAT_NAMES = arrayOf("无帽子", "红棒球帽", "青草帽", "金皇冠")
        val HAT_PRICES = intArrayOf(0, 600, 1500, 2800)
        // 商店标签页
        const val SHOP_TAB_COLOR = 0
        const val SHOP_TAB_TRAIL = 1
        const val SHOP_TAB_SCARF = 2
        const val SHOP_TAB_HAT = 3

        // 小屋：房屋主体 / 屋顶 / 院子装饰
        val HOUSE_NAMES = arrayOf("小木屋", "砖瓦房", "双层小楼", "梦幻城堡")
        val HOUSE_PRICES = intArrayOf(0, 800, 2000, 4500)
        val ROOF_NAMES = arrayOf("红屋顶", "青屋顶", "紫屋顶", "金屋顶")
        val ROOF_PRICES = intArrayOf(0, 250, 600, 1200)
        val DECO_NAMES = arrayOf("花坛", "木栅栏", "信箱", "秋千", "猫爬架", "小泳池", "望远镜", "彩旗")
        val DECO_PRICES = intArrayOf(150, 200, 250, 400, 550, 700, 900, 1200)
        const val HOME_TAB_HOUSE = 0
        const val HOME_TAB_ROOF = 1
        const val HOME_TAB_DECO = 2
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

        const val W_SUNNY = 0
        const val W_RAIN = 1
        const val W_SNOW = 2

        const val HAPTIC_LIGHT = 1
        const val HAPTIC_MED = 2
        const val HAPTIC_HEAVY = 3

        // 菜单面板
        const val PANEL_MAIN = 0
        const val PANEL_SHOP = 1
        const val PANEL_ACHIEVE = 2
        const val PANEL_HOME = 3
    }

    class Entity(val kind: Int, val lane: Int, var z: Float, var y: Float = 0f) {
        var x = LANE_X[lane]
        var taken = false
        var spin = Random.nextFloat() * 360f
    }

    class Zip(val lane: Int, var entryZ: Float, val length: Float) {
        val exitZ get() = entryZ - length
    }

    class FloatText(val text: String, val color: Int, var life: Float = 1.1f, var y: Float = 0f)

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

    class Banner(val text: String, val color: Int, var life: Float = 2.4f)

    @Volatile var state = State.READY
    @Volatile var score = 0
    @Volatile var coins = 0          // 本局拾取（计分用）
    @Volatile var highScore = 0
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
    @Volatile var comboFlash = 0f
    @Volatile var comboNextAt = 5   // 距离下一级所需连击数
    private var comboTimer = 0f
    private var comboScore = 0

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
    @Volatile var achieveCount = 0   // 已完成级数总和 /15
    private val bannerQueue = ArrayList<Banner>()
    @Volatile var bannerText = ""
    @Volatile var bannerColor = 0xFFFFD426.toInt()
    @Volatile var bannerFlash = 0f

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
    @Volatile var characterName = DEFAULT_CHARACTER_NAME
        private set
    @Volatile var hasChosenCharacterName = false
        private set

    // 菜单
    @Volatile var menuPanel = PANEL_MAIN
    @Volatile var shopTab = SHOP_TAB_COLOR
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
    @Volatile var recordFlash = 0f
    private var recordDone = false
    private var settled = false

    @Volatile var weather = W_SUNNY
    @Volatile var weatherPrev = W_SUNNY
    @Volatile var weatherBlend = 1f
    private var weatherTimer = 18f + Random.nextFloat() * 15f

    @Volatile var dayPhase = 0.12f + Random.nextFloat() * 0.2f
    private var daySpeed = 1f / 90f

    var lane = 1
    var catX = 0f
    var catY = 0f
    var velY = 0f
    var slideTimer = 0f
    var runPhase = 0f
    private var groundY = 0f
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

    val entities = ArrayList<Entity>()
    val ziplines = ArrayList<Zip>()

    private var prefs: SharedPreferences? = null
    private var sessionPickupCoins = 0  // 本局拾取计入累计统计

    fun attachPrefs(p: SharedPreferences) {
        prefs = p
        highScore = p.getInt("high3d", 0)
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
                achieveLevels[i] = p.getInt("achieveLv$i", 0).coerceIn(0, 3)
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

        ownedHouses = p.getInt("ownedHouses", 1) or 1
        ownedRoofs = p.getInt("ownedRoofs", 1) or 1
        ownedDecos = p.getInt("ownedDecos", 0)
        houseStyle = p.getInt("houseStyle", 0).coerceIn(0, HOUSE_NAMES.size - 1)
        roofStyle = p.getInt("roofStyle", 0).coerceIn(0, ROOF_NAMES.size - 1)
        if (!ownsHouse(houseStyle)) houseStyle = 0
        if (!ownsRoof(roofStyle)) roofStyle = 0
        homeBrowseHouse = houseStyle
        homeBrowseRoof = roofStyle
    }

    fun ownsColor(i: Int) = (ownedColors and (1 shl i)) != 0
    fun ownsTrail(i: Int) = (ownedTrails and (1 shl i)) != 0
    fun ownsScarf(i: Int) = (ownedScarves and (1 shl i)) != 0
    fun ownsHat(i: Int) = (ownedHats and (1 shl i)) != 0
    fun ownsHouse(i: Int) = (ownedHouses and (1 shl i)) != 0
    fun ownsRoof(i: Int) = (ownedRoofs and (1 shl i)) != 0
    fun ownsDeco(i: Int) = (ownedDecos and (1 shl i)) != 0
    fun seenUniverse(i: Int) = (seenMask and (1 shl i)) != 0

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

    /** 小屋繁荣值：已购项目总价值 */
    fun homeScore(): Int {
        var sum = 0
        for (i in 1 until HOUSE_PRICES.size) if (ownsHouse(i)) sum += HOUSE_PRICES[i]
        for (i in 1 until ROOF_PRICES.size) if (ownsRoof(i)) sum += ROOF_PRICES[i]
        for (i in DECO_PRICES.indices) if (ownsDeco(i)) sum += DECO_PRICES[i]
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
        0 -> "装扮小屋可获开局奖励"
        1 -> "开局奖励：磁铁5s"
        2 -> "开局奖励：磁铁+头盔"
        else -> "开局奖励：磁铁+头盔+加倍"
    }

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
            return "已装备 ${COLOR_NAMES[i]}"
        }
        val price = COLOR_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedColors = ownedColors or (1 shl i)
        catColor = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        return "购买成功：${COLOR_NAMES[i]}"
    }

    @Synchronized fun buyOrEquipTrail(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseTrail
        if (ownsTrail(i)) {
            trailStyle = i
            persistCosmetics()
            return "已装备 ${TRAIL_NAMES[i]}"
        }
        val price = TRAIL_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedTrails = ownedTrails or (1 shl i)
        trailStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        return "购买成功：${TRAIL_NAMES[i]}"
    }

    @Synchronized fun buyOrEquipScarf(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseScarf
        if (ownsScarf(i)) {
            scarfStyle = i
            persistCosmetics()
            return "已戴上 ${SCARF_NAMES[i]}"
        }
        val price = SCARF_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedScarves = ownedScarves or (1 shl i)
        scarfStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        return "购买成功：${SCARF_NAMES[i]}"
    }

    @Synchronized fun buyOrEquipHat(): String {
        if (state == State.RUNNING) return ""
        val i = shopBrowseHat
        if (ownsHat(i)) {
            hatStyle = i
            persistCosmetics()
            return "已戴上 ${HAT_NAMES[i]}"
        }
        val price = HAT_PRICES[i]
        if (wallet < price) return "金币不足（需 $price）"
        wallet -= price
        ownedHats = ownedHats or (1 shl i)
        hatStyle = i
        persistCosmetics()
        emit(EV_BUY, HAPTIC_MED)
        return "购买成功：${HAT_NAMES[i]}"
    }

    // ---------- 商店标签页 ----------
    @Synchronized fun switchShopTab(tab: Int) {
        if (state == State.RUNNING) return
        shopTab = tab.coerceIn(SHOP_TAB_COLOR, SHOP_TAB_HAT)
    }

    @Synchronized fun browseShop(delta: Int) {
        when (shopTab) {
            SHOP_TAB_COLOR -> browseColor(delta)
            SHOP_TAB_TRAIL -> browseTrail(delta)
            SHOP_TAB_SCARF -> {
                if (state == State.RUNNING) return
                shopBrowseScarf = (shopBrowseScarf + delta + SCARF_COUNT) % SCARF_COUNT
            }
            else -> {
                if (state == State.RUNNING) return
                shopBrowseHat = (shopBrowseHat + delta + HAT_COUNT) % HAT_COUNT
            }
        }
    }

    fun buyOrEquipShop(): String = when (shopTab) {
        SHOP_TAB_COLOR -> buyOrEquipColor()
        SHOP_TAB_TRAIL -> buyOrEquipTrail()
        SHOP_TAB_SCARF -> buyOrEquipScarf()
        else -> buyOrEquipHat()
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

    // ---------- 小屋 ----------
    @Synchronized fun switchHomeTab(tab: Int) {
        if (state == State.RUNNING) return
        homeTab = tab.coerceIn(HOME_TAB_HOUSE, HOME_TAB_DECO)
    }

    @Synchronized fun browseHome(delta: Int) {
        if (state == State.RUNNING) return
        when (homeTab) {
            HOME_TAB_HOUSE -> homeBrowseHouse =
                (homeBrowseHouse + delta + HOUSE_NAMES.size) % HOUSE_NAMES.size
            HOME_TAB_ROOF -> homeBrowseRoof =
                (homeBrowseRoof + delta + ROOF_NAMES.size) % ROOF_NAMES.size
            else -> homeBrowseDeco =
                (homeBrowseDeco + delta + DECO_NAMES.size) % DECO_NAMES.size
        }
    }

    /** 购买 / 装备当前浏览的小屋项目；返回提示文案 */
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
                return "购买成功：${ROOF_NAMES[i]}"
            }
            else -> {
                val i = homeBrowseDeco
                if (ownsDeco(i)) return "${DECO_NAMES[i]} 已摆放在院子里"
                val price = DECO_PRICES[i]
                if (wallet < price) return "金币不足（需 $price）"
                wallet -= price
                ownedDecos = ownedDecos or (1 shl i)
                persistHome()
                emit(EV_BUY, HAPTIC_MED)
                return "已摆上：${DECO_NAMES[i]}"
            }
        }
    }

    fun consumeHaptic(): Int {
        val h = hapticPulse
        hapticPulse = 0
        return h
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
            if (lv >= 3) continue
            val target = ACHIEVE_TARGETS[c][lv]
            val cur = achieveProgress(c)
            val need = target - cur
            if (need < bestNeed) {
                bestNeed = need
                bestCat = c
                bestPct = (cur.toFloat() / target).coerceIn(0f, 1f)
            }
        }
        if (bestCat < 0) return "成就已全部解锁"
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
        else -> 0
    }

    // ---------- 输入 ----------
    /** Debug 测试开关：仅当前进程内有效，重启后自动关闭。 */
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

    @Synchronized fun onSwipeUp() { if (state == State.RUNNING) jump() }

    @Synchronized fun onSwipeDown() {
        if (state != State.RUNNING) return
        if (riding != null) {
            riding = null
            velY = -14f
        } else {
            slideTimer = SLIDE_TIME
            if (!onGround) velY = -14f
            runSlides++
            bumpQuest(Q_SLIDE, 1)
            emit(EV_SLIDE, HAPTIC_LIGHT)
        }
    }

    @Synchronized fun onSwipeLeft() {
        if (state == State.RUNNING && lane > 0) { lane--; riding = null }
    }

    @Synchronized fun onSwipeRight() {
        if (state == State.RUNNING && lane < 2) { lane++; riding = null }
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
        bannerQueue.clear()
        quests.clear()
        lane = 1; catX = 0f; catY = 0f; velY = 0f
        groundY = 0f
        slideTimer = 0f; runPhase = 0f
        baseSpeed = SPEED_START; speed = SPEED_START
        distance = 0f; score = 0; coins = 0; runTime = 0f
        deadTime = 0f
        magnetTime = 0f; doubleTime = 0f; boostTime = 0f; helmetLayers = 0
        riding = null; scoreBoost = 0f; invulnTime = 0f
        recordFlash = 0f; recordDone = false; settled = false
        combo = 0; comboMult = 1; comboTimer = 0f; comboFlash = 0f; comboScore = 0
        comboNextAt = COMBO_THRESH[0]
        bestComboRun = 0; missionBonus = 0
        runJumps = 0; runSlides = 0; runSmashes = 0
        runWalletEarn = 0; sessionPickupCoins = 0
        bannerFlash = 0f; bannerText = ""
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
        rollQuests()
        // 小屋能量：开局按等级赠送 buff
        val hl = homeLevel()
        if (hl >= 1) magnetTime = 5f
        if (hl >= 2) helmetLayers = 1
        if (hl >= 3) doubleTime = 5f
        if (hl >= 1) enqueueBanner("小屋能量 Lv$hl！${homeLevelDesc(hl)}", 0xFF7DEBA0.toInt(), 2.0f)
        var z = -45f
        while (z > SPAWN_Z) {
            spawnWave(z, early = true)
            z -= 24f + Random.nextFloat() * 14f
        }
        gapRemaining = nextGap()
    }

    /** 反应时间间距：前期 ~1.7s，后期 ~0.95s */
    private fun nextGap(): Float {
        val t = (distance / 2500f).coerceIn(0f, 1f)
        val react = 1.7f - t * 0.75f
        val jitter = 0.85f + Random.nextFloat() * 0.3f
        return (baseSpeed * react * jitter).coerceIn(14f, 48f)
    }

    // ---------- 主更新 ----------
    @Synchronized fun update(dt: Float) {
        if (paused) return
        tickDayNight(dt)
        tickFeedback(dt)
        tickBanners(dt)
        if (shake > 0f) shake = (shake - dt * 5f).coerceAtLeast(0f)
        if (portalFlash > 0f) portalFlash -= dt
        if (universeBlend < 1f) universeBlend = min(1f, universeBlend + dt / 1.5f)
        if (state == State.DEAD) {
            deadTime += dt
            return
        }
        tickWeather(dt)
        runPhase += dt * speed * 0.9f
        if (state != State.RUNNING) return

        runTime += dt
        baseSpeed = min(SPEED_START + SPEED_RAMP * runTime, SPEED_MAX)
        // 前期略缓加速曲线（ease-out 近似）
        if (runTime < 70f) {
            val u = runTime / 70f
            val eased = 1f - (1f - u) * (1f - u)
            baseSpeed = SPEED_START + (SPEED_MAX - SPEED_START) * eased
        }
        speed = if (boosting) baseSpeed * BOOST_MULT else baseSpeed
        val dz = speed * dt
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
        if (comboFlash > 0f) comboFlash -= dt
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
            catY += (RIDE_Y - catY) * min(1f, dt * 8f)
            velY = 0f
            slideTimer = 0f
            if (r.exitZ >= 0f || r !in ziplines) riding = null
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
                    emit(EV_ZIP, HAPTIC_MED)
                    break
                }
            }
        }

        val it = entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.z += dz
            if (magnetTime > 0f && e.kind == COIN && e.z > -26f && !e.taken) {
                val pull = min(1f, dt * 8f)
                e.x += (catX - e.x) * pull
                e.y += (catY + 1f - e.y) * pull
                e.z += (0f - e.z) * min(1f, dt * 4f)
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
            spawnWave(SPAWN_Z - gapRemaining, early = false)
            gapRemaining += nextGap()
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

        checkCollision()
        syncQuestProgress()
        // 计分：里程 + 加倍里程 + 金币基础分 + 连击额外 + 任务分
        score = (distance + scoreBoost).toInt() + coins * 10 + comboScore + missionBonus

        if (recordFlash > 0f) recordFlash -= dt
        if (!recordDone && highScore > 0 && score > highScore) {
            recordDone = true
            recordFlash = 2.6f
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

    private fun tickBanners(dt: Float) {
        if (bannerFlash > 0f) {
            bannerFlash -= dt
            if (bannerFlash <= 0f && bannerQueue.isNotEmpty()) {
                val b = bannerQueue.removeAt(0)
                bannerText = b.text
                bannerColor = b.color
                bannerFlash = b.life
            }
        } else if (bannerQueue.isNotEmpty()) {
            val b = bannerQueue.removeAt(0)
            bannerText = b.text
            bannerColor = b.color
            bannerFlash = b.life
        }
    }

    private fun enqueueBanner(text: String, color: Int, life: Float = 2.4f) {
        if (bannerFlash <= 0f && bannerQueue.isEmpty()) {
            bannerText = text
            bannerColor = color
            bannerFlash = life
        } else {
            bannerQueue.add(Banner(text, color, life))
            if (bannerQueue.size > 6) bannerQueue.removeAt(0)
        }
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
        portalFlash = 1.0f
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
        }
        pushFloat("穿越！", 0xFF4DE8FF.toInt())
        shake = 0.30f
        spawnBurst(catX, 1.4f, 0f, floatArrayOf(0.6f, 0.85f, 1f, 1f), 16)
        emit(EV_PORTAL, HAPTIC_HEAVY)
    }

    // ---------- 生成 ----------
    private fun spawnZipline() {
        val laneZ = Random.nextInt(3)
        val length = 30f + Random.nextFloat() * 50f
        ziplines.add(Zip(laneZ, SPAWN_Z, length))
        var cz = SPAWN_Z - 8f
        while (cz > SPAWN_Z - length + 4f) {
            entities.add(Entity(COIN, laneZ, cz, CABLE_H - 1.1f))
            cz -= 4f
        }
    }

    private fun spawnWave(zBase: Float, early: Boolean) {
        wavesSincePower++
        val freeLanes = mutableListOf(0, 1, 2)
        val barOpen = distance > 180f && !early
        val rampOpen = distance > 450f && !early
        val dense = distance > 900f

        val r = Random.nextFloat()
        when {
            rampOpen && r < 0.14f -> spawnRampWave(zBase)
            barOpen && r < (if (dense) 0.38f else 0.32f) -> {
                val l = Random.nextInt(3)
                entities.add(Entity(OBST_BAR, l, zBase))
                coinRow(l, zBase)
            }
            r < 0.55f -> {
                // 最多堵 2 道，始终留一条可走
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_BLOCK, freeLanes[i], zBase))
                coinRow(freeLanes.last(), zBase)
            }
            r < 0.82f -> {
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_LOW, freeLanes[i], zBase))
                coinArc(freeLanes[0], zBase)
            }
            else -> {
                coinRow(Random.nextInt(3), zBase)
                coinRow(Random.nextInt(3), zBase - 8f)
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
            entities.add(Entity(COIN, rampLane, zBase + localZ, surfaceY + 1f))
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

    private fun coinRow(lane: Int, zBase: Float) {
        for (i in 0 until 5) entities.add(Entity(COIN, lane, zBase - 1.6f * i, 1.0f))
    }

    private fun coinArc(lane: Int, zBase: Float) {
        val ys = floatArrayOf(1.0f, 1.8f, 2.3f, 1.8f, 1.0f)
        for (i in ys.indices) entities.add(Entity(COIN, lane, zBase + 3.2f - 1.6f * i, ys[i]))
    }

    // ---------- 碰撞 / 拾取 ----------
    private fun checkCollision() {
        val catCenterY = catY + (if (sliding) 0.4f else 1.0f)
        val onZip = riding != null
        for (e in entities) {
            if (e.taken) continue
            when (e.kind) {
                COIN -> {
                    val radius = if (magnetTime > 0f) 1.6f else 1.15f
                    val dx = e.x - catX
                    val dy = e.y - catCenterY
                    if (abs(e.z) < 1.2f && dx * dx + dy * dy < radius * radius) {
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
            comboFlash = 1.4f
            emit(EV_COMBO, HAPTIC_MED)
        } else {
            emit(EV_COIN, HAPTIC_LIGHT)
        }
        bumpQuest(Q_COINS, 1)
        bumpQuest(Q_COMBO, 0) // 用 sync 刷新
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
            Q_COINS, Q_DIST, Q_COMBO, Q_JUMP, Q_SLIDE, Q_SMASH, Q_PORTAL
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
    private fun tryUnlockAchievements(persist: Boolean) {
        var unlocked = false
        for (c in 0 until ACHIEVE_CATS) {
            while (achieveLevels[c] < 3) {
                val lv = achieveLevels[c]
                val target = ACHIEVE_TARGETS[c][lv]
                if (achieveProgress(c) < target) break
                achieveLevels[c] = lv + 1
                achieveCount = achieveLevels.sum()
                val reward = ACHIEVE_REWARDS[lv]
                grantWallet(reward)
                enqueueBanner(
                    "成就：${ACHIEVE_NAMES[c]}·${ACHIEVE_TIERS[lv]} +$reward",
                    0xFFFFD426.toInt(), 2.8f
                )
                emit(EV_ACHIEVE, HAPTIC_MED)
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
        floatFlash = 0.9f
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
            ?.apply()
    }

    private fun persistAll() {
        val p = prefs ?: return
        val ed = p.edit()
            .putInt("high3d", highScore)
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
            .putInt("ownedHouses", ownedHouses)
            .putInt("ownedRoofs", ownedRoofs)
            .putInt("ownedDecos", ownedDecos)
            .putInt("houseStyle", houseStyle)
            .putInt("roofStyle", roofStyle)
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
        if (score > highScore) highScore = score
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
