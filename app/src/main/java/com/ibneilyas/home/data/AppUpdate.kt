package com.ibneilyas.home.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Checks the public "dev" release on GitHub, downloads the APK and opens the Android installer. */
object AppUpdate {
    private const val BASE = "https://github.com/Ibneilyas124/Ibn-e-Ilyas-Home-Automation/releases/download/dev/"
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Suppress("DEPRECATION")
    fun currentBuild(ctx: Context): Long {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    }

    suspend fun latestBuild(): Long? = withContext(Dispatchers.IO) {
        try {
            val r = Request.Builder().url(BASE + "dev-build.txt?t=" + System.currentTimeMillis()).build()
            http.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body!!.string().trim().toLongOrNull()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads the APK into the app cache. Refuses small files or files that are not a zip/APK. */
    suspend fun download(ctx: Context, onProgress: (Int) -> Unit = {}): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "updates")
            dir.mkdirs()
            val f = File(dir, "IbnEIlyasHome-dev.apk")
            if (f.exists()) f.delete()
            val r = Request.Builder().url(BASE + "IbnEIlyasHome-dev.apk?t=" + System.currentTimeMillis()).build()
            http.newCall(r).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val total = resp.body!!.contentLength()
                var done = 0L
                var last = -2
                resp.body!!.byteStream().use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(32768)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val p = if (total > 0) (done * 100 / total).toInt() else -1
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
            val head = f.inputStream().use { val b = ByteArray(2); it.read(b); b }
            if (f.length() > 1_000_000 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()) f else { f.delete(); null }
        } catch (e: Exception) {
            null
        }
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
        i.setDataAndType(uri, "application/vnd.android.package-archive")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
