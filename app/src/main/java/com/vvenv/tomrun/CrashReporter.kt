package com.vvenv.tomrun

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 闪退记录：崩溃时把堆栈写进私有目录，下次启动在后台传给 /api/v1/crash，成功就删。
 *
 * APK 是官网自己分发的，没有应用商店的崩溃后台；不记下来就永远不知道谁闪退了。
 * 只传版本、系统版本、机型和堆栈，不带设备编号和角色名。
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_TRACE = 12_000

    fun install(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                file.writeText("thread=${thread.name}\n" + sw.toString().take(MAX_TRACE))
            } catch (_: Exception) {
                // 写不进去也不能挡住系统的崩溃处理
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun uploadPendingAsync(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || !LeaderboardApi.isEnabled()) return
        Thread {
            try {
                val payload = JSONObject()
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".take(64))
                    .put("trace", file.readText().take(MAX_TRACE))
                val conn = URL("${LeaderboardApi.baseUrl}/api/v1/crash").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 10_000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
                    // 4xx 说明服务端不收这份，留着也只会每次启动重发
                    if (conn.responseCode in 200..499) file.delete()
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // 没网就等下次启动
            }
        }.apply { isDaemon = true }.start()
    }
}
