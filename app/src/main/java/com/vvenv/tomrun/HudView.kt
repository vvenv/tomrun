package com.vvenv.tomrun

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.InputFilter
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 像素风中文 HUD：直接绘制 + 手势识别。
 * 菜单含主界面 / 家（装扮；藏品、荣誉为全屏图鉴）。
 * 文字使用 Fusion Pixel 12px（简体），字号取 12 的整数倍以保持点阵清晰。
 */
class HudView(context: Context, private val game: Game) : View(context) {

    init {
        // 家页面布局单一事实来源：若 assets/home_layout.json 存在则覆盖默认坐标
        LayoutConfig.load(context)
    }

    /**
     * 护眼画笔：所有 HUD 颜色出图前统一分级，观感与 3D 世界（GameRenderer.grade）一致。
     * 覆盖 setColor 而不是逐个改色值常量，这样新写的绘制代码会自动继承，
     * 各处按元素含义调好的色相/明度关系也原样保留。
     */
    private class EyeSafePaint : Paint() {
        override fun setColor(color: Int) = super.setColor(EyeComfort.eyeSafe(color))
    }

    private val pixelTypeface: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/fusion-pixel-12px.ttf")
    }.getOrElse { Typeface.DEFAULT_BOLD }

    private val textPaint = EyeSafePaint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
        isSubpixelText = false
        isLinearText = false
        isFakeBoldText = false
        typeface = pixelTypeface
    }
    /** 压暗遮罩与穿越白闪：只用带 alpha 的黑/白，不参与分级 */
    private val dimPaint = Paint()
    private val btnPaint = EyeSafePaint().apply {
        isAntiAlias = false
        isDither = false
    }

    private var downX = 0f
    private var downY = 0f
    private var consumed = false
    private var toast = ""
    private var toastLife = 0f
    private var secretTapCount = 0
    private var secretTapDeadline = 0L
    private var renameDialog: AlertDialog? = null

    // 离屏按钮区域
    private val btnHome = RectF()
    /** 结算屏那一行「纪录榜 ›」的热区；READY 菜单上为空 */
    private val btnLeaderboard = RectF()
    private val btnLbClose = RectF()
    private val btnLbPrev = RectF()
    private val btnLbNext = RectF()
    private val btnLbScopeLocal = RectF()
    private val btnLbScopeGlobal = RectF()
    private val btnLeaveHome = RectF()
    /** 庭院远景地标：藏品馆 / 荣誉墙，取代原先顶栏的两个按钮 */
    private val hitMuseum = RectF()
    private val hitHonorWall = RectF()
    /** 家页面顶栏底边（含隐藏时的提示行），远景地标据此收缩 */
    private var homeBarBottom = 0f
    /** 右下角钱包铭牌，与左下角「出门」成对 */
    private val homeWalletPlate = RectF()
    private var homeWalletLabelSize = 0f
    private val homeOverlayPanel = RectF()
    private val btnCatalogClose = RectF()
    private val relicDetailFrame = RectF()
    private val hitHouse = RectF()
    private val hitCosmeticColor = RectF()
    private val hitCosmeticTrail = RectF()
    private val hitCosmeticHat = RectF()
    private var showHelp = false
    /** 玩法说明本次进程已自动弹过：不加它的话关掉后下一帧又会被首局条件重新拉起 */
    private var helpAutoShown = false
    private var showLeaderboard = false
    private var leaderboardTab = 0
    private var leaderboardScope = LB_SCOPE_LOCAL
    private var homeSubView = HOME_SUB_SCENE
    private var homeEditing = false
    private var museumPage = 0
    private var honorPage = 0
    /** 藏品大图：-1 表示未打开 */
    private var museumDetailId = -1
    private val museumTileHits = Array(MUSEUM_PER_PAGE) { RectF() }
    /**
     * 选物面板的格子：分类 tab + 该分类内的下标 + 屏幕矩形，三条并行数组。
     * 一屏最多 4 行 × 4 件（猫装扮）或 1 行 × 8 件（摆件），留 20 个位子足够。
     */
    private val pickPanelRect = RectF()
    private val pickChipRects = Array(20) { RectF() }
    private val pickChipTab = IntArray(20)
    private val pickChipIdx = IntArray(20)
    private var pickChipCount = 0
    /** 已预览、等第二下确认买下的那一格；-1 表示没有 */
    private var pickArmedTab = -1
    private var pickArmedIdx = -1
    private val hitHomeTitle = RectF()
    /** 世界切换：点房门开一张已解锁世界的清单，取代庭院里常驻的左右箭头 */
    private var showHomeWorlds = false
    private val hitDoor = RectF()
    private var lastDoorCx = 0f
    private val homeWorldRowHits = Array(Game.UNIVERSE_COUNT) { RectF() }
    private val homeWorldRowIds = IntArray(Game.UNIVERSE_COUNT)
    private var homeWorldRowCount = 0
    /** 家园图层：游戏内显示/隐藏各元素 */
    private var showHomeLayers = false
    private var homeLayerScroll = 0f
    private val btnHomeLayers = RectF()
    private val btnHomeLayersClose = RectF()
    private val btnHomeLayersShowAll = RectF()
    private val homeLayerPanel = RectF()
    private val homeLayerRowHits = ArrayList<Pair<String, RectF>>()
    private val btnPause = RectF()
    private val btnResume = RectF()
    private val btnQuit = RectF()
    private var homePhase = 0f

    // 庭院猫 AI：站立张望 / 散步 / 与装饰互动 / 打盹。坐标是**世界单位**（见 [HomeYard]）
    private var catState = CAT_IDLE
    private var catTimer = 1.5f
    private var catX = -3.5f
    private var catZ = 4.4f
    private var catY = 0f             // 相对台地面的高度：上爬架 / 下水时才不为 0
    private var catTargetX = -3.5f
    private var catTargetZ = 4.4f
    private var catYaw = 180f         // 0 背对镜头，180 面朝镜头
    private var catDeco = -1          // 互动目标装饰，-1 为无
    private var catStage = 0          // 猫爬架分段动作
    private var catStageT = 0f
    private val catRnd = java.util.Random()
    private var yardEventTimer = 6f
    private var pendingYardEvent = YARD_EVENT_NONE
    private var butterflyVisible = false
    private var butterflySpawnTimer = 10f
    private var butterflyLife = 0f
    private var butterflyX = 0f
    private var butterflyZ = 4f
    private var butterflyY = 2.2f
    private var butterflyDir = 1f
    private var petTimer = 0f
    private var petCount = 0
    private val yardCatHit = RectF()
    private val yardTelescopeHit = RectF()
    private val hitHomeEnergy = RectF()
    private val hitHomeReward = RectF()
    private val homeDragHits = HashMap<String, RectF>()
    private val dragHitScratch = RectF()
    private val yardSceneHit = RectF()
    private val butterflyHit = RectF()
    /** HUD 的像素尺度，飘字等屏幕小物件按它排版 */
    private var yardSceneS = 1f
    // 庭院手势：双击追手 / 长按撸猫
    private var yardLastTapX = 0f
    private var yardLastTapY = 0f
    private var yardLastTapTime = 0L
    private var yardPendingTapX = 0f
    private var yardPendingTapY = 0f
    private var yardFingerDown = false
    private var yardFingerX = 0f
    private var yardFingerY = 0f
    private var yardFingerDownTime = 0L
    private var yardLongPressTriggered = false
    private var handTargetX = 0f
    private var handTargetZ = 0f
    private var handMarkerLife = 0f
    /** 屏幕点打到地面的世界坐标暂存（拖放 / 叫猫共用），避免每次触摸都新建 */
    private val groundHit = FloatArray(2)
    private var strokeActive = false
    private var strokeHoldT = 0f
    private var strokeCoinTimer = 0f
    private var strokeCoinsGiven = 0
    private var strokeCoinCooldown = 0f
    private val yardFloaters = ArrayList<YardFloater>()
    private var homeDragId: String? = null
    private var homeDragActive = false
    private var homeDragStartFingerX = 0f
    private var homeDragStartFingerY = 0f
    private var homeDragStartSceneX = 0f
    private var homeDragStartSceneY = 0f
    private val homeDragStartOff = FloatArray(2)
    /** 庭院装饰优先于猫/房屋等大热区，避免秋千被猫遮挡后无法再拖 */
    private val homeYardDragOrder = arrayOf(
        "garden", "mailbox", "swing", "perch", "telescope", "pool", "fence"
    )
    private val homeDragOrder = arrayOf(
        "leave", "wallet", "name", "energy", "reward",
        "cat", "museum", "honor", "house"
    )
    /**
     * 家园 UI 文字（提示语、名牌等）。
     * 商店栏的名称/价格/状态三段文字随选物面板一起删了——面板自带排版，
     * 不再需要把三个字段拆开各自摆位。
     */
    private val homeUiTextDragOrder = arrayOf(
        "homeHint", "museumLabel", "honorLabel"
    )
    private val ownedDecoDragId = arrayOf(
        "garden", "fence", "mailbox", "swing", "perch", "pool", "telescope"
    )

    private fun decoElementId(deco: Int): String? = when (deco) {
        0 -> "garden"
        1 -> "fence"
        2 -> "mailbox"
        3 -> "swing"
        4 -> "perch"
        5 -> "pool"
        6 -> "telescope"
        else -> null
    }

    private fun homeShown(id: String) = game.homeElVisible(id)
    private val yardSingleTapRunnable = Runnable {
        handleYardSingleTap(yardPendingTapX, yardPendingTapY)
    }
    private var showStargazing = false
    private var stargazeBody = 0
    private val btnStargazeL = RectF()
    private val btnStargazeR = RectF()
    private val btnStargazeUpgrade = RectF()
    private var stargazeDoneHint = ""
    /** 刘海 / 状态栏 / 手势条等安全区（像素） */
    private var safeL = 0f
    private var safeT = 0f
    private var safeR = 0f
    private var safeB = 0f

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        applySafeInsets(insets)
        return super.onApplyWindowInsets(insets)
    }

    private fun applySafeInsets(insets: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            safeL = bars.left.toFloat()
            safeT = bars.top.toFloat()
            safeR = bars.right.toFloat()
            safeB = bars.bottom.toFloat()
        } else {
            @Suppress("DEPRECATION")
            safeL = insets.systemWindowInsetLeft.toFloat()
            @Suppress("DEPRECATION")
            safeT = insets.systemWindowInsetTop.toFloat()
            @Suppress("DEPRECATION")
            safeR = insets.systemWindowInsetRight.toFloat()
            @Suppress("DEPRECATION")
            safeB = insets.systemWindowInsetBottom.toFloat()
            if (Build.VERSION.SDK_INT >= 28) {
                insets.displayCutout?.let { cut ->
                    safeL = max(safeL, cut.safeInsetLeft.toFloat())
                    safeT = max(safeT, cut.safeInsetTop.toFloat())
                    safeR = max(safeR, cut.safeInsetRight.toFloat())
                    safeB = max(safeB, cut.safeInsetBottom.toFloat())
                }
            }
        }
        // immersive 下 systemBars 可能为 0，仍给顶/底留最低垫高，避开刘海与 Home 指示条
        if (safeT < 1f) {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) safeT = resources.getDimensionPixelSize(resId).toFloat()
            if (safeT < 1f) safeT = 24f * resources.displayMetrics.density
        }
        if (safeB < 1f) {
            safeB = 16f * resources.displayMetrics.density
        }
    }

    companion object {
        /** Fusion Pixel 设计基准；textSize 必须是其整数倍。 */
        private const val FONT_PX = 12
        /**
         * 文物名牌淡入区间，按 relicHudScale（越大越近）取值。
         * 0.50 约合身前 38 个单位，0.75 约合 15 个单位——
         * 也就是「隔着一两波障碍时完全透明，快到跟前才看清是什么」。
         */
        private const val RELIC_LABEL_FADE_LO = 0.50f
        private const val RELIC_LABEL_FADE_HI = 0.75f
        /** 可以在其后断行的标点：它们只能留在行尾，不能顶到下一行行首 */
        private const val LINE_BREAK_AFTER = "，。、；：？！,.;:?!"
        /** 藏品笔记每条停留秒数：够读完两行，又不至于久到像在说教 */
        private const val RELIC_NOTE_SECS = 7f
        private const val RELIC_NOTE_TITLE = 0xFFE8DCC0.toInt()
        private const val RELIC_NOTE_BODY = 0xFFBFC8CE.toInt()
        private const val HOME_DRAG_SNAP_PX = 10f

        private val COLOR_CHIPS = intArrayOf(
            0xFF8594B3.toInt(), 0xFFF29E42.toInt(), 0xFF4D4D59.toInt(), 0xFFF5A8C1.toInt()
        )
        private val TRAIL_CHIPS = intArrayOf(
            0xFF888888.toInt(), 0xFF4DE8FF.toInt(), 0xFFFFD54A.toInt(), 0xFFFF66CC.toInt()
        )
        /** 宇宙 HUD 主题色，与渲染层配色呼应 */
        private val UNI_HUD = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFF4DD8FF.toInt(), 0xFFFFE08A.toInt(),
            0xFFFF7A45.toInt(), 0xFFFFA1C9.toInt(), 0xFFB48CFF.toInt()
        )
        /** 图鉴内页的房间主题：博物馆展厅 / 荣誉厅 */
        private const val HALL_MUSEUM = 0
        private const val HALL_HONOR = 1
        private const val HOME_SUB_SCENE = 0
        private const val HOME_SUB_COLLECTION = 1
        private const val HOME_SUB_HONOR = 2
        /** 选物面板的行编排：一个入口摊开一族货，分类降级成行号而不是要先点的 tab */
        private val PICK_ROWS_HOUSE = arrayOf(
            PickRow(Game.HOME_TAB_HOUSE, "小屋"),
            PickRow(Game.HOME_TAB_ROOF, "屋顶")
        )
        private val PICK_ROWS_DECO = arrayOf(PickRow(Game.HOME_TAB_DECO, "摆件"))
        private val PICK_ROWS_CAT = arrayOf(
            PickRow(Game.HOME_TAB_COLOR, "配色"),
            PickRow(Game.HOME_TAB_TRAIL, "光迹"),
            PickRow(Game.HOME_TAB_SCARF, "围巾"),
            PickRow(Game.HOME_TAB_HAT, "帽子")
        )
        /** 跑酷通知带首行高度（连击 / 横幅 / 纪录共用） */
        private const val RUN_BANNER_Y = 0.283f
        /** 通知带行距：按最大字号（新纪录 48）留足，换行不会互相压字 */
        private const val NOTICE_ROW_H = 54f
        /** 通知带缩字下限：2 档 = 24px，再小点阵中文就糊了 */
        private const val NOTICE_MIN_STEPS = 2
        /** 藏品 tile 网格；荣誉仍按行分页 */
        private const val MUSEUM_COLS = 4
        private const val MUSEUM_ROWS = 4
        private const val MUSEUM_PER_PAGE = MUSEUM_COLS * MUSEUM_ROWS
        private const val HONOR_PER_PAGE = 8
        /** 全屏图鉴：顶栏 + 底栏固定占位，中间才是内容 */
        private const val OVERLAY_HEADER_H = 96f
        private const val OVERLAY_FOOTER_H = 128f
        /** 图鉴类浮层收尾行：关闭按钮尺寸 + 与面板底边的留白，排行榜/藏品/荣誉共用同一条基线 */
        private const val CLOSE_BTN_SIZE = 44f
        private const val CLOSE_ROW_MARGIN = 14f
        private const val LB_SCOPE_LOCAL = 0
        private const val LB_SCOPE_GLOBAL = 1
        /** 纪录榜顶栏比通用图鉴多一行大标题，需要单独更高的高度 */
        private const val LEADERBOARD_HEADER_H = 132f
        /** 纪录榜底栏比通用图鉴多了本地/全服切换、页码，偶尔还有同步状态行 */
        private const val LEADERBOARD_FOOTER_H = 210f   // 底部多留一行放右下角关闭按钮，方便单手操作
        /** 纪录榜条目：默认单行更紧凑；一类里只要有一条挤不下就整类退到双行 */
        private const val ROW_H_SINGLE = 44f
        private const val ROW_H_DOUBLE = 52f
        private const val ROW_NAME_W = 0.34f
        private const val ROW_DETAIL_W = 0.14f
        private const val ROW_VALUE_W = 0.28f
        private const val ROW_NAME_MIN = 16f
        private const val ROW_DETAIL_MIN = 12f
        private const val ROW_VALUE_MIN = 16f
        private const val TILE_COMMON_BG = 0xFFE2F0E6.toInt()
        private const val TILE_RARE_BG = 0xFFDCECF4.toInt()
        private const val TILE_LEGEND_BG = 0xFFF5E8C4.toInt()
        /** 亮色浮窗配色（藏品 / 荣誉共用） */
        private const val LIGHT_PANEL = 0xFFF7F1E6.toInt()
        private const val LIGHT_PANEL_EDGE = 0xFF6B4E28.toInt()
        private const val LIGHT_DIVIDER = 0x55B07A18
        private const val LIGHT_TITLE = 0xFF5C3D0A.toInt()
        private const val LIGHT_SUB = 0xFF2F4A66.toInt()
        private const val LIGHT_ROW = 0xFFFFFCF5.toInt()
        private const val LIGHT_ROW_EDGE = 0xFFD9CDB8.toInt()
        private const val LIGHT_TEXT = 0xFF1E1E24.toInt()
        private const val LIGHT_MUTED = 0xFF4A5560.toInt()
        private const val LIGHT_HINT = 0xFF5A6068.toInt()
        private const val LIGHT_BTN = 0xFFF0E4C8.toInt()
        private const val LIGHT_BTN_EDGE = 0xFF6B4E28.toInt()
        private const val LIGHT_LOCKED = 0xFF6E7680.toInt()
        /** 亮色底上的稀有度字色（比 HUD 霓虹色更深） */
        private const val LIGHT_RELIC_COMMON = 0xFF2E7A4A.toInt()
        private const val LIGHT_RELIC_RARE = 0xFF1A6F98.toInt()
        private const val LIGHT_RELIC_LEGEND = 0xFFA06A00.toInt()
        /** 商店 / 小屋面板标题：同字号、同相对返回按钮的纵坐标 */
        private const val PANEL_TITLE_SIZE = 48f
        private const val PANEL_TITLE_DY = 64f
        private const val PANEL_SUB_DY = 104f
        /** 围巾颜色，与 3D 渲染配色呼应；0 为"无"占位 */
        private val SCARF_CHIPS = intArrayOf(
            0xFF888888.toInt(), 0xFFF23F3F.toInt(), 0xFF4DD8F2.toInt(), 0xFFA673FF.toInt()
        )
        private const val HAT_CAP = 0xFFE04545.toInt()
        private const val HAT_CAP_DK = 0xFFA63030.toInt()
        private const val HAT_STRAW = 0xFFD9C46A.toInt()
        private const val HAT_STRAW_BAND = 0xFF4E9142.toInt()
        private const val HAT_GOLD = 0xFFF2C14E.toInt()
        private const val HAT_RUBY = 0xFFE0345A.toInt()

        // 庭院猫状态与姿势
        private const val CAT_IDLE = 0
        private const val CAT_WALK = 1
        private const val CAT_PLAY = 2
        private const val CAT_NAP = 3
        private const val CAT_CHASE = 4
        private const val CAT_PET = 5
        private const val CAT_CHASE_HAND = 6
        private const val POSE_STAND = 0
        private const val POSE_WALK = 1
        private const val POSE_SIT = 2
        private const val POSE_NAP = 3
        private const val POSE_SNIFF = 4
        private const val POSE_LOOKUP = 5
        private const val POSE_JUMP = 6
        private const val POSE_SWIM = 7
        private const val POSE_PET = 8
        private const val YARD_EVENT_NONE = -1
        private const val YARD_EVENT_COINS = 0
        private const val YARD_DOUBLE_TAP_MS = 360L
        private const val YARD_LONG_PRESS_MS = 520L
        private const val YARD_DRAG_SLOP = 12f
        private const val STROKE_MAX_COINS = 4
        private const val STROKE_COIN_INTERVAL = 0.82f
        private const val STROKE_COOLDOWN = 48f

        // 泳池几何（相对庭院中心 cx / 地面线 gy 的偏移，乘 s 使用）；
        // 绘制、游泳动画、靠近目标三处共用，改这里即整体挪动，猫会自动跟上
        // 泳池几何已迁到 LayoutConfig.cur.pool（assets/home_layout.json 可覆盖）
    }

    private data class YardFloater(var x: Float, var y: Float, var life: Float, val kind: Int) {
        companion object {
            const val COIN = 0
            const val HEART = 1
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
        rootWindowInsets?.let { applySafeInsets(it) }
        post {
            if (!game.hasChosenCharacterName) showRenameDialog(firstTime = true)
        }
    }

    override fun onDetachedFromWindow() {
        renameDialog?.dismiss()
        renameDialog = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipeMin = 60f * hudScale(width.toFloat(), height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; consumed = false
                yardLongPressTriggered = false
                homeDragId = null
                homeDragActive = false
                if (isYardInteractive() && !homeOverlayBlocks(event.x, event.y)) {
                    yardFingerDown = true
                    yardFingerX = event.x
                    yardFingerY = event.y
                    yardFingerDownTime = SystemClock.uptimeMillis()
                }
                if (isHomeDraggable() && !homeOverlayBlocks(event.x, event.y)) {
                    val id = hitHomeDraggable(event.x, event.y)
                    if (id != null) {
                        homeDragId = id
                        if (canHomeDrag(id)) beginHomeDrag(id, event.x, event.y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (yardFingerDown) {
                    yardFingerX = event.x
                    yardFingerY = event.y
                }
                if (homeDragId != null && canHomeDrag(homeDragId!!)) {
                    val slop = YARD_DRAG_SLOP * yardSceneS.coerceAtLeast(0.5f)
                    if (!homeDragActive &&
                        kotlin.math.hypot(event.x - downX, event.y - downY) > slop
                    ) {
                        homeDragActive = true
                        consumed = true
                        removeCallbacks(yardSingleTapRunnable)
                    }
                    if (homeDragActive) {
                        updateHomeDrag(event.x, event.y)
                        consumed = true
                    }
                }
                if (!consumed && game.state == Game.State.RUNNING && !game.paused) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > swipeMin || abs(dy) > swipeMin) {
                        consumed = true
                        if (abs(dx) > abs(dy)) {
                            if (dx > 0) game.onSwipeRight() else game.onSwipeLeft()
                        } else {
                            if (dy < 0) game.onSwipeUp() else game.onSwipeDown()
                        }
                    }
                }
                // 追手模式：手指在庭院内滑动，目标跟随
                if (catState == CAT_CHASE_HAND && yardSceneHit.contains(event.x, event.y)) {
                    aimHandAt(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (homeDragActive) {
                    game.saveHomeLayout()
                    showToast("位置已保存")
                    homeDragId = null
                    homeDragActive = false
                    consumed = true
                } else if (homeDragId != null) {
                    if (!consumed && event.actionMasked == MotionEvent.ACTION_UP) {
                        dispatchHomeDragTap(homeDragId!!, event.x, event.y)
                        consumed = true
                    }
                    homeDragId = null
                }
                if (strokeActive) {
                    endStrokeCat()
                    consumed = true
                }
                if (!consumed && event.actionMasked == MotionEvent.ACTION_UP) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (showLeaderboard && abs(dx) > swipeMin && abs(dx) > abs(dy) * 1.2f) {
                        stepLeaderboardTab(if (dx < 0f) 1 else -1)
                        consumed = true
                    } else if (tryCatalogPageSwipe(dx, dy, swipeMin)) {
                        consumed = true
                    } else if (!yardLongPressTriggered) {
                        handleTap(event.x, event.y)
                    }
                }
                yardFingerDown = false
            }
        }
        return true
    }

    private fun isHomeDraggable(): Boolean =
        (game.state == Game.State.READY || game.state == Game.State.DEAD) &&
            game.menuPanel == Game.PANEL_HOME && !showStargazing && homeSubView == HOME_SUB_SCENE &&
            !showHomeLayers && !showHomeWorlds

    private fun isYardInteractive(): Boolean = isHomeDraggable()

    /**
     * 手指落在浮层里的时候，庭院一律不接管。
     *
     * 拖放链路比 [handleTap] 先跑，2D 时代院子在屏幕中段、面板在底部，两者基本不重叠；
     * 3D 之后院子的路面、栅栏、信箱会一直投影到屏幕最下缘，正好压在选物面板底下——
     * 于是「点面板里的屋顶」变成了「拖院子里的栅栏」，看起来就是点击失灵、乱弹界面。
     */
    private fun homeOverlayBlocks(x: Float, y: Float): Boolean =
        (!pickPanelRect.isEmpty && pickPanelRect.contains(x, y)) ||
            (showHomeLayers && homeLayerPanel.contains(x, y)) ||
            (!btnHomeLayers.isEmpty && btnHomeLayers.contains(x, y))

    private fun handleTap(x: Float, y: Float) {
        if (game.state == Game.State.RUNNING) {
            when {
                game.paused -> when {
                    btnResume.contains(x, y) -> game.resumeGame()
                    btnQuit.contains(x, y) -> game.quitRun()
                }
                btnPause.contains(x, y) -> game.pauseGame()
                else -> game.onTap()
            }
            return
        }
        // 死亡冷却
        if (game.state == Game.State.DEAD && game.deadTime <= 0.6f) return

        when (game.menuPanel) {
            Game.PANEL_MAIN -> {
                if (showLeaderboard) {
                    when {
                        btnLbClose.contains(x, y) -> showLeaderboard = false
                        btnLbScopeLocal.contains(x, y) -> leaderboardScope = LB_SCOPE_LOCAL
                        btnLbScopeGlobal.contains(x, y) -> switchLeaderboardScope(LB_SCOPE_GLOBAL)
                        btnLbPrev.contains(x, y) -> stepLeaderboardTab(-1)
                        btnLbNext.contains(x, y) -> stepLeaderboardTab(1)
                        else -> showLeaderboard = false
                    }
                    return
                }
                if (showHelp) { showHelp = false; return }
                if (handleSecretTitleTap(x, y)) return
                when {
                    btnLeaderboard.contains(x, y) -> {
                        showLeaderboard = true
                        leaderboardTab = 0
                        leaderboardScope = LB_SCOPE_LOCAL
                        if (game.leaderboardRemoteEnabled()) {
                            game.refreshLeaderboardRemote(0)
                        }
                    }
                    btnHome.contains(x, y) -> openHomePage()
                    else -> game.onTap()
                }
            }
            Game.PANEL_HOME -> {
                if (showStargazing) {
                    when {
                        btnStargazeL.contains(x, y) -> navigateStargazeBody(-1)
                        btnStargazeR.contains(x, y) -> navigateStargazeBody(1)
                        btnStargazeUpgrade.contains(x, y) -> {
                            val msg = game.upgradeTelescope()
                            showToast(msg)
                            if (game.telescopeLevel > stargazeBody) {
                                stargazeBody = game.telescopeLevel
                                applyStargazeView(stargazeBody)
                            }
                        }
                        else -> closeStargazing()
                    }
                    return
                }
                if (showHomeWorlds) {
                    handleHomeWorldsTap(x, y)
                    return
                }
                if (showHomeLayers) {
                    handleHomeLayersTap(x, y)
                    return
                }
                when (homeSubView) {
                    HOME_SUB_COLLECTION -> {
                        if (museumDetailId >= 0) {
                            if (!relicDetailFrame.contains(x, y)) museumDetailId = -1
                            return
                        }
                        when {
                            btnCatalogClose.contains(x, y) -> {
                                homeSubView = HOME_SUB_SCENE
                            }
                            else -> {
                                val page = museumPage.coerceIn(0, museumPages() - 1)
                                val start = page * MUSEUM_PER_PAGE
                                for (i in museumTileHits.indices) {
                                    val id = start + i
                                    if (id >= Game.RELIC_COUNT) break
                                    if (museumTileHits[i].contains(x, y)) {
                                        if (game.relicCollected(id) || game.immortalMode) {
                                            museumDetailId = id
                                        } else {
                                            showToast("尚未发现这件文物")
                                        }
                                        break
                                    }
                                }
                            }
                        }
                        return
                    }
                    HOME_SUB_HONOR -> {
                        if (btnCatalogClose.contains(x, y)) homeSubView = HOME_SUB_SCENE
                        return
                    }
                }
                // 选物面板开着时，它盖住的那片区域优先归它，别让底下的物件抢走点击
                if (homeEditing && handlePickTap(x, y)) return

                when {
                    homeSubView == HOME_SUB_SCENE && btnHomeLayers.contains(x, y) -> {
                        showHomeLayers = true
                        homeLayerScroll = 0f
                    }
                    btnLeaveHome.contains(x, y) -> leaveHomePage()
                    hitHomeTitle.contains(x, y) -> showRenameDialog(firstTime = false)
                    hitDoor.contains(x, y) -> {
                        showHomeWorlds = true
                        closeHomePick()
                    }
                    yardTelescopeHit.contains(x, y) && game.canUseTelescope() -> openStargazing()
                    hitMuseum.contains(x, y) -> {
                        museumPage = 0
                        museumDetailId = -1
                        homeSubView = HOME_SUB_COLLECTION
                        game.markRelicsSeen()
                    }
                    hitHonorWall.contains(x, y) -> {
                        honorPage = 0
                        homeSubView = HOME_SUB_HONOR
                        game.markHonorsSeen()
                    }
                    // 房子与屋顶合成同一张面板（小屋 + 屋顶两行），不再靠热区上下半分家
                    hitHouse.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_HOUSE)
                    yardCatHit.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_COLOR)
                    yardSceneHit.contains(x, y) -> scheduleYardSceneTap(x, y)
                }
            }
        }
    }

    private fun showRenameDialog(firstTime: Boolean) {
        if (renameDialog?.isShowing == true) return
        val input = EditText(context).apply {
            if (!firstTime) {
                setText(game.characterName)
                selectAll()
            }
            hint = "角色名称"
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(Game.CHARACTER_NAME_MAX_LENGTH))
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(if (firstTime) "欢迎！先给角色起个名字" else "设置角色名称")
            .setMessage("名字将显示在你的世界和家中，最多 ${Game.CHARACTER_NAME_MAX_LENGTH} 个字符")
            .setView(input)
            .setPositiveButton("保存", null)
        if (!firstTime) builder.setNegativeButton("取消", null)
        val dialog = builder.create()
        dialog.setCancelable(!firstTime)
        dialog.setCanceledOnTouchOutside(false)
        renameDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (game.renameCharacter(input.text.toString())) {
                    showToast(if (firstTime) "欢迎来到 ${game.characterName}的世界" else "角色名称已更新")
                    invalidate()
                    dialog.dismiss()
                } else {
                    input.error = "名称不能为空"
                }
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.setOnDismissListener {
            if (renameDialog === dialog) renameDialog = null
        }
        dialog.show()
    }

    /** Debug 包：主菜单标题在 4 秒内连点 7 次，切换仅供测试的不死模式。 */
    private fun handleSecretTitleTap(x: Float, y: Float): Boolean {
        if (!BuildConfig.DEBUG) return false
        if (game.state != Game.State.READY) return false
        val inTitle = x in width * 0.22f..width * 0.78f &&
            y in height * 0.14f..height * 0.30f
        if (!inTitle) return false

        val now = SystemClock.uptimeMillis()
        if (secretTapCount == 0 || now > secretTapDeadline) {
            secretTapCount = 1
            secretTapDeadline = now + 4_000L
        } else {
            secretTapCount++
        }
        if (secretTapCount >= 7) {
            secretTapCount = 0
            secretTapDeadline = 0L
            val enabled = game.toggleImmortalMode()
            showToast(
                if (enabled) "测试模式：不死 · 藏品全览已开启"
                else "测试模式：不死 · 藏品全览已关闭"
            )
        }
        return true
    }

    private fun showToast(msg: String) {
        if (msg.isEmpty()) return
        toast = msg
        toastLife = 1.8f
    }

    private fun closeHomeSubView() {
        museumDetailId = -1
        homeSubView = HOME_SUB_SCENE
        btnCatalogClose.setEmpty()
        relicDetailFrame.setEmpty()
    }

    /**
     * 系统返回键：优先关闭图鉴大图 / 全屏图鉴 / 观星 / 帮助，再退出小屋。
     * @return true 表示已消费，Activity 不应再 finish。
     */
    fun handleBackPressed(): Boolean {
        if (showHelp) {
            showHelp = false
            return true
        }
        if (showStargazing) {
            closeStargazing()
            return true
        }
        if (game.menuPanel == Game.PANEL_HOME) {
            when {
                showHomeWorlds -> {
                    showHomeWorlds = false
                    return true
                }
                showHomeLayers -> {
                    showHomeLayers = false
                    return true
                }
                homeSubView == HOME_SUB_COLLECTION && museumDetailId >= 0 -> {
                    museumDetailId = -1
                    return true
                }
                homeSubView == HOME_SUB_COLLECTION || homeSubView == HOME_SUB_HONOR -> {
                    closeHomeSubView()
                    return true
                }
                else -> {
                    leaveHomePage()
                    return true
                }
            }
        }
        if (game.state == Game.State.RUNNING && game.paused) {
            game.resumeGame()
            return true
        }
        return false
    }

    /**
     * 键盘/手柄方向键操控：左右换道、上跳、下滑，空格/回车等同点击（开局/重开/跳跃），
     * P 键暂停切换。忽略改名弹窗弹出期间的按键，以及自动连发（只认首次按下）。
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (renameDialog?.isShowing == true) return false
        if (event.repeatCount > 0) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> game.onSwipeLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> game.onSwipeRight()
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> game.onSwipeDown()
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                if (game.state == Game.State.RUNNING) game.onSwipeUp() else game.onTap()
            }
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> game.onTap()
            KeyEvent.KEYCODE_P -> {
                if (game.state == Game.State.RUNNING) {
                    if (game.paused) game.resumeGame() else game.pauseGame()
                }
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun openHomePage() {
        HomeYard.resetLight()   // 进门重新取一次天色，在家期间不再随实时昼夜漂移
        homeSubView = HOME_SUB_SCENE
        homeEditing = false
        showHomeLayers = false
        museumDetailId = -1
        game.switchMenuPanel(Game.PANEL_HOME)
    }

    private fun leaveHomePage() {
        closeStargazing()
        cancelYardTouch()
        HomeYard.resetLight()
        homeSubView = HOME_SUB_SCENE
        homeEditing = false
        showHomeLayers = false
        museumDetailId = -1
        game.switchMenuPanel(Game.PANEL_MAIN)
    }

    private fun selectHomeCategory(tab: Int) {
        game.switchHomeTab(tab)
        homeEditing = true
        pickArmedTab = -1
        pickArmedIdx = -1
    }

    /** 收起选物面板：连带清掉「等第二下确认」的那格，免得下次开面板直接扣钱 */
    private fun closeHomePick() {
        homeEditing = false
        pickChipCount = 0
        pickPanelRect.setEmpty()
        pickArmedTab = -1
        pickArmedIdx = -1
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (toastLife > 0f) toastLife -= 0.016f
        homePhase += 0.016f
        drawHud(canvas, width.toFloat(), height.toFloat())
        postInvalidateOnAnimation()
    }

    /** 横屏短边 /720；竖屏 /560，整体再放大一档，按钮更好点 */
    private fun hudScale(w: Float, h: Float): Float =
        if (h > w) min(w, h) / 560f else min(w, h) / 720f

    /** 顶部安全区偏移：竖屏时整体下移避开挖孔/圆角，供暂停/返回按钮共用 */
    private var hudTop = 0f

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val portrait = h > w
        val s = hudScale(w, h)
        val top = if (portrait) h * 0.035f else 0f
        hudTop = top
        val sdx = 1f
        val sdy = 1f
        // 商店/小屋为独立面板：不叠主菜单计分与 buff，避免挡标题/返回
        val inSubPanel = (game.state == Game.State.READY || game.state == Game.State.DEAD) &&
            game.menuPanel == Game.PANEL_HOME

        if (!inSubPanel) {
            // 文物：世界投影名牌叠在文物上方（先画，HUD 文字盖在上面）
            drawRelicBillboards(canvas, w, h, s, sdx, sdy)

            // 右上角：跑酷只显里程 + 连击两行；得分/金币/任务/文物计数全部留到结算屏看。
            // 跑起来时眼睛在赛道上，常驻数字读不到也用不上，反而糊住视线走廊。
            textPaint.textAlign = Paint.Align.RIGHT
            if (game.state == Game.State.RUNNING) {
                pixText(canvas, "${game.distance.toInt()} m", w - 36f * s, top + 66f * s, 42f * s, Color.WHITE, sdx, sdy)
                if (game.combo > 0) {
                    var cColor = when {
                        game.comboMult >= 5 -> 0xFFFF6B6B.toInt()
                        game.comboMult >= 3 -> 0xFFFFC21F.toInt()
                        else -> 0xFF7DEBA0.toInt()
                    }
                    val next = game.comboToNext()
                    val line = if (next > 0) "连击 ${game.combo} x${game.comboMult}  差$next"
                    else "连击 ${game.combo} x${game.comboMult} MAX"
                    // 快断连时闪烁提示，提醒赶紧吃金币或做个动作续上
                    val frac = game.comboTimeFrac()
                    if (frac < 0.35f) {
                        val blink = 0.4f + 0.6f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 9f))
                        cColor = withAlpha(0xFFFF6B6B.toInt(), (blink * 255).toInt())
                    }
                    pixText(canvas, line, w - 36f * s, top + 106f * s, 26f * s, cColor, sdx, sdy)
                }
            } else {
                pixText(canvas, "最远 ${game.highDistance} m", w - 36f * s, top + 66f * s, 28f * s, Color.WHITE, sdx, sdy)
                pixText(canvas, "最高 ${game.highScore}", w - 36f * s, top + 102f * s, 24f * s, 0xFFB8C4D0.toInt(), sdx, sdy)
                pixText(canvas, "钱包 ${game.wallet}", w - 36f * s, top + 136f * s, 26f * s, 0xFFFFC21F.toInt(), sdx, sdy)
            }

            // 左上角 buff：原本一档一行最多堆六行，现在压成一行彩色短标。
            // 保留颜色编码（每种 buff 一个固定色），扫一眼就知道身上挂着什么。
            if (game.state == Game.State.RUNNING) drawBuffRow(canvas, top + 46f * s, s, sdx, sdy)

            // 飘分：右上角里程/连击之下，是拾取与击倒唯一的即时反馈，保留
            if (game.state == Game.State.RUNNING && game.floatFlash > 0f && game.lastFloat.isNotEmpty()) {
                val alpha = (min(1f, game.floatFlash / 0.55f) * 255).toInt()
                val rise = (1.7f - game.floatFlash) * 14f * s
                val floatBase = top + (if (game.combo > 0) 148f else 110f) * s
                textPaint.textAlign = Paint.Align.RIGHT
                pixText(
                    canvas, game.lastFloat, w - 36f * s, floatBase - rise, 26f * s,
                    (alpha shl 24) or (game.lastFloatColor and 0x00FFFFFF), sdx, sdy
                )
            }
            textPaint.textAlign = Paint.Align.CENTER

            // 连击 / 横幅 / 新纪录：由 Notices 排好行号后统一分行绘制
            drawNoticeBand(canvas, w, h, s, sdx, sdy)
        }

        when (game.state) {
            Game.State.READY, Game.State.DEAD -> {
                if (game.menuPanel != Game.PANEL_HOME) dim(canvas, w, h)
                when (game.menuPanel) {
                    Game.PANEL_HOME -> drawHome(canvas, w, h, s, sdx, sdy)
                    else -> drawMainMenu(canvas, w, h, s, sdx, sdy)
                }
            }
            Game.State.RUNNING -> {
                drawPauseButton(canvas, w, h, s)
                if (game.paused) drawPauseOverlay(canvas, w, h, s, sdx, sdy)
            }
        }

        // 穿越闪光：护眼降到暖白低透明，只做"换场景了"的提示，不做爆闪
        if (game.portalFlash > 0f) {
            val a = (min(1f, game.portalFlash / 0.55f) * 46).toInt()
            dimPaint.color = (a shl 24) or 0x00FFF3DF
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }

        if (toastLife > 0f && toast.isNotEmpty() &&
            homeSubView != HOME_SUB_COLLECTION && homeSubView != HOME_SUB_HONOR &&
            !showLeaderboard
        ) {
            val fade = min(1f, toastLife / 0.4f)
            val appear = min(1f, (1.8f - toastLife) / 0.12f)
            val size = 28f * s
            val steps = (size / FONT_PX).roundToInt().coerceAtLeast(1)
            textPaint.textSize = (steps * FONT_PX).toFloat()
            val fm = textPaint.fontMetrics
            val halfW = textPaint.measureText(toast) / 2f + 22f * s
            val cx = (w / 2f).roundToInt().toFloat()
            // 跑酷时落在通知带整段之下，避免与连击 / 横幅叠字；菜单面板仍在按钮区上方
            val bandY = if (game.state == Game.State.RUNNING) {
                noticeBandTop(h, s) + noticeBandHeight(s)
            } else h * 0.78f
            val baseY = (bandY + (1f - appear) * 12f * s).roundToInt().toFloat()
            val toastTop = baseY + fm.ascent - 12f * s
            val toastBottom = baseY + fm.descent + 12f * s
            // 深色底板 + 描边，遮住下层文字保证可读
            PixelUi.drawRect(
                canvas, btnPaint, cx - halfW, toastTop, cx + halfW, toastBottom,
                withAlpha(0xFF1C2634.toInt(), (fade * 235).toInt()), s,
                edge = withAlpha(0xFFFFD426.toInt(), (fade * 255).toInt()),
                edgeW = max(1f, 2f * s), shadow = true
            )
            pixText(
                canvas, toast, cx, baseY, size,
                ((fade * 255).toInt() shl 24) or 0x00FFFFFF, sdx, sdy
            )
        }
    }

    /**
     * 身上挂着的增益压成左上角一行短标：`盾x2 磁9 倍5 冲3`。
     *
     * 一档一行时最多六行文字贴着屏幕左上，跑起来根本读不完；压成一行后
     * 只靠**颜色 + 一个字**辨认，剩下的数字是秒数。颜色沿用各 buff 拾取时
     * 飘分用的那一个，两处对得上就不用再记图例。
     */
    private fun drawBuffRow(canvas: Canvas, y: Float, s: Float, sdx: Float, sdy: Float) {
        val size = 28f * s
        textPaint.textAlign = Paint.Align.LEFT
        var x = 36f * s
        val gap = 14f * s

        fun token(text: String, color: Int) {
            pixText(canvas, text, x, y, size, color, sdx, sdy)
            x += textPaint.measureText(text) + gap
        }

        if (game.immortalMode) token("不死", 0xFF4DE8FF.toInt())
        if (game.helmetLayers > 0) token(if (game.helmetLayers >= 2) "盾x2" else "盾", 0xFFFFC21F.toInt())
        if (game.magnetTime > 0f) token("磁${game.magnetLeft()}", 0xFFFF6B6B.toInt())
        if (game.doubleTime > 0f) token("倍${game.doubleLeft()}", 0xFFC77DFF.toInt())
        if (game.boostTime > 0f) token("冲${game.boostLeft()}", 0xFF4DE8FF.toInt())
        if (game.riding != null) token("索", 0xFF7DEBA0.toInt())
    }

    private fun noticeBandTop(h: Float, s: Float): Float = h * RUN_BANNER_Y

    private fun noticeBandHeight(s: Float): Float = Notices.MAX_ROWS * NOTICE_ROW_H * s

    /**
     * 通知带：行号由 [Notices] 分配，这里只按行绘制。行高固定，
     * 所以上一行提前消失也不会让下面的字往上跳。
     */
    private fun drawNoticeBand(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val rows = game.notices.rows
        val bandTop = noticeBandTop(h, s)
        textPaint.textAlign = Paint.Align.CENTER
        for (i in rows.indices) {
            val n = rows[i] ?: continue
            if (n.remain <= 0f) continue
            val y = bandTop + i * NOTICE_ROW_H * s
            val size: Float
            val fadeOut: Float
            var pop = 1f
            when (n.style) {
                Notices.Style.RECORD -> {
                    size = 48f * s; fadeOut = 0.5f
                    pop = 1f + 0.3f * (1f - min(1f, n.age * 5f))
                }
                Notices.Style.COMBO -> {
                    size = 40f * s; fadeOut = 0.4f
                    pop = 1f + 0.25f * (1f - min(1f, n.age * 5f))
                }
                Notices.Style.BANNER -> {
                    size = 30f * s; fadeOut = 0.65f
                }
            }
            val alpha = (min(1f, n.remain / fadeOut) * 255).toInt()
            pixText(
                canvas, n.text, w / 2f, y, fitNoticeSize(n.text, size * pop, w - 48f * s),
                (alpha shl 24) or (n.color and 0x00FFFFFF), sdx, sdy
            )
        }
    }

    /** 长文案按点阵档位（12 的整数倍）逐级缩小，宁可小一号也不要跑出屏幕。 */
    private fun fitNoticeSize(text: String, desired: Float, maxW: Float): Float {
        var steps = (desired / FONT_PX).roundToInt().coerceAtLeast(1)
        while (steps > NOTICE_MIN_STEPS) {
            textPaint.textSize = (steps * FONT_PX).toFloat()
            if (textPaint.measureText(text) <= maxW) break
            steps--
        }
        return (steps * FONT_PX).toFloat()
    }

    private fun drawMainMenu(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val dead = game.state == Game.State.DEAD
        if (dead) {
            pixText(canvas, "游戏结束", w / 2f, h * 0.27f, 64f * s, Color.WHITE, sdx, sdy)
            val record = when {
                game.runNewDistRecord && game.runNewScoreRecord -> "  新纪录！"
                game.runNewDistRecord -> "  最远纪录！"
                game.runNewScoreRecord -> "  得分纪录！"
                else -> ""
            }
            pixText(canvas, "${game.distance.toInt()} m$record", w / 2f, h * 0.38f, 42f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "得分 ${game.score}", w / 2f, h * 0.45f, 28f * s, 0xFFB8C4D0.toInt(), sdx, sdy)
            pixText(canvas, "本局金币 ${game.coins}  ·  钱包 +${game.runWalletEarn}", w / 2f, h * 0.52f, 28f * s, 0xFFFFD54A.toInt(), sdx, sdy)
            val qd = game.quests.count { it.done }
            pixText(canvas, "任务 $qd/3  ·  最高连击 ${game.bestComboRun}", w / 2f, h * 0.59f, 26f * s, 0xFF7DEBA0.toInt(), sdx, sdy)
            if (game.runRelics > 0) {
                pixText(
                    canvas, "发现文物 ×${game.runRelics}  ·  图鉴 ${game.relicsFound}/${Game.RELIC_COUNT}",
                    w / 2f, h * 0.65f, 26f * s, 0xFFC77DFF.toInt(), sdx, sdy
                )
            }
            // 纪录榜入口：主菜单底栏的奖杯按钮已删，改成结算屏这一行。
            // 想看排名的时刻本来就是刚死那一下，跑之前摆个按钮没人点。
            val lbHits = game.lastRunLeaderboardHits
            val lbY = h * (if (game.runRelics > 0) 0.71f else 0.65f)
            val lbLabel = if (lbHits.isNotEmpty()) {
                val names = lbHits.map { game.leaderboardTitle(it) }.distinct().take(3).joinToString(" · ")
                "入榜 $names${if (lbHits.size > 3) "…" else ""}  ›"
            } else "纪录榜  ›"
            val lbSize = fittedTextSize(lbLabel, 24f * s, w * 0.86f, 16f * s)
            pixText(canvas, lbLabel, w / 2f, lbY, lbSize, 0xFFFFD426.toInt(), sdx, sdy)
            // pixText 刚把 textSize 设成实际字号，热区照着量出来的宽度包一圈
            val lbHalfW = textPaint.measureText(lbLabel) / 2f + 20f * s
            btnLeaderboard.set(w / 2f - lbHalfW, lbY - 28f * s, w / 2f + lbHalfW, lbY + 14f * s)

            if (game.deadTime > 0.6f) {
                pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.82f, 28f * s, Color.WHITE, sdx, sdy)
            }
        } else {
            btnLeaderboard.setEmpty()
            // 第一局之前自动摊开一次玩法说明，跑过一局就再不出现——不为它留常驻的「?」按钮
            if (game.totalRuns == 0 && !helpAutoShown) {
                showHelp = true
                helpAutoShown = true
            }
            val worldTitle = "${game.characterName}的世界"
            val worldTitleSize = fittedTextSize(worldTitle, 72f * s, w * 0.64f, 36f * s)
            pixText(canvas, worldTitle, w / 2f, h * 0.30f, worldTitleSize, Color.WHITE, sdx, sdy)
            val blink = if (kotlin.math.sin(homePhase * 3f) > -0.3f) 255 else 120
            pixText(canvas, "点击屏幕开始", w / 2f, h * 0.46f, 32f * s, withAlpha(Color.WHITE, blink), sdx, sdy)
        }

        // 落在猫和底栏之间的空地上：等待时视线自然会扫到，又不挡任何东西
        drawRelicNote(canvas, w, h * (if (dead) 0.72f else 0.78f), s, sdx, sdy)

        // 底栏只剩「家」一个：帮助改成首局自动弹一次，纪录榜并进结算屏。
        // 主菜单上除了这一个按钮，点哪儿都是开始跑。
        val portrait = h > w
        val rowH = if (portrait) 72f * s else 52f * s
        val margin = if (portrait) 16f * s else 28f * s
        val rowBottom = h - (if (portrait) 48f else 28f) * s
        val rowTop = rowBottom - rowH
        btnHome.set(w - margin - rowH, rowTop, w - margin, rowBottom)
        drawHomeEntryBtn(canvas, btnHome, s)

        if (showLeaderboard) drawLeaderboardOverlay(canvas, w, h, s, sdx, sdy)
        if (showHelp) drawHelpOverlay(canvas, w, h, s, sdx, sdy)
    }

    /**
     * 藏品笔记：等待开始 / 看结算时，安静地翻出一条**已收集**文物的展签。
     *
     * 「润物细无声」的做法——不弹窗、不打断、不考问，只是把孩子自己挖到的东西
     * 摆在他发呆的地方，多看几眼就记住了。没收集过任何文物时什么都不显示，
     * 不做任何催促。
     */
    private fun drawRelicNote(canvas: Canvas, w: Float, y: Float, s: Float, sdx: Float, sdy: Float) {
        val owned = (0 until Game.RELIC_COUNT).filter { game.relicCollected(it) }
        if (owned.isEmpty()) return
        val slot = (homePhase / RELIC_NOTE_SECS).toInt()
        val id = owned[Math.floorMod(slot, owned.size)]
        // 每条停留 RELIC_NOTE_SECS 秒，首尾各淡入淡出，换条不生硬
        val t = homePhase - slot * RELIC_NOTE_SECS
        val fade = min(1f, min(t, RELIC_NOTE_SECS - t) / 0.7f).coerceAtLeast(0f)
        if (fade <= 0.02f) return

        val title = "${Game.RELIC_NAMES[id]} · ${Game.RELIC_ERAS[id]}"
        val titleSize = fitNoticeSize(title, 26f * s, w - 64f * s)
        pixText(canvas, title, w / 2f, y, titleSize, withAlpha(RELIC_NOTE_TITLE, (fade * 205).toInt()), sdx, sdy)
        val fact = Game.RELIC_FACTS[id]
        val factSize = fitNoticeSize(fact, 22f * s, w - 64f * s)
        pixText(
            canvas, fact, w / 2f, y + 34f * s, factSize,
            withAlpha(RELIC_NOTE_BODY, (fade * 180).toInt()), sdx, sdy
        )
    }

    private fun drawHelpOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        val pw = min(w * 0.72f, 560f * s)
        val top = h * 0.16f
        val bottom = h * 0.84f
        PixelUi.drawPanel(
            canvas, btnPaint, w / 2f - pw / 2f, top, w / 2f + pw / 2f, bottom,
            0xF21C2634.toInt(), 0xFFFFD426.toInt(), s, 12f * s, edgeW = 2f * s
        )

        pixText(canvas, "玩法说明", w / 2f, top + 56f * s, 36f * s, 0xFFFFD426.toInt(), sdx, sdy)
        val lines = arrayOf(
            "左右滑动 换道",
            "上滑或点击 跳跃",
            "下滑 铲滑",
            "传送门 穿越平行宇宙",
            "路上的文物 收进藏品图鉴",
            "金币 在家购买装扮与装饰",
            "装扮可用于猫与庭院",
            "家能量 提供开局奖励"
        )
        var ly = top + 116f * s
        for (line in lines) {
            pixText(canvas, line, w / 2f, ly, 26f * s, Color.WHITE, sdx, sdy)
            ly += 44f * s
        }
        pixText(canvas, "点击任意处关闭", w / 2f, bottom - 28f * s, 22f * s, 0xFFAAAAAA.toInt(), sdx, sdy)
    }

    private fun stepLeaderboardTab(delta: Int) {
        val n = game.leaderboardCategoryCount()
        if (n <= 0) return
        leaderboardTab = Math.floorMod(leaderboardTab + delta, n)
        if (leaderboardScope == LB_SCOPE_GLOBAL && game.leaderboardRemoteEnabled()) {
            game.refreshLeaderboardRemote(leaderboardTab)
        }
    }

    private fun switchLeaderboardScope(scope: Int) {
        leaderboardScope = scope
        if (scope == LB_SCOPE_GLOBAL && game.leaderboardRemoteEnabled()) {
            game.refreshLeaderboardRemote(leaderboardTab)
        }
    }

    private fun drawLeaderboardOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        val tab = leaderboardTab.coerceIn(0, game.leaderboardCategoryCount() - 1)
        val title = game.leaderboardTitle(tab)
        val subtitle = game.leaderboardSubtitle(tab)

        val left = safeL + 12f * s
        val top = safeT + 12f * s
        val right = w - safeR - 12f * s
        val bottom = h - safeB - 12f * s
        // 比其它图鉴多一行"本地纪录榜"总标题，header 要比通用的 OVERLAY_HEADER_H 更高，
        // 否则副标题会被下面的列表行盖住
        val headerBottom = top + LEADERBOARD_HEADER_H * s
        // 底栏比通用图鉴多了本地/全服切换 + 页码，偶尔还要放同步状态，同样需要单独的高度
        val footerTop = bottom - LEADERBOARD_FOOTER_H * s

        PixelUi.drawPanel(
            canvas, btnPaint, left, top, right, bottom,
            LIGHT_PANEL, LIGHT_PANEL_EDGE, s, 16f * s, edgeW = 3f * s
        )

        val titleCx = (left + right) * 0.5f
        // 顶栏用一块暖金切角 banner 把标题区和列表区分开，比整片同色更有层次
        PixelUi.drawPanel(
            canvas, btnPaint, left + 10f * s, top + 6f * s, right - 10f * s, headerBottom - 4f * s,
            0xFFF3E4B8.toInt(), LIGHT_PANEL_EDGE, s, 9f * s, edgeW = 1.5f * s, shadow = false
        )
        val boardTitle = if (leaderboardScope == LB_SCOPE_GLOBAL) "全服纪录榜" else "本地纪录榜"
        lightText(canvas, boardTitle, titleCx, top + 46f * s, 36f * s, LIGHT_TITLE)
        val catSize = fittedTextSize(title, 28f * s, (right - left) * 0.82f, 18f * s)
        lightText(canvas, title, titleCx, top + 84f * s, catSize, LIGHT_RELIC_LEGEND)
        val subSize = fittedTextSize(subtitle, 20f * s, (right - left) * 0.88f, 14f * s)
        lightText(canvas, subtitle, titleCx, top + 116f * s, subSize, LIGHT_SUB)

        val rowGap = 8f * s
        val listTop = headerBottom + 8f * s
        val listBottom = footerTop - 8f * s
        val entries = if (leaderboardScope == LB_SCOPE_GLOBAL) {
            game.leaderboardRemoteEntries(tab)
        } else {
            game.leaderboardEntries(tab)
        }
        // 单行是默认样式；只要这一类里有任何一条在单行最小字号下也放不下，
        // 整个类别统一退到双行，避免同一榜里有的行单行、有的行双行
        val rowW = (right - 16f * s) - (left + 16f * s)
        val singleLine = entries.all { rowFitsSingleLine(it, tab, rowW, s) }
        val rowH = if (singleLine) ROW_H_SINGLE * s else ROW_H_DOUBLE * s
        val maxRows = ((listBottom - listTop) / (rowH + rowGap)).toInt().coerceAtMost(Leaderboards.MAX_ENTRIES)

        if (entries.isEmpty()) {
            val emptyHint = when {
                leaderboardScope == LB_SCOPE_GLOBAL && !game.leaderboardRemoteEnabled() ->
                    "未配置全服榜服务器"
                leaderboardScope == LB_SCOPE_GLOBAL ->
                    "正在拉取或暂无全服纪录…"
                else -> "还没有纪录，跑一局试试！"
            }
            lightText(
                canvas, emptyHint,
                titleCx, (listTop + listBottom) * 0.5f, 24f * s, LIGHT_MUTED
            )
        } else {
            val shown = min(maxRows, entries.size)
            // 条目不够占满整块列表区时居中显示，避免一堆纪录挤在顶上、下面大片空白
            val contentH = shown * rowH + (shown - 1) * rowGap
            var y = listTop + max(0f, (listBottom - listTop - contentH) / 2f)
            for (i in 0 until shown) {
                val e = entries[i]
                drawLeaderboardRow(
                    canvas, left + 16f * s, y, right - 16f * s, y + rowH, s, i + 1, e, tab, singleLine
                )
                y += rowH + rowGap
            }
        }

        val navW = 56f * s
        val navH = 44f * s
        val scopeW = 72f * s
        val scopeGap = 8f * s
        val scopeTotalW = scopeW * 2f + scopeGap

        // 底栏内容从 footerTop 往下顺序排布，行数会变（有没有同步状态行）也不会互相叠字
        var fy = footerTop + 12f * s
        if (leaderboardScope == LB_SCOPE_GLOBAL && game.leaderboardRemoteEnabled()) {
            val syncLine = game.leaderboardSyncStatus()
            if (syncLine.isNotEmpty()) {
                lightText(canvas, syncLine, titleCx, fy + 14f * s, 18f * s, LIGHT_HINT)
                fy += 30f * s
            }
        }
        val pageLabel = "${tab + 1}/${game.leaderboardCategoryCount()}"
        lightText(canvas, pageLabel, titleCx, fy + 16f * s, 20f * s, LIGHT_HINT)
        fy += 34f * s

        val navY = fy
        btnLbScopeLocal.set(
            titleCx - scopeTotalW / 2f, navY,
            titleCx - scopeTotalW / 2f + scopeW, navY + navH
        )
        btnLbScopeGlobal.set(
            btnLbScopeLocal.right + scopeGap, navY,
            btnLbScopeLocal.right + scopeGap + scopeW, navY + navH
        )
        drawLightBtn(
            canvas, btnLbScopeLocal, "本地", s,
            selected = leaderboardScope == LB_SCOPE_LOCAL
        )
        drawLightBtn(
            canvas, btnLbScopeGlobal, "全服", s,
            selected = leaderboardScope == LB_SCOPE_GLOBAL,
            enabled = game.leaderboardRemoteEnabled()
        )
        btnLbPrev.set(left + 16f * s, navY, left + 16f * s + navW, navY + navH)
        btnLbNext.set(right - 16f * s - navW, navY, right - 16f * s, navY + navH)
        drawLightBtn(canvas, btnLbPrev, "<", s)
        drawLightBtn(canvas, btnLbNext, ">", s)

        drawOverlayCloseRow(canvas, left, right, bottom, s, "左右切换类别 · 点击外部关闭", btnLbClose)
    }

    /**
     * entry 的三段文字（名字/详情/数值）在各自最小字号下能否挤上单行。
     * 单行只放得下日期这种轻量详情；完整的"距离 · 得分"留给双行兜底显示，
     * 否则 detail 字符串天生比一行预算长得多，单行永远触发不了。
     */
    private fun rowFitsSingleLine(entry: Leaderboards.Entry, category: Int, rowW: Float, s: Float): Boolean {
        val name = entry.player.ifEmpty { "?" }
        val detail = formatLeaderboardWhen(entry.whenMs)
        val valueText = game.formatLeaderboardValue(category, entry.value)
        textPaint.textSize = ROW_NAME_MIN * s
        if (textPaint.measureText(name) > rowW * ROW_NAME_W) return false
        textPaint.textSize = ROW_DETAIL_MIN * s
        if (textPaint.measureText(detail) > rowW * ROW_DETAIL_W) return false
        textPaint.textSize = ROW_VALUE_MIN * s
        if (textPaint.measureText(valueText) > rowW * ROW_VALUE_W) return false
        return true
    }

    private fun leaderboardDetailText(entry: Leaderboards.Entry): String = buildString {
        append(formatLeaderboardWhen(entry.whenMs))
        if (entry.detail.isNotEmpty()) {
            append(" · ")
            append(entry.detail)
        }
    }

    private fun drawLeaderboardRow(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        s: Float, rank: Int, entry: Leaderboards.Entry, category: Int, singleLine: Boolean
    ) {
        val rowFill = if (rank <= 3) 0xFFFFF6DC.toInt() else LIGHT_ROW
        PixelUi.drawPanel(
            canvas, btnPaint, left, top, right, bottom,
            rowFill, LIGHT_ROW_EDGE, s, 7f * s, edgeW = 1.5f * s, shadow = rank <= 3
        )

        val cy = (top + bottom) * 0.5f
        val badgeCx = left + 26f * s
        if (rank <= 3) {
            // 前三名直接用与荣誉墙同款的像素奖牌，比数字更抓眼
            val medalR = 17f * s
            drawMedal(canvas, badgeCx, cy, medalR, 4 - rank)
        } else {
            val rankColor = LIGHT_MUTED
            textPaint.textAlign = Paint.Align.CENTER
            lightText(canvas, "$rank", badgeCx, centeredBaselineY(cy, 22f * s), 22f * s, rankColor)
        }

        val rowW = right - left
        val name = entry.player.ifEmpty { "?" }
        val valueText = game.formatLeaderboardValue(category, entry.value)

        if (singleLine) {
            // 单行：名字靠左，数值靠右，中间只留日期这类轻量详情；完整详情留给双行兜底
            val detail = formatLeaderboardWhen(entry.whenMs)
            textPaint.textAlign = Paint.Align.RIGHT
            val valueMaxW = rowW * ROW_VALUE_W
            val valueSize = fittedTextSize(valueText, 22f * s, valueMaxW, ROW_VALUE_MIN * s)
            lightText(canvas, valueText, right - 12f * s, centeredBaselineY(cy, valueSize), valueSize, LIGHT_RELIC_RARE)

            val detailMaxW = rowW * ROW_DETAIL_W
            val detailSize = fittedTextSize(detail, 16f * s, detailMaxW, ROW_DETAIL_MIN * s)
            lightText(canvas, detail, right - 12f * s - valueMaxW - 10f * s, centeredBaselineY(cy, detailSize), detailSize, LIGHT_HINT)

            textPaint.textAlign = Paint.Align.LEFT
            val nameMaxW = rowW * ROW_NAME_W
            val nameSize = fittedTextSize(name, 22f * s, nameMaxW, ROW_NAME_MIN * s)
            lightText(canvas, name, left + 52f * s, centeredBaselineY(cy, nameSize), nameSize, LIGHT_TEXT)
        } else {
            val detail = leaderboardDetailText(entry)
            val nameMaxW = rowW * 0.34f
            val nameSize = fittedTextSize(name, 22f * s, nameMaxW, ROW_NAME_MIN * s)
            lightText(canvas, name, left + 52f * s, cy - 6f * s, nameSize, LIGHT_TEXT)

            textPaint.textAlign = Paint.Align.RIGHT
            val valueSize = fittedTextSize(valueText, 24f * s, rowW * 0.28f, ROW_VALUE_MIN * s)
            lightText(canvas, valueText, right - 12f * s, cy - 6f * s, valueSize, LIGHT_RELIC_RARE)

            textPaint.textAlign = Paint.Align.LEFT
            val detailSize = fittedTextSize(detail, 16f * s, rowW - 64f * s, ROW_DETAIL_MIN * s)
            lightText(canvas, detail, left + 52f * s, cy + 16f * s, detailSize, LIGHT_HINT)
        }
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun formatLeaderboardWhen(whenMs: Long): String {
        val c = java.util.Calendar.getInstance()
        c.timeInMillis = whenMs
        val m = c.get(java.util.Calendar.MONTH) + 1
        val d = c.get(java.util.Calendar.DAY_OF_MONTH)
        return "%02d/%02d".format(m, d)
    }

    // ---------- 家（装扮；藏品 / 荣誉浮窗） ----------
    private fun drawHome(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val portrait = h > w
        LayoutConfig.select(portrait)   // 横竖屏各用独立一套布局
        game.selectHomeLayoutOrientation(portrait)
        homeDragHits.clear()
        val isCatTab = game.isCatHomeTab()
        // 「正在浏览哪一件」由渲染层直接读 game 决定（见 GameRenderer.drawHomeWorld），
        // 这里不再算一遍——两套预览逻辑迟早会对不上
        drawHomeScene(canvas, w, h, s)

        drawHomeTopBar(canvas, w, h, s, sdx, sdy, isCatTab)

        // 选物面板自带标题与收起提示，原来那行浮在半空的 editHint 就多余了
        if (homeEditing) drawHomePickPanel(canvas, w, h, s, sdx, sdy)
        else { pickChipCount = 0; pickPanelRect.setEmpty() }

        if (homeSubView == HOME_SUB_SCENE) {
            layoutLeaveHomeButton(w, h, s)
            if (homeShown("leave")) {
                drawLeaveHomeBtn(canvas, btnLeaveHome, s)
                putDragHit("leave", btnLeaveHome)
            } else btnLeaveHome.setEmpty()
            layoutHomeWalletPlate(w, h, s)
            if (homeShown("wallet")) {
                drawHomeWalletPlate(canvas, homeWalletPlate, s, sdx, sdy)
                putDragHit("wallet", homeWalletPlate)
            } else homeWalletPlate.setEmpty()
        } else {
            btnLeaveHome.setEmpty()
        }

        when (homeSubView) {
            HOME_SUB_COLLECTION -> drawCollectionOverlay(canvas, w, h, s, sdx, sdy)
            HOME_SUB_HONOR -> drawHonorOverlay(canvas, w, h, s, sdx, sdy)
        }
        if (showHomeWorlds) drawHomeWorldsSheet(canvas, w, h, s, sdx, sdy)
        if (showStargazing) drawStargazeOverlay(canvas, w, h, s, sdx, sdy)
        drawHomeDragHighlight(canvas, s)
        if (homeSubView == HOME_SUB_SCENE) {
            layoutHomeLayersButton(w, h, s)
            drawHomeLayersButton(canvas, s)
            if (showHomeLayers) drawHomeLayersPanel(canvas, w, h, s, sdx, sdy)
        } else {
            btnHomeLayers.setEmpty()
        }
    }

    private fun handleHomeLayersTap(x: Float, y: Float) {
        if (btnHomeLayersClose.contains(x, y) || btnHomeLayersShowAll.contains(x, y)) {
            if (btnHomeLayersShowAll.contains(x, y)) {
                game.showAllHomeElements()
                showToast("已全部显示")
            }
            showHomeLayers = false
            invalidate()
            return
        }
        for ((id, rect) in homeLayerRowHits) {
            if (rect.contains(x, y)) {
                val on = game.toggleHomeElVisible(id)
                showToast((if (on) "已显示 " else "已隐藏 ") + PlayerHomeLayout.labelOf(id))
                invalidate()
                return
            }
        }
        if (!homeLayerPanel.contains(x, y)) showHomeLayers = false
    }

    private fun layoutHomeLayersButton(w: Float, h: Float, s: Float) {
        val size = 56f * s
        btnHomeLayers.set(
            w - safeR - 12f * s - size, safeT + 10f * s,
            w - safeR - 12f * s, safeT + 10f * s + size
        )
    }

    private fun drawHomeLayersButton(canvas: Canvas, s: Float) {
        val cx = btnHomeLayers.centerX()
        val cy = btnHomeLayers.centerY()
        val r = (btnHomeLayers.width() * 0.5f).coerceAtMost(btnHomeLayers.height() * 0.5f)
        val pulse = 0.7f + 0.3f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 1.8f))
        val accent = withAlpha(0xFFFFD426.toInt(), (pulse * 255).toInt())
        PixelUi.drawTinyWoodBtn(canvas, btnPaint, cx, cy, r * 0.92f, s, accent)

        val stackX = cx - 8f * s
        val stackY = cy - 4f * s
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFFFF0D0.toInt()
        canvas.drawRect(stackX, stackY + 6f * s, stackX + 16f * s, stackY + 9f * s, btnPaint)
        canvas.drawRect(stackX + 2f * s, stackY + 3f * s, stackX + 14f * s, stackY + 6f * s, btnPaint)
        canvas.drawRect(stackX + 4f * s, stackY, stackX + 12f * s, stackY + 3f * s, btnPaint)

        pixText(
            canvas, "图层", cx, cy + 14f * s,
            16f * s, 0xFFFFF0D0.toInt(), 0f, 0f
        )
    }

    private fun drawHomeLayersPanel(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        homeLayerRowHits.clear()
        val pad = 18f * s
        val panelW = min(w * 0.88f, 440f * s)
        val panelH = min(h * 0.82f, 700f * s)
        val left = (w - panelW) / 2f
        val top = (h - panelH) / 2f
        homeLayerPanel.set(left, top, left + panelW, top + panelH)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xBB000000.toInt()
        canvas.drawRect(0f, 0f, w, h, btnPaint)

        PixelUi.drawPanel(
            canvas, btnPaint, left, top, left + panelW, top + panelH,
            0xF01C2634.toInt(), 0xFFFFD426.toInt(), s, 12f * s, edgeW = 2.5f * s
        )

        val titleBarT = top + 6f * s
        val titleBarH = 46f * s
        val titleBarB = titleBarT + titleBarH
        PixelUi.drawPanel(
            canvas, btnPaint, left + 8f * s, titleBarT, left + panelW - 8f * s, titleBarB,
            0xFF2C3A4E.toInt(), 0xFFFFD426.toInt(), s, 8f * s, edgeW = 1.5f * s, shadow = false
        )
        textPaint.textAlign = Paint.Align.CENTER
        pixText(canvas, "家园图层", left + panelW / 2f, centeredBaselineY((titleBarT + titleBarB) * 0.5f, 24f * s),
            24f * s, 0xFFFFE8A8.toInt(), sdx, sdy)

        val btnH = 36f * s
        val btnW = 92f * s
        val btnY = titleBarB + 14f * s
        btnHomeLayersShowAll.set(left + pad, btnY, left + pad + btnW, btnY + btnH)
        btnHomeLayersClose.set(left + panelW - pad - btnW, btnY, left + panelW - pad, btnY + btnH)
        PixelUi.drawButton(
            canvas, btnPaint, btnHomeLayersShowAll.left, btnHomeLayersShowAll.top,
            btnHomeLayersShowAll.right, btnHomeLayersShowAll.bottom,
            0xFF3A5468.toInt(), 0xFF6B8E9E.toInt(), s * 0.9f, notch = 6f * s
        )
        pixText(canvas, "全显示", btnHomeLayersShowAll.centerX(),
            centeredBaselineY(btnHomeLayersShowAll.centerY(), 18f * s), 18f * s, Color.WHITE, 0f, 0f)
        PixelUi.drawButton(
            canvas, btnPaint, btnHomeLayersClose.left, btnHomeLayersClose.top,
            btnHomeLayersClose.right, btnHomeLayersClose.bottom,
            0xFF5A4A2E.toInt(), 0xFFB08A48.toInt(), s * 0.9f, notch = 6f * s
        )
        pixText(canvas, "完成", btnHomeLayersClose.centerX(),
            centeredBaselineY(btnHomeLayersClose.centerY(), 18f * s), 18f * s, 0xFFFFF0D0.toInt(), 0f, 0f)

        val listTop = btnY + btnH + 14f * s
        val listBottom = top + panelH - pad - 10f * s
        val rowH = 40f * s
        val contentH = PlayerHomeLayout.ELEMENTS.size * rowH
        val maxScroll = (contentH - (listBottom - listTop)).coerceAtLeast(0f)
        homeLayerScroll = homeLayerScroll.coerceIn(0f, maxScroll)

        PixelUi.drawDivider(canvas, btnPaint, left + pad, listTop - 6f * s, left + panelW - pad, s, 0x44FFD426.toInt())

        canvas.save()
        canvas.clipRect(left + pad, listTop, left + panelW - pad, listBottom)
        var y = listTop - homeLayerScroll
        for (el in PlayerHomeLayout.ELEMENTS) {
            val rowTop = y
            val rowBottom = y + rowH - 6f * s
            if (rowBottom >= listTop && rowTop <= listBottom) {
                val on = homeShown(el.id)
                val rowL = left + pad + 2f * s
                val rowR = left + panelW - pad - 2f * s
                PixelUi.drawRect(
                    canvas, btnPaint, rowL, rowTop, rowR, rowBottom,
                    if (on) 0xFF324458.toInt() else 0xFF242C36.toInt(), s,
                    bevel = if (on) PixelUi.Bevel.RAISED else PixelUi.Bevel.INSET,
                    edge = if (on) 0x6690B8D0.toInt() else 0x33405060.toInt(),
                    edgeW = 1.5f * s, shadow = on
                )
                val box = 22f * s
                val boxL = rowL + 12f * s
                val boxCy = (rowTop + rowBottom) * 0.5f
                PixelUi.drawRect(
                    canvas, btnPaint, boxL, boxCy - box / 2f, boxL + box, boxCy + box / 2f,
                    if (on) 0xFF3F9E5A.toInt() else 0xFF3A4552.toInt(), s,
                    bevel = if (on) PixelUi.Bevel.RAISED else PixelUi.Bevel.INSET,
                    edge = if (on) 0xFF5FC87A.toInt() else 0xFF506070.toInt(),
                    edgeW = 1.5f * s
                )
                if (on) {
                    btnPaint.color = 0xFFF0FFF4.toInt()
                    canvas.drawRect(boxL + 5f * s, boxCy - 2f * s, boxL + box - 7f * s, boxCy, btnPaint)
                    canvas.drawRect(boxL + 5f * s, boxCy, boxL + box - 11f * s, boxCy + 2f * s, btnPaint)
                    btnPaint.color = 0xFFD0FFE0.toInt()
                    canvas.drawRect(boxL + 6f * s, boxCy - 2f * s, boxL + 8f * s, boxCy - 1f * s, btnPaint)
                }
                textPaint.textAlign = Paint.Align.LEFT
                pixText(
                    canvas, el.label, boxL + box + 14f * s, centeredBaselineY(boxCy, 18f * s),
                    18f * s, if (on) 0xFFF0F4FF.toInt() else 0xFF708094.toInt(), 0f, 0f
                )
                homeLayerRowHits.add(el.id to RectF(rowL, rowTop, rowR, rowBottom))
            }
            y += rowH
        }
        canvas.restore()
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeDragHighlight(canvas: Canvas, s: Float) {
        if (!homeDragActive) return
        val id = homeDragId ?: return
        val hit = homeDragHits[id] ?: return
        if (hit.isEmpty) return
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 3f * s
        btnPaint.color = 0xAAFFD426.toInt()
        canvas.drawRect(hit, btnPaint)
        btnPaint.style = Paint.Style.FILL
    }

    private fun putDragHit(id: String, rect: RectF) {
        if (!homeShown(id)) {
            homeDragHits.remove(id)
            return
        }
        if (rect.isEmpty) {
            homeDragHits.remove(id)
            return
        }
        homeDragHits.getOrPut(id) { RectF() }.set(rect)
    }

    /** 家页面顶栏：木牌铭牌 + 金币芯片 + 能量格，贴合小屋像素风 */
    private fun drawHomeTopBar(
        canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float, isCatTab: Boolean
    ) {
        val portrait = h > w
        val showHint = !homeEditing
        val margin = 26f * s

        val title = "${game.characterName}的家"
        val titleMaxW = w * 0.72f
        val titleSize = fittedTextSize(title, 30f * s, titleMaxW - 48f * s, 22f * s)
        val plaqueH = titleSize + 28f * s
        val rewardLine = game.homeLevelDesc()
        val rewardSize = fittedTextSize(rewardLine, 18f * s, w * 0.86f, 14f * s)
        val hint = if (homeEditing) {
            if (isCatTab) "点场景元素装扮家与猫" else "点房屋/屋顶/庭院/猫来装扮"
        } else {
            "拖动任意元素可自由摆放"
        }
        val fullHintSize = fittedTextSize(hint, 16f * s, w * 0.86f, 12f * s)
        val hintSize = if (showHint) fullHintSize else 0f
        val lv = game.homeLevel()

        val energyH = 26f * s
        val rewardPad = 10f * s
        val rewardH = rewardSize + rewardPad * 2f
        val topPad = 18f * s
        val afterPlaque = 14f * s
        val afterEnergy = 10f * s
        val afterReward = 10f * s
        val bottomPad = 14f * s

        // 按实际行高累加，避免能量格与奖励条叠压
        var barH = hudTop + topPad + plaqueH + afterPlaque +
            energyH + afterEnergy + rewardH + afterReward
        if (showHint) barH += hintSize + bottomPad else barH += bottomPad
        // 远景地标按顶栏最高的一种状态排版，进出装扮模式时才不会跳动
        homeBarBottom = barH + if (showHint) 0f else fullHintSize

        // 第 1 行：木牌铭牌标题（可点改名）；金币铭牌在右下角与「出门」成对
        var y = hudTop + topPad
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = titleSize
        val titleW = textPaint.measureText(title)
        val plaqueW = min(w - 2f * margin, titleW + 72f * s).coerceAtLeast(160f * s)
        val titleCx = w / 2f
        val plaqueL = titleCx - plaqueW / 2f
        val plaqueR = titleCx + plaqueW / 2f
        val plaqueT = y
        val plaqueB = y + plaqueH
        val plaqueCy = (plaqueT + plaqueB) * 0.5f
        // 顶栏文字元素：位置微调 + 字号缩放
        val ndx = game.homeNameDX() * s; val ndy = game.homeNameDY() * s
        val nameSc = LayoutConfig.cur.nameS
        val nameCx = (plaqueL + plaqueR) * 0.5f + ndx; val nameCy = plaqueCy + ndy
        val nhw = (plaqueR - plaqueL) * 0.5f * nameSc; val nhh = (plaqueB - plaqueT) * 0.5f * nameSc
        hitHomeTitle.set(nameCx - nhw, nameCy - nhh, nameCx + nhw, nameCy + nhh)
        if (homeShown("name")) {
            canvas.save(); canvas.scale(nameSc, nameSc, nameCx, nameCy)
            drawHomeNamePlaque(canvas, plaqueL + ndx, plaqueT + ndy, plaqueR + ndx, plaqueB + ndy, s)
            drawTinyHouseIcon(canvas, plaqueL + 22f * s + ndx, plaqueCy + ndy, s * 0.85f)
            pixText(
                canvas, title, titleCx + 6f * s + ndx, centeredBaselineY(plaqueCy, titleSize) + ndy,
                titleSize, 0xFFFFF6E8.toInt(), sdx, sdy
            )
            canvas.restore()
            putDragHit("name", hitHomeTitle)
        } else hitHomeTitle.setEmpty()
        y = plaqueB + afterPlaque

        // 第 2 行：家能量等级格（整行独占高度）
        val energyCy = y + energyH * 0.5f
        val enCx = w / 2f + game.homeEnergyDX() * s; val enCy = energyCy + game.homeEnergyDY() * s
        val enSc = LayoutConfig.cur.energyS
        hitHomeEnergy.set(
            enCx - 96f * s * enSc, enCy - 16f * s * enSc,
            enCx + 96f * s * enSc, enCy + 16f * s * enSc
        )
        putDragHit("energy", hitHomeEnergy)
        if (homeShown("energy")) {
            canvas.save(); canvas.scale(enSc, enSc, enCx, enCy)
            drawHomeEnergyMeter(canvas, enCx, enCy, s, lv, sdx, sdy)
            canvas.restore()
        } else hitHomeEnergy.setEmpty()
        y += energyH + afterEnergy

        // 第 3 行：奖励说明（精致切角芯片）
        textPaint.textSize = rewardSize
        val rewardChipW = min(w * 0.86f, textPaint.measureText(rewardLine) + 36f * s)
        val rewardTop = y
        val rewardBottom = y + rewardH
        val rewardCy = (rewardTop + rewardBottom) * 0.5f
        val rdx = game.homeRewardDX() * s; val rdy = game.homeRewardDY() * s
        val rewardSc = LayoutConfig.cur.rewardS
        val rewardCx = w / 2f + rdx
        val rewardCyScaled = rewardCy + rdy
        scaleRectAround(
            rewardCx, rewardCyScaled,
            w / 2f - rewardChipW / 2f + rdx, rewardTop + rdy,
            w / 2f + rewardChipW / 2f + rdx, rewardBottom + rdy,
            rewardSc, hitHomeReward
        )
        putDragHit("reward", hitHomeReward)
        if (homeShown("reward")) {
            canvas.save(); canvas.scale(rewardSc, rewardSc, rewardCx, rewardCyScaled)
            val rl = w / 2f - rewardChipW / 2f + rdx
            val rr = w / 2f + rewardChipW / 2f + rdx
            val rt = rewardTop + rdy
            val rb = rewardBottom + rdy
            PixelUi.drawPanel(
                canvas, btnPaint, rl, rt, rr, rb,
                0x99203048.toInt(), 0x5590B8D0.toInt(), s, 7f * s, edgeW = 1.2f * s, shadow = false
            )
            val sparkle = 0.6f + 0.4f * kotlin.math.sin(homePhase * 2f)
            btnPaint.color = withAlpha(0xFFFFFFFF.toInt(), (30 * sparkle).toInt())
            canvas.drawRect(rl + 8f * s, rt + 3f * s, rr - 8f * s, rt + 5f * s, btnPaint)
            pixText(
                canvas, rewardLine, w / 2f + rdx, centeredBaselineY(rewardCy, rewardSize) + rdy,
                rewardSize, 0xDDD0E8FF.toInt(), 0f, 0f
            )
            canvas.restore()
        } else hitHomeReward.setEmpty()
        y = rewardBottom + afterReward

        if (showHint) {
            drawDraggableUiText(
                canvas, "homeHint", hint, w / 2f, y + hintSize * 0.75f,
                hintSize, 0x88FFFFFF.toInt(), sdx, sdy
            )
        }
    }

    /** 钱包铭牌：与「出门」同高同底边，镜像贴在右下角 */
    private fun layoutHomeWalletPlate(w: Float, h: Float, s: Float) {
        val portrait = h > w
        val margin = if (portrait) 18f * s else 28f * s
        val bottomPad = if (portrait) 44f * s else 26f * s
        val plateH = if (portrait) 68f * s else 56f * s
        // 宽度与「出门」完全一致，两块牌子才是严格镜像
        val plateW = if (portrait) min(176f * s, w * 0.44f) else 156f * s
        val bottom = h - bottomPad
        homeWalletPlate.set(w - margin - plateW, bottom - plateH, w - margin, bottom)
        applyUiOverride("wallet", w, h, s, homeWalletPlate)
        homeWalletLabelSize = fittedTextSize(
            "${game.wallet}", if (portrait) 28f * s else 24f * s, homeWalletPlate.width() - 56f * s, 18f * s
        )
    }

    /** 若该 UI 按钮在编辑器里被移动过，用 LayoutConfig 的覆盖矩形替换默认公式结果；再叠玩家偏移 */
    private fun applyUiOverride(id: String, w: Float, h: Float, s: Float, out: RectF) {
        LayoutConfig.uiOverride(id)?.let { out.set(it.resolve(w, h, s)) }
        out.offset(game.homeUiDX(id), game.homeUiDY(id))
    }

  /** 绘制可拖放的家园 UI 文字，并注册热区（偏移存 uiDX/uiDY） */
    private fun drawDraggableUiText(
        canvas: Canvas,
        id: String,
        text: String,
        baseX: Float,
        baseY: Float,
        size: Float,
        color: Int,
        sdx: Float,
        sdy: Float,
        align: Paint.Align = Paint.Align.CENTER,
        padH: Float = 10f,
        padV: Float = 8f
    ) {
        if (!homeShown(id)) return
        val dx = game.homeUiDX(id)
        val dy = game.homeUiDY(id)
        val x = baseX + dx
        val y = baseY + dy
        textPaint.textAlign = align
        textPaint.textSize = size
        val tw = textPaint.measureText(text)
        val th = size
        val (left, right) = when (align) {
            Paint.Align.LEFT -> x to x + tw
            Paint.Align.RIGHT -> x - tw to x
            else -> x - tw / 2f to x + tw / 2f
        }
        dragHitScratch.set(left - padH, y - th - padV, right + padH, y + padV)
        putDragHit(id, dragHitScratch)
        pixText(canvas, text, x, y, size, color, sdx, sdy)
    }

    private fun drawHomeWalletPlate(canvas: Canvas, r: RectF, s: Float, sdx: Float, sdy: Float) {
        val pulse = 0.72f + 0.28f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.4f))
        val accent = withAlpha(0xFFFFD426.toInt(), (pulse * 255).toInt())
        PixelUi.drawWoodPlaque(
            canvas, btnPaint, r.left, r.top, r.right, r.bottom, s,
            PixelUi.WOOD_WALLET, accent, PixelUi.AccentSide.RIGHT
        )

        val labelSize = homeWalletLabelSize
        textPaint.textSize = labelSize
        val textW = textPaint.measureText("${game.wallet}")
        val coinR = 13f * s
        val gap = 10f * s
        val contentW = coinR * 2f + gap + textW
        val contentL = r.centerX() - contentW * 0.5f
        val cy = r.centerY()
        val coinCx = contentL + coinR

        PixelUi.drawCoin(canvas, btnPaint, coinCx, cy, coinR, pulse)

        textPaint.textAlign = Paint.Align.LEFT
        pixText(
            canvas, "${game.wallet}", contentL + coinR * 2f + gap, centeredBaselineY(cy, labelSize),
            labelSize, 0xFFFFE088.toInt(), sdx, sdy
        )
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeNamePlaque(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, s: Float) {
        val pulse = 0.65f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.2f))
        val accent = withAlpha(0xFFFFD426.toInt(), (pulse * 220).toInt())
        PixelUi.drawWoodPlaque(
            canvas, btnPaint, l, t, r, b, s,
            PixelUi.WOOD_NAME, accent, PixelUi.AccentSide.BOTH
        )
    }

    private fun drawTinyHouseIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFF8A5E32.toInt()
        canvas.drawRect(cx - 10f * s, cy - 1f * s, cx + 10f * s, cy + 11f * s, btnPaint)
        btnPaint.color = 0xFFB07A45.toInt()
        canvas.drawRect(cx - 9f * s, cy, cx + 9f * s, cy + 10f * s, btnPaint)
        btnPaint.color = 0xFFC89460.toInt()
        canvas.drawRect(cx - 9f * s, cy, cx + 9f * s, cy + 2f * s, btnPaint)
        btnPaint.color = 0xFFD9483B.toInt()
        canvas.drawRect(cx - 13f * s, cy - 7f * s, cx + 13f * s, cy, btnPaint)
        btnPaint.color = 0xFFE8685B.toInt()
        canvas.drawRect(cx - 12f * s, cy - 7f * s, cx + 12f * s, cy - 5f * s, btnPaint)
        canvas.drawRect(cx - 9f * s, cy - 14f * s, cx + 9f * s, cy - 7f * s, btnPaint)
        btnPaint.color = 0xFFF08070.toInt()
        canvas.drawRect(cx - 8f * s, cy - 14f * s, cx + 8f * s, cy - 12f * s, btnPaint)
        btnPaint.color = 0xFF5A3E22.toInt()
        canvas.drawRect(cx + 2f * s, cy + 1f * s, cx + 7f * s, cy + 10f * s, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(cx + 3f * s, cy + 2f * s, cx + 6f * s, cy + 9f * s, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(cx - 6f * s, cy + 2f * s, cx - 2f * s, cy + 6f * s, btnPaint)
        btnPaint.color = 0xFFFFEBAA.toInt()
        canvas.drawRect(cx - 5f * s, cy + 2f * s, cx - 3f * s, cy + 3f * s, btnPaint)
        btnPaint.color = 0xFFFFE8A0.toInt()
        canvas.drawRect(cx - 1f * s, cy - 10f * s, cx + 1f * s, cy - 6f * s, btnPaint)
    }

    private fun drawHomeEnergyMeter(
        canvas: Canvas, cx: Float, cy: Float, s: Float, lv: Int, sdx: Float, sdy: Float
    ) {
        val label = "家能量"
        val labelSize = 18f * s
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = labelSize
        val labelW = textPaint.measureText(label)
        val pipW = 16f * s
        val pipH = 18f * s
        val pipGap = 6f * s
        val lvLabel = "Lv$lv"
        textPaint.textSize = 18f * s
        val lvW = textPaint.measureText(lvLabel)
        val totalW = labelW + 12f * s + 3f * (pipW + pipGap) - pipGap + 10f * s + lvW
        var x = cx - totalW / 2f

        pixText(canvas, label, x, centeredBaselineY(cy, labelSize), labelSize, 0xFF9AD9A0.toInt(), sdx, sdy)
        x += labelW + 12f * s

        val glow = 0.75f + 0.25f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.4f))
        for (i in 0 until 3) {
            val pipCx = x + pipW / 2f
            PixelUi.drawCrystalPip(canvas, btnPaint, pipCx, cy, pipW, pipH, s, 0xFF7DEBA0.toInt(), i < lv, glow)
            x += pipW + pipGap
        }

        textPaint.textAlign = Paint.Align.LEFT
        pixText(
            canvas, lvLabel, x + 4f * s, centeredBaselineY(cy, 18f * s), 18f * s,
            if (lv > 0) 0xFF7DEBA0.toInt() else 0xFF8899AA.toInt(), sdx, sdy
        )
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeWorldsSheet(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        homeWorldRowCount = 0
        for (i in 0 until Game.UNIVERSE_COUNT) {
            if (game.homeWorldUnlocked(i)) homeWorldRowIds[homeWorldRowCount++] = i
        }
        val lonely = homeWorldRowCount <= 1
        val rowH = 60f * s
        val titleH = 58f * s
        val pw = min(w * 0.78f, 480f * s)
        val ph = 18f * s + titleH + 16f * s + rowH * homeWorldRowCount + (if (lonely) 56f * s else 18f * s)
        val l = w / 2f - pw / 2f
        val t = h / 2f - ph / 2f
        PixelUi.drawPanel(
            canvas, btnPaint, l, t, l + pw, t + ph,
            0xF21C2634.toInt(), 0xFFFFD426.toInt(), s, 14f * s, edgeW = 2.5f * s
        )

        val titleBarT = t + 10f * s
        val titleBarB = titleBarT + titleH
        PixelUi.drawPanel(
            canvas, btnPaint, l + 10f * s, titleBarT, l + pw - 10f * s, titleBarB,
            0xFF2C3A4E.toInt(), 0xFFFFD426.toInt(), s, 8f * s, edgeW = 1.5f * s, shadow = false
        )
        pixText(canvas, "去哪个家", w / 2f, centeredBaselineY((titleBarT + titleBarB) * 0.5f, 28f * s),
            28f * s, 0xFFFFE8A8.toInt(), sdx, sdy)

        var ry = titleBarB + 16f * s
        for (n in 0 until homeWorldRowCount) {
            val i = homeWorldRowIds[n]
            val here = i == game.homeWorld
            val r = homeWorldRowHits[n]
            val rowPad = 8f * s
            r.set(l + 16f * s, ry, l + pw - 16f * s, ry + rowH - 8f * s)

            val fillColor = if (here) 0xFF344860.toInt() else 0xFF1C2636.toInt()
            val edgeColor = withAlpha(UNI_HUD[i], if (here) 0xFF else 0x88)
            PixelUi.drawRect(
                canvas, btnPaint, r.left, r.top, r.right, r.bottom,
                fillColor, s,
                bevel = if (here) PixelUi.Bevel.PRESSED else PixelUi.Bevel.RAISED,
                edge = edgeColor, edgeW = max(1.5f, if (here) 2.5f else 1.5f) * s,
                shadow = here
            )

            if (here) {
                btnPaint.style = Paint.Style.FILL
                btnPaint.color = withAlpha(UNI_HUD[i], 50)
                canvas.drawRect(r.left + 3f * s, r.top + 3f * s, r.right - 3f * s, r.top + 6f * s, btnPaint)
            }

            val dotCx = r.left + 24f * s
            val dotCy = r.centerY()
            val dotR = 8f * s
            btnPaint.color = UNI_HUD[i]
            canvas.drawCircle(dotCx, dotCy, dotR, btnPaint)
            btnPaint.color = PixelUi.lighten(UNI_HUD[i], 0.5f)
            canvas.drawCircle(dotCx - dotR * 0.3f, dotCy - dotR * 0.3f, dotR * 0.4f, btnPaint)

            textPaint.textAlign = Paint.Align.LEFT
            val textX = dotCx + dotR + 14f * s
            pixText(
                canvas, Game.UNIVERSE_NAMES[i], textX, centeredBaselineY(r.centerY(), 24f * s),
                24f * s, if (here) 0xFFFFE8A8.toInt() else 0xFFE8EEF8.toInt(), 0f, 0f
            )

            if (here) {
                textPaint.textAlign = Paint.Align.RIGHT
                pixText(
                    canvas, "在住", r.right - 18f * s, centeredBaselineY(r.centerY(), 18f * s),
                    18f * s, UNI_HUD[i], 0f, 0f
                )
            }
            textPaint.textAlign = Paint.Align.CENTER
            ry += rowH
        }
        if (lonely) {
            pixText(
                canvas, "跑酷时穿越传送门，就能解锁那个世界的家",
                w / 2f, ry + 30f * s, 17f * s, 0x88CCE0FF.toInt(), 0f, 0f
            )
        }
    }

    private fun handleHomeWorldsTap(x: Float, y: Float) {
        for (n in 0 until homeWorldRowCount) {
            if (!homeWorldRowHits[n].contains(x, y)) continue
            val name = game.setHomeWorld(homeWorldRowIds[n])
            if (name.isNotEmpty()) showToast("来到${name}的家")
            showHomeWorlds = false
            invalidate()
            return
        }
        showHomeWorlds = false   // 点空处即关闭
        invalidate()
    }

    /** 主菜单右下角「家」入口：更精致的像素小房子 */
    private fun drawHomeEntryBtn(canvas: Canvas, r: RectF, s: Float) {
        PixelUi.drawWoodPlaque(
            canvas, btnPaint, r.left, r.top, r.right, r.bottom, s,
            PixelUi.WOOD_NAME, 0xFFFFD426.toInt(), PixelUi.AccentSide.BOTH
        )
        val cx = r.centerX()
        val cy = r.centerY() + 1f * s
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFF8A5E32.toInt()
        canvas.drawRect(cx - 15f * s, cy + 1f * s, cx + 15f * s, cy + 17f * s, btnPaint)
        btnPaint.color = 0xFFB07A45.toInt()
        canvas.drawRect(cx - 14f * s, cy + 2f * s, cx + 14f * s, cy + 16f * s, btnPaint)
        btnPaint.color = 0xFFC89460.toInt()
        canvas.drawRect(cx - 14f * s, cy + 2f * s, cx + 14f * s, cy + 4f * s, btnPaint)
        btnPaint.color = 0xFFD9483B.toInt()
        canvas.drawRect(cx - 19f * s, cy - 9f * s, cx + 19f * s, cy + 2f * s, btnPaint)
        btnPaint.color = 0xFFE8685B.toInt()
        canvas.drawRect(cx - 18f * s, cy - 9f * s, cx + 18f * s, cy - 6f * s, btnPaint)
        canvas.drawRect(cx - 13f * s, cy - 18f * s, cx + 13f * s, cy - 9f * s, btnPaint)
        btnPaint.color = 0xFFF08070.toInt()
        canvas.drawRect(cx - 12f * s, cy - 18f * s, cx + 12f * s, cy - 15f * s, btnPaint)
        btnPaint.color = 0xFF5A3E22.toInt()
        canvas.drawRect(cx + 5f * s, cy + 3f * s, cx + 11f * s, cy + 16f * s, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(cx + 6f * s, cy + 4f * s, cx + 10f * s, cy + 15f * s, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(cx - 7f * s, cy + 4f * s, cx - 2f * s, cy + 9f * s, btnPaint)
        btnPaint.color = 0xFFFFEBAA.toInt()
        canvas.drawRect(cx - 6f * s, cy + 4f * s, cx - 4f * s, cy + 5f * s, btnPaint)
        btnPaint.color = 0xFFFFE8A0.toInt()
        canvas.drawRect(cx - 1f * s, cy - 13f * s, cx + 1f * s, cy - 8f * s, btnPaint)
    }

    /** 家页左下角「出门」：木牌胶囊 + 像素门与箭头 */
    private fun layoutLeaveHomeButton(w: Float, h: Float, s: Float) {
        val portrait = h > w
        val margin = if (portrait) 18f * s else 28f * s
        val bottomPad = if (portrait) 44f * s else 26f * s
        val btnH = if (portrait) 68f * s else 56f * s
        val btnW = if (portrait) min(176f * s, w * 0.44f) else 156f * s
        val bottom = h - bottomPad
        btnLeaveHome.set(margin, bottom - btnH, margin + btnW, bottom)
        applyUiOverride("leave", w, h, s, btnLeaveHome)
    }

    private fun drawLeaveHomeBtn(canvas: Canvas, r: RectF, s: Float) {
        val pulse = 0.72f + 0.28f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.4f))
        val accent = withAlpha(0xFFFFD426.toInt(), (pulse * 255).toInt())
        PixelUi.drawWoodPlaque(
            canvas, btnPaint, r.left, r.top, r.right, r.bottom, s,
            PixelUi.WOOD_LEAVE, accent, PixelUi.AccentSide.LEFT
        )

        val doorCx = r.left + 36f * s
        val doorCy = r.centerY()
        val doorW = 11f * s
        val doorH = 16f * s

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFF4A3A28.toInt()
        canvas.drawRect(doorCx - doorW - 1f * s, doorCy - doorH - 1f * s, doorCx + doorW + 1f * s, doorCy + doorH + 1f * s, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(doorCx - doorW, doorCy - doorH, doorCx + doorW, doorCy + doorH, btnPaint)
        btnPaint.color = 0xFF8A6A44.toInt()
        canvas.drawRect(doorCx - doorW + 2f * s, doorCy - doorH + 2f * s, doorCx - doorW + 4f * s, doorCy + doorH - 2f * s, btnPaint)
        btnPaint.color = 0xFF553A20.toInt()
        canvas.drawRect(doorCx - 2f * s, doorCy - doorH, doorCx + doorW - 2f * s, doorCy + doorH, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(doorCx - doorW, doorCy - doorH, doorCx + doorW, doorCy - doorH + 4f * s, btnPaint)

        val knobGlow = 0.7f + 0.3f * kotlin.math.sin(homePhase * 3.2f)
        btnPaint.color = withAlpha(0xFFFFD426.toInt(), (knobGlow * 255).toInt())
        canvas.drawCircle(doorCx + doorW - 4f * s, doorCy + 1f * s, 3f * s, btnPaint)
        btnPaint.color = withAlpha(0xFFFFF0B0.toInt(), (knobGlow * 200).toInt())
        canvas.drawCircle(doorCx + doorW - 5f * s, doorCy - 0.5f * s, 1.5f * s, btnPaint)

        val arrowBaseX = doorCx + doorW + 12f * s
        val arrowColor = withAlpha(0xFF7DEBA0.toInt(), (180 + 75 * pulse).toInt())
        btnPaint.color = arrowColor
        val arrowW = 16f * s
        val shaftY = doorCy
        canvas.drawRect(arrowBaseX, shaftY - 2f * s, arrowBaseX + arrowW - 4f * s, shaftY + 2f * s, btnPaint)
        val headX = arrowBaseX + arrowW - 4f * s
        canvas.drawRect(headX - 2f * s, shaftY - 6f * s, headX + 2f * s, shaftY - 3f * s, btnPaint)
        canvas.drawRect(headX, shaftY - 3f * s, headX + 4f * s, shaftY, btnPaint)
        canvas.drawRect(headX, shaftY, headX + 4f * s, shaftY + 3f * s, btnPaint)
        canvas.drawRect(headX - 2f * s, shaftY + 3f * s, headX + 2f * s, shaftY + 6f * s, btnPaint)
        btnPaint.color = withAlpha(0xFFB8FFD0.toInt(), (knobGlow * 180).toInt())
        canvas.drawRect(arrowBaseX, shaftY - 2f * s, arrowBaseX + arrowW * 0.5f, shaftY, btnPaint)

        textPaint.textAlign = Paint.Align.LEFT
        val labelSize = min(28f * s, r.height() * 0.38f).coerceAtLeast(22f * s)
        pixText(canvas, "出门", r.left + 76f * s, centeredBaselineY(r.centerY(), labelSize), labelSize, 0xFFF0F8FF.toInt(), 0.5f, 0.5f)
        textPaint.textAlign = Paint.Align.CENTER
    }

    /**
     * 就地选物：点院子里的物件，底下升起这一格挑选面板。
     *
     * 替掉了原先的商店条——7 个分类 tab + 左右翻页箭头 + 购买按钮 + 名称/价格/状态
     * 三段文字，一共 12 个控件，只为一次挑一件。现在**一屏摊开所有货**：
     * 一行一个分类，格子里就是货名，点哪个换哪个。
     *
     * 两种点击语义（不再需要单独的「购买」按钮）：
     * - 已拥有 → 点一下直接换上；反正不花钱，没有误触成本
     * - 未拥有 → 第一下只把它套到场景里预览（幽灵），同一格再点一下才扣钱
     */
    private fun drawHomePickPanel(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        pickChipCount = 0
        val portrait = h > w
        val rows = pickRowsFor(game.homeTab)
        val rowH = if (portrait) 64f * s else 54f * s
        val padH = 18f * s
        val labelW = 60f * s
        val pw = min(w * 0.94f, 640f * s)
        val titleH = 50f * s
        val footerH = 34f * s
        val ph = titleH + rows.size * rowH + footerH + 8f * s
        val l = w / 2f - pw / 2f
        val b = h - (if (portrait) 120f else 90f) * s
        val t = b - ph
        pickPanelRect.set(l, t, l + pw, b)

        PixelUi.drawPanel(
            canvas, btnPaint, l, t, l + pw, b,
            0xF01C2634.toInt(), 0xFFFFD426.toInt(), s, 12f * s, edgeW = 2.5f * s
        )

        val titleBarT = t + 6f * s
        val titleBarB = t + titleH
        PixelUi.drawPanel(
            canvas, btnPaint, l + 8f * s, titleBarT, l + pw - 8f * s, titleBarB,
            0xFF2C3A4E.toInt(), 0xFFFFD426.toInt(), s, 8f * s, edgeW = 1.5f * s, shadow = false
        )

        val title = pickTitleFor(game.homeTab)
        textPaint.textAlign = Paint.Align.LEFT
        pixText(canvas, title, l + padH + 4f * s, centeredBaselineY((titleBarT + titleBarB) * 0.5f, 22f * s),
            22f * s, 0xFFFFE8A8.toInt(), sdx, sdy)

        val coinPulse = 0.75f + 0.25f * kotlin.math.sin(homePhase * 2.8f)
        val coinCx = l + pw - padH - 10f * s
        val coinCy = (titleBarT + titleBarB) * 0.5f
        PixelUi.drawCoin(canvas, btnPaint, coinCx - 28f * s, coinCy, 10f * s, coinPulse)
        textPaint.textAlign = Paint.Align.RIGHT
        pixText(canvas, "${game.wallet}", l + pw - padH, centeredBaselineY(coinCy, 20f * s),
            20f * s, 0xFFFFD75E.toInt(), sdx, sdy)
        textPaint.textAlign = Paint.Align.CENTER

        val dividerY = titleBarB + 6f * s
        PixelUi.drawDivider(canvas, btnPaint, l + padH, dividerY, l + pw - padH, s, 0x66FFD426.toInt())

        var ry = titleBarB + 12f * s
        for (row in rows) {
            textPaint.textAlign = Paint.Align.LEFT
            pixText(
                canvas, row.label, l + padH, centeredBaselineY(ry + rowH / 2f, 18f * s),
                18f * s, 0xBBBBCCDD.toInt(), sdx, sdy
            )
            textPaint.textAlign = Paint.Align.CENTER

            val gap = 8f * s
            val trackL = l + padH + labelW
            val trackR = l + pw - padH
            val n = pickItemCount(row.tab)
            if (n > 0) {
                val cw = (trackR - trackL - gap * (n - 1)) / n
                var slot = 0
                for (i in 0 until pickIndexCount(row.tab)) {
                    if (!pickItemVisible(row.tab, i)) continue
                    if (pickChipCount >= pickChipRects.size) break
                    val cl = trackL + slot * (cw + gap)
                    drawPickChip(canvas, row.tab, i, cl, ry + 4f * s, cl + cw, ry + rowH - 6f * s, s, sdx, sdy)
                    slot++
                }
            }
            ry += rowH
        }

        val hint = if (pickArmedTab >= 0) "再点一次买下  ·  点别处收起" else "点别处收起"
        val hintAlpha = (0.55f + 0.25f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 1.6f))).coerceIn(0.4f, 1f)
        pixText(canvas, hint, w / 2f, b - 12f * s, 16f * s, withAlpha(0xCCDDEEFF.toInt(), (hintAlpha * 255).toInt()), 0f, 0f)
    }

    private fun drawPickChip(
        canvas: Canvas, tab: Int, i: Int,
        cl: Float, ct: Float, cr: Float, cb: Float, s: Float, sdx: Float, sdy: Float
    ) {
        val owned = pickOwned(tab, i)
        val equipped = pickEquipped(tab, i)
        val armed = pickArmedTab == tab && pickArmedIdx == i
        val price = pickPrice(tab, i)
        val afford = owned || game.wallet >= price
        val swatch = pickSwatch(tab, i)

        val chipPad = 2f * s
        val fill = when {
            swatch != 0 -> if (afford) swatch else withAlpha(swatch, 0x55)
            equipped -> 0xFF3C5070.toInt()
            afford -> 0xFF253244.toInt()
            else -> 0xFF181E28.toInt()
        }
        val edge = when {
            equipped -> 0xFFFFD426.toInt()
            armed -> withAlpha(0xFFFFD426.toInt(), (150 + 105 * (0.5f + 0.5f * kotlin.math.sin(homePhase * 7f))).toInt())
            owned -> 0x99D4E4FF.toInt()
            afford -> 0x55A0B4CC.toInt()
            else -> 0x33506078.toInt()
        }
        val edgeW = max(1.5f, if (equipped || armed) 2.5f else 1.5f) * s

        PixelUi.drawRect(
            canvas, btnPaint, cl + chipPad, ct + chipPad, cr - chipPad, cb - chipPad, fill, s,
            bevel = if (equipped || armed) PixelUi.Bevel.PRESSED else PixelUi.Bevel.RAISED,
            edge = edge, edgeW = edgeW, shadow = equipped
        )

        if (equipped) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = withAlpha(0xFFFFD426.toInt(), 60)
            canvas.drawRect(cl + chipPad + 2f * s, ct + chipPad + 2f * s, cr - chipPad - 2f * s, ct + chipPad + 5f * s, btnPaint)
        }

        val cx = (cl + cr) / 2f
        val cy = (ct + cb) / 2f
        val maxW = (cr - cl) - 12f * s
        val name = pickName(tab, i)

        if (swatch != 0) {
            if (!afford) {
                btnPaint.style = Paint.Style.FILL
                btnPaint.color = 0x66101820.toInt()
                canvas.drawRect(cl + chipPad + 1f * s, ct + chipPad + 1f * s, cr - chipPad - 1f * s, cb - chipPad - 1f * s, btnPaint)
            }
            val ns = fittedTextSize(name, 14f * s, maxW, 10f * s)
            val bgH = ns + 5f * s
            btnPaint.color = withAlpha(0xFF101820.toInt(), 180)
            canvas.drawRect(cl + chipPad + 1f * s, cb - chipPad - bgH - 2f * s, cr - chipPad - 1f * s, cb - chipPad - 1f * s, btnPaint)
            pixText(canvas, name, cx, cb - chipPad - bgH / 2f + ns * 0.35f, ns,
                if (afford) 0xFFFFF0D0.toInt() else 0xFF8090A0.toInt(), 0f, 0f)
        } else {
            val nameY = if (owned) cy else cy - 6f * s
            val ns = fittedTextSize(name, 18f * s, maxW, 12f * s)
            pixText(
                canvas, name, cx, centeredBaselineY(nameY, ns), ns,
                if (afford) 0xFFF0F4FF.toInt() else 0xFF606E80.toInt(), sdx * 0.5f, sdy * 0.5f
            )
        }

        if (owned && equipped) {
            val checkSize = 11f * s
            val checkX = cr - chipPad - checkSize - 3f * s
            val checkY = ct + chipPad + 4f * s
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = 0xFF3F9E5A.toInt()
            canvas.drawRect(checkX, checkY, checkX + checkSize, checkY + checkSize, btnPaint)
            btnPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawRect(checkX + 2f * s, checkY + 5f * s, checkX + 4.5f * s, checkY + 9f * s, btnPaint)
            canvas.drawRect(checkX + 4.5f * s, checkY + 7f * s, checkX + 8.5f * s, checkY + 9.5f * s, btnPaint)
        }

        if (!owned) {
            val ps = fittedTextSize("$price", 14f * s, maxW, 10f * s)
            val priceH = ps + 5f * s
            btnPaint.color = withAlpha(
                if (afford) 0xFF2A1F08.toInt() else 0xFF2A1010.toInt(),
                190
            )
            canvas.drawRect(cl + chipPad + 2f * s, cb - chipPad - priceH - 1f * s, cr - chipPad - 2f * s, cb - chipPad - 1f * s, btnPaint)
            pixText(
                canvas, "$price", cx, cb - chipPad - priceH / 2f + ps * 0.35f, ps,
                if (afford) 0xFFFFD75E.toInt() else 0xFFE07060.toInt(),
                0f, 0f
            )
        }

        if (armed) {
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 2f * s
            btnPaint.color = withAlpha(0xFFFFD426.toInt(),
                (80 + 80 * (0.5f + 0.5f * kotlin.math.sin(homePhase * 6f))).toInt())
            canvas.drawRect(cl, ct, cr, cb, btnPaint)
            btnPaint.style = Paint.Style.FILL
        }

        val r = pickChipRects[pickChipCount]
        r.set(cl, ct, cr, cb)
        pickChipTab[pickChipCount] = tab
        pickChipIdx[pickChipCount] = i
        pickChipCount++
    }

    /**
     * @return true 表示这一下归选物面板，已消费。
     *
     * 落在面板外就收起面板但**不消费**——让这一下继续往下走到常规热区。
     * 于是「点房子 → 点猫」这种连着换品类的操作还是一下一个，不用先点空白关一次；
     * 而点在什么都没有的地方就单纯是关掉。原先靠 hitSky 一个矩形来关，
     * 天空之外（如远景山、地面）点了没反应，收不起来。
     */
    private fun handlePickTap(x: Float, y: Float): Boolean {
        if (!pickPanelRect.contains(x, y)) {
            closeHomePick()
            return false
        }
        for (n in 0 until pickChipCount) {
            if (!pickChipRects[n].contains(x, y)) continue
            val tab = pickChipTab[n]
            val i = pickChipIdx[n]
            val armed = pickArmedTab == tab && pickArmedIdx == i
            game.switchHomeTab(tab)          // 会把游标复位成当前在用的那件
            game.setHomeBrowse(tab, i)       // 所以紧接着才把游标挪到点中的这件
            if (pickOwned(tab, i) || armed) {
                showToast(game.buyOrEquipHome())
                pickArmedTab = -1
                pickArmedIdx = -1
            } else {
                // 先套上看看：场景里出的是幽灵预览，钱还没动
                pickArmedTab = tab
                pickArmedIdx = i
                showToast("${pickName(tab, i)} · ${pickPrice(tab, i)} 金币，再点一次买下")
            }
            invalidate()
            return true
        }
        return true   // 点在面板底板上：什么也不做，但别漏给下面的院子
    }

    private class PickRow(val tab: Int, val label: String)

    /**
     * 一个入口摊开一族货：点房子出「小屋 + 屋顶」，点猫出四行装扮。
     * 分类不再是要先点的 tab，而是面板上的行号——少一层点击。
     */
    private fun pickRowsFor(tab: Int): Array<PickRow> = when {
        tab == Game.HOME_TAB_DECO -> PICK_ROWS_DECO
        game.isCatHomeTab(tab) -> PICK_ROWS_CAT
        else -> PICK_ROWS_HOUSE
    }

    private fun pickTitleFor(tab: Int): String = when {
        tab == Game.HOME_TAB_DECO -> "院子里摆点什么"
        game.isCatHomeTab(tab) -> "给猫换身行头"
        else -> "翻修一下房子"
    }

    /** 该分类共有几件（含本世界不供应的），用于遍历上界 */
    private fun pickIndexCount(tab: Int): Int = when (tab) {
        Game.HOME_TAB_HOUSE -> Game.HOUSE_NAMES.size
        Game.HOME_TAB_ROOF -> Game.ROOF_NAMES.size
        Game.HOME_TAB_DECO -> Game.DECO_NAMES.size
        Game.HOME_TAB_COLOR -> Game.COLOR_NAMES.size
        Game.HOME_TAB_TRAIL -> Game.TRAIL_NAMES.size
        Game.HOME_TAB_SCARF -> Game.SCARF_NAMES.size
        else -> Game.HAT_NAMES.size
    }

    /** 摆件按世界供应，别的分类全世界通用 */
    private fun pickItemVisible(tab: Int, i: Int): Boolean =
        tab != Game.HOME_TAB_DECO || HomeWorldContent.decoAvailable(game.homeWorld, i)

    private fun pickItemCount(tab: Int): Int {
        var n = 0
        for (i in 0 until pickIndexCount(tab)) if (pickItemVisible(tab, i)) n++
        return n
    }

    private fun pickName(tab: Int, i: Int): String = when (tab) {
        Game.HOME_TAB_HOUSE -> game.homeHouseName(i)
        Game.HOME_TAB_ROOF -> Game.ROOF_NAMES[i]
        Game.HOME_TAB_DECO -> game.homeDecoName(i)
        Game.HOME_TAB_COLOR -> Game.COLOR_NAMES[i]
        Game.HOME_TAB_TRAIL -> Game.TRAIL_NAMES[i]
        Game.HOME_TAB_SCARF -> Game.SCARF_NAMES[i]
        else -> Game.HAT_NAMES[i]
    }

    private fun pickPrice(tab: Int, i: Int): Int = when (tab) {
        Game.HOME_TAB_HOUSE -> Game.HOUSE_PRICES[i]
        Game.HOME_TAB_ROOF -> Game.ROOF_PRICES[i]
        Game.HOME_TAB_DECO -> Game.DECO_PRICES[i]
        Game.HOME_TAB_COLOR -> Game.COLOR_PRICES[i]
        Game.HOME_TAB_TRAIL -> Game.TRAIL_PRICES[i]
        Game.HOME_TAB_SCARF -> Game.SCARF_PRICES[i]
        else -> Game.HAT_PRICES[i]
    }

    private fun pickOwned(tab: Int, i: Int): Boolean = when (tab) {
        Game.HOME_TAB_HOUSE -> game.ownsHouse(i)
        Game.HOME_TAB_ROOF -> game.ownsRoof(i)
        Game.HOME_TAB_DECO -> game.ownsDeco(i)
        Game.HOME_TAB_COLOR -> game.ownsColor(i)
        Game.HOME_TAB_TRAIL -> game.ownsTrail(i)
        Game.HOME_TAB_SCARF -> game.ownsScarf(i)
        else -> game.ownsHat(i)
    }

    /** 摆件没有「换上」一说，买了就一直摆着，故拥有即视为在用 */
    private fun pickEquipped(tab: Int, i: Int): Boolean = when (tab) {
        Game.HOME_TAB_HOUSE -> game.houseStyle == i
        Game.HOME_TAB_ROOF -> game.roofStyle == i
        Game.HOME_TAB_DECO -> game.ownsDeco(i)
        Game.HOME_TAB_COLOR -> game.catColor == i
        Game.HOME_TAB_TRAIL -> game.trailStyle == i
        Game.HOME_TAB_SCARF -> game.scarfStyle == i
        else -> game.hatStyle == i
    }

    /** 货本身就是个颜色时返回它，格子直接刷成这个色；0 表示该用文字 */
    private fun pickSwatch(tab: Int, i: Int): Int = when (tab) {
        Game.HOME_TAB_ROOF -> HomePalette.ROOF_CHIPS[i % HomePalette.ROOF_CHIPS.size]
        Game.HOME_TAB_COLOR -> COLOR_CHIPS[i % COLOR_CHIPS.size]
        Game.HOME_TAB_TRAIL -> TRAIL_CHIPS[i % TRAIL_CHIPS.size]
        else -> 0
    }

    private fun openStargazing() {
        cancelYardTouch()
        showStargazing = true
        stargazeDoneHint = ""
        val unread = game.firstUnreadStargazeBody()
        stargazeBody = when {
            unread >= 0 && game.canUnlockNewStargazeToday() -> unread
            else -> {
                val cur = stargazeBody.coerceIn(0, game.telescopeLevel)
                if (game.stargazeCardRead(cur)) cur
                else (0..game.telescopeLevel).lastOrNull { game.stargazeCardRead(it) } ?: 0
            }
        }
        applyStargazeView(stargazeBody)
        game.yardStargaze()
    }

    private fun navigateStargazeBody(delta: Int, force: Boolean = false) {
        val next = (stargazeBody + delta).coerceIn(0, game.telescopeLevel)
        if (next == stargazeBody) return
        if (!force && !game.stargazeCardRead(next) && !game.canUnlockNewStargazeToday()) {
            showToast("今日新观测已完成，明天再来读新卡")
            return
        }
        stargazeBody = next
        applyStargazeView(next)
    }

    private fun applyStargazeView(body: Int) {
        when (game.onStargazeView(body)) {
            Game.StargazeViewResult.NEW_READ -> {
                stargazeDoneHint = "今日新卡已读完，去抬头看看真实夜空吧"
            }
            Game.StargazeViewResult.BLOCKED_NEW -> {
                if (!game.stargazeCardRead(body)) {
                    showToast("今日新观测已完成，可先复习已读卡片")
                    stargazeBody = (0..game.telescopeLevel).firstOrNull { game.stargazeCardRead(it) } ?: 0
                }
            }
            Game.StargazeViewResult.REVIEW -> Unit
        }
    }

    private fun closeStargazing() {
        showStargazing = false
        if (stargazeDoneHint.isNotEmpty()) showToast(stargazeDoneHint)
        stargazeDoneHint = ""
    }


    /** 绕锚点缩放矩形，用于顶栏文字的缩放与点击热区对齐 */
    private fun scaleRectAround(px: Float, py: Float, l: Float, t: Float, r: Float, b: Float, sc: Float, out: RectF) {
        out.set(px + (l - px) * sc, py + (t - py) * sc, px + (r - px) * sc, py + (b - py) * sc)
    }

    /** 把屏幕点打到院子地面上作为叫猫的落点；打不到（指到天上了）返回 false */
    private fun aimHandAt(screenX: Float, screenY: Float): Boolean {
        if (!HomeYard.screenToGround(screenX, screenY, width.toFloat(), height.toFloat(), groundHit)) return false
        handTargetX = groundHit[0].coerceIn(-HomeYard.WALK_HALF_X, HomeYard.WALK_HALF_X)
        handTargetZ = groundHit[1].coerceIn(HomeYard.WALK_FAR_Z, HomeYard.WALK_NEAR_Z)
        handMarkerLife = 2.5f
        return true
    }

    private fun snapDragPx(v: Float): Float =
        kotlin.math.round(v / HOME_DRAG_SNAP_PX) * HOME_DRAG_SNAP_PX

    /**
     * 奖牌盘面：lv 0 为未解锁的灰位，1/2/3/4 对应铜 / 银 / 金 / 钻。
     * 档位色是语义色（金牌就该是金的），不随世界换；[tint] 供远景做大气透视时统一褪色。
     */
    private fun drawMedal(
        canvas: Canvas, cx: Float, cy: Float, r: Float, lv: Int,
        tint: ((Int) -> Int)? = null
    ) {
        fun t(c: Int) = tint?.invoke(c) ?: c
        if (lv <= 0) {
            pixDisc(canvas, cx, cy, r, t(0xFF8A7561.toInt()))
            pixDisc(canvas, cx, cy, r * 0.72f, t(0xFF6B5B4B.toInt()))
            return
        }
        val face = when (lv) {
            1 -> 0xFFC87A3A.toInt()
            2 -> 0xFFAEB6C4.toInt()
            3 -> 0xFFF2C14E.toInt()
            else -> 0xFF6FD3E0.toInt()
        }
        val glow = when (lv) {
            1 -> 0xFFE8AE74.toInt()
            2 -> 0xFFDCE3EC.toInt()
            3 -> 0xFFFFEDB0.toInt()
            else -> 0xFFCBF3F9.toInt()
        }
        pixDisc(canvas, cx, cy, r, t(darken(face)))
        pixDisc(canvas, cx, cy, r * 0.82f, t(face))
        pixDisc(canvas, cx, cy, r * 0.44f, t(glow))
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = t(0xCCFFFFFF.toInt())
        canvas.drawRect(cx - r * 0.62f, cy - r * 0.5f, cx - r * 0.36f, cy - r * 0.24f, btnPaint)
    }

    /** 奖牌上方的挂钉与绶带；topY 为盘面顶端 */
    private fun drawMedalRibbon(canvas: Canvas, cx: Float, topY: Float, r: Float, lv: Int) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFF5B5046.toInt()
        canvas.drawRect(cx - r * 0.18f, topY - r * 1.0f, cx + r * 0.18f, topY - r * 0.64f, btnPaint)
        btnPaint.color = if (lv > 0) 0xFFA83B3B.toInt() else 0xFF7A6C5C.toInt()
        canvas.drawRect(cx - r * 0.64f, topY - r * 0.64f, cx - r * 0.18f, topY + r * 0.18f, btnPaint)
        canvas.drawRect(cx + r * 0.18f, topY - r * 0.64f, cx + r * 0.64f, topY + r * 0.18f, btnPaint)
    }

    /** 像素圆盘：5 段等高横条拼出圆形，用于奖牌盘面 */
    private fun pixDisc(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        if (r <= 0f) return
        val f = floatArrayOf(0.5f, 0.86f, 1f, 0.86f, 0.5f)
        val band = r * 2f / 5f
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = color
        for (i in 0..4) {
            val hw = r * f[i]
            canvas.drawRect(cx - hw, cy - r + i * band, cx + hw, cy - r + (i + 1) * band, btnPaint)
        }
    }

    private fun darken(c: Int): Int = PixelUi.darken(c)

    private fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)

    // ---------- 家园 3D 场景的交互层 ----------

    /**
     * 家页面这一帧要画的**只剩交互层**：场景本身由 [GameRenderer] 在 GL 线程出图，
     * 这里做三件事——推进庭院里那只猫、把渲染层投影出来的热区接过来、画贴在屏幕上的小东西
     * （编辑描边 / 爱心 / 飘字）。
     *
     * 换句话说，HudView 不再复刻一遍院子的布局公式。热区来自「实际画出来的那个盒子」，
     * 所以拖动、缩放、换房型、横竖屏切换全都自动对得上，不用两头改坐标。
     */
    private fun drawHomeScene(canvas: Canvas, w: Float, h: Float, s: Float) {
        val dt = 0.016f
        yardSceneS = s
        yardSceneHit.set(0f, 0f, w, h)
        updateYardCat(dt)
        syncHomeHits(w, h)

        if (homeEditing) {
            val pulse = if (kotlin.math.sin(homePhase * 4f) > 0f) 0x44FFD426 else 0x22FFD426
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 2f
            btnPaint.color = pulse
            when (game.homeTab) {
                Game.HOME_TAB_HOUSE, Game.HOME_TAB_ROOF -> canvas.drawRect(hitHouse, btnPaint)
                Game.HOME_TAB_DECO -> for (id in ownedDecoDragId) {
                    homeDragHits[id]?.let { canvas.drawRect(it, btnPaint) }
                }
                Game.HOME_TAB_COLOR, Game.HOME_TAB_TRAIL, Game.HOME_TAB_SCARF, Game.HOME_TAB_HAT ->
                    canvas.drawRect(yardCatHit, btnPaint)
            }
            btnPaint.style = Paint.Style.FILL
        }

        if (catState == CAT_PET && !yardCatHit.isEmpty) {
            drawPetHearts(canvas, yardCatHit.centerX(), yardCatHit.top, s)
        }
        drawYardFloaters(canvas, s)
    }

    /** 把渲染层投影出来的屏幕矩形接成热区与拖放目标 */
    private fun syncHomeHits(w: Float, h: Float) {
        if (!HomeYard.ready()) return
        HomeYard.hit(HomeYard.HOUSE, w, h, hitHouse)
        HomeYard.hit(HomeYard.DOOR, w, h, hitDoor)
        HomeYard.hit(HomeYard.MUSEUM, w, h, hitMuseum)
        HomeYard.hit(HomeYard.HONOR, w, h, hitHonorWall)
        HomeYard.hit(HomeYard.CAT, w, h, yardCatHit)
        HomeYard.hit(HomeYard.BUTTERFLY, w, h, butterflyHit)
        HomeYard.hit(HomeYard.TELESCOPE, w, h, yardTelescopeHit)
        if (!hitHouse.isEmpty) putDragHit(HomeYard.HOUSE, hitHouse)
        if (!hitDoor.isEmpty) putDragHit(HomeYard.DOOR, hitDoor)
        if (!hitHonorWall.isEmpty) putDragHit(HomeYard.HONOR, hitHonorWall)
        if (!hitMuseum.isEmpty) putDragHit(HomeYard.MUSEUM, hitMuseum)
        if (!yardCatHit.isEmpty) putDragHit(HomeYard.CAT, yardCatHit)
        for (id in ownedDecoDragId) {
            if (HomeYard.hit(id, w, h, dragHitScratch)) putDragHit(id, dragHitScratch)
        }
    }

    private fun updateYardTouch(dt: Float) {
        strokeCoinCooldown = max(0f, strokeCoinCooldown - dt)
        handMarkerLife = max(0f, handMarkerLife - dt)
        HomeYard.handLife = handMarkerLife
        var fi = yardFloaters.size - 1
        while (fi >= 0) {
            val f = yardFloaters[fi]
            f.life -= dt
            f.y -= 28f * dt * yardSceneS
            if (f.life <= 0f) yardFloaters.removeAt(fi)
            fi--
        }
        if (homeDragActive) return
        if (yardFingerDown && yardCatHit.contains(yardFingerX, yardFingerY)) {
            val hold = SystemClock.uptimeMillis() - yardFingerDownTime
            if (!strokeActive && !yardLongPressTriggered && hold >= YARD_LONG_PRESS_MS) {
                yardLongPressTriggered = true
                startStrokeCat()
            }
        }
        if (strokeActive) {
            if (!yardFingerDown || !yardCatHit.contains(yardFingerX, yardFingerY)) {
                endStrokeCat()
            } else {
                updateStroke(dt)
            }
        }
    }

    /**
     * 庭院猫的状态机：站立张望 → 走向目标（散步点或某件已购装饰）→ 互动 → 继续。
     *
     * 3D 化之后它在 x/z 两个方向上走，而不再是沿一条横线滑动——「绕到花坛那边去闻一闻」
     * 这件事只有在有纵深的院子里才成立。位置与姿势每帧发布给 [HomeYard]，渲染层照着画。
     */
    private fun updateYardCat(dt: Float) {
        updateYardTouch(dt)
        catTimer -= dt
        yardEventTimer -= dt
        updateButterfly(dt)

        when (catState) {
            CAT_IDLE -> if (catTimer <= 0f) pickYardGoal()
            CAT_WALK -> {
                if (stepToward(catTargetX, catTargetZ, 1.9f, dt)) {
                    if (pendingYardEvent != YARD_EVENT_NONE) {
                        resolveYardEvent()
                        catState = CAT_IDLE
                        catTimer = 1.2f + catRnd.nextFloat() * 1.5f
                    } else if (catDeco >= 0) {
                        catState = CAT_PLAY
                        catStage = 0
                        catStageT = 0f
                        catTimer = when (catDeco) {
                            3 -> 4.5f      // 秋千
                            4 -> 99f       // 爬架由分段控制
                            5 -> 7f        // 泳池由分段控制
                            else -> 3.2f
                        }
                        faceTo(decoX(catDeco), decoZ(catDeco))
                    } else {
                        catState = CAT_IDLE
                        catTimer = 1f + catRnd.nextFloat() * 2f
                    }
                }
            }
            CAT_PLAY -> {
                catStageT += dt
                when (catDeco) {
                    3 -> {   // 秋千：坐上去跟着荡
                        val sx = HomeYard.posX(game, HomeYard.SWING)
                        val sz = HomeYard.posZ(game, HomeYard.SWING)
                        val sway = kotlin.math.sin(homePhase * 1.6f)
                        catX = sx
                        catZ = sz + sway * 0.55f
                        catY = 1.75f - kotlin.math.abs(sway) * 0.12f
                        catYaw = 180f
                        if (catTimer <= 0f) endYardPlay()
                    }
                    4 -> {   // 猫爬架：跳下层 → 停留 → 跳顶层 → 蹲坐甩尾 → 跳下
                        val px = HomeYard.posX(game, HomeYard.PERCH)
                        val pz = HomeYard.posZ(game, HomeYard.PERCH)
                        val stageEnd = floatArrayOf(0.45f, 1.2f, 0.45f, 2.8f, 0.5f)
                        val t = (catStageT / stageEnd[catStage]).coerceIn(0f, 1f)
                        val hop = kotlin.math.sin(t * Math.PI.toFloat()) * 0.5f
                        when (catStage) {
                            0 -> { catX = px - 0.45f + 0.2f * t; catZ = pz + 0.9f - 0.5f * t; catY = 1.25f * t + hop }
                            1 -> { catY = 1.25f }
                            2 -> { catX = px - 0.25f + 0.5f * t; catY = 1.25f + 1.35f * t + hop }
                            3 -> { catY = 2.6f }
                            else -> { catX = px + 0.25f + 1.4f * t; catZ = pz + 1.2f * t; catY = 2.6f * (1f - t * t) }
                        }
                        catYaw = 180f
                        if (catStageT > stageEnd[catStage]) {
                            catStageT = 0f
                            catStage++
                            if (catStage > 4) { catY = 0f; endYardPlay() }
                        }
                    }
                    5 -> {   // 泳池：跃入 → 来回划水 → 爬回岸边；跃入瞬间偶有溅起金币
                        val poolX = HomeYard.posX(game, HomeYard.POOL)
                        val poolZ = HomeYard.posZ(game, HomeYard.POOL)
                        when {
                            catStageT < 0.5f -> {
                                val t = catStageT / 0.5f
                                catX = poolX - 3.2f + 1.5f * t
                                catZ = poolZ + 1.9f - 0.6f * t
                                catY = -0.25f * t + kotlin.math.sin(t * Math.PI.toFloat()) * 0.7f
                                if (catStage == 0 && catStageT >= 0.45f) {
                                    catStage = 1
                                    if (catRnd.nextFloat() < 0.4f) {
                                        val n = 1 + catRnd.nextInt(4)
                                        showToast(game.grantYardCoins(n, "游泳溅起了 $n 枚金币！"))
                                    }
                                }
                            }
                            catStageT < 6f -> {
                                val st = catStageT - 0.5f
                                val swim = kotlin.math.sin(st * 1.1f)
                                catX = poolX + swim * 1.5f
                                catZ = poolZ + kotlin.math.cos(st * 0.7f) * 0.7f
                                catY = -0.25f
                                catYaw = if (kotlin.math.cos(st * 1.1f) >= 0f) 90f else 270f
                            }
                            else -> {
                                val t = ((catStageT - 6f) / 0.5f).coerceIn(0f, 1f)
                                catX = poolX + (poolX - 3.2f - poolX) * t
                                catZ = poolZ + 1.9f * t
                                catY = -0.25f + 0.25f * t + kotlin.math.sin(t * Math.PI.toFloat()) * 0.6f
                            }
                        }
                        if (catStageT > 6.5f) { catY = 0f; endYardPlay() }
                    }
                    else -> if (catTimer <= 0f) endYardPlay()
                }
            }
            CAT_NAP -> if (catTimer <= 0f) {
                catState = CAT_IDLE
                catTimer = 0.5f
            }
            CAT_CHASE -> if (butterflyVisible) {
                stepToward(butterflyX, butterflyZ, 3.1f, dt, stopAt = 0.8f)
            }
            CAT_CHASE_HAND -> if (stepToward(handTargetX, handTargetZ, 3.6f, dt)) {
                catState = CAT_PET
                petTimer = 1.4f
                petCount = 2
                game.yardPet()
                showToast("追到手啦！")
                spawnCatFloater(YardFloater.HEART)
            }
            CAT_PET -> if (!strokeActive) {
                petTimer -= dt
                if (petTimer <= 0f) {
                    catState = CAT_IDLE
                    catTimer = 0.6f + catRnd.nextFloat() * 1.2f
                    petCount = 0
                }
            }
        }

        HomeYard.catX = catX
        HomeYard.catZ = catZ
        HomeYard.catY = catY
        HomeYard.catYaw = catYaw
        HomeYard.catPose = when {
            catState == CAT_PLAY && catDeco == 0 -> HomeScene3D.POSE_SNIFF
            catState == CAT_PLAY && catDeco == 2 -> HomeScene3D.POSE_LOOKUP
            catState == CAT_PLAY && catDeco == 3 -> HomeScene3D.POSE_SIT
            catState == CAT_PLAY && catDeco == 4 ->
                if (catStage == 1 || catStage == 3) HomeScene3D.POSE_SIT else HomeScene3D.POSE_JUMP
            catState == CAT_PLAY && catDeco == 5 ->
                if (catStageT in 0.5f..6f) HomeScene3D.POSE_SWIM else HomeScene3D.POSE_JUMP
            catState == CAT_WALK || catState == CAT_CHASE || catState == CAT_CHASE_HAND -> HomeScene3D.POSE_WALK
            catState == CAT_NAP -> HomeScene3D.POSE_NAP
            catState == CAT_PET -> HomeScene3D.POSE_PET
            else -> HomeScene3D.POSE_STAND
        }
        HomeYard.handX = handTargetX
        HomeYard.handZ = handTargetZ
    }

    /** 朝目标走一步；到了返回 true。顺带把朝向转到行进方向上 */
    private fun stepToward(tx: Float, tz: Float, speed: Float, dt: Float, stopAt: Float = 0f): Boolean {
        val dx = tx - catX
        val dz = tz - catZ
        val dist = kotlin.math.hypot(dx, dz)
        if (dist <= stopAt) { faceTo(tx, tz); return true }
        val step = speed * dt
        if (dist <= step) {
            catX = tx; catZ = tz
            faceTo(tx, tz)
            return true
        }
        catX += dx / dist * step
        catZ += dz / dist * step
        faceTo(tx, tz)
        return false
    }

    /** 模型正面朝 -z，所以朝向角是 atan2(-dx, -dz) */
    private fun faceTo(tx: Float, tz: Float) {
        val dx = tx - catX
        val dz = tz - catZ
        if (kotlin.math.abs(dx) < 1e-3f && kotlin.math.abs(dz) < 1e-3f) return
        catYaw = Math.toDegrees(kotlin.math.atan2(-dx.toDouble(), -dz.toDouble())).toFloat()
    }

    private fun decoX(deco: Int) = HomeYard.decoId(deco)?.let { HomeYard.posX(game, it) } ?: 0f
    private fun decoZ(deco: Int) = HomeYard.decoId(deco)?.let { HomeYard.posZ(game, it) } ?: 0f

    private fun updateButterfly(dt: Float) {
        // 蝴蝶只出现在有花草的世界：水下 / 熔岩 / 星空的家没有
        val w = game.homeWorld
        val allowed = w != Game.UNI_WATER && w != Game.UNI_LAVA && w != Game.UNI_SPACE
        if (!allowed) {
            butterflyVisible = false
            HomeYard.butterflyOn = false
            return
        }
        if (butterflyVisible) {
            butterflyLife -= dt
            butterflyX += butterflyDir * 1.4f * dt
            butterflyZ += kotlin.math.sin(homePhase * 0.7f) * 0.6f * dt
            if (kotlin.math.abs(butterflyX) >= HomeYard.WALK_HALF_X) {
                butterflyX = butterflyX.coerceIn(-HomeYard.WALK_HALF_X, HomeYard.WALK_HALF_X)
                butterflyDir = -butterflyDir
            }
            butterflyY = 1.6f + kotlin.math.sin(homePhase * 1.8f) * 0.5f
            if (butterflyLife <= 0f) {
                butterflyVisible = false
                butterflySpawnTimer = 15f + catRnd.nextFloat() * 20f
                if (catState == CAT_CHASE) {
                    catState = CAT_IDLE
                    catTimer = 1f + catRnd.nextFloat()
                }
            }
        } else {
            butterflySpawnTimer -= dt
            if (butterflySpawnTimer <= 0f && catState == CAT_IDLE) {
                butterflyVisible = true
                butterflyLife = 6f + catRnd.nextFloat() * 4f
                butterflyX = (catRnd.nextFloat() * 2f - 1f) * HomeYard.WALK_HALF_X * 0.8f
                butterflyZ = HomeYard.WALK_FAR_Z + catRnd.nextFloat() * (HomeYard.WALK_NEAR_Z - HomeYard.WALK_FAR_Z)
                butterflyDir = if (catRnd.nextBoolean()) 1f else -1f
                catDeco = -1
                catState = CAT_CHASE
            }
        }
        HomeYard.butterflyOn = butterflyVisible
        HomeYard.butterflyX = butterflyX
        HomeYard.butterflyY = HomeYard.DECK_Y + butterflyY
        HomeYard.butterflyZ = butterflyZ
    }

    private fun pickYardGoal() {
        if (yardEventTimer <= 0f) {
            pendingYardEvent = YARD_EVENT_COINS
            catDeco = -1
            randomWalkGoal()
            catState = CAT_WALK
            return
        }
        // 候选：散步（权重 2）、打盹（1）、每个已购可互动装饰（1）
        val opts = ArrayList<Int>()
        opts.add(-1); opts.add(-1)
        opts.add(-2)
        for (i in intArrayOf(0, 2, 3, 4, 5)) if (game.ownsDeco(i)) opts.add(i)
        when (val pick = opts[catRnd.nextInt(opts.size)]) {
            -2 -> {
                catState = CAT_NAP
                catTimer = 2.5f + catRnd.nextFloat() * 2.5f
            }
            -1 -> {
                catDeco = -1
                randomWalkGoal()
                catState = CAT_WALK
            }
            else -> {
                catDeco = pick
                // 停在装饰旁边而不是踩上去：站位统一取「朝镜头这一侧一步远」
                catTargetX = decoX(pick) + when (pick) {
                    0 -> 1.6f
                    2 -> -1.2f
                    4 -> -1.4f
                    5 -> -3.2f
                    else -> 0f
                }
                catTargetZ = decoZ(pick) + when (pick) {
                    0 -> 1.1f
                    2 -> 0.9f
                    3 -> 1.1f
                    4 -> 0.9f
                    else -> 1.9f
                }
                catState = CAT_WALK
            }
        }
    }

    private fun randomWalkGoal() {
        catTargetX = (catRnd.nextFloat() * 2f - 1f) * HomeYard.WALK_HALF_X
        catTargetZ = HomeYard.WALK_FAR_Z + catRnd.nextFloat() * (HomeYard.WALK_NEAR_Z - HomeYard.WALK_FAR_Z)
    }

    /** 在猫头顶冒一个飘字：位置取渲染层给的屏幕热区，3D 里怎么动这儿就怎么跟 */
    private fun spawnCatFloater(kind: Int) {
        if (yardCatHit.isEmpty) return
        spawnYardFloater(
            yardCatHit.centerX() + (catRnd.nextFloat() * 2f - 1f) * 10f * yardSceneS,
            yardCatHit.top - 6f * yardSceneS, kind
        )
    }

    // ---------- 拖放：屏幕位移 → 地面世界坐标 ----------

    /** 会落在院子地面上、按世界坐标存偏移的元素；其余（顶栏文字、按钮）仍按屏幕像素挪 */
    private fun isYardDragId(id: String) = when (id) {
        HomeYard.HOUSE, HomeYard.MUSEUM, HomeYard.CAT, HomeYard.GARDEN, HomeYard.FENCE,
        HomeYard.MAILBOX, HomeYard.SWING, HomeYard.PERCH, HomeYard.POOL, HomeYard.TELESCOPE -> true
        else -> false
    }

    /**
     * 命中判定：**盖住这个点的最小的那个矩形赢**。
     *
     * 2D 时代热区是按元素顺序排的，因为每个热区都贴着画出来的轮廓、彼此几乎不重叠。
     * 3D 之后热区是包围盒投影出来的矩形，大件会整个罩住小件：门在房子里、奖章墙在藏馆里、
     * 猫可能站在房子前面。固定顺序在这种情况下必然误判——按面积取最小的那个，
     * 「点小的那件」永远符合直觉，也不用为每对元素手工排优先级。
     */
    private fun hitHomeDraggable(x: Float, y: Float): String? {
        var best: String? = null
        var bestArea = Float.MAX_VALUE
        for ((id, r) in homeDragHits) {
            if (r.isEmpty || !r.contains(x, y)) continue
            if (!canHomeDrag(id) && !isTapOnlyId(id)) continue
            val area = r.width() * r.height()
            if (area < bestArea) { bestArea = area; best = id }
        }
        return best
    }

    /** 不能拖、但可以点开的东西：门（换世界）、荣誉奖章墙 */
    private fun isTapOnlyId(id: String) = id == HomeYard.DOOR || id == HomeYard.HONOR

    private fun beginHomeDrag(id: String, screenX: Float, screenY: Float) {
        homeDragStartFingerX = screenX
        homeDragStartFingerY = screenY
        if (HomeYard.screenToGround(screenX, screenY, width.toFloat(), height.toFloat(), groundHit)) {
            homeDragStartSceneX = groundHit[0]
            homeDragStartSceneY = groundHit[1]
        }
        val po = game.homeLayout()
        when {
            isYardDragId(id) -> {
                homeDragStartOff[0] = game.homeYardOffX(id)
                homeDragStartOff[1] = game.homeYardOffZ(id)
            }
            id == "name" -> { homeDragStartOff[0] = po.nameDX; homeDragStartOff[1] = po.nameDY }
            id == "energy" -> { homeDragStartOff[0] = po.energyDX; homeDragStartOff[1] = po.energyDY }
            id == "reward" -> { homeDragStartOff[0] = po.rewardDX; homeDragStartOff[1] = po.rewardDY }
            else -> {
                homeDragStartOff[0] = game.homeUiDX(id)
                homeDragStartOff[1] = game.homeUiDY(id)
            }
        }
    }

    /**
     * 拖动。院子里的东西走地面射线：手指落在地上的哪一点，东西就摆到哪一点——
     * 近处挪一寸、远处挪一尺，透视自然成立，这是 2D 版按屏幕像素平移做不到的。
     */
    private fun updateHomeDrag(screenX: Float, screenY: Float) {
        val id = homeDragId ?: return
        val po = game.homeLayout()
        val pixDx = snapDragPx(screenX - homeDragStartFingerX)
        val pixDy = snapDragPx(screenY - homeDragStartFingerY)
        if (isYardDragId(id)) {
            if (!HomeYard.screenToGround(screenX, screenY, width.toFloat(), height.toFloat(), groundHit)) return
            val nx = snapWorld(homeDragStartOff[0] + (groundHit[0] - homeDragStartSceneX))
            val nz = snapWorld(homeDragStartOff[1] + (groundHit[1] - homeDragStartSceneY))
            game.setHomeYardOff(
                id,
                HomeYard.clampX(HomeYard.anchorX(id) + nx) - HomeYard.anchorX(id),
                HomeYard.clampZ(HomeYard.anchorZ(id) + nz) - HomeYard.anchorZ(id)
            )
            return
        }
        val s = hudScale(width.toFloat(), height.toFloat())
        when (id) {
            "name" -> { po.nameDX = homeDragStartOff[0] + pixDx / s; po.nameDY = homeDragStartOff[1] + pixDy / s }
            "energy" -> { po.energyDX = homeDragStartOff[0] + pixDx / s; po.energyDY = homeDragStartOff[1] + pixDy / s }
            "reward" -> { po.rewardDX = homeDragStartOff[0] + pixDx / s; po.rewardDY = homeDragStartOff[1] + pixDy / s }
            else -> {
                po.uiDX[id] = homeDragStartOff[0] + pixDx
                po.uiDY[id] = homeDragStartOff[1] + pixDy
            }
        }
    }

    /** 摆放吸附到 0.25 个世界单位：手不稳也能摆整齐，又比整格自由 */
    private fun snapWorld(v: Float) = kotlin.math.round(v * 4f) / 4f

    private fun canHomeDrag(id: String): Boolean = when (id) {
        "garden" -> game.ownsDeco(0)
        "fence" -> game.ownsDeco(1)
        "mailbox" -> game.ownsDeco(2)
        "swing" -> game.ownsDeco(3)
        "perch" -> game.ownsDeco(4)
        "pool" -> game.ownsDeco(5)
        "telescope" -> game.ownsDeco(6)
        "homeHint" -> !homeEditing
        // 门与荣誉侧墙是房屋 / 藏馆剪影的一部分，只能点开、不能单独搬走
        "honor", "door" -> false
        "museumLabel" -> true
        else -> true
    }

    private fun dispatchHomeDragTap(id: String, x: Float, y: Float) {
        when (id) {
            "door" -> { showHomeWorlds = true; closeHomePick() }
            "telescope" -> if (game.canUseTelescope()) openStargazing()
            "museum" -> {
                museumPage = 0; museumDetailId = -1; homeSubView = HOME_SUB_COLLECTION
                game.markRelicsSeen()
            }
            "honor" -> { honorPage = 0; homeSubView = HOME_SUB_HONOR; game.markHonorsSeen() }
            // 门套在房子的热区里，而拖放这条链路比 handleTap 先跑，
            // 所以门要在这儿先分流出去，否则永远是「翻修房子」把它吃掉
            "house" -> if (hitDoor.contains(x, y)) {
                showHomeWorlds = true
                closeHomePick()
            } else selectHomeCategory(Game.HOME_TAB_HOUSE)
            "roof" -> selectHomeCategory(Game.HOME_TAB_ROOF)
            "garden", "fence", "mailbox", "swing", "perch", "pool" -> selectHomeCategory(Game.HOME_TAB_DECO)
            // 点猫 = 摸一把 + 摊开装扮面板。原先只在「已经处在猫装扮分类里」时才开面板，
            // 而进那个分类唯一的入口就是商店条的 tab——tab 删掉后就成了死循环，装扮再也点不到。
            // 长按撸猫（startStrokeCat）不受影响，那才是持续给币的那个动作。
            "cat" -> {
                petYardCat()
                selectHomeCategory(Game.HOME_TAB_COLOR)
            }
            "name" -> showRenameDialog(firstTime = false)
            "leave" -> leaveHomePage()
            "energy", "reward", "wallet", "homeHint",
            "museumLabel", "honorLabel" -> { }
        }
    }

    /** 双击庭院：小猫追向手指落点（落点是地面上的一个真实位置，不是屏幕上的一条横坐标） */
    private fun startChaseHand(screenX: Float, screenY: Float) {
        removeCallbacks(yardSingleTapRunnable)
        if (!aimHandAt(screenX, screenY)) return
        catDeco = -1
        pendingYardEvent = YARD_EVENT_NONE
        butterflyVisible = false
        catState = CAT_CHASE_HAND
        showToast("来追我的手！")
    }

    private fun scheduleYardSceneTap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val isDouble = yardLastTapTime > 0L &&
            now - yardLastTapTime < YARD_DOUBLE_TAP_MS &&
            abs(x - yardLastTapX) < 44f * yardSceneS &&
            abs(y - yardLastTapY) < 44f * yardSceneS
        removeCallbacks(yardSingleTapRunnable)
        if (isDouble) {
            yardLastTapTime = 0L
            startChaseHand(x, y)
            return
        }
        yardLastTapX = x
        yardLastTapY = y
        yardLastTapTime = now
        yardPendingTapX = x
        yardPendingTapY = y
        postDelayed(yardSingleTapRunnable, YARD_DOUBLE_TAP_MS)
    }

    private fun handleYardSingleTap(x: Float, y: Float) {
        when {
            butterflyHit.contains(x, y) && butterflyVisible -> pokeButterfly()
            yardCatHit.contains(x, y) -> {
                petYardCat()
                selectHomeCategory(Game.HOME_TAB_COLOR)
            }
        }
    }

    /** 点蝴蝶：惊飞；猫在追时帮抓有金币 */
    private fun pokeButterfly() {
        if (!butterflyVisible) return
        val helpingChase = catState == CAT_CHASE
        butterflyVisible = false
        butterflySpawnTimer = 12f + catRnd.nextFloat() * 16f
        if (!butterflyHit.isEmpty) {
            spawnYardFloater(butterflyHit.centerX(), butterflyHit.top, YardFloater.HEART)
        }
        when {
            helpingChase -> {
                catState = CAT_IDLE
                catTimer = 1f + catRnd.nextFloat()
                showToast(game.grantYardCoins(1 + catRnd.nextInt(2), "帮小猫抓到蝴蝶！"))
            }
            catState == CAT_IDLE && catRnd.nextFloat() < 0.55f -> {
                catDeco = -1
                butterflyVisible = true
                butterflyLife = 5f + catRnd.nextFloat() * 3f
                catState = CAT_CHASE
                showToast("快去帮它抓！")
            }
            else -> {
                if (catState == CAT_CHASE) {
                    catState = CAT_IDLE
                    catTimer = 0.8f
                }
                showToast("蝴蝶翩然而去")
            }
        }
    }

    /** 长按撸猫：持续呼噜 + 限时掉落金币 */
    private fun startStrokeCat() {
        catDeco = -1
        pendingYardEvent = YARD_EVENT_NONE
        butterflyVisible = false
        catState = CAT_PET
        petTimer = 99f
        strokeActive = true
        strokeHoldT = 0f
        strokeCoinTimer = 0.45f
        strokeCoinsGiven = if (strokeCoinCooldown > 0f) STROKE_MAX_COINS else 0
        petCount = 1
        game.yardPet()
        showToast(
            if (strokeCoinCooldown > 0f) "呼噜呼噜～（今天撸够啦）"
            else "长按撸猫…"
        )
    }

    private fun updateStroke(dt: Float) {
        strokeHoldT += dt
        petTimer = 99f
        if ((strokeHoldT * 3f).toInt() > petCount) {
            petCount = (petCount + 1).coerceAtMost(8)
        }
        if (strokeCoinCooldown > 0f || strokeCoinsGiven >= STROKE_MAX_COINS) return
        strokeCoinTimer -= dt
        if (strokeCoinTimer <= 0f) {
            strokeCoinsGiven++
            strokeCoinTimer = STROKE_COIN_INTERVAL
            game.yardPet()
            showToast(game.grantYardCoins(1, "撸猫 +1"))
            spawnCatFloater(YardFloater.COIN)
        }
    }

    private fun endStrokeCat() {
        if (!strokeActive) return
        strokeActive = false
        if (strokeCoinsGiven > 0) strokeCoinCooldown = STROKE_COOLDOWN
        catState = CAT_IDLE
        catTimer = 0.7f + catRnd.nextFloat()
        petCount = 0
        petTimer = 0f
    }

    private fun cancelYardTouch() {
        removeCallbacks(yardSingleTapRunnable)
        yardFingerDown = false
        yardLongPressTriggered = false
        endStrokeCat()
    }

    private fun spawnYardFloater(x: Float, y: Float, kind: Int) {
        yardFloaters.add(YardFloater(x, y, 1.2f, kind))
        if (yardFloaters.size > 12) yardFloaters.removeAt(0)
    }

    /** 单击庭院小猫：短摸 / 吵醒打盹 */
    private fun petYardCat() {
        if (catState == CAT_NAP) {
            catState = CAT_IDLE
            catTimer = 1.8f
            game.yardPet()
            showToast("喵！干嘛吵醒我")
            return
        }
        val extending = catState == CAT_PET && !strokeActive
        catDeco = -1
        pendingYardEvent = YARD_EVENT_NONE
        butterflyVisible = false
        catState = CAT_PET
        petTimer = if (extending) min(petTimer + 0.6f, 2.8f) else 1.9f
        petCount = (petCount + 1).coerceAtMost(6)
        game.yardPet()
        spawnCatFloater(YardFloater.HEART)
        if (!extending || catRnd.nextFloat() < 0.35f) {
            val msg = when (catRnd.nextInt(4)) {
                0 -> "呼噜～"
                1 -> "喵～"
                2 -> "${game.characterName} 很开心"
                else -> "尾巴摇起来了"
            }
            showToast(msg)
        }
    }

    private fun endYardPlay() {
        catDeco = -1
        catState = CAT_IDLE
        catTimer = 0.8f + catRnd.nextFloat() * 1.5f
    }

    private fun resolveYardEvent() {
        val message = when (pendingYardEvent) {
            YARD_EVENT_COINS -> game.grantYardCoins(2 + catRnd.nextInt(5))
            else -> ""
        }
        pendingYardEvent = YARD_EVENT_NONE
        yardEventTimer = 18f + catRnd.nextFloat() * 18f
        showToast(message)
    }

    private fun drawPetHearts(canvas: Canvas, x: Float, footY: Float, s: Float) {
        val elapsed = 1.9f - petTimer
        btnPaint.style = Paint.Style.FILL
        for (i in 0 until petCount.coerceAtMost(4)) {
            val hp = ((elapsed - i * 0.18f) / 1.1f).coerceIn(0f, 1f)
            if (hp <= 0f || hp >= 1f) continue
            val hx = x + (i - (petCount - 1) / 2f) * 16f * s
            val hy = footY - (42f + hp * 36f) * s
            val a = ((1f - hp) * 220f).toInt().coerceIn(40, 220)
            btnPaint.color = withAlpha(0xFFF25A7A.toInt(), a)
            canvas.drawRect(hx - 4f * s, hy - 2f * s, hx + 4f * s, hy + 2f * s, btnPaint)
            canvas.drawRect(hx - 6f * s, hy - 4f * s, hx - 2f * s, hy, btnPaint)
            canvas.drawRect(hx + 2f * s, hy - 4f * s, hx + 6f * s, hy, btnPaint)
            canvas.drawRect(hx - 2f * s, hy, hx + 2f * s, hy + 5f * s, btnPaint)
        }
    }

    private fun drawYardFloaters(canvas: Canvas, s: Float) {
        btnPaint.style = Paint.Style.FILL
        for (f in yardFloaters) {
            val a = (f.life / 1.2f * 230f).toInt().coerceIn(30, 230)
            if (f.kind == YardFloater.COIN) {
                btnPaint.color = withAlpha(0xFFFFD426.toInt(), a)
                canvas.drawRect(f.x - 5f * s, f.y - 5f * s, f.x + 5f * s, f.y + 5f * s, btnPaint)
                btnPaint.color = withAlpha(0xFFFFE878.toInt(), a)
                canvas.drawRect(f.x - 2f * s, f.y - 2f * s, f.x + 2f * s, f.y + 2f * s, btnPaint)
            } else {
                btnPaint.color = withAlpha(0xFFF25A7A.toInt(), a)
                canvas.drawRect(f.x - 4f * s, f.y - 2f * s, f.x + 4f * s, f.y + 2f * s, btnPaint)
                canvas.drawRect(f.x - 2f * s, f.y, f.x + 2f * s, f.y + 5f * s, btnPaint)
            }
        }
    }

    // ---------- 望月镜 ----------
    private fun drawStargazeOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        val pw = min(w * 0.92f, 560f * s)
        val left = w / 2f - pw / 2f
        val right = w / 2f + pw / 2f

        val body = stargazeBody.coerceIn(0, game.telescopeLevel)
        val card = StargazeCards.card(body)
        val name = Game.TELESCOPE_BODY_NAMES[body]
        val readTag = if (game.stargazeCardRead(body)) "已读" else "新卡"
        val hooks = StargazeCards.hooks(card)
        val facts = StargazeCards.facts(card)
        val tonights = StargazeCards.tonights(card)

        // 卡片高度按内容实际需要来算，不再固定占屏幕 80%，避免大片空白；
        // 各元素先算出「相对卡片顶部」的位置，最后统一居中定出 top/bottom
        val nameY = 66f * s
        val readTagY = nameY + 34f * s
        val lensR = min(pw * 0.26f, 92f * s)
        val lensCy = readTagY + 26f * s + lensR
        val badgeY = lensCy + lensR + 30f * s
        val hookY = badgeY + 32f * s
        val hookLineGap = 28f * s
        val chipTop = hookY + hookLineGap * (hooks.size - 1).coerceAtLeast(0) + 30f * s
        val chipH = 40f * s
        val chipRowGap = 12f * s
        val factRows = (facts.size + 1) / 2
        val tipY = chipTop + factRows * chipH + (factRows - 1).coerceAtLeast(0) * chipRowGap + 30f * s
        val tipLineGap = 26f * s
        val footTextY = tipY + tipLineGap * (tonights.size - 1).coerceAtLeast(0) + 40f * s
        val hasUpgrade = game.telescopeCanUpgrade()
        val upH = 44f * s
        val btnRowCy = footTextY + 34f * s
        // 没有升级按钮时不再单独留一整行给收尾按钮，卡片会更短
        val hintY = if (hasUpgrade) btnRowCy + upH / 2f + 26f * s else footTextY + 30f * s
        val relBottom = hintY + 20f * s

        val top = ((h - relBottom) / 2f).coerceIn(h * 0.05f, h * 0.16f)
        val bottom = top + relBottom
        PixelUi.drawPanel(
            canvas, btnPaint, left, top, right, bottom,
            0xF20C1220.toInt(), 0xFF6B8CFF.toInt(), s, 16f * s, edgeW = 2.5f * s
        )

        // 顶栏：名称 + 阅读进度
        pixText(canvas, name, w / 2f, top + nameY, 34f * s, 0xFFB8CCFF.toInt(), sdx, sdy)
        pixText(canvas, "$readTag · ${game.stargazeReadCount()}/${game.telescopeLevel + 1}",
            w / 2f, top + readTagY, 18f * s, 0xFF88AACC.toInt(), sdx, sdy)

        // 中央大镜筒（主视觉）
        val lensCx = w / 2f
        val lensCyAbs = top + lensCy
        btnPaint.color = 0xFF060818.toInt()
        canvas.drawCircle(lensCx, lensCyAbs, lensR + 6f * s, btnPaint)
        canvas.save()
        canvas.clipRect(lensCx - lensR, lensCyAbs - lensR, lensCx + lensR, lensCyAbs + lensR)
        btnPaint.color = 0xFF0A1028.toInt()
        canvas.drawRect(lensCx - lensR, lensCyAbs - lensR, lensCx + lensR, lensCyAbs + lensR, btnPaint)
        drawStargazeStars(canvas, lensCx, lensCyAbs, lensR, s, body)
        drawCelestialBody(canvas, lensCx, lensCyAbs, lensR * 0.58f, s, body)
        canvas.restore()
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 3f * s
        btnPaint.color = 0xFF4A5A88.toInt()
        canvas.drawCircle(lensCx, lensCyAbs, lensR, btnPaint)
        btnPaint.strokeWidth = 1.5f * s
        btnPaint.color = 0xFF2A3458.toInt()
        canvas.drawCircle(lensCx, lensCyAbs, lensR + 6f * s, btnPaint)
        btnPaint.style = Paint.Style.FILL

        // 术语徽章
        pixText(canvas, card.term, w / 2f, top + badgeY, 26f * s, 0xFFFFD426.toInt(), sdx, sdy)

        // 两句钩子
        for (i in hooks.indices) {
            val hookSize = fittedTextSize(hooks[i], 22f * s, pw * 0.86f, 16f * s)
            pixText(canvas, hooks[i], w / 2f, top + hookY + i * hookLineGap, hookSize, 0xFFE8ECF4.toInt(), sdx, sdy)
        }

        // 四条要点（2×2 小卡）
        val chipW = (pw - 48f * s) / 2f
        for (i in facts.indices) {
            val row = i / 2
            val col = i % 2
            val cx = left + 16f * s + chipW * 0.5f + col * (chipW + 16f * s)
            val cl = cx - chipW * 0.5f
            val cr = cx + chipW * 0.5f
            val chipTopAbs = top + chipTop + row * (chipH + chipRowGap)
            val chipTextY = chipTopAbs + chipH * 0.62f
            PixelUi.drawPanel(
                canvas, btnPaint, cl, chipTopAbs, cr, chipTopAbs + chipH,
                0xFF162038.toInt(), 0xFF3A4A70.toInt(), s, 6f * s,
                edgeW = 1.5f * s, shadow = false
            )
            val factSize = fittedTextSize(facts[i], 18f * s, chipW * 0.9f, 14f * s)
            pixText(canvas, facts[i], cx, chipTextY, factSize, 0xFFCCDDFF.toInt(), sdx, sdy)
        }

        // 两条「今晚一看」
        for (i in tonights.indices) {
            val tipText = "今晚 · ${tonights[i]}"
            pixText(canvas, tipText, w / 2f, top + tipY + i * tipLineGap,
                fittedTextSize(tipText, 20f * s, pw * 0.88f, 15f * s),
                0xFF9AD9A0.toInt(), sdx, sdy)
        }

        // 左右切换（贴在镜筒两侧）
        val arrowHalf = 28f * s
        btnStargazeL.set(left + 10f * s, lensCyAbs - arrowHalf, left + 10f * s + arrowHalf * 2f, lensCyAbs + arrowHalf)
        btnStargazeR.set(right - 10f * s - arrowHalf * 2f, lensCyAbs - arrowHalf, right - 10f * s, lensCyAbs + arrowHalf)
        if (body > 0) drawBtn(canvas, btnStargazeL, "<", s)
        if (body < game.telescopeLevel) drawBtn(canvas, btnStargazeR, ">", s)

        // 提示语：读完新卡的引导语优先，否则显示常规说明
        val foot = stargazeDoneHint.ifEmpty { "新卡每天 1 张 · 旧卡可随时复习" }
        pixText(canvas, foot, w / 2f, top + footTextY, 16f * s, 0xFF888888.toInt(), sdx, sdy)

        // 没有独立的关闭按钮：点卡片外任意空白处即可关闭，升级（如有）居中显示
        if (hasUpgrade) {
            val upW = min(pw * 0.55f, 220f * s)
            val upLeft = w / 2f - upW / 2f
            val btnRowCyAbs = top + btnRowCy
            btnStargazeUpgrade.set(upLeft, btnRowCyAbs - upH / 2f, upLeft + upW, btnRowCyAbs + upH / 2f)
            drawBtn(canvas, btnStargazeUpgrade, "升级 (${game.telescopeUpgradePrice()})", s)
        } else {
            btnStargazeUpgrade.setEmpty()
        }

        pixText(canvas, "点击外部关闭", w / 2f, top + hintY, 14f * s, 0xFF667088.toInt(), sdx, sdy)
    }

    private fun drawStargazeStars(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float, body: Int) {
        btnPaint.style = Paint.Style.FILL
        val starSeed = intArrayOf(3, 7, 11, 17, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71)
        for (i in starSeed.indices) {
            val t = starSeed[i]
            val sx = cx + kotlin.math.sin(t * 1.7f + i) * r * 0.82f
            val sy = cy + kotlin.math.cos(t * 2.3f + i * 0.7f) * r * 0.78f
            val tw = (kotlin.math.sin(homePhase * 3f + t) * 0.5f + 0.5f)
            val a = (120 + tw * 135).toInt().coerceIn(80, 255)
            val size = if (i % 4 == 0) 2.5f else 1.5f
            btnPaint.color = withAlpha(0xFFFFFFFF.toInt(), a)
            canvas.drawRect(sx - size * s, sy - size * s, sx + size * s, sy + size * s, btnPaint)
        }
        if (body == 4) {
            // 深空星云背景雾
            btnPaint.color = withAlpha(0xFF8B5CF6.toInt(), 48)
            canvas.drawCircle(cx - r * 0.2f, cy + r * 0.1f, r * 0.55f, btnPaint)
            btnPaint.color = withAlpha(0xFFF472B6.toInt(), 36)
            canvas.drawCircle(cx + r * 0.25f, cy - r * 0.15f, r * 0.45f, btnPaint)
        }
    }

    private fun drawCelestialBody(canvas: Canvas, cx: Float, cy: Float, r: Float, s: Float, body: Int) {
        btnPaint.style = Paint.Style.FILL
        when (body) {
            0 -> { // 月亮
                btnPaint.color = 0xFFE8E8E0.toInt()
                canvas.drawCircle(cx, cy, r, btnPaint)
                btnPaint.color = 0xFFC8C8C0.toInt()
                canvas.drawCircle(cx - r * 0.28f, cy - r * 0.18f, r * 0.18f, btnPaint)
                canvas.drawCircle(cx + r * 0.22f, cy + r * 0.25f, r * 0.12f, btnPaint)
                canvas.drawCircle(cx + r * 0.05f, cy - r * 0.32f, r * 0.08f, btnPaint)
            }
            1 -> { // 火星
                btnPaint.color = 0xFFE07050.toInt()
                canvas.drawCircle(cx, cy, r, btnPaint)
                btnPaint.color = 0xFFC04830.toInt()
                canvas.drawCircle(cx - r * 0.2f, cy + r * 0.15f, r * 0.22f, btnPaint)
                canvas.drawCircle(cx + r * 0.25f, cy - r * 0.1f, r * 0.15f, btnPaint)
                // 极冠
                btnPaint.color = 0xFFF0F0F0.toInt()
                canvas.drawRect(cx - r * 0.35f, cy - r * 0.95f, cx + r * 0.35f, cy - r * 0.72f, btnPaint)
            }
            2 -> { // 土星
                btnPaint.color = 0xFFE8D090.toInt()
                canvas.drawCircle(cx, cy, r * 0.72f, btnPaint)
                btnPaint.color = 0xFFD0B870.toInt()
                canvas.drawRect(cx - r * 1.15f, cy - r * 0.08f, cx + r * 1.15f, cy + r * 0.08f, btnPaint)
                btnPaint.color = withAlpha(0xFFE8D090.toInt(), 180)
                canvas.drawRect(cx - r * 1.05f, cy - r * 0.18f, cx + r * 1.05f, cy - r * 0.10f, btnPaint)
                btnPaint.color = withAlpha(0xFFE8D090.toInt(), 180)
                canvas.drawRect(cx - r * 1.05f, cy + r * 0.10f, cx + r * 1.05f, cy + r * 0.18f, btnPaint)
            }
            3 -> { // 木星
                btnPaint.color = 0xFFD8A060.toInt()
                canvas.drawCircle(cx, cy, r, btnPaint)
                val bands = intArrayOf(0xFFC08040.toInt(), 0xFFE0B070.toInt(), 0xFFB86830.toInt())
                for (i in 0..2) {
                    btnPaint.color = bands[i]
                    val by = cy - r * 0.5f + i * r * 0.38f
                    canvas.drawRect(cx - r * 0.92f, by, cx + r * 0.92f, by + r * 0.14f, btnPaint)
                }
                // 大红斑
                btnPaint.color = 0xFFE05040.toInt()
                canvas.drawCircle(cx + r * 0.35f, cy + r * 0.2f, r * 0.16f, btnPaint)
            }
            4 -> { // 深空星云
                btnPaint.color = withAlpha(0xFF7C3AED.toInt(), 200)
                canvas.drawCircle(cx - r * 0.15f, cy, r * 0.75f, btnPaint)
                btnPaint.color = withAlpha(0xFFEC4899.toInt(), 160)
                canvas.drawCircle(cx + r * 0.2f, cy - r * 0.1f, r * 0.55f, btnPaint)
                btnPaint.color = withAlpha(0xFF38BDF8.toInt(), 120)
                canvas.drawCircle(cx, cy + r * 0.25f, r * 0.35f, btnPaint)
                btnPaint.color = Color.WHITE
                canvas.drawRect(cx - 2f * s, cy - r * 0.4f, cx + 2f * s, cy - r * 0.36f, btnPaint)
                canvas.drawRect(cx + r * 0.3f, cy + r * 0.1f, cx + r * 0.34f, cy + r * 0.14f, btnPaint)
            }
            5 -> { // 金星
                btnPaint.color = 0xFFF0E8C8.toInt()
                canvas.drawCircle(cx, cy, r, btnPaint)
                btnPaint.color = withAlpha(0xFFE8D8A8.toInt(), 200)
                canvas.drawCircle(cx - r * 0.15f, cy - r * 0.1f, r * 0.55f, btnPaint)
                btnPaint.color = withAlpha(0xFFD8C898.toInt(), 160)
                canvas.drawCircle(cx + r * 0.2f, cy + r * 0.15f, r * 0.4f, btnPaint)
            }
            6 -> { // 水星
                btnPaint.color = 0xFFB0A898.toInt()
                canvas.drawCircle(cx, cy, r * 0.82f, btnPaint)
                btnPaint.color = 0xFF908878.toInt()
                canvas.drawCircle(cx - r * 0.22f, cy + r * 0.18f, r * 0.14f, btnPaint)
                canvas.drawCircle(cx + r * 0.18f, cy - r * 0.12f, r * 0.1f, btnPaint)
                canvas.drawCircle(cx + r * 0.05f, cy + r * 0.28f, r * 0.08f, btnPaint)
            }
            7 -> { // 天王星
                btnPaint.color = 0xFF88C8D8.toInt()
                canvas.drawCircle(cx, cy, r * 0.78f, btnPaint)
                btnPaint.color = withAlpha(0xFF68A8C8.toInt(), 140)
                canvas.drawRect(cx - r * 1.05f, cy - r * 0.04f, cx + r * 1.05f, cy + r * 0.04f, btnPaint)
            }
            else -> { // 海王星
                btnPaint.color = 0xFF3060C8.toInt()
                canvas.drawCircle(cx, cy, r, btnPaint)
                btnPaint.color = withAlpha(0xFF2048A8.toInt(), 180)
                canvas.drawCircle(cx - r * 0.18f, cy + r * 0.12f, r * 0.35f, btnPaint)
                btnPaint.color = withAlpha(0xFF4878D0.toInt(), 140)
                canvas.drawCircle(cx + r * 0.22f, cy - r * 0.15f, r * 0.25f, btnPaint)
            }
        }
    }

    private fun drawPauseButton(canvas: Canvas, w: Float, h: Float, s: Float) {
        // 放在右下角，方便单手握持时用拇指直接点到
        val size = 100f * s
        val right = w - safeR - 30f * s
        val bottom = h - safeB - 30f * s
        btnPause.set(right - size, bottom - size, right, bottom)
        PixelUi.drawRect(
            canvas, btnPaint, btnPause.left, btnPause.top, btnPause.right, btnPause.bottom,
            0xFF4A5A6E.toInt(), s, edge = 0xFF6B4E28.toInt(), edgeW = 1f, shadow = true
        )
        btnPaint.color = Color.WHITE
        val cx = btnPause.centerX()
        val cy = btnPause.centerY()
        canvas.drawRect(cx - 12f * s, cy - 14f * s, cx - 4f * s, cy + 14f * s, btnPaint)
        canvas.drawRect(cx + 4f * s, cy - 14f * s, cx + 12f * s, cy + 14f * s, btnPaint)
    }

    private fun drawPauseOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        dim(canvas, w, h)   // 双层压暗，突出暂停菜单
        pixText(canvas, "已暂停", w / 2f, h * 0.34f, 64f * s, Color.WHITE, sdx, sdy)

        val portrait = h > w
        val halfW = if (portrait) min(180f * s, w * 0.40f) else 150f * s
        val bh = if (portrait) 68f * s else 56f * s
        btnResume.set(w / 2f - halfW, h * 0.48f, w / 2f + halfW, h * 0.48f + bh)
        drawBtn(canvas, btnResume, "继续跑酷", s)
        btnQuit.set(w / 2f - halfW, h * 0.60f, w / 2f + halfW, h * 0.60f + bh)
        drawBtn(canvas, btnQuit, "结束本局", s)
    }

    private fun honorPages(): Int =
        (Game.ACHIEVE_CATS + HONOR_PER_PAGE - 1) / HONOR_PER_PAGE

    private fun museumPages(): Int =
        (Game.RELIC_COUNT + MUSEUM_PER_PAGE - 1) / MUSEUM_PER_PAGE

    private fun relicDetailNavIndex(id: Int, delta: Int): Int {
        val n = Game.RELIC_COUNT
        return ((id + delta) % n + n) % n
    }

    /** 详情浮层：左滑下一件，右滑上一件；测试全览可浏览全部，否则只在已收集间切换 */
    private fun navigateMuseumDetail(delta: Int) {
        if (museumDetailId !in 0 until Game.RELIC_COUNT) return
        val nextId = if (game.immortalMode) {
            relicDetailNavIndex(museumDetailId, delta)
        } else {
            var id = museumDetailId
            var found = museumDetailId
            for (step in 1 until Game.RELIC_COUNT) {
                id = relicDetailNavIndex(id, delta)
                if (game.relicCollected(id)) {
                    found = id
                    break
                }
            }
            found
        }
        museumDetailId = nextId
        museumPage = museumDetailId / MUSEUM_PER_PAGE
    }

    /** 藏品 / 荣誉图鉴：左滑下一页，右滑上一页；详情打开时左滑下一件、右滑上一件 */
    private fun tryCatalogPageSwipe(dx: Float, dy: Float, minDist: Float): Boolean {
        if (game.menuPanel != Game.PANEL_HOME) return false
        if (abs(dx) < minDist || abs(dx) <= abs(dy)) return false
        when (homeSubView) {
            HOME_SUB_COLLECTION -> {
                if (museumDetailId >= 0) {
                    navigateMuseumDetail(if (dx < 0f) 1 else -1)
                    return true
                }
                val pages = museumPages()
                if (pages <= 1) return false
                museumPage = if (dx < 0f) (museumPage + 1) % pages
                else (museumPage + pages - 1) % pages
                return true
            }
            HOME_SUB_HONOR -> {
                val pages = honorPages()
                if (pages <= 1) return false
                honorPage = if (dx < 0f) (honorPage + 1) % pages
                else (honorPage + pages - 1) % pages
                return true
            }
            else -> return false
        }
    }

    /** 小屋「藏品 / 荣誉」共用全屏框：顶栏标题、中部内容、底栏分页彼此隔离 */
    private data class OverlayFrame(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val pw: Float,
        val contentTop: Float,
        val contentBottom: Float
    )

    private fun beginCatalogOverlay(
        canvas: Canvas, w: Float, h: Float, s: Float,
        title: String, subtitle: String, sdx: Float, sdy: Float, hall: Int
    ): OverlayFrame {
        dim(canvas, w, h)

        val left = safeL
        val top = safeT
        val right = w - safeR
        val bottom = h - safeB
        val headerBottom = top + OVERLAY_HEADER_H * s
        val contentTop = headerBottom + 10f * s
        val contentBottom = bottom - OVERLAY_FOOTER_H * s

        // 背景铺满；标题 / X / 列表落在 safe area 内
        drawHallBackdrop(canvas, w, h, s, hall, headerBottom, contentBottom + 14f * s)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2.5f * s
        btnPaint.color = LIGHT_PANEL_EDGE
        canvas.drawRect(1.5f * s, 1.5f * s, w - 1.5f * s, h - 1.5f * s, btnPaint)
        btnPaint.style = Paint.Style.FILL

        homeOverlayPanel.set(0f, 0f, w, h)

        textPaint.textAlign = Paint.Align.CENTER
        val titleCx = (left + right) * 0.5f
        lightText(canvas, title, titleCx, top + 46f * s, 36f * s, LIGHT_TITLE)
        val subSize = fittedTextSize(subtitle, 20f * s, (right - left) * 0.72f, 14f * s)
        lightText(canvas, subtitle, titleCx, top + 80f * s, subSize, LIGHT_SUB)

        return OverlayFrame(left, top, right, bottom, right - left, contentTop, contentBottom)
    }

    /**
     * 图鉴内页的"房间"：博物馆展厅（抹灰墙 + 木地板 + 顶部射灯）
     * 或荣誉厅（石砖墙 + 木护墙板）。底色仍偏亮，沿用原有深色字。
     */
    private fun drawHallBackdrop(
        canvas: Canvas, w: Float, h: Float, s: Float, hall: Int, ceilY: Float, floorY: Float
    ) {
        fun rc(l: Float, t: Float, r: Float, b: Float, color: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = color
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        if (hall == HALL_MUSEUM) {
            rc(0f, 0f, w, floorY, 0xFFF3EAD6.toInt())
            rc(0f, 0f, w, ceilY, 0xFFE7DAC0.toInt())
            // 木质檐口
            rc(0f, ceilY - 9f * s, w, ceilY, 0xFF8A6A3E.toInt())
            rc(0f, ceilY - 9f * s, w, ceilY - 6f * s, 0xFFB08B56.toInt())
            // 墙裙 + 踢脚线
            rc(0f, floorY - 34f * s, w, floorY, 0xFFDFD1B4.toInt())
            rc(0f, floorY - 37f * s, w, floorY - 32f * s, 0xFF8A6A3E.toInt())
            // 木地板
            rc(0f, floorY, w, h, 0xFFC79A66.toInt())
            var fy = floorY + 20f * s
            while (fy < h) {
                rc(0f, fy, w, fy + 2f * s, 0xFFAB8250.toInt())
                fy += 22f * s
            }
            // 顶部射灯：分段矩形拼出像素光锥
            for (t in floatArrayOf(0.22f, 0.5f, 0.78f)) {
                val lx = w * t
                rc(lx - 8f * s, ceilY, lx + 8f * s, ceilY + 10f * s, 0xFF6B4A2B.toInt())
                rc(lx - 5f * s, ceilY + 8f * s, lx + 5f * s, ceilY + 13f * s, 0xFFFFE9A6.toInt())
                val segs = 10
                val span = floorY - ceilY - 13f * s
                for (i in 0 until segs) {
                    val p0 = i / segs.toFloat()
                    val p1 = (i + 1) / segs.toFloat()
                    val hw = (7f + 58f * p0) * s
                    rc(
                        lx - hw, ceilY + 13f * s + span * p0,
                        lx + hw, ceilY + 13f * s + span * p1,
                        withAlpha(0xFFFFF0C0.toInt(), (34 * (1f - p0)).toInt())
                    )
                }
            }
        } else {
            // 石砖墙：错缝大砖，缝色比砖面稍深
            rc(0f, 0f, w, floorY, 0xFFEFE6D4.toInt())
            val brickH = 34f * s
            val brickW = 96f * s
            var row = 0
            var by = 0f
            while (by < floorY) {
                rc(0f, by, w, by + 2.5f * s, 0xFFDBCDB2.toInt())
                var bx = if (row % 2 == 0) 0f else brickW * 0.5f
                while (bx < w) {
                    rc(bx, by, bx + 2.5f * s, min(by + brickH, floorY), 0xFFDBCDB2.toInt())
                    bx += brickW
                }
                by += brickH
                row++
            }
            // 木护墙板
            rc(0f, floorY, w, h, 0xFF9C7245.toInt())
            rc(0f, floorY, w, floorY + 5f * s, 0xFFC79A66.toInt())
        }
    }

    /** 底栏：页码居中，多页时提示左右滑动 */
    private fun drawCatalogFooter(
        canvas: Canvas, frame: OverlayFrame, s: Float,
        page: Int, pages: Int, sdx: Float, sdy: Float,
        closeHint: String? = null, pageMidY: Float? = null
    ) {
        val cx = (frame.left + frame.right) * 0.5f
        val hint = closeHint ?: if (pages > 1) {
            "左右滑动翻页 · 点 X 或返回退出"
        } else {
            "点 X 或返回退出"
        }
        // 页码单独一行，居中于「内容分割线 ↔ 页脚收尾行」之间的页脚带；pageMidY 可覆盖
        textPaint.textAlign = Paint.Align.CENTER
        val dividerY = frame.contentBottom + 14f * s
        val closeRowCy = frame.bottom - (CLOSE_ROW_MARGIN + CLOSE_BTN_SIZE * 0.5f) * s
        val midY = pageMidY ?: (dividerY + closeRowCy) * 0.5f
        lightText(canvas, "${page + 1}/$pages", cx, centeredBaselineY(midY, 22f * s), 22f * s, LIGHT_TEXT)
        drawOverlayCloseRow(canvas, frame.left, frame.right, frame.bottom, s, hint, btnCatalogClose)
    }

    /** 钉在荣誉墙上的木质匾额：木框 + 内板 + 四角螺钉，已解锁的镶一圈金属边 */
    private fun drawHonorPlaque(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, s: Float, lv: Int
    ) {
        PixelUi.drawWoodPlaque(
            canvas, btnPaint, left, top, right, bottom, s,
            PixelUi.WoodStyle(0xFF7A5230.toInt(), 0xFF9C6A3C.toInt(), 0xFF5A3A20.toInt()),
            0, PixelUi.AccentSide.NONE, accentStroke = false
        )
        val inset = 8f * s
        PixelUi.drawRect(
            canvas, btnPaint, left + inset, top + inset, right - inset, bottom - inset,
            LIGHT_ROW, s, bevel = PixelUi.Bevel.INSET
        )
        if (lv > 0) {
            val edge = when (lv) {
                1 -> 0xFFC87A3A.toInt()
                2 -> 0xFFAEB6C4.toInt()
                3 -> 0xFFF2C14E.toInt()
                else -> 0xFF6FD3E0.toInt()
            }
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 2f * s
            btnPaint.color = edge
            canvas.drawRect(left + inset, top + inset, right - inset, bottom - inset, btnPaint)
            btnPaint.style = Paint.Style.FILL
        }
        val d = 3f * s
        for (px in floatArrayOf(left + 4.5f * s, right - 4.5f * s)) {
            for (py in floatArrayOf(top + 5.5f * s, bottom - 5.5f * s)) {
                btnPaint.color = 0xFFC9B48C.toInt()
                canvas.drawRect(px - d, py - d, px + d, py + d, btnPaint)
                btnPaint.color = 0xFF6B5540.toInt()
                canvas.drawRect(px - d * 0.4f, py - d, px + d * 0.4f, py + d, btnPaint)
            }
        }
    }

    private fun drawLightBtn(
        canvas: Canvas, r: RectF, label: String, s: Float,
        selected: Boolean = false, enabled: Boolean = true
    ) {
        val fill = when {
            !enabled -> 0xFFE8E2D6.toInt()
            selected -> 0xFFFFF6DC.toInt()
            else -> LIGHT_BTN
        }
        val edge = if (selected) LIGHT_PANEL_EDGE else LIGHT_BTN_EDGE
        val bevel = when {
            !enabled -> PixelUi.Bevel.INSET
            selected -> PixelUi.Bevel.PRESSED
            else -> PixelUi.Bevel.RAISED
        }
        PixelUi.drawButton(
            canvas, btnPaint, r.left, r.top, r.right, r.bottom,
            fill, edge, s, bevel = bevel, shadow = !selected && enabled
        )
        textPaint.textAlign = Paint.Align.CENTER
        val labelSize = min(32f * s, r.height() * 0.45f).coerceAtLeast(24f * s)
        val ink = when {
            !enabled -> LIGHT_LOCKED
            selected -> LIGHT_TITLE
            else -> LIGHT_TEXT
        }
        val labelDy = if (selected) bevelW(s) * 0.5f else 0f
        lightText(
            canvas, label, r.centerX(),
            centeredBaselineY(r.centerY() + labelDy, labelSize), labelSize, ink
        )
    }

    private fun bevelW(s: Float) = max(3f * s, 3f)

    /** 亮色底上的稀有度字色，避免霓虹色 + 阴影发糊 */
    private fun relicInkOnLight(rarity: Int): Int = when (rarity) {
        Game.RELIC_LEGEND -> LIGHT_RELIC_LEGEND
        Game.RELIC_RARE -> LIGHT_RELIC_RARE
        else -> LIGHT_RELIC_COMMON
    }

    private fun relicTileFill(rarity: Int, collected: Boolean): Int = when (rarity) {
        Game.RELIC_LEGEND -> if (collected) TILE_LEGEND_BG else 0xFFECE6D8.toInt()
        Game.RELIC_RARE -> if (collected) TILE_RARE_BG else 0xFFE2E8EC.toInt()
        else -> if (collected) TILE_COMMON_BG else 0xFFE4EAE4.toInt()
    }

    private fun relicTileStroke(rarity: Int, collected: Boolean): Int {
        val ink = relicInkOnLight(rarity)
        return if (collected) ink else withAlpha(ink, 0x78)
    }

    private fun drawHonorOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val frame = beginCatalogOverlay(
            canvas, w, h, s,
            "荣誉 ${game.achieveCount}/${Game.ACHIEVE_MAX}",
            game.nextAchieveHint(),
            sdx, sdy, HALL_HONOR
        )
        val pages = honorPages().coerceAtLeast(1)
        val page = honorPage.coerceIn(0, pages - 1)
        val start = page * HONOR_PER_PAGE
        val end = min(start + HONOR_PER_PAGE, Game.ACHIEVE_CATS)
        val rows = (end - start).coerceAtLeast(1)
        val rowGap = 6f * s
        // 卡片高度按满页行数固定，短页顶端对齐、底部留白，避免不同页卡片忽高忽低
        val rowH = ((frame.contentBottom - frame.contentTop) - rowGap * (HONOR_PER_PAGE - 1)) / HONOR_PER_PAGE
        val rowLeft = frame.left + 16f * s
        val rowRight = frame.right - 16f * s
        val badgeW = 56f * s

        for (i in 0 until rows) {
            val c = start + i
            val rowTop = frame.contentTop + i * (rowH + rowGap)
            val rowBottom = rowTop + rowH
            val lv = game.achieveLevels[c]
            val tierColor = when (lv) {
                0 -> LIGHT_LOCKED
                1 -> 0xFF8A5520.toInt()
                2 -> 0xFF5A6068.toInt()
                3 -> 0xFF8A6410.toInt()
                else -> 0xFF2E8898.toInt()
            }
            drawHonorPlaque(canvas, rowLeft, rowTop, rowRight, rowBottom, s, lv)

            val badgeLeft = rowLeft + 10f * s
            val medalR = min(rowH * 0.30f, 30f * s)
            drawMedalRibbon(canvas, badgeLeft + badgeW * 0.5f, (rowTop + rowBottom) * 0.5f - medalR, medalR, lv)
            drawMedal(canvas, badgeLeft + badgeW * 0.5f, (rowTop + rowBottom) * 0.5f, medalR, lv)

            val textLeft = badgeLeft + badgeW + 14f * s
            val textMaxW = rowRight - textLeft - 12f * s
            val cur = game.achieveProgress(c)
            val nameLine = Game.ACHIEVE_NAMES[c]
            val detail = if (lv >= Game.ACHIEVE_TIERS_PER) {
                "${Game.ACHIEVE_TIERS[Game.ACHIEVE_TIERS_PER - 1]}满级"
            } else {
                val target = Game.ACHIEVE_TARGETS[c][lv]
                val reward = Game.ACHIEVE_REWARDS[lv]
                "$cur/$target · 奖$reward"
            }
            textPaint.textAlign = Paint.Align.LEFT
            val nameSize = fittedTextSize(nameLine, 22f * s, textMaxW, 14f * s)
            val detailSize = fittedTextSize(detail, 18f * s, textMaxW, 12f * s)
            val midY = (rowTop + rowBottom) * 0.5f
            lightText(canvas, nameLine, textLeft, midY - 6f * s, nameSize, LIGHT_TEXT)
            lightText(canvas, detail, textLeft, midY + 18f * s, detailSize, tierColor)
            textPaint.textAlign = Paint.Align.CENTER
        }

        drawCatalogFooter(canvas, frame, s, page, pages, sdx, sdy)
    }

    private fun drawCollectionOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val previewAll = game.immortalMode
        val sub = when {
            previewAll -> "测试全览：可查看未收集文物"
            game.museumComplete() -> "全收集！你是小小考古学家"
            else -> "跑酷路上收集文物（集齐奖 ${Game.MUSEUM_REWARD}）"
        }
        val frame = beginCatalogOverlay(
            canvas, w, h, s,
            "藏品 ${game.relicsFound}/${Game.RELIC_COUNT}",
            sub, sdx, sdy, HALL_MUSEUM
        )
        val pages = museumPages().coerceAtLeast(1)
        val page = museumPage.coerceIn(0, pages - 1)
        val start = page * MUSEUM_PER_PAGE

        val gap = 10f * s
        val padX = 18f * s
        val availW = frame.pw - padX * 2f
        val availH = frame.contentBottom - frame.contentTop
        val cellW = (availW - gap * (MUSEUM_COLS - 1)) / MUSEUM_COLS
        val cellH = (availH - gap * (MUSEUM_ROWS - 1)) / MUSEUM_ROWS
        // 展柜是竖长的：宽度吃满，高度尽量占满整面墙
        val tileW = min(cellW, cellH * 1.15f)
        val tileH = min(cellH, tileW * 1.55f)
        val gridW = MUSEUM_COLS * tileW + (MUSEUM_COLS - 1) * gap
        val gridH = MUSEUM_ROWS * tileH + (MUSEUM_ROWS - 1) * gap
        val originX = frame.left + (frame.pw - gridW) * 0.5f
        val originY = frame.contentTop + (availH - gridH) * 0.42f
        val iconHalf = min(tileW, tileH * 0.84f) * 0.42f

        for (i in museumTileHits.indices) museumTileHits[i].setEmpty()
        for (i in 0 until MUSEUM_PER_PAGE) {
            val id = start + i
            if (id >= Game.RELIC_COUNT) break
            val col = i % MUSEUM_COLS
            val row = i / MUSEUM_COLS
            val left = originX + col * (tileW + gap)
            val top = originY + row * (tileH + gap)
            val right = left + tileW
            val bottom = top + tileH
            museumTileHits[i].set(left, top, right, bottom)

            val collected = game.relicCollected(id)
            val showArt = collected || previewAll
            drawRelicCase(canvas, left, top, right, bottom, s, id, showArt, iconHalf)
            if (previewAll && !collected) {
                // 未收集预览：右下角小点，避免和已收集混淆
                btnPaint.style = Paint.Style.FILL
                btnPaint.color = 0xFF4DE8FF.toInt()
                val d = tileW * 0.08f
                canvas.drawRect(right - d * 2.4f, top + d * 0.9f, right - d * 0.9f, top + d * 2.4f, btnPaint)
            }
        }

        val footerHint = when {
            pages <= 1 && previewAll -> "点图标查看 · X 退出"
            pages <= 1 -> "点图标查看 · X 或返回退出"
            previewAll -> "左右滑动翻页 · 点图标查看 · X 退出"
            else -> "左右滑动翻页 · 点图标查看 · X 或返回退出"
        }
        drawCatalogFooter(canvas, frame, s, page, pages, sdx, sdy, closeHint = footerHint)

        if (museumDetailId in 0 until Game.RELIC_COUNT &&
            (game.relicCollected(museumDetailId) || previewAll)
        ) {
            drawRelicDetailOverlay(canvas, w, h, s, sdx, sdy, museumDetailId)
        } else {
            relicDetailFrame.setEmpty()
        }
    }

    /**
     * 文物展柜：木质底座 + 玻璃罩 + 斜向反光，柜顶一盏小射灯。
     * 未收集时玻璃罩内是空的，只剩 RelicIcons 的剪影。
     */
    private fun drawRelicCase(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        s: Float, id: Int, showArt: Boolean, iconHalf: Float
    ) {
        val rarity = Game.RELIC_RARITY[id]
        val ink = relicInkOnLight(rarity)
        val baseH = (bottom - top) * 0.16f
        val glassB = bottom - baseH
        fun rc(l: Float, t: Float, r: Float, b: Float, color: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = color
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        // 投影：让展柜从墙面/地板底色上脱离出来，呼应荣誉匾额的投影处理
        rc(left + 3f * s, top + 4f * s, right + 3f * s, bottom + 4f * s, 0x2E000000)
        // 柜顶射灯
        rc((left + right) * 0.5f - 4f * s, top - 7f * s, (left + right) * 0.5f + 4f * s, top - 1f * s, 0xFF6B4A2B.toInt())
        // 玻璃罩
        rc(left, top, right, glassB, relicTileFill(rarity, showArt))
        RelicIcons.draw(
            canvas, btnPaint, id,
            (left + right) * 0.5f, (top + glassB) * 0.5f,
            iconHalf, showArt, lightSurface = true, withChrome = false, richArt = true
        )
        // 斜向反光：裁剪在罩内，斜条穿过整块玻璃
        canvas.save()
        canvas.clipRect(left, top, right, glassB)
        canvas.rotate(-32f, left, top)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0x38FFFFFF
        val span = (right - left) * 2.4f
        canvas.drawRect(left + span * 0.30f, top - span, left + span * 0.40f, top + span, btnPaint)
        canvas.drawRect(left + span * 0.46f, top - span, left + span * 0.50f, top + span, btnPaint)
        canvas.restore()
        // 罩体边框
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = if (showArt) 2.5f * s else 1.5f * s
        btnPaint.color = relicTileStroke(rarity, showArt)
        canvas.drawRect(left, top, right, glassB, btnPaint)
        btnPaint.style = Paint.Style.FILL
        // 木质底座 + 铭牌
        rc(left - 2f * s, glassB, right + 2f * s, bottom, 0xFF8A6238.toInt())
        rc(left - 2f * s, glassB, right + 2f * s, glassB + 3f * s, 0xFFB08B56.toInt())
        rc(left + baseH * 0.5f, bottom - baseH * 0.62f, right - baseH * 0.5f, bottom - baseH * 0.22f,
            if (showArt) withAlpha(ink, 0xCC) else 0xFF6B5540.toInt())
    }

    /**
     * 把一句小知识断成两行，返回第二行的起始下标。
     *
     * 中文排版里标点不能出现在行首，所以优先在中点附近的标点**之后**断开；
     * 找不到标点就退回中点，并把紧跟的标点留在上一行。
     */
    private fun splitTwoLines(text: String): Int {
        val mid = text.length / 2
        for (offset in 0..3) {
            for (i in intArrayOf(mid - offset, mid + offset)) {
                if (i in 1 until text.length && text[i - 1] in LINE_BREAK_AFTER) return i
            }
        }
        var split = mid
        while (split < text.length && text[split] in LINE_BREAK_AFTER) split++
        return split
    }

    private fun drawRelicDetailOverlay(
        canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float, id: Int
    ) {
        dim(canvas, w, h)
        val maxW = (w - safeL - safeR - 16f * s).coerceAtLeast(200f * s)
        val maxH = (h - safeT - safeB - 16f * s).coerceAtLeast(280f * s)
        val pw = min(w * 0.92f, min(560f * s, maxW))
        val ph = min(h * 0.9f, min(760f * s, maxH))
        var left = w / 2f - pw / 2f
        var right = w / 2f + pw / 2f
        var top = h / 2f - ph / 2f
        var bottom = h / 2f + ph / 2f
        if (left < safeL + 8f * s) {
            val shift = safeL + 8f * s - left
            left += shift
            right += shift
        }
        if (right > w - safeR - 8f * s) {
            val shift = right - (w - safeR - 8f * s)
            left -= shift
            right -= shift
        }
        if (top < safeT + 8f * s) {
            val shift = safeT + 8f * s - top
            top += shift
            bottom += shift
        }
        if (bottom > h - safeB - 8f * s) {
            val shift = bottom - (h - safeB - 8f * s)
            top -= shift
            bottom -= shift
        }

        val rarity = Game.RELIC_RARITY[id]
        val frameColor = relicInkOnLight(rarity)
        PixelUi.drawPanel(
            canvas, btnPaint, left, top, right, bottom,
            LIGHT_PANEL, frameColor, s, 14f * s, edgeW = 3f * s
        )

        relicDetailFrame.set(left, top, right, bottom)
        // 详情浮层盖住图鉴时，禁用图鉴 X，改由点击外部关闭
        btnCatalogClose.setEmpty()

        val cx = (left + right) * 0.5f
        val iconHalf = min(pw * 0.38f, ph * 0.30f)
        RelicIcons.draw(
            canvas, btnPaint, id, cx, top + 40f * s + iconHalf,
            iconHalf, collected = true, fancy = true, phase = homePhase, lightSurface = true
        )

        var ty = top + 52f * s + iconHalf * 2f + 24f * s
        textPaint.textAlign = Paint.Align.CENTER
        lightText(canvas, Game.RELIC_NAMES[id], cx, ty, 40f * s, frameColor)
        ty += 40f * s
        val collected = game.relicCollected(id)
        val eraLine = buildString {
            append(Game.RELIC_ERAS[id])
            append("  ·  ")
            append(Game.RELIC_RARITY_NAMES[rarity])
            if (game.immortalMode && !collected) append("  ·  预览")
        }
        lightText(canvas, eraLine, cx, ty, 24f * s, LIGHT_SUB)
        ty += 48f * s

        val fact = Game.RELIC_FACTS[id]
        val factMaxW = pw * 0.84f
        if (fact.length > 11) {
            val split = splitTwoLines(fact)
            val line1 = fact.substring(0, split)
            val line2 = fact.substring(split)
            val sz1 = fittedTextSize(line1, 22f * s, factMaxW, 14f * s)
            val sz2 = fittedTextSize(line2, 22f * s, factMaxW, 14f * s)
            lightText(canvas, line1, cx, ty, sz1, LIGHT_TEXT)
            lightText(canvas, line2, cx, ty + 34f * s, sz2, LIGHT_TEXT)
        } else {
            val factSize = fittedTextSize(fact, 22f * s, factMaxW, 14f * s)
            lightText(canvas, fact, cx, ty, factSize, LIGHT_TEXT)
        }

        val swipeHint = if (game.immortalMode || game.relicsFound > 1) {
            "左右滑动查看 · 点击外部关闭"
        } else {
            "点击外部关闭"
        }
        lightText(canvas, swipeHint, cx, bottom - 36f * s, 20f * s, LIGHT_HINT)
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, s: Float) {
        PixelUi.drawButton(
            canvas, btnPaint, r.left, r.top, r.right, r.bottom,
            0xFF4A5A6E.toInt(), 0xFF6B4E28.toInt(), s,
            bevel = PixelUi.Bevel.RAISED, shadow = true
        )
        textPaint.textAlign = Paint.Align.CENTER
        val labelSize = min(32f * s, r.height() * 0.45f).coerceAtLeast(24f * s)
        pixText(canvas, label, r.centerX(), centeredBaselineY(r.centerY(), labelSize), labelSize, Color.WHITE, 1f, 1f)
    }

    /**
     * 按钮标签的居中基线：用字体真实 ascent/descent 算，而不是固定的 0.32 经验系数。
     * 按钮框越贴字号（比如迷你的年龄切换按钮），经验系数的误差就越明显。
     */
    private fun centeredBaselineY(cy: Float, textSize: Float): Float {
        val steps = (textSize / FONT_PX).roundToInt().coerceAtLeast(1)
        textPaint.textSize = (steps * FONT_PX).toFloat()
        val fm = textPaint.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    private fun fittedTextSize(text: String, preferredSize: Float, maxWidth: Float, minSize: Float): Float {
        var steps = (preferredSize / FONT_PX).roundToInt().coerceAtLeast(1)
        val minSteps = (minSize / FONT_PX).roundToInt().coerceAtLeast(1)
        while (steps > minSteps) {
            textPaint.textSize = (steps * FONT_PX).toFloat()
            if (textPaint.measureText(text) <= maxWidth) break
            steps--
        }
        return (steps * FONT_PX).toFloat()
    }

    private fun drawRelicBillboards(
        canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float
    ) {
        if (game.state != Game.State.RUNNING) return
        if (game.relicHudCount <= 0) return
        synchronized(game) {
            val n = game.relicHudCount.coerceAtMost(Game.RELIC_HUD_MAX)
            for (i in 0 until n) {
                val id = game.relicHudId[i]
                if (id !in 0 until Game.RELIC_COUNT) continue
                val x = game.relicHudX[i] * w
                val y = game.relicHudY[i] * h
                val scale = game.relicHudScale[i]
                // 不用文字认牌，只留像素图标；远处淡出，靠近再淡入
                val fade = ((scale - RELIC_LABEL_FADE_LO) /
                    (RELIC_LABEL_FADE_HI - RELIC_LABEL_FADE_LO)).coerceIn(0f, 1f)
                if (fade <= 0.02f) continue
                val iconHalf = (18f * s * scale).coerceIn(12f * s, 26f * s)
                val rarity = Game.RELIC_RARITY[id]
                val accent = game.relicBannerColor(rarity)
                val a255 = (fade * 255).toInt()
                // 浅色底牌 + 稀有度描边：与绿色障碍/路面拉开对比，上方叠 RelicIcons 小图
                val pad = iconHalf + 3f * s
                PixelUi.drawRect(
                    canvas, btnPaint, x - pad, y - pad, x + pad, y + pad,
                    withAlpha(0xF8F4E8.toInt(), (fade * 0xEE).toInt()), s,
                    bevel = PixelUi.Bevel.RAISED,
                    edge = withAlpha(accent, a255),
                    edgeW = max(2f, 2.5f * s), shadow = true
                )
                RelicIcons.draw(
                    canvas, btnPaint, id, x, y, iconHalf,
                    collected = true, lightSurface = true, withChrome = false, alpha = a255
                )
            }
        }
    }

    /** 亮色面板文字：不画阴影，避免点阵字与黑影糊成一团 */
    private fun lightText(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int
    ) {
        pixText(canvas, text, x, y, size, color, 0f, 0f)
    }

    private fun pixText(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int,
        shadowDx: Float, shadowDy: Float
    ) {
        val steps = (size / FONT_PX).roundToInt().coerceAtLeast(1)
        textPaint.textSize = (steps * FONT_PX).toFloat()
        val ix = x.roundToInt().toFloat()
        val iy = y.roundToInt().toFloat()
        // 偏移为 0 时跳过阴影，否则会叠在字上把颜色染脏
        if (shadowDx != 0f || shadowDy != 0f) {
            textPaint.color = 0xAA000000.toInt()
            canvas.drawText(text, ix + shadowDx * steps, iy + shadowDy * steps, textPaint)
        }
        textPaint.color = color
        canvas.drawText(text, ix, iy, textPaint)
    }

    private fun dim(canvas: Canvas, w: Float, h: Float) {
        dimPaint.color = 0x66000000
        canvas.drawRect(0f, 0f, w, h, dimPaint)
    }

    /**
     * 图鉴类浮层统一的底部收尾行：提示文字与关闭按钮共用同一条基线，
     * 排行榜 / 藏品 / 荣誉三处共用，改一处三处一起对齐。
     */
    private fun drawOverlayCloseRow(
        canvas: Canvas, left: Float, right: Float, panelBottom: Float, s: Float,
        hint: String, closeBtn: RectF
    ) {
        val closeSize = CLOSE_BTN_SIZE * s
        val margin = CLOSE_ROW_MARGIN * s
        val cy = panelBottom - margin - closeSize / 2f
        closeBtn.set(right - margin - closeSize, cy - closeSize / 2f, right - margin, cy + closeSize / 2f)
        val hintMaxW = ((right - left) - (closeSize + margin) * 2f - 16f * s).coerceAtLeast(40f * s)
        val hintSize = fittedTextSize(hint, 18f * s, hintMaxW, 12f * s)
        textPaint.textAlign = Paint.Align.CENTER
        lightText(canvas, hint, (left + right) * 0.5f, centeredBaselineY(cy, hintSize), hintSize, LIGHT_HINT)
        drawLightBtn(canvas, closeBtn, "X", s)
    }
}
