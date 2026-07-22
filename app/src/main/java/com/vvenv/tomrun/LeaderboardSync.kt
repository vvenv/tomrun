package com.vvenv.tomrun

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 本地纪录入榜后异步上报；打开全服榜时拉取远端；失败写入待同步队列。
 */
class LeaderboardSync(private val prefs: SharedPreferences) {

    data class PendingSubmit(
        val category: Int,
        val player: String,
        val value: Int,
        val whenMs: Long,
        val detail: String
    )

    companion object {
        private const val PREF_PENDING = "lbPending"
        private const val PREF_REMOTE_PREFIX = "lbRemote_"
        private const val SEP_ROW = '\u001e'
        private const val SEP_FIELD = '\u001f'

        const val STATE_DISABLED = 0
        const val STATE_IDLE = 1
        const val STATE_SYNCING = 2
        const val STATE_OK = 3
        const val STATE_ERROR = 4
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)

    private val remoteBoards = Array(Leaderboards.CATEGORY_COUNT) { ArrayList<Leaderboards.Entry>() }

    @Volatile var state = STATE_DISABLED
        private set
    @Volatile var statusMessage = ""
        private set

    private var deviceId = ""
    private var listener: (() -> Unit)? = null

    fun attachDeviceId(id: String) {
        deviceId = id
        state = if (LeaderboardApi.isEnabled()) STATE_IDLE else STATE_DISABLED
        statusMessage = if (LeaderboardApi.isEnabled()) "" else "未配置服务器"
    }

    fun setListener(cb: (() -> Unit)?) {
        listener = cb
    }

    fun remoteEntries(category: Int): List<Leaderboards.Entry> {
        if (category !in 0 until Leaderboards.CATEGORY_COUNT) return emptyList()
        synchronized(remoteBoards) {
            return remoteBoards[category].toList()
        }
    }

    fun statusLine(): String = when (state) {
        STATE_DISABLED -> "全服榜未启用"
        STATE_SYNCING -> "同步中…"
        STATE_ERROR -> statusMessage.ifEmpty { "同步失败" }
        STATE_OK -> statusMessage.ifEmpty { "全服榜已更新" }
        else -> ""
    }

    fun loadRemoteCache() {
        for (i in 0 until Leaderboards.CATEGORY_COUNT) {
            val raw = prefs.getString(PREF_REMOTE_PREFIX + i, "") ?: ""
            synchronized(remoteBoards) {
                remoteBoards[i].clear()
                remoteBoards[i].addAll(decodeEntries(raw))
            }
        }
    }

    fun onLocalEntry(category: Int, entry: Leaderboards.Entry) {
        if (!LeaderboardApi.isEnabled() || deviceId.isEmpty()) return
        enqueuePending(
            PendingSubmit(category, entry.player, entry.value, entry.whenMs, entry.detail)
        )
        flushPendingAsync()
    }

    fun refreshCategory(category: Int) {
        if (!LeaderboardApi.isEnabled()) return
        runAsync {
            try {
                setState(STATE_SYNCING, "")
                val entries = LeaderboardApi.fetch(category)
                cacheRemote(category, entries)
                setState(STATE_OK, "全服榜已更新")
            } catch (e: Exception) {
                setState(STATE_ERROR, e.message ?: "拉取失败")
            }
        }
    }

    fun flushPendingAsync() {
        if (!LeaderboardApi.isEnabled() || deviceId.isEmpty()) return
        runAsync { flushPendingBlocking() }
    }

    private fun flushPendingBlocking() {
        val pending = loadPending().toMutableList()
        if (pending.isEmpty()) return
        setState(STATE_SYNCING, "")
        val remain = ArrayList<PendingSubmit>()
        var okAny = false
        for (item in pending) {
            try {
                LeaderboardApi.submit(
                    item.category, item.player, item.value, item.whenMs, item.detail, deviceId
                )
                okAny = true
            } catch (_: Exception) {
                remain.add(item)
            }
        }
        savePending(remain)
        if (remain.isEmpty()) {
            setState(STATE_OK, if (okAny) "已上传纪录" else "")
        } else {
            setState(STATE_ERROR, "待同步 ${remain.size} 条")
        }
    }

    private fun cacheRemote(category: Int, entries: List<Leaderboards.Entry>) {
        synchronized(remoteBoards) {
            remoteBoards[category].clear()
            remoteBoards[category].addAll(entries)
        }
        prefs.edit().putString(PREF_REMOTE_PREFIX + category, encodeEntries(entries)).apply()
    }

    private fun enqueuePending(item: PendingSubmit) {
        val list = loadPending().toMutableList()
        list.add(item)
        savePending(list)
    }

    private fun loadPending(): List<PendingSubmit> {
        val raw = prefs.getString(PREF_PENDING, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<PendingSubmit>()
        for (chunk in raw.split(SEP_ROW)) {
            if (chunk.isEmpty()) continue
            val p = chunk.split(SEP_FIELD, limit = 5)
            if (p.size < 5) continue
            val cat = p[0].toIntOrNull() ?: continue
            val value = p[2].toIntOrNull() ?: continue
            val whenMs = p[3].toLongOrNull() ?: continue
            out.add(PendingSubmit(cat, p[1], value, whenMs, p[4]))
        }
        return out
    }

    private fun savePending(list: List<PendingSubmit>) {
        if (list.isEmpty()) {
            prefs.edit().remove(PREF_PENDING).apply()
            return
        }
        val encoded = list.joinToString(SEP_ROW.toString()) { p ->
            "${p.category}$SEP_FIELD${p.player}$SEP_FIELD${p.value}$SEP_FIELD${p.whenMs}$SEP_FIELD${p.detail}"
        }
        prefs.edit().putString(PREF_PENDING, encoded).apply()
    }

    private fun encodeEntries(list: List<Leaderboards.Entry>): String {
        if (list.isEmpty()) return ""
        return list.joinToString(SEP_ROW.toString()) { e ->
            "${e.player}$SEP_FIELD${e.value}$SEP_FIELD${e.whenMs}$SEP_FIELD${e.detail}"
        }
    }

    private fun decodeEntries(raw: String): List<Leaderboards.Entry> {
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<Leaderboards.Entry>()
        for (chunk in raw.split(SEP_ROW)) {
            if (chunk.isEmpty()) continue
            val p = chunk.split(SEP_FIELD, limit = 4)
            if (p.size < 3) continue
            val value = p[1].toIntOrNull() ?: continue
            val whenMs = p[2].toLongOrNull() ?: continue
            val detail = if (p.size >= 4) p[3] else ""
            out.add(Leaderboards.Entry(p[0], value, whenMs, detail))
        }
        return out
    }

    private fun runAsync(block: () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                block()
            } finally {
                busy.set(false)
            }
        }
    }

    private fun setState(next: Int, message: String) {
        state = next
        statusMessage = message
        mainHandler.post { listener?.invoke() }
    }
}
