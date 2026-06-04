package com.example.carvideo.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
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
    private const val PREFS = "car_video_updater"
    private const val KEY_LAST_DOWNLOAD_ID = "last_download_id"
    private const val APK_FILE_NAME = "car-video-update.apk"
    private const val APK_MIME = "application/vnd.android.package-archive"

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
        // Remove previous APK so the installer never opens an old/corrupt file.
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME).delete()

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Car Video Private update")
            .setDescription(info.assetName)
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_DOWNLOAD_ID, id)
            .apply()
        return id
    }

    fun installDownloadedApk(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            throw IllegalStateException("Geef eerst toestemming voor installeren uit deze app en druk daarna opnieuw op installeren.")
        }

        val downloadedFile = getCompletedDownloadFile(context)
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            downloadedFile
        )

        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }

        try {
            context.startActivity(installIntent)
        } catch (e: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun getCompletedDownloadFile(context: Context): File {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_LAST_DOWNLOAD_ID, -1L)
        val fallbackFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

        if (id == -1L) {
            if (fallbackFile.exists() && fallbackFile.length() > 0) return fallbackFile
            throw IllegalStateException("Geen download gevonden. Download de update opnieuw.")
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) {
                if (fallbackFile.exists() && fallbackFile.length() > 0) return fallbackFile
                throw IllegalStateException("Download niet gevonden. Download opnieuw.")
            }

            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUriColumn = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = if (localUriColumn >= 0) it.getString(localUriColumn) else null
                    val file = localUri?.let { uriString ->
                        val uri = Uri.parse(uriString)
                        if (uri.scheme == "file") File(uri.path ?: "") else null
                    } ?: fallbackFile

                    if (file.exists() && file.length() > 0) return file
                    throw IllegalStateException("APK is gedownload maar bestand is niet leesbaar. Download opnieuw.")
                }
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> {
                    throw IllegalStateException("Download is nog bezig. Wacht tot de download klaar is.")
                }
                DownloadManager.STATUS_FAILED -> {
                    throw IllegalStateException("Download is mislukt. Download opnieuw.")
                }
                else -> {
                    throw IllegalStateException("Downloadstatus onbekend. Download opnieuw.")
                }
            }
        }
    }

    private fun extractVersionCodeFromAsset(assetName: String, tag: String): Int {
        Regex("code(\\d+)").find(assetName)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        Regex("code(\\d+)").find(tag)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        val parts = tag.removePrefix("v").split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        return when (parts.size) {
            0 -> 0
            1 -> parts[0]
            2 -> parts[0] * 100 + parts[1] * 10
            else -> parts[0] * 100 + parts[1] * 10 + parts[2]
        }
    }
}
