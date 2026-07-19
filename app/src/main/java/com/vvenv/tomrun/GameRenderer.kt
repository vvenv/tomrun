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

    // 天气 + 昼夜混合后的场景配色
    private val skyCol = FloatArray(4) { 1f }
    private val grassCol = FloatArray(4) { 1f }
    private val grassDarkCol = FloatArray(4) { 1f }
    private val roadCol = FloatArray(4) { 1f }
    private val edgeCol = FloatArray(4) { 1f }
    private val cloudCol = FloatArray(4) { 1f }
    private var wSun = 1f
    private var nightAmt = 0f
    private var duskAmt = 0f

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
            uniform int uMode;        // 0 常规光照 1 软阴影 2 无光照
            varying vec3 vNormal;
            varying vec3 vLocal;
            varying float vDist;
            void main() {
                float fog = smoothstep(55.0, 150.0, vDist) * 0.92;
                if (uMode == 1) {
                    float r = length(vLocal.xz) * 2.0;
                    float a = uColor.a * smoothstep(1.0, 0.30, r);
                    gl_FragColor = vec4(uColor.rgb, a);
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
        private val WOOD = floatArrayOf(0.82f, 0.52f, 0.20f, 1f)
        private val WOOD_DARK = floatArrayOf(0.62f, 0.37f, 0.12f, 1f)
        private val METAL = floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
        private val GOLD = floatArrayOf(1.0f, 0.84f, 0.10f, 1f)
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

        private val RAIN_SKY = floatArrayOf(0.44f, 0.51f, 0.62f, 1f)
        private val SNOW_SKY = floatArrayOf(0.72f, 0.78f, 0.86f, 1f)
        private val GRASS_R = floatArrayOf(0.26f, 0.55f, 0.24f, 1f)
        private val GRASS_S = floatArrayOf(0.84f, 0.88f, 0.93f, 1f)
        private val GRASS_DR = floatArrayOf(0.20f, 0.45f, 0.19f, 1f)
        private val GRASS_DS = floatArrayOf(0.75f, 0.80f, 0.87f, 1f)
        private val ROAD_R = floatArrayOf(0.28f, 0.27f, 0.32f, 1f)
        private val ROAD_S = floatArrayOf(0.52f, 0.54f, 0.60f, 1f)
        private val EDGE_R = floatArrayOf(0.60f, 0.59f, 0.56f, 1f)
        private val EDGE_S = floatArrayOf(0.86f, 0.87f, 0.90f, 1f)
        private val CLOUD_R = floatArrayOf(0.58f, 0.61f, 0.67f, 1f)
        private val RAIN_DROP = floatArrayOf(0.62f, 0.74f, 0.95f, 0.55f)
        private val SNOW_FLAKE = floatArrayOf(0.98f, 0.98f, 1.0f, 0.9f)

        private val MAGNET_RED = floatArrayOf(0.90f, 0.24f, 0.24f, 1f)
        private val MAGNET_TIP = floatArrayOf(0.92f, 0.92f, 0.95f, 1f)
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
        aspect = width.toFloat() / height
        Matrix.perspectiveM(proj, 0, 52f, aspect, 0.5f, 400f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        game.update(dt)

        updateWeatherColors()
        GLES20.glClearColor(skyCol[0], skyCol[1], skyCol[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        setSkyFog()

        // 动态 FOV：速度越高视野越宽；冲刺再加一点
        val spd = game.speed
        val fov = 52f + (spd - 14f) * 0.55f + (if (game.boosting) 4f else 0f)
        Matrix.perspectiveM(proj, 0, fov.coerceIn(50f, 68f), aspect, 0.5f, 400f)

        camX += (game.catX * 0.55f - camX) * min(1f, dt * 6f)
        val shakeAmt = game.shake
        val sx = if (shakeAmt > 0f) sin(now * 0.00000005) * shakeAmt * 0.18f else 0.0
        val sy = if (shakeAmt > 0f) cos(now * 0.00000007) * shakeAmt * 0.12f else 0.0
        val eyeY = 3.6f + game.catY * 0.22f + sy.toFloat()
        val centerY = 1.5f + game.catY * 0.25f
        Matrix.setLookAtM(
            view, 0,
            camX + sx.toFloat(), eyeY, 7.4f,
            camX * 0.5f + sx.toFloat(), centerY, -8f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawSky()
        drawTrack()
        drawScenery()
        drawStreetLamps()
        drawEntities()
        drawParticles()
        drawCat()
        drawSpeedLines(dt)
        drawWeather(dt)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
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

    private fun updateWeatherColors() {
        wSun = weatherWeight(Game.W_SUNNY)
        val wr = weatherWeight(Game.W_RAIN)
        val ws = weatherWeight(Game.W_SNOW)
        nightAmt = game.nightAmount()
        duskAmt = game.duskAmount()

        mix3(skyCol, SKY, RAIN_SKY, SNOW_SKY, wSun, wr, ws)
        mix3(grassCol, GRASS, GRASS_R, GRASS_S, wSun, wr, ws)
        mix3(grassDarkCol, GRASS_DARK, GRASS_DR, GRASS_DS, wSun, wr, ws)
        mix3(roadCol, ROAD, ROAD_R, ROAD_S, wSun, wr, ws)
        mix3(edgeCol, ROAD_EDGE, EDGE_R, EDGE_S, wSun, wr, ws)
        mix3(cloudCol, CLOUD, CLOUD_R, CLOUD, wSun, wr, ws)

        // 昼夜：夜晚压暗并偏蓝紫；黄昏加暖橙
        for (arr in arrayOf(skyCol, grassCol, grassDarkCol, roadCol, edgeCol, cloudCol)) {
            applyDayNight(arr)
        }
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
        val wr = weatherWeight(Game.W_RAIN)
        val ws = weatherWeight(Game.W_SNOW)
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

    // ---------- 天空：太阳/月亮/星星 + 远山 ----------
    private fun drawSky() {
        Matrix.setIdentityM(model, 0)
        stack.clear()

        mMode = 2
        val dayVis = (1f - nightAmt).coerceIn(0f, 1f)
        if (dayVis > 0.05f && wSun > 0.05f) {
            setFog(SUN[0], SUN[1], SUN[2])
            pushModel(20f, 26f - nightAmt * 18f, -110f)
            val k = 7f * wSun * dayVis
            drawPart(0f, 0f, 0f, k, k, k, SUN)
            popModel()
        }
        if (nightAmt > 0.15f) {
            setFog(MOON[0], MOON[1], MOON[2])
            pushModel(-18f, 24f, -110f)
            val k = 5f * nightAmt
            drawPart(0f, 0f, 0f, k, k, k, MOON)
            popModel()
            // 星星
            for (i in 0 until 18) {
                val x = -70f + i * 8.5f + (i % 3) * 3f
                val y = 14f + (i * 7 % 11)
                val tw = 0.25f + 0.15f * abs(sin(game.dayPhase * 40f + i))
                drawBox(x, y, -130f, tw, tw, tw, STAR)
            }
        }
        setSkyFog()

        mMode = 0
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

    // ---------- 路边景物 ----------
    private fun drawScenery() {
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

    /** 夜间路灯：保持跑道可读性 */
    private fun drawStreetLamps() {
        if (nightAmt < 0.2f) return
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
                        e.spin += 3f
                        if (e.z < 1.5f && e.y < 2f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        drawPart(0f, 0f, 0f, 0.62f, 0.62f, 0.2f, GOLD)
                        popModel()
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
                        // 闪电箭：青色外框 + 亮核
                        drawPart(0f, 0.15f, 0f, 0.35f, 0.7f, 0.2f, BOOST_CYAN)
                        drawPart(0.15f, -0.1f, 0f, 0.45f, 0.25f, 0.2f, BOOST_CYAN)
                        drawPart(0f, 0.15f, 0.02f, 0.18f, 0.45f, 0.18f, BOOST_CORE)
                        popModel()
                    }
                    Game.OBST_LOW -> {
                        pushModel(x, 0f, e.z)
                        drawPart(-0.85f, 0.45f, 0f, 0.16f, 0.9f, 0.16f, WOOD_DARK)
                        drawPart(0.85f, 0.45f, 0f, 0.16f, 0.9f, 0.16f, WOOD_DARK)
                        drawPart(0f, 0.72f, 0f, 1.9f, 0.2f, 0.1f, WOOD)
                        drawPart(0f, 0.38f, 0f, 1.9f, 0.2f, 0.1f, WOOD)
                        popModel()
                    }
                    Game.OBST_BAR -> {
                        pushModel(x, 0f, e.z)
                        drawPart(-0.95f, 1.1f, 0f, 0.18f, 2.2f, 0.18f, METAL)
                        drawPart(0.95f, 1.1f, 0f, 0.18f, 2.2f, 0.18f, METAL)
                        drawPart(0f, 1.75f, 0f, 2.1f, 0.9f, 0.35f, WOOD)
                        drawPart(0f, 1.75f, 0.03f, 1.9f, 0.7f, 0.35f, WOOD_DARK)
                        popModel()
                    }
                    Game.OBST_BLOCK -> {
                        pushModel(x, 0f, e.z)
                        drawPart(0f, 1.1f, 0f, 1.9f, 2.2f, 1.2f, WOOD)
                        drawPart(0f, 1.1f, 0.02f, 1.7f, 2.0f, 1.2f, WOOD_DARK)
                        drawPart(0f, 2.14f, 0f, 1.96f, 0.14f, 1.26f, WOOD_DARK)
                        popModel()
                    }
                    Game.OBST_RAMP -> drawRamp(x, e.z)
                }
            }
        }
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

    // ---------- 像素猫 ----------
    private fun drawCat() {
        val g = game
        drawShadow(g.catX, 0f, 0.85f - min(g.catY * 0.10f, 0.3f))

        val squash = if (g.sliding) 0.5f else 1f
        val bob = if (g.onGround && g.state == Game.State.RUNNING) abs(sin(g.runPhase)) * 0.07f else 0f

        // 冲刺尾迹
        if (g.boosting && g.state == Game.State.RUNNING) {
            mMode = 2
            for (i in 1..4) {
                val a = 0.45f - i * 0.08f
                val col = floatArrayOf(BOOST_TRAIL[0], BOOST_TRAIL[1], BOOST_TRAIL[2], a)
                drawBox(g.catX, g.catY + 0.9f, 0.35f * i, 0.5f - i * 0.05f, 0.7f, 0.2f, col)
            }
            mMode = 0
        }

        pushModel(g.catX, g.catY + bob, 0f)
        if (g.state == Game.State.DEAD) {
            Matrix.rotateM(model, 0, -65f, 1f, 0f, 0f)
            Matrix.translateM(model, 0, 0f, 0.3f, 0.3f)
        }
        val c = CAT_MAIN[game.catColor % CAT_MAIN.size]
        val cd = CAT_DK[game.catColor % CAT_DK.size]

        val ridingZip = g.riding != null
        val lean = (Game.LANE_X[g.lane] - g.catX) * 9f
        Matrix.rotateM(model, 0, -lean, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, 1f, squash, 1f)
        if (!g.onGround && !ridingZip && g.state == Game.State.RUNNING) {
            Matrix.rotateM(model, 0, if (g.velY > 0) 14f else -10f, 1f, 0f, 0f)
        }

        if (ridingZip) {
            val grip = Game.CABLE_H - g.catY
            drawPart(0f, (2.1f + grip) / 2f, -0.2f, 0.1f, grip - 2.05f, 0.1f, cd)
            drawPart(0f, grip - 0.1f, -0.2f, 0.3f, 0.2f, 0.24f, GANTRY)
        }

        val wag = sin(g.runPhase * 0.7f) * 0.12f
        drawPart(0.05f + wag, 1.15f, 0.75f, 0.2f, 0.2f, 0.3f, c)
        drawPart(0.05f + wag * 2f, 1.45f, 0.9f, 0.18f, 0.34f, 0.18f, c)
        drawPart(0.05f + wag * 3f, 1.72f, 0.9f, 0.2f, 0.24f, 0.2f, cd)

        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val lx = if (left) -0.3f else 0.3f
            val lz = if (front) -0.4f else 0.4f
            val phase = g.runPhase + if (i == 0 || i == 3) 0f else Math.PI.toFloat()
            val swing = when {
                g.sliding -> 65f
                !g.onGround -> if (front) -35f else 30f
                g.state == Game.State.RUNNING -> sin(phase) * 38f
                else -> 0f
            }
            pushModel(lx, 0.6f, lz)
            Matrix.rotateM(model, 0, swing, 1f, 0f, 0f)
            drawPart(0f, -0.26f, 0f, 0.24f, 0.52f, 0.24f, if (left) c else cd)
            drawPart(0f, -0.55f, 0f, 0.26f, 0.14f, 0.28f, CAT_WHITE)
            popModel()
        }

        drawPart(0f, 0.85f, 0f, 0.95f, 0.8f, 1.35f, c)
        drawPart(0f, 1.15f, 0.3f, 0.97f, 0.24f, 0.3f, cd)
        drawPart(0f, 1.15f, -0.25f, 0.97f, 0.24f, 0.3f, cd)

        pushModel(0f, 1.75f, -0.42f)
        if (g.state == Game.State.DEAD) Matrix.rotateM(model, 0, 25f, 1f, 0f, 0f)
        drawPart(0f, 0f, 0f, 0.9f, 0.8f, 0.85f, c)
        drawPart(-0.3f, 0.52f, 0f, 0.24f, 0.26f, 0.14f, cd)
        drawPart(0.3f, 0.52f, 0f, 0.24f, 0.26f, 0.14f, cd)
        drawPart(0f, -0.15f, -0.4f, 0.5f, 0.32f, 0.14f, CAT_WHITE)
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

    // ---------- 工具 ----------
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
