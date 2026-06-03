package com.example.carvideo.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.carvideo.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val CRASH_FILE = "last_crash.txt"
    private const val EVENT_FILE = "app_events.txt"
    private const val MAX_EVENT_BYTES = 80_000

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        val appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the logger cause a second crash.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        logEvent(appContext, "App gestart: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    fun logEvent(context: Context, message: String) {
        try {
            val file = File(context.filesDir, EVENT_FILE)
            val line = "${timestamp()} - $message\n"
            file.appendText(line)
            if (file.length() > MAX_EVENT_BYTES) {
                val text = file.readText()
                file.writeText(text.takeLast(MAX_EVENT_BYTES / 2))
            }
        } catch (_: Throwable) {
            // Ignore logging problems.
        }
    }

    fun readCrashLog(context: Context): String {
        val file = File(context.filesDir, CRASH_FILE)
        return if (file.exists()) file.readText() else "Geen crash-log gevonden."
    }

    fun readEventLog(context: Context): String {
        val file = File(context.filesDir, EVENT_FILE)
        return if (file.exists()) file.readText() else "Geen event-log gevonden."
    }

    fun readFullLog(context: Context): String {
        return buildString {
            appendLine("===== LAATSTE CRASH =====")
            appendLine(readCrashLog(context))
            appendLine()
            appendLine("===== APP EVENTS =====")
            appendLine(readEventLog(context))
        }
    }

    fun clearLogs(context: Context) {
        File(context.filesDir, CRASH_FILE).delete()
        File(context.filesDir, EVENT_FILE).delete()
        logEvent(context, "Logs gewist")
    }

    fun copyLogsToClipboard(context: Context): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("CarVideo crash log", readFullLog(context)))
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val log = buildString {
            appendLine("Time: ${timestamp()}")
            appendLine("Thread: ${thread.name}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Package: ${BuildConfig.APPLICATION_ID}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine()
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message ?: "geen message"}")
            appendLine()
            appendLine(sw.toString())
        }

        File(context.filesDir, CRASH_FILE).writeText(log)
        logEvent(context, "CRASH: ${throwable.javaClass.simpleName}: ${throwable.message ?: "geen message"}")
    }

    private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
