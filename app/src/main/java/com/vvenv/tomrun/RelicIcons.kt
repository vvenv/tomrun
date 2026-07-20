package com.vvenv.tomrun

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 藏品像素配图：图鉴列表、跑道名牌与大图展柜共用。
 * [half] 为半宽（图标约 2*half 见方），坐标以中心为准。
 * 器物先烘焙到离屏 Bitmap，再整数倍最近邻放大，保持点阵锐利。
 */
object RelicIcons {

    private val BRONZE = 0xFF5A8A6A.toInt()
    private val BRONZE_DK = 0xFF3E6450.toInt()
    private val BRONZE_LT = 0xFF7AAD8A.toInt()
    private val JADE = 0xFF4EC8C0.toInt()
    private val JADE_DK = 0xFF2A8A84.toInt()
    private val JADE_LT = 0xFF8EE8E0.toInt()
    private val GOLD = 0xFFE8C040.toInt()
    private val GOLD_DK = 0xFFB89020.toInt()
    private val GOLD_LT = 0xFFFFE078.toInt()
    private val CLAY = 0xFFC47848.toInt()
    private val CLAY_DK = 0xFF8A5030.toInt()
    private val CLAY_LT = 0xFFE09868.toInt()
    private val BONE = 0xFFE8DCC0.toInt()
    private val BONE_DK = 0xFFB0A080.toInt()
    private val INK = 0xFF2A2A2A.toInt()
    private val PAPER = 0xFFF0E8D0.toInt()
    private val BLUE = 0xFF3A6EC8.toInt()
    private val BLUE_LT = 0xFF6A9AE8.toInt()
    private val WHITE = 0xFFF5F5F5.toInt()
    private val WOOD = 0xFF8B5A2B.toInt()
    private val WOOD_LT = 0xFFC49A6C.toInt()
    private val IRON = 0xFF6A7078.toInt()
    private val IRON_LT = 0xFF9AA0A8.toInt()
    private val LOCKED = 0xFF5A6470.toInt()
    private val LOCKED_DK = 0xFF3A4450.toInt()

    /** 大图离屏分辨率（逻辑像素）；调高可保留小数坐标细节 */
    private const val CACHE_FANCY = 160
    /** 列表/跑道小图离屏分辨率 */
    private const val CACHE_SIMPLE = 40

    private val fancyCache = arrayOfNulls<Bitmap>(Game.RELIC_COUNT)
    private val simpleCache = arrayOfNulls<Bitmap>(Game.RELIC_COUNT)
    private val blitRect = RectF()
    private val blitPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val bakePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }

    fun clearCache() {
        for (i in fancyCache.indices) {
            fancyCache[i]?.recycle()
            fancyCache[i] = null
            simpleCache[i]?.recycle()
            simpleCache[i] = null
        }
    }

    fun draw(
        canvas: Canvas,
        paint: Paint,
        id: Int,
        cx: Float,
        cy: Float,
        half: Float,
        collected: Boolean,
        fancy: Boolean = false,
        phase: Float = 0f,
        lightSurface: Boolean = false
    ) {
        paint.style = Paint.Style.FILL
        if (fancy && collected) {
            // 展柜框与闪点即时绘制（含动画）；器物走 Bitmap 缓存
            drawShowcaseFrame(canvas, paint, cx, cy, half, Game.RELIC_RARITY[id.coerceIn(0, Game.RELIC_COUNT - 1)], phase)
            val artHalf = half * 0.64f
            blitCachedArtifact(canvas, id, cx, cy - half * 0.08f, artHalf, fancy = true)
            drawSparkles(canvas, paint, cx, cy, half, phase)
        } else {
            paint.color = when {
                lightSurface && collected -> 0x22C8962A
                lightSurface -> 0x188A9098
                collected -> 0x33201810
                else -> 0x22101820
            }
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
            paint.color = when {
                lightSurface && collected -> 0x88C8962A.toInt()
                lightSurface -> 0x669AA0A8.toInt()
                collected -> 0x55FFD426
                else -> 0x33888888
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (half * 0.06f).coerceAtLeast(1f)
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
            paint.style = Paint.Style.FILL
            if (!collected) {
                drawMystery(canvas, paint, cx, cy, half)
            } else {
                blitCachedArtifact(canvas, id, cx, cy, half * 0.88f, fancy = false)
            }
        }
    }

    /** 放大走整数倍最近邻（点阵锐利）；缩小按实际比例并开滤波（跑道名牌等小尺寸） */
    private fun blitCachedArtifact(
        canvas: Canvas, id: Int, cx: Float, cy: Float, displayHalf: Float, fancy: Boolean
    ) {
        val bmp = cachedArtifact(id, fancy)
        val src = bmp.width.toFloat()
        val target = displayHalf * 2f
        val dst: Float
        if (target >= src) {
            dst = src * floor(target / src).toInt().coerceAtLeast(1)
            blitPaint.isFilterBitmap = false
        } else {
            dst = target
            blitPaint.isFilterBitmap = true
        }
        blitRect.set(cx - dst * 0.5f, cy - dst * 0.5f, cx + dst * 0.5f, cy + dst * 0.5f)
        canvas.drawBitmap(bmp, null, blitRect, blitPaint)
    }

    private fun cachedArtifact(id: Int, fancy: Boolean): Bitmap {
        val safeId = id.coerceIn(0, Game.RELIC_COUNT - 1)
        val cache = if (fancy) fancyCache else simpleCache
        cache[safeId]?.let { return it }
        val size = if (fancy) CACHE_FANCY else CACHE_SIMPLE
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val c = Canvas(bmp)
        bakePaint.style = Paint.Style.FILL
        bakePaint.shader = null
        // 器物坐标最大约 ±9（剑首/鹤首），half = size*0.88 时恰好铺满画布不裁切
        val bakeHalf = size * if (fancy) 0.88f else 0.5f
        drawArtifact(c, bakePaint, safeId, size * 0.5f, size * 0.5f, bakeHalf, fancy)
        if (fancy) refineFancy(bmp)
        cache[safeId] = bmp
        return bmp
    }

    /**
     * 大图烘焙后的像素级精修：
     * 1. 剪影外 1~2px 深色描边，轮廓更立体清晰；
     * 2. 剪影上缘受光提亮、下缘压暗，形成简单体积感。
     * 只处理透明度边界，不改动器物内部结构。
     */
    private fun refineFancy(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val out = src.copyOf()
        val outlineR = (w / 80).coerceAtLeast(1)
        val outline = 0xE0201A14.toInt()

        fun solid(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= w || y >= h) return false
            return (src[y * w + x] ushr 24) > 0x50
        }

        fun nearSolid(x: Int, y: Int, r: Int): Boolean {
            for (dy in -r..r) {
                for (dx in -r..r) {
                    if (dx == 0 && dy == 0) continue
                    if (solid(x + dx, y + dy)) return true
                }
            }
            return false
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (!solid(x, y)) {
                    if (nearSolid(x, y, outlineR)) out[i] = outline
                    continue
                }
                val c = src[i]
                // 上缘受光 / 下缘投影（只作用于剪影边界）
                if (!solid(x, y - 1) || !solid(x, y - 2)) {
                    out[i] = tint(c, 1.22f)
                } else if (!solid(x, y + 1)) {
                    out[i] = tint(c, 0.68f)
                } else if (!solid(x - 1, y)) {
                    out[i] = tint(c, 1.1f)
                } else if (!solid(x + 1, y)) {
                    out[i] = tint(c, 0.82f)
                }
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h)
    }

    private fun tint(color: Int, k: Float): Int {
        val a = color ushr 24
        val r = (((color shr 16) and 0xFF) * k).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * k).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * k).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun drawShowcaseFrame(
        canvas: Canvas, paint: Paint, cx: Float, cy: Float, half: Float, rarity: Int, phase: Float
    ) {
        val glow = when (rarity) {
            Game.RELIC_LEGEND -> 0xFFFFD426.toInt()
            Game.RELIC_RARE -> 0xFF4DE8FF.toInt()
            else -> 0xFF7AC89A.toInt()
        }
        val pulse = 0.55f + 0.12f * sin(phase * 2.2f)
        paint.color = withAlpha(glow, (70 * pulse).toInt())
        canvas.drawCircle(cx, cy, half * 1.08f, paint)
        paint.color = withAlpha(glow, (40 * pulse).toInt())
        canvas.drawCircle(cx, cy, half * 1.18f, paint)

        paint.color = 0xFF3A2818.toInt()
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        paint.color = 0xFF6A4A28.toInt()
        val inset = half * 0.06f
        canvas.drawRect(cx - half + inset, cy - half + inset, cx + half - inset, cy + half - inset, paint)

        paint.color = 0xFF141C2A.toInt()
        val pad = half * 0.12f
        canvas.drawRect(cx - half + pad, cy - half + pad, cx + half - pad, cy + half - pad, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (half * 0.035f).coerceAtLeast(1.5f)
        paint.color = glow
        canvas.drawRect(cx - half + pad, cy - half + pad, cx + half - pad, cy + half - pad, paint)
        paint.style = Paint.Style.FILL

        val c = half * 0.16f
        paint.color = GOLD_LT
        canvas.drawRect(cx - half + pad, cy - half + pad, cx - half + pad + c, cy - half + pad + half * 0.04f, paint)
        canvas.drawRect(cx - half + pad, cy - half + pad, cx - half + pad + half * 0.04f, cy - half + pad + c, paint)
        canvas.drawRect(cx + half - pad - c, cy - half + pad, cx + half - pad, cy - half + pad + half * 0.04f, paint)
        canvas.drawRect(cx + half - pad - half * 0.04f, cy - half + pad, cx + half - pad, cy - half + pad + c, paint)
        canvas.drawRect(cx - half + pad, cy + half - pad - half * 0.04f, cx - half + pad + c, cy + half - pad, paint)
        canvas.drawRect(cx - half + pad, cy + half - pad - c, cx - half + pad + half * 0.04f, cy + half - pad, paint)
        canvas.drawRect(cx + half - pad - c, cy + half - pad - half * 0.04f, cx + half - pad, cy + half - pad, paint)
        canvas.drawRect(cx + half - pad - half * 0.04f, cy + half - pad - c, cx + half - pad, cy + half - pad, paint)

        paint.color = 0xFF4A3420.toInt()
        canvas.drawRect(cx - half * 0.72f, cy + half * 0.62f, cx + half * 0.72f, cy + half * 0.78f, paint)
        paint.color = 0xFF7A5A30.toInt()
        canvas.drawRect(cx - half * 0.78f, cy + half * 0.78f, cx + half * 0.78f, cy + half * 0.88f, paint)
        paint.color = GOLD_DK
        canvas.drawRect(cx - half * 0.2f, cy + half * 0.68f, cx + half * 0.2f, cy + half * 0.72f, paint)
    }

    private fun drawSparkles(canvas: Canvas, paint: Paint, cx: Float, cy: Float, half: Float, phase: Float) {
        val seeds = floatArrayOf(0.7f, 1.9f, 2.8f, 4.1f, 5.3f, 6.0f)
        for (i in seeds.indices) {
            val a = seeds[i] + phase * (0.6f + i * 0.07f)
            val r = half * (0.78f + 0.08f * sin(a * 1.3f))
            val sx = cx + cos(a) * r * 0.85f
            val sy = cy + sin(a * 1.1f) * r * 0.75f
            val tw = (sin(phase * 4f + i) * 0.5f + 0.5f)
            val sz = half * (0.018f + 0.022f * tw)
            paint.color = withAlpha(0xFFFFF6C8.toInt(), (120 + tw * 135).toInt())
            canvas.drawRect(sx - sz, sy - sz, sx + sz, sy + sz, paint)
        }
    }

    private fun drawArtifact(
        canvas: Canvas, paint: Paint, id: Int, cx: Float, cy: Float, half: Float, fancy: Boolean
    ) {
        // fancy 用更细网格，便于大图刻画实物轮廓
        val u = half / if (fancy) 16f else 8f
        when (id.coerceIn(0, Game.RELIC_COUNT - 1)) {
            0 -> drawBasin(canvas, paint, cx, cy, u, fancy)
            1 -> drawOracle(canvas, paint, cx, cy, u, fancy)
            2 -> drawJue(canvas, paint, cx, cy, u, fancy)
            3 -> drawBamboo(canvas, paint, cx, cy, u, fancy)
            4 -> drawCoin(canvas, paint, cx, cy, u, fancy)
            5 -> drawTile(canvas, paint, cx, cy, u, fancy)
            6 -> drawSancai(canvas, paint, cx, cy, u, fancy)
            7 -> drawPorcelain(canvas, paint, cx, cy, u, fancy)
            8 -> drawDing(canvas, paint, cx, cy, u, fancy)
            9 -> drawSword(canvas, paint, cx, cy, u, fancy)
            10 -> drawBells(canvas, paint, cx, cy, u, fancy)
            11 -> drawTerracotta(canvas, paint, cx, cy, u, fancy)
            12 -> drawGallopingHorse(canvas, paint, cx, cy, u, fancy)
            13 -> drawScroll(canvas, paint, cx, cy, u, fancy)
            14 -> drawZun(canvas, paint, cx, cy, u, fancy)
            15 -> drawJadeSuit(canvas, paint, cx, cy, u, fancy)
            16 -> drawQingming(canvas, paint, cx, cy, u, fancy)
            17 -> drawApsara(canvas, paint, cx, cy, u, fancy)
            18 -> drawCong(canvas, paint, cx, cy, u, fancy)
            19 -> drawMirror(canvas, paint, cx, cy, u, fancy)
            20 -> drawAbacus(canvas, paint, cx, cy, u, fancy)
            21 -> drawSinan(canvas, paint, cx, cy, u, fancy)
            22 -> drawPalaceLamp(canvas, paint, cx, cy, u, fancy)
            23 -> drawSilkPainting(canvas, paint, cx, cy, u, fancy)
            24 -> drawMask(canvas, paint, cx, cy, u, fancy)
            25 -> drawLotusCrane(canvas, paint, cx, cy, u, fancy)
            else -> drawDing(canvas, paint, cx, cy, u, fancy)
        }
    }

    private fun withAlpha(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0x00FFFFFF)

    private fun px(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, x: Float, y: Float, w: Float, h: Float, c: Int) {
        paint.color = c
        canvas.drawRect(cx + x * u, cy + y * u, cx + (x + w) * u, cy + (y + h) * u, paint)
    }

    private fun disc(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, x: Float, y: Float, r: Float, c: Int) {
        paint.color = c
        canvas.drawCircle(cx + x * u, cy + y * u, r * u, paint)
    }

    private fun drawMystery(canvas: Canvas, paint: Paint, cx: Float, cy: Float, half: Float) {
        val u = half / 8f
        paint.color = LOCKED
        canvas.drawCircle(cx, cy - u, u * 2.2f, paint)
        paint.color = LOCKED_DK
        canvas.drawRect(cx - u * 1.2f, cy + u * 1.5f, cx + u * 1.2f, cy + u * 4f, paint)
        paint.color = 0xFF8899AA.toInt()
        canvas.drawRect(cx - u * 1.4f, cy - u * 2.2f, cx + u * 1.4f, cy - u * 1.4f, paint)
        canvas.drawRect(cx + u * 0.4f, cy - u * 1.4f, cx + u * 1.4f, cy + u * 0.2f, paint)
        canvas.drawRect(cx - u * 0.4f, cy + u * 0.2f, cx + u * 0.4f, cy + u * 1.2f, paint)
        canvas.drawRect(cx - u * 0.5f, cy + u * 2.2f, cx + u * 0.5f, cy + u * 3.2f, paint)
    }

    // --- 器物（大图按实物标志特征刻画） ---

    /** 半坡人面鱼纹彩陶盆：宽口浅腹 + 人面与两侧鱼纹 */
    private fun drawBasin(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -5.5f, 2.5f, 11f, 2.3f, CLAY_DK)
            px(canvas, paint, cx, cy, u, -5f, -1.2f, 10f, 4.2f, CLAY)
            disc(canvas, paint, cx, cy, u, 0f, 0.6f, 1.6f, 0xFFE8C090.toInt())
            return
        }
        // 盆体外轮廓：上宽下收
        px(canvas, paint, cx, cy, u, -7.2f, 3.2f, 14.4f, 2.2f, CLAY_DK)
        px(canvas, paint, cx, cy, u, -6.8f, -1.5f, 13.6f, 5.2f, CLAY)
        px(canvas, paint, cx, cy, u, -6.4f, -2.2f, 12.8f, 1.2f, CLAY_LT) // 口沿
        px(canvas, paint, cx, cy, u, -6.0f, -1.5f, 12f, 0.5f, 0xFFE8B888.toInt())
        // 内壁色
        px(canvas, paint, cx, cy, u, -5.6f, -0.8f, 11.2f, 3.6f, 0xFFD09060.toInt())
        // 人面鱼纹（中心）
        disc(canvas, paint, cx, cy, u, 0f, 0.4f, 2.4f, 0xFFE8C898.toInt())
        px(canvas, paint, cx, cy, u, -1.5f, -0.5f, 0.9f, 0.9f, CLAY_DK) // 眼
        px(canvas, paint, cx, cy, u, 0.6f, -0.5f, 0.9f, 0.9f, CLAY_DK)
        px(canvas, paint, cx, cy, u, -0.35f, 0.3f, 0.7f, 0.5f, CLAY_DK) // 鼻
        px(canvas, paint, cx, cy, u, -0.9f, 1.1f, 1.8f, 0.55f, CLAY_DK) // 口
        // 两颊鱼身
        px(canvas, paint, cx, cy, u, -5.5f, -0.2f, 2.8f, 1.6f, 0xFFB87040.toInt())
        px(canvas, paint, cx, cy, u, -5.8f, 0.2f, 0.8f, 0.8f, 0xFFB87040.toInt()) // 鱼尾
        px(canvas, paint, cx, cy, u, 2.7f, -0.2f, 2.8f, 1.6f, 0xFFB87040.toInt())
        px(canvas, paint, cx, cy, u, 5.0f, 0.2f, 0.8f, 0.8f, 0xFFB87040.toInt())
        // 鱼眼与鳍
        disc(canvas, paint, cx, cy, u, -4.2f, 0.4f, 0.35f, CLAY_DK)
        disc(canvas, paint, cx, cy, u, 4.2f, 0.4f, 0.35f, CLAY_DK)
        px(canvas, paint, cx, cy, u, -4.6f, -0.6f, 1.2f, 0.35f, 0xFFA06030.toInt())
        px(canvas, paint, cx, cy, u, 3.4f, -0.6f, 1.2f, 0.35f, 0xFFA06030.toInt())
        // 口沿三角纹
        for (i in -5..5) {
            if (i % 2 == 0) px(canvas, paint, cx, cy, u, i * 1.1f - 0.35f, -2.0f, 0.7f, 0.55f, 0xFFA05828.toInt())
        }
        px(canvas, paint, cx, cy, u, -5.2f, -1.2f, 0.45f, 3.5f, 0x55FFE0C0)
    }

    /** 甲骨：不规则骨片 + 卜裂纹 + 契刻文字 */
    private fun drawOracle(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -4f, -5.5f, 8f, 11f, BONE_DK)
            px(canvas, paint, cx, cy, u, -3.5f, -5f, 7f, 10f, BONE)
            return
        }
        // 龟甲/骨片不规则外形
        px(canvas, paint, cx, cy, u, -5.5f, -6.5f, 11f, 13f, BONE_DK)
        px(canvas, paint, cx, cy, u, -5f, -6f, 10f, 12f, BONE)
        px(canvas, paint, cx, cy, u, -4.5f, -5.5f, 9f, 11f, 0xFFF0E4C8.toInt())
        // 左侧缺角、右侧鼓出，更像残片
        px(canvas, paint, cx, cy, u, -5.5f, -6.5f, 1.8f, 2.2f, 0xFF1A2030.toInt())
        px(canvas, paint, cx, cy, u, 4.2f, 4.5f, 1.8f, 2f, 0xFF1A2030.toInt())
        // 卜裂纹（兆纹）
        paint.color = 0xFF8A7060.toInt()
        canvas.drawRect(cx - u * 0.2f, cy - u * 5.5f, cx + u * 0.2f, cy + u * 4.5f, paint)
        canvas.drawRect(cx - u * 3.5f, cy - u * 1.5f, cx + u * 3.8f, cy - u * 1.1f, paint)
        canvas.drawRect(cx - u * 2.5f, cy + u * 1.8f, cx + u * 2.2f, cy + u * 2.15f, paint)
        canvas.drawRect(cx + u * 1.5f, cy - u * 4f, cx + u * 1.85f, cy - u * 0.5f, paint)
        // 契刻「贞」「卜」状字块
        val glyphs = arrayOf(
            -3.2f to -4.2f, -1.0f to -3.5f, 1.2f to -4.0f,
            -2.8f to -1.0f, 0.2f to -0.6f, 2.4f to -1.2f,
            -3.0f to 2.2f, -0.5f to 2.8f, 1.8f to 2.0f, -1.5f to 4.2f
        )
        for ((gx, gy) in glyphs) {
            px(canvas, paint, cx, cy, u, gx, gy, 1.4f, 0.35f, BONE_DK)
            px(canvas, paint, cx, cy, u, gx + 0.2f, gy - 0.7f, 0.35f, 1.6f, BONE_DK)
            px(canvas, paint, cx, cy, u, gx + 0.55f, gy + 0.5f, 0.9f, 0.3f, BONE_DK)
        }
        // 灼痕深色圆点
        disc(canvas, paint, cx, cy, u, -2.0f, 0.8f, 0.55f, 0xFF6A5040.toInt())
        disc(canvas, paint, cx, cy, u, 2.5f, 3.5f, 0.45f, 0xFF6A5040.toInt())
        px(canvas, paint, cx, cy, u, -4.2f, -5f, 0.4f, 9f, 0x66FFFFFF)
    }

    /** 青铜爵：流、柱、鋬、三尖足 */
    private fun drawJue(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -2.8f, 2f, 1f, 3.5f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, 1.8f, 2f, 1f, 3.5f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, -0.5f, 2f, 1f, 3.5f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, -2.8f, -2.2f, 5.6f, 4.4f, BRONZE)
            return
        }
        // 三尖足
        px(canvas, paint, cx, cy, u, -3.6f, 2.5f, 1.1f, 4.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -3.3f, 6.5f, 0.5f, 0.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 2.5f, 2.5f, 1.1f, 4.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 2.8f, 6.5f, 0.5f, 0.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -0.55f, 2.5f, 1.1f, 4.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -0.25f, 6.8f, 0.5f, 0.7f, BRONZE_DK)
        // 杯身
        px(canvas, paint, cx, cy, u, -3.4f, -2.0f, 6.8f, 5f, BRONZE)
        px(canvas, paint, cx, cy, u, -3.0f, -1.6f, 6f, 4f, BRONZE_LT)
        // 口沿
        px(canvas, paint, cx, cy, u, -3.6f, -2.4f, 7.2f, 0.7f, BRONZE_LT)
        // 流（向前倾的长嘴）
        px(canvas, paint, cx, cy, u, 2.8f, -4.5f, 4.2f, 1.8f, BRONZE)
        px(canvas, paint, cx, cy, u, 5.5f, -4.8f, 1.5f, 1.2f, BRONZE_LT)
        // 尾（流对面翘起）
        px(canvas, paint, cx, cy, u, -4.5f, -3.8f, 1.6f, 1.8f, BRONZE)
        // 双柱
        px(canvas, paint, cx, cy, u, -2.0f, -5.5f, 0.7f, 3.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 0.4f, -5.5f, 0.7f, 3.2f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, -1.65f, -5.6f, 0.55f, GOLD)
        disc(canvas, paint, cx, cy, u, 0.75f, -5.6f, 0.55f, GOLD)
        // 鋬（侧面把手）
        px(canvas, paint, cx, cy, u, -5.2f, -0.8f, 1.8f, 0.7f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -5.5f, -0.8f, 0.7f, 2.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -5.2f, 1.5f, 1.8f, 0.7f, BRONZE_DK)
        // 饕餮纹带
        px(canvas, paint, cx, cy, u, -2.6f, -0.3f, 5.2f, 1.4f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -1.2f, -0.05f, 2.4f, 0.9f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -2.8f, -1.8f, 0.4f, 3.2f, 0x55B8E0C0)
    }

    /** 竹简：成册竹片 + 编绳 + 墨字 */
    private fun drawBamboo(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        val n = if (fancy) 6 else 5
        val step = if (fancy) 1.9f else 1.7f
        val start = -(n - 1) / 2f * step
        for (i in 0 until n) {
            val x = start + i * step
            px(canvas, paint, cx, cy, u, x - 0.7f, -6.5f, 1.4f, 13f, WOOD_LT)
            px(canvas, paint, cx, cy, u, x - 0.55f, -6.2f, 1.1f, 12.4f, 0xFFD4B888.toInt())
            // 竹节
            px(canvas, paint, cx, cy, u, x - 0.7f, -2.2f, 1.4f, 0.35f, WOOD)
            px(canvas, paint, cx, cy, u, x - 0.7f, 2.0f, 1.4f, 0.35f, WOOD)
            if (fancy) {
                // 竖行墨字
                for (row in 0..4) {
                    val gy = -5.2f + row * 2.1f
                    px(canvas, paint, cx, cy, u, x - 0.2f, gy, 0.4f, 1.2f, INK)
                    if (row % 2 == 0) px(canvas, paint, cx, cy, u, x - 0.35f, gy + 0.35f, 0.7f, 0.25f, INK)
                }
            }
        }
        // 编绳
        px(canvas, paint, cx, cy, u, start - 0.9f, -3.6f, n * step + 0.4f, 0.55f, WOOD)
        px(canvas, paint, cx, cy, u, start - 0.9f, 3.0f, n * step + 0.4f, 0.55f, WOOD)
        if (fancy) {
            px(canvas, paint, cx, cy, u, start - 0.9f, -3.45f, n * step + 0.4f, 0.25f, 0xFF6A4020.toInt())
            px(canvas, paint, cx, cy, u, start - 0.9f, 3.15f, n * step + 0.4f, 0.25f, 0xFF6A4020.toInt())
        }
    }

    /** 秦半两：圆钱方孔 +「半」「两」字样 */
    private fun drawCoin(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        disc(canvas, paint, cx, cy, u, 0f, 0f, if (fancy) 7.2f else 5.5f, GOLD_DK)
        disc(canvas, paint, cx, cy, u, 0f, 0f, if (fancy) 6.6f else 5f, GOLD)
        if (fancy) {
            disc(canvas, paint, cx, cy, u, -1.5f, -1.8f, 1.6f, GOLD_LT) // 高光
            // 外郭
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.55f
            paint.color = GOLD_DK
            canvas.drawCircle(cx, cy, u * 6.3f, paint)
            paint.style = Paint.Style.FILL
        }
        // 方孔
        px(canvas, paint, cx, cy, u, -1.6f, -1.6f, 3.2f, 3.2f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -1.15f, -1.15f, 2.3f, 2.3f, 0xFF1A1020.toInt())
        if (fancy) {
            // 「半」在左、「两」在右（简化笔画）
            // 半
            px(canvas, paint, cx, cy, u, -4.8f, -2.4f, 2.2f, 0.4f, GOLD_DK)
            px(canvas, paint, cx, cy, u, -3.9f, -2.4f, 0.4f, 4.8f, GOLD_DK)
            px(canvas, paint, cx, cy, u, -4.8f, -0.2f, 2.2f, 0.4f, GOLD_DK)
            px(canvas, paint, cx, cy, u, -4.6f, 1.5f, 1.8f, 0.4f, GOLD_DK)
            // 两
            px(canvas, paint, cx, cy, u, 2.6f, -2.4f, 2.2f, 0.4f, GOLD_DK)
            px(canvas, paint, cx, cy, u, 2.6f, -2.4f, 0.4f, 4.8f, GOLD_DK)
            px(canvas, paint, cx, cy, u, 4.4f, -2.4f, 0.4f, 4.8f, GOLD_DK)
            px(canvas, paint, cx, cy, u, 2.6f, -0.2f, 2.2f, 0.4f, GOLD_DK)
            px(canvas, paint, cx, cy, u, 3.2f, 1.5f, 1.0f, 0.4f, GOLD_DK)
            // 上下铭文位
            px(canvas, paint, cx, cy, u, -1.0f, -5.2f, 2.0f, 0.55f, GOLD_DK)
            px(canvas, paint, cx, cy, u, -1.0f, 4.6f, 2.0f, 0.55f, GOLD_DK)
        }
    }

    /** 汉瓦当：圆形当面 + 半缺下缘 + 中心十字/文字 */
    private fun drawTile(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        val r = if (fancy) 7f else 5.4f
        disc(canvas, paint, cx, cy, u, 0f, 0.3f, r, 0xFF6A6A60.toInt())
        disc(canvas, paint, cx, cy, u, 0f, 0f, r - 0.5f, 0xFFA8A898.toInt())
        // 下半遮挡成瓦当剖面感
        px(canvas, paint, cx, cy, u, -r - 0.2f, 0.8f, r * 2 + 0.4f, r + 1f, 0xFF1A1020.toInt())
        if (fancy) {
            // 外圈弦纹
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u * 0.35f
            paint.color = 0xFF5A5A50.toInt()
            canvas.drawCircle(cx, cy, u * 6.2f, paint)
            canvas.drawCircle(cx, cy, u * 5.4f, paint)
            paint.style = Paint.Style.FILL
            // 四分区
            px(canvas, paint, cx, cy, u, -0.3f, -5.5f, 0.6f, 11f, 0xFF5A5A50.toInt())
            px(canvas, paint, cx, cy, u, -5.5f, -0.3f, 11f, 0.6f, 0xFF5A5A50.toInt())
            // 中心圆乳
            disc(canvas, paint, cx, cy, u, 0f, -0.4f, 1.8f, 0xFFC8C8B8.toInt())
            disc(canvas, paint, cx, cy, u, 0f, -0.4f, 0.9f, 0xFF5A5A50.toInt())
            // 「长乐」简化字块
            px(canvas, paint, cx, cy, u, -3.8f, -3.2f, 1.6f, 1.6f, 0xFF4A4A40.toInt())
            px(canvas, paint, cx, cy, u, 2.2f, -3.2f, 1.6f, 1.6f, 0xFF4A4A40.toInt())
            px(canvas, paint, cx, cy, u, -3.8f, 1.2f, 1.6f, 1.6f, 0xFF4A4A40.toInt())
            px(canvas, paint, cx, cy, u, 2.2f, 1.2f, 1.6f, 1.6f, 0xFF4A4A40.toInt())
            // 云纹小卷
            disc(canvas, paint, cx, cy, u, -4.5f, -0.3f, 0.7f, 0xFFB8B8AC.toInt())
            disc(canvas, paint, cx, cy, u, 4.5f, -0.3f, 0.7f, 0xFFB8B8AC.toInt())
        } else {
            disc(canvas, paint, cx, cy, u, 0f, -0.2f, 2.2f, 0xFFC0C0B4.toInt())
            px(canvas, paint, cx, cy, u, -0.45f, -2.8f, 0.9f, 5.2f, 0xFF5A5A50.toInt())
            px(canvas, paint, cx, cy, u, -2.8f, -0.45f, 5.6f, 0.9f, 0xFF5A5A50.toInt())
        }
    }

    /** 唐三彩：骆驼驮货，黄绿白釉斑 */
    private fun drawSancai(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -4.2f, -1.2f, 7.4f, 3.4f, 0xFFC08040.toInt())
            px(canvas, paint, cx, cy, u, -2.6f, -3.8f, 2.2f, 2.8f, 0xFF5EC878.toInt())
            px(canvas, paint, cx, cy, u, 0.1f, -3.8f, 2.2f, 2.8f, 0xFFF5F0E0.toInt())
            return
        }
        // 四腿
        px(canvas, paint, cx, cy, u, -4.2f, 2.2f, 1.1f, 4.5f, CLAY_DK)
        px(canvas, paint, cx, cy, u, -2.0f, 2.2f, 1.1f, 4.5f, CLAY_DK)
        px(canvas, paint, cx, cy, u, 1.2f, 2.2f, 1.1f, 4.5f, CLAY_DK)
        px(canvas, paint, cx, cy, u, 3.4f, 2.2f, 1.1f, 4.5f, CLAY_DK)
        // 身体
        px(canvas, paint, cx, cy, u, -4.8f, -1.0f, 9.6f, 4f, 0xFFD09050.toInt())
        px(canvas, paint, cx, cy, u, -4.4f, -0.6f, 8.8f, 3.2f, 0xFFE8B060.toInt())
        // 双峰与驼鞍
        px(canvas, paint, cx, cy, u, -2.8f, -3.8f, 2.4f, 3.2f, 0xFF5EC878.toInt()) // 绿釉峰
        px(canvas, paint, cx, cy, u, 0.4f, -4.0f, 2.4f, 3.4f, 0xFFF5F0E0.toInt()) // 白釉峰
        px(canvas, paint, cx, cy, u, -1.5f, -2.2f, 3.2f, 1.4f, 0xFFC07030.toInt()) // 鞍
        // 头颈
        px(canvas, paint, cx, cy, u, 3.5f, -3.5f, 1.6f, 4.2f, 0xFFE0A050.toInt())
        px(canvas, paint, cx, cy, u, 4.2f, -5.0f, 2.6f, 2.0f, 0xFFE8B060.toInt())
        disc(canvas, paint, cx, cy, u, 6.4f, -4.3f, 0.9f, 0xFFE8B060.toInt())
        // 耳
        px(canvas, paint, cx, cy, u, 5.0f, -5.8f, 0.5f, 1.0f, CLAY_DK)
        px(canvas, paint, cx, cy, u, 5.8f, -5.8f, 0.5f, 1.0f, CLAY_DK)
        // 三彩釉斑
        px(canvas, paint, cx, cy, u, -3.5f, 0.5f, 1.8f, 1.2f, 0xFF4A90C8.toInt())
        px(canvas, paint, cx, cy, u, 1.5f, 0.8f, 1.6f, 1.0f, 0xFF5EC878.toInt())
        px(canvas, paint, cx, cy, u, -1.0f, 1.5f, 1.4f, 0.8f, 0xFFF0E0C0.toInt())
        // 货囊
        px(canvas, paint, cx, cy, u, -3.2f, -2.8f, 1.6f, 1.4f, 0xFFB06030.toInt())
        px(canvas, paint, cx, cy, u, 1.2f, -2.8f, 1.6f, 1.4f, 0xFFB06030.toInt())
        px(canvas, paint, cx, cy, u, -4.0f, -0.4f, 0.4f, 2.5f, 0x55FFE8C0)
    }

    /** 青花瓷梅瓶：白地蓝花 + 肩部缠枝 */
    private fun drawPorcelain(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3.2f, -2.2f, 6.4f, 6.4f, WHITE)
            px(canvas, paint, cx, cy, u, -1.5f, -4.7f, 3f, 2.7f, WHITE)
            px(canvas, paint, cx, cy, u, -2.4f, -0.6f, 4.8f, 1f, BLUE)
            return
        }
        // 梅瓶：小口、短颈、丰肩、敛腹
        px(canvas, paint, cx, cy, u, -1.4f, -7.0f, 2.8f, 1.0f, 0xFFE8E8F0.toInt()) // 口
        px(canvas, paint, cx, cy, u, -1.1f, -6.2f, 2.2f, 1.8f, WHITE) // 颈
        px(canvas, paint, cx, cy, u, -5.2f, -4.5f, 10.4f, 3.2f, 0xFFE8E8F0.toInt()) // 肩外廓
        px(canvas, paint, cx, cy, u, -4.8f, -4.2f, 9.6f, 2.8f, WHITE)
        px(canvas, paint, cx, cy, u, -4.2f, -1.5f, 8.4f, 6.5f, WHITE) // 腹
        px(canvas, paint, cx, cy, u, -3.2f, 4.5f, 6.4f, 2.2f, 0xFFE0E0E8.toInt()) // 胫
        px(canvas, paint, cx, cy, u, -2.4f, 6.2f, 4.8f, 0.9f, WHITE) // 足
        // 青花纹样
        px(canvas, paint, cx, cy, u, -4.2f, -3.8f, 8.4f, 0.7f, BLUE) // 肩弦纹
        // 缠枝花
        disc(canvas, paint, cx, cy, u, -2.0f, -2.5f, 1.1f, BLUE)
        disc(canvas, paint, cx, cy, u, 2.0f, -2.2f, 1.0f, BLUE)
        disc(canvas, paint, cx, cy, u, 0f, 0.5f, 1.6f, BLUE)
        disc(canvas, paint, cx, cy, u, -0.4f, 0.2f, 0.6f, BLUE_LT)
        px(canvas, paint, cx, cy, u, -3.5f, 0.2f, 1.2f, 0.4f, BLUE)
        px(canvas, paint, cx, cy, u, 2.3f, 0.8f, 1.2f, 0.4f, BLUE)
        // 腹部蕉叶纹
        for (i in -3..3) {
            px(canvas, paint, cx, cy, u, i * 1.1f - 0.35f, 2.5f, 0.7f, 1.8f, BLUE)
        }
        px(canvas, paint, cx, cy, u, -3.6f, 5.0f, 7.2f, 0.45f, BLUE)
        // 高光
        px(canvas, paint, cx, cy, u, -4.0f, -3.5f, 0.45f, 7f, 0x66FFFFFF)
    }

    /** 后母戊鼎：方鼎双耳四足 + 饕餮 */
    private fun drawDing(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3.8f, 2.5f, 1.2f, 3f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, 2.6f, 2.5f, 1.2f, 3f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, -4.5f, -2.2f, 9f, 5f, BRONZE)
            return
        }
        // 四足
        px(canvas, paint, cx, cy, u, -5.2f, 3.0f, 1.5f, 4.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 3.7f, 3.0f, 1.5f, 4.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -2.2f, 3.0f, 1.3f, 4.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 0.9f, 3.0f, 1.3f, 4.2f, BRONZE_DK)
        // 蹄足端
        for (lx in floatArrayOf(-5.0f, -1.95f, 1.15f, 3.95f)) {
            px(canvas, paint, cx, cy, u, lx, 7.0f, 1.1f, 0.7f, BRONZE)
        }
        // 方腹
        px(canvas, paint, cx, cy, u, -5.8f, -2.5f, 11.6f, 6.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -5.4f, -2.1f, 10.8f, 5.4f, BRONZE)
        // 双立耳
        px(canvas, paint, cx, cy, u, -3.8f, -6.2f, 1.3f, 4.0f, BRONZE)
        px(canvas, paint, cx, cy, u, 2.5f, -6.2f, 1.3f, 4.0f, BRONZE)
        px(canvas, paint, cx, cy, u, -3.5f, -6.5f, 0.7f, 0.7f, BRONZE_LT)
        px(canvas, paint, cx, cy, u, 2.8f, -6.5f, 0.7f, 0.7f, BRONZE_LT)
        // 口沿
        px(canvas, paint, cx, cy, u, -5.6f, -2.5f, 11.2f, 0.8f, BRONZE_LT)
        // 饕餮兽面
        px(canvas, paint, cx, cy, u, -3.5f, -0.8f, 7f, 2.8f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, -1.8f, 0.2f, 0.7f, GOLD)
        disc(canvas, paint, cx, cy, u, 1.8f, 0.2f, 0.7f, GOLD)
        px(canvas, paint, cx, cy, u, -0.8f, 0.8f, 1.6f, 0.7f, GOLD_DK)
        // 扉棱
        px(canvas, paint, cx, cy, u, -0.35f, -2.0f, 0.7f, 5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -5.4f, 1.5f, 10.8f, 0.45f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -4.8f, -1.8f, 0.4f, 4f, 0x55B8E0C0)
    }

    /** 越王勾践剑：窄刃、剑格、圆茎、箍 */
    private fun drawSword(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -0.8f, -6f, 1.6f, 8.2f, IRON)
            px(canvas, paint, cx, cy, u, -2.8f, 1.6f, 5.6f, 1.2f, GOLD)
            px(canvas, paint, cx, cy, u, -1.1f, 2.8f, 2.2f, 2.7f, WOOD)
            return
        }
        // 剑身（中脊高光）
        px(canvas, paint, cx, cy, u, -1.0f, -7.5f, 2.0f, 10.5f, 0xFF7A9088.toInt())
        px(canvas, paint, cx, cy, u, -0.55f, -7.2f, 1.1f, 9.8f, 0xFFB0C8C0.toInt())
        px(canvas, paint, cx, cy, u, -0.2f, -7.0f, 0.4f, 9.2f, 0xFFE8F8F0.toInt()) // 中脊
        // 尖锋
        px(canvas, paint, cx, cy, u, -0.7f, -8.0f, 1.4f, 0.9f, 0xFF9AB0A8.toInt())
        px(canvas, paint, cx, cy, u, -0.35f, -8.5f, 0.7f, 0.7f, 0xFFC0D8D0.toInt())
        // 剑格（鸟篆纹暗示）
        px(canvas, paint, cx, cy, u, -3.2f, 2.5f, 6.4f, 1.5f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -2.8f, 2.7f, 5.6f, 1.1f, GOLD)
        px(canvas, paint, cx, cy, u, -2.2f, 3.0f, 1.0f, 0.5f, GOLD_LT)
        px(canvas, paint, cx, cy, u, 1.2f, 3.0f, 1.0f, 0.5f, GOLD_LT)
        // 圆茎 + 双箍
        px(canvas, paint, cx, cy, u, -1.0f, 4.0f, 2.0f, 3.2f, 0xFF5A7068.toInt())
        px(canvas, paint, cx, cy, u, -1.3f, 4.5f, 2.6f, 0.55f, GOLD)
        px(canvas, paint, cx, cy, u, -1.3f, 5.8f, 2.6f, 0.55f, GOLD)
        // 剑首（圆盘）
        disc(canvas, paint, cx, cy, u, 0f, 7.5f, 1.5f, GOLD_DK)
        disc(canvas, paint, cx, cy, u, 0f, 7.5f, 1.0f, GOLD)
        disc(canvas, paint, cx, cy, u, 0f, 7.5f, 0.45f, GOLD_LT)
        // 绿锈斑
        disc(canvas, paint, cx, cy, u, -0.6f, -3f, 0.4f, 0x884EC8A0.toInt())
        disc(canvas, paint, cx, cy, u, 0.5f, -1f, 0.35f, 0x884EC8A0.toInt())
    }

    /** 曾侯乙编钟：双层红漆钟架，上排小甬钟、下排大甬钟 */
    private fun drawBells(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -7.2f, -6.5f, 14.4f, 1.5f, WOOD)
            px(canvas, paint, cx, cy, u, -6.8f, -6.2f, 13.6f, 0.9f, WOOD_LT)
            px(canvas, paint, cx, cy, u, -7.0f, -6.5f, 1.0f, 13f, WOOD)
            px(canvas, paint, cx, cy, u, 6.0f, -6.5f, 1.0f, 13f, WOOD)
            val sizes = floatArrayOf(1.2f, 1.4f, 1.6f)
            for (i in sizes.indices) {
                val x = -3.2f + i * 3.2f
                val s = sizes[i]
                val top = -5.0f
                val h = 5.5f + s * 1.2f
                px(canvas, paint, cx, cy, u, x - 0.25f, -6.5f, 0.5f, 1.6f, GOLD)
                px(canvas, paint, cx, cy, u, x - 0.9f * s, top, 1.8f * s, h * 0.35f, BRONZE_LT)
                px(canvas, paint, cx, cy, u, x - 1.2f * s, top + h * 0.3f, 2.4f * s, h * 0.7f, BRONZE)
                px(canvas, paint, cx, cy, u, x - 1.2f * s, top + h - 0.8f, 2.4f * s, 0.8f, BRONZE_DK)
            }
            return
        }

        val LACQ = 0xFF8A3020.toInt()      // 红漆梁
        val LACQ_DK = 0xFF5A1E14.toInt()   // 纹饰底
        val LACQ_LT = 0xFFA84830.toInt()

        // 红漆横梁：漆面 + 深色纹饰格 + 青铜端套
        fun beam(top: Float, hgt: Float, halfW: Float) {
            px(canvas, paint, cx, cy, u, -halfW, top, halfW * 2, hgt, LACQ)
            px(canvas, paint, cx, cy, u, -halfW, top, halfW * 2, 0.25f, LACQ_LT)
            px(canvas, paint, cx, cy, u, -halfW, top + hgt - 0.25f, halfW * 2, 0.25f, 0xFF3E140C.toInt())
            // 纹饰格
            var gx = -halfW + 1.3f
            while (gx < halfW - 2.2f) {
                px(canvas, paint, cx, cy, u, gx, top + 0.3f, 1.6f, hgt - 0.6f, LACQ_DK)
                disc(canvas, paint, cx, cy, u, gx + 0.8f, top + hgt * 0.5f, 0.28f, GOLD_DK)
                gx += 2.4f
            }
            // 青铜端套
            px(canvas, paint, cx, cy, u, -halfW - 0.5f, top - 0.15f, 1.1f, hgt + 0.3f, BRONZE)
            px(canvas, paint, cx, cy, u, halfW - 0.6f, top - 0.15f, 1.1f, hgt + 0.3f, BRONZE)
            px(canvas, paint, cx, cy, u, -halfW - 0.5f, top - 0.15f, 1.1f, 0.3f, BRONZE_LT)
            px(canvas, paint, cx, cy, u, halfW - 0.6f, top - 0.15f, 1.1f, 0.3f, BRONZE_LT)
        }

        // 甬钟：甬柱挂钩、合瓦形身、乳钉、篆带、于口两侧尖角
        fun bell(x: Float, top: Float, hgt: Float, wTop: Float, wBot: Float, glyph: Boolean) {
            // 甬（悬挂柱）
            px(canvas, paint, cx, cy, u, x - 0.14f, top - 0.7f, 0.28f, 0.7f, GOLD_DK)
            disc(canvas, paint, cx, cy, u, x, top - 0.7f, 0.2f, GOLD)
            // 钟身：分段渐宽
            val steps = 4
            for (s in 0 until steps) {
                val t = top + hgt * s / steps
                val ww = wTop + (wBot - wTop) * (s + 1) / steps
                val tone = when (s) {
                    0 -> BRONZE_LT
                    steps - 1 -> BRONZE_DK
                    else -> BRONZE
                }
                px(canvas, paint, cx, cy, u, x - ww / 2, t, ww, hgt / steps + 0.06f, tone)
            }
            // 左缘受光
            px(canvas, paint, cx, cy, u, x - wBot / 2 + 0.1f, top + hgt * 0.3f, 0.22f, hgt * 0.6f, 0x558EE8C0)
            // 于口：底缘两侧下垂尖角（合瓦口弧线）
            px(canvas, paint, cx, cy, u, x - wBot / 2, top + hgt, wBot * 0.28f, 0.5f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, x + wBot / 2 - wBot * 0.28f, top + hgt, wBot * 0.28f, 0.5f, BRONZE_DK)
            // 篆带（中部横带）
            px(canvas, paint, cx, cy, u, x - wBot / 2 + 0.15f, top + hgt * 0.52f, wBot - 0.3f, 0.32f, GOLD_DK)
            // 枚（乳钉三列两段）
            val cols = intArrayOf(-1, 0, 1)
            for (row in 0..1) {
                for (col in cols) {
                    disc(canvas, paint, cx, cy, u, x + col * wBot * 0.26f, top + hgt * (0.18f + row * 0.18f), 0.16f, GOLD_DK)
                }
            }
            // 铭文（大钟金字）
            if (glyph) {
                px(canvas, paint, cx, cy, u, x - 0.32f, top + hgt * 0.66f, 0.64f, 0.22f, GOLD)
                px(canvas, paint, cx, cy, u, x - 0.12f, top + hgt * 0.6f, 0.24f, 0.7f, GOLD)
                px(canvas, paint, cx, cy, u, x - 0.42f, top + hgt * 0.82f, 0.84f, 0.2f, GOLD)
            }
        }

        // 青铜立柱（两侧承梁）
        fun post(x: Float) {
            px(canvas, paint, cx, cy, u, x - 0.45f, -7.4f, 0.9f, 14.6f, BRONZE)
            px(canvas, paint, cx, cy, u, x - 0.45f, -7.4f, 0.3f, 14.6f, BRONZE_LT)
            px(canvas, paint, cx, cy, u, x + 0.2f, -7.4f, 0.25f, 14.6f, BRONZE_DK)
            // 柱头/柱础
            px(canvas, paint, cx, cy, u, x - 0.7f, -7.6f, 1.4f, 0.5f, BRONZE_LT)
            px(canvas, paint, cx, cy, u, x - 0.9f, 6.9f, 1.8f, 0.6f, BRONZE_DK)
        }

        post(-7.0f)
        post(7.0f)

        // 上层：梁 + 5 枚小甬钟
        beam(-7.2f, 1.3f, 6.4f)
        for (i in 0..4) {
            val x = -5.0f + i * 2.5f
            bell(x, -5.6f, 3.0f + i * 0.12f, 1.1f, 1.7f + i * 0.08f, glyph = false)
        }

        // 下层：梁 + 4 枚大甬钟（右侧更大）
        beam(-1.4f, 1.4f, 6.6f)
        val bigW = floatArrayOf(2.4f, 2.7f, 3.0f, 3.3f)
        val bigH = floatArrayOf(5.2f, 5.6f, 6.0f, 6.4f)
        var bx = -5.2f
        for (i in 0..3) {
            bell(bx, 0.7f, bigH[i], 1.5f, bigW[i], glyph = true)
            bx += bigW[i] * 0.5f + (if (i < 3) bigW[i + 1] * 0.5f else 0f) + 0.65f
        }
    }

    /** 兵马俑：铠甲武士立像 */
    private fun drawTerracotta(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -2.5f, -1f, 5f, 5.2f, CLAY)
            px(canvas, paint, cx, cy, u, -2f, -4.5f, 4f, 3.5f, CLAY)
            return
        }
        // 腿靴
        px(canvas, paint, cx, cy, u, -2.4f, 3.5f, 1.8f, 4f, CLAY_DK)
        px(canvas, paint, cx, cy, u, 0.6f, 3.5f, 1.8f, 4f, CLAY_DK)
        px(canvas, paint, cx, cy, u, -2.6f, 7.0f, 2.2f, 0.8f, 0xFF5A3820.toInt())
        px(canvas, paint, cx, cy, u, 0.4f, 7.0f, 2.2f, 0.8f, 0xFF5A3820.toInt())
        // 战袍下摆
        px(canvas, paint, cx, cy, u, -3.2f, 1.5f, 6.4f, 2.5f, CLAY)
        // 铠甲胸腹（甲片网格）
        px(canvas, paint, cx, cy, u, -3.0f, -2.0f, 6.0f, 4.0f, CLAY_DK)
        for (row in 0..3) {
            for (col in 0..3) {
                px(
                    canvas, paint, cx, cy, u,
                    -2.6f + col * 1.35f, -1.7f + row * 0.9f, 1.1f, 0.7f,
                    if ((row + col) % 2 == 0) CLAY else 0xFFB06838.toInt()
                )
            }
        }
        // 护肩
        px(canvas, paint, cx, cy, u, -4.5f, -2.2f, 1.6f, 2.8f, CLAY_DK)
        px(canvas, paint, cx, cy, u, 2.9f, -2.2f, 1.6f, 2.8f, CLAY_DK)
        // 头（发髻）
        px(canvas, paint, cx, cy, u, -2.2f, -5.8f, 4.4f, 4.0f, CLAY_LT)
        px(canvas, paint, cx, cy, u, -1.8f, -5.4f, 3.6f, 3.4f, CLAY)
        px(canvas, paint, cx, cy, u, -1.2f, -6.8f, 2.4f, 1.4f, CLAY_DK) // 髻
        // 面容
        px(canvas, paint, cx, cy, u, -1.4f, -4.2f, 0.9f, 0.7f, 0xFF2A2018.toInt())
        px(canvas, paint, cx, cy, u, 0.5f, -4.2f, 0.9f, 0.7f, 0xFF2A2018.toInt())
        px(canvas, paint, cx, cy, u, -0.9f, -3.0f, 1.8f, 0.55f, 0xFF2A2018.toInt())
        // 右手执物暗示
        px(canvas, paint, cx, cy, u, 4.0f, -1.5f, 0.7f, 5.5f, 0xFF6A6A60.toInt())
        px(canvas, paint, cx, cy, u, -1.5f, -5.0f, 0.4f, 2.5f, 0x44FFE0C0)
    }

    /** 铜奔马：马踏飞燕——右后蹄踏燕，三足腾空 */
    private fun drawGallopingHorse(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3.2f, -1.2f, 6.0f, 2.8f, BRONZE)
            px(canvas, paint, cx, cy, u, 2.2f, -3.2f, 2.2f, 2.8f, BRONZE)
            px(canvas, paint, cx, cy, u, -1.6f, 1.8f, 0.9f, 3.0f, BRONZE_DK) // 踏燕后蹄
            px(canvas, paint, cx, cy, u, -2.4f, 4.6f, 2.6f, 1.0f, BRONZE_DK) // 燕
            return
        }

        // —— 飞燕（展翅，承右后蹄）——
        px(canvas, paint, cx, cy, u, -2.8f, 6.2f, 3.2f, 1.1f, BRONZE_DK) // 身
        px(canvas, paint, cx, cy, u, -4.6f, 5.5f, 2.0f, 0.95f, BRONZE)    // 左翅
        px(canvas, paint, cx, cy, u, 0.2f, 5.5f, 2.0f, 0.95f, BRONZE)     // 右翅
        px(canvas, paint, cx, cy, u, -5.0f, 5.2f, 0.9f, 0.55f, BRONZE_LT)
        px(canvas, paint, cx, cy, u, 1.4f, 5.2f, 0.9f, 0.55f, BRONZE_LT)
        disc(canvas, paint, cx, cy, u, 0.9f, 6.65f, 0.5f, BRONZE_DK)      // 燕头（朝前）
        px(canvas, paint, cx, cy, u, 1.25f, 6.5f, 0.7f, 0.35f, BRONZE_LT) // 喙

        // —— 四腿：右后踏燕，余三足腾空 ——
        // 右后（支撑）：自臀垂至燕背
        px(canvas, paint, cx, cy, u, -1.8f, 1.0f, 1.05f, 5.4f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -1.6f, 1.0f, 0.35f, 5.0f, BRONZE)
        px(canvas, paint, cx, cy, u, -2.0f, 6.0f, 1.4f, 0.55f, BRONZE_DK) // 蹄
        // 左后：扬起后踢
        px(canvas, paint, cx, cy, u, -4.8f, 0.0f, 1.0f, 2.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -6.0f, 1.4f, 1.9f, 0.85f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -6.6f, 1.7f, 0.9f, 0.55f, BRONZE)     // 蹄尖
        // 右前：屈收于胸下
        px(canvas, paint, cx, cy, u, 1.8f, 0.3f, 0.95f, 2.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 2.4f, 2.4f, 1.6f, 0.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 3.6f, 2.6f, 0.7f, 0.5f, BRONZE)
        // 左前：前伸腾空
        px(canvas, paint, cx, cy, u, 3.4f, -0.3f, 0.95f, 2.1f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 4.1f, 1.4f, 2.3f, 0.85f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 6.0f, 1.5f, 0.75f, 0.55f, BRONZE)

        // —— 躯干（奔姿微前倾）——
        px(canvas, paint, cx, cy, u, -4.8f, -2.2f, 9.0f, 4.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -4.4f, -1.8f, 8.2f, 3.2f, BRONZE)
        px(canvas, paint, cx, cy, u, -4.0f, -1.5f, 7.4f, 1.2f, BRONZE_LT) // 背光
        // 胸肌 / 腹弧
        px(canvas, paint, cx, cy, u, 1.5f, -0.6f, 2.8f, 2.4f, BRONZE)
        px(canvas, paint, cx, cy, u, -3.8f, 0.6f, 5.5f, 1.1f, BRONZE_DK)

        // —— 颈与头（昂首张口）——
        px(canvas, paint, cx, cy, u, 2.8f, -4.8f, 2.0f, 3.6f, BRONZE)
        px(canvas, paint, cx, cy, u, 3.1f, -4.5f, 1.2f, 3.0f, BRONZE_LT)
        // 头骨
        px(canvas, paint, cx, cy, u, 3.6f, -6.4f, 3.4f, 2.4f, BRONZE)
        px(canvas, paint, cx, cy, u, 4.0f, -6.1f, 2.6f, 1.6f, BRONZE_LT)
        // 张口
        px(canvas, paint, cx, cy, u, 6.4f, -5.4f, 1.3f, 0.9f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 6.55f, -5.2f, 0.95f, 0.35f, 0xFF2A2010.toInt())
        // 下颌
        px(canvas, paint, cx, cy, u, 5.2f, -4.4f, 1.8f, 0.7f, BRONZE_DK)
        // 耳
        px(canvas, paint, cx, cy, u, 4.4f, -7.4f, 0.55f, 1.15f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 5.2f, -7.2f, 0.55f, 1.0f, BRONZE_DK)
        // 竖鬃
        px(canvas, paint, cx, cy, u, 2.6f, -5.8f, 0.7f, 2.8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 2.4f, -6.2f, 0.45f, 1.2f, BRONZE)
        disc(canvas, paint, cx, cy, u, 5.5f, -5.6f, 0.32f, 0xFF1A1810.toInt()) // 眼

        // —— 尾：扬起后垂 ——
        px(canvas, paint, cx, cy, u, -5.6f, -1.6f, 1.6f, 0.75f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -6.8f, -1.2f, 1.4f, 0.7f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -7.2f, -0.4f, 0.95f, 2.6f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -7.0f, 1.8f, 1.3f, 0.7f, BRONZE)

        // 铜锈高光
        px(canvas, paint, cx, cy, u, -3.2f, -1.6f, 0.4f, 2.4f, 0x55B8E0C0)
        px(canvas, paint, cx, cy, u, 4.2f, -5.8f, 0.3f, 1.4f, 0x44C8F0D0)
    }

    /** 兰亭序：展开手卷 + 行书墨迹 + 朱印 */
    private fun drawScroll(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        // 天杆地杆
        px(canvas, paint, cx, cy, u, -7.0f, -4.5f, 1.3f, 9f, WOOD)
        px(canvas, paint, cx, cy, u, 5.7f, -4.5f, 1.3f, 9f, WOOD)
        disc(canvas, paint, cx, cy, u, -6.35f, -4.5f, 0.7f, WOOD_LT)
        disc(canvas, paint, cx, cy, u, 6.35f, -4.5f, 0.7f, WOOD_LT)
        disc(canvas, paint, cx, cy, u, -6.35f, 4.5f, 0.7f, WOOD_LT)
        disc(canvas, paint, cx, cy, u, 6.35f, 4.5f, 0.7f, WOOD_LT)
        // 纸面
        px(canvas, paint, cx, cy, u, -5.8f, -3.8f, 11.6f, 7.6f, PAPER)
        px(canvas, paint, cx, cy, u, -5.4f, -3.4f, 10.8f, 6.8f, 0xFFF8F0D8.toInt())
        if (fancy) {
            // 行书起伏笔画（「永和九年」意）
            px(canvas, paint, cx, cy, u, -4.0f, -2.2f, 3.5f, 0.35f, INK)
            px(canvas, paint, cx, cy, u, -3.2f, -1.2f, 0.4f, 2.2f, INK)
            px(canvas, paint, cx, cy, u, -2.5f, -0.5f, 2.8f, 0.3f, INK)
            px(canvas, paint, cx, cy, u, -4.2f, 1.0f, 4.0f, 0.35f, INK)
            px(canvas, paint, cx, cy, u, -3.5f, 2.0f, 0.4f, 1.5f, INK)
            px(canvas, paint, cx, cy, u, 0.5f, -2.0f, 2.5f, 0.3f, INK)
            px(canvas, paint, cx, cy, u, 1.0f, -1.2f, 0.35f, 2.5f, INK)
            px(canvas, paint, cx, cy, u, 0.2f, 0.8f, 3.2f, 0.3f, INK)
            px(canvas, paint, cx, cy, u, 1.5f, 1.8f, 2.0f, 0.3f, INK)
            // 朱文印
            px(canvas, paint, cx, cy, u, 3.2f, 1.8f, 1.8f, 1.8f, 0xFFC04040.toInt())
            px(canvas, paint, cx, cy, u, 3.5f, 2.1f, 1.2f, 1.2f, 0xFFF8F0D8.toInt())
            px(canvas, paint, cx, cy, u, 3.7f, 2.4f, 0.8f, 0.6f, 0xFFC04040.toInt())
            px(canvas, paint, cx, cy, u, -5.0f, -3.0f, 0.4f, 6f, 0x55FFFFFF)
        } else {
            px(canvas, paint, cx, cy, u, -2.2f, -1.6f, 5f, 0.6f, INK)
            px(canvas, paint, cx, cy, u, -1.8f, -0.2f, 3.6f, 0.6f, INK)
            px(canvas, paint, cx, cy, u, -2.4f, 1.2f, 4.6f, 0.6f, INK)
        }
    }

    /** 四羊方尊：方尊四角卷角羊首 */
    private fun drawZun(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3.5f, -2f, 7f, 5.5f, BRONZE)
            px(canvas, paint, cx, cy, u, -2f, -4f, 4f, 2f, BRONZE)
            px(canvas, paint, cx, cy, u, -4.8f, -3.2f, 1.5f, 2.4f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, 3.3f, -3.2f, 1.5f, 2.4f, BRONZE_DK)
            return
        }
        // 圈足
        px(canvas, paint, cx, cy, u, -4.0f, 4.5f, 8.0f, 2.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -3.5f, 4.8f, 7.0f, 1.6f, BRONZE)
        // 方腹
        px(canvas, paint, cx, cy, u, -4.8f, -1.5f, 9.6f, 6.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -4.4f, -1.1f, 8.8f, 5.7f, BRONZE)
        // 颈
        px(canvas, paint, cx, cy, u, -2.8f, -4.5f, 5.6f, 3.5f, BRONZE_LT)
        px(canvas, paint, cx, cy, u, -2.4f, -4.1f, 4.8f, 3.0f, BRONZE)
        // 口沿外侈
        px(canvas, paint, cx, cy, u, -3.5f, -5.5f, 7.0f, 1.2f, BRONZE_LT)
        // 四羊首（可见三角+右侧，角卷曲）
        fun ram(ox: Float, facing: Float) {
            px(canvas, paint, cx, cy, u, ox - 1.2f, -2.5f, 2.4f, 2.8f, BRONZE_DK)
            disc(canvas, paint, cx, cy, u, ox, -1.5f, 1.3f, BRONZE)
            // 卷角
            px(canvas, paint, cx, cy, u, ox - 1.6f * facing, -3.8f, 0.7f, 2.0f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, ox - 2.4f * facing, -4.5f, 1.2f, 0.7f, BRONZE_DK)
            px(canvas, paint, cx, cy, u, ox - 2.6f * facing, -3.8f, 0.7f, 1.2f, BRONZE_DK)
            disc(canvas, paint, cx, cy, u, ox + 0.4f * facing, -1.3f, 0.3f, 0xFF2A2010.toInt())
        }
        ram(-4.5f, 1f)
        ram(4.5f, -1f)
        // 正面羊身暗示
        px(canvas, paint, cx, cy, u, -1.5f, 0.5f, 3.0f, 2.5f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, 0f, 1.0f, 1.4f, GOLD)
        px(canvas, paint, cx, cy, u, -0.7f, 1.5f, 1.4f, 0.8f, GOLD_LT)
        // 扉棱
        px(canvas, paint, cx, cy, u, -0.3f, -4.0f, 0.6f, 8f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -3.8f, -0.8f, 0.4f, 4f, 0x55B8E0C0)
    }

    /** 金缕玉衣：人形玉片金缕 */
    private fun drawJadeSuit(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3f, -5f, 6f, 10f, JADE)
            return
        }
        // 头罩
        px(canvas, paint, cx, cy, u, -2.8f, -7.2f, 5.6f, 3.2f, JADE_DK)
        px(canvas, paint, cx, cy, u, -2.4f, -6.8f, 4.8f, 2.6f, JADE)
        // 面罩五官位
        px(canvas, paint, cx, cy, u, -1.5f, -5.8f, 0.9f, 0.6f, JADE_DK)
        px(canvas, paint, cx, cy, u, 0.6f, -5.8f, 0.9f, 0.6f, JADE_DK)
        px(canvas, paint, cx, cy, u, -0.7f, -4.8f, 1.4f, 0.45f, JADE_DK)
        // 躯干玉片
        px(canvas, paint, cx, cy, u, -3.5f, -4.0f, 7.0f, 7.5f, JADE_DK)
        for (row in -3..3) {
            for (col in -2..2) {
                val c = if ((row + col) % 2 == 0) JADE else JADE_LT
                px(canvas, paint, cx, cy, u, col * 1.25f - 0.45f, row * 0.95f - 0.45f, 0.9f, 0.8f, c)
                // 金缕十字钉
                if (fancy && (row + col) % 2 == 0) {
                    px(canvas, paint, cx, cy, u, col * 1.25f - 0.12f, row * 0.95f - 0.12f, 0.24f, 0.24f, GOLD_LT)
                }
            }
        }
        // 袖
        px(canvas, paint, cx, cy, u, -5.5f, -3.0f, 2.0f, 4.5f, JADE)
        px(canvas, paint, cx, cy, u, 3.5f, -3.0f, 2.0f, 4.5f, JADE)
        for (i in 0..3) {
            px(canvas, paint, cx, cy, u, -5.3f, -2.7f + i * 1.0f, 1.6f, 0.7f, if (i % 2 == 0) JADE_LT else JADE)
            px(canvas, paint, cx, cy, u, 3.7f, -2.7f + i * 1.0f, 1.6f, 0.7f, if (i % 2 == 0) JADE_LT else JADE)
        }
        // 裤筒
        px(canvas, paint, cx, cy, u, -3.0f, 3.5f, 2.4f, 3.8f, JADE)
        px(canvas, paint, cx, cy, u, 0.6f, 3.5f, 2.4f, 3.8f, JADE)
        // 金缕边线
        px(canvas, paint, cx, cy, u, -3.5f, -4.0f, 7.0f, 0.25f, GOLD)
        px(canvas, paint, cx, cy, u, -3.5f, 3.2f, 7.0f, 0.25f, GOLD)
    }

    /** 清明上河图：长卷 + 虹桥舟船屋宇 */
    private fun drawQingming(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        px(canvas, paint, cx, cy, u, -7.5f, -4.0f, 15f, 8f, 0xFFE8DCC0.toInt())
        px(canvas, paint, cx, cy, u, -7.0f, -3.5f, 14f, 7f, PAPER)
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -5.2f, 1.6f, 10.4f, 1f, 0xFF6A8A5A.toInt())
            px(canvas, paint, cx, cy, u, -3.2f, -1.6f, 2.4f, 3.2f, 0xFF8A6040.toInt())
            return
        }
        // 河水
        px(canvas, paint, cx, cy, u, -6.5f, 1.2f, 13f, 2.2f, 0xFFA8C8D8.toInt())
        px(canvas, paint, cx, cy, u, -6.5f, 2.0f, 13f, 0.35f, 0xFF88B0C8.toInt())
        // 虹桥
        px(canvas, paint, cx, cy, u, -2.5f, -0.8f, 5.0f, 0.6f, 0xFF8A6040.toInt())
        px(canvas, paint, cx, cy, u, -3.0f, -0.3f, 1.0f, 2.0f, 0xFF6A4830.toInt())
        px(canvas, paint, cx, cy, u, 2.0f, -0.3f, 1.0f, 2.0f, 0xFF6A4830.toInt())
        px(canvas, paint, cx, cy, u, -1.5f, -1.5f, 3.0f, 0.5f, 0xFFA07850.toInt()) // 桥拱顶
        // 船
        px(canvas, paint, cx, cy, u, -5.5f, 1.8f, 2.8f, 0.9f, 0xFF6A4830.toInt())
        px(canvas, paint, cx, cy, u, -4.8f, 1.0f, 1.4f, 1.0f, 0xFFC07040.toInt()) // 篷
        px(canvas, paint, cx, cy, u, 3.5f, 2.0f, 2.2f, 0.7f, 0xFF6A4830.toInt())
        // 城楼屋宇
        px(canvas, paint, cx, cy, u, -6.0f, -2.8f, 2.5f, 3.2f, 0xFF8A6040.toInt())
        px(canvas, paint, cx, cy, u, -6.3f, -3.3f, 3.1f, 0.7f, 0xFFC04040.toInt()) // 屋顶
        px(canvas, paint, cx, cy, u, -2.0f, -2.5f, 2.0f, 2.5f, 0xFFA07850.toInt())
        px(canvas, paint, cx, cy, u, -2.2f, -3.0f, 2.4f, 0.6f, 0xFF805030.toInt())
        px(canvas, paint, cx, cy, u, 3.0f, -2.2f, 3.0f, 2.8f, 0xFF8A6040.toInt())
        px(canvas, paint, cx, cy, u, 2.7f, -2.8f, 3.6f, 0.7f, 0xFF5080B0.toInt())
        // 柳树
        disc(canvas, paint, cx, cy, u, 1.0f, -2.0f, 1.3f, 0xFF6A8A5A.toInt())
        px(canvas, paint, cx, cy, u, 0.8f, -1.0f, 0.4f, 2.0f, 0xFF6A4830.toInt())
        // 点景小人
        for (i in 0..4) {
            px(canvas, paint, cx, cy, u, -4f + i * 2.2f, 0.2f, 0.4f, 0.9f, 0xFF4A3020.toInt())
        }
        px(canvas, paint, cx, cy, u, -6.5f, -3.0f, 0.4f, 5.5f, 0x44FFFFFF)
    }

    /** 敦煌飞天：飘带舞姿 */
    private fun drawApsara(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            disc(canvas, paint, cx, cy, u, 0f, -2.8f, 1.8f, 0xFFE8A0C0.toInt())
            px(canvas, paint, cx, cy, u, -1.7f, -1.2f, 3.4f, 4.4f, 0xFFD06090.toInt())
            px(canvas, paint, cx, cy, u, -5.5f, -0.6f, 4f, 1f, 0xFFFFD426.toInt())
            return
        }
        // 飘带（上下翻飞）
        px(canvas, paint, cx, cy, u, -7.0f, -3.5f, 4.5f, 0.7f, 0xFFFFD426.toInt())
        px(canvas, paint, cx, cy, u, -7.2f, -2.5f, 0.7f, 3.5f, 0xFFFFE878.toInt())
        px(canvas, paint, cx, cy, u, -7.0f, 1.0f, 3.5f, 0.7f, 0xFFFFD426.toInt())
        px(canvas, paint, cx, cy, u, 2.5f, -4.5f, 4.5f, 0.7f, 0xFFFFD426.toInt())
        px(canvas, paint, cx, cy, u, 6.3f, -4.5f, 0.7f, 4.0f, 0xFFFFE878.toInt())
        px(canvas, paint, cx, cy, u, 3.5f, -0.5f, 3.5f, 0.7f, 0xFFFFD426.toInt())
        px(canvas, paint, cx, cy, u, -6.0f, 2.5f, 4.0f, 0.7f, 0xFFFFB040.toInt())
        px(canvas, paint, cx, cy, u, 2.0f, 3.0f, 4.5f, 0.7f, 0xFFFFB040.toInt())
        // 身姿侧飞
        px(canvas, paint, cx, cy, u, -1.8f, -1.5f, 3.6f, 5.0f, 0xFFD06090.toInt())
        px(canvas, paint, cx, cy, u, -1.4f, -1.1f, 2.8f, 4.2f, 0xFFE080A8.toInt())
        // 头与发髻
        disc(canvas, paint, cx, cy, u, 0.3f, -3.5f, 1.9f, 0xFFF0C0D8.toInt())
        disc(canvas, paint, cx, cy, u, -0.2f, -3.8f, 0.7f, 0xFFE8A0C0.toInt())
        px(canvas, paint, cx, cy, u, -0.5f, -5.2f, 1.8f, 1.0f, 0xFF805070.toInt()) // 髻
        // 宝冠
        px(canvas, paint, cx, cy, u, -0.8f, -5.5f, 2.4f, 0.55f, GOLD)
        disc(canvas, paint, cx, cy, u, 0.4f, -5.7f, 0.4f, GOLD_LT)
        // 面容
        px(canvas, paint, cx, cy, u, -0.5f, -3.6f, 0.55f, 0.45f, 0xFFB04070.toInt())
        px(canvas, paint, cx, cy, u, 0.7f, -3.6f, 0.55f, 0.45f, 0xFFB04070.toInt())
        // 手臂前伸
        px(canvas, paint, cx, cy, u, 1.5f, -1.5f, 3.0f, 0.7f, 0xFFF0C0D8.toInt())
        px(canvas, paint, cx, cy, u, -4.0f, 0.5f, 2.5f, 0.7f, 0xFFF0C0D8.toInt())
        // 莲花座暗示
        disc(canvas, paint, cx, cy, u, 0f, 4.5f, 1.5f, 0xFFFF8090.toInt())
        px(canvas, paint, cx, cy, u, -2.0f, 4.0f, 4.0f, 0.6f, 0xFFFFA0B0.toInt())
    }

    /** 玉琮：外方内圆 + 节带神人兽面 */
    private fun drawCong(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -4f, -4.5f, 8f, 9f, JADE)
            disc(canvas, paint, cx, cy, u, 0f, 0f, 1.3f, 0xFF1A2030.toInt())
            return
        }
        px(canvas, paint, cx, cy, u, -5.5f, -7.0f, 11f, 14f, JADE_DK)
        px(canvas, paint, cx, cy, u, -5.0f, -6.5f, 10f, 13f, JADE)
        // 射口（上下圆凸）
        disc(canvas, paint, cx, cy, u, 0f, -6.5f, 2.2f, JADE_LT)
        disc(canvas, paint, cx, cy, u, 0f, 6.5f, 2.2f, JADE_LT)
        // 中孔
        disc(canvas, paint, cx, cy, u, 0f, 0f, 2.8f, JADE_DK)
        disc(canvas, paint, cx, cy, u, 0f, 0f, 1.6f, 0xFF1A2030.toInt())
        // 分节横线 + 角部神人兽面
        for (i in -2..2) {
            px(canvas, paint, cx, cy, u, -5.0f, i * 2.2f - 0.2f, 10f, 0.4f, JADE_DK)
        }
        for (corner in arrayOf(-4.0f to -4.5f, 2.5f to -4.5f, -4.0f to 2.5f, 2.5f to 2.5f)) {
            val (ox, oy) = corner
            px(canvas, paint, cx, cy, u, ox, oy, 1.5f, 0.35f, JADE_DK) // 眼
            px(canvas, paint, cx, cy, u, ox + 0.9f, oy, 0.5f, 0.35f, JADE_DK)
            px(canvas, paint, cx, cy, u, ox + 0.3f, oy + 0.7f, 1.0f, 0.35f, JADE_DK) // 鼻梁
            disc(canvas, paint, cx, cy, u, ox + 0.35f, oy + 0.15f, 0.28f, 0xFF1A4038.toInt())
            disc(canvas, paint, cx, cy, u, ox + 1.15f, oy + 0.15f, 0.28f, 0xFF1A4038.toInt())
        }
        px(canvas, paint, cx, cy, u, -4.3f, -5.5f, 0.4f, 11f, 0x66D0FFF8)
    }

    /** 铜镜：圆镜钮座 + 神兽铭带 */
    private fun drawMirror(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        disc(canvas, paint, cx, cy, u, 0f, 0f, if (fancy) 7.2f else 5.5f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, 0f, 0f, if (fancy) 6.6f else 5f, BRONZE)
        if (!fancy) {
            disc(canvas, paint, cx, cy, u, 0f, 0f, 3.8f, 0xFFD0D8D0.toInt())
            disc(canvas, paint, cx, cy, u, 0f, 0f, 0.6f, BRONZE_LT)
            return
        }
        // 镜缘
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.5f
        paint.color = BRONZE_DK
        canvas.drawCircle(cx, cy, u * 6.3f, paint)
        paint.style = Paint.Style.FILL
        // 铭文圈
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.3f
        paint.color = BRONZE_LT
        canvas.drawCircle(cx, cy, u * 5.2f, paint)
        paint.style = Paint.Style.FILL
        // 简化铭文字块
        for (i in 0 until 8) {
            val a = i * (Math.PI * 2 / 8).toFloat()
            val mx = cos(a) * 5.2f
            val my = sin(a) * 5.2f
            px(canvas, paint, cx, cy, u, mx - 0.35f, my - 0.35f, 0.7f, 0.7f, BRONZE_DK)
        }
        // 内区神兽纹
        px(canvas, paint, cx, cy, u, -0.35f, -3.5f, 0.7f, 2.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -2.5f, -2.8f, 1.2f, 1.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 1.3f, -2.8f, 1.2f, 1.2f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -3.0f, 0.5f, 1.5f, 1.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 1.5f, 0.5f, 1.5f, 1.0f, BRONZE_DK)
        // 钮
        disc(canvas, paint, cx, cy, u, 0f, 0f, 1.5f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, 0f, 0f, 0.85f, BRONZE_LT)
        disc(canvas, paint, cx, cy, u, 0f, 0f, 0.35f, 0xFF1A2030.toInt())
        // 半边镜面反光暗示
        disc(canvas, paint, cx, cy, u, -1.5f, -1.2f, 1.8f, 0x44E8F0E8)
    }

    /** 算盘：框梁档珠 */
    private fun drawAbacus(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        px(canvas, paint, cx, cy, u, -7.0f, -5.5f, 14f, 11f, WOOD)
        px(canvas, paint, cx, cy, u, -6.4f, -4.9f, 12.8f, 9.8f, WOOD_LT)
        // 梁
        px(canvas, paint, cx, cy, u, -6.2f, -0.55f, 12.4f, 1.1f, WOOD)
        if (fancy) px(canvas, paint, cx, cy, u, -6.2f, -0.35f, 12.4f, 0.35f, 0xFF6A4020.toInt())
        val cols = if (fancy) 7 else 7
        for (i in 0 until cols) {
            val x = -5.0f + i * 1.55f
            // 档
            px(canvas, paint, cx, cy, u, x - 0.12f, -4.5f, 0.24f, 8.5f, 0xFF5A3820.toInt())
            // 上珠（一颗）
            disc(canvas, paint, cx, cy, u, x, -2.8f, 0.7f, 0xFFC03030.toInt())
            if (fancy) disc(canvas, paint, cx, cy, u, x - 0.2f, -3.0f, 0.25f, 0xFFE06060.toInt())
            // 下珠（两/四颗示意）
            disc(canvas, paint, cx, cy, u, x, 1.5f, 0.7f, 0xFFC03030.toInt())
            disc(canvas, paint, cx, cy, u, x, 3.2f, 0.7f, 0xFFC03030.toInt())
            if (fancy && i % 2 == 0) {
                disc(canvas, paint, cx, cy, u, x, 4.6f, 0.65f, 0xFFA02828.toInt())
            }
            if (fancy) {
                disc(canvas, paint, cx, cy, u, x - 0.2f, 1.3f, 0.22f, 0xFFE06060.toInt())
            }
        }
    }

    /** 司南：方形地盘 + 勺形磁体 */
    private fun drawSinan(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        // 地盘
        px(canvas, paint, cx, cy, u, -6.5f, 0.5f, 13f, 6.5f, 0xFFB0A888.toInt())
        px(canvas, paint, cx, cy, u, -6.0f, 1.0f, 12f, 5.5f, 0xFFD0C8B0.toInt())
        if (fancy) {
            // 地盘刻度
            for (i in -2..2) {
                px(canvas, paint, cx, cy, u, i * 2.2f - 0.2f, 1.3f, 0.4f, 0.8f, 0xFF8A8070.toInt())
                px(canvas, paint, cx, cy, u, i * 2.2f - 0.2f, 5.2f, 0.4f, 0.8f, 0xFF8A8070.toInt())
            }
            px(canvas, paint, cx, cy, u, -5.2f, 3.5f, 10.4f, 0.35f, 0xFF8A8070.toInt())
            // 中心圆
            disc(canvas, paint, cx, cy, u, 0f, 3.5f, 2.0f, 0xFFC0B898.toInt())
            disc(canvas, paint, cx, cy, u, 0f, 3.5f, 0.5f, 0xFF8A8070.toInt())
        }
        // 勺（柄指南，勺头向北——画面勺头在左、柄向右）
        disc(canvas, paint, cx, cy, u, -2.0f, -2.0f, if (fancy) 3.0f else 2.5f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, -2.0f, -1.8f, if (fancy) 2.6f else 2.2f, BRONZE)
        px(canvas, paint, cx, cy, u, -0.5f, -2.4f, if (fancy) 6.5f else 5f, 1.4f, BRONZE_LT)
        if (fancy) {
            // 勺内凹
            disc(canvas, paint, cx, cy, u, -2.2f, -2.2f, 1.5f, BRONZE_DK)
            disc(canvas, paint, cx, cy, u, -2.0f, -1.8f, 0.7f, GOLD)
            // 柄端
            px(canvas, paint, cx, cy, u, 5.5f, -2.6f, 1.2f, 1.8f, BRONZE)
            px(canvas, paint, cx, cy, u, -4.5f, 2.8f, 4f, 0.4f, 0xFF8A8070.toInt())
        }
    }

    /** 长信宫灯：跪姿宫女捧灯 */
    private fun drawPalaceLamp(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -2f, -1f, 4f, 5.5f, GOLD)
            px(canvas, paint, cx, cy, u, 3.4f, -4.4f, 1.8f, 4f, GOLD)
            return
        }
        // 底座
        px(canvas, paint, cx, cy, u, -3.5f, 5.5f, 7.0f, 1.5f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -3.0f, 5.8f, 6.0f, 1.0f, GOLD)
        // 跪姿腿
        px(canvas, paint, cx, cy, u, -3.2f, 2.5f, 5.5f, 3.5f, GOLD)
        px(canvas, paint, cx, cy, u, -2.8f, 2.8f, 4.7f, 2.8f, GOLD_LT)
        // 身躯
        px(canvas, paint, cx, cy, u, -2.5f, -1.5f, 5.0f, 4.5f, GOLD)
        px(canvas, paint, cx, cy, u, -2.1f, -1.1f, 4.2f, 3.8f, GOLD_LT)
        // 头与发髻
        disc(canvas, paint, cx, cy, u, 0f, -3.5f, 2.0f, GOLD_LT)
        px(canvas, paint, cx, cy, u, -1.2f, -5.2f, 2.4f, 1.5f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -0.9f, -3.8f, 0.7f, 0.55f, 0xFF2A2010.toInt())
        px(canvas, paint, cx, cy, u, 0.2f, -3.8f, 0.7f, 0.55f, 0xFF2A2010.toInt())
        // 右臂前伸托灯
        px(canvas, paint, cx, cy, u, 2.0f, -1.5f, 3.5f, 1.0f, GOLD)
        // 灯罩（袖中藏灯）
        px(canvas, paint, cx, cy, u, 4.0f, -4.0f, 2.8f, 4.5f, GOLD_DK)
        px(canvas, paint, cx, cy, u, 4.4f, -3.5f, 2.0f, 3.5f, 0xFFFFF0A0.toInt())
        paint.color = withAlpha(0xFFFFF8C0.toInt(), 140)
        canvas.drawCircle(cx + u * 5.4f, cy - u * 1.8f, u * 2.2f, paint)
        // 左手扶灯
        px(canvas, paint, cx, cy, u, 3.5f, 0.5f, 1.5f, 0.8f, GOLD_LT)
        // 袍纹
        px(canvas, paint, cx, cy, u, -1.5f, 0.5f, 3.0f, 0.35f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -1.8f, -3.0f, 0.35f, 4f, 0x55FFE8A0)
    }

    /** 马王堆帛画：T形升仙图 */
    private fun drawSilkPainting(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3.5f, -5f, 7f, 10f, 0xFFD4B070.toInt())
            px(canvas, paint, cx, cy, u, -4.5f, -5.2f, 9f, 1.7f, 0xFFE06060.toInt())
            return
        }
        // T形：横幅天界 + 竖幅人界
        px(canvas, paint, cx, cy, u, -7.0f, -7.0f, 14f, 4.5f, 0xFFC04040.toInt())
        px(canvas, paint, cx, cy, u, -6.5f, -6.5f, 13f, 3.7f, 0xFFE07060.toInt())
        px(canvas, paint, cx, cy, u, -4.0f, -3.0f, 8.0f, 10.5f, 0xFFC4A060.toInt())
        px(canvas, paint, cx, cy, u, -3.5f, -2.5f, 7.0f, 9.5f, 0xFFD4B070.toInt())
        // 天界：日（金乌）月（蟾蜍）
        disc(canvas, paint, cx, cy, u, -4.0f, -5.0f, 1.3f, 0xFFFFD040.toInt())
        disc(canvas, paint, cx, cy, u, -4.0f, -5.0f, 0.5f, 0xFF805020.toInt())
        disc(canvas, paint, cx, cy, u, 4.0f, -5.0f, 1.3f, 0xFFE8F0FF.toInt())
        disc(canvas, paint, cx, cy, u, 4.0f, -5.0f, 0.45f, 0xFF80A080.toInt())
        // 人首蛇身神
        disc(canvas, paint, cx, cy, u, 0f, -5.2f, 1.1f, 0xFFF0D0A0.toInt())
        px(canvas, paint, cx, cy, u, -0.5f, -4.2f, 1.0f, 1.5f, 0xFF60A060.toInt())
        // 龙
        px(canvas, paint, cx, cy, u, -6.0f, -4.0f, 3.5f, 0.7f, 0xFF4080C0.toInt())
        px(canvas, paint, cx, cy, u, 2.5f, -4.0f, 3.5f, 0.7f, 0xFF4080C0.toInt())
        // 中部墓主与侍从
        disc(canvas, paint, cx, cy, u, 0f, -0.5f, 1.4f, 0xFFF0D0A0.toInt())
        px(canvas, paint, cx, cy, u, -1.5f, 0.8f, 3.0f, 3.5f, 0xFFC04040.toInt())
        px(canvas, paint, cx, cy, u, -3.0f, 1.5f, 1.2f, 2.5f, 0xFF60A060.toInt())
        px(canvas, paint, cx, cy, u, 1.8f, 1.5f, 1.2f, 2.5f, 0xFF60A060.toInt())
        // 下部祭祀/龙穿璧
        disc(canvas, paint, cx, cy, u, 0f, 5.5f, 1.6f, 0xFF4080C0.toInt())
        disc(canvas, paint, cx, cy, u, 0f, 5.5f, 0.7f, 0xFFD4B070.toInt())
        px(canvas, paint, cx, cy, u, -2.5f, 5.0f, 2.0f, 0.6f, 0xFF805030.toInt())
        px(canvas, paint, cx, cy, u, 0.5f, 5.0f, 2.0f, 0.6f, 0xFF805030.toInt())
        px(canvas, paint, cx, cy, u, -0.8f, -6.0f, 1.6f, 0.5f, GOLD)
    }

    /** 三星堆面具：纵目巨耳 */
    private fun drawMask(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -4f, -4.5f, 8f, 8.5f, BRONZE)
            px(canvas, paint, cx, cy, u, -2.8f, -2.2f, 1.1f, 1.2f, 0xFF1A1020.toInt())
            px(canvas, paint, cx, cy, u, 1.7f, -2.2f, 1.1f, 1.2f, 0xFF1A1020.toInt())
            return
        }
        // 面庞
        px(canvas, paint, cx, cy, u, -5.5f, -5.5f, 11f, 11f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -5.0f, -5.0f, 10f, 10f, BRONZE)
        // 额饰
        px(canvas, paint, cx, cy, u, -2.5f, -6.5f, 5.0f, 1.5f, GOLD)
        px(canvas, paint, cx, cy, u, -0.5f, -7.2f, 1.0f, 1.2f, GOLD_LT)
        // 纵目（外凸圆柱眼）
        px(canvas, paint, cx, cy, u, -4.5f, -2.8f, 3.2f, 2.4f, GOLD_DK)
        px(canvas, paint, cx, cy, u, 1.3f, -2.8f, 3.2f, 2.4f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -4.0f, -2.4f, 2.2f, 1.6f, GOLD)
        px(canvas, paint, cx, cy, u, 1.8f, -2.4f, 2.2f, 1.6f, GOLD)
        // 瞳孔深凹
        px(canvas, paint, cx, cy, u, -3.5f, -2.0f, 1.2f, 0.9f, 0xFF1A1020.toInt())
        px(canvas, paint, cx, cy, u, 2.3f, -2.0f, 1.2f, 0.9f, 0xFF1A1020.toInt())
        // 棱柱眼柄外伸
        px(canvas, paint, cx, cy, u, -5.5f, -2.0f, 1.2f, 0.8f, GOLD_DK)
        px(canvas, paint, cx, cy, u, 4.3f, -2.0f, 1.2f, 0.8f, GOLD_DK)
        // 巨耳
        px(canvas, paint, cx, cy, u, -7.5f, -3.0f, 2.2f, 5.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 5.3f, -3.0f, 2.2f, 5.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -7.1f, -2.5f, 1.4f, 4.5f, BRONZE)
        px(canvas, paint, cx, cy, u, 5.7f, -2.5f, 1.4f, 4.5f, BRONZE)
        // 阔鼻阔口
        px(canvas, paint, cx, cy, u, -1.2f, 0.2f, 2.4f, 1.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -2.8f, 2.2f, 5.6f, 1.8f, GOLD_DK)
        px(canvas, paint, cx, cy, u, -2.3f, 2.6f, 4.6f, 1.0f, 0xFF1A1020.toInt())
        // 高光
        px(canvas, paint, cx, cy, u, -4.3f, -4.5f, 0.4f, 7f, 0x55B8E0C0)
    }

    /** 莲鹤方壶：方壶 + 盖顶展翅鹤 */
    private fun drawLotusCrane(canvas: Canvas, paint: Paint, cx: Float, cy: Float, u: Float, fancy: Boolean) {
        if (!fancy) {
            px(canvas, paint, cx, cy, u, -3f, 0f, 6f, 4.5f, BRONZE)
            px(canvas, paint, cx, cy, u, -0.7f, -5.5f, 1.4f, 3.5f, WHITE)
            return
        }
        // 圈足
        px(canvas, paint, cx, cy, u, -4.0f, 5.5f, 8.0f, 2.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -3.5f, 5.8f, 7.0f, 1.4f, BRONZE)
        // 方腹
        px(canvas, paint, cx, cy, u, -4.5f, 0.0f, 9.0f, 6.0f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, -4.1f, 0.4f, 8.2f, 5.2f, BRONZE)
        // 双耳（龙形简化）
        px(canvas, paint, cx, cy, u, -6.0f, 0.5f, 1.8f, 3.5f, BRONZE_DK)
        px(canvas, paint, cx, cy, u, 4.2f, 0.5f, 1.8f, 3.5f, BRONZE_DK)
        disc(canvas, paint, cx, cy, u, -5.5f, 0.3f, 0.8f, BRONZE)
        disc(canvas, paint, cx, cy, u, 5.5f, 0.3f, 0.8f, BRONZE)
        // 颈
        px(canvas, paint, cx, cy, u, -2.5f, -3.0f, 5.0f, 3.5f, BRONZE_LT)
        px(canvas, paint, cx, cy, u, -2.1f, -2.6f, 4.2f, 2.8f, BRONZE)
        // 莲瓣盖
        for (i in -3..3) {
            val lx = i * 1.15f
            px(canvas, paint, cx, cy, u, lx - 0.5f, -4.2f, 1.0f, 1.8f, BRONZE_LT)
            px(canvas, paint, cx, cy, u, lx - 0.35f, -4.0f, 0.7f, 1.4f, 0xFF8AB898.toInt())
        }
        // 仙鹤立盖顶，展翅
        px(canvas, paint, cx, cy, u, -0.7f, -7.5f, 1.4f, 3.5f, WHITE) // 颈身
        disc(canvas, paint, cx, cy, u, 0f, -7.8f, 0.9f, WHITE) // 头
        px(canvas, paint, cx, cy, u, 0.7f, -8.0f, 1.5f, 0.45f, 0xFFE04040.toInt()) // 喙
        px(canvas, paint, cx, cy, u, -0.35f, -8.0f, 0.35f, 0.35f, 0xFF2A2A2A.toInt()) // 眼
        // 展翅
        px(canvas, paint, cx, cy, u, -4.5f, -6.0f, 3.8f, 1.3f, 0xFFE8E8E8.toInt())
        px(canvas, paint, cx, cy, u, 0.7f, -6.0f, 3.8f, 1.3f, 0xFFE8E8E8.toInt())
        px(canvas, paint, cx, cy, u, -4.2f, -5.7f, 3.2f, 0.7f, WHITE)
        px(canvas, paint, cx, cy, u, 1.0f, -5.7f, 3.2f, 0.7f, WHITE)
        // 腹饰
        px(canvas, paint, cx, cy, u, -1.5f, 2.0f, 3.0f, 1.5f, GOLD)
        px(canvas, paint, cx, cy, u, -1.0f, 2.4f, 2.0f, 0.7f, GOLD_LT)
        px(canvas, paint, cx, cy, u, -3.5f, 0.8f, 0.4f, 4f, 0x55B8E0C0)
    }
}

