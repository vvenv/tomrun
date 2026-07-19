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

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val dimPaint = Paint()
    private val btnPaint = Paint()
    private val pixPaint = Paint().apply { isFilterBitmap = false }

    private var buf: Bitmap? = null
    private var bufCanvas: Canvas? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    private var downX = 0f
    private var downY = 0f
    private var consumed = false

    private val colorBtn = RectF()   // 离屏坐标系

    companion object {
        private const val PIX = 3    // 像素化倍率
        private val COLOR_NAMES = arrayOf("蓝灰", "橘黄", "乌黑", "粉红")
        private val COLOR_CHIPS = intArrayOf(
            0xFF8594B3.toInt(), 0xFFF29E42.toInt(), 0xFF4D4D59.toInt(), 0xFFF5A8C1.toInt()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            buf = Bitmap.createBitmap(w / PIX, h / PIX, Bitmap.Config.ARGB_8888)
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

    // ---------- 全部 HUD 内容（离屏低分辨率坐标系） ----------
    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f

        textPaint.setShadowLayer(3f * s, 0f, 1.5f * s, 0x88000000.toInt())

        // 右上角：得分 / 金币 / 最高
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 42f * s
        textPaint.color = Color.WHITE
        canvas.drawText("得分 ${game.score}", w - 36f * s, 66f * s, textPaint)
        textPaint.textSize = 28f * s
        textPaint.color = 0xFFFFD54A.toInt()
        canvas.drawText("金币 ${game.coins}", w - 36f * s, 106f * s, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("最高 ${game.highScore}", w - 36f * s, 142f * s, textPaint)

        // 左上角：生效中的道具 / 滑索 / 天气
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 30f * s
        var buffY = 66f * s
        if (game.helmet) {
            textPaint.color = 0xFFFFC21F.toInt()
            canvas.drawText("⛑ 头盔", 36f * s, buffY, textPaint)
            buffY += 44f * s
        }
        if (game.magnetTime > 0f) {
            textPaint.color = 0xFFFF6B6B.toInt()
            canvas.drawText("🧲 磁铁 ${game.magnetLeft()}s", 36f * s, buffY, textPaint)
            buffY += 44f * s
        }
        if (game.doubleTime > 0f) {
            textPaint.color = 0xFFC77DFF.toInt()
            canvas.drawText("✦ 加倍 ${game.doubleLeft()}s", 36f * s, buffY, textPaint)
            buffY += 44f * s
        }
        if (game.riding != null) {
            textPaint.color = 0xFF7DEBA0.toInt()
            canvas.drawText("⚡ 滑索中", 36f * s, buffY, textPaint)
            buffY += 44f * s
        }
        // 按主导权重显示天气（过渡期显示占比大的那个）
        val wRain = weatherWeight(Game.W_RAIN)
        val wSnow = weatherWeight(Game.W_SNOW)
        if (wRain > 0.5f) {
            textPaint.color = 0xFF9BB8E8.toInt()
            canvas.drawText("🌧 雨", 36f * s, buffY, textPaint)
        } else if (wSnow > 0.5f) {
            textPaint.color = 0xFFE8F2FF.toInt()
            canvas.drawText("❄ 雪", 36f * s, buffY, textPaint)
        }
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER

        // 跑酷中打破纪录的横幅
        if (game.state == Game.State.RUNNING && game.recordFlash > 0f) {
            val t = 2.6f - game.recordFlash
            val pop = 1f + 0.35f * (1f - min(1f, t * 5f))
            val alpha = (min(1f, game.recordFlash / 0.5f) * 255).toInt()
            textPaint.textSize = 64f * s * pop
            textPaint.color = (alpha shl 24) or 0x00FFD426
            canvas.drawText("🏆 新纪录！", w / 2f, h * 0.24f, textPaint)
            textPaint.color = Color.WHITE
        }

        when (game.state) {
            Game.State.READY -> {
                dim(canvas, w, h)
                textPaint.textSize = 78f * s
                canvas.drawText("汤姆猫跑酷", w / 2f, h * 0.34f, textPaint)
                textPaint.textSize = 34f * s
                canvas.drawText("点击屏幕开始", w / 2f, h * 0.50f, textPaint)
                textPaint.textSize = 27f * s
                canvas.drawText("左右滑动·换道    上滑·跳跃    下滑·铲滑", w / 2f, h * 0.61f, textPaint)
                canvas.drawText("道具：磁铁吸金币 · 头盔抗撞 · 加倍得分", w / 2f, h * 0.69f, textPaint)
                canvas.drawText("在地面对准绿色门架，走上索道跳过障碍！", w / 2f, h * 0.77f, textPaint)
                drawColorButton(canvas, w, h)
            }
            Game.State.DEAD -> {
                dim(canvas, w, h)
                textPaint.textSize = 70f * s
                canvas.drawText("游戏结束", w / 2f, h * 0.34f, textPaint)
                textPaint.textSize = 38f * s
                val record = if (game.score >= game.highScore && game.score > 0) "  新纪录！" else ""
                canvas.drawText("得分 ${game.score}$record", w / 2f, h * 0.48f, textPaint)
                textPaint.textSize = 30f * s
                textPaint.color = 0xFFFFD54A.toInt()
                canvas.drawText("金币 ${game.coins}", w / 2f, h * 0.57f, textPaint)
                textPaint.color = Color.WHITE
                if (game.deadTime > 0.6f) {
                    canvas.drawText("点击屏幕再来一次", w / 2f, h * 0.68f, textPaint)
                }
                drawColorButton(canvas, w, h)
            }
            Game.State.RUNNING -> Unit
        }
        textPaint.clearShadowLayer()
    }

    /** 像素风直角按钮（离屏坐标系） */
    private fun drawColorButton(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f
        val bw = 320f * s
        val bh = 62f * s
        colorBtn.set(w / 2f - bw / 2f, h * 0.855f, w / 2f + bw / 2f, h * 0.855f + bh)

        btnPaint.style = Paint.Style.FILL
        btnPaint.color = 0xCC222C38.toInt()
        canvas.drawRect(colorBtn, btnPaint)
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 3f * s
        btnPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawRect(colorBtn, btnPaint)
        btnPaint.style = Paint.Style.FILL

        // 颜色小方块
        val idx = game.catColor % COLOR_NAMES.size
        btnPaint.color = COLOR_CHIPS[idx]
        val cs = 15f * s
        canvas.drawRect(
            colorBtn.left + 30f * s, colorBtn.centerY() - cs,
            colorBtn.left + 30f * s + cs * 2f, colorBtn.centerY() + cs, btnPaint
        )

        textPaint.textSize = 27f * s
        canvas.drawText(
            "猫咪颜色：${COLOR_NAMES[idx]} ↺",
            colorBtn.centerX() + 14f * s, colorBtn.centerY() + 10f * s, textPaint
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
