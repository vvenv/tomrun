package com.vvenv.tomrun

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D 渲染：第三人称跟随相机，卡通低模风格 + 距离雾。
 */
class GameRenderer(private val game: Game) : GLSurfaceView.Renderer {

    private var program = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0

    private lateinit var cube: Mesh
    private lateinit var sphere: Mesh
    private lateinit var cylinder: Mesh
    private lateinit var cone: Mesh

    // 矩阵
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)

    // 模型矩阵栈（用于组合猫的身体部件）
    private val stack = ArrayList<FloatArray>()

    private var lastNanos = 0L
    private var camX = 0f

    companion object {
        private const val VSH = """
            attribute vec3 aPos;
            attribute vec3 aNormal;
            uniform mat4 uMVP;
            uniform mat4 uModel;
            varying vec3 vNormal;
            varying float vDist;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vNormal = mat3(uModel) * aNormal;
                vDist = gl_Position.w;
            }
        """

        private const val FSH = """
            precision mediump float;
            uniform vec4 uColor;
            varying vec3 vNormal;
            varying float vDist;
            void main() {
                vec3 n = normalize(vNormal);
                vec3 l = normalize(vec3(0.35, 0.85, 0.45));
                float d = max(dot(n, l), 0.0);
                vec3 c = uColor.rgb * (0.66 + 0.42 * d);
                float fog = smoothstep(55.0, 145.0, vDist);
                c = mix(c, vec3(0.66, 0.83, 0.94), fog);
                gl_FragColor = vec4(c, uColor.a);
            }
        """

        // 颜色
        private val SKY = floatArrayOf(0.53f, 0.78f, 0.94f, 1f)
        private val ROAD = floatArrayOf(0.55f, 0.51f, 0.47f, 1f)
        private val ROAD_EDGE = floatArrayOf(0.85f, 0.83f, 0.78f, 1f)
        private val DASH = floatArrayOf(0.95f, 0.95f, 0.92f, 1f)
        private val GRASS = floatArrayOf(0.44f, 0.70f, 0.36f, 1f)
        private val TREE_LEAF = floatArrayOf(0.30f, 0.58f, 0.30f, 1f)
        private val TREE_TRUNK = floatArrayOf(0.52f, 0.36f, 0.22f, 1f)
        private val WOOD = floatArrayOf(0.72f, 0.47f, 0.26f, 1f)
        private val WOOD_DARK = floatArrayOf(0.58f, 0.36f, 0.19f, 1f)
        private val METAL = floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
        private val GOLD = floatArrayOf(1.0f, 0.78f, 0.20f, 1f)
        private val CAT_GRAY = floatArrayOf(0.56f, 0.61f, 0.70f, 1f)
        private val CAT_DARK = floatArrayOf(0.44f, 0.49f, 0.58f, 1f)
        private val CAT_WHITE = floatArrayOf(0.95f, 0.95f, 0.92f, 1f)
        private val CAT_PINK = floatArrayOf(0.93f, 0.63f, 0.66f, 1f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VSH, FSH)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")

        cube = Mesh.cube()
        sphere = Mesh.sphere()
        cylinder = Mesh.cylinder()
        cone = Mesh.cylinder(topR = 0f)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glClearColor(SKY[0], SKY[1], SKY[2], 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height
        Matrix.perspectiveM(proj, 0, 52f, aspect, 0.5f, 300f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0.016f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        game.update(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)

        // 相机：跟着猫的横向位置轻微移动
        camX += (game.catX * 0.55f - camX) * min(1f, dt * 6f)
        val eyeY = 3.6f + game.catY * 0.3f
        Matrix.setLookAtM(
            view, 0,
            camX, eyeY, 7.4f,
            camX * 0.5f, 1.5f, -8f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        drawTrack()
        drawEntities()
        drawCat()

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    // ---------- 场景 ----------
    private fun drawTrack() {
        // 草地两侧
        drawBox(-30.5f, -0.55f, -110f, 53f, 1f, 260f, GRASS)
        drawBox(30.5f, -0.55f, -110f, 53f, 1f, 260f, GRASS)
        // 跑道
        drawBox(0f, -0.5f, -110f, 8.2f, 1f, 260f, ROAD)
        // 路缘
        drawBox(-4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, ROAD_EDGE)
        drawBox(4.35f, -0.42f, -110f, 0.5f, 1.06f, 260f, ROAD_EDGE)

        // 车道虚线：随距离滚动
        val gap = 5f
        val offset = game.distance % gap
        var z = 6f - offset
        while (z > -220f) {
            for (x in floatArrayOf(-1.1f, 1.1f)) {
                drawBox(x, 0.02f, z, 0.14f, 0.04f, 1.6f, DASH)
            }
            z -= gap
        }

        // 路边的树：稀疏排布，滚动
        val treeGap = 17f
        val tOffset = game.distance % treeGap
        var tz = 4f - tOffset
        var flip = ((game.distance / treeGap).toInt() % 2) == 0
        while (tz > -200f) {
            val tx = if (flip) -7.5f else 7.5f
            drawTree(tx, tz)
            drawTree(-tx * 1.6f, tz - 8f)
            flip = !flip
            tz -= treeGap
        }
    }

    private fun drawTree(x: Float, z: Float) {
        pushModel(x, 0f, z)
        drawPart(cylinder, 0f, 0.7f, 0f, 0.45f, 1.4f, 0.45f, TREE_TRUNK)
        drawPart(cone, 0f, 2.3f, 0f, 2.4f, 2.6f, 2.4f, TREE_LEAF)
        drawPart(cone, 0f, 3.4f, 0f, 1.7f, 2.0f, 1.7f, TREE_LEAF)
        popModel()
    }

    private fun drawEntities() {
        synchronized(game) {
            for (e in game.entities) {
                val x = Game.LANE_X[e.lane]
                when (e.kind) {
                    Game.COIN -> {
                        e.spin += 4f
                        pushModel(x, e.y, e.z)
                        Matrix.rotateM(model, 0, e.spin, 0f, 1f, 0f)
                        // 立起来的金币：圆柱绕 X 转 90°，厚度 0.14
                        drawPart(cylinder, 0f, 0f, 0f, 0.72f, 0.14f, 0.72f, GOLD, rotX = 90f)
                        popModel()
                    }
                    Game.OBST_LOW -> {
                        pushModel(x, 0f, e.z)
                        // 木头矮栏：两根柱子 + 两条横板
                        drawPart(cube, -0.85f, 0.45f, 0f, 0.16f, 0.9f, 0.16f, WOOD_DARK)
                        drawPart(cube, 0.85f, 0.45f, 0f, 0.16f, 0.9f, 0.16f, WOOD_DARK)
                        drawPart(cube, 0f, 0.72f, 0f, 1.9f, 0.2f, 0.1f, WOOD)
                        drawPart(cube, 0f, 0.38f, 0f, 1.9f, 0.2f, 0.1f, WOOD)
                        popModel()
                    }
                    Game.OBST_BAR -> {
                        pushModel(x, 0f, e.z)
                        // 高空横杆：铲滑通过
                        drawPart(cube, -0.95f, 1.1f, 0f, 0.18f, 2.2f, 0.18f, METAL)
                        drawPart(cube, 0.95f, 1.1f, 0f, 0.18f, 2.2f, 0.18f, METAL)
                        drawPart(cube, 0f, 1.75f, 0f, 2.1f, 0.9f, 0.35f, WOOD)
                        popModel()
                    }
                    Game.OBST_BLOCK -> {
                        pushModel(x, 0f, e.z)
                        drawPart(cube, 0f, 1.1f, 0f, 1.9f, 2.2f, 1.2f, WOOD)
                        drawPart(cube, 0f, 1.1f, 0.02f, 1.7f, 2.0f, 1.2f, WOOD_DARK)
                        popModel()
                    }
                }
            }
        }
    }

    // ---------- 猫 ----------
    private fun drawCat() {
        val g = game
        val squash = if (g.sliding) 0.5f else 1f
        val bob = if (g.onGround && g.state == Game.State.RUNNING) kotlin.math.abs(sin(g.runPhase)) * 0.07f else 0f

        pushModel(g.catX, g.catY + bob, 0f)
        if (g.state == Game.State.DEAD) {
            // 撞倒：向后躺
            Matrix.rotateM(model, 0, -65f, 1f, 0f, 0f)
            Matrix.translateM(model, 0, 0f, 0.3f, 0.3f)
        }
        Matrix.scaleM(model, 0, 1f, squash, 1f)
        if (!g.onGround && g.state == Game.State.RUNNING) {
            Matrix.rotateM(model, 0, if (g.velY > 0) 14f else -10f, 1f, 0f, 0f)
        }

        // 尾巴：翘向右后方摆动（避免和身体重叠）
        val wag = sin(g.runPhase * 0.7f) * 14f
        pushModel(0.1f, 0.95f, 0.68f)
        Matrix.rotateM(model, 0, 42f, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, -16f + wag, 0f, 0f, 1f)
        drawPart(cylinder, 0f, 0.35f, 0f, 0.16f, 0.9f, 0.16f, CAT_GRAY)
        drawPart(sphere, 0f, 0.8f, 0f, 0.2f, 0.2f, 0.2f, CAT_DARK)
        popModel()

        // 身体
        drawPart(sphere, 0f, 0.85f, 0f, 1.05f, 0.95f, 1.5f, CAT_GRAY)
        // 白胸口（藏在身体前下方，转弯时能瞥见）
        drawPart(sphere, 0f, 0.6f, -0.5f, 0.5f, 0.5f, 0.4f, CAT_WHITE)

        // 四条腿：对角摆动
        for (i in 0 until 4) {
            val front = i < 2
            val left = i % 2 == 0
            val lx = if (left) -0.32f else 0.32f
            val lz = if (front) -0.42f else 0.42f
            val phase = g.runPhase + if ((i == 0 || i == 3)) 0f else Math.PI.toFloat()
            val swing = when {
                g.sliding -> 65f
                !g.onGround -> if (front) -35f else 30f
                g.state == Game.State.RUNNING -> sin(phase) * 38f
                else -> 0f
            }
            pushModel(lx, 0.62f, lz)
            Matrix.rotateM(model, 0, swing, 1f, 0f, 0f)
            drawPart(cube, 0f, -0.28f, 0f, 0.19f, 0.56f, 0.19f, if (left) CAT_GRAY else CAT_DARK)
            drawPart(sphere, 0f, -0.56f, 0f, 0.22f, 0.18f, 0.24f, CAT_WHITE)
            popModel()
        }

        // 头（背对镜头）
        pushModel(0f, 1.72f, -0.42f)
        if (g.state == Game.State.DEAD) Matrix.rotateM(model, 0, 25f, 1f, 0f, 0f)
        drawPart(sphere, 0f, 0f, 0f, 0.95f, 0.9f, 0.9f, CAT_GRAY)
        // 耳朵
        drawPart(cone, -0.3f, 0.52f, 0f, 0.3f, 0.42f, 0.22f, CAT_GRAY)
        drawPart(cone, 0.3f, 0.52f, 0f, 0.3f, 0.42f, 0.22f, CAT_GRAY)
        drawPart(cone, -0.3f, 0.5f, -0.02f, 0.16f, 0.24f, 0.14f, CAT_PINK)
        drawPart(cone, 0.3f, 0.5f, -0.02f, 0.16f, 0.24f, 0.14f, CAT_PINK)
        // 白口鼻（在前方，转弯/跳跃时能瞥见）
        drawPart(sphere, 0f, -0.12f, -0.38f, 0.5f, 0.4f, 0.35f, CAT_WHITE)
        popModel()

        popModel()
    }

    // ---------- 矩阵/绘制工具 ----------
    private fun pushModel(x: Float, y: Float, z: Float) {
        val saved = FloatArray(16)
        if (stack.isEmpty()) {
            Matrix.setIdentityM(model, 0)
        }
        System.arraycopy(model, 0, saved, 0, 16)
        stack.add(saved)
        Matrix.translateM(model, 0, x, y, z)
    }

    private fun popModel() {
        val saved = stack.removeAt(stack.size - 1)
        System.arraycopy(saved, 0, model, 0, 16)
    }

    /** 在当前 model 基础上：平移+缩放（可选绕 X 轴旋转）画一个网格 */
    private fun drawPart(
        mesh: Mesh,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray,
        rotX: Float = 0f
    ) {
        System.arraycopy(model, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, x, y, z)
        if (rotX != 0f) Matrix.rotateM(tmp, 0, rotX, 1f, 0f, 0f)
        Matrix.scaleM(tmp, 0, sx, sy, sz)
        emit(mesh, tmp, color)
    }

    /** 独立画一个盒子（世界坐标，中心 x/y/z，尺寸 w/h/d） */
    private fun drawBox(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, color: FloatArray) {
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, x, y, z)
        Matrix.scaleM(tmp, 0, w, h, d)
        emit(cube, tmp, color)
    }

    private fun emit(mesh: Mesh, modelM: FloatArray, color: FloatArray) {
        Matrix.multiplyMM(mvp, 0, vp, 0, modelM, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)
        GLES20.glUniform4fv(uColor, 1, color, 0)
        mesh.draw(aPos, aNormal)
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
