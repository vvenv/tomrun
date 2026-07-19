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

    // 关闭抗锯齿 / 亚像素：低分辨率位图上必须是硬边缘，最近邻放大后才是块状像素字
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

    private val colorBtn = RectF()   // 离屏坐标系

    companion object {
        private const val PIX = 3    // 像素化倍率（越大块越粗）
        private val COLOR_NAMES = arrayOf("蓝灰", "橘黄", "乌黑", "粉红")
        private val COLOR_CHIPS = intArrayOf(
            0xFF8594B3.toInt(), 0xFFF29E42.toInt(), 0xFF4D4D59.toInt(), 0xFFF5A8C1.toInt()
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

    // ---------- 全部 HUD 内容（离屏低分辨率坐标系） ----------
    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val s = h / 720f
        // 硬阴影：整像素偏移，避免 soft shadow 把像素边缘抹糊
        val shadowDx = 1f
        val shadowDy = 1f

        // 右上角：得分 / 金币 / 最高
        textPaint.textAlign = Paint.Align.RIGHT
        pixText(canvas, "得分 ${game.score}", w - 36f * s, 66f * s, 42f * s, Color.WHITE, shadowDx, shadowDy)
        pixText(canvas, "金币 ${game.coins}", w - 36f * s, 106f * s, 28f * s, 0xFFFFD54A.toInt(), shadowDx, shadowDy)
        pixText(canvas, "最高 ${game.highScore}", w - 36f * s, 142f * s, 28f * s, Color.WHITE, shadowDx, shadowDy)

        // 左上角：生效中的道具 / 滑索 / 天气
        textPaint.textAlign = Paint.Align.LEFT
        var buffY = 66f * s
        if (game.helmet) {
            pixText(canvas, "头盔", 36f * s, buffY, 30f * s, 0xFFFFC21F.toInt(), shadowDx, shadowDy)
            buffY += 44f * s
        }
        if (game.magnetTime > 0f) {
            pixText(canvas, "磁铁 ${game.magnetLeft()}s", 36f * s, buffY, 30f * s, 0xFFFF6B6B.toInt(), shadowDx, shadowDy)
            buffY += 44f * s
        }
        if (game.doubleTime > 0f) {
            pixText(canvas, "加倍 ${game.doubleLeft()}s", 36f * s, buffY, 30f * s, 0xFFC77DFF.toInt(), shadowDx, shadowDy)
            buffY += 44f * s
        }
        if (game.riding != null) {
            pixText(canvas, "滑索中", 36f * s, buffY, 30f * s, 0xFF7DEBA0.toInt(), shadowDx, shadowDy)
            buffY += 44f * s
        }
        // 按主导权重显示天气（过渡期显示占比大的那个）
        val wRain = weatherWeight(Game.W_RAIN)
        val wSnow = weatherWeight(Game.W_SNOW)
        if (wRain > 0.5f) {
            pixText(canvas, "雨", 36f * s, buffY, 30f * s, 0xFF9BB8E8.toInt(), shadowDx, shadowDy)
        } else if (wSnow > 0.5f) {
            pixText(canvas, "雪", 36f * s, buffY, 30f * s, 0xFFE8F2FF.toInt(), shadowDx, shadowDy)
        }
        textPaint.textAlign = Paint.Align.CENTER

        // 跑酷中打破纪录的横幅
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
                pixText(canvas, "汤姆猫跑酷", w / 2f, h * 0.34f, 78f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "点击屏幕开始", w / 2f, h * 0.50f, 34f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "左右滑动·换道    上滑·跳跃    下滑·铲滑", w / 2f, h * 0.61f, 27f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "道具：磁铁吸金币 · 头盔抗撞 · 加倍得分", w / 2f, h * 0.69f, 27f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "在地面对准绿色门架，走上索道跳过障碍！", w / 2f, h * 0.77f, 27f * s, Color.WHITE, shadowDx, shadowDy)
                drawColorButton(canvas, w, h)
            }
            Game.State.DEAD -> {
                dim(canvas, w, h)
                pixText(canvas, "游戏结束", w / 2f, h * 0.34f, 70f * s, Color.WHITE, shadowDx, shadowDy)
                val record = if (game.score >= game.highScore && game.score > 0) "  新纪录！" else ""
                pixText(canvas, "得分 ${game.score}$record", w / 2f, h * 0.48f, 38f * s, Color.WHITE, shadowDx, shadowDy)
                pixText(canvas, "金币 ${game.coins}", w / 2f, h * 0.57f, 30f * s, 0xFFFFD54A.toInt(), shadowDx, shadowDy)
                if (game.deadTime > 0.6f) {
                    pixText(canvas, "点击屏幕再来一次", w / 2f, h * 0.68f, 30f * s, Color.WHITE, shadowDx, shadowDy)
                }
                drawColorButton(canvas, w, h)
            }
            Game.State.RUNNING -> Unit
        }
    }

    /** 整像素字号/坐标 + 硬阴影，保证离屏位图边缘是锐利色块 */
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

    /** 像素风直角按钮（离屏坐标系） */
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
        btnPaint.strokeWidth = 1f  // 离屏 1px 描边，放大后才是粗像素边
        btnPaint.color = 0xAAFFFFFF.toInt()
        canvas.drawRect(colorBtn, btnPaint)
        btnPaint.style = Paint.Style.FILL

        // 颜色小方块
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
