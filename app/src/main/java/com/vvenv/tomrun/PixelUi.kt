package com.vvenv.tomrun

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

/**
 * 星露谷式立体像素 UI：厚木框 + 多层斜面 + 双色投影。
 * 绘制顺序仿 SDV 对话框按钮——先外圈深色框，再内芯填色，最后左上亮带 / 右下暗带。
 */
object PixelUi {

    enum class Bevel { RAISED, PRESSED, INSET }

    enum class AccentSide { NONE, LEFT, RIGHT, BOTH }

    data class WoodStyle(
        val body: Int,
        val topHi: Int,
        val bottomLo: Int,
        val grain: Boolean = false
    )

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

    fun drawButton(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        fill: Int, edge: Int, s: Float,
        bevel: Bevel = Bevel.RAISED,
        shadow: Boolean = true,
        notch: Float? = null
    ) {
        val nn = notch ?: min(r - l, b - t) * 0.18f
        val bw = bandW(s)
        val dy = if (bevel == Bevel.PRESSED) bw * 0.55f else 0f
        val tl = l
        val tt = t + dy
        val tr = r
        val tb = b + dy

        if (shadow && bevel == Bevel.RAISED) drawDropShadow(canvas, paint, tl, tt, tr, tb, s, nn)
        drawDepthFrameChamfer(canvas, paint, tl, tt, tr, tb, nn, paletteFor(fill, edge), s, bevel)
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

    fun drawWoodPlaque(
        canvas: Canvas, paint: Paint,
        l: Float, t: Float, r: Float, b: Float,
        s: Float, style: WoodStyle, accent: Int,
        accentSide: AccentSide = AccentSide.NONE,
        accentStroke: Boolean = true
    ) {
        val edge = darken(style.body, 0.18f)
        drawDropShadow(canvas, paint, l, t, r, b, s, null)
        drawDepthFrameRect(
            canvas, paint, l, t, r, b,
            DepthPalette(
                fill = style.body,
                outer = edge,
                specular = lighten(style.topHi, 0.35f),
                hi = style.topHi,
                midHi = lighten(style.body, 0.12f),
                lo = style.bottomLo,
                deep = darken(style.bottomLo, 0.22f)
            ),
            s, Bevel.RAISED
        )

        val ow = frameW(s)
        val il = l + ow
        val it = t + ow
        val ir = r - ow
        val ib = b - ow

        if (style.grain) {
            paint.style = Paint.Style.FILL
            paint.color = 0x405A3A1E
            for (i in 1..3) {
                val ly = it + 6f * s + i * ((ib - it - 12f * s) / 4f)
                canvas.drawRect(il + 5f * s, ly, ir - 5f * s, ly + 1.5f * s, paint)
            }
        }

        if (accentStroke) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(2f, 2f * s)
            paint.color = accent
            canvas.drawRect(l + 1f * s, t + 1f * s, r - 1f * s, b - 1f * s, paint)
            paint.style = Paint.Style.FILL
        }

        val barW = max(5f, 5f * s)
        paint.color = accent
        when (accentSide) {
            AccentSide.LEFT -> canvas.drawRect(il, it + 2f * s, il + barW, ib - 2f * s, paint)
            AccentSide.RIGHT -> canvas.drawRect(ir - barW, it + 2f * s, ir, ib - 2f * s, paint)
            AccentSide.BOTH -> {
                canvas.drawRect(il, it + 2f * s, il + barW, ib - 2f * s, paint)
                canvas.drawRect(ir - barW, it + 2f * s, ir, ib - 2f * s, paint)
            }
            AccentSide.NONE -> Unit
        }
    }

    val WOOD_NAME = WoodStyle(0xFF6A4528.toInt(), 0xFF9A6A3C.toInt(), 0xFF3E2818.toInt(), grain = true)
    val WOOD_LEAVE = WoodStyle(0xFF3A4E62.toInt(), 0xFF5A7088.toInt(), 0xFF243040.toInt())
    val WOOD_WALLET = WoodStyle(0xFF4A3C28.toInt(), 0xFF6A5238.toInt(), 0xFF2A2018.toInt())
}
