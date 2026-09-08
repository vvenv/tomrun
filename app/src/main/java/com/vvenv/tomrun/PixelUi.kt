package com.vvenv.tomrun

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

/**
 * 简洁像素风 UI：圆角按钮 + 切角面板 + 顶部高光 / 底部暗边。
 * 按钮走干净统一的圆角风格，避免过多装饰元素堆叠。
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

    /** 外框厚度：SDV 按钮约 3px */
    private fun frameW(s: Float): Float = max(3f * s, 3f)

    /** 内斜面亮/暗带厚度 */
    private fun bandW(s: Float): Float = max(3f * s, 3f)

    private fun drawDropShadow(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float, s: Float, notch: Float?
    ) {
        paint.style = Paint.Style.FILL
        paint.color = 0x88000000.toInt()
        val dx1 = 4f * s
        val dy1 = 5f * s
        if (notch != null && notch > 0f) {
            canvas.drawPath(chamferPath(l + dx1, t + dy1, r + dx1, b + dy1, notch), paint)
        } else {
            canvas.drawRect(l + dx1, t + dy1, r + dx1, b + dy1, paint)
        }
        paint.color = 0x44000000
        val dx0 = 2f * s
        val dy0 = 3f * s
        if (notch != null && notch > 0f) {
            canvas.drawPath(chamferPath(l + dx0, t + dy0, r + dx0, b + dy0, notch * 0.85f), paint)
        } else {
            canvas.drawRect(l + dx0, t + dy0, r + dx0, b + dy0, paint)
        }
    }

    /**
     * 轴对齐立体框：外圈 [outer] 填色 + 内芯 [fill] + 多层斜面带。
     * 这是 SDV 按钮质感的核心。
     */
    private fun drawDepthFrameRect(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        palette: DepthPalette, s: Float, bevel: Bevel
    ) {
        val ow = frameW(s)
        val bw = bandW(s)
        val il = l + ow
        val it = t + ow
        val ir = r - ow
        val ib = b - ow
        if (ir <= il || ib <= it) return

        paint.style = Paint.Style.FILL
        paint.color = palette.outer
        canvas.drawRect(l, t, r, b, paint)

        paint.color = palette.fill
        canvas.drawRect(il, it, ir, ib, paint)

        when (bevel) {
            Bevel.RAISED -> drawRaisedBands(canvas, paint, il, it, ir, ib, palette, s, bw)
            Bevel.PRESSED, Bevel.INSET -> drawInsetBands(canvas, paint, il, it, ir, ib, palette, s, bw)
        }
    }

    private fun drawRaisedBands(
        canvas: Canvas, paint: Paint,
        il: Float, it: Float, ir: Float, ib: Float,
        palette: DepthPalette, s: Float, bw: Float
    ) {
        val specH = max(1f, 1f * s)
        paint.color = palette.specular
        canvas.drawRect(il, it, ir, it + specH, paint)

        paint.color = palette.hi
        canvas.drawRect(il, it + specH, ir, it + specH + bw, paint)
        canvas.drawRect(il, it + specH, il + bw, ib, paint)

        paint.color = palette.midHi
        canvas.drawRect(il + bw * 0.25f, it + specH + bw, ir - bw * 0.5f, it + specH + bw * 1.55f, paint)

        paint.color = palette.lo
        canvas.drawRect(il, ib - bw * 1.45f, ir, ib, paint)
        canvas.drawRect(ir - bw * 1.45f, it, ir, ib, paint)

        paint.color = palette.deep
        canvas.drawRect(ir - bw, ib - bw, ir, ib, paint)
        canvas.drawRect(il, ib - bw, il + bw * 0.55f, ib, paint)
    }

    private fun drawInsetBands(
        canvas: Canvas, paint: Paint,
        il: Float, it: Float, ir: Float, ib: Float,
        palette: DepthPalette, s: Float, bw: Float
    ) {
        paint.color = palette.deep
        canvas.drawRect(il, it, ir, it + bw, paint)
        canvas.drawRect(il, it, il + bw, ib, paint)

        paint.color = palette.lo
        canvas.drawRect(il + bw, it + bw, ir, it + bw * 1.5f, paint)
        canvas.drawRect(il + bw, it + bw, il + bw * 1.5f, ib, paint)

        paint.color = palette.midHi
        canvas.drawRect(il, ib - bw, ir, ib, paint)
        canvas.drawRect(ir - bw, it, ir, ib, paint)

        paint.color = palette.specular
        val specH = max(1f, s)
        canvas.drawRect(ir - bw * 1.2f, ib - specH, ir, ib, paint)
    }

    /** 切角形状：外框 path 填色 + 内缩 path 填芯 + 矩形斜面带（裁剪在切角内） */
    private fun drawDepthFrameChamfer(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float, notch: Float,
        palette: DepthPalette, s: Float, bevel: Bevel
    ) {
        val ow = frameW(s)
        val nn = notch.coerceAtLeast(ow + 1f)

        paint.style = Paint.Style.FILL
        paint.color = palette.outer
        canvas.drawPath(chamferPath(l, t, r, b, nn), paint)

        val il = l + ow
        val it = t + ow
        val ir = r - ow
        val ib = b - ow
        val innerNotch = (nn - ow).coerceAtLeast(0f)
        paint.color = palette.fill
        canvas.drawPath(chamferPath(il, it, ir, ib, innerNotch), paint)

        canvas.save()
        canvas.clipPath(chamferPath(il, it, ir, ib, innerNotch))
        when (bevel) {
            Bevel.RAISED -> drawRaisedBands(canvas, paint, il, it, ir, ib, palette, s, bandW(s))
            Bevel.PRESSED, Bevel.INSET -> drawInsetBands(canvas, paint, il, it, ir, ib, palette, s, bandW(s))
        }
        canvas.restore()
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
        if (shadow) drawDropShadow(canvas, paint, l, t, r, b, s, notch)
        val palette = paletteFor(fill, edge)
        if (bevel) {
            drawDepthFrameChamfer(canvas, paint, l, t, r, b, notch, palette, s, Bevel.RAISED)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawPath(chamferPath(l, t, r, b, notch), paint)
            if (edgeW > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = edgeW
                paint.color = edge
                canvas.drawPath(chamferPath(l, t, r, b, notch), paint)
            }
        }
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
        val bw = bandW(s)
        val dy = if (bevel == Bevel.PRESSED) bw * 0.5f else 0f
        val tl = l
        val tt = t + dy
        val tr = r
        val tb = b + dy
        val edgeColor = edge ?: darken(fill, 0.35f)

        if (shadow && bevel == Bevel.RAISED) drawDropShadow(canvas, paint, tl, tt, tr, tb, s, null)
        drawDepthFrameRect(canvas, paint, tl, tt, tr, tb, paletteFor(fill, edgeColor), s, bevel)
        paint.style = Paint.Style.FILL
    }

    /**
     * 精致的能量水晶/进度格：比普通矩形格更有立体感和光泽。
     */
    fun drawCrystalPip(
        canvas: Canvas, paint: Paint,
        cx: Float, cy: Float, w: Float, h: Float,
        s: Float, color: Int, filled: Boolean, glow: Float = 1f
    ) {
        val l = cx - w / 2f
        val t = cy - h / 2f
        val r = cx + w / 2f
        val b = cy + h / 2f
        val bevel = max(2f, 2f * s)

        if (filled) {
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(darken(color, 0.3f), (glow * 255).toInt())
            canvas.drawRect(l - 1f * s, t - 1f * s, r + 1f * s, b + 1f * s, paint)

            paint.color = withAlpha(color, (glow * 255).toInt())
            canvas.drawRect(l, t, r, b, paint)

            paint.color = withAlpha(lighten(color, 0.45f), (glow * 255).toInt())
            canvas.drawRect(l + bevel, t + bevel, r - bevel, t + bevel * 1.5f, paint)
            canvas.drawRect(l + bevel, t + bevel, l + bevel * 1.5f, b - bevel, paint)

            paint.color = withAlpha(lighten(color, 0.7f), (glow * 180).toInt())
            canvas.drawRect(l + bevel * 1.5f, t + bevel * 1.3f, l + w * 0.35f, t + bevel * 2.2f, paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = 0xFF2A303A.toInt()
            canvas.drawRect(l, t, r, b, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(1f, 1f * s)
            paint.color = 0x555A6A7E.toInt()
            canvas.drawRect(l, t, r, b, paint)
            paint.style = Paint.Style.FILL
        }
    }

    /**
     * 绘制装饰性分隔线：双线中间带点，用于铭牌/面板内部。
     */
    fun drawDivider(
        canvas: Canvas, paint: Paint,
        x1: Float, y: Float, x2: Float,
        s: Float, color: Int
    ) {
        paint.style = Paint.Style.FILL
        val midX = (x1 + x2) / 2f
        val lineH = max(1f, 1f * s)
        val dotR = max(1.5f, 2f * s)

        paint.color = withAlpha(color, 120)
        canvas.drawRect(x1, y - lineH / 2f, midX - dotR * 2f, y + lineH / 2f, paint)
        canvas.drawRect(midX + dotR * 2f, y - lineH / 2f, x2, y + lineH / 2f, paint)

        paint.color = color
        canvas.drawCircle(midX, y, dotR, paint)
        paint.color = lighten(color, 0.4f)
        canvas.drawCircle(midX - dotR * 0.3f, y - dotR * 0.3f, dotR * 0.45f, paint)
    }

    /**
     * 简洁统一按钮：纯色填充 + 顶部高光 + 底部暗边 + 圆角描边。
     * 去掉木纹、钉头、切角等多余装饰，整体更干净精致。
     */
    fun drawBtn(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, s: Float,
        pressed: Boolean = false,
        radius: Float? = null
    ) {
        val rr = (radius ?: min(r - l, b - t) * 0.18f).coerceAtMost(min(r - l, b - t) * 0.5f)
        val bw = max(2f, 2f * s)
        val dy = if (pressed) bw * 0.5f else 0f

        if (!pressed) {
            paint.color = 0x44000000.toInt()
            canvas.drawRoundRect(l + 2f * s, t + dy + 3f * s, r + 2f * s, b + dy + 3f * s, rr, rr, paint)
        }

        paint.color = fill
        canvas.drawRoundRect(l, t + dy, r, b + dy, rr, rr, paint)

        paint.color = lighten(fill, 0.34f)
        canvas.drawRoundRect(l + bw, t + dy + bw, r - bw, t + dy + bw * 1.4f, bw * 0.5f, bw * 0.5f, paint)

        paint.color = darken(fill, 0.22f)
        canvas.drawRoundRect(l + bw, b + dy - bw * 1.4f, r - bw, b + dy - bw, bw * 0.5f, bw * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, 1.5f * s)
        paint.color = darken(fill, 0.32f)
        canvas.drawRoundRect(l, t + dy, r, b + dy, rr, rr, paint)
        paint.style = Paint.Style.FILL
    }

    /**
     * 横向进度条：深槽 + 填充 + 顶部高光。用于追击倒计时等瞬时提示。
     */
    fun drawMeter(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, frac: Float, s: Float
    ) {
        val f = frac.coerceIn(0f, 1f)
        val bw = max(1f, 1.5f * s)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF1A222C.toInt()
        canvas.drawRect(l, t, r, b, paint)
        paint.color = darken(fill, 0.45f)
        canvas.drawRect(l, t, r, t + bw, paint)
        canvas.drawRect(l, b - bw, r, b, paint)
        canvas.drawRect(l, t, l + bw, b, paint)
        canvas.drawRect(r - bw, t, r, b, paint)
        val innerL = l + bw
        val innerR = r - bw
        val innerT = t + bw
        val innerB = b - bw
        val mid = innerL + (innerR - innerL) * f
        if (mid > innerL) {
            paint.color = fill
            canvas.drawRect(innerL, innerT, mid, innerB, paint)
            paint.color = lighten(fill, 0.35f)
            canvas.drawRect(innerL, innerT, mid, innerT + bw, paint)
        }
    }

    /**
     * 金币图标：比简单矩形更精致的像素金币。
     */
    fun drawCoin(canvas: Canvas, paint: Paint, cx: Float, cy: Float, r: Float, pulse: Float = 1f) {
        val inner = r * 0.72f
        val core = r * 0.42f
        val shine = r * 0.28f

        paint.style = Paint.Style.FILL
        paint.color = 0xFFB8860B.toInt()
        canvas.drawCircle(cx, cy, r, paint)

        paint.color = 0xFFFFC21F.toInt()
        canvas.drawCircle(cx, cy, inner, paint)

        paint.color = 0xFFFFE878.toInt()
        canvas.drawCircle(cx, cy, core, paint)

        paint.color = withAlpha(0xFFFFFFFF.toInt(), (180 * pulse).toInt())
        canvas.drawCircle(cx - shine * 0.6f, cy - shine * 0.6f, shine, paint)

        paint.color = 0xFF8A6508.toInt()
        canvas.drawRect(cx - r * 0.12f, cy - inner * 0.8f, cx + r * 0.12f, cy + inner * 0.8f, paint)
    }
}
