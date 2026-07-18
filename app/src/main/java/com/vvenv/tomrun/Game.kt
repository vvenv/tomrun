package com.vvenv.tomrun

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * 纯游戏逻辑（不含渲染）：三跑道无尽跑酷。
 * update() 在 GL 线程调用；滑动输入来自 UI 线程，方法内加锁。
 * HUD 只读取 @Volatile 字段。
 */
class Game {

    enum class State { READY, RUNNING, DEAD }

    companion object {
        // 实体类型
        const val OBST_LOW = 0    // 矮栏：跳过
        const val OBST_BAR = 1    // 高空横杆：铲滑过
        const val OBST_BLOCK = 2  // 大箱子：必须换道
        const val COIN = 3

        val LANE_X = floatArrayOf(-2.2f, 0f, 2.2f)
        const val SPAWN_Z = -150f
        const val GRAVITY = 24f
        const val JUMP_V = 8.6f
        const val SLIDE_TIME = 0.75f
    }

    class Entity(val kind: Int, val lane: Int, var z: Float, val y: Float = 0f) {
        var taken = false          // 仅金币用
        var spin = Random.nextFloat() * 360f
    }

    @Volatile var state = State.READY
    @Volatile var score = 0
    @Volatile var coins = 0
    @Volatile var highScore = 0
    @Volatile var deadTime = 0f

    // 猫状态（GL 线程写，渲染直接读）
    var lane = 1
    var catX = 0f              // 平滑趋近目标跑道
    var catY = 0f
    var velY = 0f
    var slideTimer = 0f
    var runPhase = 0f
    val sliding get() = slideTimer > 0f
    val onGround get() = catY <= 0.001f

    var speed = 14f
    var distance = 0f
    private var gapRemaining = 0f  // 距离下一波生成还差多少米

    val entities = ArrayList<Entity>()

    private var prefs: SharedPreferences? = null

    fun attachPrefs(p: SharedPreferences) {
        prefs = p
        highScore = p.getInt("high3d", 0)
    }

    // ---------- 输入（UI 线程） ----------
    @Synchronized fun onTap() {
        when (state) {
            State.READY -> { reset(); start() }
            State.RUNNING -> jump()
            State.DEAD -> if (deadTime > 0.6f) { reset(); start() }
        }
    }

    @Synchronized fun onSwipeUp() { if (state == State.RUNNING) jump() }

    @Synchronized fun onSwipeDown() {
        if (state == State.RUNNING) {
            slideTimer = SLIDE_TIME
            if (!onGround) velY = -14f   // 空中下滑：快速落地
        }
    }

    @Synchronized fun onSwipeLeft() {
        if (state == State.RUNNING && lane > 0) lane--
    }

    @Synchronized fun onSwipeRight() {
        if (state == State.RUNNING && lane < 2) lane++
    }

    private fun jump() {
        if (onGround) {
            velY = JUMP_V
            slideTimer = 0f
        }
    }

    private fun start() {
        state = State.RUNNING
    }

    @Synchronized fun reset() {
        entities.clear()
        lane = 1; catX = 0f; catY = 0f; velY = 0f
        slideTimer = 0f; runPhase = 0f
        speed = 14f; distance = 0f; score = 0; coins = 0
        deadTime = 0f
        // 预填充开局的障碍，避免前 150 米空跑
        var z = -45f
        while (z > SPAWN_Z) {
            spawnWave(z)
            z -= 22f + Random.nextFloat() * 12f
        }
        gapRemaining = nextGap()
    }

    private fun nextGap() = 16f + Random.nextFloat() * 14f + speed * 0.35f

    // ---------- 主更新（GL 线程） ----------
    @Synchronized fun update(dt: Float) {
        if (state == State.DEAD) { deadTime += dt; return }
        runPhase += dt * speed * 0.9f
        if (state != State.RUNNING) return

        speed = min(speed + 0.35f * dt, 30f)
        val dz = speed * dt
        distance += dz

        // 猫横向 / 纵向
        val targetX = LANE_X[lane]
        catX += (targetX - catX) * min(1f, dt * 12f)
        if (!onGround || velY > 0f) {
            velY -= GRAVITY * dt
            catY += velY * dt
            if (catY <= 0f) { catY = 0f; velY = 0f }
        }
        if (slideTimer > 0f) slideTimer -= dt

        // 实体前移
        val it = entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.z += dz
            if (e.z > 8f || e.taken) it.remove()
        }

        // 生成新一波
        gapRemaining -= dz
        while (gapRemaining <= 0f) {
            spawnWave(SPAWN_Z - gapRemaining)
            gapRemaining += nextGap()
        }

        checkCollision()
        score = distance.toInt() + coins * 10
    }

    private fun spawnWave(zBase: Float) {
        val r = Random.nextFloat()
        val freeLanes = mutableListOf(0, 1, 2)
        when {
            r < 0.30f -> {
                // 矮栏 1~3 条道（都能跳过去）
                val n = 1 + Random.nextInt(3)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_LOW, freeLanes[i], zBase))
                coinArc(freeLanes[0], zBase)
            }
            r < 0.60f -> {
                // 大箱子 1~2 条道，留出活路
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) {
                    entities.add(Entity(OBST_BLOCK, freeLanes[i], zBase))
                }
                val free = freeLanes[2]
                coinRow(free, zBase)
            }
            r < 0.82f -> {
                // 高空横杆：铲滑过，杆下放一排金币
                val l = Random.nextInt(3)
                entities.add(Entity(OBST_BAR, l, zBase))
                coinRow(l, zBase)
            }
            else -> {
                // 纯金币波
                coinRow(Random.nextInt(3), zBase)
                coinRow(Random.nextInt(3), zBase - 8f)
            }
        }
    }

    private fun coinRow(lane: Int, zBase: Float) {
        for (i in 0 until 5) entities.add(Entity(COIN, lane, zBase - 1.6f * i, 1.0f))
    }

    private fun coinArc(lane: Int, zBase: Float) {
        // 跨过矮栏的金币弧线
        val ys = floatArrayOf(1.0f, 1.8f, 2.3f, 1.8f, 1.0f)
        for (i in ys.indices) entities.add(Entity(COIN, lane, zBase + 3.2f - 1.6f * i, ys[i]))
    }

    private fun checkCollision() {
        val catLaneX = catX
        val hitH = if (sliding) 0.7f else 1.9f   // 猫的碰撞高度
        for (e in entities) {
            if (e.taken) continue
            val ex = LANE_X[e.lane]
            if (abs(e.z) > 0.9f) continue
            val laneHit = abs(ex - catLaneX) < 1.1f
            if (!laneHit) continue
            when (e.kind) {
                COIN -> {
                    val catCenterY = catY + (if (sliding) 0.4f else 1.0f)
                    if (abs(e.y - catCenterY) < 1.1f) { e.taken = true; coins++ }
                }
                OBST_LOW -> if (catY < 0.55f) { die(); return }
                OBST_BAR -> if (!sliding || catY > 0.3f) { die(); return }
                OBST_BLOCK -> { die(); return }
            }
        }
    }

    private fun die() {
        state = State.DEAD
        deadTime = 0f
        if (score > highScore) {
            highScore = score
            prefs?.edit()?.putInt("high3d", score)?.apply()
        }
    }
}
