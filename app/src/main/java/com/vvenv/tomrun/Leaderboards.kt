package com.vvenv.tomrun

import android.content.SharedPreferences

/**
 * 第一阶段：本地异步纪录榜（SharedPreferences 持久化，无网络）。
 * 每类保留前 [MAX_ENTRIES] 条；同分 newer 优先。
 */
class Leaderboards {

    data class Entry(
        val player: String,
        val value: Int,
        val whenMs: Long,
        val detail: String = ""
    )

    companion object {
        const val MAX_ENTRIES = 10

        const val RUN_DISTANCE = 0
        const val RUN_SCORE = 1
        const val MUSEUM_COLLECT = 2
        const val HONOR_COUNT = 3
        const val CATEGORY_COUNT = 4

        private val PREF_KEYS = arrayOf(
            "lbRunDist", "lbRunScore", "lbMuseum", "lbHonor"
        )

        val TITLES = arrayOf(
            "单场距离", "单场得分", "藏品图鉴", "荣誉获得"
        )

        val SUBTITLES = arrayOf(
            "一局跑过的最远距离",
            "一局获得的最高得分",
            "图鉴中已收集的文物总数",
            "累计解锁的荣誉级数"
        )

        private const val SEP_ENTRY = '\u001e'
        private const val SEP_FIELD = '\u001f'

        /** REST 路径片段，与 server/leaderboard_server.py 一致 */
        val CATEGORY_SLUGS = arrayOf(
            "run_distance", "run_score", "museum_collect", "honor_count"
        )
    }

    /** 本地入榜后回调（category, entry），供云端同步 */
    var onEntrySubmitted: ((Int, Entry) -> Unit)? = null

    private val boards = Array(CATEGORY_COUNT) { ArrayList<Entry>(MAX_ENTRIES) }

    fun load(prefs: SharedPreferences) {
        for (i in 0 until CATEGORY_COUNT) {
            boards[i].clear()
            boards[i].addAll(decode(prefs.getString(PREF_KEYS[i], "") ?: ""))
        }
    }

    fun save(prefs: SharedPreferences) {
        val ed = prefs.edit()
        for (i in 0 until CATEGORY_COUNT) ed.putString(PREF_KEYS[i], encode(boards[i]))
        ed.apply()
    }

    fun entries(category: Int): List<Entry> {
        if (category !in 0 until CATEGORY_COUNT) return emptyList()
        return boards[category].toList()
    }

    fun formatValue(category: Int, value: Int): String = when (category) {
        RUN_DISTANCE -> "${value} m"
        MUSEUM_COLLECT -> "${value}/${Game.RELIC_COUNT}"
        HONOR_COUNT -> "${value}/${Game.ACHIEVE_MAX}"
        else -> value.toString()
    }

    /** 本局结算：距离与得分分别尝试入榜，返回入榜的类别下标列表 */
    fun recordRun(player: String, distance: Int, score: Int): IntArray {
        val hits = ArrayList<Int>(2)
        if (submit(RUN_DISTANCE, player, distance, runDetail(distance, score))) hits.add(RUN_DISTANCE)
        if (submit(RUN_SCORE, player, score, "${distance} m")) hits.add(RUN_SCORE)
        return hits.toIntArray()
    }

    /** 里程碑类：仅当 value 高于该玩家已有最佳时才尝试入榜 */
    fun recordMilestone(category: Int, player: String, value: Int, detail: String): Boolean {
        if (category !in intArrayOf(HONOR_COUNT, MUSEUM_COLLECT)) return false
        if (value <= 0) return false
        val best = boards[category]
            .filter { it.player == player }
            .maxOfOrNull { it.value } ?: 0
        if (value <= best) return false
        return submit(category, player, value, detail)
    }

    /** 从旧存档的最高分/最远距离补种一条纪录（仅执行一次） */
    fun seedFromLegacy(player: String, highDistance: Int, highScore: Int, relicsFound: Int, honorCount: Int) {
        if (highDistance > 0) submit(RUN_DISTANCE, player, highDistance, "历史最佳")
        if (highScore > 0) submit(RUN_SCORE, player, highScore, "历史最佳")
        if (relicsFound > 0) submit(MUSEUM_COLLECT, player, relicsFound, "当前图鉴")
        if (honorCount > 0) submit(HONOR_COUNT, player, honorCount, "当前荣誉")
    }

    private fun runDetail(distance: Int, score: Int): String = "${distance} m · 得分 $score"

    private fun submit(category: Int, player: String, value: Int, detail: String): Boolean {
        if (category !in 0 until CATEGORY_COUNT) return false
        val list = boards[category]
        if (list.size >= MAX_ENTRIES) {
            val worst = list.last()
            if (value < worst.value) return false
            if (value == worst.value && worst.whenMs >= System.currentTimeMillis()) return false
        }
        val entry = Entry(sanitize(player), value, System.currentTimeMillis(), sanitize(detail))
        list.add(entry)
        list.sortWith(compareByDescending<Entry> { it.value }.thenByDescending { it.whenMs })
        while (list.size > MAX_ENTRIES) list.removeAt(list.lastIndex)
        onEntrySubmitted?.invoke(category, entry)
        return true
    }

    private fun sanitize(raw: String): String =
        raw.replace(SEP_ENTRY, ' ').replace(SEP_FIELD, ' ').trim().take(Game.CHARACTER_NAME_MAX_LENGTH + 24)

    private fun encode(list: List<Entry>): String {
        if (list.isEmpty()) return ""
        return list.joinToString(SEP_ENTRY.toString()) { e ->
            "${sanitize(e.player)}$SEP_FIELD${e.value}$SEP_FIELD${e.whenMs}$SEP_FIELD${sanitize(e.detail)}"
        }
    }

    private fun decode(raw: String): List<Entry> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<Entry>(MAX_ENTRIES)
        for (chunk in raw.split(SEP_ENTRY)) {
            if (chunk.isEmpty()) continue
            val p = chunk.split(SEP_FIELD, limit = 4)
            if (p.size < 3) continue
            val value = p[1].toIntOrNull() ?: continue
            val whenMs = p[2].toLongOrNull() ?: continue
            val detail = if (p.size >= 4) p[3] else ""
            out.add(Entry(p[0], value, whenMs, detail))
        }
        out.sortWith(compareByDescending<Entry> { it.value }.thenByDescending { it.whenMs })
        while (out.size > MAX_ENTRIES) out.removeAt(out.lastIndex)
        return out
    }
}
