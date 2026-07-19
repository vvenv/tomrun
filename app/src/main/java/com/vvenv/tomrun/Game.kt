package com.vvenv.tomrun

import android.content.SharedPreferences
import kotlin.math.abs
import kotlin.math.ceil
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
        const val P_MAGNET = 4    // 磁铁：吸金币
        const val P_HELMET = 5    // 头盔：抗一次撞击
        const val P_DOUBLE = 6    // 加倍：得分 x2
        const val OBST_RAMP = 7   // 施工跳台：沿斜坡跑上去越过路障

        val LANE_X = floatArrayOf(-2.2f, 0f, 2.2f)
        const val SPAWN_Z = -150f
        const val GRAVITY = 24f
        const val JUMP_V = 8.6f
        const val SLIDE_TIME = 0.75f
        const val RAMP_LENGTH = 9f
        const val RAMP_HEIGHT = 2.4f

        const val CABLE_H = 5.4f   // 索道钢缆高度
        const val RIDE_Y = 3.0f    // 滑索时猫的脚底高度

        // 音效事件
        const val EV_JUMP = 0
        const val EV_SLIDE = 1
        const val EV_COIN = 2
        const val EV_POWER = 3
        const val EV_SHIELD = 4
        const val EV_DIE = 5
        const val EV_ZIP = 6
        const val EV_RECORD = 7

        const val CAT_COLOR_COUNT = 4

        // 天气
        const val W_SUNNY = 0
        const val W_RAIN = 1
        const val W_SNOW = 2
    }

    class Entity(val kind: Int, val lane: Int, var z: Float, var y: Float = 0f) {
        var x = LANE_X[lane]
        var taken = false
        var spin = Random.nextFloat() * 360f
    }

    /** 高空索道：entryZ 是入口（先到达玩家），钢缆向远处延伸 length */
    class Zip(val lane: Int, var entryZ: Float, val length: Float) {
        val exitZ get() = entryZ - length
    }

    @Volatile var state = State.READY
    @Volatile var score = 0
    @Volatile var coins = 0
    @Volatile var highScore = 0
    @Volatile var deadTime = 0f

    // 道具状态（HUD/渲染读取）
    @Volatile var magnetTime = 0f
    @Volatile var doubleTime = 0f
    @Volatile var helmet = false
    @Volatile var riding: Zip? = null

    // 音效回调（MainActivity 注入）、跑酷中新纪录横幅、猫的颜色
    @Volatile var onEvent: ((Int) -> Unit)? = null
    @Volatile var recordFlash = 0f
    @Volatile var catColor = 0
    private var recordDone = false

    // 天气：随机轮换，weatherBlend 从 0 到 1 平滑过渡到当前天气
    @Volatile var weather = W_SUNNY
    @Volatile var weatherPrev = W_SUNNY
    @Volatile var weatherBlend = 1f
    private var weatherTimer = 18f + Random.nextFloat() * 15f

    // 猫状态（GL 线程写，渲染直接读）
    var lane = 1
    var catX = 0f
    var catY = 0f
    var velY = 0f
    var slideTimer = 0f
    var runPhase = 0f
    private var groundY = 0f
    val sliding get() = slideTimer > 0f
    val onGround get() = catY <= groundY + 0.001f

    var speed = 14f
    var distance = 0f
    private var gapRemaining = 0f    // 距离下一波障碍生成还差多少米
    private var zipGap = 100f        // 距离下一条索道还差多少米
    private var scoreBoost = 0f      // 加倍期间的额外里程分
    private var invulnTime = 0f      // 头盔碎掉后的短暂无敌

    val entities = ArrayList<Entity>()
    val ziplines = ArrayList<Zip>()

    private var prefs: SharedPreferences? = null

    fun attachPrefs(p: SharedPreferences) {
        prefs = p
        highScore = p.getInt("high3d", 0)
        catColor = p.getInt("catColor", 0) % CAT_COLOR_COUNT
    }

    fun cycleCatColor() {
        catColor = (catColor + 1) % CAT_COLOR_COUNT
        prefs?.edit()?.putInt("catColor", catColor)?.apply()
    }

    private fun emit(event: Int) {
        onEvent?.invoke(event)
    }

    /** 磁铁/加倍的剩余秒数（HUD 显示用） */
    fun magnetLeft() = ceil(magnetTime).toInt()
    fun doubleLeft() = ceil(doubleTime).toInt()

    // ---------- 输入（UI 线程） ----------
    @Synchronized fun onTap() {
        when (state) {
            State.READY -> { reset(); state = State.RUNNING }
            State.RUNNING -> jump()
            State.DEAD -> if (deadTime > 0.6f) { reset(); state = State.RUNNING }
        }
    }

    @Synchronized fun onSwipeUp() { if (state == State.RUNNING) jump() }

    @Synchronized fun onSwipeDown() {
        if (state != State.RUNNING) return
        if (riding != null) {
            riding = null           // 提前下索道
            velY = -14f
        } else {
            slideTimer = SLIDE_TIME
            if (!onGround) velY = -14f
            emit(EV_SLIDE)
        }
    }

    @Synchronized fun onSwipeLeft() {
        if (state == State.RUNNING && lane > 0) { lane--; riding = null }
    }

    @Synchronized fun onSwipeRight() {
        if (state == State.RUNNING && lane < 2) { lane++; riding = null }
    }

    private fun jump() {
        if (riding != null) return
        if (onGround) {
            velY = JUMP_V
            slideTimer = 0f
            emit(EV_JUMP)
        }
    }

    @Synchronized fun reset() {
        entities.clear()
        ziplines.clear()
        lane = 1; catX = 0f; catY = 0f; velY = 0f
        groundY = 0f
        slideTimer = 0f; runPhase = 0f
        speed = 14f; distance = 0f; score = 0; coins = 0
        deadTime = 0f
        magnetTime = 0f; doubleTime = 0f; helmet = false
        riding = null; scoreBoost = 0f; invulnTime = 0f
        recordFlash = 0f; recordDone = false
        zipGap = 90f + Random.nextFloat() * 80f
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
        tickWeather(dt)
        runPhase += dt * speed * 0.9f
        if (state != State.RUNNING) return

        speed = min(speed + 0.35f * dt, 30f)
        val dz = speed * dt
        distance += dz

        if (magnetTime > 0f) magnetTime -= dt
        if (doubleTime > 0f) { doubleTime -= dt; scoreBoost += dz }
        if (invulnTime > 0f) invulnTime -= dt

        // 猫横向
        val targetX = LANE_X[lane]
        catX += (targetX - catX) * min(1f, dt * 12f)

        // 斜坡随场景前移；站在坡面时脚底贴合坡面，离开顶端后自然下落
        val wasGrounded = onGround && velY <= 0f
        val nextGroundY = rampSurfaceAtPlayer(dz)
        groundY = nextGroundY

        // 索道推进 / 清理
        for (zip in ziplines) zip.entryZ += dz
        ziplines.removeAll { it.exitZ > 12f }

        val r = riding
        if (r != null) {
            // 滑索中：吊在钢缆下，终点到了就松手落下
            catY += (RIDE_Y - catY) * min(1f, dt * 8f)
            velY = 0f
            slideTimer = 0f
            if (r.exitZ >= 0f || r !in ziplines) riding = null
        } else {
            // 常规纵向物理
            if (wasGrounded && nextGroundY >= catY - 0.08f) {
                catY = nextGroundY
                velY = 0f
            } else if (!onGround || velY > 0f) {
                velY -= GRAVITY * dt
                catY += velY * dt
                if (catY <= groundY) { catY = groundY; velY = 0f }
            }
            if (slideTimer > 0f) slideTimer -= dt
            // 入口经过时从地面进入索道
            for (zip in ziplines) {
                if (zip.lane == lane && catY < 0.3f &&
                    zip.entryZ >= 0f && zip.entryZ - dz < 0f &&
                    abs(catX - LANE_X[lane]) < 0.6f
                ) {
                    riding = zip
                    emit(EV_ZIP)
                    break
                }
            }
        }

        // 实体前移 / 磁铁吸金币 / 清理
        val it = entities.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.z += dz
            if (magnetTime > 0f && e.kind == COIN && e.z > -26f && !e.taken) {
                val pull = min(1f, dt * 8f)
                e.x += (catX - e.x) * pull
                e.y += (catY + 1f - e.y) * pull
                e.z += (0f - e.z) * min(1f, dt * 4f)
            }
            if (e.z > 8f || e.taken) it.remove()
        }

        // 生成新一波障碍
        gapRemaining -= dz
        while (gapRemaining <= 0f) {
            spawnWave(SPAWN_Z - gapRemaining)
            gapRemaining += nextGap()
        }
        // 生成索道（同一时间最多一条）
        zipGap -= dz
        if (zipGap <= 0f && ziplines.isEmpty()) {
            spawnZipline()
            zipGap = 160f + Random.nextFloat() * 160f
        }

        checkCollision()
        score = (distance + scoreBoost).toInt() + coins * 10

        // 跑酷中打破纪录：弹横幅 + 号角音（首局最高分为 0 时不提示）
        if (recordFlash > 0f) recordFlash -= dt
        if (!recordDone && highScore > 0 && score > highScore) {
            recordDone = true
            recordFlash = 2.6f
            emit(EV_RECORD)
        }
    }

    private fun tickWeather(dt: Float) {
        weatherTimer -= dt
        if (weatherTimer <= 0f) {
            weatherPrev = weather
            var next = Random.nextInt(3)
            if (next == weather) next = (next + 1) % 3
            weather = next
            weatherBlend = 0f
            weatherTimer = 25f + Random.nextFloat() * 20f
        }
        if (weatherBlend < 1f) weatherBlend = min(1f, weatherBlend + dt / 2.5f)
    }

    private fun spawnZipline() {
        val laneZ = Random.nextInt(3)
        val length = 30f + Random.nextFloat() * 50f
        val zip = Zip(laneZ, SPAWN_Z, length)
        ziplines.add(zip)
        // 钢缆下挂一排金币，奖励走索道
        var cz = SPAWN_Z - 8f
        while (cz > SPAWN_Z - length + 4f) {
            entities.add(Entity(COIN, laneZ, cz, CABLE_H - 1.1f))
            cz -= 4f
        }
    }

    private fun spawnWave(zBase: Float) {
        val r = Random.nextFloat()
        val freeLanes = mutableListOf(0, 1, 2)
        when {
            r < 0.25f -> {
                val n = 1 + Random.nextInt(3)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_LOW, freeLanes[i], zBase))
                coinArc(freeLanes[0], zBase)
            }
            r < 0.50f -> {
                val n = 1 + Random.nextInt(2)
                freeLanes.shuffle()
                for (i in 0 until n) entities.add(Entity(OBST_BLOCK, freeLanes[i], zBase))
                coinRow(freeLanes[2], zBase)
            }
            r < 0.68f -> {
                val l = Random.nextInt(3)
                entities.add(Entity(OBST_BAR, l, zBase))
                coinRow(l, zBase)
            }
            r < 0.82f -> spawnRampWave(zBase)
            else -> {
                coinRow(Random.nextInt(3), zBase)
                coinRow(Random.nextInt(3), zBase - 8f)
            }
        }
        // 随机道具：磁铁 / 头盔 / 加倍
        if (Random.nextFloat() < 0.14f) {
            val kind = P_MAGNET + Random.nextInt(3)
            entities.add(Entity(kind, Random.nextInt(3), zBase - 10f, 1.2f))
        }
    }

    /** 施工跳台：两条道被木箱封住，剩余跑道可沿斜坡越过水泥路障 */
    private fun spawnRampWave(zBase: Float) {
        val rampLane = Random.nextInt(3)
        entities.add(Entity(OBST_RAMP, rampLane, zBase))
        for (l in 0..2) {
            if (l != rampLane) entities.add(Entity(OBST_BLOCK, l, zBase))
        }
        val half = RAMP_LENGTH / 2f
        for (i in 0 until 5) {
            val localZ = half - 0.8f - i * (RAMP_LENGTH - 1.6f) / 4f
            val surfaceY = RAMP_HEIGHT * (half - localZ) / RAMP_LENGTH
            entities.add(Entity(COIN, rampLane, zBase + localZ, surfaceY + 1f))
        }
    }

    /** 下一帧玩家脚下的斜坡高度；斜坡前端低、后端高 */
    private fun rampSurfaceAtPlayer(dz: Float): Float {
        val half = RAMP_LENGTH / 2f
        for (e in entities) {
            if (e.kind != OBST_RAMP || e.lane != lane) continue
            val nextZ = e.z + dz
            if (nextZ in -half..half && abs(e.x - catX) < 1.1f) {
                return RAMP_HEIGHT * (nextZ + half) / RAMP_LENGTH
            }
        }
        return 0f
    }

    private fun coinRow(lane: Int, zBase: Float) {
        for (i in 0 until 5) entities.add(Entity(COIN, lane, zBase - 1.6f * i, 1.0f))
    }

    private fun coinArc(lane: Int, zBase: Float) {
        val ys = floatArrayOf(1.0f, 1.8f, 2.3f, 1.8f, 1.0f)
        for (i in ys.indices) entities.add(Entity(COIN, lane, zBase + 3.2f - 1.6f * i, ys[i]))
    }

    private fun checkCollision() {
        val catCenterY = catY + (if (sliding) 0.4f else 1.0f)
        val onZip = riding != null
        for (e in entities) {
            if (e.taken) continue
            when (e.kind) {
                COIN -> {
                    val radius = if (magnetTime > 0f) 1.6f else 1.15f
                    val dx = e.x - catX
                    val dy = e.y - catCenterY
                    if (abs(e.z) < 1.2f && dx * dx + dy * dy < radius * radius) {
                        e.taken = true
                        coins += if (doubleTime > 0f) 2 else 1
                        emit(EV_COIN)
                    }
                }
                P_MAGNET, P_HELMET, P_DOUBLE -> {
                    if (abs(e.z) < 1.0f && abs(e.x - catX) < 1.1f && abs(e.y - catCenterY) < 1.3f) {
                        e.taken = true
                        when (e.kind) {
                            P_MAGNET -> magnetTime = 8f
                            P_HELMET -> helmet = true
                            P_DOUBLE -> doubleTime = 10f
                        }
                        emit(EV_POWER)
                    }
                }
                else -> {
                    // 障碍：滑索中 / 短暂无敌时不判定
                    if (onZip || invulnTime > 0f) continue
                    if (abs(e.z) > 0.9f) continue
                    if (abs(e.x - catX) >= 1.1f) continue
                    val hit = when (e.kind) {
                        OBST_LOW -> catY < 0.55f
                        OBST_BAR -> !sliding || catY > 0.3f
                        OBST_RAMP -> false
                        else -> true
                    }
                    if (hit) {
                        if (helmet) {
                            helmet = false
                            invulnTime = 1f
                            emit(EV_SHIELD)
                        } else {
                            die()
                            return
                        }
                    }
                }
            }
        }
    }

    private fun die() {
        state = State.DEAD
        deadTime = 0f
        emit(EV_DIE)
        if (score > highScore) {
            highScore = score
            prefs?.edit()?.putInt("high3d", score)?.apply()
        }
    }
}
