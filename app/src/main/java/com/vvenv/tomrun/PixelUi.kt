package com.vvenv.tomrun

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

/**
 * 极简像素 UI：平涂、细描边、几乎没有阴影和斜面。
 * 3D 世界负责体积感，HUD 只负责读得清。
 */
object PixelUi {

    enum class Bevel { RAISED, PRESSED, INSET }

    /** 多层斜面色阶，由 [paletteFor] 从主题色推导 */
    data class DepthPalette(
        val fill: Int,
        val outer: Int,
        val specular: Int,
        val hi: Int,
        val midHi: Int,
        val lo: Int,
        val deep: Int
    )

    private val chamferPathBuf = Path()

    fun withAlpha(c: Int, a: Int): Int = (a shl 24) or (c and 0x00FFFFFF)

    fun lighten(c: Int, amount: Float): Int {
        val a = c ushr 24 and 0xFF
        fun ch(x: Int) = (x + ((255 - x) * amount)).toInt().coerceIn(0, 255)
        return (a shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }

    fun darken(c: Int, amount: Float): Int {
        val a = c ushr 24 and 0xFF
        fun ch(x: Int) = (x * (1f - amount)).toInt().coerceIn(0, 255)
        return (a shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }

    fun darken(c: Int): Int = darken(c, 0.28f)

    /** 从面板/按钮的主题色生成 SDV 式多层斜面色 */
    fun paletteFor(fill: Int, edge: Int): DepthPalette = DepthPalette(
        fill = fill,
        outer = edge,
        specular = lighten(fill, 0.52f),
        hi = lighten(fill, 0.34f),
        midHi = lighten(fill, 0.18f),
        lo = blend(edge, darken(fill, 0.35f), 0.55f),
        deep = darken(edge, 0.28f)
    )

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(ai: Int, bi: Int) = (ai + (bi - ai) * t).toInt().coerceIn(0, 255)
        val alpha = ((a ushr 24) * (1f - t) + (b ushr 24) * t).toInt().coerceIn(0, 255)
        return (alpha shl 24) or
            (ch(a shr 16 and 0xFF, b shr 16 and 0xFF) shl 16) or
            (ch(a shr 8 and 0xFF, b shr 8 and 0xFF) shl 8) or
            ch(a and 0xFF, b and 0xFF)
    }

    fun chamferPath(l: Float, t: Float, r: Float, b: Float, n: Float): Path {
        val nn = n.coerceAtMost(min(r - l, b - t) * 0.5f).coerceAtLeast(0f)
        chamferPathBuf.reset()
        if (nn <= 0f) {
            chamferPathBuf.addRect(l, t, r, b, Path.Direction.CW)
            return chamferPathBuf
        }
        chamferPathBuf.moveTo(l + nn, t)
        chamferPathBuf.lineTo(r - nn, t)
        chamferPathBuf.lineTo(r, t + nn)
        chamferPathBuf.lineTo(r, b - nn)
        chamferPathBuf.lineTo(r - nn, b)
        chamferPathBuf.lineTo(l + nn, b)
        chamferPathBuf.lineTo(l, b - nn)
        chamferPathBuf.lineTo(l, t + nn)
        chamferPathBuf.close()
        return chamferPathBuf
    }

    fun drawPanel(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, edge: Int, s: Float,
        notch: Float,
        edgeW: Float = 0f,
        shadow: Boolean = true,
        bevel: Boolean = true
    ) {
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRect(l, t, r, b, paint)
        val stroke = if (edgeW > 0f) edgeW else max(1f, s)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = withAlpha(edge, 0x66)
        canvas.drawRect(l + stroke * 0.5f, t + stroke * 0.5f, r - stroke * 0.5f, b - stroke * 0.5f, paint)
        paint.style = Paint.Style.FILL
    }

    fun drawRect(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, s: Float,
        bevel: Bevel = Bevel.RAISED,
        edge: Int? = null,
        edgeW: Float = 0f,
        shadow: Boolean = false
    ) {
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawRect(l, t, r, b, paint)
        val stroke = if (edgeW > 0f) edgeW else max(1f, s)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = withAlpha(edge ?: 0xFFFFFFFF.toInt(), if (bevel == Bevel.INSET) 0x22 else 0x33)
        canvas.drawRect(l + stroke * 0.5f, t + stroke * 0.5f, r - stroke * 0.5f, b - stroke * 0.5f, paint)
        paint.style = Paint.Style.FILL
    }

    fun drawCrystalPip(
        canvas: Canvas, paint: Paint,
        cx: Float, cy: Float, w: Float, h: Float,
        s: Float, color: Int, filled: Boolean, glow: Float = 1f
    ) {
        val l = cx - w / 2f
        val t = cy - h / 2f
        val r = cx + w / 2f
        val b = cy + h / 2f
        paint.style = Paint.Style.FILL
        if (filled) {
            paint.color = withAlpha(color, (glow * 220).toInt())
            canvas.drawRect(l, t, r, b, paint)
        } else {
            paint.color = 0x332A303A
            canvas.drawRect(l, t, r, b, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, s)
            paint.color = 0x445A6A7E
            canvas.drawRect(l, t, r, b, paint)
            paint.style = Paint.Style.FILL
        }
    }

    fun drawDivider(
        canvas: Canvas, paint: Paint,
        x1: Float, y: Float, x2: Float,
        s: Float, color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = withAlpha(color, 80)
        val h = max(1f, s)
        canvas.drawRect(x1, y - h * 0.5f, x2, y + h * 0.5f, paint)
    }

    fun drawBtn(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, s: Float,
        pressed: Boolean = false,
        radius: Float? = null
    ) {
        val rr = (radius ?: min(r - l, b - t) * 0.12f).coerceAtMost(8f * s)
        val use = if (pressed) darken(fill, 0.08f) else fill
        paint.style = Paint.Style.FILL
        paint.color = use
        canvas.drawRoundRect(l, t, r, b, rr, rr, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, s)
        paint.color = withAlpha(0xFFFFFFFF.toInt(), if (pressed) 0x18 else 0x28)
        canvas.drawRoundRect(l, t, r, b, rr, rr, paint)
        paint.style = Paint.Style.FILL
    }

    fun drawMeter(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, frac: Float, s: Float
    ) {
        val f = frac.coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = 0x331A222C
        canvas.drawRect(l, t, r, b, paint)
        val mid = l + (r - l) * f
        if (mid > l) {
            paint.color = fill
            canvas.drawRect(l, t, mid, b, paint)
        }
    }

    fun drawCoin(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, pulse: Float = 1f) {
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFC21F.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = withAlpha(0xFFFFFFFF.toInt(), (40 * pulse).toInt())
        canvas.drawCircle(cx, cy, r * 0.55f, paint)
    }
}
