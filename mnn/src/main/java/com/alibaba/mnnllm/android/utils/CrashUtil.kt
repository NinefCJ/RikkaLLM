// Created by ruoyi.sjd on 2025/5/9.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM: trimmed version - crash log capture kept, the UI-facing
// shareLatestCrash helper (FileProvider/clipboard/toast) was removed.

package com.alibaba.mnnllm.android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@SuppressLint("StaticFieldLeak")
object CrashUtil : Thread.UncaughtExceptionHandler {
    private const val TAG = "CrashUtil"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var context: Context
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private lateinit var crashDir: File

    init {
        try {
            System.loadLibrary("mnnllmapp")
        } catch (e: Throwable) {
            Log.w(TAG, "Native crash library load failed", e)
        }
    }

    private external fun initNative(crashDir: String)

    fun init(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        crashDir = File(context.filesDir, "crash").apply { if (!exists()) mkdirs() }
        //seems not work fine for native crash
//        initNative(crashDir.absolutePath)
    }

    @JvmStatic
    fun onNativeCrash() {
        val ts = dateFormat.format(Date())
        val file = File(crashDir, "native_$ts.log")
        FileWriter(file).use { fw ->
            fw.appendLine("===== Native Crash =====")
            fw.appendLine()
            fw.appendLine("===== Logcat (main) =====")
            fw.append(getLogcat("-d", "-v", "time"))
            fw.appendLine()
            fw.appendLine("===== Logcat (crash buffer) =====")
            fw.append(getLogcat("-d", "-b", "crash", "-v", "time"))
        }
        Log.i(TAG, "Saved native crash log to ${file.absolutePath}")
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        saveJavaCrash(thread, ex)
        defaultHandler?.uncaughtException(thread, ex) ?: run {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun saveJavaCrash(thread: Thread, ex: Throwable) {
        val ts = dateFormat.format(Date())
        val file = File(crashDir, "crash_$ts.log")
        FileWriter(file).use { fw ->
            fw.appendLine("===== Java Crash =====")
            fw.appendLine("Thread: ${thread.name}")
            fw.appendLine(Log.getStackTraceString(ex))
            fw.appendLine("\n===== Logcat (main) =====")
            fw.append(getLogcat("-d", "-v", "time"))
            fw.appendLine("\n===== Logcat (crash buffer) =====")
            fw.append(getLogcat("-d", "-b", "crash", "-v", "time"))
        }
        Log.i(TAG, "Saved crash log to ${file.absolutePath}")
    }

    private fun getLogcat(vararg args: String): String {
        val sb = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", *args))
            BufferedReader(InputStreamReader(process.inputStream)).use { br ->
                br.lineSequence().forEach { sb.appendLine(it) }
            }
        } catch (ioe: IOException) {
            sb.appendLine("Failed to collect logcat: \${ioe.message}")
        }
        return sb.toString()
    }

    fun hasCrash(): Boolean {
        return crashDir.listFiles()?.isNotEmpty() ?: false
    }
}
