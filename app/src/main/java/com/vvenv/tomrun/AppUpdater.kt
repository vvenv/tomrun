package com.vvenv.tomrun

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

/**
 * App 内更新：系统 DownloadManager 下载 → 校验 sha256 → 拉起系统安装器。
 *
 * 用 DownloadManager 而不是自己开连接：切到后台也照样下、通知栏有进度、断网会自己续；
 * 它给出的 content:// 地址还能直接交给安装器读，不用另配 FileProvider（这个工程没引 androidx）。
 *
 * 安装器只认签名一致的包：1.0 是 debug 签名，从 1.0 升上来会被系统拒绝，那时退回去开官网下载页。
 *
 * 所有方法都在主线程调用；状态字段给 HudView 每帧读。
 */
object AppUpdater {

    const val IDLE = 0
    const val DOWNLOADING = 1
    const val VERIFYING = 2
    const val READY = 3
    const val FAILED = 4

    @Volatile var state = IDLE
        private set
    /** 0~1；总大小未知时为 -1 */
    @Volatile var progress = 0f
        private set
    var release: UpdateCheck.Release? = null
        private set
    var failReason = ""
        private set

    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val PREFS = "tomrun_update"
    private val main = Handler(Looper.getMainLooper())
    private var downloadId = -1L
    private var lastPoll = 0L
    /** 去系统设置里开「允许安装未知应用」了，回到前台时接着装 */
    private var installAfterSettings = false

    private fun fileName(code: Int) = "tomrun-$code.apk"

    private fun dir(ctx: Context): File? = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

    /** 每次启动：已经装上了的安装包留着只占地方，删掉 */
    fun cleanup(ctx: Context) {
        dir(ctx)?.listFiles()?.forEach { f ->
            val code = f.name.removePrefix("tomrun-").removeSuffix(".apk").toIntOrNull()
            if (code != null && code <= BuildConfig.VERSION_CODE) f.delete()
        }
    }

    /** 查到新版后：把上次没下完 / 下完没装的那份接回来，别让孩子再等一遍 */
    fun restore(ctx: Context, latest: UpdateCheck.Release) {
        release = latest
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getInt("code", 0) == latest.versionCode) {
            downloadId = p.getLong("id", -1L)
            if (downloadId >= 0) {
                state = DOWNLOADING
                poll(ctx, force = true)
            }
        }
    }

    /** 点了「更新」：没下就开始下，下好了就装 */
    fun start(activity: Activity) {
        val rel = release ?: return
        when (state) {
            DOWNLOADING, VERIFYING -> return
            READY -> { install(activity); return }
        }
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val target = dir(activity)?.let { File(it, fileName(rel.versionCode)) }
        if (target == null) {
            fail("手机存储暂时不可用")
            return
        }
        target.delete()
        val req = DownloadManager.Request(Uri.parse(rel.apkUrl))
            .setTitle("汤姆猫跑酷 v${rel.versionName}")
            .setDescription("正在下载新版本")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName(rel.versionCode))
        downloadId = try {
            dm.enqueue(req)
        } catch (_: Exception) {
            fail("下载没能开始")
            return
        }
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("code", rel.versionCode).putLong("id", downloadId).apply()
        state = DOWNLOADING
        progress = 0f
    }

    /** HudView 每帧调；内部限流，半秒问一次 DownloadManager */
    fun poll(ctx: Context, force: Boolean = false) {
        if (state != DOWNLOADING || downloadId < 0) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastPoll < 500L) return
        lastPoll = now
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
            if (c == null || !c.moveToFirst()) {
                // 记录没了（用户在通知栏取消、或系统清掉了）：当没下过
                forget(ctx)
                return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> verify(ctx)
                DownloadManager.STATUS_FAILED -> {
                    forget(ctx)
                    fail("下载失败，检查一下网络再试")
                }
            }
        }
    }

    private fun verify(ctx: Context) {
        val rel = release ?: return
        val file = dir(ctx)?.let { File(it, fileName(rel.versionCode)) }
        state = VERIFYING
        Thread {
            val ok = file != null && file.exists() && checksumOk(file, rel)
            main.post {
                if (ok) {
                    state = READY
                    progress = 1f
                } else {
                    file?.delete()
                    forget(ctx)
                    fail("下载的文件不完整，再试一次")
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun checksumOk(file: File, rel: UpdateCheck.Release): Boolean {
        if (rel.size > 0 && file.length() != rel.size) return false
        if (rel.sha256.isEmpty()) return true
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val hex = md.digest().joinToString("") { "%02x".format(it) }
        return hex == rel.sha256
    }

    /** 拉起系统安装器；还没给「安装未知应用」权限就先送去设置页，回来后在 [onResume] 里接着装 */
    fun install(activity: Activity) {
        if (state != READY) return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            // 直接跳进一页英文设置，孩子不知道要干什么——先用一句话说清楚，再送过去
            android.app.AlertDialog.Builder(activity)
                .setTitle("还差一步")
                .setMessage("手机需要你允许「汤姆猫跑酷」安装更新：在下一页打开开关，再按返回，就会接着安装。")
                .setPositiveButton("去打开") { _, _ -> openInstallSettings(activity) }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = try { dm.getUriForDownloadedFile(downloadId) } catch (_: Exception) { null }
        if (uri == null) {
            forget(activity)
            fail("找不到下载好的文件，再试一次")
            return
        }
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openDownloadPage(activity)
        }
    }

    /** 「允许安装未知应用」这一页 API 26 才有；更老的系统装的时候自己会问 */
    private fun openInstallSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < 26) return
        installAfterSettings = true
        try {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
        } catch (_: Exception) {
            installAfterSettings = false
            openDownloadPage(activity)
        }
    }

    /** 从系统设置回来：开了权限就接着装，没开就算了，菜单上那行字还在 */
    fun onResume(activity: Activity) {
        if (!installAfterSettings) return
        installAfterSettings = false
        if (Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()) install(activity)
    }

    /**
     * 要不要弹「发现新版本」：同一个版本点过「以后再说」，当天就不再弹，第二天再问一次。
     * 主菜单那行提示一直都在，不靠弹窗也能找到入口。
     */
    fun shouldPrompt(ctx: Context): Boolean {
        val rel = release ?: return false
        if (state != IDLE) return false
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !(p.getInt("snoozeCode", 0) == rel.versionCode && p.getLong("snoozeDay", -1L) == today())
    }

    fun snooze(ctx: Context) {
        val rel = release ?: return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("snoozeCode", rel.versionCode).putLong("snoozeDay", today()).apply()
    }

    private fun today(): Long = (System.currentTimeMillis() + java.util.TimeZone.getDefault().rawOffset) / 86_400_000L

    fun openDownloadPage(activity: Activity) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateCheck.DOWNLOAD_PAGE)))
        } catch (_: Exception) {
        }
    }

    private fun forget(ctx: Context) {
        downloadId = -1L
        if (state == DOWNLOADING || state == VERIFYING) state = IDLE
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("code").remove("id").apply()
    }

    private fun fail(reason: String) {
        failReason = reason
        state = FAILED
    }
}
