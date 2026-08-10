// Created by ruoyi.sjd on 2025/1/10.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
//
// Ported to RikkaLLM: trimmed to the file helpers required by the engine layer
// (media/uri/gallery helpers of the original UI-oriented class were removed).
package com.alibaba.mnnllm.android.utils

import android.annotation.SuppressLint
import android.util.Log
import java.io.File

object FileUtils {
    const val TAG: String = "FileUtils"

    fun ensureParentDirectoriesExist(file: File) {
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
    }

    @SuppressLint("DefaultLocale")
    fun formatFileSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L

        return when {
            size >= gb -> String.format("%.2f GB", size.toFloat() / gb)
            size >= mb -> String.format("%.2f MB", size.toFloat() / mb)
            size >= kb -> String.format("%.2f KB", size.toFloat() / kb)
            else -> "$size B"
        }
    }

    fun getFileSizeString(file: File?): String {
        val size = getFileSize(file)
        return formatFileSize(size)
    }

    fun getFileSize(file: File?): Long {
        if (file == null || !file.exists()) {
            return 0L
        }

        return try {
            if (file.isFile) {
                file.length()
            } else {
                var size = 0L
                file.listFiles()?.forEach { child ->
                    size += if (child.isFile) {
                        child.length()
                    } else {
                        getFileSize(child)
                    }

                }
                size
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to get size for ${file.path}: ${e.message}")
            0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get size for ${file.path}", e)
            0L
        }
    }
}
