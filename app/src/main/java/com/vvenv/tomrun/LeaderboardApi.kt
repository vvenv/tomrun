package com.vvenv.tomrun

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 纪录榜 REST 客户端（HttpURLConnection + org.json，无第三方依赖）。
 *
 * GET  /api/v1/leaderboard/{slug}?limit=10
 * POST /api/v1/leaderboard/{slug}
 */
object LeaderboardApi {

    private const val CONNECT_MS = 8_000
    private const val READ_MS = 10_000

    val baseUrl: String
        get() = BuildConfig.LEADERBOARD_API_BASE.trim().trimEnd('/')

    fun isEnabled(): Boolean = baseUrl.isNotEmpty()

    data class SubmitResult(val accepted: Boolean, val rank: Int)

    fun fetch(category: Int, limit: Int = Leaderboards.MAX_ENTRIES): List<Leaderboards.Entry> {
        if (!isEnabled() || category !in 0 until Leaderboards.CATEGORY_COUNT) return emptyList()
        val slug = Leaderboards.CATEGORY_SLUGS[category]
        val conn = open("GET", "$baseUrl/api/v1/leaderboard/$slug?limit=$limit")
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw ApiException("HTTP $code")
            val body = readText(conn)
            parseEntries(JSONObject(body).optJSONArray("entries"))
        } finally {
            conn.disconnect()
        }
    }

    fun submit(
        category: Int,
        player: String,
        value: Int,
        whenMs: Long,
        detail: String,
        deviceId: String
    ): SubmitResult {
        if (!isEnabled() || category !in 0 until Leaderboards.CATEGORY_COUNT) {
            throw ApiException("API disabled")
        }
        val slug = Leaderboards.CATEGORY_SLUGS[category]
        val payload = JSONObject()
            .put("player", player)
            .put("value", value)
            .put("whenMs", whenMs)
            .put("detail", detail)
            .put("deviceId", deviceId)
            .put("gameVersion", BuildConfig.VERSION_NAME)
        val conn = open("POST", "$baseUrl/api/v1/leaderboard/$slug")
        return try {
            writeJson(conn, payload)
            val code = conn.responseCode
            if (code !in 200..299) throw ApiException("HTTP $code")
            val resp = JSONObject(readText(conn))
            SubmitResult(resp.optBoolean("accepted", false), resp.optInt("rank", 0))
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "tomrun/${BuildConfig.VERSION_NAME}")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return conn
    }

    private fun writeJson(conn: HttpURLConnection, json: JSONObject) {
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
    }

    private fun readText(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
    }

    private fun parseEntries(arr: JSONArray?): List<Leaderboards.Entry> {
        if (arr == null) return emptyList()
        val out = ArrayList<Leaderboards.Entry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Leaderboards.Entry(
                    player = o.optString("player", ""),
                    value = o.optInt("value", 0),
                    whenMs = o.optLong("whenMs", 0L),
                    detail = o.optString("detail", "")
                )
            )
        }
        return out
    }

    class ApiException(message: String) : Exception(message)
}
