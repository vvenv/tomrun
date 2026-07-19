package com.vvenv.tomrun

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

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
                // 半球环境光 + 方向光
                vec3 c = uColor.rgb * (0.52 + 0.14 * n.y + 0.44 * d);
                c = mix(c, uFogColor, fog);
                gl_FragColor = vec4(c, uColor.a);
            }
        """

        // ---- 8-bit 配色 ----
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
        private val MOUNTAIN = floatArrayOf(0.45f, 0.62f, 0.55f, 1f)
        private val SHADOW = floatArrayOf(0.04f, 0.09f, 0.04f, 0.32f)
        private val FLOWER = arrayOf(
            floatArrayOf(1f, 1f, 1f, 1f),
            floatArrayOf(0.98f, 0.55f, 0.65f, 1f),
            floatArrayOf(1.0f, 0.82f, 0.25f, 1f)
        )
        private val CAT = floatArrayOf(0.52f, 0.58f, 0.70f, 1f)
        private val CAT_DARK = floatArrayOf(0.36f, 0.42f, 0.55f, 1f)
        private val CAT_WHITE = floatArrayOf(0.95f, 0.95f, 0.92f, 1f)
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
        val aspect = width.toFloat() / height
        Matrix.perspectiveM(proj, 0, 52f, aspect, 0.5f, 400f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0.016f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        game.update(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        setFog(SKY[0] + 0.09f, SKY[1] + 0.05f, SKY[2] + 0.02f)

        camX += (game.catX * 0.55f - camX) * min(1f, dt * 6f)
        val eyeY = 3.6f + game.catY * 0.3f
        Matrix.setLookAtM(view, 0, camX, eyeY, 7.4f, camX * 0.5f, 1.5f, -8f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawSky()
        drawTrack()
        drawScenery()
        drawEntities()
        drawCat()

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    private fun setFog(r: Float, g: Float, b: Float) = GLES20.glUniform3f(uFog, min(1f, r), min(1f, g), min(1f, b))

    // ---------- 天空：太阳 + 远山台地 ----------
    private fun drawSky() {
        Matrix.setIdentityM(model, 0)
        stack.clear()

        // 太阳：无光照、不受雾影响
        setFog(SUN[0], SUN[1], SUN[2])
        mMode = 2
        pushModel(20f, 26f, -110f)
        drawPart(0f, 0f, 0f, 7f, 7f, 7f, SUN)
        popModel()
        setFog(SKY[0] + 0.09f, SKY[1] + 0.05f, SKY[2] + 0.02f)

        mMode = 0
        for (i in 0 until 7) {
            val x = -90f + i * 30f + (i % 3) * 8f
            if (abs(x) < 16f) continue
            val h = 26f + (i % 3) * 10f
            pushModel(x, 0f, -180f)
            drawPart(0f, h * 0.14f, 0f, h * 1.9f, h * 0.28f, h, MOUNTAIN)
            drawPart(0f, h * 0.38f, 0f, h * 1.1f, h * 0.22f, h * 0.8f, MOUNTAIN)
            popModel()
        }
    }

    // ---------- 跑道 ----------
    private fun drawTrack() {
        drawBox(-30.5f, -0.55f, -110f, 53f, 1f, 260f, GRASS)
        drawBox(30.5f, -0.55f, -110f, 53f, 1f, 260f, GRASS)
        drawBox(-5.6f, -0.53f, -110f, 2.4f, 1.02f, 260f, GRASS_DARK)
        drawBox(5.6f, -0.53f, -110f, 2.4f, 1.02f, 260f, GRASS_DARK)
        drawBox(0f, -0.5f, -110f, 8.2f, 1f, 260f, ROAD)
        drawBox(-4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, ROAD_EDGE)
        drawBox(4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, ROAD_EDGE)

        scroll(5f, game.distance) { _, z ->
            for (x in floatArrayOf(-1.1f, 1.1f)) {
                drawBox(x, 0.02f, z, 0.14f, 0.04f, 1.6f, DASH)
            }
        }
    }

    // ---------- 路边景物（确定性散布） ----------
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
        // 云：无光照纯白，视差滚动
        mMode = 2
        scroll(31f, game.distance * 0.45f) { m, z ->
            val side = if (mod(m, 2) == 0) -1f else 1f
            val x = side * (9f + mod(m * 23, 140) / 10f)
            val y = 11f + mod(m * 19, 60) / 10f
            val k = 1.1f + mod(m * 13, 60) / 100f
            pushModel(x, y, z)
            drawPart(0f, 0f, 0f, 3.2f * k, 1.3f * k, 1.5f * k, CLOUD)
            drawPart(1.7f * k, 0.5f * k, 0f, 2.0f * k, 1.1f * k, 1.3f * k, CLOUD)
            drawPart(-1.7f * k, 0.4f * k, 0f, 1.8f * k, 1.0f * k, 1.2f * k, CLOUD)
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
            for (e in game.entities) {
                val x = Game.LANE_X[e.lane]
                when (e.kind) {
                    Game.COIN -> {
                        e.spin += 3f
                        if (e.z < 1.5f) drawShadow(x, e.z, 0.5f)
                        pushModel(x, e.y, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        drawPart(0f, 0f, 0f, 0.62f, 0.62f, 0.2f, GOLD)
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
                }
            }
        }
    }

    /** 方形软阴影贴地投影 */
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

        pushModel(g.catX, g.catY + bob, 0f)
        if (g.state == Game.State.DEAD) {
            Matrix.rotateM(model, 0, -65f, 1f, 0f, 0f)
            Matrix.translateM(model, 0, 0f, 0.3f, 0.3f)
        }
        val lean = (Game.LANE_X[g.lane] - g.catX) * 9f
        Matrix.rotateM(model, 0, -lean, 0f, 0f, 1f)
        Matrix.scaleM(model, 0, 1f, squash, 1f)
        if (!g.onGround && g.state == Game.State.RUNNING) {
            Matrix.rotateM(model, 0, if (g.velY > 0) 14f else -10f, 1f, 0f, 0f)
        }

        // 尾巴：三节方块阶梯
        val wag = sin(g.runPhase * 0.7f) * 0.12f
        drawPart(0.05f + wag, 1.15f, 0.75f, 0.2f, 0.2f, 0.3f, CAT)
        drawPart(0.05f + wag * 2f, 1.45f, 0.9f, 0.18f, 0.34f, 0.18f, CAT)
        drawPart(0.05f + wag * 3f, 1.72f, 0.9f, 0.2f, 0.24f, 0.2f, CAT_DARK)

        // 四条腿
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
            drawPart(0f, -0.26f, 0f, 0.24f, 0.52f, 0.24f, if (left) CAT else CAT_DARK)
            drawPart(0f, -0.55f, 0f, 0.26f, 0.14f, 0.28f, CAT_WHITE)
            popModel()
        }

        // 方块身体 + 条纹
        drawPart(0f, 0.85f, 0f, 0.95f, 0.8f, 1.35f, CAT)
        drawPart(0f, 1.15f, 0.3f, 0.97f, 0.24f, 0.3f, CAT_DARK)
        drawPart(0f, 1.15f, -0.25f, 0.97f, 0.24f, 0.3f, CAT_DARK)

        // 方块头 + 耳朵 + 白口鼻
        pushModel(0f, 1.75f, -0.42f)
        if (g.state == Game.State.DEAD) Matrix.rotateM(model, 0, 25f, 1f, 0f, 0f)
        drawPart(0f, 0f, 0f, 0.9f, 0.8f, 0.85f, CAT)
        drawPart(-0.3f, 0.52f, 0f, 0.24f, 0.26f, 0.14f, CAT_DARK)
        drawPart(0.3f, 0.52f, 0f, 0.24f, 0.26f, 0.14f, CAT_DARK)
        drawPart(0f, -0.15f, -0.4f, 0.5f, 0.32f, 0.14f, CAT_WHITE)
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

    /** 在当前 model 基础上平移+缩放画一个方块 */
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

    /** 独立画一个盒子（世界坐标） */
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
