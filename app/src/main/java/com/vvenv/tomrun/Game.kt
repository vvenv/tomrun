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
        const val P_BOOST = 8    // 闪电冲刺：加速 + 撞碎障碍

        val LANE_X = floatArrayOf(-2.2f, 0f, 2.2f)
        const val SPAWN_Z = -150f
        const val GRAVITY = 24f
        const val JUMP_V = 8.6f
        const val SLIDE_TIME = 0.75f
        const val RAMP_LENGTH = 9f
        const val RAMP_HEIGHT = 2.4f

        const val CABLE_H = 5.4f   // 索道钢缆高度
        const val RIDE_Y = 3.0f    // 滑索时猫的脚底高度

        // 连击 / 道具上限
        const val COMBO_WINDOW = 1.6f
        const val COMBO_STEP = 5
        const val COMBO_MAX_MULT = 5
        const val MAGNET_BASE = 8f
        const val DOUBLE_BASE = 10f
        const val BOOST_BASE = 6f
        const val MAGNET_CAP = 16f
        const val DOUBLE_CAP = 20f
        const val BOOST_CAP = 12f
        const val HELMET_MAX = 2

        // 局内任务目标
        const val QUEST_COINS = 50
        const val QUEST_COMBO = 15
        const val QUEST_DIST = 800

        // 音效 / 反馈事件
        const val EV_JUMP = 0
        const val EV_SLIDE = 1
        const val EV_COIN = 2
        const val EV_POWER = 3
        const val EV_SHIELD = 4
        const val EV_DIE = 5
        const val EV_ZIP = 6
        const val EV_RECORD = 7
        const val EV_COMBO = 8
        const val EV_BOOST = 9
        const val EV_SMASH = 10
        const val EV_QUEST = 11
        const val EV_ACHIEVE = 12

        const val CAT_COLOR_COUNT = 8

        // 天气
        const val W_SUNNY = 0
        const val W_RAIN = 1
        const val W_SNOW = 2

        // 反馈强度（振动分级）
        const val HAPTIC_LIGHT = 1
        const val HAPTIC_MED = 2
        const val HAPTIC_HEAVY = 3
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

    /** HUD 飘分 / 提示条目 */
    class FloatText(val text: String, val color: Int, var life: Float = 1.1f, var y: Float = 0f)

    /** 渲染用体素粒子 */
    class Particle(
        var x: Float, var y: Float, var z: Float,
        var vx: Float, var vy: Float, var vz: Float,
        var life: Float, val color: FloatArray, val size: Float
    )

    @Volatile var state = State.READY
    @Volatile var score = 0
    @Volatile var coins = 0
    @Volatile var highScore = 0
    @Volatile var deadTime = 0f

    // 道具状态（HUD/渲染读取）
    @Volatile var magnetTime = 0f
    @Volatile var doubleTime = 0f
    @Volatile var boostTime = 0f
    @Volatile var helmetLayers = 0
    @Volatile var riding: Zip? = null
    val helmet get() = helmetLayers > 0
    val boosting get() = boostTime > 0f

    // 连击
    @Volatile var combo = 0
    @Volatile var comboMult = 1
    @Volatile var comboFlash = 0f
    private var comboTimer = 0f
    private var comboScore = 0   // 连击额外分数（不计入 coins*10）

    // 局内任务进度
    @Volatile var questCoinsDone = false
    @Volatile var questComboDone = false
    @Volatile var questDistDone = false
    @Volatile var questFlash = 0f
    @Volatile var questFlashText = ""
    @Volatile var bestComboRun = 0
    private var missionBonus = 0

    // 持久成就（累计）
    @Volatile var totalCoins = 0
    @Volatile var totalDistance = 0
    @Volatile var totalQuests = 0
    @Volatile var bestComboEver = 0
    @Volatile var achieveCount = 0
    @Volatile var achieveFlash = 0f
    @Volatile var achieveFlashText = ""
    private var unlockedMask = 0

    // 反馈：飘分、粒子、相机震动、触觉强度
    val floatTexts = ArrayList<FloatText>()
    val particles = ArrayList<Particle>()
    @Volatile var shake = 0f
    @Volatile var hapticPulse = 0   // 0=无；MainActivity 读取后清零
    @Volatile var lastFloat = ""
    @Volatile var lastFloatColor = 0xFFFFD54A.toInt()
    @Volatile var floatFlash = 0f

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

    // 昼夜：0=正午 → 0.25=黄昏 → 0.5=午夜 → 0.75=黎明 → 1=正午
    @Volatile var dayPhase = 0.12f + Random.nextFloat() * 0.2f
    private var daySpeed = 1f / 90f   // 约 90 秒一昼夜

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
    var baseSpeed = 14f
    var distance = 0f
    private var gapRemaining = 0f
    private var zipGap = 100f
    private var scoreBoost = 0f      // 加倍期间的额外里程分
    private var invulnTime = 0f

    val entities = ArrayList<Entity>()
    val ziplines = ArrayList<Zip>()

    private var prefs: SharedPreferences? = null
    private var sessionCoins = 0
    private var sessionDistSaved = 0f

    fun attachPrefs(p: SharedPreferences) {
        prefs = p
        highScore = p.getInt("high3d", 0)
        catColor = p.getInt("catColor", 0) % CAT_COLOR_COUNT
        totalCoins = p.getInt("totalCoins", 0)
        totalDistance = p.getInt("totalDist", 0)
        totalQuests = p.getInt("totalQuests", 0)
        bestComboEver = p.getInt("bestCombo", 0)
        unlockedMask = p.getInt("achieveMask", 0)
        achieveCount = Integer.bitCount(unlockedMask)
    }

    fun cycleCatColor() {
        catColor = (catColor + 1) % CAT_COLOR_COUNT
        prefs?.edit()?.putInt("catColor", catColor)?.apply()
    }

    /** MainActivity 消费触觉脉冲后调用 */
    fun consumeHaptic(): Int {
        val h = hapticPulse
        hapticPulse = 0
        return h
    }

    private fun emit(event: Int, haptic: Int = 0) {
        if (haptic > hapticPulse) hapticPulse = haptic
        onEvent?.invoke(event)
    }

    fun magnetLeft() = ceil(magnetTime).toInt()
    fun doubleLeft() = ceil(doubleTime).toInt()
    fun boostLeft() = ceil(boostTime).toInt()

    /** 夜晚强度 0~1，用于渲染调暗与路灯 */
    fun nightAmount(): Float {
        // 0.25~0.75 逐渐入夜，0.4~0.6 最暗
        val p = dayPhase
        return when {
            p < 0.20f -> 0f
            p < 0.35f -> (p - 0.20f) / 0.15f
            p < 0.65f -> 1f
            p < 0.80f -> 1f - (p - 0.65f) / 0.15f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    /** 黄昏暖色强度 0~1 */
    fun duskAmount(): Float {
        val p = dayPhase
        return when {
            p in 0.18f..0.32f -> 1f - abs(p - 0.25f) / 0.07f
            p in 0.68f..0.82f -> 1f - abs(p - 0.75f) / 0.07f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

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
            riding = null
            velY = -14f
        } else {
            slideTimer = SLIDE_TIME
            if (!onGround) velY = -14f
            emit(EV_SLIDE, HAPTIC_LIGHT)
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
            emit(EV_JUMP, HAPTIC_LIGHT)
        }
    }

    @Synchronized fun reset() {
        entities.clear()
        ziplines.clear()
        floatTexts.clear()
        particles.clear()
        lane = 1; catX = 0f; catY = 0f; velY = 0f
        groundY = 0f
        slideTimer = 0f; runPhase = 0f
        baseSpeed = 14f; speed = 14f; distance = 0f; score = 0; coins = 0
        deadTime = 0f
        magnetTime = 0f; doubleTime = 0f; boostTime = 0f; helmetLayers = 0
        riding = null; scoreBoost = 0f; invulnTime = 0f
        recordFlash = 0f; recordDone = false
        combo = 0; comboMult = 1; comboTimer = 0f; comboFlash = 0f; comboScore = 0
        questCoinsDone = false; questComboDone = false; questDistDone = false
        questFlash = 0f; questFlashText = ""; bestComboRun = 0; missionBonus = 0
        achieveFlash = 0f; achieveFlashText = ""
        shake = 0f; hapticPulse = 0; floatFlash = 0f; lastFloat = ""
        sessionCoins = 0; sessionDistSaved = 0f
        zipGap = 90f + Random.nextFloat() * 80f
        var z = -45f
        while (z > SPAWN_Z) {
            spawnWave(z)
            z -= 22f + Random.nextFloat() * 12f
        }
        gapRemaining = nextGap()
    }

    private fun nextGap() = 16f + Random.nextFloat() * 14f + baseSpeed * 0.35f

    // ---------- 主更新（GL 线程） ----------
    @Synchronized fun update(dt: Float) {
        tickDayNight(dt)
        tickFeedback(dt)
        // 镜头震动在死亡界面也要衰减，否则会一直抖
        if (shake > 0f) shake = (shake - dt * 5f).coerceAtLeast(0f)
        if (state == State.DEAD) {
            deadTime += dt
            return
        }
        tickWeather(dt)
        runPhase += dt * speed * 0.9f
        if (state != State.RUNNING) return

        baseSpeed = min(baseSpeed + 0.35f * dt, 30f)
        speed = if (boosting) baseSpeed * 1.30f else baseSpeed
        val dz = speed * dt
        distance += dz

        if (magnetTime > 0f) magnetTime = (magnetTime - dt).coerceAtLeast(0f)
        if (doubleTime > 0f) {
            doubleTime = (doubleTime - dt).coerceAtLeast(0f)
            scoreBoost += dz
        }
        if (boostTime > 0f) boostTime = (boostTime - dt).coerceAtLeast(0f)
        if (invulnTime > 0f) invulnTime = (invulnTime - dt).coerceAtLeast(0f)

        // 连击超时
        if (combo > 0) {
            comboTimer -= dt
            if (comboTimer <= 0f) resetCombo()
        }
        if (comboFlash > 0f) comboFlash -= dt
        if (questFlash > 0f) questFlash -= dt
        if (achieveFlash > 0f) achieveFlash -= dt
        if (floatFlash > 0f) floatFlash -= dt

        // 猫横向
        val targetX = LANE_X[lane]
        catX += (targetX - catX) * min(1f, dt * 12f)

        val wasGrounded = onGround && velY <= 0f
        val nextGroundY = rampSurfaceAtPlayer(dz)
        groundY = nextGroundY

        for (zip in ziplines) zip.entryZ += dz
        ziplines.removeAll { it.exitZ > 12f }

        val r = riding
        if (r != null) {
            catY += (RIDE_Y - catY) * min(1f, dt * 8f)
            velY = 0f
            slideTimer = 0f
            if (r.exitZ >= 0f || r !in ziplines) riding = null
        } else {
            if (wasGrounded && nextGroundY >= catY - 0.08f) {
                catY = nextGroundY
                velY = 0f
            } else if (!onGround || velY > 0f) {
                velY -= GRAVITY * dt
                catY += velY * dt
                if (catY <= groundY) { catY = groundY; velY = 0f }
            }
            if (slideTimer > 0f) slideTimer -= dt
            for (zip in ziplines) {
                if (zip.lane == lane && catY < 0.3f &&
                    zip.entryZ >= 0f && zip.entryZ - dz < 0f &&
                    abs(catX - LANE_X[lane]) < 0.6f
                ) {
                    riding = zip
                    emit(EV_ZIP, HAPTIC_MED)
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

        // 粒子物理
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.life -= dt
            if (p.life <= 0f) { pit.remove(); continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt + dz
            p.vy -= 18f * dt
            if (p.y < 0.05f) { p.y = 0.05f; p.vy *= -0.3f }
        }

        gapRemaining -= dz
        while (gapRemaining <= 0f) {
            spawnWave(SPAWN_Z - gapRemaining)
            gapRemaining += nextGap()
        }
        zipGap -= dz
        if (zipGap <= 0f && ziplines.isEmpty()) {
            spawnZipline()
            zipGap = 160f + Random.nextFloat() * 160f
        }

        checkCollision()
        checkQuests()
        score = (distance + scoreBoost).toInt() + coins * 10 + comboScore + missionBonus

        if (recordFlash > 0f) recordFlash -= dt
        if (!recordDone && highScore > 0 && score > highScore) {
            recordDone = true
            recordFlash = 2.6f
            emit(EV_RECORD, HAPTIC_MED)
        }
    }

    private fun tickDayNight(dt: Float) {
        dayPhase += daySpeed * dt
        if (dayPhase >= 1f) dayPhase -= 1f
    }

    private fun tickFeedback(dt: Float) {
        val fit = floatTexts.iterator()
        while (fit.hasNext()) {
            val f = fit.next()
            f.life -= dt
            f.y += dt * 40f
            if (f.life <= 0f) fit.remove()
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
        // 随机道具：磁铁 / 头盔 / 加倍 / 冲刺
        if (Random.nextFloat() < 0.16f) {
            val kinds = intArrayOf(P_MAGNET, P_HELMET, P_DOUBLE, P_BOOST)
            val kind = kinds[Random.nextInt(kinds.size)]
            entities.add(Entity(kind, Random.nextInt(3), zBase - 10f, 1.2f))
        }
    }

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
                        collectCoin(e)
                    }
                }
                P_MAGNET, P_HELMET, P_DOUBLE, P_BOOST -> {
                    if (abs(e.z) < 1.0f && abs(e.x - catX) < 1.1f && abs(e.y - catCenterY) < 1.3f) {
                        e.taken = true
                        pickupPower(e)
                    }
                }
                else -> {
                    if (onZip || invulnTime > 0f) continue
                    if (abs(e.z) > 0.9f) continue
                    if (abs(e.x - catX) >= 1.1f) continue
                    val hit = when (e.kind) {
                        OBST_LOW -> catY < 0.55f
                        OBST_BAR -> !sliding || catY > 0.3f
                        OBST_RAMP -> false
                        else -> true
                    }
                    if (!hit) continue

                    // 冲刺：撞碎障碍
                    if (boosting && e.kind != OBST_RAMP) {
                        e.taken = true
                        smashObstacle(e)
                        continue
                    }

                    if (helmetLayers > 0) {
                        helmetLayers--
                        invulnTime = 1f
                        shake = 0.45f
                        resetCombo()
                        spawnBurst(e.x, catY + 1f, e.z, floatArrayOf(1f, 0.76f, 0.12f, 1f), 8)
                        emit(EV_SHIELD, HAPTIC_HEAVY)
                    } else {
                        die()
                        return
                    }
                }
            }
        }
    }

    private fun collectCoin(e: Entity) {
        val gained = if (doubleTime > 0f) 2 else 1
        coins += gained
        sessionCoins += gained

        // 连击：续接窗口内累加；倍率每 COMBO_STEP 枚升一级，最高 COMBO_MAX_MULT
        val prevMult = comboMult
        combo++
        comboTimer = COMBO_WINDOW
        if (combo > bestComboRun) bestComboRun = combo
        if (bestComboRun > bestComboEver) bestComboEver = bestComboRun
        comboMult = min(COMBO_MAX_MULT, 1 + (combo - 1) / COMBO_STEP)
        // 连击额外分：基础分已在 coins*10；此处只加 (mult-1) 倍奖励，避免与加倍双重计算
        val basePts = 10 * gained
        val comboExtra = basePts * (comboMult - 1)
        comboScore += comboExtra

        val show = if (comboMult > 1) "+${basePts + comboExtra} x$comboMult" else "+$basePts"
        pushFloat(show, 0xFFFFD54A.toInt())
        spawnBurst(e.x, e.y, e.z, floatArrayOf(1f, 0.84f, 0.10f, 1f), 5)

        if (comboMult > prevMult) {
            comboFlash = 1.4f
            emit(EV_COMBO, HAPTIC_MED)
        } else {
            emit(EV_COIN, HAPTIC_LIGHT)
        }
        if (bestComboEver >= 25 || totalCoins + sessionCoins >= 500) {
            tryUnlockAchievements()
        }
    }

    private fun pickupPower(e: Entity) {
        when (e.kind) {
            P_MAGNET -> {
                magnetTime = min(MAGNET_CAP, magnetTime + MAGNET_BASE)
                pushFloat("磁铁 +${MAGNET_BASE.toInt()}s", 0xFFFF6B6B.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_HELMET -> {
                helmetLayers = min(HELMET_MAX, helmetLayers + 1)
                pushFloat(if (helmetLayers >= 2) "头盔 x2" else "头盔", 0xFFFFC21F.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_DOUBLE -> {
                doubleTime = min(DOUBLE_CAP, doubleTime + DOUBLE_BASE)
                pushFloat("加倍 +${DOUBLE_BASE.toInt()}s", 0xFFC77DFF.toInt())
                emit(EV_POWER, HAPTIC_MED)
            }
            P_BOOST -> {
                boostTime = min(BOOST_CAP, boostTime + BOOST_BASE)
                pushFloat("冲刺！", 0xFF4DE8FF.toInt())
                emit(EV_BOOST, HAPTIC_MED)
            }
        }
        spawnBurst(e.x, e.y, e.z, floatArrayOf(0.9f, 0.5f, 1f, 1f), 10)
    }

    private fun smashObstacle(e: Entity) {
        missionBonus += 25
        pushFloat("+25 撞碎", 0xFF4DE8FF.toInt())
        shake = 0.35f
        val col = when (e.kind) {
            OBST_BAR -> floatArrayOf(0.55f, 0.58f, 0.64f, 1f)
            else -> floatArrayOf(0.82f, 0.52f, 0.20f, 1f)
        }
        spawnBurst(e.x, 1.0f, e.z, col, 14)
        emit(EV_SMASH, HAPTIC_MED)
    }

    private fun resetCombo() {
        combo = 0
        comboMult = 1
        comboTimer = 0f
    }

    private fun checkQuests() {
        if (!questCoinsDone && coins >= QUEST_COINS) {
            questCoinsDone = true
            completeQuest("任务：收集 ${QUEST_COINS} 金币", 200)
        }
        if (!questComboDone && bestComboRun >= QUEST_COMBO) {
            questComboDone = true
            completeQuest("任务：连击 ${QUEST_COMBO}", 250)
        }
        if (!questDistDone && distance >= QUEST_DIST) {
            questDistDone = true
            completeQuest("任务：奔跑 ${QUEST_DIST} 米", 300)
        }
    }

    private fun completeQuest(text: String, bonus: Int) {
        missionBonus += bonus
        totalQuests++
        questFlash = 2.4f
        questFlashText = text
        pushFloat("+$bonus 任务", 0xFF7DEBA0.toInt())
        emit(EV_QUEST, HAPTIC_MED)
        persistStats()
        tryUnlockAchievements()
    }

    private fun tryUnlockAchievements() {
        // bit0: 累计 500 金币  bit1: 累计 5000 米  bit2: 完成 10 任务  bit3: 连击 25
        fun unlock(bit: Int, name: String) {
            val mask = 1 shl bit
            if (unlockedMask and mask == 0) {
                unlockedMask = unlockedMask or mask
                achieveCount = Integer.bitCount(unlockedMask)
                achieveFlash = 2.8f
                achieveFlashText = "成就：$name"
                emit(EV_ACHIEVE, HAPTIC_MED)
                persistStats()
            }
        }
        if (totalCoins + sessionCoins >= 500) unlock(0, "金币收藏家")
        if (totalDistance + distance.toInt() >= 5000) unlock(1, "长跑健将")
        if (totalQuests >= 10) unlock(2, "任务达人")
        if (bestComboEver >= 25) unlock(3, "连击大师")
    }

    private fun pushFloat(text: String, color: Int) {
        floatTexts.add(FloatText(text, color))
        if (floatTexts.size > 8) floatTexts.removeAt(0)
        lastFloat = text
        lastFloatColor = color
        floatFlash = 0.9f
    }

    private fun spawnBurst(x: Float, y: Float, z: Float, color: FloatArray, n: Int) {
        for (i in 0 until n) {
            particles.add(
                Particle(
                    x, y, z,
                    (Random.nextFloat() - 0.5f) * 6f,
                    Random.nextFloat() * 5f + 2f,
                    (Random.nextFloat() - 0.5f) * 4f,
                    0.45f + Random.nextFloat() * 0.4f,
                    color,
                    0.12f + Random.nextFloat() * 0.12f
                )
            )
        }
        if (particles.size > 80) {
            particles.subList(0, particles.size - 80).clear()
        }
    }

    private fun persistStats() {
        val p = prefs ?: return
        p.edit()
            .putInt("totalCoins", totalCoins)
            .putInt("totalDist", totalDistance)
            .putInt("totalQuests", totalQuests)
            .putInt("bestCombo", bestComboEver)
            .putInt("achieveMask", unlockedMask)
            .apply()
    }

    private fun die() {
        state = State.DEAD
        deadTime = 0f
        shake = 0.28f
        resetCombo()
        emit(EV_DIE, HAPTIC_HEAVY)

        // 结算累计
        totalCoins += sessionCoins
        val distAdd = (distance - sessionDistSaved).toInt().coerceAtLeast(0)
        totalDistance += distAdd
        sessionDistSaved = distance
        if (bestComboRun > bestComboEver) bestComboEver = bestComboRun
        persistStats()
        tryUnlockAchievements()

        if (score > highScore) {
            highScore = score
            prefs?.edit()?.putInt("high3d", score)?.apply()
        }
    }
}
