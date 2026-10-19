package com.vvenv.tomrun

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 启动时看一眼官网的 /version.json（发版脚本写入），有新版就提示，点一下在 App 里下载安装（见 [AppUpdater]）。
 * APK 是官网自己分发的，没有商店推送更新，不提醒的话老版本会一直留在孩子手机上。
 */
object UpdateCheck {

    /** 下载页：App 内更新出岔子（比如签名对不上装不进去）时的退路 */
    const val DOWNLOAD_PAGE = "https://run.wenfu.dev/#download"

    /** version.json 里的一次发版；sha256 / size 是发版脚本算的，下载完拿来对一遍 */
    class Release(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val sha256: String,
        val size: Long,
    )

    fun checkAsync(onNewer: (Release) -> Unit) {
        if (!LeaderboardApi.isEnabled()) return
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                val base = LeaderboardApi.baseUrl
                val conn = URL("$base/version.json").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    conn.useCaches = false
                    if (conn.responseCode != 200) return@Thread
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val code = json.optInt("versionCode", 0)
                    val name = json.optString("versionName", "")
                    if (code > BuildConfig.VERSION_CODE && name.isNotEmpty()) {
                        val apk = json.optString("apk", "").ifEmpty { "$base/downloads/tomrun.apk" }
                        val release = Release(
                            code, name,
                            if (apk.startsWith("http")) apk else base + "/" + apk.trimStart('/'),
                            json.optString("sha256", "").lowercase(),
                            json.optLong("size", 0L),
                        )
                        main.post { onNewer(release) }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // 没网就算了，下次启动再看
            }
        }.apply { isDaemon = true }.start()
    }
}
