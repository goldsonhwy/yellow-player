package com.goldsonhwy.yellowplayer.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object CrashReporter {
    private const val CRASH_FILE = "last_crash.txt"
    private const val DEBUG_FILE = "debug_info.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never make a crash worse.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun writeDebugSnapshot(context: Context, note: String = "manual export") {
        val file = File(context.filesDir, DEBUG_FILE)
        file.writeText(buildDebugText(context, note))
    }

    fun createDebugZip(context: Context): File {
        writeDebugSnapshot(context, "export button tapped")
        val out = File(context.cacheDir, "yellow-player-debug-${timestampForFile()}.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            addText(zip, "debug_info.txt", File(context.filesDir, DEBUG_FILE).readText())
            val crash = File(context.filesDir, CRASH_FILE)
            if (crash.exists()) {
                addText(zip, "last_crash.txt", crash.readText())
            } else {
                addText(zip, "last_crash.txt", "No crash log recorded yet.\n")
            }
        }
        return out
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine("Yellow Player Crash Report")
            appendLine("Time: ${timestampHuman()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(buildDebugText(context, "uncaught exception"))
            appendLine()
            appendLine("--- Stacktrace ---")
            appendLine(sw.toString())
        }
        File(context.filesDir, CRASH_FILE).writeText(text)
    }

    private fun buildDebugText(context: Context, note: String): String {
        val allFilesAccess = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { Environment.isExternalStorageManager() }.getOrDefault(false).toString()
        } else "pre-android-11"
        val ext = runCatching { Environment.getExternalStorageDirectory().absolutePath }.getOrDefault("unknown")
        val extReadable = runCatching { Environment.getExternalStorageDirectory().canRead() }.getOrDefault(false)
        return buildString {
            appendLine("Yellow Player Debug Info")
            appendLine("Time: ${timestampHuman()}")
            appendLine("Note: $note")
            appendLine("Package: ${context.packageName}")
            appendLine("App version: 1.0.6")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("All files access: $allFilesAccess")
            appendLine("External storage path: $ext")
            appendLine("External storage readable: $extReadable")
            appendLine("Files dir: ${context.filesDir.absolutePath}")
            appendLine("Cache dir: ${context.cacheDir.absolutePath}")
        }
    }

    private fun addText(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun timestampHuman(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun timestampForFile(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
