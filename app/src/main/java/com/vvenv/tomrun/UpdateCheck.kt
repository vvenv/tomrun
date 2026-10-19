package com.vvenv.tomrun

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 启动时看一眼官网的 /version.json（发版脚本写入），有新版就在主菜单安静地提一句。
 * APK 是官网自己分发的，没有商店推送更新，不提醒的话老版本会一直留在孩子手机上。
 */
object UpdateCheck {

    /** 下载页；版本号写在 version.json 里，地址固定 */
    const val DOWNLOAD_PAGE = "https://run.wenfu.dev/#download"

    fun checkAsync(onNewer: (versionName: String) -> Unit) {
        if (!LeaderboardApi.isEnabled()) return
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                val conn = URL("${LeaderboardApi.baseUrl}/version.json").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    if (conn.responseCode != 200) return@Thread
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val code = json.optInt("versionCode", 0)
                    val name = json.optString("versionName", "")
                    if (code > BuildConfig.VERSION_CODE && name.isNotEmpty()) main.post { onNewer(name) }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // 没网就算了，下次启动再看
            }
        }.apply { isDaemon = true }.start()
    }
}
