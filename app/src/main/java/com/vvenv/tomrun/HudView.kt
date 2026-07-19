package com.vvenv.tomrun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 像素风中文 HUD：直接绘制 + 手势识别。
 * 菜单含主界面 / 商店 / 成就面板。
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

    // 离屏按钮区域
    private val btnShop = RectF()
    private val btnHome = RectF()
    private val btnAchieve = RectF()
    private val btnBack = RectF()
    private val btnColorL = RectF()
    private val btnColorR = RectF()
    private val btnColorBuy = RectF()
    private val btnTrailL = RectF()
    private val btnTrailR = RectF()
    private val btnTrailBuy = RectF()
    private val btnHomeTabs = arrayOf(RectF(), RectF(), RectF())
    private val btnHomeL = RectF()
    private val btnHomeR = RectF()
    private val btnHomeBuy = RectF()
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
        private val HOME_TAB_NAMES = arrayOf("房屋", "屋顶", "装饰")

        // 庭院猫状态与姿势
        private const val CAT_IDLE = 0
        private const val CAT_WALK = 1
        private const val CAT_PLAY = 2
        private const val CAT_NAP = 3
        private const val POSE_STAND = 0
        private const val POSE_WALK = 1
        private const val POSE_SIT = 2
        private const val POSE_NAP = 3
        private const val POSE_SNIFF = 4
        private const val POSE_LOOKUP = 5
        private const val POSE_JUMP = 6
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipeMin = 60f * (height / 720f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; consumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!consumed && game.state == Game.State.RUNNING) {
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
            }
            MotionEvent.ACTION_UP -> {
                if (!consumed) handleTap(event.x, event.y)
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        if (game.state == Game.State.RUNNING) {
            game.onTap()
            return
        }
        // 死亡冷却
        if (game.state == Game.State.DEAD && game.deadTime <= 0.6f) return

        when (game.menuPanel) {
            Game.PANEL_MAIN -> {
                if (handleSecretTitleTap(x, y)) return
                when {
                    btnShop.contains(x, y) -> game.switchMenuPanel(Game.PANEL_SHOP)
                    btnHome.contains(x, y) -> game.switchMenuPanel(Game.PANEL_HOME)
                    btnAchieve.contains(x, y) -> game.switchMenuPanel(Game.PANEL_ACHIEVE)
                    else -> game.onTap()
                }
            }
            Game.PANEL_HOME -> {
                when {
                    btnBack.contains(x, y) -> game.switchMenuPanel(Game.PANEL_MAIN)
                    btnHomeTabs[0].contains(x, y) -> game.switchHomeTab(Game.HOME_TAB_HOUSE)
                    btnHomeTabs[1].contains(x, y) -> game.switchHomeTab(Game.HOME_TAB_ROOF)
                    btnHomeTabs[2].contains(x, y) -> game.switchHomeTab(Game.HOME_TAB_DECO)
                    btnHomeL.contains(x, y) -> game.browseHome(-1)
                    btnHomeR.contains(x, y) -> game.browseHome(1)
                    btnHomeBuy.contains(x, y) -> showToast(game.buyOrEquipHome())
                }
            }
            Game.PANEL_SHOP -> {
                when {
                    btnBack.contains(x, y) -> game.switchMenuPanel(Game.PANEL_MAIN)
                    btnColorL.contains(x, y) -> game.browseColor(-1)
                    btnColorR.contains(x, y) -> game.browseColor(1)
                    btnColorBuy.contains(x, y) -> showToast(game.buyOrEquipColor())
                    btnTrailL.contains(x, y) -> game.browseTrail(-1)
                    btnTrailR.contains(x, y) -> game.browseTrail(1)
                    btnTrailBuy.contains(x, y) -> showToast(game.buyOrEquipTrail())
                }
            }
            Game.PANEL_ACHIEVE -> {
                if (btnBack.contains(x, y)) game.switchMenuPanel(Game.PANEL_MAIN)
            }
        }
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
            showToast(if (enabled) "测试模式：不死已开启" else "测试模式：不死已关闭")
        }
        return true
    }

    private fun showToast(msg: String) {
        if (msg.isEmpty()) return
        toast = msg
        toastLife = 1.8f
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (toastLife > 0f) toastLife -= 0.016f
        homePhase += 0.016f
        drawHud(canvas, width.toFloat(), height.toFloat())
        postInvalidateOnAnimation()
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f
        val sdx = 1f
        val sdy = 1f

        // 右上角
        textPaint.textAlign = Paint.Align.RIGHT
        pixText(canvas, "得分 ${game.score}", w - 36f * s, 66f * s, 42f * s, Color.WHITE, sdx, sdy)
        pixText(canvas, "金币 ${game.coins}", w - 36f * s, 106f * s, 28f * s, 0xFFFFD54A.toInt(), sdx, sdy)
        pixText(canvas, "最高 ${game.highScore}", w - 36f * s, 142f * s, 28f * s, Color.WHITE, sdx, sdy)
        pixText(canvas, "钱包 ${game.wallet}", w - 36f * s, 178f * s, 26f * s, 0xFFFFC21F.toInt(), sdx, sdy)
        if (game.state == Game.State.RUNNING && game.combo > 0) {
            val cColor = when {
                game.comboMult >= 5 -> 0xFFFF6B6B.toInt()
                game.comboMult >= 3 -> 0xFFFFC21F.toInt()
                else -> 0xFF7DEBA0.toInt()
            }
            val next = game.comboToNext()
            val line = if (next > 0) "连击 ${game.combo} x${game.comboMult}  差$next"
            else "连击 ${game.combo} x${game.comboMult} MAX"
            pixText(canvas, line, w - 36f * s, 214f * s, 26f * s, cColor, sdx, sdy)
        }

        // 顶部中央：当前宇宙
        if (game.state == Game.State.RUNNING && game.universe != Game.UNI_MEADOW) {
            pixText(
                canvas, "· ${Game.UNIVERSE_NAMES[game.universe]} ·", w / 2f, 66f * s, 26f * s,
                UNI_HUD[game.universe % UNI_HUD.size], sdx, sdy
            )
        }

        // 左上角 buff
        textPaint.textAlign = Paint.Align.LEFT
        var buffY = 66f * s
        if (game.state == Game.State.RUNNING && game.immortalMode) {
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

        // 局内任务
        if (game.state == Game.State.RUNNING) {
            textPaint.textAlign = Paint.Align.LEFT
            var qy = h - 36f * s
            for (i in game.quests.indices.reversed()) {
                val q = game.quests[i]
                val color = if (q.done) 0xFF7DEBA0.toInt() else 0xAAFFFFFF.toInt()
                val mark = if (q.done) "√" else "·"
                pixText(
                    canvas, "$mark ${q.label} ${q.progress}/${q.target}",
                    36f * s, qy, 22f * s, color, sdx, sdy
                )
                qy -= 28f * s
            }
        }

        textPaint.textAlign = Paint.Align.CENTER

        // 飘分
        if (game.state == Game.State.RUNNING && game.floatFlash > 0f && game.lastFloat.isNotEmpty()) {
            val alpha = (min(1f, game.floatFlash / 0.35f) * 255).toInt()
            val rise = (0.9f - game.floatFlash) * 30f * s
            pixText(
                canvas, game.lastFloat, w / 2f, h * 0.42f - rise, 36f * s,
                (alpha shl 24) or (game.lastFloatColor and 0x00FFFFFF), sdx, sdy
            )
        }

        // 连击升级
        if (game.state == Game.State.RUNNING && game.comboFlash > 0f) {
            val t = 1.4f - game.comboFlash
            val pop = 1f + 0.3f * (1f - min(1f, t * 5f))
            val alpha = (min(1f, game.comboFlash / 0.4f) * 255).toInt()
            pixText(
                canvas, "连击 x${game.comboMult}！", w / 2f, h * 0.30f, 52f * s * pop,
                (alpha shl 24) or 0x00FFC21F, sdx, sdy
            )
        }

        // 通用横幅队列（任务/成就）
        if (game.bannerFlash > 0f && game.bannerText.isNotEmpty()) {
            val alpha = (min(1f, game.bannerFlash / 0.45f) * 255).toInt()
            pixText(
                canvas, game.bannerText, w / 2f, h * 0.20f, 34f * s,
                (alpha shl 24) or (game.bannerColor and 0x00FFFFFF), sdx, sdy
            )
        }

        if (game.state == Game.State.RUNNING && game.recordFlash > 0f) {
            val t = 2.6f - game.recordFlash
            val pop = 1f + 0.35f * (1f - min(1f, t * 5f))
            val alpha = (min(1f, game.recordFlash / 0.5f) * 255).toInt()
            pixText(
                canvas, "新纪录！", w / 2f, h * 0.24f, 64f * s * pop,
                (alpha shl 24) or 0x00FFD426, sdx, sdy
            )
        }

        when (game.state) {
            Game.State.READY, Game.State.DEAD -> {
                dim(canvas, w, h)
                when (game.menuPanel) {
                    Game.PANEL_SHOP -> drawShop(canvas, w, h, s, sdx, sdy)
                    Game.PANEL_ACHIEVE -> drawAchieve(canvas, w, h, s, sdx, sdy)
                    Game.PANEL_HOME -> drawHome(canvas, w, h, s, sdx, sdy)
                    else -> drawMainMenu(canvas, w, h, s, sdx, sdy)
                }
            }
            Game.State.RUNNING -> Unit
        }

        // 穿越白闪
        if (game.portalFlash > 0f) {
            val a = (min(1f, game.portalFlash) * 200).toInt()
            dimPaint.color = (a shl 24) or 0x00FFFFFF
            canvas.drawRect(0f, 0f, w, h, dimPaint)
        }

        if (toastLife > 0f && toast.isNotEmpty()) {
            val fade = min(1f, toastLife / 0.4f)
            val appear = min(1f, (1.8f - toastLife) / 0.12f)
            val size = 28f * s
            val steps = (size / FONT_PX).roundToInt().coerceAtLeast(1)
            textPaint.textSize = (steps * FONT_PX).toFloat()
            val fm = textPaint.fontMetrics
            val halfW = textPaint.measureText(toast) / 2f + 22f * s
            val cx = (w / 2f).roundToInt().toFloat()
            // 出现时轻微上浮，落点在按钮区上方，避免与面板文字直接重叠
            val baseY = (h * 0.78f + (1f - appear) * 12f * s).roundToInt().toFloat()
            val top = baseY + fm.ascent - 12f * s
            val bottom = baseY + fm.descent + 12f * s
            // 深色底板 + 描边，遮住下层文字保证可读
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = withAlpha(0xFF1C2634.toInt(), (fade * 235).toInt())
            canvas.drawRect(cx - halfW, top, cx + halfW, bottom, btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 1f
            btnPaint.color = withAlpha(0xFFFFD426.toInt(), (fade * 255).toInt())
            canvas.drawRect(cx - halfW, top, cx + halfW, bottom, btnPaint)
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
            pixText(canvas, "游戏结束", w / 2f, h * 0.26f, 64f * s, Color.WHITE, sdx, sdy)
            val record = if (game.score >= game.highScore && game.score > 0) "  新纪录！" else ""
            pixText(canvas, "得分 ${game.score}$record", w / 2f, h * 0.36f, 36f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "本局金币 ${game.coins}  ·  钱包 +${game.runWalletEarn}", w / 2f, h * 0.44f, 28f * s, 0xFFFFD54A.toInt(), sdx, sdy)
            val qd = game.quests.count { it.done }
            pixText(canvas, "任务 $qd/3  ·  最高连击 ${game.bestComboRun}", w / 2f, h * 0.51f, 26f * s, 0xFF7DEBA0.toInt(), sdx, sdy)
            if (game.deadTime > 0.6f) {
                // 放到底部，避开 0.61h 起的成就/图鉴信息与按钮区
                pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.94f, 28f * s, Color.WHITE, sdx, sdy)
            }
        } else {
            pixText(canvas, "汤姆猫跑酷", w / 2f, h * 0.24f, 72f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "点击屏幕开始", w / 2f, h * 0.36f, 32f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "左右换道 · 上滑跳跃 · 下滑铲滑", w / 2f, h * 0.44f, 24f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "传送门穿越平行宇宙 · 金币装扮小屋", w / 2f, h * 0.51f, 24f * s, Color.WHITE, sdx, sdy)
        }

        pixText(
            canvas, "成就 ${game.achieveCount}/15  ·  ${game.nextAchieveHint()}",
            w / 2f, h * 0.61f, 24f * s, 0xFFFFD426.toInt(), sdx, sdy
        )
        pixText(
            canvas,
            "宇宙图鉴 ${game.universesSeen}/${Game.UNIVERSE_COUNT}  ·  累计穿越 ${game.totalPortals} 次",
            w / 2f, h * 0.665f, 24f * s, 0xFF4DE8FF.toInt(), sdx, sdy
        )

        // 商店 / 小屋 / 成就 按钮
        val bw = 148f * s
        val bh = 52f * s
        val gap = 14f * s
        val y = h * 0.73f
        btnShop.set(w / 2f - bw * 1.5f - gap, y, w / 2f - bw * 0.5f - gap, y + bh)
        btnHome.set(w / 2f - bw * 0.5f, y, w / 2f + bw * 0.5f, y + bh)
        btnAchieve.set(w / 2f + bw * 0.5f + gap, y, w / 2f + bw * 1.5f + gap, y + bh)
        drawBtn(canvas, btnShop, "商店", s)
        drawBtn(canvas, btnHome, "小屋", s)
        drawBtn(canvas, btnAchieve, "成就", s)

        // 当前装备提示
        pixText(
            canvas,
            "装备：${Game.COLOR_NAMES[game.catColor]} · ${Game.TRAIL_NAMES[game.trailStyle]}" +
                "  ·  小屋能量 Lv${game.homeLevel()}",
            w / 2f, h * 0.86f, 24f * s, 0xFFAAAAAA.toInt(), sdx, sdy
        )
    }

    // ---------- 小屋 ----------
    private fun drawHome(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        pixText(canvas, "汤姆的小屋", w / 2f, h * 0.075f, 48f * s, Color.WHITE, sdx, sdy)

        // 浏览预览：房屋 / 屋顶标签页直接预览浏览项
        val house = if (game.homeTab == Game.HOME_TAB_HOUSE) game.homeBrowseHouse else game.houseStyle
        val roof = if (game.homeTab == Game.HOME_TAB_ROOF) game.homeBrowseRoof else game.roofStyle
        val ghostDeco = if (game.homeTab == Game.HOME_TAB_DECO) game.homeBrowseDeco else -1
        drawHomeScene(canvas, w, h, s, house, roof, ghostDeco)

        // 标签页
        val tabW = 120f * s
        val tabH = 44f * s
        val tabY = h * 0.575f
        for (i in 0..2) {
            val cx = w / 2f + (i - 1) * (tabW + 12f * s)
            btnHomeTabs[i].set(cx - tabW / 2f, tabY, cx + tabW / 2f, tabY + tabH)
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = if (game.homeTab == i) 0xEE3A5068.toInt() else 0xCC222C38.toInt()
            canvas.drawRect(btnHomeTabs[i], btnPaint)
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 1f
            btnPaint.color = if (game.homeTab == i) 0xFFFFD426.toInt() else 0xAAFFFFFF.toInt()
            canvas.drawRect(btnHomeTabs[i], btnPaint)
            btnPaint.style = Paint.Style.FILL
            pixText(
                canvas, HOME_TAB_NAMES[i], cx, tabY + tabH / 2f + 8f * s, 24f * s,
                if (game.homeTab == i) 0xFFFFD426.toInt() else Color.WHITE, 1f, 1f
            )
        }

        // 浏览行
        val by = h * 0.68f
        btnHomeL.set(w * 0.18f - 30f * s, by - 24f * s, w * 0.18f + 30f * s, by + 24f * s)
        btnHomeR.set(w * 0.82f - 30f * s, by - 24f * s, w * 0.82f + 30f * s, by + 24f * s)
        drawBtn(canvas, btnHomeL, "<", s)
        drawBtn(canvas, btnHomeR, ">", s)
        val (name, price, status, action) = when (game.homeTab) {
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
            else -> {
                val i = game.homeBrowseDeco
                HomeRow(
                    Game.DECO_NAMES[i], Game.DECO_PRICES[i],
                    if (game.ownsDeco(i)) "已摆放" else "",
                    if (game.ownsDeco(i)) "已摆放" else "购买"
                )
            }
        }
        val info = if (status.isEmpty()) "$name  价格 $price" else "$name  $status"
        pixText(canvas, info, w / 2f, by + 8f * s, 26f * s, Color.WHITE, sdx, sdy)

        btnHomeBuy.set(w / 2f - 120f * s, h * 0.735f, w / 2f + 120f * s, h * 0.735f + 48f * s)
        drawBtn(canvas, btnHomeBuy, action, s)

        pixText(
            canvas, "小屋能量 Lv${game.homeLevel()} · ${game.homeLevelDesc()}",
            w / 2f, h * 0.855f, 24f * s, 0xFF7DEBA0.toInt(), sdx, sdy
        )

        btnBack.set(w / 2f - 100f * s, h * 0.92f - 26f * s, w / 2f + 100f * s, h * 0.92f + 26f * s)
        drawBtn(canvas, btnBack, "返回", s)
    }

    private data class HomeRow(val name: String, val price: Int, val status: String, val action: String)

    /** Canvas 像素画小屋场景；ghostDeco 为浏览中未购买装饰的半透明预览 */
    private fun drawHomeScene(canvas: Canvas, w: Float, h: Float, s: Float, house: Int, roof: Int, ghostDeco: Int) {
        val cx = w / 2f
        val top = h * 0.105f
        val gy = h * 0.42f            // 地面线
        val bottom = h * 0.545f
        val half = min(w * 0.46f, 330f * s)
        fun rc(l: Float, t: Float, r: Float, b: Float, color: Int) {
            btnPaint.style = Paint.Style.FILL
            btnPaint.color = color
            canvas.drawRect(l, t, r, b, btnPaint)
        }
        // 天空 / 太阳 / 草地
        rc(cx - half, top, cx + half, gy, 0xFF8ED4F2.toInt())
        rc(cx + half - 90f * s, top + 24f * s, cx + half - 50f * s, top + 64f * s, 0xFFFFD75E.toInt())
        rc(cx - half, gy, cx + half, bottom, 0xFF6FBF56.toInt())
        rc(cx - half, gy, cx + half, gy + 8f * s, 0xFF5CA847.toInt())

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

        // 庭院里的猫（当前配色，自由活动）
        updateYardCat(0.016f, half / s)
        drawYardCat(canvas, cx, gy, s)
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
     * 互动：花坛嗅花 / 信箱抬头张望 / 秋千跟着荡 / 猫爬架两段跳上顶层蹲坐 / 泳池边喝水；
     * 拥有的装饰越多，行为越丰富。
     */
    private fun updateYardCat(dt: Float, bound: Float) {
        catTimer -= dt
        when (catState) {
            CAT_IDLE -> if (catTimer <= 0f) pickYardGoal(bound)
            CAT_WALK -> {
                val d = catTarget - catX
                catDir = if (d >= 0f) 1f else -1f
                val step = 60f * dt
                if (abs(d) <= step) {
                    catX = catTarget
                    if (catDeco >= 0) {
                        catState = CAT_PLAY
                        catStage = 0
                        catStageT = 0f
                        catTimer = when (catDeco) {
                            3 -> 4.5f      // 秋千
                            4 -> 99f       // 爬架由分段控制
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
                } else if (catTimer <= 0f) {
                    endYardPlay()
                }
            }
            CAT_NAP -> if (catTimer <= 0f) {
                catState = CAT_IDLE
                catTimer = 0.5f
            }
        }
    }

    private fun endYardPlay() {
        catDeco = -1
        catState = CAT_IDLE
        catTimer = 0.8f + catRnd.nextFloat() * 1.5f
    }

    private fun pickYardGoal(bound: Float) {
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

    private fun drawYardCat(canvas: Canvas, cx: Float, gy: Float, s: Float) {
        var x = cx + catX * s
        var footY = gy
        var pose = when (catState) {
            CAT_WALK -> POSE_WALK
            CAT_NAP -> POSE_NAP
            else -> POSE_STAND
        }
        if (catState == CAT_PLAY) {
            when (catDeco) {
                0 -> pose = POSE_SNIFF
                2 -> pose = POSE_LOOKUP
                5 -> pose = if (kotlin.math.sin(homePhase * 1.4f) > 0f) POSE_SNIFF else POSE_STAND
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
        drawPixelCat(canvas, x, footY, s, catDir, pose)
    }

    /** 参数化像素猫：footY 为脚底，dir=1 朝右 / -1 朝左（水平镜像） */
    private fun drawPixelCat(canvas: Canvas, x: Float, footY: Float, s: Float, dir: Float, pose: Int) {
        val c = COLOR_CHIPS[game.catColor % COLOR_CHIPS.size]
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

    private fun drawShop(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        pixText(canvas, "外观商店", w / 2f, h * 0.16f, 48f * s, Color.WHITE, sdx, sdy)
        pixText(canvas, "钱包 ${game.wallet}", w / 2f, h * 0.24f, 30f * s, 0xFFFFC21F.toInt(), sdx, sdy)

        // 配色行
        val cy = h * 0.38f
        pixText(canvas, "猫咪配色", w / 2f, cy - 40f * s, 26f * s, Color.WHITE, sdx, sdy)
        val ci = game.shopBrowseColor
        btnColorL.set(w * 0.18f - 30f * s, cy - 24f * s, w * 0.18f + 30f * s, cy + 24f * s)
        btnColorR.set(w * 0.82f - 30f * s, cy - 24f * s, w * 0.82f + 30f * s, cy + 24f * s)
        drawBtn(canvas, btnColorL, "<", s)
        drawBtn(canvas, btnColorR, ">", s)
        btnPaint.style = Paint.Style.FILL
        btnPaint.color = COLOR_CHIPS[ci]
        canvas.drawRect(w / 2f - 22f * s, cy - 22f * s, w / 2f + 22f * s, cy + 22f * s, btnPaint)
        val cStatus = when {
            game.catColor == ci -> "已装备"
            game.ownsColor(ci) -> "已拥有 · 点击装备"
            else -> "价格 ${Game.COLOR_PRICES[ci]}"
        }
        pixText(canvas, "${Game.COLOR_NAMES[ci]}  $cStatus", w / 2f, cy + 48f * s, 24f * s, Color.WHITE, sdx, sdy)
        btnColorBuy.set(w / 2f - 120f * s, cy + 60f * s, w / 2f + 120f * s, cy + 108f * s)
        drawBtn(canvas, btnColorBuy, if (game.ownsColor(ci)) "装备配色" else "购买配色", s)

        // 尾迹行
        val ty = h * 0.68f
        pixText(canvas, "奔跑尾迹", w / 2f, ty - 40f * s, 26f * s, Color.WHITE, sdx, sdy)
        val ti = game.shopBrowseTrail
        btnTrailL.set(w * 0.18f - 30f * s, ty - 24f * s, w * 0.18f + 30f * s, ty + 24f * s)
        btnTrailR.set(w * 0.82f - 30f * s, ty - 24f * s, w * 0.82f + 30f * s, ty + 24f * s)
        drawBtn(canvas, btnTrailL, "<", s)
        drawBtn(canvas, btnTrailR, ">", s)
        btnPaint.color = TRAIL_CHIPS[ti]
        canvas.drawRect(w / 2f - 22f * s, ty - 22f * s, w / 2f + 22f * s, ty + 22f * s, btnPaint)
        val tStatus = when {
            game.trailStyle == ti -> "已装备"
            game.ownsTrail(ti) -> "已拥有 · 点击装备"
            else -> "价格 ${Game.TRAIL_PRICES[ti]}"
        }
        pixText(canvas, "${Game.TRAIL_NAMES[ti]}  $tStatus", w / 2f, ty + 48f * s, 24f * s, Color.WHITE, sdx, sdy)
        btnTrailBuy.set(w / 2f - 120f * s, ty + 60f * s, w / 2f + 120f * s, ty + 108f * s)
        drawBtn(canvas, btnTrailBuy, if (game.ownsTrail(ti)) "装备尾迹" else "购买尾迹", s)

        btnBack.set(w / 2f - 100f * s, h * 0.92f - 26f * s, w / 2f + 100f * s, h * 0.92f + 26f * s)
        drawBtn(canvas, btnBack, "返回", s)
    }

    private fun drawAchieve(canvas: Canvas, w: Float, h: Float, s: Float, sdx: Float, sdy: Float) {
        pixText(canvas, "成就 ${game.achieveCount}/15", w / 2f, h * 0.14f, 44f * s, 0xFFFFD426.toInt(), sdx, sdy)
        pixText(canvas, game.nextAchieveHint(), w / 2f, h * 0.21f, 24f * s, Color.WHITE, sdx, sdy)

        var y = h * 0.30f
        for (c in 0 until Game.ACHIEVE_CATS) {
            val lv = game.achieveLevels[c]
            val tierLabel = when (lv) {
                0 -> "未解锁"
                1 -> "铜"
                2 -> "银"
                else -> "金"
            }
            val cur = game.achieveProgress(c)
            val line = if (lv >= 3) {
                "${Game.ACHIEVE_NAMES[c]}  金满级"
            } else {
                val target = Game.ACHIEVE_TARGETS[c][lv]
                val reward = Game.ACHIEVE_REWARDS[lv]
                "${Game.ACHIEVE_NAMES[c]} ·$tierLabel  $cur/$target  奖$reward"
            }
            val color = when (lv) {
                0 -> 0xAAFFFFFF.toInt()
                1 -> 0xFFCD7F32.toInt()
                2 -> 0xFFC0C0C0.toInt()
                else -> 0xFFFFD426.toInt()
            }
            pixText(canvas, line, w / 2f, y, 24f * s, color, sdx, sdy)
            y += 52f * s
        }

        btnBack.set(w / 2f - 100f * s, h * 0.90f - 26f * s, w / 2f + 100f * s, h * 0.90f + 26f * s)
        drawBtn(canvas, btnBack, "返回", s)
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
        pixText(canvas, label, r.centerX(), r.centerY() + 8f * s, 24f * s, Color.WHITE, 1f, 1f)
    }

    private fun pixText(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int,
        shadowDx: Float, shadowDy: Float
    ) {
        val steps = (size / FONT_PX).roundToInt().coerceAtLeast(1)
        textPaint.textSize = (steps * FONT_PX).toFloat()
        val ix = x.roundToInt().toFloat()
        val iy = y.roundToInt().toFloat()
        textPaint.color = 0x88000000.toInt()
        canvas.drawText(text, ix + shadowDx, iy + shadowDy, textPaint)
        textPaint.color = color
        canvas.drawText(text, ix, iy, textPaint)
    }

    private fun dim(canvas: Canvas, w: Float, h: Float) {
        dimPaint.color = 0x66000000
        canvas.drawRect(0f, 0f, w, h, dimPaint)
    }
}
