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
 * 像素风中文 HUD：所有内容先画进 1/PIX 分辨率的离屏位图，
 * 再用最近邻放大回屏幕——文字（含中文）自动变成像素字。
 * 同时负责滑动手势识别。
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

    private val colorBtn = RectF()

    companion object {
        private const val PIX = 3
        private val COLOR_NAMES = arrayOf("蓝灰", "橘黄", "乌黑", "粉红", "奶白", "青绿", "紫罗兰", "棕褐")
        private val COLOR_CHIPS = intArrayOf(
            0xFF8594B3.toInt(), 0xFFF29E42.toInt(), 0xFF4D4D59.toInt(), 0xFFF5A8C1.toInt(),
            0xFFEDE5D1.toInt(), 0xFF59B8A6.toInt(), 0xFF9E80D1.toInt(), 0xFF9E704D.toInt()
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
                if (!consumed) {
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
                if (!consumed) {
                    val onBtn = game.state != Game.State.RUNNING &&
                        colorBtn.contains(event.x / PIX, event.y / PIX)
                    if (onBtn) game.cycleCatColor() else game.onTap()
                }
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val b = buf ?: return
        b.eraseColor(Color.TRANSPARENT)
        drawHud(bufCanvas!!, b.width.toFloat(), b.height.toFloat())
        srcRect.set(0, 0, b.width, b.height)
        dstRect.set(0, 0, width, height)
        canvas.drawBitmap(b, srcRect, dstRect, pixPaint)
        postInvalidateOnAnimation()
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f
        val shadowDx = 1f
        val shadowDy = 1f

        // 右上角：得分 / 金币 / 最高 / 连击
        textPaint.textAlign = Paint.Align.RIGHT
        pixText(canvas, "得分 ${game.score}", w - 36f * s, 66f * s, 42f * s, Color.WHITE, shadowDx, shadowDy)
        pixText(canvas, "金币 ${game.coins}", w - 36f * s, 106f * s, 28f * s, 0xFFFFD54A.toInt(), shadowDx, shadowDy)
        pixText(canvas, "最高 ${game.highScore}", w - 36f * s, 142f * s, 28f * s, Color.WHITE, shadowDx, shadowDy)
        if (game.state == Game.State.RUNNING && game.combo > 0) {
            val cColor = when {
                game.comboMult >= 5 -> 0xFFFF6B6B.toInt()
                game.comboMult >= 3 -> 0xFFFFC21F.toInt()
                else -> 0xFF7DEBA0.toInt()
            }
            pixText(
                canvas, "连击 ${game.combo}  x${game.comboMult}",
                w - 36f * s, 182f * s, 30f * s, cColor, shadowDx, shadowDy
            )
        }

        // 左上角：道具 / 滑索 / 天气 / 昼夜
        textPaint.textAlign = Paint.Align.LEFT
        var buffY = 66f * s
        if (game.helmetLayers > 0) {
            val hl = if (game.helmetLayers >= 2) "头盔 x2" else "头盔"
            pixText(canvas, hl, 36f * s, buffY, 30f * s, 0xFFFFC21F.toInt(), shadowDx, shadowDy)
            buffY += 40f * s
        }
        if (game.magnetTime > 0f) {
            pixText(canvas, "磁铁 ${game.magnetLeft()}s", 36f * s, buffY, 30f * s, 0xFFFF6B6B.toInt(), shadowDx, shadowDy)
            buffY += 40f * s
        }
        if (game.doubleTime > 0f) {
            pixText(canvas, "加倍 ${game.doubleLeft()}s", 36f * s, buffY, 30f * s, 0xFFC77DFF.toInt(), shadowDx, shadowDy)
            buffY += 40f * s
        }
        if (game.boostTime > 0f) {
            pixText(canvas, "冲刺 ${game.boostLeft()}s", 36f * s, buffY, 30f * s, 0xFF4DE8FF.toInt(), shadowDx, shadowDy)
            buffY += 40f * s
        }
        if (game.riding != null) {
            pixText(canvas, "滑索中", 36f * s, buffY, 30f * s, 0xFF7DEBA0.toInt(), shadowDx, shadowDy)
            buffY += 40f * s
        }
        val wRain = weatherWeight(Game.W_RAIN)
        val wSnow = weatherWeight(Game.W_SNOW)
        when {
            wRain > 0.5f -> {
                pixText(canvas, "雨", 36f * s, buffY, 28f * s, 0xFF9BB8E8.toInt(), shadowDx, shadowDy)
                buffY += 36f * s
            }
            wSnow > 0.5f -> {
                pixText(canvas, "雪", 36f * s, buffY, 28f * s, 0xFFE8F2FF.toInt(), shadowDx, shadowDy)
                buffY += 36f * s
            }
        }
        val night = game.nightAmount()
        val dusk = game.duskAmount()
        when {
            night > 0.55f -> pixText(canvas, "夜晚", 36f * s, buffY, 28f * s, 0xFF9AA8D0.toInt(), shadowDx, shadowDy)
            dusk > 0.4f -> pixText(canvas, "黄昏", 36f * s, buffY, 28f * s, 0xFFFFAA66.toInt(), shadowDx, shadowDy)
        }

        // 局内任务进度（左下）
        if (game.state == Game.State.RUNNING) {
            textPaint.textAlign = Paint.Align.LEFT
            var qy = h - 40f * s
            qy = drawQuestLine(
                canvas,
                "金币 ${min(game.coins, Game.QUEST_COINS)}/${Game.QUEST_COINS}",
                game.questCoinsDone, 36f * s, qy, s, shadowDx, shadowDy
            )
            qy = drawQuestLine(
                canvas,
                "连击 ${min(game.bestComboRun, Game.QUEST_COMBO)}/${Game.QUEST_COMBO}",
                game.questComboDone, 36f * s, qy, s, shadowDx, shadowDy
            )
            drawQuestLine(
                canvas,
                "距离 ${min(game.distance.toInt(), Game.QUEST_DIST)}/${Game.QUEST_DIST}",
                game.questDistDone, 36f * s, qy, s, shadowDx, shadowDy
            )
        }

        textPaint.textAlign = Paint.Align.CENTER

        // 飘分（屏幕中部偏上）
        if (game.state == Game.State.RUNNING && game.floatFlash > 0f && game.lastFloat.isNotEmpty()) {
            val alpha = (min(1f, game.floatFlash / 0.35f) * 255).toInt()
            val rise = (0.9f - game.floatFlash) * 30f * s
            pixText(
                canvas, game.lastFloat, w / 2f, h * 0.42f - rise, 36f * s,
                (alpha shl 24) or (game.lastFloatColor and 0x00FFFFFF), shadowDx, shadowDy
            )
        }

        // 连击升级横幅
        if (game.state == Game.State.RUNNING && game.comboFlash > 0f) {
            val t = 1.4f - game.comboFlash
            val pop = 1f + 0.3f * (1f - min(1f, t * 5f))
            val alpha = (min(1f, game.comboFlash / 0.4f) * 255).toInt()
            pixText(
                canvas, "连击 x${game.comboMult}！", w / 2f, h * 0.30f, 52f * s * pop,
                (alpha shl 24) or 0x00FFC21F, shadowDx, shadowDy
            )
        }

        // 任务完成横幅
        if (game.questFlash > 0f && game.questFlashText.isNotEmpty()) {
            val alpha = (min(1f, game.questFlash / 0.5f) * 255).toInt()
            pixText(
                canvas, game.questFlashText, w / 2f, h * 0.22f, 34f * s,
                (alpha shl 24) or 0x007DEBA0, shadowDx, shadowDy
            )
        }

        // 成就解锁横幅
        if (game.achieveFlash > 0f && game.achieveFlashText.isNotEmpty()) {
            val alpha = (min(1f, game.achieveFlash / 0.5f) * 255).toInt()
            pixText(
                canvas, game.achieveFlashText, w / 2f, h * 0.18f, 36f * s,
                (alpha shl 24) or 0x00FFD426, shadowDx, shadowDy
            )
        }

        // 破纪录
        if (game.state == Game.State.RUNNING && game.recordFlash > 0f) {
            val t = 2.6f - game.recordFlash
            val pop = 1f + 0.35f * (1f - min(1f, t * 5f))
            val alpha = (min(1f, game.recordFlash / 0.5f) * 255).toInt()
            pixText(
                canvas, "新纪录！", w / 2f, h * 0.24f, 64f * s * pop,
                (alpha shl 24) or 0x00FFD426, shadowDx, shadowDy
            )
        }

        when (game.state) {
            Game.State.READY -> {
                dim(canvas, w, h)
                pixText(canvas, "汤姆猫跑酷", w / 2f, h * 0.28f, 78f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "点击屏幕开始", w / 2f, h * 0.42f, 34f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "左右滑动·换道    上滑·跳跃    下滑·铲滑", w / 2f, h * 0.52f, 26f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "道具：磁铁 · 头盔 · 加倍 · 闪电冲刺", w / 2f, h * 0.59f, 26f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "连击吃金币加分倍率 · 完成局内任务拿奖励", w / 2f, h * 0.66f, 26f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(
                    canvas, "成就 ${game.achieveCount}/4  ·  累计金币 ${game.totalCoins}",
                    w / 2f, h * 0.73f, 26f * s, 0xFFFFD54A.toInt(), shadowDx, shadowDy
                )
                drawColorButton(canvas, w, h)
            }
            Game.State.DEAD -> {
                dim(canvas, w, h)
                pixText(canvas, "游戏结束", w / 2f, h * 0.28f, 70f * s, Color.WHITE, shadowDx, shadowDy)
                val record = if (game.score >= game.highScore && game.score > 0) "  新纪录！" else ""
                pixText(canvas, "得分 ${game.score}$record", w / 2f, h * 0.40f, 38f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "金币 ${game.coins}", w / 2f, h * 0.48f, 30f * s, 0xFFFFD54A.toInt(), shadowDx, shadowDy)
                val qd = (if (game.questCoinsDone) 1 else 0) +
                    (if (game.questComboDone) 1 else 0) +
                    (if (game.questDistDone) 1 else 0)
                pixText(canvas, "本局任务 $qd/3  ·  最高连击 ${game.bestComboRun}", w / 2f, h * 0.56f, 28f * s, 0xFF7DEBA0.toInt(), shadowDx, shadowDy)
                pixText(
                    canvas, "成就 ${game.achieveCount}/4",
                    w / 2f, h * 0.63f, 28f * s, 0xFFFFD426.toInt(), shadowDx, shadowDy
                )
                if (game.deadTime > 0.6f) {
                    pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.72f, 30f * s, Color.WHITE, shadowDx, shadowDy)
                }
                drawColorButton(canvas, w, h)
            }
            Game.State.RUNNING -> Unit
        }
    }

    private fun drawQuestLine(
        canvas: Canvas, text: String, done: Boolean,
        x: Float, y: Float, s: Float, sdx: Float, sdy: Float
    ): Float {
        val color = if (done) 0xFF7DEBA0.toInt() else 0xAAFFFFFF.toInt()
        val label = if (done) "√ $text" else "· $text"
        pixText(canvas, label, x, y, 22f * s, color, sdx, sdy)
        return y - 28f * s
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

    private fun drawColorButton(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f
        val bw = 320f * s
        val bh = 62f * s
        colorBtn.set(
            (w / 2f - bw / 2f).toInt().toFloat(),
            (h * 0.855f).toInt().toFloat(),
            (w / 2f + bw / 2f).toInt().toFloat(),
            (h * 0.855f + bh).toInt().toFloat()
        )

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xCC222C38.toInt()
        canvas.drawRect(colorBtn, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 1f
        btnPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawRect(colorBtn, btnPaint)
        btnPaint.style = Paint.Style.FILL

        val idx = game.catColor % COLOR_NAMES.size
        btnPaint.color = COLOR_CHIPS[idx]
        val cs = (15f * s).toInt().coerceAtLeast(2).toFloat()
        val cx = (colorBtn.left + 30f * s).toInt().toFloat()
        canvas.drawRect(
            cx, colorBtn.centerY() - cs,
            cx + cs * 2f, colorBtn.centerY() + cs, btnPaint
        )

        textPaint.textAlign = Paint.Align.CENTER
        pixText(
            canvas,
            "猫咪颜色：${COLOR_NAMES[idx]}",
            colorBtn.centerX() + 14f * s, colorBtn.centerY() + 10f * s, 27f * s,
            Color.WHITE, 1f, 1f
        )
    }

    private fun weatherWeight(type: Int): Float {
        var w = 0f
        if (game.weather == type) w += game.weatherBlend
        if (game.weatherPrev == type) w += 1f - game.weatherBlend
        return w
    }

    private fun dim(canvas: Canvas, w: Float, h: Float) {
        dimPaint.color = 0x66000000
        canvas.drawRect(0f, 0f, w, h, dimPaint)
    }
}
