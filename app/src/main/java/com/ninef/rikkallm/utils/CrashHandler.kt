package com.ninef.rikkallm.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File

private const val TAG = "CrashHandler"
private const val PREFS_NAME = "crash_handler"
private const val KEY_CRASHED = "crashed"
private const val KEY_STACKTRACE_HEAD = "stacktrace_head"
private const val CRASH_LOG_FILE = "crash_log.txt"
// SharedPreferences 仅保留崩溃标记与头部摘要（受单值体积限制）
private const val MAX_HEAD_LENGTH = 8000

object CrashHandler {
    fun install(context: Context) {
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            markCrashed(appContext, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun hasCrashed(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CRASHED, false)
    }

    /**
     * 返回完整的崩溃栈（含所有 Caused by 链路）。优先读取磁盘上的完整日志文件，
     * 回退到 SharedPreferences 中的头部摘要。
     */
    fun getStackTrace(context: Context): String? {
        val file = File(context.filesDir, CRASH_LOG_FILE)
        if (file.exists()) {
            return runCatching { file.readText() }.getOrNull()
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STACKTRACE_HEAD, null)
    }

    /** 完整崩溃日志文件路径，便于将完整报告导出分析根因。 */
    fun getCrashLogFile(context: Context): File = File(context.filesDir, CRASH_LOG_FILE)

    fun clearCrashed(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_CRASHED).remove(KEY_STACKTRACE_HEAD) }
        File(context.filesDir, CRASH_LOG_FILE).delete()
    }

    private fun markCrashed(context: Context, thread: Thread, throwable: Throwable) {
        val fullTrace = buildString {
            appendLine("Thread: ${thread.name}")
            appendLine(throwable.stackTraceToString())
        }
        // 完整异常链写入文件（不截断），确保包含所有 Caused by，便于定位根因
        runCatching { File(context.filesDir, CRASH_LOG_FILE).writeText(fullTrace) }
            .onFailure { Log.e(TAG, "Failed to write full crash log to file", it) }
        // SharedPreferences 仅保留崩溃标记与头部摘要
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) {
                putBoolean(KEY_CRASHED, true)
                putString(KEY_STACKTRACE_HEAD, fullTrace.take(MAX_HEAD_LENGTH))
            } // commit() 同步写入，确保崩溃前写完
    }
}
