package com.fdzaki.adshield.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Built-in crash logger, installed once from [com.fdzaki.adshield.AdShieldApp.onCreate].
 *
 * FAIL-SAFE CONTRACT (do not weaken this — see PROJECT_STATE.md): every
 * single operation in this file is wrapped so that a failure while logging
 * a crash can NEVER itself throw and mask/replace the original crash, and
 * can NEVER prevent the previously-installed handler (Android's own
 * default, which shows the system crash dialog and kills the process) from
 * running afterward.
 *
 * Storage strategy is deliberately split by API level:
 *  - API 29+ (Q+): write via MediaStore into the PUBLIC
 *    Documents/AdShield/logs/ folder. This needs no storage permission at
 *    all under scoped storage for files this app creates itself.
 *  - Below API 29: MediaStore's Documents collection isn't available, and
 *    writing to public storage there would require the legacy
 *    WRITE_EXTERNAL_STORAGE permission — which the standing project rule
 *    explicitly forbids adding just for logging. So on old Android we fall
 *    back to app-private external storage
 *    (Android/data/com.fdzaki.adshield/files/AdShield/logs/), which needs
 *    no permission on any API level. This is a deliberate, documented
 *    trade-off (see PROJECT_STATE.md Assumption Log): pre-API 29 users get
 *    a slightly less discoverable log location, not a missing feature.
 */
object CrashLogger {

    private const val APP_FOLDER = "AdShield"
    private const val LOG_SUBDIR = "logs"
    private const val MAX_RETAINED_LOGS = 50
    private const val FILE_PREFIX = "crash_"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Absolute fail-safe: a problem while logging must never
                // block or replace normal crash handling below.
            } finally {
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    // Extremely unlikely (Android always installs its own
                    // default handler before Application.onCreate runs),
                    // but fall back to a plain process kill so behavior
                    // without us installed anything is preserved.
                    Runtime.getRuntime().exit(10)
                }
            }
        }
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uniqueId = UUID.randomUUID().toString().take(8)
        val fileName = "$FILE_PREFIX${timestamp}_$uniqueId.txt"
        val content = buildLogContent(context, thread, throwable)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, fileName, content)
            pruneOldLogsMediaStore(context)
        } else {
            writeViaAppPrivateExternal(context, fileName, content)
            pruneOldLogsPrivateExternal(context)
        }
    }

    private fun buildLogContent(context: Context, thread: Thread, throwable: Throwable): String {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "unknown"
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: -1L

        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))

        return buildString {
            appendLine("=== AdShield Crash Report ===")
            appendLine("App Version: $versionName (versionCode $versionCode)")
            appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Thread: ${thread.name} (id=${thread.id})")
            appendLine()
            appendLine("--- Uncaught Exception Stack Trace ---")
            append(stackTraceWriter.toString())
        }
    }

    // ---- API 29+: public Documents/AdShield/logs/ via MediaStore ----

    private fun writeViaMediaStore(context: Context, fileName: String, content: String) {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/$LOG_SUBDIR"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val collection = MediaStore.Files.getContentUri("external")
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
    }

    private fun pruneOldLogsMediaStore(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$APP_FOLDER/$LOG_SUBDIR/"
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        // Selection is scoped to BOTH our exact folder AND our own crash-log
        // naming prefix, so this can only ever touch files this logger
        // itself created — never another app's files or the user's own
        // diagnostic logs living elsewhere.
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(relativePath, "$FILE_PREFIX%")

        val entries = mutableListOf<Pair<Long, String>>()
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                entries.add(cursor.getLong(idCol) to cursor.getString(nameCol))
            }
        }

        if (entries.size <= MAX_RETAINED_LOGS) return
        // Filenames embed yyyyMMdd_HHmmss right after the fixed prefix, so
        // lexicographic sort on the name is also chronological sort.
        val toDelete = entries.sortedBy { it.second }.take(entries.size - MAX_RETAINED_LOGS)
        for ((id, _) in toDelete) {
            runCatching {
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
        }
    }

    // ---- Below API 29: app-private external storage fallback ----

    private fun writeViaAppPrivateExternal(context: Context, fileName: String, content: String) {
        val dir = File(context.getExternalFilesDir(null), "$APP_FOLDER/$LOG_SUBDIR")
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).writeText(content, Charsets.UTF_8)
    }

    private fun pruneOldLogsPrivateExternal(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "$APP_FOLDER/$LOG_SUBDIR")
        val files = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) } ?: return
        if (files.size <= MAX_RETAINED_LOGS) return
        files.sortedBy { it.name }
            .take(files.size - MAX_RETAINED_LOGS)
            .forEach { runCatching { it.delete() } }
    }
}
