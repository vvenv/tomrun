package com.vvenv.tomrun

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 像素/体素风 3D 渲染：第三人称跟随相机，全方块世界 + 距离雾 + 软阴影。
 */
class GameRenderer(private val game: Game) : GLSurfaceView.Renderer {

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uFog = 0
    private var uMode = 0

    private lateinit var cube: Mesh

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)
    private val stack = ArrayList<FloatArray>()

    private var lastNanos = 0L
    private var camX = 0f
    private var mMode = 0
    private var aspect = 1.6f
    private var powerFxPhase = 0f
    private val projectTmp = FloatArray(2)

    /** 横屏参考宽高比：竖屏时用它把垂直 FOV 换算成「同等水平视野」，保证三道刚好入镜。 */
    private val REF_ASPECT = 1.6f

    // 天气 + 昼夜混合后的场景配色
    private val skyCol = FloatArray(4) { 1f }
    private val grassCol = FloatArray(4) { 1f }
    private val grassDarkCol = FloatArray(4) { 1f }
    private val roadCol = FloatArray(4) { 1f }
    private val edgeCol = FloatArray(4) { 1f }
    private val cloudCol = FloatArray(4) { 1f }
    private var wSun = 1f
    private var wRain = 0f
    private var wSnow = 0f
    private var nightAmt = 0f
    private var duskAmt = 0f

    // 平行宇宙调色板混合
    private val palPrev = Array(6) { FloatArray(4) { 1f } }
    private val palCur = Array(6) { FloatArray(4) { 1f } }
    private var meadowW = 1f          // 草原世界权重（含过渡）
    private var scenePhase = 0f       // 与速度无关的场景动画时钟

    // 宇宙环境粒子（气泡 / 火星 / 糖屑 / 星尘）
    private val arand = java.util.Random(7)
    private val ambX = FloatArray(70) { arand.nextFloat() * 24f - 12f }
    private val ambY = FloatArray(70) { arand.nextFloat() * 12f }
    private val ambZ = FloatArray(70) { arand.nextFloat() * 50f - 45f }
    private val ambSeed = FloatArray(70) { arand.nextFloat() * 6.28f }

    // 雨 / 雪粒子
    private val prand = java.util.Random(42)
    private val rainX = FloatArray(110) { prand.nextFloat() * 26f - 13f }
    private val rainY = FloatArray(110) { prand.nextFloat() * 15f }
    private val rainZ = FloatArray(110) { prand.nextFloat() * 46f - 40f }
    private val snowX = FloatArray(100) { prand.nextFloat() * 26f - 13f }
    private val snowY = FloatArray(100) { prand.nextFloat() * 15f }
    private val snowZ = FloatArray(100) { prand.nextFloat() * 46f - 40f }
    private val snowSeed = FloatArray(100) { prand.nextFloat() * 6.28f }
    private var snowPhase = 0f

    // 速度线池
    private val lineX = FloatArray(28) { prand.nextFloat() * 10f - 5f }
    private val lineY = FloatArray(28) { 0.4f + prand.nextFloat() * 3.5f }
    private val lineZ = FloatArray(28) { prand.nextFloat() * 30f - 28f }

    // 双脚光迹历史采样（沿猫的真实轨迹）
    private val trailX = FloatArray(24)
    private val trailY = FloatArray(24)
    private val trailZ = FloatArray(24)
    private val trailAge = FloatArray(24)
    private val trailSeed = FloatArray(24)
    private var trailCount = 0
    private var trailEmit = 0f

    companion object {
        private const val VSH = """
            attribute vec3 aPos;
            attribute vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            varying vec3 vNormal;
            varying vec3 vLocal;
            varying float vDist;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vNormal = mat3(uModel) * aNormal;
                vLocal = aPos;
                vDist = gl_Position.w;
            }
        """

        private const val FSH = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uFogColor;
            uniform int uMode;        // 0 常规光照 1 软阴影 2 无光照 3 纯自发光(无雾)
            varying vec3 vNormal;
            varying vec3 vLocal;
            varying float vDist;
            void main() {
                // 雾起始推远：障碍物需更早可读；高速时 55 起雾会像"忽然冒出"
                float fog = smoothstep(95.0, 200.0, vDist) * 0.78;
                if (uMode == 1) {
                    float r = length(vLocal.xz) * 2.0;
                    float a = uColor.a * smoothstep(1.0, 0.30, r);
                    gl_FragColor = vec4(uColor.rgb, a);
                    return;
                }
                if (uMode == 3) {
                    // 金币等拾取物：不吃雾、不吃光照，保证夜景也够亮
                    gl_FragColor = uColor;
                    return;
                }
                if (uMode == 2) {
                    gl_FragColor = vec4(mix(uColor.rgb, uFogColor, fog), uColor.a);
                    return;
                }
                vec3 n = normalize(vNormal);
                vec3 l = normalize(vec3(0.35, 0.85, 0.45));
                float d = max(dot(n, l), 0.0);
                vec3 c = uColor.rgb * (0.52 + 0.14 * n.y + 0.44 * d);
                c = mix(c, uFogColor, fog);
                gl_FragColor = vec4(c, uColor.a);
            }
        """

        private val SKY = floatArrayOf(0.42f, 0.80f, 0.95f, 1f)
        private val ROAD = floatArrayOf(0.42f, 0.40f, 0.44f, 1f)
        private val ROAD_EDGE = floatArrayOf(0.78f, 0.76f, 0.70f, 1f)
        private val DASH = floatArrayOf(0.96f, 0.95f, 0.90f, 1f)
        private val GRASS = floatArrayOf(0.34f, 0.74f, 0.28f, 1f)
        private val GRASS_DARK = floatArrayOf(0.27f, 0.63f, 0.23f, 1f)
        private val TREE_LEAF = floatArrayOf(0.16f, 0.62f, 0.22f, 1f)
        private val TREE_LEAF2 = floatArrayOf(0.22f, 0.70f, 0.26f, 1f)
        private val TREE_TRUNK = floatArrayOf(0.48f, 0.30f, 0.14f, 1f)
        private val BUSH = floatArrayOf(0.24f, 0.60f, 0.26f, 1f)
        private val ROCK = floatArrayOf(0.60f, 0.59f, 0.56f, 1f)
        private val METAL = floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
        // 金币：对齐 HUD 亮黄（#FFD54A），纯自发光；半透明叠暗路会变土黄，故本体不透明
        private val GOLD = floatArrayOf(1.0f, 0.84f, 0.29f, 1f)
        private val GOLD_RIM = floatArrayOf(1.0f, 0.70f, 0.08f, 1f)
        private val GOLD_CORE = floatArrayOf(1.0f, 0.98f, 0.75f, 1f)
        private val GOLD_GLOW = floatArrayOf(1.0f, 0.88f, 0.25f, 0.85f)
        // 文物：按稀有度上色（青铜 / 青玉 / 鎏金），底座与光核共用
        private val RELIC_BODY = arrayOf(
            floatArrayOf(0.62f, 0.42f, 0.22f, 1f), // 普通：铜褐，避免与绿色障碍/草地混淆
            floatArrayOf(0.30f, 0.75f, 0.85f, 1f),
            floatArrayOf(1.0f, 0.78f, 0.20f, 1f)
        )
        private val RELIC_BASE = floatArrayOf(0.45f, 0.34f, 0.22f, 1f)
        private val RELIC_GLOW = floatArrayOf(1.0f, 0.95f, 0.70f, 1f)
        private val CLOUD = floatArrayOf(1f, 1f, 1f, 1f)
        private val SUN = floatArrayOf(1.0f, 0.90f, 0.35f, 1f)
        private val MOON = floatArrayOf(0.92f, 0.94f, 1.0f, 1f)
        private val STAR = floatArrayOf(0.95f, 0.96f, 1.0f, 0.9f)
        private val LAMP = floatArrayOf(1.0f, 0.85f, 0.45f, 1f)
        private val LAMP_POLE = floatArrayOf(0.35f, 0.36f, 0.40f, 1f)
        private val MOUNTAIN = floatArrayOf(0.45f, 0.62f, 0.55f, 1f)
        private val SHADOW = floatArrayOf(0.04f, 0.09f, 0.04f, 0.32f)
        private val SPEED_LINE = floatArrayOf(0.95f, 0.97f, 1.0f, 0.35f)
        private val BOOST_TRAIL = floatArrayOf(0.30f, 0.90f, 1.0f, 0.55f)
        private val TRAIL_CYAN = floatArrayOf(0.30f, 0.90f, 1.0f, 0.55f)
        private val TRAIL_GOLD = floatArrayOf(1.0f, 0.84f, 0.20f, 0.55f)
        private val TRAIL_RAINBOW = arrayOf(
            floatArrayOf(1.0f, 0.35f, 0.45f, 0.55f),
            floatArrayOf(1.0f, 0.80f, 0.20f, 0.55f),
            floatArrayOf(0.35f, 0.95f, 0.45f, 0.55f),
            floatArrayOf(0.35f, 0.70f, 1.0f, 0.55f)
        )
        private val FLOWER = arrayOf(
            floatArrayOf(1f, 1f, 1f, 1f),
            floatArrayOf(0.98f, 0.55f, 0.65f, 1f),
            floatArrayOf(1.0f, 0.82f, 0.25f, 1f)
        )
        // 蓝灰 / 橘黄 / 乌黑 / 粉红 / 奶白 / 青绿 / 紫罗兰 / 棕褐
        private val CAT_MAIN = arrayOf(
            floatArrayOf(0.52f, 0.58f, 0.70f, 1f),
            floatArrayOf(0.95f, 0.62f, 0.26f, 1f),
            floatArrayOf(0.30f, 0.30f, 0.35f, 1f),
            floatArrayOf(0.96f, 0.66f, 0.76f, 1f),
            floatArrayOf(0.93f, 0.90f, 0.82f, 1f),
            floatArrayOf(0.35f, 0.72f, 0.65f, 1f),
            floatArrayOf(0.62f, 0.50f, 0.82f, 1f),
            floatArrayOf(0.62f, 0.44f, 0.30f, 1f)
        )
        private val CAT_DK = arrayOf(
            floatArrayOf(0.36f, 0.42f, 0.55f, 1f),
            floatArrayOf(0.76f, 0.42f, 0.12f, 1f),
            floatArrayOf(0.16f, 0.16f, 0.21f, 1f),
            floatArrayOf(0.82f, 0.47f, 0.60f, 1f),
            floatArrayOf(0.72f, 0.67f, 0.56f, 1f),
            floatArrayOf(0.20f, 0.50f, 0.45f, 1f),
            floatArrayOf(0.44f, 0.33f, 0.62f, 1f),
            floatArrayOf(0.42f, 0.28f, 0.18f, 1f)
        )
        private val CAT_WHITE = floatArrayOf(0.95f, 0.95f, 0.92f, 1f)
        private val CAT_PINK = floatArrayOf(0.96f, 0.62f, 0.72f, 1f)
        private val CAT_NOSE = floatArrayOf(0.92f, 0.42f, 0.52f, 1f)
        private val CAT_EYE = floatArrayOf(0.12f, 0.12f, 0.14f, 1f)
        private val CAT_EYE_HL = floatArrayOf(1f, 1f, 1f, 1f)
        /** 相对障碍物/跑道的体型；1 为原始建模尺寸。 */
        private const val CAT_SCALE = 0.80f

        // 围巾 / 帽子（下标 0 为"无"，占位）
        private val SCARF_COLS = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f),
            floatArrayOf(0.95f, 0.25f, 0.25f, 1f),
            floatArrayOf(0.30f, 0.85f, 0.95f, 1f),
            floatArrayOf(0.65f, 0.45f, 1.0f, 1f)
        )
        private val HAT_RED = floatArrayOf(0.88f, 0.27f, 0.27f, 1f)
        private val HAT_RED_DK = floatArrayOf(0.65f, 0.17f, 0.17f, 1f)
        private val STRAW = floatArrayOf(0.85f, 0.77f, 0.42f, 1f)
        private val STRAW_BAND = floatArrayOf(0.30f, 0.62f, 0.30f, 1f)
        private val CROWN_GOLD = floatArrayOf(0.95f, 0.78f, 0.20f, 1f)
        private val CROWN_RUBY = floatArrayOf(0.90f, 0.20f, 0.35f, 1f)

        private val RAIN_SKY = floatArrayOf(0.44f, 0.51f, 0.62f, 1f)
        private val SNOW_SKY = floatArrayOf(0.72f, 0.78f, 0.86f, 1f)
        private val GRASS_R = floatArrayOf(0.26f, 0.55f, 0.24f, 1f)
        private val GRASS_S = floatArrayOf(0.84f, 0.88f, 0.93f, 1f)
        private val GRASS_DR = floatArrayOf(0.20f, 0.45f, 0.19f, 1f)
        private val GRASS_DS = floatArrayOf(0.75f, 0.80f, 0.87f, 1f)
        private val ROAD_R = floatArrayOf(0.28f, 0.27f, 0.32f, 1f)
        // 雪天路面压暗偏蓝：避免与金属杆 METAL(0.55,0.58,0.64) 同灰度
        private val ROAD_S = floatArrayOf(0.38f, 0.41f, 0.50f, 1f)
        private val EDGE_R = floatArrayOf(0.60f, 0.59f, 0.56f, 1f)
        private val EDGE_S = floatArrayOf(0.86f, 0.87f, 0.90f, 1f)
        private val CLOUD_R = floatArrayOf(0.58f, 0.61f, 0.67f, 1f)
        private val RAIN_DROP = floatArrayOf(0.62f, 0.74f, 0.95f, 0.55f)
        private val SNOW_FLAKE = floatArrayOf(0.98f, 0.98f, 1.0f, 0.9f)

        // 平行宇宙配色 [sky, grass, grassDark, road, edge, cloud]；草原(0)占位走天气混色
        private val UNI_PAL = arrayOf(
            arrayOf(
                floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 1f),
                floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 1f),
                floatArrayOf(0f, 0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 1f)
            ),
            arrayOf( // 水下：深海蓝绿 + 沙路（灰化去黄，避免与金币 GOLD 同色系）
                floatArrayOf(0.05f, 0.32f, 0.52f, 1f),
                floatArrayOf(0.10f, 0.42f, 0.47f, 1f),
                floatArrayOf(0.07f, 0.33f, 0.39f, 1f),
                floatArrayOf(0.58f, 0.53f, 0.44f, 1f),
                floatArrayOf(0.88f, 0.84f, 0.72f, 1f),
                floatArrayOf(0.55f, 0.85f, 0.95f, 1f)
            ),
            arrayOf( // 天空：云海 + 蓝紫云路（原金色路会吞掉金币和黄色跳板）
                floatArrayOf(0.55f, 0.82f, 1.0f, 1f),
                floatArrayOf(0.90f, 0.93f, 0.98f, 1f),
                floatArrayOf(0.78f, 0.84f, 0.94f, 1f),
                floatArrayOf(0.58f, 0.66f, 0.90f, 1f),
                floatArrayOf(0.98f, 0.52f, 0.40f, 1f),
                floatArrayOf(1f, 1f, 1f, 1f)
            ),
            arrayOf( // 熔岩：暗红天 + 烬石路 + 岩浆描边
                floatArrayOf(0.24f, 0.08f, 0.10f, 1f),
                floatArrayOf(0.17f, 0.13f, 0.13f, 1f),
                floatArrayOf(0.11f, 0.08f, 0.08f, 1f),
                floatArrayOf(0.30f, 0.24f, 0.24f, 1f),
                floatArrayOf(1.0f, 0.45f, 0.10f, 1f),
                floatArrayOf(0.36f, 0.28f, 0.28f, 1f)
            ),
            arrayOf( // 糖果：棉花糖粉天 + 深巧克力路（压暗，和糖果障碍色拉开）
                floatArrayOf(0.99f, 0.76f, 0.86f, 1f),
                floatArrayOf(0.64f, 0.90f, 0.72f, 1f),
                floatArrayOf(0.53f, 0.82f, 0.62f, 1f),
                floatArrayOf(0.29f, 0.16f, 0.10f, 1f),
                floatArrayOf(1.0f, 0.94f, 0.82f, 1f),
                floatArrayOf(1.0f, 0.88f, 0.94f, 1f)
            ),
            arrayOf( // 星空：靛黑天 + 霓虹描边
                floatArrayOf(0.05f, 0.04f, 0.13f, 1f),
                floatArrayOf(0.28f, 0.26f, 0.42f, 1f),
                floatArrayOf(0.20f, 0.18f, 0.33f, 1f),
                floatArrayOf(0.19f, 0.17f, 0.36f, 1f),
                floatArrayOf(0.30f, 0.90f, 1.0f, 1f),
                floatArrayOf(0.45f, 0.30f, 0.70f, 1f)
            )
        )
        private val PORTAL_RING = arrayOf(
            floatArrayOf(1.0f, 0.40f, 0.45f, 0.95f),
            floatArrayOf(1.0f, 0.80f, 0.25f, 0.95f),
            floatArrayOf(0.40f, 0.95f, 0.50f, 0.95f),
            floatArrayOf(0.35f, 0.70f, 1.0f, 0.95f),
            floatArrayOf(0.75f, 0.45f, 1.0f, 0.95f)
        )
        // 水下
        private val SEAWEED = floatArrayOf(0.14f, 0.62f, 0.42f, 1f)
        private val CORAL_PINK = floatArrayOf(0.98f, 0.52f, 0.60f, 1f)
        private val CORAL_ORANGE = floatArrayOf(0.98f, 0.62f, 0.30f, 1f)
        private val FISH = arrayOf(
            floatArrayOf(1.0f, 0.72f, 0.25f, 1f),
            floatArrayOf(0.35f, 0.80f, 1.0f, 1f),
            floatArrayOf(0.95f, 0.45f, 0.65f, 1f)
        )
        private val BUBBLE = floatArrayOf(0.78f, 0.93f, 1.0f, 0.5f)
        // 天空
        private val ISLAND_DIRT = floatArrayOf(0.55f, 0.40f, 0.26f, 1f)
        private val ISLAND_DIRT_DK = floatArrayOf(0.42f, 0.30f, 0.19f, 1f)
        private val RAINBOW = arrayOf(
            floatArrayOf(1.0f, 0.38f, 0.40f, 0.8f),
            floatArrayOf(1.0f, 0.80f, 0.25f, 0.8f),
            floatArrayOf(0.40f, 0.90f, 0.45f, 0.8f),
            floatArrayOf(0.40f, 0.65f, 1.0f, 0.8f)
        )
        private val BIRD = floatArrayOf(1f, 1f, 1f, 1f)
        // 熔岩：黑曜石略提亮，靠岩浆描边与警示面读危险
        private val OBSIDIAN = floatArrayOf(0.28f, 0.22f, 0.28f, 1f)
        private val LAVA_GLOW = floatArrayOf(1.0f, 0.50f, 0.08f, 0.9f)
        private val LAVA_CORE = floatArrayOf(1.0f, 0.85f, 0.25f, 0.95f)
        private val EMBER = floatArrayOf(1.0f, 0.55f, 0.15f, 0.8f)
        // 糖果
        private val CANDY_STICK = floatArrayOf(0.98f, 0.97f, 0.94f, 1f)
        private val CANDY_RED = floatArrayOf(0.96f, 0.30f, 0.35f, 1f)
        private val LOLLIPOP = arrayOf(
            floatArrayOf(0.98f, 0.45f, 0.65f, 1f),
            floatArrayOf(0.45f, 0.80f, 0.98f, 1f),
            floatArrayOf(0.65f, 0.90f, 0.40f, 1f)
        )
        private val GUMDROP = arrayOf(
            floatArrayOf(0.95f, 0.55f, 0.75f, 1f),
            floatArrayOf(0.60f, 0.85f, 0.55f, 1f),
            floatArrayOf(0.98f, 0.85f, 0.40f, 1f)
        )
        // 星空：岩石提亮，避免与靛黑路同灰；警示面用霓虹
        private val ASTEROID = floatArrayOf(0.68f, 0.64f, 0.78f, 1f)
        private val ASTEROID_DK = floatArrayOf(0.40f, 0.36f, 0.52f, 1f)
        private val CRYSTAL_CYAN = floatArrayOf(0.35f, 0.95f, 1.0f, 0.9f)
        private val CRYSTAL_PURPLE = floatArrayOf(0.70f, 0.45f, 1.0f, 0.9f)
        private val DANGER_FACE = floatArrayOf(1.0f, 0.32f, 0.18f, 1f)
        private val DANGER_FACE_LT = floatArrayOf(1.0f, 0.92f, 0.55f, 1f)
        private val PLANET_A = floatArrayOf(0.80f, 0.50f, 0.90f, 1f)
        private val PLANET_RING = floatArrayOf(0.95f, 0.85f, 0.55f, 0.9f)
        private val PLANET_B = floatArrayOf(0.95f, 0.60f, 0.35f, 1f)
        private val STARDUST = floatArrayOf(0.85f, 0.90f, 1.0f, 0.7f)

        private val MAGNET_RED = floatArrayOf(0.90f, 0.24f, 0.24f, 1f)
        private val MAGNET_TIP = floatArrayOf(0.92f, 0.92f, 0.95f, 1f)
        private val MAGNET_BLUE = floatArrayOf(0.25f, 0.65f, 1.0f, 1f)
        private val HELMET_Y = floatArrayOf(1.0f, 0.76f, 0.12f, 1f)
        private val DOUBLE_P = floatArrayOf(0.62f, 0.30f, 0.90f, 1f)
        private val DOUBLE_CORE = floatArrayOf(1.0f, 0.84f, 0.10f, 1f)
        private val BOOST_CYAN = floatArrayOf(0.25f, 0.90f, 1.0f, 1f)
        private val BOOST_CORE = floatArrayOf(1.0f, 1.0f, 0.85f, 1f)
        private val CABLE = floatArrayOf(0.25f, 0.26f, 0.30f, 1f)
        private val GANTRY = floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
        private val GANTRY_IN = floatArrayOf(0.30f, 0.75f, 0.35f, 1f)
        private val RAMP_YELLOW = floatArrayOf(0.96f, 0.66f, 0.10f, 1f)
        private val RAMP_EDGE = floatArrayOf(0.76f, 0.38f, 0.05f, 1f)
        private val CONCRETE = floatArrayOf(0.64f, 0.66f, 0.64f, 1f)
        private val WARNING = floatArrayOf(0.94f, 0.28f, 0.10f, 1f)

        // 障碍材质：草原=真实马路道具，其余宇宙沿用各自景物调色
        private val BARRIER_RED = floatArrayOf(0.90f, 0.24f, 0.20f, 1f)   // 水马 / 道闸红条
        private val BARRIER_WHITE = floatArrayOf(0.95f, 0.95f, 0.92f, 1f) // 反光白条
        private val CONE_ORANGE = floatArrayOf(0.97f, 0.45f, 0.10f, 1f)   // 施工路障桶
        private val GATE_BOX = floatArrayOf(0.88f, 0.78f, 0.20f, 1f)      // 道闸机箱黄
        private val RAINBOW_SOLID = arrayOf(                              // 天空世界彩虹横杆
            floatArrayOf(0.95f, 0.35f, 0.40f, 1f),
            floatArrayOf(0.98f, 0.72f, 0.25f, 1f),
            floatArrayOf(0.45f, 0.85f, 0.45f, 1f),
            floatArrayOf(0.40f, 0.65f, 1.0f, 1f),
            floatArrayOf(0.70f, 0.45f, 1.0f, 1f)
        )
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VSH, FSH)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uFog = GLES20.glGetUniformLocation(program, "uFogColor")
        uMode = GLES20.glGetUniformLocation(program, "uMode")

        cube = Mesh.cube()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(SKY[0], SKY[1], SKY[2], 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height > 0) width.toFloat() / height else 1.6f
        applyProjection(52f)
    }

    /**
     * 宽屏：直接用垂直 FOV（原横屏手感）。
     * 窄屏：锚定「参考横屏」的水平 FOV，反推垂直 FOV，避免三条道被挤扁。
     */
    private fun applyProjection(vFovDeg: Float) {
        val vFov = vFovDeg.coerceIn(50f, 68f)
        val vertical = if (aspect >= 1f) {
            vFov
        } else {
            val halfV = Math.toRadians(vFov.toDouble() / 2.0)
            val halfH = atan(tan(halfV) * REF_ASPECT)
            Math.toDegrees(2.0 * atan(tan(halfH) / aspect.toDouble())).toFloat()
        }
        Matrix.perspectiveM(proj, 0, vertical, aspect, 0.5f, 400f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        game.update(dt)

        // 家页面：独立 Canvas 场景，不渲染跑道
        if (game.menuPanel == Game.PANEL_HOME &&
            (game.state == Game.State.READY || game.state == Game.State.DEAD)
        ) {
            GLES20.glClearColor(0.56f, 0.83f, 0.95f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            return
        }

        scenePhase += dt

        updateWeatherColors()
        GLES20.glClearColor(skyCol[0], skyCol[1], skyCol[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        setSkyFog()

        // 动态 FOV：速度越高视野越宽；冲刺再加一点（竖屏走水平锚定）
        val spd = game.speed
        val fov = 52f + (spd - 14f) * 0.55f + (if (game.boosting) 4f else 0f)
        applyProjection(fov)

        camX += (game.catX * 0.55f - camX) * min(1f, dt * 6f)
        val shakeAmt = game.shake
        val sx = if (shakeAmt > 0f) sin(now * 0.00000005) * shakeAmt * 0.18f else 0.0
        val sy = if (shakeAmt > 0f) cos(now * 0.00000007) * shakeAmt * 0.12f else 0.0
        // 竖屏：抬高俯视，地平线上移、猫压到约下 1/4，赛道更长
        val portrait = aspect < 1f
        val eyeY = (if (portrait) 6.8f else 4.35f) + game.catY * 0.22f + sy.toFloat()
        val centerY = (if (portrait) 0.15f else 1.15f) + game.catY * 0.28f
        val eyeZ = if (portrait) 5.2f else 6.0f
        val lookZ = if (portrait) -12f else -8f
        Matrix.setLookAtM(
            view, 0,
            camX + sx.toFloat(), eyeY, eyeZ,
            camX * 0.5f + sx.toFloat(), centerY, lookZ,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        publishRelicLabels()
        publishYokaiHud()

        drawSky()
        drawTrack()
        drawScenery()
        drawStreetLamps()
        drawEntities()
        drawPortal()
        drawYokai()
        drawParticles()
        drawCat(dt)
        drawSpeedLines(dt)
        drawWeather(dt)
        drawAmbient(dt)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    /** 把文物金币投影到 0~1 屏幕坐标，供 HUD 叠字 */
    private fun publishRelicLabels() {
        var n = 0
        synchronized(game) {
            if (game.state != Game.State.RUNNING) {
                game.relicHudCount = 0
                return
            }
            for (e in game.entities) {
                if (n >= Game.RELIC_HUD_MAX) break
                if (!e.isRelic || e.taken) continue
                if (e.z > 3f || e.z < -100f) continue
                if (!projectWorld(e.x, e.y + 0.95f, e.z, projectTmp)) continue
                game.relicHudX[n] = projectTmp[0]
                game.relicHudY[n] = projectTmp[1]
                game.relicHudScale[n] = (1.15f / (1f + abs(e.z) * 0.035f)).coerceIn(0.5f, 1.35f)
                game.relicHudId[n] = e.relicId
                n++
            }
            game.relicHudCount = n
        }
    }

    /** 妖怪头顶屏幕坐标，供 HUD 画呼喊气泡 */
    private fun publishYokaiHud() {
        game.yokaiHudVisible = false
        if (!game.chaseActive) return
        val z = game.yokaiZ
        if (z < -220f || z > 4f) return
        val lane = game.yokaiLane.coerceIn(0, 2)
        val x = Game.LANE_X[lane]
        val bob = abs(sin(game.yokaiRunPhase)) * 0.09f
        if (projectWorld(x, 2.55f + bob, z, projectTmp)) {
            game.yokaiHudX = projectTmp[0]
            game.yokaiHudY = projectTmp[1]
            game.yokaiHudVisible = true
        }
    }

    /** 世界坐标 → 归一化屏幕坐标 (x,y ∈ 0~1，原点左上)。失败返回 false。 */
    private fun projectWorld(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val m = vp
        val cw = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (cw <= 0.05f) return false
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        val ndcX = cx / cw
        val ndcY = cy / cw
        if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.2f || ndcY > 1.2f) return false
        out[0] = ndcX * 0.5f + 0.5f
        out[1] = 1f - (ndcY * 0.5f + 0.5f)
        return true
    }

    // ---------- 天气 / 昼夜配色 ----------
    private fun weatherWeight(type: Int): Float {
        var w = 0f
        if (game.weather == type) w += game.weatherBlend
        if (game.weatherPrev == type) w += 1f - game.weatherBlend
        return w
    }

    private fun mix3(out: FloatArray, a: FloatArray, b: FloatArray, c: FloatArray, wa: Float, wb: Float, wc: Float) {
        for (i in 0..2) out[i] = a[i] * wa + b[i] * wb + c[i] * wc
        out[3] = 1f
    }

    /** 草原按天气混色，其余宇宙用专属调色板 */
    private fun fillUniversePalette(uni: Int, out: Array<FloatArray>) {
        if (uni == Game.UNI_MEADOW) {
            mix3(out[0], SKY, RAIN_SKY, SNOW_SKY, wSun, wRain, wSnow)
            mix3(out[1], GRASS, GRASS_R, GRASS_S, wSun, wRain, wSnow)
            mix3(out[2], GRASS_DARK, GRASS_DR, GRASS_DS, wSun, wRain, wSnow)
            mix3(out[3], ROAD, ROAD_R, ROAD_S, wSun, wRain, wSnow)
            mix3(out[4], ROAD_EDGE, EDGE_R, EDGE_S, wSun, wRain, wSnow)
            mix3(out[5], CLOUD, CLOUD_R, CLOUD, wSun, wRain, wSnow)
        } else {
            for (i in 0..5) System.arraycopy(UNI_PAL[uni][i], 0, out[i], 0, 4)
        }
    }

    private fun updateWeatherColors() {
        wSun = weatherWeight(Game.W_SUNNY)
        wRain = weatherWeight(Game.W_RAIN)
        wSnow = weatherWeight(Game.W_SNOW)

        val blend = game.universeBlend
        val cur = game.universe
        val prev = game.universePrev
        meadowW = (if (cur == Game.UNI_MEADOW) blend else 0f) +
            (if (prev == Game.UNI_MEADOW) 1f - blend else 0f)

        // 特殊宇宙自带光照氛围，昼夜影响减弱
        nightAmt = game.nightAmount() * (0.3f + 0.7f * meadowW)
        duskAmt = game.duskAmount() * (0.3f + 0.7f * meadowW)

        fillUniversePalette(prev, palPrev)
        fillUniversePalette(cur, palCur)
        val outs = arrayOf(skyCol, grassCol, grassDarkCol, roadCol, edgeCol, cloudCol)
        for (i in outs.indices) {
            for (k in 0..2) outs[i][k] = palPrev[i][k] + (palCur[i][k] - palPrev[i][k]) * blend
            outs[i][3] = 1f
        }

        // 昼夜：夜晚压暗并偏蓝紫；黄昏加暖橙
        for (arr in outs) applyDayNight(arr)
    }

    private fun applyDayNight(c: FloatArray) {
        if (duskAmt > 0.01f) {
            c[0] = c[0] * (1f - 0.15f * duskAmt) + 1.0f * 0.15f * duskAmt
            c[1] = c[1] * (1f - 0.25f * duskAmt) + 0.55f * 0.15f * duskAmt
            c[2] = c[2] * (1f - 0.45f * duskAmt)
        }
        if (nightAmt > 0.01f) {
            val dim = 1f - 0.55f * nightAmt
            c[0] *= dim * (1f - 0.15f * nightAmt)
            c[1] *= dim * (1f - 0.05f * nightAmt)
            c[2] = min(1f, c[2] * dim + 0.08f * nightAmt)
        }
    }

    private fun setSkyFog() = setFog(skyCol[0] + 0.08f, skyCol[1] + 0.05f, skyCol[2] + 0.02f)

    private fun drawWeather(dt: Float) {
        // 雨雪只属于草原世界
        val wr = weatherWeight(Game.W_RAIN) * meadowW
        val ws = weatherWeight(Game.W_SNOW) * meadowW
        mMode = 2
        if (wr > 0.02f) {
            val n = (rainX.size * wr).toInt()
            for (i in rainX.indices) {
                rainY[i] -= 26f * dt
                if (rainY[i] < 0f) rainY[i] += 15f
                if (i < n) {
                    drawBox(camX + rainX[i], rainY[i], rainZ[i], 0.04f, 0.7f, 0.04f, RAIN_DROP)
                }
            }
        }
        if (ws > 0.02f) {
            snowPhase += dt
            val n = (snowX.size * ws).toInt()
            for (i in snowX.indices) {
                snowY[i] -= 3.2f * dt
                if (snowY[i] < 0f) snowY[i] += 15f
                if (i < n) {
                    val sway = sin(snowPhase * 1.7f + snowSeed[i]) * 0.6f
                    drawBox(camX + snowX[i] + sway, snowY[i], snowZ[i], 0.11f, 0.11f, 0.11f, SNOW_FLAKE)
                }
            }
        }
        mMode = 0
    }

    private fun setFog(r: Float, g: Float, b: Float) = GLES20.glUniform3f(uFog, min(1f, r), min(1f, g), min(1f, b))

    // ---------- 天空：太阳/月亮/星星/行星 + 远山 ----------
    private fun drawSky() {
        Matrix.setIdentityM(model, 0)
        stack.clear()
        val uni = game.universe

        mMode = 2
        val brightSky = uni == Game.UNI_MEADOW || uni == Game.UNI_SKY || uni == Game.UNI_CANDY
        val dayVis = (1f - nightAmt).coerceIn(0f, 1f)
        if (brightSky && dayVis > 0.05f && wSun > 0.05f) {
            setFog(SUN[0], SUN[1], SUN[2])
            pushModel(20f, 26f - nightAmt * 18f, -110f)
            val k = 7f * wSun * dayVis
            drawPart(0f, 0f, 0f, k, k, k, SUN)
            popModel()
        }
        val starry = nightAmt > 0.15f || uni == Game.UNI_SPACE
        if (nightAmt > 0.15f && uni != Game.UNI_SPACE) {
            setFog(MOON[0], MOON[1], MOON[2])
            pushModel(-18f, 24f, -110f)
            val k = 5f * nightAmt
            drawPart(0f, 0f, 0f, k, k, k, MOON)
            popModel()
        }
        if (uni == Game.UNI_SPACE) {
            // 环状大行星 + 橙色小行星
            setSkyFog()
            pushModel(22f, 24f, -120f)
            Matrix.rotateM(model, 0, 18f, 0f, 0f, 1f)
            drawPart(0f, 0f, 0f, 8f, 8f, 8f, PLANET_A)
            drawPart(0f, 0f, 0f, 15f, 0.7f, 4f, PLANET_RING)
            popModel()
            drawBox(-26f, 29f, -130f, 3.6f, 3.6f, 3.6f, PLANET_B)
        }
        if (starry) {
            setSkyFog()
            val rows = if (uni == Game.UNI_SPACE) 2 else 1
            for (r in 0 until rows) {
                for (i in 0 until 18) {
                    val x = -70f + i * 8.5f + (i % 3) * 3f + r * 4f
                    val y = 14f + (i * 7 % 11) + r * 9f
                    val tw = 0.25f + 0.15f * abs(sin(game.dayPhase * 40f + i + r * 3))
                    drawBox(x, y, -130f, tw, tw, tw, STAR)
                }
            }
        }
        setSkyFog()

        mMode = 0
        if (meadowW > 0.35f) {
            for (i in 0 until 7) {
                val x = -90f + i * 30f + (i % 3) * 8f
                if (abs(x) < 16f) continue
                val h = 26f + (i % 3) * 10f
                val mc = floatArrayOf(MOUNTAIN[0], MOUNTAIN[1], MOUNTAIN[2], 1f)
                applyDayNight(mc)
                pushModel(x, 0f, -180f)
                drawPart(0f, h * 0.14f, 0f, h * 1.9f, h * 0.28f, h, mc)
                drawPart(0f, h * 0.38f, 0f, h * 1.1f, h * 0.22f, h * 0.8f, mc)
                popModel()
            }
        }
    }

    // ---------- 跑道 ----------
    private fun drawTrack() {
        drawBox(-30.5f, -0.55f, -110f, 53f, 1f, 260f, grassCol)
        drawBox(30.5f, -0.55f, -110f, 53f, 1f, 260f, grassCol)
        drawBox(-5.6f, -0.53f, -110f, 2.4f, 1.02f, 260f, grassDarkCol)
        drawBox(5.6f, -0.53f, -110f, 2.4f, 1.02f, 260f, grassDarkCol)
        drawBox(0f, -0.5f, -110f, 8.2f, 1f, 260f, roadCol)
        drawBox(-4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, edgeCol)
        drawBox(4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, edgeCol)

        scroll(5f, game.distance) { _, z ->
            for (x in floatArrayOf(-1.1f, 1.1f)) {
                drawBox(x, 0.02f, z, 0.14f, 0.04f, 1.6f, DASH)
            }
        }
    }

    // ---------- 路边景物：按宇宙分发 ----------
    private fun drawScenery() {
        when (game.universe) {
            Game.UNI_WATER -> drawSceneryWater()
            Game.UNI_SKY -> drawScenerySky()
            Game.UNI_LAVA -> drawSceneryLava()
            Game.UNI_CANDY -> drawSceneryCandy()
            Game.UNI_SPACE -> drawScenerySpace()
            else -> drawSceneryMeadow()
        }
        drawClouds()
    }

    private fun drawSceneryMeadow() {
        scroll(17f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (6.8f + mod(m * 37, 40) / 10f)
            val k = 0.75f + mod(m * 53, 50) / 100f
            drawTree(x, z, k, if (mod(m, 3) == 0) TREE_LEAF2 else TREE_LEAF)
            val x2 = -side * (9f + mod(m * 61, 50) / 10f)
            drawTree(x2, z - 8f, 1.5f - k * 0.5f, TREE_LEAF)
        }
        scroll(7.3f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) 1f else -1f
            val x = side * (5.4f + mod(m * 29, 25) / 10f)
            val k = 0.7f + mod(m * 41, 40) / 100f
            pushModel(x, 0.22f * k, z)
            drawPart(0f, 0f, 0f, 1.1f * k, 0.5f * k, 1.1f * k, BUSH)
            popModel()
        }
        scroll(13.7f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (4.9f + mod(m * 43, 18) / 10f)
            val k = 0.5f + mod(m * 31, 45) / 100f
            pushModel(x, 0.14f * k, z)
            drawPart(0f, 0f, 0f, 0.5f * k, 0.3f * k, 0.42f * k, ROCK)
            popModel()
        }
        scroll(4.7f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (4.9f + mod(m * 17, 60) / 10f)
            pushModel(x, 0.12f, z)
            drawPart(0f, 0f, 0f, 0.2f, 0.2f, 0.2f, FLOWER[mod(m, 3)])
            popModel()
        }
    }

    /** 水下：摇摆海草 + 珊瑚 + 头顶游鱼 */
    private fun drawSceneryWater() {
        scroll(11f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (5.6f + mod(m * 37, 45) / 10f)
            val k = 0.8f + mod(m * 53, 50) / 100f
            pushModel(x, 0f, z)
            for (j in 0..3) {
                val sway = sin(scenePhase * 1.8f + m + j * 0.8f) * 0.22f * j
                drawPart(sway, 0.4f + j * 0.75f * k, 0f, 0.32f * k, 0.8f * k, 0.2f, SEAWEED)
            }
            popModel()
        }
        scroll(19f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) 1f else -1f
            val x = side * (6.5f + mod(m * 29, 38) / 10f)
            val c = if (mod(m, 2) == 0) CORAL_PINK else CORAL_ORANGE
            pushModel(x, 0f, z)
            drawPart(0f, 0.5f, 0f, 0.35f, 1.0f, 0.35f, c)
            drawPart(0.35f, 0.9f, 0f, 0.25f, 0.7f, 0.25f, c)
            drawPart(-0.3f, 0.75f, 0f, 0.22f, 0.5f, 0.22f, c)
            popModel()
        }
        // 鱼群横游（含头顶穿过跑道）
        scroll(16f, game.distance * 0.6f) { m, z ->
            val fy = 2.4f + mod(m * 13, 30) / 10f
            val ph = scenePhase * 0.5f + m * 1.7f
            val fx = sin(ph) * 9f
            val dir = if (cos(ph) > 0f) 1f else -1f
            val fc = FISH[mod(m, 3)]
            pushModel(fx, fy + sin(scenePhase * 2f + m) * 0.15f, z)
            drawPart(0f, 0f, 0f, 0.55f, 0.3f, 0.22f, fc)
            drawPart(-dir * 0.38f, 0.05f, 0f, 0.2f, 0.26f, 0.1f, fc)
            popModel()
        }
    }

    /** 天空：漂浮岛 + 彩虹拱桥 + 飞鸟 */
    private fun drawScenerySky() {
        scroll(15f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (7.5f + mod(m * 41, 60) / 10f)
            val k = 0.8f + mod(m * 31, 60) / 100f
            val fy = -0.2f + mod(m * 23, 25) / 10f
            val bob = sin(scenePhase * 0.8f + m) * 0.25f
            pushModel(x, fy + bob, z)
            drawPart(0f, 0.55f, 0f, 2.6f * k, 0.5f, 2.2f * k, GRASS)
            drawPart(0f, 0.1f, 0f, 2.0f * k, 0.6f, 1.7f * k, ISLAND_DIRT)
            drawPart(0f, -0.35f, 0f, 1.2f * k, 0.5f, 1.0f * k, ISLAND_DIRT_DK)
            drawPart(0.5f * k, 1.05f, 0f, 0.18f, 0.6f, 0.18f, TREE_TRUNK)
            drawPart(0.5f * k, 1.65f, 0f, 0.7f * k, 0.7f, 0.7f * k, TREE_LEAF)
            popModel()
        }
        // 远景彩虹拱桥
        mMode = 2
        scroll(95f, game.distance * 0.7f) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            pushModel(side * 12f, 0f, z - 55f)
            for (band in RAINBOW.indices) {
                val rr = 20f + band * 1.5f
                for (seg in 0..8) {
                    val a = Math.PI.toFloat() * seg / 8f
                    drawPart(cos(a) * rr, sin(a) * rr * 0.6f, 0f, 1.8f, 1.4f, 0.8f, RAINBOW[band])
                }
            }
            popModel()
        }
        mMode = 0
        // 小鸟
        scroll(21f, game.distance * 0.9f) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (4.5f + mod(m * 19, 40) / 10f)
            val y = 4.5f + mod(m * 27, 30) / 10f
            val flap = sin(scenePhase * 6f + m) * 0.28f
            pushModel(x, y, z)
            drawPart(0f, 0f, 0f, 0.34f, 0.18f, 0.3f, BIRD)
            drawPart(-0.3f, flap, 0f, 0.3f, 0.08f, 0.22f, BIRD)
            drawPart(0.3f, flap, 0f, 0.3f, 0.08f, 0.22f, BIRD)
            popModel()
        }
    }

    /** 熔岩：黑曜石尖岩 + 发光岩浆池 + 远景火山 */
    private fun drawSceneryLava() {
        scroll(14f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (5.8f + mod(m * 37, 40) / 10f)
            val k = 0.7f + mod(m * 53, 60) / 100f
            pushModel(x, 0f, z)
            drawPart(0f, 0.8f * k, 0f, 0.9f * k, 1.6f * k, 0.9f * k, OBSIDIAN)
            drawPart(0.2f * k, 1.9f * k, 0f, 0.5f * k, 0.9f * k, 0.5f * k, OBSIDIAN)
            drawPart(0.2f * k, 2.5f * k, 0f, 0.22f * k, 0.5f * k, 0.22f * k, OBSIDIAN)
            popModel()
        }
        mMode = 2
        scroll(9f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) 1f else -1f
            val x = side * (6.2f + mod(m * 29, 45) / 10f)
            val k = 0.8f + mod(m * 41, 50) / 100f
            val pulse = 0.75f + 0.25f * sin(scenePhase * 2.5f + m)
            val glow = floatArrayOf(LAVA_GLOW[0] * pulse, LAVA_GLOW[1] * pulse, LAVA_GLOW[2], 0.9f)
            drawBox(x, 0.06f, z, 1.6f * k, 0.12f, 1.3f * k, glow)
            drawBox(x, 0.10f, z, 0.8f * k, 0.1f, 0.6f * k, LAVA_CORE)
        }
        // 远景火山：黑锥 + 发光火山口
        scroll(70f, game.distance * 0.6f) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (22f + mod(m * 23, 90) / 10f)
            pushModel(x, 0f, z - 60f)
            drawPart(0f, 5f, 0f, 22f, 10f, 14f, OBSIDIAN)
            drawPart(0f, 12f, 0f, 12f, 6f, 9f, OBSIDIAN)
            drawPart(0f, 15.4f, 0f, 5f, 1.4f, 4f, LAVA_GLOW)
            popModel()
        }
        mMode = 0
    }

    /** 糖果：棒棒糖树 + 软糖丛 + 拐杖糖 */
    private fun drawSceneryCandy() {
        scroll(13f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (6.4f + mod(m * 37, 42) / 10f)
            val k = 0.8f + mod(m * 53, 50) / 100f
            val c = LOLLIPOP[mod(m, 3)]
            pushModel(x, 0f, z)
            drawPart(0f, 1.1f * k, 0f, 0.22f, 2.2f * k, 0.22f, CANDY_STICK)
            drawPart(0f, 2.7f * k, 0f, 1.5f * k, 1.5f * k, 0.5f, c)
            drawPart(0f, 2.7f * k, 0.05f, 0.8f * k, 0.8f * k, 0.5f, CANDY_STICK)
            popModel()
        }
        scroll(8.5f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) 1f else -1f
            val x = side * (5.2f + mod(m * 29, 30) / 10f)
            val k = 0.6f + mod(m * 41, 45) / 100f
            val c = GUMDROP[mod(m, 3)]
            pushModel(x, 0f, z)
            drawPart(0f, 0.35f * k, 0f, 1.0f * k, 0.7f * k, 1.0f * k, c)
            drawPart(0f, 0.75f * k, 0f, 0.6f * k, 0.35f * k, 0.6f * k, c)
            popModel()
        }
        scroll(23f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (7.8f + mod(m * 19, 35) / 10f)
            pushModel(x, 0f, z)
            for (j in 0..5) {
                drawPart(0f, 0.3f + j * 0.55f, 0f, 0.3f, 0.55f, 0.3f,
                    if (j % 2 == 0) CANDY_RED else CANDY_STICK)
            }
            drawPart(0.32f, 3.4f, 0f, 0.6f, 0.3f, 0.3f, CANDY_RED)
            popModel()
        }
    }

    /** 星空：漂浮陨石 + 霓虹水晶 */
    private fun drawScenerySpace() {
        scroll(17f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (6.5f + mod(m * 37, 55) / 10f)
            val k = 0.6f + mod(m * 53, 60) / 100f
            val fy = 1.2f + mod(m * 23, 32) / 10f
            val bob = sin(scenePhase * 0.6f + m) * 0.35f
            pushModel(x, fy + bob, z)
            Matrix.rotateM(model, 0, scenePhase * 12f + m * 40f, 0.3f, 1f, 0.2f)
            drawPart(0f, 0f, 0f, 1.1f * k, 0.9f * k, 1.0f * k, ASTEROID)
            drawPart(0.4f * k, 0.3f * k, 0f, 0.5f * k, 0.45f * k, 0.5f * k, ASTEROID)
            popModel()
        }
        mMode = 2
        scroll(12f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) 1f else -1f
            val x = side * (5.5f + mod(m * 29, 38) / 10f)
            val k = 0.7f + mod(m * 41, 55) / 100f
            val pulse = 0.7f + 0.3f * sin(scenePhase * 3f + m * 1.3f)
            val c = if (mod(m, 2) == 0) CRYSTAL_CYAN else CRYSTAL_PURPLE
            val col = floatArrayOf(c[0] * pulse, c[1] * pulse, c[2] * pulse, 0.9f)
            pushModel(x, 0f, z)
            drawPart(0f, 0.8f * k, 0f, 0.45f * k, 1.6f * k, 0.45f * k, col)
            drawPart(0f, 1.9f * k, 0f, 0.22f * k, 0.7f * k, 0.22f * k, col)
            drawPart(0.4f * k, 0.5f * k, 0f, 0.28f * k, 1.0f * k, 0.28f * k, col)
            popModel()
        }
        mMode = 0
    }

    private fun drawClouds() {
        mMode = 2
        scroll(31f, game.distance * 0.45f) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (9f + mod(m * 23, 140) / 10f)
            val y = 11f + mod(m * 19, 60) / 10f
            val k = 1.1f + mod(m * 13, 60) / 100f
            pushModel(x, y, z)
            drawPart(0f, 0f, 0f, 3.2f * k, 1.3f * k, 1.5f * k, cloudCol)
            drawPart(1.7f * k, 0.5f * k, 0f, 2.0f * k, 1.1f * k, 1.3f * k, cloudCol)
            drawPart(-1.7f * k, 0.4f * k, 0f, 1.8f * k, 1.0f * k, 1.2f * k, cloudCol)
            popModel()
        }
        mMode = 0
    }

    /** 宇宙环境粒子：气泡 / 火星 / 糖屑 / 星尘 */
    private fun drawAmbient(dt: Float) {
        val uni = game.universe
        if (uni == Game.UNI_MEADOW || uni == Game.UNI_SKY) return
        val strength = if (game.universePrev == Game.UNI_MEADOW) game.universeBlend else 1f
        if (strength < 0.05f) return
        mMode = 2
        val n = (ambX.size * strength).toInt()
        for (i in 0 until n) {
            when (uni) {
                Game.UNI_WATER -> {
                    ambY[i] += 1.3f * dt
                    if (ambY[i] > 12f) ambY[i] -= 12f
                    val sway = sin(scenePhase * 1.5f + ambSeed[i]) * 0.4f
                    val k = 0.08f + 0.05f * abs(sin(ambSeed[i] * 3f))
                    drawBox(camX + ambX[i] + sway, ambY[i], ambZ[i], k, k, k, BUBBLE)
                }
                Game.UNI_LAVA -> {
                    ambY[i] += 2.2f * dt
                    if (ambY[i] > 12f) ambY[i] -= 12f
                    val fl = 0.5f + 0.5f * abs(sin(scenePhase * 5f + ambSeed[i]))
                    val col = floatArrayOf(1f, 0.45f + 0.35f * fl, 0.1f, 0.55f + 0.3f * fl)
                    val k = 0.07f + 0.05f * fl
                    drawBox(camX + ambX[i], ambY[i], ambZ[i], k, k, k, col)
                }
                Game.UNI_CANDY -> {
                    ambY[i] -= 1.0f * dt
                    if (ambY[i] < 0f) ambY[i] += 12f
                    val sway = sin(scenePhase * 1.2f + ambSeed[i]) * 0.5f
                    val c = PORTAL_RING[i % PORTAL_RING.size]
                    drawBox(camX + ambX[i] + sway, ambY[i], ambZ[i], 0.09f, 0.09f, 0.09f, c)
                }
                Game.UNI_SPACE -> {
                    ambZ[i] += 2.5f * dt
                    if (ambZ[i] > 6f) ambZ[i] -= 50f
                    val tw = 0.4f + 0.6f * abs(sin(scenePhase * 4f + ambSeed[i]))
                    val col = floatArrayOf(STARDUST[0], STARDUST[1], STARDUST[2], 0.7f * tw)
                    val k = 0.06f + 0.04f * tw
                    drawBox(camX + ambX[i], ambY[i], ambZ[i], k, k, k, col)
                }
            }
        }
        mMode = 0
    }

    /** 传送门：门柱 + 旋转彩环 + 目标宇宙色门芯 */
    private fun drawPortal() {
        if (!game.portalActive) return
        val z = game.portalZ
        if (z < -220f || z > 4f) return
        pushModel(0f, 0f, z)
        drawPart(-4.2f, 2.6f, 0f, 0.5f, 5.2f, 0.5f, GANTRY)
        drawPart(4.2f, 2.6f, 0f, 0.5f, 5.2f, 0.5f, GANTRY)
        drawPart(0f, 5.4f, 0f, 8.9f, 0.5f, 0.5f, GANTRY)
        popModel()

        mMode = 2
        val spin = scenePhase * 2.2f
        for (i in 0 until 12) {
            val a = spin + i * (2f * Math.PI.toFloat() / 12f)
            val px = cos(a) * 3.4f
            val py = 2.7f + sin(a) * 2.1f
            drawBox(px, py, z, 0.42f, 0.42f, 0.42f, PORTAL_RING[i % PORTAL_RING.size])
        }
        val tc = if (game.portalTarget == Game.UNI_MEADOW) SKY else UNI_PAL[game.portalTarget][0]
        val pulse = 0.30f + 0.10f * sin(scenePhase * 3f)
        drawBox(0f, 2.7f, z + 0.1f, 7.4f, 4.6f, 0.12f,
            floatArrayOf(min(1f, tc[0] + 0.25f), min(1f, tc[1] + 0.25f), min(1f, tc[2] + 0.3f), pulse))
        drawBox(0f, 2.7f, z + 0.2f, 5.0f, 3.2f, 0.1f, floatArrayOf(1f, 1f, 1f, pulse * 0.55f))
        mMode = 0
    }

    /** 追击妖怪：在前方赛道上奔跑逃窜，光晕+大体型+夸张动作 */
    private fun drawYokai() {
        if (!game.chaseActive) return
        val z = game.yokaiZ
        if (z < -220f || z > 4f) return
        val lane = game.yokaiLane.coerceIn(0, 2)
        val x = Game.LANE_X[lane]
        val c = argbCol(game.yokaiColor)
        val cd = floatArrayOf(c[0] * 0.68f, c[1] * 0.68f, c[2] * 0.68f, 1f)
        val hi = floatArrayOf(
            min(1f, c[0] + 0.38f), min(1f, c[1] + 0.38f), min(1f, c[2] + 0.42f), 1f
        )
        val phase = game.yokaiRunPhase
        val bob = abs(sin(phase)) * 0.14f
        val sway = sin(phase * 0.55f) * 0.12f
        val distBoost = ((-z).coerceIn(12f, 90f) / 45f).coerceIn(1f, 1.22f)
        val scale = 1.42f * distBoost
        val pulse = 0.50f + 0.18f * sin(scenePhase * 5.5f)

        drawShadow(x, z, 1.15f * distBoost)
        // 脚下警示环 + 光晕（远处也醒目）
        mMode = 2
        val ringCol = floatArrayOf(hi[0], hi[1], hi[2], pulse * 0.55f)
        drawBox(x, 0.06f, z, 2.6f * distBoost, 0.08f, 2.6f * distBoost, ringCol)
        pushModel(x, bob, z)
        emitGlowHalo(0f, 1.05f + bob, 0f, 2.2f, 2.2f, 0.85f, floatArrayOf(hi[0], hi[1], hi[2], pulse * 0.42f))
        popModel()
        mMode = 0

        pushModel(x + sway, bob, z)
        Matrix.rotateM(model, 0, 180f, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, sin(phase * 0.45f) * 7f, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, scale, scale, scale)
        if (game.yokaiKind % 2 == 0) drawYokaiQuadruped(c, cd, hi, phase)
        else drawYokaiHopper(c, cd, hi, phase)
        // 脚后尘土 + 速度线
        mMode = 2
        val dustA = (0.35f + abs(sin(phase * 0.55f)) * 0.25f).coerceIn(0.18f, 0.58f)
        drawBox(0f, 0.10f, 0.78f, 0.52f, 0.08f, 0.34f, floatArrayOf(c[0], c[1], c[2], dustA))
        drawBox(0f, 0.08f, 1.05f, 0.38f, 0.06f, 0.26f, floatArrayOf(c[0], c[1], c[2], dustA * 0.75f))
        drawBox(sway * 0.5f, 0.55f, 1.18f, 0.12f, 0.12f, 0.55f, floatArrayOf(hi[0], hi[1], hi[2], 0.65f))
        drawBox(-sway * 0.5f, 0.70f, 1.28f, 0.10f, 0.10f, 0.45f, floatArrayOf(hi[0], hi[1], hi[2], 0.50f))
        // 头顶感叹号
        val markPulse = 1f + 0.12f * sin(scenePhase * 7f)
        drawBox(0f, 2.05f + bob * 2f, 0f, 0.22f * markPulse, 0.52f * markPulse, 0.22f * markPulse,
            floatArrayOf(1f, 0.92f, 0.15f, 0.95f))
        drawBox(0f, 1.62f + bob * 2f, 0f, 0.30f * markPulse, 0.30f * markPulse, 0.30f * markPulse,
            floatArrayOf(1f, 0.92f, 0.15f, 0.95f))
        mMode = 0
        popModel()
    }

    /** 四足妖（狼/兽型）：与猫类似的奔跑摆腿 */
    private fun drawYokaiQuadruped(c: FloatArray, cd: FloatArray, hi: FloatArray, phase: Float) {
        val wag = sin(phase * 0.85f) * 0.20f
        drawPart(wag, 1.0f, 0.85f, 0.30f, 0.30f, 0.38f, cd)
        drawPart(wag * 1.5f, 1.32f, 1.0f, 0.28f, 0.36f, 0.28f, c)
        drawPart(wag * 2.1f, 1.58f, 1.08f, 0.26f, 0.32f, 0.26f, hi)
        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val lx = if (left) -0.50f else 0.50f
            val lz = if (front) 0.54f else -0.54f
            val legPhase = phase + if (i == 0 || i == 3) 0f else Math.PI.toFloat()
            val swing = sin(legPhase) * 54f
            pushModel(lx, 0.62f, lz)
            Matrix.rotateM(model, 0, swing, 1f, 0f, 0f)
            drawPart(0f, -0.32f, 0f, 0.38f, 0.62f, 0.38f, if (left) c else cd)
            drawPart(0f, -0.64f, 0.02f, 0.40f, 0.16f, 0.42f, floatArrayOf(0.12f, 0.10f, 0.10f, 1f))
            popModel()
        }
        drawPart(0f, 0.84f, 0f, 1.45f, 0.96f, 1.92f, c)
        drawPart(0f, 0.90f, 0f, 1.15f, 0.22f, 1.55f, hi)
        drawPart(0f, 0.58f, 0f, 1.02f, 0.48f, 1.38f, cd)
        drawPart(0f, 1.14f, 0.78f, 1.02f, 0.86f, 0.92f, c)
        drawPart(-0.34f, 1.50f, 0.72f, 0.24f, 0.42f, 0.18f, cd)
        drawPart(0.34f, 1.50f, 0.72f, 0.24f, 0.42f, 0.18f, cd)
        drawPart(-0.24f, 1.18f, 1.12f, 0.16f, 0.16f, 0.10f, floatArrayOf(1f, 0.95f, 0.2f, 1f))
        drawPart(0.24f, 1.18f, 1.12f, 0.16f, 0.16f, 0.10f, floatArrayOf(1f, 0.95f, 0.2f, 1f))
        drawPart(0f, 1.22f, 1.18f, 0.14f, 0.10f, 0.12f, floatArrayOf(1f, 0.3f, 0.25f, 1f))
        val jaw = sin(phase * 2.4f) * 7f
        pushModel(0f, 1.02f, 1.08f)
        Matrix.rotateM(model, 0, jaw, 1f, 0f, 0f)
        drawPart(0f, -0.10f, 0.08f, 0.46f, 0.18f, 0.28f, cd)
        popModel()
    }

    /** 双足/飞行妖：蹦跳 + 振翅 */
    private fun drawYokaiHopper(c: FloatArray, cd: FloatArray, hi: FloatArray, phase: Float) {
        val hop = abs(sin(phase * 1.25f)) * 0.28f
        val wing = sin(scenePhase * 9f + phase * 0.5f) * 0.52f
        drawPart(-1.22f + wing, 1.48f + hop, 0f, 0.36f, 0.92f, 0.68f, cd)
        drawPart(1.22f - wing, 1.48f + hop, 0f, 0.36f, 0.92f, 0.68f, cd)
        drawPart(-1.22f + wing, 1.48f + hop, 0.12f, 0.22f, 0.55f, 0.12f, hi)
        drawPart(1.22f - wing, 1.48f + hop, 0.12f, 0.22f, 0.55f, 0.12f, hi)
        for (i in 0 until 2) {
            val side = if (i == 0) -1f else 1f
            val swing = sin(phase * 1.25f + i * Math.PI.toFloat()) * 48f
            pushModel(side * 0.40f, 0.52f + hop * 0.5f, 0.10f)
            Matrix.rotateM(model, 0, swing, 1f, 0f, 0f)
            drawPart(0f, -0.28f, 0f, 0.34f, 0.58f, 0.34f, cd)
            popModel()
        }
        drawPart(0f, 1.14f + hop, 0f, 1.28f, 1.28f, 1.12f, c)
        drawPart(0f, 1.22f + hop, 0f, 0.72f, 0.18f, 1.0f, hi)
        drawPart(0f, 1.26f + hop, 0.42f, 0.68f, 0.44f, 0.56f, cd)
        drawPart(-0.28f, 1.34f + hop, 0.58f, 0.20f, 0.20f, 0.12f, floatArrayOf(1f, 0.35f, 0.35f, 1f))
        drawPart(0.28f, 1.34f + hop, 0.58f, 0.20f, 0.20f, 0.12f, floatArrayOf(1f, 0.35f, 0.35f, 1f))
        val beak = sin(phase * 2.8f) * 8f
        pushModel(0f, 1.10f + hop, 0.78f)
        Matrix.rotateM(model, 0, beak, 0f, 0f, 1f)
        drawPart(0f, 0f, 0.22f, 0.18f, 0.14f, 0.36f, cd)
        popModel()
    }

    /** 夜间路灯：保持跑道可读性（仅草原世界） */
    private fun drawStreetLamps() {
        if (nightAmt < 0.2f || meadowW < 0.5f) return
        mMode = 2
        scroll(22f, game.distance) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * 4.6f
            pushModel(x, 0f, z)
            drawPart(0f, 1.6f, 0f, 0.12f, 3.2f, 0.12f, LAMP_POLE)
            drawPart(side * -0.35f, 3.15f, 0f, 0.7f, 0.1f, 0.1f, LAMP_POLE)
            drawPart(side * -0.7f, 3.0f, 0f, 0.35f, 0.35f, 0.35f, LAMP)
            popModel()
        }
        mMode = 0
    }

    private fun drawTree(x: Float, z: Float, k: Float, leaf: FloatArray) {
        pushModel(x, 0f, z)
        drawPart(0f, 0.7f * k, 0f, 0.45f * k, 1.4f * k, 0.45f * k, TREE_TRUNK)
        drawPart(0f, 2.1f * k, 0f, 2.3f * k, 1.6f * k, 2.3f * k, leaf)
        drawPart(0f, 3.2f * k, 0f, 1.6f * k, 1.2f * k, 1.6f * k, leaf)
        drawPart(0f, 4.0f * k, 0f, 0.9f * k, 0.8f * k, 0.9f * k, leaf)
        popModel()
    }

    // ---------- 实体 ----------
    private fun drawEntities() {
        synchronized(game) {
            for (zip in game.ziplines) drawZipline(zip)
            for (e in game.entities) {
                val x = e.x
                when (e.kind) {
                    Game.COIN -> {
                        if (e.z > 2.5f) continue
                        e.spin += if (e.isRelic) 1.4f else 3f
                        if (e.z < 1.5f && e.y < 2f) drawShadow(x, e.z, 0.5f)
                        if (e.isRelic) {
                            // 文物：木底座 + 稀有度配色的小鼎 + 光核，缓慢旋转漂浮
                            val rarity = Game.RELIC_RARITY[e.relicId.coerceIn(0, Game.RELIC_COUNT - 1)]
                            val body = RELIC_BODY[rarity.coerceIn(0, RELIC_BODY.size - 1)]
                            pushModel(x, e.y + sin(e.spin * 0.05f) * 0.10f, e.z)
                            Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                            drawPart(0f, -0.42f, 0f, 0.66f, 0.12f, 0.66f, RELIC_BASE)
                            drawPart(0f, 0.02f, 0f, 0.56f, 0.44f, 0.46f, body)
                            drawPart(-0.20f, 0.34f, 0f, 0.10f, 0.18f, 0.10f, body)
                            drawPart(0.20f, 0.34f, 0f, 0.10f, 0.18f, 0.10f, body)
                            drawPart(-0.18f, -0.28f, 0.14f, 0.10f, 0.22f, 0.10f, body)
                            drawPart(0.18f, -0.28f, 0.14f, 0.10f, 0.22f, 0.10f, body)
                            drawPart(0f, -0.28f, -0.16f, 0.10f, 0.22f, 0.10f, body)
                            drawPart(0f, 0.02f, 0f, 0.24f, 0.24f, 0.50f, RELIC_GLOW)
                            popModel()
                        } else {
                            // 金币：加法光晕 + 不透明亮金本体（避免半透明叠暗路变成土黄）
                            val bob = sin(e.spin * 0.05f) * 0.10f
                            pushModel(x, e.y + bob, e.z)
                            Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                            // 贴近地面的金币缩小光晕，减少对矮障碍的遮挡
                            val halo = if (e.y < 1.35f) 0.82f else 1.15f
                            emitGlowHalo(0f, 0f, 0f, halo, halo, 0.55f, GOLD_GLOW)
                            emitPart(0f, 0f, 0f, 0.82f, 0.82f, 0.14f, GOLD_RIM)
                            emitPart(0f, 0f, 0f, 0.72f, 0.72f, 0.26f, GOLD)
                            emitPart(0f, 0f, 0.04f, 0.42f, 0.42f, 0.28f, GOLD_CORE)
                            popModel()
                        }
                    }
                    Game.P_MAGNET -> {
                        e.spin += 2f
                        if (e.z < 1.5f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y + sin(e.spin * 0.05f) * 0.12f, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        drawPart(0f, 0.22f, 0f, 0.56f, 0.2f, 0.2f, MAGNET_RED)
                        drawPart(-0.19f, -0.05f, 0f, 0.18f, 0.4f, 0.2f, MAGNET_RED)
                        drawPart(0.19f, -0.05f, 0f, 0.18f, 0.4f, 0.2f, MAGNET_RED)
                        drawPart(-0.19f, -0.3f, 0f, 0.18f, 0.12f, 0.2f, MAGNET_TIP)
                        drawPart(0.19f, -0.3f, 0f, 0.18f, 0.12f, 0.2f, MAGNET_TIP)
                        popModel()
                    }
                    Game.P_HELMET -> {
                        e.spin += 2f
                        if (e.z < 1.5f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y + sin(e.spin * 0.05f) * 0.12f, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        drawPart(0f, 0.1f, 0f, 0.5f, 0.34f, 0.5f, HELMET_Y)
                        drawPart(0f, 0.24f, 0f, 0.3f, 0.14f, 0.34f, HELMET_Y)
                        drawPart(0f, -0.1f, 0f, 0.72f, 0.1f, 0.72f, HELMET_Y)
                        popModel()
                    }
                    Game.P_DOUBLE -> {
                        e.spin += 3f
                        if (e.z < 1.5f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y + sin(e.spin * 0.05f) * 0.12f, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        drawPart(0f, 0f, 0f, 0.55f, 0.55f, 0.55f, DOUBLE_P)
                        pushModel(0f, 0f, 0f)
                        Matrix.rotateM(model, 0, 45f, 0f, 1f, 0f)
                        Matrix.rotateM(model, 0, 45f, 1f, 0f, 0f)
                        drawPart(0f, 0f, 0f, 0.42f, 0.42f, 0.42f, DOUBLE_CORE)
                        popModel()
                        popModel()
                    }
                    Game.P_BOOST -> {
                        e.spin += 4f
                        if (e.z < 1.5f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y + sin(e.spin * 0.05f) * 0.12f, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        // 闪电 ⚡：三段 Z 轴旋转折线 + 亮核
                        emitGlowHalo(0f, 0f, 0f, 0.88f, 0.88f, 0.42f, BOOST_TRAIL)
                        pushModel(-0.03f, 0.26f, 0f)
                        Matrix.rotateM(model, 0, -26f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.26f, 0.42f, 0.2f, BOOST_CYAN)
                        popModel()
                        pushModel(0.06f, -0.01f, 0f)
                        Matrix.rotateM(model, 0, 118f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.26f, 0.30f, 0.2f, BOOST_CYAN)
                        popModel()
                        pushModel(-0.02f, -0.26f, 0f)
                        Matrix.rotateM(model, 0, -28f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.26f, 0.40f, 0.2f, BOOST_CYAN)
                        popModel()
                        pushModel(-0.03f, 0.26f, 0.02f)
                        Matrix.rotateM(model, 0, -26f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.13f, 0.34f, 0.18f, BOOST_CORE)
                        popModel()
                        pushModel(0.06f, -0.01f, 0.02f)
                        Matrix.rotateM(model, 0, 118f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.13f, 0.24f, 0.18f, BOOST_CORE)
                        popModel()
                        pushModel(-0.02f, -0.26f, 0.02f)
                        Matrix.rotateM(model, 0, -28f, 0f, 0f, 1f)
                        drawPart(0f, 0f, 0f, 0.13f, 0.32f, 0.18f, BOOST_CORE)
                        popModel()
                        popModel()
                    }
                    Game.OBST_LOW -> {
                        if (e.z < 2.5f) drawShadow(x, e.z, 1.0f)
                        drawObstLow(x, e.z)
                    }
                    Game.OBST_BAR -> {
                        if (e.z < 2.5f) drawShadow(x, e.z, 0.7f)
                        drawObstBar(x, e.z)
                    }
                    Game.OBST_BLOCK -> {
                        if (e.z < 2.5f) drawShadow(x, e.z, 1.15f)
                        drawObstBlock(x, e.z)
                    }
                    Game.OBST_RAMP -> drawRamp(x, e.z)
                }
            }
            // 警示条叠在拾取物之上，保证矮障碍远距可读
            drawObstacleDangerOverlays()
        }
    }

    /** 二次绘制障碍警示面（不写深度），避免被金币光晕挡住 */
    private fun drawObstacleDangerOverlays() {
        mMode = 2
        GLES20.glDepthMask(false)
        for (e in game.entities) {
            if (e.z > 45f || e.z < -70f) continue
            when (e.kind) {
                Game.OBST_LOW -> {
                    pushModel(e.x, 0f, e.z)
                    drawLowDangerFace()
                    popModel()
                }
                Game.OBST_BAR -> {
                    pushModel(e.x, 0f, e.z)
                    drawBarDangerFace()
                    popModel()
                }
                Game.OBST_BLOCK -> {
                    pushModel(e.x, 0f, e.z)
                    drawBlockDangerFace()
                    popModel()
                }
            }
        }
        GLES20.glDepthMask(true)
        mMode = 0
    }

    private fun drawRamp(x: Float, z: Float) {
        val length = Game.RAMP_LENGTH
        val height = Game.RAMP_HEIGHT
        val slopeLength = sqrt(length * length + height * height)
        val angle = Math.toDegrees(atan(height / length).toDouble()).toFloat()

        pushModel(x, height / 2f, z)
        Matrix.rotateM(model, 0, angle, 1f, 0f, 0f)
        drawPart(0f, 0f, 0f, 1.9f, 0.24f, slopeLength, RAMP_YELLOW)
        drawPart(-0.88f, 0.16f, 0f, 0.14f, 0.24f, slopeLength, RAMP_EDGE)
        drawPart(0.88f, 0.16f, 0f, 0.14f, 0.24f, slopeLength, RAMP_EDGE)
        popModel()

        pushModel(x, 0f, z - length / 2f - 0.65f)
        drawPart(0f, 0.62f, 0f, 1.9f, 1.24f, 0.72f, CONCRETE)
        for (i in -2..2) {
            drawPart(i * 0.38f, 0.72f, -0.37f, 0.18f, 0.28f, 0.05f, if (i % 2 == 0) WARNING else DASH)
        }
        popModel()
    }

    /** 发光装饰块（自发光，不受光照；仍受雾影响） */
    private fun glowPart(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
        mMode = 2
        drawPart(x, y, z, sx, sy, sz, color)
        mMode = 0
    }

    /** 拾取物自发光：不吃雾、不吃光照；可选加法光晕 */
    private fun emitPart(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
        mMode = 3
        drawPart(x, y, z, sx, sy, sz, color)
        mMode = 0
    }

    private fun emitGlowHalo(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray) {
        mMode = 3
        GLES20.glDepthMask(false)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        drawPart(x, y, z, sx, sy, sz, color)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(true)
        mMode = 0
    }

    /**
     * 朝向玩家（+Z）的警示面：红/黄条带，远距也能把障碍和路边景物分开。
     * @param y 条带中心高度  @param w 宽度  @param bandH 单条高度
     */
    private fun dangerFace(y: Float, w: Float, bandH: Float = 0.14f, z: Float = 0.10f) {
        glowPart(0f, y, z, w, bandH, 0.08f, DANGER_FACE)
        glowPart(0f, y + bandH * 0.85f, z + 0.02f, w * 0.92f, bandH * 0.55f, 0.06f, DANGER_FACE_LT)
    }

    /** 矮障碍（需跳过）：草原=施工水马，其余宇宙主题化 */
    private fun drawObstLow(x: Float, z: Float) {
        pushModel(x, 0f, z)
        when (game.universe) {
            Game.UNI_WATER -> {
                drawPart(0f, 0.38f, 0f, 1.9f, 0.76f, 0.62f, CORAL_ORANGE)
                drawPart(-0.5f, 0.68f, 0f, 0.42f, 0.5f, 0.42f, CORAL_PINK)
                drawPart(0.52f, 0.60f, 0f, 0.4f, 0.4f, 0.4f, CORAL_PINK)
                drawPart(0f, 0.55f, 0f, 0.3f, 0.72f, 0.3f, SEAWEED)
            }
            Game.UNI_SKY -> {
                drawPart(0f, 0.40f, 0f, 1.9f, 0.56f, 0.72f, ISLAND_DIRT)
                drawPart(0f, 0.18f, 0f, 1.5f, 0.44f, 0.6f, ISLAND_DIRT_DK)
                drawPart(0f, 0.70f, 0f, 1.94f, 0.18f, 0.76f, GRASS)
            }
            Game.UNI_LAVA -> {
                drawPart(0f, 0.44f, 0f, 1.9f, 0.80f, 0.7f, OBSIDIAN)
                drawPart(-0.55f, 0.64f, 0f, 0.4f, 0.44f, 0.4f, OBSIDIAN)
                glowPart(0f, 0.46f, 0.06f, 1.7f, 0.14f, 0.72f, LAVA_GLOW)
                glowPart(0f, 0.46f, 0.06f, 0.5f, 0.2f, 0.74f, LAVA_CORE)
            }
            Game.UNI_CANDY -> {
                drawPart(0f, 0.38f, 0f, 1.88f, 0.72f, 0.56f, CANDY_STICK)
                drawPart(0f, 0.76f, 0f, 1.94f, 0.18f, 0.62f, CANDY_RED)
                for (i in -1..1) drawPart(i * 0.62f, 0.38f, 0.02f, 0.16f, 0.66f, 0.6f, CANDY_RED)
            }
            Game.UNI_SPACE -> {
                drawPart(0f, 0.44f, 0f, 1.9f, 0.78f, 0.7f, ASTEROID)
                drawPart(-0.5f, 0.66f, 0f, 0.4f, 0.4f, 0.4f, ASTEROID_DK)
                glowPart(0f, 0.46f, 0.08f, 1.75f, 0.22f, 0.74f, CRYSTAL_CYAN)
                glowPart(0f, 0.24f, 0.10f, 1.6f, 0.12f, 0.72f, CRYSTAL_PURPLE)
            }
            else -> { // 草原：塑料水马（白身红顶红竖纹 + 混凝土底座）
                drawPart(0f, 0.06f, 0f, 1.9f, 0.12f, 0.62f, CONCRETE)
                drawPart(0f, 0.40f, 0f, 1.86f, 0.68f, 0.5f, BARRIER_WHITE)
                drawPart(0f, 0.78f, 0f, 1.94f, 0.20f, 0.58f, BARRIER_RED)
                for (i in -1..1) drawPart(i * 0.62f, 0.40f, 0.02f, 0.12f, 0.68f, 0.54f, BARRIER_RED)
            }
        }
        popModel()
    }

    private fun drawLowDangerFace() {
        when (game.universe) {
            Game.UNI_WATER -> dangerFace(0.38f, 1.7f)
            Game.UNI_SKY -> dangerFace(0.40f, 1.7f)
            Game.UNI_LAVA -> dangerFace(0.26f, 1.75f, 0.12f, 0.14f)
            Game.UNI_CANDY -> dangerFace(0.38f, 1.7f)
            Game.UNI_SPACE -> dangerFace(0.60f, 1.7f, 0.12f, 0.14f)
            else -> dangerFace(0.58f, 1.7f, 0.14f, 0.16f)
        }
    }

    /** 悬空障碍（需下滑钻过）：草原=道闸横杆，其余宇宙主题化 */
    private fun drawObstBar(x: Float, z: Float) {
        pushModel(x, 0f, z)
        when (game.universe) {
            Game.UNI_WATER -> {
                drawPart(-0.95f, 1.0f, 0f, 0.3f, 2.0f, 0.3f, CORAL_ORANGE)
                drawPart(0.95f, 1.0f, 0f, 0.3f, 2.0f, 0.3f, CORAL_ORANGE)
                drawPart(0f, 1.85f, 0f, 2.1f, 0.5f, 0.42f, CORAL_PINK)
                for (i in -2..2) drawPart(i * 0.4f, 1.42f, 0f, 0.14f, 0.5f, 0.14f, SEAWEED)
            }
            Game.UNI_SKY -> {
                drawPart(-0.95f, 1.0f, 0f, 0.26f, 2.0f, 0.26f, ISLAND_DIRT)
                drawPart(0.95f, 1.0f, 0f, 0.26f, 2.0f, 0.26f, ISLAND_DIRT)
                for (i in RAINBOW_SOLID.indices) {
                    drawPart(-0.8f + i * 0.4f, 1.8f, 0f, 0.42f, 0.36f, 0.4f, RAINBOW_SOLID[i])
                }
            }
            Game.UNI_LAVA -> {
                drawPart(-0.95f, 1.0f, 0f, 0.3f, 2.0f, 0.3f, OBSIDIAN)
                drawPart(0.95f, 1.0f, 0f, 0.3f, 2.0f, 0.3f, OBSIDIAN)
                drawPart(0f, 1.82f, 0f, 2.1f, 0.55f, 0.4f, OBSIDIAN)
                glowPart(0f, 1.56f, 0.06f, 1.9f, 0.14f, 0.42f, LAVA_GLOW)
                glowPart(0f, 1.56f, 0.06f, 1.6f, 0.18f, 0.44f, LAVA_CORE)
            }
            Game.UNI_CANDY -> {
                drawPart(-0.95f, 1.0f, 0f, 0.28f, 2.0f, 0.28f, CANDY_STICK)
                drawPart(0.95f, 1.0f, 0f, 0.28f, 2.0f, 0.28f, CANDY_STICK)
                for (i in -2..2) {
                    drawPart(i * 0.42f, 1.8f, 0f, 0.44f, 0.4f, 0.4f, if (i % 2 == 0) CANDY_RED else CANDY_STICK)
                }
            }
            Game.UNI_SPACE -> {
                drawPart(-0.95f, 1.0f, 0f, 0.28f, 2.0f, 0.28f, ASTEROID_DK)
                drawPart(0.95f, 1.0f, 0f, 0.28f, 2.0f, 0.28f, ASTEROID_DK)
                drawPart(0f, 1.82f, 0f, 2.1f, 0.28f, 0.36f, ASTEROID)
                glowPart(0f, 1.64f, 0.04f, 2.05f, 0.20f, 0.38f, CRYSTAL_CYAN)
                glowPart(0f, 2.02f, 0.04f, 2.05f, 0.16f, 0.38f, CRYSTAL_PURPLE)
            }
            else -> { // 草原：停车道闸（黄机箱 + 红白横杆）
                drawPart(-1.0f, 0.72f, 0f, 0.36f, 1.44f, 0.36f, GATE_BOX)
                drawPart(-1.0f, 1.5f, 0f, 0.44f, 0.24f, 0.44f, METAL)
                for (i in 0 until 5) {
                    drawPart(-0.72f + i * 0.44f, 1.62f, 0f, 0.44f, 0.2f, 0.24f,
                        if (i % 2 == 0) BARRIER_RED else BARRIER_WHITE)
                }
                drawPart(0.98f, 1.62f, 0f, 0.14f, 0.36f, 0.24f, CONCRETE)
            }
        }
        popModel()
    }

    private fun drawBarDangerFace() {
        when (game.universe) {
            Game.UNI_WATER -> dangerFace(1.85f, 2.0f, 0.12f, 0.14f)
            Game.UNI_SKY -> dangerFace(1.55f, 2.0f, 0.12f, 0.14f)
            Game.UNI_LAVA -> dangerFace(1.95f, 2.0f, 0.12f, 0.14f)
            Game.UNI_CANDY -> dangerFace(1.55f, 2.0f, 0.12f, 0.14f)
            Game.UNI_SPACE -> dangerFace(1.82f, 2.0f, 0.14f, 0.16f)
            else -> dangerFace(1.62f, 2.0f, 0.12f, 0.14f)
        }
    }

    /** 实心障碍（需变道/冲刺撞碎）：草原=摞起的施工路障桶，其余宇宙主题化 */
    private fun drawObstBlock(x: Float, z: Float) {
        pushModel(x, 0f, z)
        when (game.universe) {
            Game.UNI_WATER -> {
                drawPart(0f, 1.05f, 0f, 1.85f, 2.1f, 1.15f, CORAL_ORANGE)
                drawPart(0f, 1.1f, 0.03f, 1.6f, 1.8f, 1.15f, CORAL_PINK)
                drawPart(-0.4f, 2.2f, 0f, 0.4f, 0.6f, 0.4f, SEAWEED)
                drawPart(0.4f, 2.15f, 0f, 0.3f, 0.5f, 0.3f, SEAWEED)
            }
            Game.UNI_SKY -> {
                drawPart(0f, 1.0f, 0f, 1.85f, 2.0f, 1.15f, ISLAND_DIRT)
                drawPart(0f, 0.45f, 0f, 1.6f, 0.9f, 1.1f, ISLAND_DIRT_DK)
                drawPart(0f, 2.06f, 0f, 1.95f, 0.2f, 1.25f, GRASS)
            }
            Game.UNI_LAVA -> {
                drawPart(0f, 1.05f, 0f, 1.85f, 2.1f, 1.15f, OBSIDIAN)
                glowPart(-0.4f, 1.05f, 0.08f, 0.22f, 1.9f, 1.18f, LAVA_GLOW)
                glowPart(0.4f, 1.05f, 0.08f, 0.22f, 1.9f, 1.18f, LAVA_GLOW)
                glowPart(0f, 2.1f, 0.04f, 1.6f, 0.24f, 1.2f, LAVA_CORE)
            }
            Game.UNI_CANDY -> {
                drawPart(0f, 1.05f, 0f, 1.85f, 2.1f, 1.15f, GUMDROP[0])
                drawPart(0f, 1.1f, 0.03f, 1.5f, 1.7f, 1.15f, CANDY_STICK)
                drawPart(0f, 2.18f, 0f, 1.7f, 0.3f, 1.2f, CANDY_RED)
            }
            Game.UNI_SPACE -> {
                drawPart(0f, 1.05f, 0f, 1.85f, 2.1f, 1.15f, ASTEROID)
                drawPart(0f, 1.05f, -0.08f, 1.55f, 1.7f, 0.9f, ASTEROID_DK)
                glowPart(0f, 1.05f, 0.10f, 0.28f, 1.95f, 1.18f, CRYSTAL_CYAN)
                glowPart(-0.55f, 1.05f, 0.10f, 0.18f, 1.7f, 1.18f, CRYSTAL_PURPLE)
                glowPart(0.55f, 1.05f, 0.10f, 0.18f, 1.7f, 1.18f, CRYSTAL_PURPLE)
            }
            else -> { // 草原：摞起的施工路障桶（橙身白反光环）
                for (bx in floatArrayOf(-0.6f, 0f, 0.6f)) {
                    drawPart(bx, 0.6f, 0.22f, 0.54f, 1.2f, 0.54f, CONE_ORANGE)
                    drawPart(bx, 0.75f, 0.22f, 0.58f, 0.16f, 0.58f, BARRIER_WHITE)
                    drawPart(bx, 0.42f, 0.22f, 0.58f, 0.16f, 0.58f, BARRIER_WHITE)
                }
                for (bx in floatArrayOf(-0.32f, 0.32f)) {
                    drawPart(bx, 1.68f, -0.12f, 0.54f, 1.12f, 0.54f, CONE_ORANGE)
                    drawPart(bx, 1.82f, -0.12f, 0.58f, 0.16f, 0.58f, BARRIER_WHITE)
                }
            }
        }
        popModel()
    }

    private fun drawBlockDangerFace() {
        when (game.universe) {
            Game.UNI_WATER -> dangerFace(1.05f, 1.7f, 0.16f, 0.16f)
            Game.UNI_SKY -> dangerFace(1.0f, 1.7f, 0.16f, 0.16f)
            Game.UNI_LAVA -> dangerFace(1.05f, 1.7f, 0.18f, 0.18f)
            Game.UNI_CANDY -> dangerFace(1.05f, 1.7f, 0.16f, 0.16f)
            Game.UNI_SPACE -> {
                dangerFace(1.55f, 1.7f, 0.18f, 0.18f)
                dangerFace(0.55f, 1.7f, 0.14f, 0.18f)
            }
            else -> dangerFace(1.05f, 1.7f, 0.16f, 0.16f)
        }
    }

    private fun drawZipline(zip: Game.Zip) {
        val x = Game.LANE_X[zip.lane]
        val h = Game.CABLE_H
        val midZ = (zip.entryZ + zip.exitZ) / 2f
        drawBox(x, h, midZ, 0.06f, 0.06f, zip.length, CABLE)
        drawGantry(x, zip.entryZ, GANTRY_IN)
        drawGantry(x, zip.exitZ, GANTRY)
    }

    private fun drawGantry(x: Float, z: Float, color: FloatArray) {
        if (z < -200f || z > 10f) return
        val h = Game.CABLE_H
        pushModel(x, 0f, z)
        drawPart(-1.0f, h / 2f, 0f, 0.16f, h, 0.16f, color)
        drawPart(1.0f, h / 2f, 0f, 0.16f, h, 0.16f, color)
        drawPart(0f, h + 0.1f, 0f, 2.3f, 0.2f, 0.2f, color)
        popModel()
    }

    private fun drawParticles() {
        synchronized(game) {
            mMode = 2
            for (p in game.particles) {
                val a = (p.life / 0.7f).coerceIn(0f, 1f)
                val col = floatArrayOf(p.color[0], p.color[1], p.color[2], a)
                drawBox(p.x, p.y, p.z, p.size, p.size, p.size, col)
            }
            mMode = 0
        }
    }

    private fun drawSpeedLines(dt: Float) {
        if (game.state != Game.State.RUNNING) return
        val t = ((game.speed - 16f) / 14f).coerceIn(0f, 1f)
        val boost = if (game.boosting) 1f else 0f
        val strength = (t * 0.7f + boost * 0.5f).coerceIn(0f, 1f)
        if (strength < 0.08f) return
        mMode = 2
        val n = (lineX.size * strength).toInt().coerceAtLeast(4)
        val scroll = game.speed * dt * 2.2f
        for (i in 0 until n) {
            lineZ[i] += scroll
            if (lineZ[i] > 6f) {
                lineZ[i] = -28f - prand.nextFloat() * 10f
                lineX[i] = prand.nextFloat() * 10f - 5f
                lineY[i] = 0.4f + prand.nextFloat() * 3.5f
            }
            val len = 0.8f + strength * 1.8f + boost
            val col = if (boost > 0f) BOOST_TRAIL else SPEED_LINE
            drawBox(camX * 0.3f + lineX[i], lineY[i], lineZ[i], 0.04f, 0.04f, len, col)
        }
        mMode = 0
    }

    private fun drawShadow(x: Float, z: Float, size: Float) {
        mMode = 1
        pushModel(x, 0.015f, z)
        drawPart(0f, 0f, 0f, size * 2.4f, 0.02f, size * 2.4f, SHADOW)
        popModel()
        mMode = 0
    }

    /**
     * 双脚光迹：0 无 / 1 青 / 2 金 / 3 彩虹；冲刺时叠加强化。
     * 两条低矮光带沿换道与跳跃轨迹弯曲，既能表现速度，又不会遮住角色本身。
     */
    private fun drawCosmeticTrail(g: Game, dt: Float) {
        val style = g.trailStyle
        if (style == 0) {
            trailCount = 0
            trailEmit = 0f
            return
        }
        val boost = g.boosting

        val dz = g.speed * dt
        // 已有采样点随世界后移 + 老化
        for (i in 0 until trailCount) {
            trailZ[i] += dz
            trailAge[i] += dt
        }
        // 淘汰过老/出屏的点（保持队列前段有效即可，简单压缩）
        var w = 0
        val maxAge = if (boost) 0.42f else 0.30f
        val maxZ = if (boost) 3.8f else 2.8f
        for (i in 0 until trailCount) {
            if (trailAge[i] < maxAge && trailZ[i] < maxZ) {
                trailX[w] = trailX[i]; trailY[w] = trailY[i]
                trailZ[w] = trailZ[i]; trailAge[w] = trailAge[i]
                trailSeed[w] = trailSeed[i]
                w++
            }
        }
        trailCount = w

        // 按间隔记录双脚中心；离地时光迹随跳跃形成弧线
        if (!g.paused && g.state == Game.State.RUNNING) {
            trailEmit -= dt
            val emitGap = if (boost) 0.040f else 0.065f
            if (trailEmit <= 0f && trailCount < trailX.size) {
                trailEmit = emitGap
                val i = trailCount++
                trailX[i] = g.catX
                trailY[i] = g.catY + 0.08f
                trailZ[i] = 0.18f
                trailAge[i] = 0f
                trailSeed[i] = prand.nextFloat() * 6.28f
            }
        }

        // 自发光、不吃雾：避免在夜路面上混成灰褐「脚印块」
        mMode = 3
        GLES20.glDepthMask(false)
        for (i in 0 until trailCount) {
            val t = (trailAge[i] / maxAge).coerceIn(0f, 1f)   // 0 新 → 1 将消失
            val fade = (1f - t) * (1f - t) * (1f - t)
            val base = when (style) {
                1 -> TRAIL_CYAN
                2 -> TRAIL_GOLD
                3 -> TRAIL_RAINBOW[((trailSeed[i] * 3f + trailAge[i] * 9f).toInt()) % TRAIL_RAINBOW.size]
                else -> BOOST_TRAIL
            }
            val boostK = if (boost) 1.25f else 1f
            val a = 0.62f * fade * boostK
            if (a < 0.04f) continue
            val col = floatArrayOf(base[0], base[1], base[2], a.coerceAtMost(0.72f))
            val width = 0.18f * (1f - t * 0.45f) * boostK
            val length = 0.34f * (1f - t * 0.25f) * boostK
            val footGap = 0.31f * CAT_SCALE
            val rise = t * 0.06f
            drawBox(trailX[i] - footGap, trailY[i] + rise, trailZ[i], width, 0.045f, length, col)
            drawBox(trailX[i] + footGap, trailY[i] + rise, trailZ[i], width, 0.045f, length, col)

            // 金色和彩虹附带稀疏星屑
            if (style >= 2 && i % 3 == 0) {
                val tw = 0.4f + 0.6f * abs(sin(trailSeed[i] * 5f + trailAge[i] * 18f))
                val spA = (a * tw).coerceAtMost(0.75f)
                if (spA >= 0.04f) {
                    val sp = floatArrayOf(1f, 1f, 0.92f, spA)
                    val ss = 0.08f * boostK * (1f - t * 0.5f)
                    drawBox(
                        trailX[i] + sin(trailSeed[i]) * 0.48f,
                        trailY[i] + 0.18f + t * 0.30f,
                        trailZ[i],
                        ss, ss, ss, sp
                    )
                }
            }
        }
        GLES20.glDepthMask(true)
        mMode = 0
    }

    // ---------- 像素猫 ----------
    private fun drawCat(dt: Float) {
        val g = game
        powerFxPhase = (powerFxPhase + dt * 5f) % 1000f
        drawShadow(g.catX, 0f, (0.95f - min(g.catY * 0.10f, 0.3f)) * CAT_SCALE)

        val squash = if (g.sliding) 0.5f else 1f
        val bob = if (g.onGround && g.state == Game.State.RUNNING) abs(sin(g.runPhase)) * 0.07f else 0f

        if (g.state == Game.State.RUNNING) {
            drawCosmeticTrail(g, dt)
        } else {
            trailCount = 0
        }

        pushModel(g.catX, g.catY + bob, 0f)
        if (g.state == Game.State.DEAD) {
            Matrix.rotateM(model, 0, -65f, 1f, 0f, 0f)
            Matrix.translateM(model, 0, 0f, 0.3f * CAT_SCALE, 0.3f * CAT_SCALE)
        }
        val c = CAT_MAIN[game.catColor % CAT_MAIN.size]
        val cd = CAT_DK[game.catColor % CAT_DK.size]

        val ridingZip = g.riding != null
        val lean = (Game.LANE_X[g.lane] - g.catX) * 9f
        Matrix.rotateM(model, 0, -lean, 0f, 0f, 1f)
        // 滑索握杆在缩小前绘制，保证顶端仍接到世界坐标缆绳
        if (ridingZip) {
            val reach = 1.9f * CAT_SCALE
            val gripTop = Game.CABLE_H - g.catY
            drawPart(0f, (reach + gripTop) / 2f, -0.2f, 0.1f, gripTop - reach, 0.1f, cd)
            drawPart(0f, gripTop - 0.1f, -0.2f, 0.3f, 0.2f, 0.24f, GANTRY)
        }
        Matrix.scaleM(model, 0, CAT_SCALE, squash * CAT_SCALE, CAT_SCALE)
        if (!g.onGround && !ridingZip && g.state == Game.State.RUNNING) {
            Matrix.rotateM(model, 0, if (g.velY > 0) 14f else -10f, 1f, 0f, 0f)
        }

        // 尾巴：同色三节，从背部正中上翘，左右轻摆
        val wag = sin(g.runPhase * 0.7f) * 0.12f
        drawPart(wag, 0.95f, 0.72f, 0.22f, 0.22f, 0.28f, c)
        drawPart(wag * 1.6f, 1.25f, 0.85f, 0.20f, 0.28f, 0.20f, c)
        drawPart(wag * 2.2f, 1.55f, 0.95f, 0.22f, 0.26f, 0.22f, cd)

        // 四腿：略外撇，俯视能看见爪垫
        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val lx = if (left) -0.38f else 0.38f
            val lz = if (front) -0.42f else 0.42f
            val phase = g.runPhase + if (i == 0 || i == 3) 0f else Math.PI.toFloat()
            val swing = when {
                g.sliding -> 65f
                !g.onGround -> if (front) -35f else 30f
                g.state == Game.State.RUNNING -> sin(phase) * 38f
                else -> 0f
            }
            pushModel(lx, 0.52f, lz)
            Matrix.rotateM(model, 0, swing, 1f, 0f, 0f)
            drawPart(0f, -0.22f, 0f, 0.26f, 0.48f, 0.26f, if (left) c else cd)
            drawPart(0f, -0.50f, 0.02f, 0.28f, 0.14f, 0.30f, CAT_WHITE)
            popModel()
        }

        // 身体：偏矮偏长，俯视像猫而不是竖柱
        drawPart(0f, 0.72f, 0f, 1.05f, 0.72f, 1.45f, c)
        drawPart(0f, 0.48f, -0.05f, 0.78f, 0.36f, 1.1f, CAT_WHITE)
        drawPart(0f, 0.95f, 0.28f, 1.08f, 0.22f, 0.32f, cd)
        drawPart(0f, 0.95f, -0.28f, 1.08f, 0.22f, 0.32f, cd)

        if (g.scarfStyle > 0) {
            val sc = SCARF_COLS[g.scarfStyle % SCARF_COLS.size]
            drawPart(0f, 1.12f, -0.35f, 1.05f, 0.20f, 0.34f, sc)
            val fl = sin(g.runPhase * 1.1f) * 0.15f
            drawPart(0.22f, 1.10f + fl * 0.4f, 0.25f, 0.24f, 0.16f, 0.55f, sc)
            drawPart(0.22f, 1.04f + fl, 0.72f, 0.20f, 0.13f, 0.45f, sc)
        }
        drawActivePowerUps(g)

        // 头：略靠前；俯视时耳朵是主要辨识点
        pushModel(0f, 1.48f, -0.55f)
        if (g.state == Game.State.DEAD) {
            Matrix.rotateM(model, 0, 25f, 1f, 0f, 0f)
        } else if (g.state == Game.State.RUNNING) {
            val nod = when {
                g.sliding -> 12f
                !g.onGround && !ridingZip -> if (g.velY > 0) -6f else 4f
                else -> sin(g.runPhase * 2f) * 2.5f
            }
            // 变道时头侧看，直线时微摆 —— 让侧脸偶尔露出来
            val yaw = lean * 1.6f + if (g.onGround && !g.sliding) sin(g.runPhase * 0.55f) * 5f else 0f
            val roll = -lean * 0.35f
            Matrix.rotateM(model, 0, nod, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.rotateM(model, 0, roll, 0f, 0f, 1f)
        }
        drawPart(0f, 0f, 0f, 0.92f, 0.78f, 0.88f, c)
        drawPart(-0.40f, -0.06f, -0.15f, 0.20f, 0.30f, 0.36f, CAT_WHITE)
        drawPart(0.40f, -0.06f, -0.15f, 0.20f, 0.30f, 0.36f, CAT_WHITE)
        drawPart(0f, -0.16f, -0.42f, 0.46f, 0.32f, 0.16f, CAT_WHITE)
        drawPart(0f, -0.10f, -0.50f, 0.14f, 0.10f, 0.08f, CAT_NOSE)
        drawPart(-0.22f, 0.08f, -0.40f, 0.18f, 0.18f, 0.08f, CAT_EYE)
        drawPart(0.22f, 0.08f, -0.40f, 0.18f, 0.18f, 0.08f, CAT_EYE)
        drawPart(-0.18f, 0.12f, -0.44f, 0.06f, 0.06f, 0.04f, CAT_EYE_HL)
        drawPart(0.26f, 0.12f, -0.44f, 0.06f, 0.06f, 0.04f, CAT_EYE_HL)

        // 大三角耳：从正后俯视最显眼
        val earWiggle = if (g.state == Game.State.RUNNING && g.onGround && !g.sliding) {
            sin(g.runPhase * 2.4f) * 5f
        } else 0f
        pushModel(-0.32f, 0.42f, 0.05f)
        Matrix.rotateM(model, 0, -18f - earWiggle, 0f, 0f, 1f)
        drawPart(0f, 0.16f, 0f, 0.28f, 0.42f, 0.18f, cd)
        drawPart(0f, 0.12f, -0.05f, 0.14f, 0.26f, 0.08f, CAT_PINK)
        popModel()
        pushModel(0.32f, 0.42f, 0.05f)
        Matrix.rotateM(model, 0, 18f + earWiggle * 0.85f, 0f, 0f, 1f)
        drawPart(0f, 0.16f, 0f, 0.28f, 0.42f, 0.18f, cd)
        drawPart(0f, 0.12f, -0.05f, 0.14f, 0.26f, 0.08f, CAT_PINK)
        popModel()

        if (!g.helmet && g.hatStyle > 0) {
            when (g.hatStyle) {
                1 -> {
                    drawPart(0f, 0.47f, 0.05f, 0.82f, 0.26f, 0.75f, HAT_RED)
                    drawPart(0f, 0.40f, -0.55f, 0.66f, 0.09f, 0.5f, HAT_RED)
                    drawPart(0f, 0.63f, 0.05f, 0.16f, 0.1f, 0.16f, HAT_RED_DK)
                }
                2 -> {
                    drawPart(0f, 0.42f, 0f, 1.35f, 0.08f, 1.3f, STRAW)
                    drawPart(0f, 0.56f, 0f, 0.72f, 0.24f, 0.7f, STRAW)
                    drawPart(0f, 0.49f, 0f, 0.76f, 0.07f, 0.74f, STRAW_BAND)
                }
                else -> {
                    drawPart(0f, 0.50f, 0f, 0.66f, 0.16f, 0.64f, CROWN_GOLD)
                    for (i in -1..1) {
                        drawPart(i * 0.22f, 0.64f, 0f, 0.12f, 0.14f, 0.12f, CROWN_GOLD)
                    }
                    drawPart(0f, 0.52f, -0.34f, 0.12f, 0.12f, 0.06f, CROWN_RUBY)
                }
            }
        }
        if (g.helmet) {
            drawPart(0f, 0.5f, 0f, 0.96f, 0.3f, 0.9f, HELMET_Y)
            drawPart(0f, 0.34f, -0.08f, 1.06f, 0.1f, 1.04f, HELMET_Y)
            if (g.helmetLayers >= 2) {
                drawPart(0f, 0.62f, 0f, 0.7f, 0.14f, 0.7f, BOOST_CORE)
            }
        }
        popModel()

        popModel()
    }

    /** 把生效中的道具做成猫身上的装备，HUD 之外也能一眼辨认。 */
    private fun drawActivePowerUps(g: Game) {
        if (g.magnetTime > 0f) {
            // 小巧背挂磁铁，避免把猫身剪影撑成「红塔」
            drawPart(0f, 1.05f, 0.78f, 0.48f, 0.14f, 0.12f, MAGNET_RED)
            drawPart(-0.18f, 0.88f, 0.78f, 0.12f, 0.36f, 0.12f, MAGNET_RED)
            drawPart(0.18f, 0.88f, 0.78f, 0.12f, 0.36f, 0.12f, MAGNET_RED)
            drawPart(-0.18f, 0.68f, 0.78f, 0.14f, 0.10f, 0.14f, MAGNET_TIP)
            drawPart(0.18f, 0.68f, 0.78f, 0.14f, 0.10f, 0.14f, MAGNET_TIP)
            // 两侧各一颗电荷，少而干净
            for (i in 0 until 2) {
                val a = powerFxPhase * 2.2f + i * Math.PI.toFloat()
                val x = cos(a) * 0.72f
                val y = 0.95f + sin(a * 1.4f) * 0.18f
                val z = 0.55f
                drawPart(x, y, z, 0.10f, 0.10f, 0.10f, if (i == 0) MAGNET_RED else MAGNET_BLUE)
            }
        }

        if (g.doubleTime > 0f) {
            drawPart(-0.55f, 1.05f, 0f, 0.20f, 0.16f, 0.38f, DOUBLE_P)
            drawPart(0.55f, 1.05f, 0f, 0.20f, 0.16f, 0.38f, DOUBLE_P)
            drawPart(0f, 1.15f, 0.55f, 0.30f, 0.26f, 0.12f, DOUBLE_CORE)
            for (i in 0 until 2) {
                val a = powerFxPhase * 1.25f + i * Math.PI.toFloat()
                pushModel(cos(a) * 0.72f, 1.35f + sin(a * 2f) * 0.14f, sin(a) * 0.55f)
                Matrix.rotateM(model, 0, powerFxPhase * 95f + i * 90f, 0f, 1f, 0f)
                drawPart(0f, 0f, 0f, 0.18f, 0.18f, 0.18f, if (i == 0) DOUBLE_P else DOUBLE_CORE)
                popModel()
            }
        }

        if (g.boostTime > 0f) {
            val flame = 0.28f + abs(sin(powerFxPhase * 3.2f)) * 0.22f
            for (side in intArrayOf(-1, 1)) {
                val x = side * 0.38f
                drawPart(x, 0.85f, 0.72f, 0.24f, 0.40f, 0.26f, BOOST_CYAN)
                drawPart(x, 0.85f, 0.88f, 0.12f, 0.26f, 0.12f, BOOST_CORE)
                drawPart(x, 0.85f, 1.08f, 0.14f, 0.18f, flame, BOOST_CORE)
                drawPart(x, 0.85f, 1.32f + flame * 0.25f, 0.10f, 0.12f, flame * 0.7f, BOOST_CYAN)
            }
        }
    }

    // ---------- 工具 ----------
    private fun argbCol(c: Int, a: Float = 1f): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }

    private inline fun scroll(gap: Float, scrollDist: Float, draw: (m: Int, z: Float) -> Unit) {
        var m = floor((8f - scrollDist) / gap).toInt()
        while (true) {
            val z = m * gap + scrollDist
            if (z < -220f) break
            draw(m, z)
            m--
        }
    }

    private fun mod(a: Int, b: Int): Int = ((a % b) + b) % b

    private fun pushModel(x: Float, y: Float, z: Float) {
        val saved = FloatArray(16)
        if (stack.isEmpty()) Matrix.setIdentityM(model, 0)
        System.arraycopy(model, 0, saved, 0, 16)
        stack.add(saved)
        Matrix.translateM(model, 0, x, y, z)
    }

    private fun popModel() {
        val saved = stack.removeAt(stack.size - 1)
        System.arraycopy(saved, 0, model, 0, 16)
    }

    private fun drawPart(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray
    ) {
        System.arraycopy(model, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, x, y, z)
        Matrix.scaleM(tmp, 0, sx, sy, sz)
        emit(tmp, color)
    }

    private fun drawBox(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: FloatArray) {
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, x, y, z)
        Matrix.scaleM(tmp, 0, w, h, d)
        emit(tmp, color)
    }

    private fun emit(modelM: FloatArray, color: FloatArray) {
        Matrix.multiplyMM(mvp, 0, vp, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
        GLES20.glUniform4fv(uColor, 1, color, 0)
        GLES20.glUniform1i(uMode, mMode)
        cube.draw(aPos, aNormal)
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES20.glCreateShader(type)
        GLES20.glShaderSource(sh, src)
        GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            throw RuntimeException("Shader error: " + GLES20.glGetShaderInfoLog(sh))
        }
        return sh
    }
}
