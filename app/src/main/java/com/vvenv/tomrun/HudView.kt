package com.vvenv.tomrun

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 覆盖在 3D 画面上的中文 HUD：得分、开始/结束界面，并负责手势识别。
 */
class HudView(context: Context, private val game: Game) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val dimPaint = Paint()

    private var downX = 0f
    private var downY = 0f
    private var consumed = false

    private val s get() = height / 720f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val swipeMin = 60f * s
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
                if (!consumed) game.onTap()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = this.s

        textPaint.setShadowLayer(4f * s, 0f, 2f * s, 0x88000000.toInt())

        // 右上角：得分 / 金币 / 最高
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = 42f * s
        canvas.drawText("得分 ${game.score}", w - 36f * s, 66f * s, textPaint)
        textPaint.textSize = 28f * s
        textPaint.color = 0xFFFFD54A.toInt()
        canvas.drawText("金币 ${game.coins}", w - 36f * s, 106f * s, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText("最高 ${game.highScore}", w - 36f * s, 142f * s, textPaint)

        // 左上角：生效中的道具
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
        }
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER

        when (game.state) {
            Game.State.READY -> {
                dim(canvas)
                textPaint.textSize = 78f * s
                canvas.drawText("汤姆猫跑酷", w / 2f, h * 0.34f, textPaint)
                textPaint.textSize = 34f * s
                canvas.drawText("点击屏幕开始", w / 2f, h * 0.50f, textPaint)
                textPaint.textSize = 27f * s
                canvas.drawText("左右滑动·换道    上滑·跳跃    下滑·铲滑", w / 2f, h * 0.61f, textPaint)
                canvas.drawText("道具：磁铁吸金币 · 头盔抗撞 · 加倍得分", w / 2f, h * 0.69f, textPaint)
                canvas.drawText("在地面对准绿色门架，走上索道跳过障碍！", w / 2f, h * 0.77f, textPaint)
            }
            Game.State.DEAD -> {
                dim(canvas)
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
            }
            Game.State.RUNNING -> Unit
        }
        textPaint.clearShadowLayer()
        postInvalidateOnAnimation()
    }

    private fun dim(canvas: Canvas) {
        dimPaint.color = 0x66000000
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }
}
