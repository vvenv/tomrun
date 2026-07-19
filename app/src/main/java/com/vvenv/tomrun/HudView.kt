package com.vvenv.tomrun

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * 像素风中文 HUD：离屏低分辨率最近邻放大 + 手势识别。
 * 菜单含主界面 / 商店 / 成就面板。
 */
class HudView(context: Context, private val game: Game) : View(context) {

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
        isSubpixelText = false
        isLinearText = false
        isFakeBoldText = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dimPaint = Paint()
    private val btnPaint = Paint().apply {
        isAntiAlias = false
        isDither = false
    }
    private val pixPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }

    private var buf: Bitmap? = null
    private var bufCanvas: Canvas? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    private var downX = 0f
    private var downY = 0f
    private var consumed = false
    private var toast = ""
    private var toastLife = 0f

    // 离屏按钮区域
    private val btnShop = RectF()
    private val btnAchieve = RectF()
    private val btnBack = RectF()
    private val btnColorL = RectF()
    private val btnColorR = RectF()
    private val btnColorBuy = RectF()
    private val btnTrailL = RectF()
    private val btnTrailR = RectF()
    private val btnTrailBuy = RectF()

    companion object {
        private const val PIX = 1
        private val COLOR_CHIPS = intArrayOf(
            0xFF8594B3.toInt(), 0xFFF29E42.toInt(), 0xFF4D4D59.toInt(), 0xFFF5A8C1.toInt()
        )
        private val TRAIL_CHIPS = intArrayOf(
            0xFF888888.toInt(), 0xFF4DE8FF.toInt(), 0xFFFFD54A.toInt(), 0xFFFF66CC.toInt()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            buf = Bitmap.createBitmap(w / PIX, h / PIX, Bitmap.Config.ARGB_8888).also {
                it.density = Bitmap.DENSITY_NONE
            }
            bufCanvas = Canvas(buf!!)
        }
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
                if (!consumed) handleTap(event.x / PIX, event.y / PIX)
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
                when {
                    btnShop.contains(x, y) -> game.switchMenuPanel(Game.PANEL_SHOP)
                    btnAchieve.contains(x, y) -> game.switchMenuPanel(Game.PANEL_ACHIEVE)
                    else -> game.onTap()
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

    private fun showToast(msg: String) {
        if (msg.isEmpty()) return
        toast = msg
        toastLife = 1.8f
    }

    override fun onDraw(canvas: Canvas) {
        val b = buf ?: return
        b.eraseColor(Color.TRANSPARENT)
        if (toastLife > 0f) toastLife -= 0.016f
        drawHud(bufCanvas!!, b.width.toFloat(), b.height.toFloat())
        srcRect.set(0, 0, b.width, b.height)
        dstRect.set(0, 0, width, height)
        canvas.drawBitmap(b, srcRect, dstRect, pixPaint)
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

        // 左上角 buff
        textPaint.textAlign = Paint.Align.LEFT
        var buffY = 66f * s
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
                    else -> drawMainMenu(canvas, w, h, s, sdx, sdy)
                }
            }
            Game.State.RUNNING -> Unit
        }

        if (toastLife > 0f && toast.isNotEmpty()) {
            val alpha = (min(1f, toastLife / 0.4f) * 255).toInt()
            pixText(
                canvas, toast, w / 2f, h * 0.78f, 28f * s,
                (alpha shl 24) or 0x00FFFFFF, sdx, sdy
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
                pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.58f, 28f * s, Color.WHITE, sdx, sdy)
            }
        } else {
            pixText(canvas, "汤姆猫跑酷", w / 2f, h * 0.24f, 72f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "点击屏幕开始", w / 2f, h * 0.36f, 32f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "左右换道 · 上滑跳跃 · 下滑铲滑", w / 2f, h * 0.44f, 24f * s, Color.WHITE, sdx, sdy)
            pixText(canvas, "连击加分 · 局内任务 · 钱包买外观", w / 2f, h * 0.51f, 24f * s, Color.WHITE, sdx, sdy)
        }

        pixText(
            canvas, "成就 ${game.achieveCount}/15  ·  ${game.nextAchieveHint()}",
            w / 2f, h * 0.62f, 24f * s, 0xFFFFD426.toInt(), sdx, sdy
        )

        // 商店 / 成就 按钮
        val bw = 200f * s
        val bh = 52f * s
        val y = h * 0.72f
        btnShop.set(w / 2f - bw - 16f * s, y, w / 2f - 16f * s, y + bh)
        btnAchieve.set(w / 2f + 16f * s, y, w / 2f + bw + 16f * s, y + bh)
        drawBtn(canvas, btnShop, "商店", s)
        drawBtn(canvas, btnAchieve, "成就", s)

        // 当前装备提示
        pixText(
            canvas,
            "装备：${Game.COLOR_NAMES[game.catColor]} · ${Game.TRAIL_NAMES[game.trailStyle]}",
            w / 2f, h * 0.86f, 24f * s, 0xFFAAAAAA.toInt(), sdx, sdy
        )
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
        textPaint.textSize = size.toInt().coerceAtLeast(8).toFloat()
        val ix = x.toInt().toFloat()
        val iy = y.toInt().toFloat()
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
