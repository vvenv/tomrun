package com.vvenv.tomrun

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.InputFilter
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

    private val pixelTypeface: Typeface = runCatching {
        Typeface.createFromAsset(context.assets, "fonts/fusion-pixel-12px.ttf")
    }.getOrElse { Typeface.DEFAULT_BOLD }

    private val textPaint = Paint().apply {
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
    private val dimPaint = Paint()
    private val btnPaint = Paint().apply {
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
    private val btnHelp = RectF()
    private val btnLeaveHome = RectF()
    private val btnCollection = RectF()
    private val btnHonor = RectF()
    private val homeOverlayPanel = RectF()
    private val btnCatalogClose = RectF()
    private val hitHouse = RectF()
    private val hitRoof = RectF()
    private val hitYardDeco = RectF()
    private val hitCosmeticColor = RectF()
    private val hitCosmeticTrail = RectF()
    private val hitCosmeticScarf = RectF()
    private val hitCosmeticHat = RectF()
    private val hitSky = RectF()
    private var showHelp = false
    private var homeSubView = HOME_SUB_SCENE
    private var homeEditing = false
    private var museumPage = 0
    private var honorPage = 0
    /** 藏品大图：-1 表示未打开 */
    private var museumDetailId = -1
    private val museumTileHits = Array(MUSEUM_PER_PAGE) { RectF() }
    private val btnMuseumPrev = RectF()
    private val btnMuseumNext = RectF()
    private val btnHomeTabs = Array(7) { RectF() }
    private val btnHomeL = RectF()
    private val btnHomeR = RectF()
    private val btnHomeBuy = RectF()
    private val hitHomeTitle = RectF()
    private val btnPause = RectF()
    private val btnResume = RectF()
    private val btnQuit = RectF()
    private var homePhase = 0f

    // 庭院猫 AI：站立张望 / 散步 / 与装饰互动 / 打盹
    private var catState = CAT_IDLE
    private var catTimer = 1.5f
    private var catX = -118f          // s 单位，相对场景中心
    private var catDir = 1f
    private var catTarget = -118f
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
    private var butterflyHeight = 70f
    private var butterflyDir = 1f
    private var petTimer = 0f
    private var petCount = 0
    private val yardCatHit = RectF()
    private val yardTelescopeHit = RectF()
    private val yardSceneHit = RectF()
    private val butterflyHit = RectF()
    private var yardSceneCx = 0f
    private var yardSceneGy = 0f
    private var yardSceneS = 1f
    private var yardSceneHalf = 0f
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
    private var handMarkerLife = 0f
    private var strokeActive = false
    private var strokeHoldT = 0f
    private var strokeCoinTimer = 0f
    private var strokeCoinsGiven = 0
    private var strokeCoinCooldown = 0f
    private val yardFloaters = ArrayList<YardFloater>()
    private val yardSingleTapRunnable = Runnable {
        handleYardSingleTap(yardPendingTapX, yardPendingTapY)
    }
    private var showStargazing = false
    private var stargazeBody = 0
    private val btnStargazeL = RectF()
    private val btnStargazeR = RectF()
    private val btnStargazeUpgrade = RectF()
    private val btnStargazeAge = RectF()
    private val btnStargazeDone = RectF()
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
        private val ROOF_CHIPS = intArrayOf(
            0xFFD9483B.toInt(), 0xFF3FA9A5.toInt(), 0xFF8C6BD9.toInt(), 0xFFF2C14E.toInt()
        )
        private const val HOME_SUB_SCENE = 0
        private const val HOME_SUB_COLLECTION = 1
        private const val HOME_SUB_HONOR = 2
        private val CAT_COSMETIC_TABS = arrayOf("配色", "光迹", "围巾", "帽子")
        /** 跑酷连击 / 横幅 / 纪录等 toast 统一高度 */
        private const val RUN_BANNER_Y = 0.283f
        /** 藏品 tile 网格；荣誉仍按行分页 */
        private const val MUSEUM_COLS = 4
        private const val MUSEUM_ROWS = 4
        private const val MUSEUM_PER_PAGE = MUSEUM_COLS * MUSEUM_ROWS
        private const val HONOR_PER_PAGE = 8
        /** 全屏图鉴：顶栏 + 底栏固定占位，中间才是内容 */
        private const val OVERLAY_HEADER_H = 96f
        private const val OVERLAY_FOOTER_H = 88f
        private const val TILE_COMMON_BG = 0xFFE2F0E6.toInt()
        private const val TILE_RARE_BG = 0xFFDCECF4.toInt()
        private const val TILE_LEGEND_BG = 0xFFF5E8C4.toInt()
        /** 亮色浮窗配色（藏品 / 荣誉共用） */
        private const val LIGHT_PANEL = 0xFFF7F1E6.toInt()
        private const val LIGHT_PANEL_EDGE = 0xFFB07A18.toInt()
        private const val LIGHT_DIVIDER = 0x55B07A18
        private const val LIGHT_TITLE = 0xFF5C3D0A.toInt()
        private const val LIGHT_SUB = 0xFF2F4A66.toInt()
        private const val LIGHT_ROW = 0xFFFFFCF5.toInt()
        private const val LIGHT_ROW_EDGE = 0xFFD9CDB8.toInt()
        private const val LIGHT_TEXT = 0xFF1E1E24.toInt()
        private const val LIGHT_MUTED = 0xFF4A5560.toInt()
        private const val LIGHT_HINT = 0xFF5A6068.toInt()
        private const val LIGHT_BTN = 0xFFEFE6D4.toInt()
        private const val LIGHT_BTN_EDGE = 0xFF8A6A20.toInt()
        private const val LIGHT_BADGE = 0xFFFFF8EC.toInt()
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
        private const val STROKE_MAX_COINS = 4
        private const val STROKE_COIN_INTERVAL = 0.82f
        private const val STROKE_COOLDOWN = 48f
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
                if (isYardInteractive()) {
                    yardFingerDown = true
                    yardFingerX = event.x
                    yardFingerY = event.y
                    yardFingerDownTime = SystemClock.uptimeMillis()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (yardFingerDown) {
                    yardFingerX = event.x
                    yardFingerY = event.y
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
                    handTargetX = screenToSceneX(event.x)
                    handMarkerLife = 2.5f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (strokeActive) {
                    endStrokeCat()
                    consumed = true
                }
                if (!consumed && event.actionMasked == MotionEvent.ACTION_UP) {
                    if (!yardLongPressTriggered) handleTap(event.x, event.y)
                }
                yardFingerDown = false
            }
        }
        return true
    }

    private fun isYardInteractive(): Boolean =
        (game.state == Game.State.READY || game.state == Game.State.DEAD) &&
            game.menuPanel == Game.PANEL_HOME && !showStargazing

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
                if (showHelp) { showHelp = false; return }
                if (handleSecretTitleTap(x, y)) return
                when {
                    btnHelp.contains(x, y) -> showHelp = true
                    btnHome.contains(x, y) -> openHomePage()
                    else -> game.onTap()
                }
            }
            Game.PANEL_HOME -> {
                if (showStargazing) {
                    when {
                        btnStargazeAge.contains(x, y) -> {
                            game.toggleStargazeAgeMode()
                            showToast("已切换为${game.stargazeAgeLabel()}版")
                        }
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
                        btnStargazeDone.contains(x, y) -> closeStargazing()
                        else -> closeStargazing()
                    }
                    return
                }
                when (homeSubView) {
                    HOME_SUB_COLLECTION -> {
                        if (museumDetailId >= 0) {
                            if (btnCatalogClose.contains(x, y)) museumDetailId = -1
                            return
                        }
                        when {
                            btnCatalogClose.contains(x, y) -> {
                                homeSubView = HOME_SUB_SCENE
                            }
                            btnMuseumPrev.contains(x, y) -> museumPage =
                                (museumPage + museumPages() - 1) % museumPages()
                            btnMuseumNext.contains(x, y) -> museumPage =
                                (museumPage + 1) % museumPages()
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
                        when {
                            btnCatalogClose.contains(x, y) -> homeSubView = HOME_SUB_SCENE
                            btnMuseumPrev.contains(x, y) -> honorPage =
                                (honorPage + honorPages() - 1) % honorPages()
                            btnMuseumNext.contains(x, y) -> honorPage =
                                (honorPage + 1) % honorPages()
                        }
                        return
                    }
                }
                when {
                    btnLeaveHome.contains(x, y) -> leaveHomePage()
                    btnCollection.contains(x, y) -> {
                        museumPage = 0
                        museumDetailId = -1
                        homeSubView = HOME_SUB_COLLECTION
                    }
                    btnHonor.contains(x, y) -> {
                        honorPage = 0
                        homeSubView = HOME_SUB_HONOR
                    }
                    hitHomeTitle.contains(x, y) -> showRenameDialog(firstTime = false)
                    homeEditing && btnHomeL.contains(x, y) -> game.browseHome(-1)
                    homeEditing && btnHomeR.contains(x, y) -> game.browseHome(1)
                    homeEditing && btnHomeBuy.contains(x, y) -> showToast(game.buyOrEquipHome())
                    homeEditing && game.isCatHomeTab() && btnHomeTabs[0].contains(x, y) ->
                        game.switchHomeTab(Game.HOME_TAB_COLOR)
                    homeEditing && game.isCatHomeTab() && btnHomeTabs[1].contains(x, y) ->
                        game.switchHomeTab(Game.HOME_TAB_TRAIL)
                    homeEditing && game.isCatHomeTab() && btnHomeTabs[2].contains(x, y) ->
                        game.switchHomeTab(Game.HOME_TAB_SCARF)
                    homeEditing && game.isCatHomeTab() && btnHomeTabs[3].contains(x, y) ->
                        game.switchHomeTab(Game.HOME_TAB_HAT)
                    yardTelescopeHit.contains(x, y) && game.canUseTelescope() -> openStargazing()
                    hitHouse.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_HOUSE)
                    hitRoof.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_ROOF)
                    hitYardDeco.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_DECO)
                    hitCosmeticColor.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_COLOR)
                    hitCosmeticTrail.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_TRAIL)
                    hitCosmeticScarf.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_SCARF)
                    hitCosmeticHat.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_HAT)
                    yardCatHit.contains(x, y) -> selectHomeCategory(Game.HOME_TAB_COLOR)
                    hitSky.contains(x, y) -> homeEditing = false
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

    private fun openHomePage() {
        homeSubView = HOME_SUB_SCENE
        homeEditing = false
        museumDetailId = -1
        game.switchMenuPanel(Game.PANEL_HOME)
    }

    private fun leaveHomePage() {
        closeStargazing()
        cancelYardTouch()
        homeSubView = HOME_SUB_SCENE
        homeEditing = false
        museumDetailId = -1
        game.switchMenuPanel(Game.PANEL_MAIN)
    }

    private fun selectHomeCategory(tab: Int) {
        game.switchHomeTab(tab)
        homeEditing = true
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

            // 右上角：跑酷才显示得分/金币；菜单只留最高分与钱包
            textPaint.textAlign = Paint.Align.RIGHT
            if (game.state == Game.State.RUNNING) {
                pixText(canvas, "得分 ${game.score}", w - 36f * s, top + 66f * s, 42f * s, Color.WHITE, sdx, sdy)
                pixText(canvas, "金币 ${game.coins}", w - 36f * s, top + 106f * s, 28f * s, 0xFFFFD54A.toInt(), sdx, sdy)
                if (game.combo > 0) {
                    val cColor = when {
                        game.comboMult >= 5 -> 0xFFFF6B6B.toInt()
                        game.comboMult >= 3 -> 0xFFFFC21F.toInt()
                        else -> 0xFF7DEBA0.toInt()
                    }
                    val next = game.comboToNext()
                    val line = if (next > 0) "连击 ${game.combo} x${game.comboMult}  差$next"
                    else "连击 ${game.combo} x${game.comboMult} MAX"
                    pixText(canvas, line, w - 36f * s, top + 142f * s, 26f * s, cColor, sdx, sdy)
                }
            } else {
                pixText(canvas, "最高 ${game.highScore}", w - 36f * s, top + 66f * s, 28f * s, Color.WHITE, sdx, sdy)
                pixText(canvas, "钱包 ${game.wallet}", w - 36f * s, top + 102f * s, 26f * s, 0xFFFFC21F.toInt(), sdx, sdy)
            }

            // 左上角 buff：仅跑酷中显示
            textPaint.textAlign = Paint.Align.LEFT
            val pauseBottom = top + 30f * s + 100f * s   // 与 drawPauseButton 同尺寸
            val buffStartY = pauseBottom + 32f * s
            var buffY = buffStartY
            if (game.state == Game.State.RUNNING) {
                if (game.immortalMode) {
                    pixText(canvas, "测试 · 不死", 36f * s, buffY, 28f * s, 0xFF4DE8FF.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
                if (game.helmetLayers > 0) {
                    pixText(canvas, if (game.helmetLayers >= 2) "头盔 x2" else "头盔",
                        36f * s, buffY, 28f * s, 0xFFFFC21F.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
                if (game.magnetTime > 0f) {
                    pixText(canvas, "磁铁 ${game.magnetLeft()}s", 36f * s, buffY, 28f * s, 0xFFFF6B6B.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
                if (game.doubleTime > 0f) {
                    pixText(canvas, "加倍 ${game.doubleLeft()}s", 36f * s, buffY, 28f * s, 0xFFC77DFF.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
                if (game.boostTime > 0f) {
                    pixText(canvas, "冲刺 ${game.boostLeft()}s", 36f * s, buffY, 28f * s, 0xFF4DE8FF.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
                if (game.riding != null) {
                    pixText(canvas, "滑索中", 36f * s, buffY, 28f * s, 0xFF7DEBA0.toInt(), sdx, sdy)
                    buffY += 36f * s
                }
            }

            // 顶部中央：本局发现文物（与头盔提示同高）
            if (game.state == Game.State.RUNNING && game.runRelics > 0) {
                textPaint.textAlign = Paint.Align.CENTER
                pixText(
                    canvas, "文物 ×${game.runRelics}", w / 2f, buffStartY, 26f * s,
                    0xFFC77DFF.toInt(), sdx, sdy
                )
            }

            // 顶部中央：当前宇宙（文物计数上方一行）
            if (game.state == Game.State.RUNNING && game.universe != Game.UNI_MEADOW) {
                textPaint.textAlign = Paint.Align.CENTER
                pixText(
                    canvas, "· ${Game.UNIVERSE_NAMES[game.universe]} ·", w / 2f,
                    buffStartY - 40f * s, 26f * s,
                    UNI_HUD[game.universe % UNI_HUD.size], sdx, sdy
                )
            }

            // 局内任务（竖屏时抬高避开手势条）
            if (game.state == Game.State.RUNNING) {
                textPaint.textAlign = Paint.Align.LEFT
                var qy = h - (if (portrait) 84f else 36f) * s
                for (i in game.quests.indices.reversed()) {
                    val q = game.quests[i]
                    val color = if (q.done) 0xFF7DEBA0.toInt() else 0xDDFFFFFF.toInt()
                    val mark = if (q.done) "√" else "·"
                    pixText(
                        canvas, "$mark ${q.label} ${q.progress}/${q.target}",
                        36f * s, qy, 24f * s, color, sdx, sdy
                    )
                    qy -= 32f * s
                }
            }

            // 飘分：收拢到右上角计分区下方，不遮挡赛道视线走廊
            if (game.state == Game.State.RUNNING && game.floatFlash > 0f && game.lastFloat.isNotEmpty()) {
                val alpha = (min(1f, game.floatFlash / 0.35f) * 255).toInt()
                val rise = (0.9f - game.floatFlash) * 18f * s
                textPaint.textAlign = Paint.Align.RIGHT
                pixText(
                    canvas, game.lastFloat, w - 36f * s, top + 182f * s - rise, 26f * s,
                    (alpha shl 24) or (game.lastFloatColor and 0x00FFFFFF), sdx, sdy
                )
            }
            textPaint.textAlign = Paint.Align.CENTER

            // 连击 / 横幅 / 新纪录：统一通知带高度
            if (game.state == Game.State.RUNNING && game.comboFlash > 0f) {
                val t = 1.4f - game.comboFlash
                val pop = 1f + 0.25f * (1f - min(1f, t * 5f))
                val alpha = (min(1f, game.comboFlash / 0.4f) * 255).toInt()
                pixText(
                    canvas, "连击 x${game.comboMult}！", w / 2f, h * RUN_BANNER_Y, 40f * s * pop,
                    (alpha shl 24) or 0x00FFC21F, sdx, sdy
                )
            }

            if (game.bannerFlash > 0f && game.bannerText.isNotEmpty()) {
                val alpha = (min(1f, game.bannerFlash / 0.45f) * 255).toInt()
                pixText(
                    canvas, game.bannerText, w / 2f, h * RUN_BANNER_Y, 30f * s,
                    (alpha shl 24) or (game.bannerColor and 0x00FFFFFF), sdx, sdy
                )
            }

            if (game.state == Game.State.RUNNING && game.recordFlash > 0f) {
                val t = 2.6f - game.recordFlash
                val pop = 1f + 0.3f * (1f - min(1f, t * 5f))
                val alpha = (min(1f, game.recordFlash / 0.5f) * 255).toInt()
                pixText(
                    canvas, "新纪录！", w / 2f, h * RUN_BANNER_Y, 48f * s * pop,
                    (alpha shl 24) or 0x00FFD426, sdx, sdy
                )
            }
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
                drawPauseButton(canvas, s)
                if (game.paused) drawPauseOverlay(canvas, w, h, s, sdx, sdy)
            }
        }

        // 穿越白闪（半透明，避免完全遮挡赛道导致撞障）
        if (game.portalFlash > 0f) {
            val a = (min(1f, game.portalFlash / 0.55f) * 120).toInt()
            dimPaint.color = (a shl 24) or 0x00FFFFFF
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }

        if (toastLife > 0f && toast.isNotEmpty() &&
            homeSubView != HOME_SUB_COLLECTION && homeSubView != HOME_SUB_HONOR
        ) {
            val fade = min(1f, toastLife / 0.4f)
            val appear = min(1f, (1.8f - toastLife) / 0.12f)
            val size = 28f * s
            val steps = (size / FONT_PX).roundToInt().coerceAtLeast(1)
            textPaint.textSize = (steps * FONT_PX).toFloat()
            val fm = textPaint.fontMetrics
            val halfW = textPaint.measureText(toast) / 2f + 22f * s
            val cx = (w / 2f).roundToInt().toFloat()
            // 跑酷与连击同高；菜单面板仍落在按钮区上方
            val bandY = if (game.state == Game.State.RUNNING) h * RUN_BANNER_Y else h * 0.78f
            val baseY = (bandY + (1f - appear) * 12f * s).roundToInt().toFloat()
            val toastTop = baseY + fm.ascent - 12f * s
            val toastBottom = baseY + fm.descent + 12f * s
            // 深色底板 + 描边，遮住下层文字保证可读
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = withAlpha(0xFF1C2634.toInt(), (fade * 235).toInt())
            canvas.drawRect(cx - halfW, toastTop, cx + halfW, toastBottom, btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 1f
            btnPaint.color = withAlpha(0xFFFFD426.toInt(), (fade * 255).toInt())
            canvas.drawRect(cx - halfW, toastTop, cx + halfW, toastBottom, btnPaint)
            btnPaint.style = Paint.Style.FILL
            pixText(
                canvas, toast, cx, baseY, size,
                ((fade * 255).toInt() shl 24) or 0x00FFFFFF, sdx, sdy
            )
        }
    }

    private fun drawMainMenu(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val dead = game.state == Game.State.DEAD
        if (dead) {
            pixText(canvas, "游戏结束", w / 2f, h * 0.27f, 64f * s, Color.WHITE, sdx, sdy)
            val record = if (game.score >= game.highScore && game.score > 0) "  新纪录！" else ""
            pixText(canvas, "得分 ${game.score}$record", w / 2f, h * 0.38f, 36f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "本局金币 ${game.coins}  ·  钱包 +${game.runWalletEarn}", w / 2f, h * 0.46f, 28f * s, 0xFFFFD54A.toInt(), sdx, sdy)
            val qd = game.quests.count { it.done }
            pixText(canvas, "任务 $qd/3  ·  最高连击 ${game.bestComboRun}", w / 2f, h * 0.53f, 26f * s, 0xFF7DEBA0.toInt(), sdx, sdy)
            if (game.runRelics > 0) {
                pixText(
                    canvas, "发现文物 ×${game.runRelics}  ·  图鉴 ${game.relicsFound}/${Game.RELIC_COUNT}",
                    w / 2f, h * 0.59f, 26f * s, 0xFFC77DFF.toInt(), sdx, sdy
                )
            }
            if (game.deadTime > 0.6f) {
                pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.82f, 28f * s, Color.WHITE, sdx, sdy)
            }
        } else {
            val worldTitle = "${game.characterName}的世界"
            val worldTitleSize = fittedTextSize(worldTitle, 72f * s, w * 0.64f, 36f * s)
            pixText(canvas, worldTitle, w / 2f, h * 0.30f, worldTitleSize, Color.WHITE, sdx, sdy)
            val blink = if (kotlin.math.sin(homePhase * 3f) > -0.3f) 255 else 120
            pixText(canvas, "点击屏幕开始", w / 2f, h * 0.46f, 32f * s, withAlpha(Color.WHITE, blink), sdx, sdy)
        }

        // 底栏：左帮助 · 右家（对称）
        val portrait = h > w
        val rowH = if (portrait) 72f * s else 52f * s
        val margin = if (portrait) 16f * s else 28f * s
        val rowBottom = h - (if (portrait) 48f else 28f) * s
        val rowTop = rowBottom - rowH
        btnHelp.set(margin, rowTop, margin + rowH, rowBottom)
        btnHome.set(w - margin - rowH, rowTop, w - margin, rowBottom)
        drawBtn(canvas, btnHelp, "?", s)
        drawHomeEntryBtn(canvas, btnHome, s)

        if (showHelp) drawHelpOverlay(canvas, w, h, s, sdx, sdy)
    }

    private fun drawHelpOverlay(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        dim(canvas, w, h)
        val pw = min(w * 0.72f, 560f * s)
        val top = h * 0.16f
        val bottom = h * 0.84f
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xF21C2634.toInt()
        canvas.drawRect(w / 2f - pw / 2f, top, w / 2f + pw / 2f, bottom, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1f
        btnPaint.color = 0xFFFFD426.toInt()
        canvas.drawRect(w / 2f - pw / 2f, top, w / 2f + pw / 2f, bottom, btnPaint)
        btnPaint.style = Paint.Style.FILL

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

    // ---------- 家（装扮；藏品 / 荣誉浮窗） ----------
    private fun drawHome(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val isCatTab = game.isCatHomeTab()
        val house = if (game.homeTab == Game.HOME_TAB_HOUSE) game.homeBrowseHouse else game.houseStyle
        val roof = if (game.homeTab == Game.HOME_TAB_ROOF) game.homeBrowseRoof else game.roofStyle
        val ghostDeco = if (game.homeTab == Game.HOME_TAB_DECO) game.homeBrowseDeco else -1

        val yardColor = when (game.homeTab) {
            Game.HOME_TAB_COLOR -> game.shopBrowseColor
            else -> game.catColor
        }
        val yardTrail = when (game.homeTab) {
            Game.HOME_TAB_TRAIL -> game.shopBrowseTrail
            else -> game.trailStyle
        }
        val yardScarf = when (game.homeTab) {
            Game.HOME_TAB_SCARF -> game.shopBrowseScarf
            else -> game.scarfStyle
        }
        val yardHat = when (game.homeTab) {
            Game.HOME_TAB_HAT -> game.shopBrowseHat
            else -> game.hatStyle
        }
        val cosmeticGhost = isCatTab && when (game.homeTab) {
            Game.HOME_TAB_COLOR -> !game.ownsColor(game.shopBrowseColor) && game.shopBrowseColor > 0
            Game.HOME_TAB_TRAIL -> !game.ownsTrail(game.shopBrowseTrail) && game.shopBrowseTrail > 0
            Game.HOME_TAB_SCARF -> !game.ownsScarf(game.shopBrowseScarf) && game.shopBrowseScarf > 0
            Game.HOME_TAB_HAT -> !game.ownsHat(game.shopBrowseHat) && game.shopBrowseHat > 0
            else -> false
        }

        drawHomeScene(
            canvas, w, h, s, house, roof, ghostDeco,
            yardColor, yardTrail, yardScarf, yardHat, cosmeticGhost,
            highlightEditing = homeEditing
        )

        drawHomeTopBar(canvas, w, h, s, sdx, sdy, isCatTab)

        if (homeEditing) {
            drawHomeShopStrip(canvas, w, h, s, sdx, sdy)
            val hint = if (isCatTab) "点选猫装扮分类 · 点天空收起" else "点天空收起"
            pixText(canvas, hint, w / 2f, h * 0.52f, 20f * s, 0x99FFFFFF.toInt(), sdx, sdy)
        }

        if (homeSubView == HOME_SUB_SCENE) {
            layoutLeaveHomeButton(w, h, s)
            drawLeaveHomeBtn(canvas, btnLeaveHome, s)
        } else {
            btnLeaveHome.setEmpty()
        }

        when (homeSubView) {
            HOME_SUB_COLLECTION -> drawCollectionOverlay(canvas, w, h, s, sdx, sdy)
            HOME_SUB_HONOR -> drawHonorOverlay(canvas, w, h, s, sdx, sdy)
        }
        if (showStargazing) drawStargazeOverlay(canvas, w, h, s, sdx, sdy)
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
        val hint = if (isCatTab) "点场景元素装扮家与猫" else "点房屋/屋顶/庭院/猫来装扮"
        val hintSize = if (showHint) fittedTextSize(hint, 16f * s, w * 0.86f, 12f * s) else 0f
        val lv = game.homeLevel()

        val row1H = if (portrait) 44f * s else 40f * s
        val energyH = 26f * s
        val rewardPad = 10f * s
        val rewardH = rewardSize + rewardPad * 2f
        val topPad = 18f * s
        val afterRow1 = 16f * s
        val afterPlaque = 14f * s
        val afterEnergy = 10f * s
        val afterReward = 10f * s
        val bottomPad = 14f * s

        // 按实际行高累加，避免能量格与奖励条叠压
        var barH = hudTop + topPad + row1H + afterRow1 + plaqueH + afterPlaque +
            energyH + afterEnergy + rewardH + afterReward
        if (showHint) barH += hintSize + bottomPad else barH += bottomPad

        // 分层底：深 → 浅，底部木纹金边，让天空透一点
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xE8141C28.toInt()
        canvas.drawRect(0f, 0f, w, barH * 0.42f, btnPaint)
        btnPaint.color = 0xC81C2634.toInt()
        canvas.drawRect(0f, barH * 0.42f, w, barH * 0.78f, btnPaint)
        btnPaint.color = 0x99182430.toInt()
        canvas.drawRect(0f, barH * 0.78f, w, barH, btnPaint)
        // 木纹底边
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(0f, barH - 5f * s, w, barH - 2f * s, btnPaint)
        btnPaint.color = 0xFF97622F.toInt()
        canvas.drawRect(0f, barH - 2f * s, w, barH, btnPaint)
        val edgePulse = 0.55f + 0.45f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 1.8f))
        btnPaint.color = withAlpha(0xFFFFD426.toInt(), (edgePulse * 180).toInt())
        canvas.drawRect(0f, barH - 1.5f * s, w, barH, btnPaint)
        // 角饰
        drawHomeBarCorner(canvas, 10f * s, hudTop + 10f * s, s, 1f)
        drawHomeBarCorner(canvas, w - 10f * s, hudTop + 10f * s, s, -1f)

        // 第 1 行：金币芯片 + 藏品/荣誉
        var y = hudTop + topPad
        val row1Cy = y + row1H * 0.5f
        val btnH = if (portrait) 40f * s else 36f * s
        val btnW = if (portrait) min(88f * s, w * 0.20f) else 76f * s
        val btnGap = 8f * s
        val btnRight = w - margin
        btnHonor.set(btnRight - btnW, row1Cy - btnH * 0.45f, btnRight, row1Cy + btnH * 0.55f)
        btnCollection.set(btnHonor.left - btnGap - btnW, btnHonor.top, btnHonor.left - btnGap, btnHonor.bottom)
        drawBtn(canvas, btnCollection, "藏品", s)
        drawBtn(canvas, btnHonor, "荣誉", s)
        drawHomeWalletChip(canvas, margin, row1Cy, s, sdx, sdy)
        y += row1H + afterRow1

        // 第 2 行：木牌铭牌标题（可点改名）
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
        hitHomeTitle.set(plaqueL, plaqueT, plaqueR, plaqueB)
        drawHomeNamePlaque(canvas, plaqueL, plaqueT, plaqueR, plaqueB, s)
        drawTinyHouseIcon(canvas, plaqueL + 22f * s, plaqueCy, s * 0.85f)
        pixText(
            canvas, title, titleCx + 6f * s, plaqueCy + titleSize * 0.32f,
            titleSize, 0xFFFFF6E8.toInt(), sdx, sdy
        )
        y = plaqueB + afterPlaque

        // 第 3 行：家能量等级格（整行独占高度）
        val energyCy = y + energyH * 0.5f
        drawHomeEnergyMeter(canvas, w / 2f, energyCy, s, lv, sdx, sdy)
        y += energyH + afterEnergy

        // 第 4 行：奖励说明（芯片完全落在本行内）
        textPaint.textSize = rewardSize
        val rewardChipW = min(w * 0.88f, textPaint.measureText(rewardLine) + 28f * s)
        val rewardTop = y
        val rewardBottom = y + rewardH
        val rewardCy = (rewardTop + rewardBottom) * 0.5f
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0x55101820.toInt()
        canvas.drawRect(
            w / 2f - rewardChipW / 2f, rewardTop,
            w / 2f + rewardChipW / 2f, rewardBottom, btnPaint
        )
        pixText(
            canvas, rewardLine, w / 2f, rewardCy + rewardSize * 0.32f,
            rewardSize, 0xCCE8F4FF.toInt(), sdx, sdy
        )
        y = rewardBottom + afterReward

        if (showHint) {
            pixText(canvas, hint, w / 2f, y + hintSize * 0.75f, hintSize, 0x88FFFFFF.toInt(), sdx, sdy)
        }
    }

    private fun drawHomeBarCorner(canvas: Canvas, x: Float, y: Float, s: Float, dir: Float) {
        val len = 14f * s
        val thick = 2.5f * s
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xAAFFD426.toInt()
        canvas.drawRect(x, y, x + dir * len, y + thick, btnPaint)
        canvas.drawRect(x, y, x + dir * thick, y + len, btnPaint)
    }

    private fun drawHomeWalletChip(canvas: Canvas, left: Float, cy: Float, s: Float, sdx: Float, sdy: Float) {
        val label = "${game.wallet}"
        val labelSize = 22f * s
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = labelSize
        val textW = textPaint.measureText(label)
        val chipH = 36f * s
        val chipW = 28f * s + textW + 16f * s
        val top = cy - chipH * 0.5f
        val bottom = cy + chipH * 0.5f
        val right = left + chipW

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xEE2A2418.toInt()
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.color = 0xFF3A3020.toInt()
        canvas.drawRect(left + 2f * s, top + 2f * s, right - 2f * s, top + 6f * s, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1.5f * s
        btnPaint.color = 0xCCFFC21F.toInt()
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.FILL

        // 像素金币
        val cx = left + 16f * s
        btnPaint.color = 0xFFFFC21F.toInt()
        canvas.drawRect(cx - 8f * s, cy - 8f * s, cx + 8f * s, cy + 8f * s, btnPaint)
        btnPaint.color = 0xFFFFE878.toInt()
        canvas.drawRect(cx - 5f * s, cy - 5f * s, cx + 5f * s, cy + 5f * s, btnPaint)
        btnPaint.color = 0xFFB8860B.toInt()
        canvas.drawRect(cx - 2f * s, cy - 5f * s, cx + 2f * s, cy + 5f * s, btnPaint)

        pixText(canvas, label, left + 28f * s, cy + labelSize * 0.32f, labelSize, 0xFFFFD75E.toInt(), sdx, sdy)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeNamePlaque(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, s: Float) {
        val pulse = 0.65f + 0.35f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.2f))
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0x66000000
        canvas.drawRect(l + 3f * s, t + 4f * s, r + 3f * s, b + 4f * s, btnPaint)
        btnPaint.color = 0xEE5A3A1E.toInt()
        canvas.drawRect(l, t, r, b, btnPaint)
        btnPaint.color = 0xFF7A5230.toInt()
        canvas.drawRect(l + 3f * s, t + 3f * s, r - 3f * s, t + 9f * s, btnPaint)
        btnPaint.color = 0xFF4A2E16.toInt()
        canvas.drawRect(l + 3f * s, b - 8f * s, r - 3f * s, b - 3f * s, btnPaint)
        // 木纹横线
        btnPaint.color = 0x335A3A1E
        for (i in 1..3) {
            val ly = t + 12f * s + i * ((b - t - 20f * s) / 4f)
            canvas.drawRect(l + 8f * s, ly, r - 8f * s, ly + 1.5f * s, btnPaint)
        }
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2f * s
        btnPaint.color = withAlpha(0xFFFFD426.toInt(), (pulse * 220).toInt())
        canvas.drawRect(l, t, r, b, btnPaint)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = withAlpha(0xFFFFD426.toInt(), (pulse * 255).toInt())
        canvas.drawRect(l + 2f * s, t + 2f * s, l + 7f * s, b - 2f * s, btnPaint)
        canvas.drawRect(r - 7f * s, t + 2f * s, r - 2f * s, b - 2f * s, btnPaint)
    }

    private fun drawTinyHouseIcon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFB07A45.toInt()
        canvas.drawRect(cx - 9f * s, cy - 1f * s, cx + 9f * s, cy + 10f * s, btnPaint)
        btnPaint.color = 0xFFD9483B.toInt()
        canvas.drawRect(cx - 12f * s, cy - 8f * s, cx + 12f * s, cy - 1f * s, btnPaint)
        canvas.drawRect(cx - 8f * s, cy - 13f * s, cx + 8f * s, cy - 8f * s, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(cx + 2f * s, cy + 1f * s, cx + 6f * s, cy + 10f * s, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(cx - 6f * s, cy + 1f * s, cx - 2f * s, cy + 5f * s, btnPaint)
    }

    private fun drawHomeEnergyMeter(
        canvas: Canvas, cx: Float, cy: Float, s: Float, lv: Int, sdx: Float, sdy: Float
    ) {
        val label = "家能量"
        val labelSize = 18f * s
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = labelSize
        val labelW = textPaint.measureText(label)
        val pip = 12f * s
        val pipGap = 5f * s
        val lvLabel = "Lv$lv"
        textPaint.textSize = 18f * s
        val lvW = textPaint.measureText(lvLabel)
        val totalW = labelW + 10f * s + 3f * (pip + pipGap) + 8f * s + lvW
        var x = cx - totalW / 2f

        pixText(canvas, label, x, cy + labelSize * 0.32f, labelSize, 0xFF9AD9A0.toInt(), sdx, sdy)
        x += labelW + 10f * s

        val glow = 0.7f + 0.3f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.6f))
        for (i in 0 until 3) {
            val filled = i < lv
            btnPaint.style = Paint.Style.FILL
            if (filled) {
                btnPaint.color = withAlpha(0xFF7DEBA0.toInt(), (glow * 255).toInt())
                canvas.drawRect(x - 1.5f * s, cy - pip * 0.5f - 1.5f * s, x + pip + 1.5f * s, cy + pip * 0.5f + 1.5f * s, btnPaint)
                btnPaint.color = 0xFF7DEBA0.toInt()
            } else {
                btnPaint.color = 0xFF2A3848.toInt()
            }
            canvas.drawRect(x, cy - pip * 0.5f, x + pip, cy + pip * 0.5f, btnPaint)
            if (filled) {
                btnPaint.color = 0xFFB8FFD0.toInt()
                canvas.drawRect(x + 2f * s, cy - pip * 0.5f + 2f * s, x + pip * 0.55f, cy - 1f * s, btnPaint)
            } else {
                btnPaint.style = Paint.Style.STROKE
                btnPaint.strokeWidth = 1.5f * s
                btnPaint.color = 0x663A5068.toInt()
                canvas.drawRect(x, cy - pip * 0.5f, x + pip, cy + pip * 0.5f, btnPaint)
                btnPaint.style = Paint.Style.FILL
            }
            x += pip + pipGap
        }

        textPaint.textAlign = Paint.Align.LEFT
        pixText(
            canvas, lvLabel, x + 8f * s, cy + 18f * s * 0.32f, 18f * s,
            if (lv > 0) 0xFF7DEBA0.toInt() else 0xFF8899AA.toInt(), sdx, sdy
        )
        textPaint.textAlign = Paint.Align.CENTER
    }

    /** 主菜单右下角「家」入口：像素小房子 */
    private fun drawHomeEntryBtn(canvas: Canvas, r: RectF, s: Float) {
        drawBtn(canvas, r, "", s)
        val cx = r.centerX()
        val cy = r.centerY()
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFB07A45.toInt()
        canvas.drawRect(cx - 14f * s, cy + 2f * s, cx + 14f * s, cy + 18f * s, btnPaint)
        btnPaint.color = 0xFFD9483B.toInt()
        canvas.drawRect(cx - 18f * s, cy - 10f * s, cx + 18f * s, cy + 2f * s, btnPaint)
        canvas.drawRect(cx - 12f * s, cy - 18f * s, cx + 12f * s, cy - 10f * s, btnPaint)
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(cx + 4f * s, cy + 2f * s, cx + 10f * s, cy + 18f * s, btnPaint)
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
    }

    private fun drawLeaveHomeBtn(canvas: Canvas, r: RectF, s: Float) {
        val pulse = 0.72f + 0.28f * (0.5f + 0.5f * kotlin.math.sin(homePhase * 2.4f))
        val accent = withAlpha(0xFFFFD426.toInt(), (pulse * 255).toInt())

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0x55000000
        canvas.drawRect(r.left + 3f * s, r.top + 5f * s, r.right + 3f * s, r.bottom + 5f * s, btnPaint)

        btnPaint.color = 0xEE2A3848.toInt()
        canvas.drawRect(r, btnPaint)
        btnPaint.color = 0xFF3A4E62.toInt()
        canvas.drawRect(r.left + 4f * s, r.top + 4f * s, r.right - 4f * s, r.top + 10f * s, btnPaint)

        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2f
        btnPaint.color = accent
        canvas.drawRect(r, btnPaint)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = accent
        canvas.drawRect(r.left + 2f * s, r.top + 2f * s, r.left + 8f * s, r.bottom - 2f * s, btnPaint)

        val doorCx = r.left + 34f * s
        val doorCy = r.centerY()
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(doorCx - 10f * s, doorCy - 14f * s, doorCx + 10f * s, doorCy + 14f * s, btnPaint)
        btnPaint.color = 0xFF553A20.toInt()
        canvas.drawRect(doorCx - 6f * s, doorCy - 10f * s, doorCx + 6f * s, doorCy + 14f * s, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(doorCx + 2f * s, doorCy, doorCx + 5f * s, doorCy + 4f * s, btnPaint)

        val ax = doorCx + 22f * s
        btnPaint.color = 0xFF7DEBA0.toInt()
        canvas.drawRect(ax, doorCy - 2f * s, ax + 14f * s, doorCy + 2f * s, btnPaint)
        canvas.drawRect(ax + 10f * s, doorCy - 6f * s, ax + 14f * s, doorCy + 6f * s, btnPaint)
        canvas.drawRect(ax + 6f * s, doorCy - 4f * s, ax + 10f * s, doorCy - 2f * s, btnPaint)
        canvas.drawRect(ax + 6f * s, doorCy + 2f * s, ax + 10f * s, doorCy + 4f * s, btnPaint)

        textPaint.textAlign = Paint.Align.LEFT
        val labelSize = min(28f * s, r.height() * 0.38f).coerceAtLeast(22f * s)
        pixText(canvas, "出门", r.left + 72f * s, r.centerY() + labelSize * 0.32f, labelSize, Color.WHITE, 1f, 1f)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHomeShopStrip(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        val portrait = h > w
        val stripTop = h * (if (portrait) 0.74f else 0.78f)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xCC1C2634.toInt()
        canvas.drawRect(0f, stripTop, w, h * 0.86f, btnPaint)

        if (game.isCatHomeTab()) {
            val tabGap = 8f * s
            val tabH = if (portrait) 40f * s else 34f * s
            val tabW = if (portrait) min(88f * s, (w * 0.92f - 3f * tabGap) / 4f) else 76f * s
            val tabY = stripTop + 12f * s
            for (i in CAT_COSMETIC_TABS.indices) {
                val cx = w / 2f + (i - 1.5f) * (tabW + tabGap)
                val tabIdx = i + Game.HOME_TAB_COLOR
                btnHomeTabs[i].set(cx - tabW / 2f, tabY, cx + tabW / 2f, tabY + tabH)
                btnPaint.style = Paint.Style.FILL
                btnPaint.color = if (game.homeTab == tabIdx) 0xEE3A5068.toInt() else 0xCC222C38.toInt()
                canvas.drawRect(btnHomeTabs[i], btnPaint)
                btnPaint.style = Paint.Style.STROKE
                btnPaint.strokeWidth = 1f
                btnPaint.color = if (game.homeTab == tabIdx) 0xFFFFD426.toInt() else 0xAAFFFFFF.toInt()
                canvas.drawRect(btnHomeTabs[i], btnPaint)
                btnPaint.style = Paint.Style.FILL
                pixText(
                    canvas, CAT_COSMETIC_TABS[i], cx, tabY + tabH / 2f + 7f * s, 22f * s,
                    if (game.homeTab == tabIdx) 0xFFFFD426.toInt() else Color.WHITE, 1f, 1f
                )
            }
        }

        val by = h * (if (portrait) 0.80f else 0.83f)
        val arrowHalf = if (portrait) 36f * s else 28f * s
        val arrowH = if (portrait) 28f * s else 22f * s
        btnHomeL.set(w * 0.16f - arrowHalf, by - arrowH, w * 0.16f + arrowHalf, by + arrowH)
        btnHomeR.set(w * 0.84f - arrowHalf, by - arrowH, w * 0.84f + arrowHalf, by + arrowH)
        drawBtn(canvas, btnHomeL, "<", s)
        drawBtn(canvas, btnHomeR, ">", s)
        val (name, price, status, action) = homeBrowseRow()
        val info = if (status.isEmpty()) "$name  价格 $price" else "$name  $status"
        val infoSize = fittedTextSize(info, 24f * s, w * 0.54f, 18f * s)
        pixText(canvas, info, w / 2f, by + 6f * s, infoSize, Color.WHITE, sdx, sdy)

        val buyH = if (portrait) 52f * s else 44f * s
        val buyW = if (portrait) min(150f * s, w * 0.34f) else 120f * s
        btnHomeBuy.set(w / 2f - buyW, h * 0.855f, w / 2f + buyW, h * 0.855f + buyH)
        drawBtn(canvas, btnHomeBuy, action, s)
    }

    private fun homeBrowseRow(): HomeRow = when (game.homeTab) {
        Game.HOME_TAB_HOUSE -> {
            val i = game.homeBrowseHouse
            HomeRow(
                Game.HOUSE_NAMES[i], Game.HOUSE_PRICES[i],
                when {
                    game.houseStyle == i -> "居住中"
                    game.ownsHouse(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsHouse(i)) "入住" else "购买"
            )
        }
        Game.HOME_TAB_ROOF -> {
            val i = game.homeBrowseRoof
            HomeRow(
                Game.ROOF_NAMES[i], Game.ROOF_PRICES[i],
                when {
                    game.roofStyle == i -> "使用中"
                    game.ownsRoof(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsRoof(i)) "换上" else "购买"
            )
        }
        Game.HOME_TAB_DECO -> {
            val i = game.homeBrowseDeco
            HomeRow(
                Game.DECO_NAMES[i], Game.DECO_PRICES[i],
                if (game.ownsDeco(i)) "已摆放" else "",
                if (game.ownsDeco(i)) "已摆放" else "购买"
            )
        }
        Game.HOME_TAB_COLOR -> {
            val i = game.shopBrowseColor
            HomeRow(
                Game.COLOR_NAMES[i], Game.COLOR_PRICES[i],
                when {
                    game.catColor == i -> "已装备"
                    game.ownsColor(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsColor(i)) "装备" else "购买"
            )
        }
        Game.HOME_TAB_TRAIL -> {
            val i = game.shopBrowseTrail
            HomeRow(
                Game.TRAIL_NAMES[i], Game.TRAIL_PRICES[i],
                when {
                    game.trailStyle == i -> "已装备"
                    game.ownsTrail(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsTrail(i)) "装备" else "购买"
            )
        }
        Game.HOME_TAB_SCARF -> {
            val i = game.shopBrowseScarf
            HomeRow(
                Game.SCARF_NAMES[i], Game.SCARF_PRICES[i],
                when {
                    game.scarfStyle == i -> "已戴上"
                    game.ownsScarf(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsScarf(i)) "戴上" else "购买"
            )
        }
        else -> {
            val i = game.shopBrowseHat
            HomeRow(
                Game.HAT_NAMES[i], Game.HAT_PRICES[i],
                when {
                    game.hatStyle == i -> "已戴上"
                    game.ownsHat(i) -> "已拥有"
                    else -> ""
                },
                if (game.ownsHat(i)) "戴上" else "购买"
            )
        }
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

    private data class HomeRow(val name: String, val price: Int, val status: String, val action: String)

    /** Canvas 像素画家场景；ghostDeco 为浏览中未购买装饰的半透明预览 */
    private fun drawHomeScene(
        canvas: Canvas, w: Float, h: Float, s: Float,
        house: Int, roof: Int, ghostDeco: Int,
        yardColor: Int = game.catColor,
        yardTrail: Int = game.trailStyle,
        yardScarf: Int = game.scarfStyle,
        yardHat: Int = game.hatStyle,
        cosmeticGhost: Boolean = false,
        highlightEditing: Boolean = false
    ) {
        val cx = w / 2f
        val top = 0f
        val gy = h * 0.58f
        val bottom = h
        val half = min(w * 0.49f, 420f * s)
        fun rc(l: Float, t: Float, r: Float, b: Float, color: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = color
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        // 全屏天空 / 草地（遮住 GL 层，独立页面）
        rc(0f, top, w, gy, 0xFF8ED4F2.toInt())
        rc(w - 90f * s, top + 24f * s, w - 50f * s, top + 64f * s, 0xFFFFD75E.toInt())
        rc(0f, gy, w, bottom, 0xFF6FBF56.toInt())
        rc(0f, gy, w, gy + 8f * s, 0xFF5CA847.toInt())

        val roofC = ROOF_CHIPS[roof]
        val roofD = darken(roofC)

        // 房屋主体（中心 cx，底部落在 gy）
        when (house) {
            0 -> { // 小木屋
                rc(cx - 80f * s, gy - 110f * s, cx + 80f * s, gy, 0xFFB07A45.toInt())
                for (i in 0..3) {
                    rc(cx - 80f * s, gy - 110f * s + i * 28f * s, cx + 80f * s, gy - 108f * s + i * 28f * s, 0xFF97622F.toInt())
                }
                pyramidRoof(canvas, cx, gy - 110f * s, 104f * s, 46f * s, roofC, roofD, s)
                door(canvas, cx + 34f * s, gy, s)
                window(canvas, cx - 40f * s, gy - 62f * s, s)
            }
            1 -> { // 砖瓦房
                rc(cx - 100f * s, gy - 122f * s, cx + 100f * s, gy, 0xFFC96A4A.toInt())
                for (r in 0..4) for (c in 0..5) {
                    val bx = cx - 100f * s + (c * 34f + if (r % 2 == 0) 0f else 17f) * s
                    rc(bx, gy - 122f * s + r * 25f * s, bx + 15f * s, gy - 120f * s + r * 25f * s, 0xFFB2543A.toInt())
                }
                pyramidRoof(canvas, cx, gy - 122f * s, 126f * s, 50f * s, roofC, roofD, s)
                door(canvas, cx - 52f * s, gy, s)
                window(canvas, cx + 14f * s, gy - 66f * s, s)
                window(canvas, cx + 58f * s, gy - 66f * s, s)
            }
            2 -> { // 双层小楼
                rc(cx - 100f * s, gy - 190f * s, cx + 100f * s, gy, 0xFFEBDDBB.toInt())
                rc(cx - 100f * s, gy - 100f * s, cx + 100f * s, gy - 92f * s, 0xFFC9B98F.toInt())
                pyramidRoof(canvas, cx, gy - 190f * s, 126f * s, 48f * s, roofC, roofD, s)
                door(canvas, cx, gy, s)
                window(canvas, cx - 64f * s, gy - 52f * s, s)
                window(canvas, cx + 64f * s, gy - 52f * s, s)
                window(canvas, cx - 64f * s, gy - 142f * s, s)
                window(canvas, cx + 64f * s, gy - 142f * s, s)
                // 阳台
                rc(cx - 30f * s, gy - 126f * s, cx + 30f * s, gy - 118f * s, 0xFF97622F.toInt())
            }
            else -> { // 梦幻城堡
                rc(cx - 85f * s, gy - 150f * s, cx + 85f * s, gy, 0xFFE3E6EF.toInt())
                rc(cx - 130f * s, gy - 205f * s, cx - 82f * s, gy, 0xFFD3D7E4.toInt())
                rc(cx + 82f * s, gy - 205f * s, cx + 130f * s, gy, 0xFFD3D7E4.toInt())
                pyramidRoof(canvas, cx - 106f * s, gy - 205f * s, 56f * s, 42f * s, roofC, roofD, s)
                pyramidRoof(canvas, cx + 106f * s, gy - 205f * s, 56f * s, 42f * s, roofC, roofD, s)
                pyramidRoof(canvas, cx, gy - 150f * s, 96f * s, 40f * s, roofC, roofD, s)
                // 旗帜
                rc(cx - 108f * s, gy - 268f * s, cx - 104f * s, gy - 247f * s, 0xFF97622F.toInt())
                rc(cx - 104f * s, gy - 266f * s, cx - 84f * s, gy - 256f * s, 0xFFF25A5A.toInt())
                rc(cx + 104f * s, gy - 268f * s, cx + 108f * s, gy - 247f * s, 0xFF97622F.toInt())
                rc(cx + 108f * s, gy - 266f * s, cx + 128f * s, gy - 256f * s, 0xFF5AA9F2.toInt())
                // 大门 + 窄窗
                rc(cx - 26f * s, gy - 64f * s, cx + 26f * s, gy, 0xFF6B4A2B.toInt())
                rc(cx - 18f * s, gy - 56f * s, cx + 18f * s, gy, 0xFF553A20.toInt())
                rc(cx - 60f * s, gy - 110f * s, cx - 48f * s, gy - 78f * s, 0xFF7A86A8.toInt())
                rc(cx + 48f * s, gy - 110f * s, cx + 60f * s, gy - 78f * s, 0xFF7A86A8.toInt())
            }
        }

        // 装饰：已购实心；浏览未购的半透明预览
        for (i in Game.DECO_NAMES.indices) {
            val owned = game.ownsDeco(i)
            if (!owned && i != ghostDeco) continue
            val alpha = if (owned) 255 else 110
            drawDeco(canvas, i, cx, gy, half, s, alpha)
        }

        drawCosmeticYardDeco(canvas, cx, gy, half, s, yardColor, yardTrail, yardScarf, yardHat, cosmeticGhost)

        // 可点击热区（装扮购置）
        hitSky.set(0f, top, w, gy - 130f * s)
        hitRoof.set(cx - 118f * s, top, cx + 118f * s, gy - 108f * s)
        hitHouse.set(cx - 108f * s, gy - 108f * s, cx + 108f * s, gy)
        hitYardDeco.set(cx - half, gy - 12f * s, cx + half, bottom)
        hitCosmeticColor.set(cx - 166f * s, gy - 20f * s, cx - 130f * s, gy + 32f * s)
        hitCosmeticTrail.set(cx - half + 20f * s, gy + 28f * s, cx - half + 210f * s, gy + 54f * s)
        hitCosmeticScarf.set(cx + 26f * s, gy - 72f * s, cx + 118f * s, gy + 10f * s)
        hitCosmeticHat.set(cx + 182f * s, gy - 90f * s, cx + 210f * s, gy + 4f * s)

        if (highlightEditing) {
            val pulse = if (kotlin.math.sin(homePhase * 4f) > 0f) 0x44FFD426 else 0x22FFD426
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 2f
            btnPaint.color = pulse
            val active = when (game.homeTab) {
                Game.HOME_TAB_HOUSE -> hitHouse
                Game.HOME_TAB_ROOF -> hitRoof
                Game.HOME_TAB_DECO -> hitYardDeco
                Game.HOME_TAB_COLOR -> hitCosmeticColor
                Game.HOME_TAB_TRAIL -> hitCosmeticTrail
                Game.HOME_TAB_SCARF -> hitCosmeticScarf
                Game.HOME_TAB_HAT -> hitCosmeticHat
                else -> null
            }
            active?.let { canvas.drawRect(it, btnPaint) }
            if (game.isCatHomeTab()) canvas.drawRect(yardCatHit, btnPaint)
            btnPaint.style = Paint.Style.FILL
        }

        // 庭院里的猫与偶尔飞过的蝴蝶
        yardSceneCx = cx
        yardSceneGy = gy
        yardSceneS = s
        yardSceneHalf = half
        yardSceneHit.set(0f, top, w, bottom)
        updateYardCat(0.016f, half / s)
        drawYardButterfly(canvas, cx, gy, s)
        drawYardCat(canvas, cx, gy, s, yardColor, yardScarf, yardHat)
        if (handMarkerLife > 0f) drawHandMarker(canvas, cx, gy, s)
        drawYardFloaters(canvas, s)
        if (game.canUseTelescope()) {
            val tx = cx - 176f * s
            yardTelescopeHit.set(tx - 24f * s, gy - 36f * s, tx + 42f * s, gy + 32f * s)
        } else {
            yardTelescopeHit.setEmpty()
        }
    }

    /** 装扮物品在庭院中的可视化：配色花盆 / 光迹小径 / 围巾晾绳 / 帽子稻草人 */
    private fun drawCosmeticYardDeco(
        canvas: Canvas, cx: Float, gy: Float, half: Float, s: Float,
        color: Int, trail: Int, scarf: Int, hat: Int, ghost: Boolean
    ) {
        val alpha = if (ghost) 110 else 255
        fun rc(l: Float, t: Float, r: Float, b: Float, c: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = withAlpha(c, alpha)
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        if (color > 0) {
            val pot = COLOR_CHIPS[color % COLOR_CHIPS.size]
            val fx = cx - 148f * s
            rc(fx - 14f * s, gy + 10f * s, fx + 14f * s, gy + 28f * s, 0xFF97622F.toInt())
            rc(fx - 10f * s, gy - 6f * s, fx + 10f * s, gy + 12f * s, pot)
            rc(fx - 5f * s, gy - 16f * s, fx + 5f * s, gy - 6f * s, darken(pot))
        }
        if (trail > 0) {
            val rainbow = intArrayOf(
                0xFFF25A5A.toInt(), 0xFFFFD75E.toInt(), 0xFF6FBF56.toInt(), 0xFF57B6E8.toInt()
            )
            for (i in 0..4) {
                val tx = cx - half + 36f * s + i * 34f * s
                val chip = if (trail == 3) rainbow[i % 4] else TRAIL_CHIPS[trail]
                rc(tx - 7f * s, gy + 36f * s, tx + 7f * s, gy + 48f * s, chip)
            }
        }
        if (scarf > 0) {
            val sx = cx + 72f * s
            val scC = SCARF_CHIPS[scarf]
            rc(sx - 44f * s, gy - 66f * s, sx - 40f * s, gy + 6f * s, 0xFF97622F.toInt())
            rc(sx + 40f * s, gy - 54f * s, sx + 44f * s, gy + 6f * s, 0xFF97622F.toInt())
            rc(sx - 42f * s, gy - 64f * s, sx + 42f * s, gy - 60f * s, 0xFFB9B9B9.toInt())
            val sway = kotlin.math.sin(homePhase * 1.2f) * 5f * s
            rc(sx - 18f * s + sway, gy - 60f * s, sx + 18f * s + sway, gy - 46f * s, scC)
        }
        if (hat > 0) {
            val hx = cx + 196f * s
            rc(hx - 3f * s, gy - 48f * s, hx + 3f * s, gy, 0xFF97622F.toInt())
            rc(hx - 10f * s, gy - 66f * s, hx + 10f * s, gy - 48f * s, 0xFFD8B98A.toInt())
            when (hat) {
                1 -> {
                    rc(hx - 14f * s, gy - 74f * s, hx + 14f * s, gy - 68f * s, HAT_CAP)
                    rc(hx - 8f * s, gy - 82f * s, hx + 8f * s, gy - 74f * s, HAT_CAP_DK)
                }
                2 -> {
                    rc(hx - 16f * s, gy - 78f * s, hx + 16f * s, gy - 70f * s, HAT_STRAW)
                    rc(hx - 16f * s, gy - 70f * s, hx + 16f * s, gy - 66f * s, HAT_STRAW_BAND)
                }
                else -> {
                    rc(hx - 12f * s, gy - 80f * s, hx + 12f * s, gy - 72f * s, HAT_GOLD)
                    rc(hx - 4f * s, gy - 86f * s, hx + 4f * s, gy - 80f * s, HAT_RUBY)
                }
            }
        }
    }

    private fun darken(c: Int): Int {
        val r = ((c shr 16 and 0xFF) * 0.72f).toInt()
        val g = ((c shr 8 and 0xFF) * 0.72f).toInt()
        val b = ((c and 0xFF) * 0.72f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)

    /** 阶梯金字塔屋顶：bottomY 为屋顶底边，halfW 为半宽 */
    private fun pyramidRoof(canvas: Canvas, cx: Float, bottomY: Float, halfW: Float, height: Float, c: Int, cd: Int, s: Float) {
        val steps = 4
        btnPaint.style = Paint.Style.FILL
        for (i in 0 until steps) {
            val t = i.toFloat() / steps
            val hw = halfW * (1f - t * 0.82f)
            btnPaint.color = if (i == 0) cd else c
            canvas.drawRect(
                cx - hw, bottomY - height * (i + 1) / steps,
                cx + hw, bottomY - height * i / steps, btnPaint
            )
        }
    }

    private fun door(canvas: Canvas, cx: Float, gy: Float, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFF6B4A2B.toInt()
        canvas.drawRect(cx - 17f * s, gy - 54f * s, cx + 17f * s, gy, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(cx + 7f * s, gy - 30f * s, cx + 12f * s, gy - 25f * s, btnPaint)
    }

    private fun window(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFFFF3B8.toInt()
        canvas.drawRect(cx - 15f * s, cy - 15f * s, cx + 15f * s, cy + 15f * s, btnPaint)
        btnPaint.color = 0xFF8A6B3F.toInt()
        canvas.drawRect(cx - 2f * s, cy - 15f * s, cx + 2f * s, cy + 15f * s, btnPaint)
        canvas.drawRect(cx - 15f * s, cy - 2f * s, cx + 15f * s, cy + 2f * s, btnPaint)
    }

    private fun drawDeco(canvas: Canvas, deco: Int, cx: Float, gy: Float, half: Float, s: Float, alpha: Int) {
        fun rc(l: Float, t: Float, r: Float, b: Float, color: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = withAlpha(color, alpha)
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        when (deco) {
            0 -> { // 花坛
                val fx = cx - 232f * s
                rc(fx - 40f * s, gy + 16f * s, fx + 40f * s, gy + 32f * s, 0xFF97622F.toInt())
                for (i in 0..2) {
                    val px = fx - 26f * s + i * 26f * s
                    rc(px - 2f * s, gy + 2f * s, px + 2f * s, gy + 16f * s, 0xFF4E9142.toInt())
                    val fc = intArrayOf(0xFFF25A5A.toInt(), 0xFFFFD75E.toInt(), 0xFFF5A8C1.toInt())[i]
                    rc(px - 6f * s, gy - 8f * s, px + 6f * s, gy + 4f * s, fc)
                }
            }
            1 -> { // 木栅栏
                var px = cx - half + 20f * s
                while (px < cx + half - 20f * s) {
                    rc(px - 3f * s, gy + 36f * s, px + 3f * s, gy + 64f * s, 0xFFD8B98A.toInt())
                    px += 34f * s
                }
                rc(cx - half + 12f * s, gy + 44f * s, cx + half - 12f * s, gy + 50f * s, 0xFFC9A570.toInt())
            }
            2 -> { // 信箱
                val mx = cx + 210f * s
                rc(mx - 3f * s, gy - 34f * s, mx + 3f * s, gy, 0xFF97622F.toInt())
                rc(mx - 16f * s, gy - 52f * s, mx + 16f * s, gy - 32f * s, 0xFFF25A5A.toInt())
                rc(mx + 12f * s, gy - 62f * s, mx + 16f * s, gy - 50f * s, 0xFFFFD75E.toInt())
            }
            3 -> { // 秋千（座位摇摆）
                val sx = cx - 280f * s
                rc(sx - 34f * s, gy - 84f * s, sx - 28f * s, gy, 0xFF97622F.toInt())
                rc(sx + 28f * s, gy - 84f * s, sx + 34f * s, gy, 0xFF97622F.toInt())
                rc(sx - 38f * s, gy - 90f * s, sx + 38f * s, gy - 82f * s, 0xFF7A4E22.toInt())
                val sway = kotlin.math.sin(homePhase * 1.6f) * 10f * s
                rc(sx - 14f * s + sway, gy - 82f * s, sx - 11f * s + sway * 1.2f, gy - 34f * s, 0xFFB9B9B9.toInt())
                rc(sx + 11f * s + sway, gy - 82f * s, sx + 14f * s + sway * 1.2f, gy - 34f * s, 0xFFB9B9B9.toInt())
                rc(sx - 18f * s + sway * 1.2f, gy - 34f * s, sx + 18f * s + sway * 1.2f, gy - 26f * s, 0xFFFFD75E.toInt())
            }
            4 -> { // 猫爬架
                val tx = cx + 158f * s
                rc(tx - 4f * s, gy - 96f * s, tx + 4f * s, gy, 0xFFC9A570.toInt())
                rc(tx - 30f * s, gy - 64f * s, tx + 10f * s, gy - 54f * s, 0xFF8594B3.toInt())
                rc(tx - 10f * s, gy - 102f * s, tx + 30f * s, gy - 92f * s, 0xFFF5A8C1.toInt())
                rc(tx + 12f * s, gy - 92f * s, tx + 20f * s, gy - 78f * s, 0xFFB9B9B9.toInt())
            }
            5 -> { // 小泳池
                rc(cx + 96f * s, gy + 24f * s, cx + 260f * s, gy + 72f * s, 0xFFE3E6EF.toInt())
                rc(cx + 104f * s, gy + 30f * s, cx + 252f * s, gy + 66f * s, 0xFF57B6E8.toInt())
                val rip = kotlin.math.sin(homePhase * 2.2f) * 6f * s
                rc(cx + 120f * s + rip, gy + 42f * s, cx + 168f * s + rip, gy + 46f * s, 0xFF9AD9F5.toInt())
                rc(cx + 180f * s - rip, gy + 54f * s, cx + 228f * s - rip, gy + 58f * s, 0xFF9AD9F5.toInt())
            }
            6 -> { // 望远镜
                val tx = cx - 176f * s
                rc(tx - 12f * s, gy - 4f * s, tx - 6f * s, gy + 28f * s, 0xFF6B6B77.toInt())
                rc(tx + 6f * s, gy - 4f * s, tx + 12f * s, gy + 28f * s, 0xFF6B6B77.toInt())
                canvas.save()
                canvas.rotate(-30f, tx, gy - 10f * s)
                btnPaint.color = withAlpha(0xFF3E4A66.toInt(), alpha)
                canvas.drawRect(tx - 8f * s, gy - 18f * s, tx + 34f * s, gy - 2f * s, btnPaint)
                btnPaint.color = withAlpha(0xFF57B6E8.toInt(), alpha)
                canvas.drawRect(tx + 30f * s, gy - 16f * s, tx + 34f * s, gy - 4f * s, btnPaint)
                canvas.restore()
            }
            else -> { // 彩旗：从屋顶拉向两侧
                val flags = intArrayOf(
                    0xFFF25A5A.toInt(), 0xFFFFD75E.toInt(), 0xFF6FBF56.toInt(),
                    0xFF57B6E8.toInt(), 0xFFC77DFF.toInt()
                )
                for (side in intArrayOf(-1, 1)) {
                    for (i in 0..4) {
                        val t = (i + 1) / 6f
                        val fx = cx + side * t * (half - 30f * s)
                        val fy = gy - 200f * s + t * t * 130f * s
                        rc(fx - 7f * s, fy, fx + 7f * s, fy + 16f * s, flags[i])
                    }
                }
            }
        }
    }

    // ---------- 庭院猫 ----------
    /**
     * 状态机：站立张望 → 走向目标（散步点或已购装饰）→ 互动 → 继续。
     * 互动：花坛嗅花 / 信箱抬头张望 / 秋千跟着荡 / 猫爬架两段跳上顶层蹲坐 / 跳进泳池游泳；
     * 拥有的装饰越多，行为越丰富。
     */
    private fun updateYardCat(dt: Float, bound: Float) {
        updateYardTouch(dt)
        catTimer -= dt
        yardEventTimer -= dt
        if (butterflyVisible) {
            butterflyLife -= dt
            butterflyX += butterflyDir * 18f * dt
            val butterflyBound = (bound - 40f).coerceAtLeast(30f)
            if (abs(butterflyX) >= butterflyBound) {
                butterflyX = butterflyX.coerceIn(-butterflyBound, butterflyBound)
                butterflyDir = -butterflyDir
            }
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
                val butterflyBound = (bound - 60f).coerceAtLeast(20f)
                butterflyVisible = true
                butterflyLife = 6f + catRnd.nextFloat() * 4f
                butterflyX = (catRnd.nextFloat() * 2f - 1f) * butterflyBound
                butterflyHeight = 48f + catRnd.nextFloat() * 48f
                butterflyDir = if (catRnd.nextBoolean()) 1f else -1f
                catDeco = -1
                catState = CAT_CHASE
            }
        }
        when (catState) {
            CAT_IDLE -> if (catTimer <= 0f) pickYardGoal(bound)
            CAT_WALK -> {
                val d = catTarget - catX
                catDir = if (d >= 0f) 1f else -1f
                val step = 60f * dt
                if (abs(d) <= step) {
                    catX = catTarget
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
                        catDir = if (catDeco == 0) -1f else 1f   // 面向装饰
                    } else {
                        catState = CAT_IDLE
                        catTimer = 1f + catRnd.nextFloat() * 2f
                    }
                } else {
                    catX += step * catDir
                }
            }
            CAT_PLAY -> {
                catStageT += dt
                if (catDeco == 4) {
                    // 猫爬架：跳下层 → 停留 → 跳顶层 → 蹲坐甩尾 → 跳下
                    val stageEnd = floatArrayOf(0.45f, 1.2f, 0.45f, 2.8f, 0.5f)
                    if (catStageT > stageEnd[catStage]) {
                        catStageT = 0f
                        catStage++
                        if (catStage > 4) endYardPlay()
                    }
                } else if (catDeco == 5) {
                    // 泳池：跃入 → 来回划水 → 爬回岸边；跃入瞬间偶有溅起金币
                    if (catStage == 0 && catStageT >= 0.5f) {
                        catStage = 1
                        if (catRnd.nextFloat() < 0.4f) {
                            val n = 1 + catRnd.nextInt(4)
                            showToast(game.grantYardCoins(n, "游泳溅起了 $n 枚金币！"))
                        }
                    }
                    if (catStageT > 6.5f) endYardPlay()
                } else if (catTimer <= 0f) {
                    endYardPlay()
                }
            }
            CAT_NAP -> if (catTimer <= 0f) {
                catState = CAT_IDLE
                catTimer = 0.5f
            }
            CAT_CHASE -> if (butterflyVisible) {
                val d = butterflyX - catX
                catDir = if (d >= 0f) 1f else -1f
                if (abs(d) > 16f) catX += 82f * dt * catDir
            }
            CAT_CHASE_HAND -> {
                val d = handTargetX - catX
                catDir = if (d >= 0f) 1f else -1f
                val step = 96f * dt
                if (abs(d) <= step) {
                    catX = handTargetX
                    catState = CAT_PET
                    petTimer = 1.4f
                    petCount = 2
                    game.yardPet()
                    showToast("追到手啦！")
                    spawnYardFloater(yardSceneCx + catX * yardSceneS, yardSceneGy - 44f * yardSceneS, YardFloater.HEART)
                } else {
                    catX += step * catDir
                }
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
    }

    private fun updateYardTouch(dt: Float) {
        strokeCoinCooldown = max(0f, strokeCoinCooldown - dt)
        handMarkerLife = max(0f, handMarkerLife - dt)
        var fi = yardFloaters.size - 1
        while (fi >= 0) {
            val f = yardFloaters[fi]
            f.life -= dt
            f.y -= 28f * dt * yardSceneS
            if (f.life <= 0f) yardFloaters.removeAt(fi)
            fi--
        }
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

    private fun screenToSceneX(screenX: Float): Float {
        val bound = yardSceneHalf / yardSceneS
        return ((screenX - yardSceneCx) / yardSceneS).coerceIn(-bound + 28f, bound - 28f)
    }

    /** 双击庭院：小猫追向手指落点 */
    private fun startChaseHand(screenX: Float) {
        removeCallbacks(yardSingleTapRunnable)
        handTargetX = screenToSceneX(screenX)
        handMarkerLife = 2.5f
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
            startChaseHand(x)
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
            yardCatHit.contains(x, y) -> petYardCat()
        }
    }

    /** 点蝴蝶：惊飞；猫在追时帮抓有金币 */
    private fun pokeButterfly() {
        if (!butterflyVisible) return
        val helpingChase = catState == CAT_CHASE
        butterflyVisible = false
        butterflySpawnTimer = 12f + catRnd.nextFloat() * 16f
        spawnYardFloater(
            yardSceneCx + butterflyX * yardSceneS,
            yardSceneGy - butterflyHeight * yardSceneS,
            YardFloater.HEART
        )
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
            spawnYardFloater(
                yardSceneCx + catX * yardSceneS + (catRnd.nextFloat() * 2f - 1f) * 12f * yardSceneS,
                yardSceneGy - 38f * yardSceneS,
                YardFloater.COIN
            )
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
        spawnYardFloater(yardSceneCx + catX * yardSceneS, yardSceneGy - 40f * yardSceneS, YardFloater.HEART)
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

    private fun pickYardGoal(bound: Float) {
        if (yardEventTimer <= 0f) {
            pendingYardEvent = YARD_EVENT_COINS
            catDeco = -1
            val eventRange = (bound - 70f).coerceAtLeast(20f)
            catTarget = (catRnd.nextFloat() * 2f - 1f) * eventRange
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
                catTarget = (catRnd.nextFloat() * 2f - 1f) * (bound - 60f)
                catState = CAT_WALK
            }
            else -> {
                catDeco = pick
                catTarget = when (pick) {
                    0 -> -196f    // 花坛旁
                    2 -> 178f     // 信箱旁
                    3 -> -280f    // 秋千下
                    4 -> 126f     // 爬架起跳点
                    else -> 82f   // 泳池左沿
                }
                // 窄屏时目标在场景外就改为散步
                if (abs(catTarget) > bound - 30f) {
                    catDeco = -1
                    catTarget = 0f
                }
                catState = CAT_WALK
            }
        }
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

    private fun drawYardButterfly(canvas: Canvas, cx: Float, gy: Float, s: Float) {
        if (!butterflyVisible) {
            butterflyHit.setEmpty()
            return
        }
        val x = cx + butterflyX * s
        val y = gy - butterflyHeight * s + kotlin.math.sin(homePhase * 4f) * 8f * s
        butterflyHit.set(x - 16f * s, y - 12f * s, x + 16f * s, y + 12f * s)
        val wing = if (kotlin.math.sin(homePhase * 12f) > 0f) 7f else 4f
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xFFFF8FCB.toInt()
        canvas.drawRect(x - wing * s, y - 5f * s, x - 1f * s, y + 3f * s, btnPaint)
        canvas.drawRect(x + 1f * s, y - 5f * s, x + wing * s, y + 3f * s, btnPaint)
        btnPaint.color = 0xFFFFD75E.toInt()
        canvas.drawRect(x - 1f * s, y - 3f * s, x + 1f * s, y + 5f * s, btnPaint)
    }

    private fun drawYardCat(
        canvas: Canvas, cx: Float, gy: Float, s: Float,
        colorIdx: Int = game.catColor, scarf: Int = game.scarfStyle, hat: Int = game.hatStyle
    ) {
        var x = cx + catX * s
        var footY = gy
        var pose = when (catState) {
            CAT_WALK, CAT_CHASE, CAT_CHASE_HAND -> POSE_WALK
            CAT_NAP -> POSE_NAP
            CAT_PET -> POSE_PET
            else -> POSE_STAND
        }
        if (catState == CAT_PLAY) {
            when (catDeco) {
                0 -> pose = POSE_SNIFF
                2 -> pose = POSE_LOOKUP
                5 -> { // 跳进泳池游泳：跃入 → 来回划水 → 爬回岸边
                    val edgeX = 82f
                    val left = 124f
                    val right = 226f
                    val surfaceY = gy + 40f * s
                    when {
                        catStageT < 0.5f -> {          // 从池边跃入水中
                            val p = (catStageT / 0.5f).coerceIn(0f, 1f)
                            x = cx + (edgeX + (left - edgeX) * p) * s
                            footY = gy + (surfaceY - gy) * p -
                                kotlin.math.sin(p * Math.PI.toFloat()) * 22f * s
                            pose = POSE_JUMP
                        }
                        catStageT < 6f -> {            // 在水里来回划水
                            val st = catStageT - 0.5f
                            val swim = kotlin.math.sin(st * 1.1f) * 0.5f + 0.5f
                            x = cx + (left + (right - left) * swim) * s
                            catDir = if (kotlin.math.cos(st * 1.1f) >= 0f) 1f else -1f
                            footY = surfaceY
                            pose = POSE_SWIM
                        }
                        else -> {                      // 爬回岸边
                            val p = ((catStageT - 6f) / 0.5f).coerceIn(0f, 1f)
                            val fromX = left + (right - left) *
                                (kotlin.math.sin(5.5f * 1.1f) * 0.5f + 0.5f)
                            x = cx + (fromX + (edgeX - fromX) * p) * s
                            footY = surfaceY + (gy - surfaceY) * p -
                                kotlin.math.sin(p * Math.PI.toFloat()) * 22f * s
                            pose = POSE_JUMP
                            catDir = -1f
                        }
                    }
                    catX = (x - cx) / s
                }
                3 -> { // 坐上秋千跟着荡
                    val sway = kotlin.math.sin(homePhase * 1.6f) * 10f * s
                    x = cx - 280f * s + sway * 1.2f
                    footY = gy - 34f * s
                    pose = POSE_SIT
                }
                4 -> { // 猫爬架：与 drawDeco 的平台位置对齐
                    val lowY = gy - 64f * s
                    val topY = gy - 102f * s
                    when (catStage) {
                        0 -> {
                            val t = (catStageT / 0.45f).coerceIn(0f, 1f)
                            x = cx + (126f + 22f * t) * s
                            footY = gy + (lowY - gy) * t -
                                kotlin.math.sin(t * Math.PI.toFloat()) * 18f * s
                            pose = POSE_JUMP
                        }
                        1 -> { x = cx + 148f * s; footY = lowY; pose = POSE_STAND }
                        2 -> {
                            val t = (catStageT / 0.45f).coerceIn(0f, 1f)
                            x = cx + (148f + 20f * t) * s
                            footY = lowY + (topY - lowY) * t -
                                kotlin.math.sin(t * Math.PI.toFloat()) * 16f * s
                            pose = POSE_JUMP
                        }
                        3 -> { x = cx + 168f * s; footY = topY; pose = POSE_SIT }
                        else -> {
                            val t = (catStageT / 0.5f).coerceIn(0f, 1f)
                            x = cx + (168f + 40f * t) * s
                            footY = topY + (gy - topY) * t * t
                            pose = POSE_JUMP
                        }
                    }
                    catX = (x - cx) / s   // 同步位置，动作衔接不瞬移
                }
            }
        }
        drawPixelCat(canvas, x, footY, s, catDir, pose, colorIdx, scarf, hat)
        if (catState == CAT_PET) drawPetHearts(canvas, x, footY, s)
        // 点击热区：覆盖当前姿势下的猫身
        val hitW = if (pose == POSE_NAP) 44f else 36f
        val hitH = if (pose == POSE_NAP) 28f else if (pose == POSE_PET) 52f else 46f
        val hitTop = if (pose == POSE_NAP) 18f else hitH
        yardCatHit.set(x - hitW * s, footY - hitTop * s, x + hitW * s, footY + 6f * s)
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

    /** 双击落点：像素小手标记 */
    private fun drawHandMarker(canvas: Canvas, cx: Float, gy: Float, s: Float) {
        val x = cx + handTargetX * s
        val y = gy - 22f * s
        val bob = kotlin.math.sin(homePhase * 8f) * 4f * s
        val a = (handMarkerLife / 2.5f * 220f).toInt().coerceIn(60, 220)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = withAlpha(0xFFFFE8A8.toInt(), a)
        canvas.drawRect(x - 10f * s, y + bob, x + 10f * s, y + 16f * s + bob, btnPaint)
        btnPaint.color = withAlpha(0xFFFFD075.toInt(), a)
        canvas.drawRect(x - 14f * s, y + 12f * s + bob, x - 6f * s, y + 20f * s + bob, btnPaint)
        canvas.drawRect(x - 2f * s, y + 10f * s + bob, x + 6f * s, y + 18f * s + bob, btnPaint)
        canvas.drawRect(x + 8f * s, y + 12f * s + bob, x + 16f * s, y + 20f * s + bob, btnPaint)
        canvas.drawRect(x + 4f * s, y + 18f * s + bob, x + 12f * s, y + 24f * s + bob, btnPaint)
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
        val top = h * 0.10f
        val left = w / 2f - pw / 2f
        val right = w / 2f + pw / 2f
        val bottom = h * 0.90f

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xF20C1220.toInt()
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2f
        btnPaint.color = 0xFF6B8CFF.toInt()
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.FILL

        val body = stargazeBody.coerceIn(0, game.telescopeLevel)
        val card = StargazeCards.card(body)
        val age = game.stargazeAgeMode
        val name = Game.TELESCOPE_BODY_NAMES[body]
        val readTag = if (game.stargazeCardRead(body)) "已读" else "新卡"
        val hook = StargazeCards.hook(card, age)
        val facts = StargazeCards.facts(card, age)
        val tonight = StargazeCards.tonight(card, age)

        // 顶栏：名称 + 年龄切换
        pixText(canvas, name, w / 2f, top + 34f * s, 34f * s, 0xFFB8CCFF.toInt(), sdx, sdy)
        val ageW = 72f * s
        val ageH = 32f * s
        btnStargazeAge.set(right - ageW - 14f * s, top + 14f * s, right - 14f * s, top + 14f * s + ageH)
        drawBtn(canvas, btnStargazeAge, game.stargazeAgeLabel(), s)
        pixText(canvas, "$readTag · ${game.stargazeReadCount()}/${game.telescopeLevel + 1}",
            w / 2f, top + 60f * s, 18f * s, 0xFF88AACC.toInt(), sdx, sdy)

        // 中央大镜筒（主视觉）
        val lensR = min(pw * 0.28f, 110f * s)
        val lensCx = w / 2f
        val lensCy = top + 60f * s + lensR + 18f * s
        btnPaint.color = 0xFF060818.toInt()
        canvas.drawCircle(lensCx, lensCy, lensR + 6f * s, btnPaint)
        canvas.save()
        canvas.clipRect(lensCx - lensR, lensCy - lensR, lensCx + lensR, lensCy + lensR)
        btnPaint.color = 0xFF0A1028.toInt()
        canvas.drawRect(lensCx - lensR, lensCy - lensR, lensCx + lensR, lensCy + lensR, btnPaint)
        drawStargazeStars(canvas, lensCx, lensCy, lensR, s, body)
        drawCelestialBody(canvas, lensCx, lensCy, lensR * 0.58f, s, body)
        canvas.restore()
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 3f * s
        btnPaint.color = 0xFF4A5A88.toInt()
        canvas.drawCircle(lensCx, lensCy, lensR, btnPaint)
        btnPaint.strokeWidth = 1.5f * s
        btnPaint.color = 0xFF2A3458.toInt()
        canvas.drawCircle(lensCx, lensCy, lensR + 6f * s, btnPaint)
        btnPaint.style = Paint.Style.FILL

        // 术语徽章
        val badgeY = lensCy + lensR + 28f * s
        pixText(canvas, card.term, w / 2f, badgeY, 26f * s, 0xFFFFD426.toInt(), sdx, sdy)

        // 一句钩子
        val hookSize = fittedTextSize(hook, 22f * s, pw * 0.86f, 16f * s)
        pixText(canvas, hook, w / 2f, badgeY + 30f * s, hookSize, 0xFFE8ECF4.toInt(), sdx, sdy)

        // 两条要点（并排小卡）
        val chipW = (pw - 48f * s) / 2f
        val chipH = 40f * s
        val chipTop = badgeY + 52f * s
        val chipY = chipTop + chipH * 0.62f
        for (i in 0 until minOf(2, facts.size)) {
            val cx = left + 16f * s + chipW * 0.5f + i * (chipW + 16f * s)
            val cl = cx - chipW * 0.5f
            val cr = cx + chipW * 0.5f
            btnPaint.color = 0xFF162038.toInt()
            canvas.drawRect(cl, chipTop, cr, chipTop + chipH, btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 1.5f * s
            btnPaint.color = 0xFF3A4A70.toInt()
            canvas.drawRect(cl, chipTop, cr, chipTop + chipH, btnPaint)
            btnPaint.style = Paint.Style.FILL
            val factSize = fittedTextSize(facts[i], 18f * s, chipW * 0.9f, 14f * s)
            pixText(canvas, facts[i], cx, chipY, factSize, 0xFFCCDDFF.toInt(), sdx, sdy)
        }

        // 今晚一看
        val tipTop = chipTop + chipH + 22f * s
        pixText(canvas, "今晚 · $tonight", w / 2f, tipTop,
            fittedTextSize("今晚 · $tonight", 20f * s, pw * 0.88f, 15f * s),
            0xFF9AD9A0.toInt(), sdx, sdy)

        // 左右切换（贴在镜筒两侧）
        val arrowHalf = 28f * s
        btnStargazeL.set(left + 10f * s, lensCy - arrowHalf, left + 10f * s + arrowHalf * 2f, lensCy + arrowHalf)
        btnStargazeR.set(right - 10f * s - arrowHalf * 2f, lensCy - arrowHalf, right - 10f * s, lensCy + arrowHalf)
        if (body > 0) drawBtn(canvas, btnStargazeL, "<", s)
        if (body < game.telescopeLevel) drawBtn(canvas, btnStargazeR, ">", s)

        // 升级 / 关闭
        val upH = 44f * s
        val upW = min(pw * 0.42f, 220f * s)
        val upLeft = w / 2f - upW - 8f * s
        btnStargazeUpgrade.set(upLeft, bottom - upH - 18f * s, upLeft + upW, bottom - 18f * s)
        if (game.telescopeCanUpgrade()) {
            drawBtn(canvas, btnStargazeUpgrade, "升级 (${game.telescopeUpgradePrice()})", s)
        } else {
            btnStargazeUpgrade.setEmpty()
        }
        val doneW = min(pw * 0.42f, 220f * s)
        val doneLeft = w / 2f + 8f * s
        btnStargazeDone.set(doneLeft, bottom - upH - 18f * s, doneLeft + doneW, bottom - 18f * s)
        drawBtn(canvas, btnStargazeDone,
            if (stargazeDoneHint.isNotEmpty()) "读完了，休息去" else "关闭", s)

        val foot = stargazeDoneHint.ifEmpty { "新卡每天 1 张 · 旧卡可随时复习" }
        pixText(canvas, foot, w / 2f, bottom - 72f * s, 16f * s, 0xFF888888.toInt(), sdx, sdy)
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
        if (body >= Game.TELESCOPE_MAX_LEVEL) {
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
            else -> { // 深空星云
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
        }
    }

    /** 参数化像素猫：footY 为脚底，dir=1 朝右 / -1 朝左（水平镜像）；外观默认取当前装备 */
    private fun drawPixelCat(
        canvas: Canvas, x: Float, footY: Float, s: Float, dir: Float, pose: Int,
        colorIdx: Int = game.catColor, scarf: Int = game.scarfStyle, hat: Int = game.hatStyle
    ) {
        val c = COLOR_CHIPS[colorIdx % COLOR_CHIPS.size]
        val cd = darken(c)
        val eye = 0xFF222222.toInt()
        val paw = 0xFFF2F2EE.toInt()
        fun rc(dxl: Float, dyt: Float, dxr: Float, dyb: Float, color: Int) {
            val x1 = x + dxl * dir * s
            val x2 = x + dxr * dir * s
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = color
            canvas.drawRect(min(x1, x2), footY + dyt * s, kotlin.math.max(x1, x2), footY + dyb * s, btnPaint)
        }
        // 帽子：hL 为头部左沿、hT 为头顶（猫身坐标）
        fun hatAt(hL: Float, hT: Float) {
            when (hat) {
                1 -> { // 红棒球帽
                    rc(hL + 1f, hT - 9f, hL + 23f, hT + 1f, HAT_CAP)
                    rc(hL + 20f, hT - 4f, hL + 34f, hT, HAT_CAP)
                    rc(hL + 10f, hT - 12f, hL + 14f, hT - 8f, HAT_CAP_DK)
                }
                2 -> { // 青草帽
                    rc(hL - 6f, hT - 4f, hL + 30f, hT, HAT_STRAW)
                    rc(hL + 4f, hT - 12f, hL + 20f, hT - 4f, HAT_STRAW)
                    rc(hL + 4f, hT - 6f, hL + 20f, hT - 3f, HAT_STRAW_BAND)
                }
                3 -> { // 金皇冠
                    rc(hL + 4f, hT - 8f, hL + 20f, hT, HAT_GOLD)
                    for (i in 0..2) {
                        rc(hL + 5f + i * 6f, hT - 13f, hL + 9f + i * 6f, hT - 8f, HAT_GOLD)
                    }
                    rc(hL + 10f, hT - 6f, hL + 14f, hT - 2f, HAT_RUBY)
                }
            }
        }
        val scC = SCARF_CHIPS[scarf % SCARF_CHIPS.size]
        val t = homePhase
        when (pose) {
            POSE_NAP -> {
                rc(-24f, -14f, 20f, 0f, c)                    // 趴平的身体
                rc(4f, -24f, 30f, -4f, c)                     // 头贴地
                rc(6f, -30f, 12f, -22f, cd)                   // 耳
                rc(20f, -30f, 26f, -22f, cd)
                rc(11f, -16f, 24f, -14f, cd)                  // 闭眼线
                rc(-34f, -10f, -22f, -4f, cd)                 // 尾巴收拢
                val fl = (t * 1.2f) % 1f                      // 飘起的 z
                textPaint.textAlign = Paint.Align.LEFT
                pixText(
                    canvas, "z", x + 34f * dir * s, footY - (30f + fl * 14f) * s, 20f * s,
                    withAlpha(0xFFFFFFFF.toInt(), (200 * (1f - fl)).toInt()), 1f, 1f
                )
                textPaint.textAlign = Paint.Align.CENTER
            }
            POSE_SIT -> {
                rc(-16f, -30f, 12f, 0f, c)                    // 竖起的身体
                rc(-4f, -50f, 22f, -26f, c)                   // 头
                rc(-2f, -56f, 4f, -48f, cd)                   // 耳
                rc(12f, -56f, 18f, -48f, cd)
                rc(2f, -40f, 5f, -37f, eye)
                rc(11f, -40f, 14f, -37f, eye)
                val tw = kotlin.math.sin(t * 4f) * 8f
                rc(-26f + tw, -10f, -14f, -4f, cd)            // 甩尾
                rc(-14f, -6f, -6f, 0f, paw)
                rc(2f, -6f, 10f, 0f, paw)
                if (scarf > 0) rc(-4f, -30f, 16f, -24f, scC)  // 颈圈
                if (hat > 0) hatAt(-4f, -50f)
            }
            POSE_PET -> {
                val lean = kotlin.math.sin(t * 5f) * 3f         // 被摸时微微蹭手
                rc(-16f + lean, -30f, 12f + lean, 0f, c)
                rc(-2f + lean, -50f, 24f + lean, -24f, c)       // 头略抬
                rc(0f + lean, -56f, 6f + lean, -48f, cd)
                rc(14f + lean, -56f, 20f + lean, -48f, cd)
                rc(4f + lean, -40f, 17f + lean, -38f, cd)       // 眯眼笑
                rc(0f + lean, -34f, 4f + lean, -30f, 0xFFF7A8B8.toInt()) // 腮红
                rc(13f + lean, -34f, 17f + lean, -30f, 0xFFF7A8B8.toInt())
                val tw = kotlin.math.sin(t * 11f) * 14f           // 尾巴快速摇摆
                rc(-28f + tw, -12f, -12f, -4f, cd)
                rc(-14f, -6f, -6f, 0f, paw)
                rc(2f, -6f, 10f, 0f, paw)
                if (scarf > 0) rc(-2f + lean, -28f, 18f + lean, -22f, scC)
                if (hat > 0) hatAt(-2f + lean, -50f)
            }
            POSE_SWIM -> {
                val paddle = kotlin.math.sin(t * 12f)
                val ripC = 0xFF9AD9F5.toInt()
                val splash = 0xFFCDEBFA.toInt()
                // 身体周围扩散的水面涟漪
                rc(-30f, -1f, -16f, 2f, ripC)
                rc(14f, -1f, 30f, 2f, ripC)
                // 露出水面的后背与翘起的尾巴尖
                rc(-16f, -6f, 8f, 2f, c)
                val tw = paddle * 4f
                rc(-24f + tw, -10f, -14f + tw, -4f, cd)
                // 抬出水面的头
                rc(6f, -22f, 28f, -4f, c)
                rc(8f, -28f, 14f, -22f, cd)               // 耳
                rc(22f, -28f, 28f, -22f, cd)
                rc(13f, -15f, 16f, -12f, eye)             // 眼
                rc(22f, -15f, 25f, -12f, eye)
                rc(27f, -12f, 30f, -9f, 0xFFF7A8B8.toInt()) // 鼻尖
                // 划水的前爪与溅起的水花
                val pawDy = -2f + paddle * 3f
                rc(2f, pawDy, 10f, pawDy + 5f, paw)
                if (paddle > 0.4f) {
                    rc(11f, -7f, 14f, -4f, splash)
                    rc(15f, -10f, 17f, -7f, splash)
                }
                if (scarf > 0) rc(6f, -9f, 24f, -3f, scC) // 贴水面的围巾
                if (hat > 0) hatAt(6f, -22f)
            }
            else -> {
                val walk = pose == POSE_WALK
                val jump = pose == POSE_JUMP
                val bob = when {
                    jump -> 0f
                    walk -> kotlin.math.abs(kotlin.math.sin(t * 9f)) * 3f
                    else -> kotlin.math.sin(t * 2f) * 1.5f
                }
                val headDy = when (pose) {
                    POSE_SNIFF -> 16f
                    POSE_LOOKUP -> -7f
                    else -> 0f
                }
                val headDx = if (pose == POSE_SNIFF) 6f else 0f
                rc(-20f, -22f - bob, 16f, 0f, c)              // 身体
                rc(6f + headDx, -40f - bob + headDy, 30f + headDx, -16f - bob + headDy, c)   // 头
                rc(8f + headDx, -46f - bob + headDy, 14f + headDx, -38f - bob + headDy, cd)  // 耳
                rc(22f + headDx, -46f - bob + headDy, 28f + headDx, -38f - bob + headDy, cd)
                rc(12f + headDx, -30f - bob + headDy, 15f + headDx, -27f - bob + headDy, eye)
                rc(21f + headDx, -30f - bob + headDy, 24f + headDx, -27f - bob + headDy, eye)
                val tailW = if (jump) 8f else kotlin.math.sin(t * 3f) * 6f
                val tailDy = if (jump) -8f else 0f            // 跳跃时尾巴上扬
                rc(-32f + tailW, -30f + tailDy, -18f, -24f + tailDy, cd)
                if (scarf > 0) {
                    rc(4f, -22f - bob, 22f, -15f - bob, scC)  // 颈圈
                    val fl = kotlin.math.sin(t * 3f) * 3f     // 身后小飘带
                    rc(-4f, -19f - bob + fl * 0.4f, 4f, -7f - bob + fl, scC)
                }
                if (hat > 0) hatAt(6f + headDx, -40f - bob + headDy)
                if (walk) {
                    val sw = kotlin.math.sin(t * 9f) * 5f     // 前后爪交替
                    rc(-18f + sw, -6f, -10f + sw, 0f, paw)
                    rc(4f - sw, -6f, 12f - sw, 0f, paw)
                } else if (jump) {
                    rc(-14f, -8f, -6f, -2f, paw)              // 收腿
                    rc(2f, -8f, 10f, -2f, paw)
                } else {
                    rc(-18f, -6f, -10f, 0f, paw)
                    rc(4f, -6f, 12f, 0f, paw)
                }
            }
        }
    }

    // ---------- 暂停 ----------
    private fun drawPauseButton(canvas: Canvas, s: Float) {
        val size = 100f * s
        btnPause.set(30f * s, hudTop + 30f * s, 30f * s + size, hudTop + 30f * s + size)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0x88222C38.toInt()
        canvas.drawRect(btnPause, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1f
        btnPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawRect(btnPause, btnPaint)
        btnPaint.style = Paint.Style.FILL
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
        title: String, subtitle: String, sdx: Float, sdy: Float
    ): OverlayFrame {
        dim(canvas, w, h)
        // 背景铺满；标题 / X / 列表落在 safe area 内
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = LIGHT_PANEL
        canvas.drawRect(0f, 0f, w, h, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2.5f * s
        btnPaint.color = LIGHT_PANEL_EDGE
        canvas.drawRect(1.5f * s, 1.5f * s, w - 1.5f * s, h - 1.5f * s, btnPaint)
        btnPaint.style = Paint.Style.FILL

        val left = safeL
        val top = safeT
        val right = w - safeR
        val bottom = h - safeB
        homeOverlayPanel.set(0f, 0f, w, h)

        val headerBottom = top + OVERLAY_HEADER_H * s
        btnPaint.color = LIGHT_DIVIDER
        canvas.drawRect(left + 16f * s, headerBottom - 1.5f * s, right - 16f * s, headerBottom, btnPaint)

        val closeSize = 44f * s
        btnCatalogClose.set(
            right - 14f * s - closeSize, top + 14f * s,
            right - 14f * s, top + 14f * s + closeSize
        )
        drawLightBtn(canvas, btnCatalogClose, "X", s)

        textPaint.textAlign = Paint.Align.CENTER
        val titleCx = (left + right) * 0.5f
        lightText(canvas, title, titleCx, top + 38f * s, 36f * s, LIGHT_TITLE)
        val subSize = fittedTextSize(subtitle, 20f * s, (right - left) * 0.72f, 14f * s)
        lightText(canvas, subtitle, titleCx, top + 72f * s, subSize, LIGHT_SUB)

        val contentTop = headerBottom + 10f * s
        val contentBottom = bottom - OVERLAY_FOOTER_H * s
        return OverlayFrame(left, top, right, bottom, right - left, contentTop, contentBottom)
    }

    /** 底栏：分页按钮与页码同一行居中 */
    private fun drawCatalogFooter(
        canvas: Canvas, frame: OverlayFrame, s: Float,
        page: Int, pages: Int, sdx: Float, sdy: Float,
        closeHint: String = "点 X 或返回退出"
    ) {
        val cx = (frame.left + frame.right) * 0.5f
        val btnW = 64f * s
        val btnH = 40f * s
        val pagerCy = frame.bottom - 52f * s
        val pagerTop = pagerCy - btnH * 0.5f
        val pagerBottom = pagerCy + btnH * 0.5f
        val gap = 56f * s
        val pageLabel = "${page + 1}/$pages"
        val pageSteps = ((22f * s) / FONT_PX).roundToInt().coerceAtLeast(1)
        textPaint.textSize = (pageSteps * FONT_PX).toFloat()
        val pageW = max(textPaint.measureText(pageLabel), 48f * s)
        val halfSpan = pageW * 0.5f + gap + btnW

        if (pages > 1) {
            btnMuseumPrev.set(cx - halfSpan, pagerTop, cx - halfSpan + btnW, pagerBottom)
            btnMuseumNext.set(cx + halfSpan - btnW, pagerTop, cx + halfSpan, pagerBottom)
            drawLightBtn(canvas, btnMuseumPrev, "<", s)
            drawLightBtn(canvas, btnMuseumNext, ">", s)
        } else {
            btnMuseumPrev.setEmpty()
            btnMuseumNext.setEmpty()
        }
        textPaint.textAlign = Paint.Align.CENTER
        lightText(canvas, pageLabel, cx, pagerCy + 8f * s, 22f * s, LIGHT_TEXT)
        lightText(canvas, closeHint, cx, frame.bottom - 18f * s, 16f * s, LIGHT_HINT)
    }

    private fun drawOverlayRowBg(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, s: Float
    ) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = LIGHT_ROW
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1.5f * s
        btnPaint.color = LIGHT_ROW_EDGE
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.FILL
    }

    private fun drawLightBtn(canvas: Canvas, r: RectF, label: String, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = LIGHT_BTN
        canvas.drawRect(r, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1.5f * s
        btnPaint.color = LIGHT_BTN_EDGE
        canvas.drawRect(r, btnPaint)
        btnPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        val labelSize = min(32f * s, r.height() * 0.45f).coerceAtLeast(24f * s)
        lightText(canvas, label, r.centerX(), r.centerY() + labelSize * 0.32f, labelSize, LIGHT_TEXT)
    }

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
            sdx, sdy
        )
        val pages = honorPages().coerceAtLeast(1)
        val page = honorPage.coerceIn(0, pages - 1)
        val start = page * HONOR_PER_PAGE
        val end = min(start + HONOR_PER_PAGE, Game.ACHIEVE_CATS)
        val rows = (end - start).coerceAtLeast(1)
        val rowGap = 6f * s
        val rowH = ((frame.contentBottom - frame.contentTop) - rowGap * (rows - 1)) / rows
        val rowLeft = frame.left + 16f * s
        val rowRight = frame.right - 16f * s
        val badgeW = 56f * s

        for (i in 0 until rows) {
            val c = start + i
            val rowTop = frame.contentTop + i * (rowH + rowGap)
            val rowBottom = rowTop + rowH
            drawOverlayRowBg(canvas, rowLeft, rowTop, rowRight, rowBottom, s)

            val lv = game.achieveLevels[c]
            val tierLabel = when (lv) {
                0 -> "—"
                1 -> "铜"
                2 -> "银"
                else -> "金"
            }
            val tierColor = when (lv) {
                0 -> LIGHT_LOCKED
                1 -> 0xFF8A5520.toInt()
                2 -> 0xFF5A6068.toInt()
                else -> 0xFF8A6410.toInt()
            }
            val badgeLeft = rowLeft + 10f * s
            val badgeTop = rowTop + (rowH - 28f * s) * 0.5f
            val badgeBottom = badgeTop + 28f * s
            btnPaint.color = LIGHT_BADGE
            canvas.drawRect(badgeLeft, badgeTop, badgeLeft + badgeW, badgeBottom, btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 1.5f * s
            btnPaint.color = tierColor
            canvas.drawRect(badgeLeft, badgeTop, badgeLeft + badgeW, badgeBottom, btnPaint)
            btnPaint.style = Paint.Style.FILL
            textPaint.textAlign = Paint.Align.CENTER
            lightText(
                canvas, tierLabel,
                badgeLeft + badgeW * 0.5f, badgeTop + 22f * s, 20f * s, tierColor
            )

            val textLeft = badgeLeft + badgeW + 14f * s
            val textMaxW = rowRight - textLeft - 12f * s
            val cur = game.achieveProgress(c)
            val nameLine = Game.ACHIEVE_NAMES[c]
            val detail = if (lv >= Game.ACHIEVE_TIERS_PER) {
                "金满级"
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
            sub, sdx, sdy
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
        val tile = min(cellW, cellH)
        val gridW = MUSEUM_COLS * tile + (MUSEUM_COLS - 1) * gap
        val gridH = MUSEUM_ROWS * tile + (MUSEUM_ROWS - 1) * gap
        val originX = frame.left + (frame.pw - gridW) * 0.5f
        val originY = frame.contentTop + (availH - gridH) * 0.5f
        val iconHalf = tile * 0.36f

        for (i in museumTileHits.indices) museumTileHits[i].setEmpty()
        for (i in 0 until MUSEUM_PER_PAGE) {
            val id = start + i
            if (id >= Game.RELIC_COUNT) break
            val col = i % MUSEUM_COLS
            val row = i / MUSEUM_COLS
            val left = originX + col * (tile + gap)
            val top = originY + row * (tile + gap)
            val right = left + tile
            val bottom = top + tile
            museumTileHits[i].set(left, top, right, bottom)

            val rarity = Game.RELIC_RARITY[id]
            val collected = game.relicCollected(id)
            val showArt = collected || previewAll
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = relicTileFill(rarity, showArt)
            canvas.drawRect(left, top, right, bottom, btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = if (showArt) 2.5f * s else 1.5f * s
            btnPaint.color = relicTileStroke(rarity, showArt)
            canvas.drawRect(left, top, right, bottom, btnPaint)
            btnPaint.style = Paint.Style.FILL

            RelicIcons.draw(
                canvas, btnPaint, id,
                (left + right) * 0.5f, (top + bottom) * 0.5f,
                iconHalf, showArt, lightSurface = true, withChrome = false
            )
            if (previewAll && !collected) {
                // 未收集预览：右下角小点，避免和已收集混淆
                btnPaint.color = 0xFF4DE8FF.toInt()
                val d = tile * 0.09f
                canvas.drawRect(right - d * 2.2f, bottom - d * 2.2f, right - d * 0.7f, bottom - d * 0.7f, btnPaint)
            }
        }

        drawCatalogFooter(
            canvas, frame, s, page, pages, sdx, sdy,
            closeHint = if (previewAll) "测试全览 · 点图标查看 · X 退出"
            else "点图标查看 · X 或返回退出"
        )

        if (museumDetailId in 0 until Game.RELIC_COUNT &&
            (game.relicCollected(museumDetailId) || previewAll)
        ) {
            drawRelicDetailOverlay(canvas, w, h, s, sdx, sdy, museumDetailId)
        }
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

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = LIGHT_PANEL
        canvas.drawRect(left, top, right, bottom, btnPaint)
        val rarity = Game.RELIC_RARITY[id]
        val frameColor = relicInkOnLight(rarity)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 3f * s
        btnPaint.color = frameColor
        canvas.drawRect(left, top, right, bottom, btnPaint)
        btnPaint.style = Paint.Style.FILL

        val closeSize = 44f * s
        btnCatalogClose.set(
            right - 12f * s - closeSize, top + 12f * s,
            right - 12f * s, top + 12f * s + closeSize
        )
        drawLightBtn(canvas, btnCatalogClose, "X", s)

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
            val mid = fact.length / 2
            var split = mid
            for (i in mid downTo (mid - 3).coerceAtLeast(1)) {
                split = i
                break
            }
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

        lightText(
            canvas, "点 X 或返回图鉴",
            cx, bottom - 36f * s, 20f * s, LIGHT_HINT
        )
    }

    private fun drawBtn(canvas: Canvas, r: RectF, label: String, s: Float) {
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xCC222C38.toInt()
        canvas.drawRect(r, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1f
        btnPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawRect(r, btnPaint)
        btnPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        val labelSize = min(32f * s, r.height() * 0.45f).coerceAtLeast(24f * s)
        pixText(canvas, label, r.centerX(), r.centerY() + labelSize * 0.32f, labelSize, Color.WHITE, 1f, 1f)
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
        textPaint.textAlign = Paint.Align.CENTER
        synchronized(game) {
            val n = game.relicHudCount.coerceAtMost(Game.RELIC_HUD_MAX)
            for (i in 0 until n) {
                val id = game.relicHudId[i]
                if (id !in 0 until Game.RELIC_COUNT) continue
                val x = game.relicHudX[i] * w
                val y = game.relicHudY[i] * h
                val scale = game.relicHudScale[i]
                val size = (24f * s * scale).coerceIn(14f * s, 34f * s)
                val iconHalf = (18f * s * scale).coerceIn(12f * s, 26f * s)
                RelicIcons.draw(canvas, btnPaint, id, x, y - size - iconHalf - 4f * s, iconHalf, true)
                val label = Game.RELIC_NAMES[id]
                val color = game.relicBannerColor(Game.RELIC_RARITY[id])
                // 深色描边，保证各背景上都可读
                pixText(canvas, label, x - 2f * s, y, size, 0xEE1A1028.toInt(), 0f, 0f)
                pixText(canvas, label, x + 2f * s, y, size, 0xEE1A1028.toInt(), 0f, 0f)
                pixText(canvas, label, x, y - 2f * s, size, 0xEE1A1028.toInt(), 0f, 0f)
                pixText(canvas, label, x, y + 2f * s, size, 0xEE1A1028.toInt(), 0f, 0f)
                pixText(canvas, label, x, y, size, color, sdx, sdy)
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
}
