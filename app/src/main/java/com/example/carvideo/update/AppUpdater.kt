package com.example.carvideo.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.carvideo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val releaseName: String,
    val assetName: String,
    val apkUrl: String,
    val versionCode: Int,
    val isUpdateAvailable: Boolean
)

object AppUpdater {
    private const val OWNER = "cma58"
    private const val REPO = "CarVideoApp"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CarVideoPrivate/${BuildConfig.VERSION_NAME}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("GitHub update check failed: HTTP ${response.code}")
            }

            val body = response.body.string()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val name = json.optString("name", tag)
            val assets = json.getJSONArray("assets")

            var assetName = ""
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val candidateName = asset.optString("name", "")
                if (candidateName.endsWith(".apk", ignoreCase = true)) {
                    assetName = candidateName
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }

            if (apkUrl.isBlank()) throw IllegalStateException("Geen APK gevonden in laatste GitHub release")

            val remoteVersionCode = extractVersionCodeFromAsset(assetName, tag)
            UpdateInfo(
                tagName = tag,
                releaseName = name,
                assetName = assetName,
                apkUrl = apkUrl,
                versionCode = remoteVersionCode,
                isUpdateAvailable = remoteVersionCode > BuildConfig.VERSION_CODE
            )
        }
    }

    fun downloadWithDownloadManager(context: Context, info: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Car Video Private update")
            .setDescription(info.assetName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "car-video-update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun installDownloadedApk(context: Context) {
        val apk = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "car-video-update.apk")
        if (!apk.exists()) throw IllegalStateException("APK nog niet gevonden. Wacht tot download klaar is.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException("Geen installer gevonden op dit toestel", e)
        }
    }

    private fun extractVersionCodeFromAsset(assetName: String, tag: String): Int {
        // Preferred filename pattern from GitHub Actions: CarVideoApp-private-v1.2.34-code123.apk
        Regex("code(\\d+)").find(assetName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("code(\\d+)").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        // Fallback: convert v0.2.5 to 25, v1.2.3 to 123. Good enough for older releases.
        val parts = tag.removePrefix("v").split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        return when (parts.size) {
            0 -> 0
            1 -> parts[0]
            2 -> parts[0] * 100 + parts[1] * 10
            else -> parts[0] * 100 + parts[1] * 10 + parts[2]
        }
    }
}
