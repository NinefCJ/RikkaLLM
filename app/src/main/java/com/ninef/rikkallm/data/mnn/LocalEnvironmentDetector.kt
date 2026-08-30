package com.ninef.rikkallm.data.mnn

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import com.alibaba.mnnllm.android.server.LocalMnnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ninef.rikkallm.data.huggingface.*
import java.io.File

/** 本地环境兼容性等级 */
enum class CompatibilityLevel {
    COMPATIBLE,       // 满足运行要求
    NEEDS_ATTENTION,  // 可运行但存在风险（如内存吃紧）
    INCOMPATIBLE,     // 无法满足运行要求
}

/** 本地环境检测报告 */
data class EnvironmentReport(
    val deviceRamMb: Int,
    val availableStorageMb: Int,
    val abi: List<String>,
    val supportsCpu: Boolean = true,
    val supportsVulkan: Boolean = false,
    val supportsNnapi: Boolean = false,
    val mnnAvailable: Boolean = true,
    val estimatedModelSizeMb: Long,
    val minRamMb: Int,
    val compatibility: CompatibilityLevel,
    val issues: List<String>,
    val suggestions: List<String>,
)

/**
 * 本地运行环境检测：在加载模型前扫描设备能力，给出兼容性评估与缺失依赖 / 配置建议。
 *
 * 说明：本项目以 Android 端 MNN 引擎作为本地推理后端，因此"本地环境"检测的是设备
 * 硬件能力（内存、存储、CPU 架构、Vulkan/NNAPI 后端、MNN 引擎可用性），而非桌面
 * 端的 Python / CUDA 环境。
 */
class LocalEnvironmentDetector(
    private val context: Context,
    private val localMnnManager: LocalMnnManager,
) {
    suspend fun analyze(model: HfModel): EnvironmentReport = withContext(Dispatchers.Default) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ramMb = (mem.totalMem / 1048576).toInt()

        val rootFile = runCatching { localMnnManager.modelsRoot() }.getOrNull()
        val availMb = if (rootFile != null && rootFile.exists()) {
            runCatching {
                val stat = StatFs(rootFile.absolutePath)
                (stat.availableBlocksLong * stat.blockSizeLong / 1048576).toInt()
            }.getOrDefault(0)
        } else {
            runCatching {
                val stat = StatFs(context.filesDir.absolutePath)
                (stat.availableBlocksLong * stat.blockSizeLong / 1048576).toInt()
            }.getOrDefault(0)
        }

        val abi = Build.SUPPORTED_ABIS.toList()
        val isArm64 = abi.any { it.equals("arm64-v8a", true) }
        val vulkan = runCatching {
            context.packageManager.hasSystemFeature("android.hardware.vulkan")
        }.getOrDefault(false)
        val nnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        val modelSize = model.estimatedDownloadMb()
        val minRam = model.estimateMinRamMb()

        val issues = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        if (model.isMnnFormat && !isArm64) {
            issues += "当前设备 CPU 架构 (${abi.firstOrNull() ?: "未知"}) 不支持 MNN 预编译引擎（仅 arm64-v8a）"
        }
        if (ramMb < minRam) {
            issues += "设备内存 $ramMb MB 低于模型建议最低内存 $minRam MB"
            suggestions += "建议在内存更大的设备运行，或选择更小的量化版本"
        }
        if (modelSize > 0 && availMb > 0 && availMb < modelSize) {
            issues += "可用存储 $availMb MB 不足以容纳模型约 $modelSize MB"
            suggestions += "清理存储空间，或将模型目录移动到容量更大的分区后再下载"
        }

        val compatibility = when {
            (model.isMnnFormat && !isArm64) ||
                (modelSize > 0 && availMb > 0 && availMb < modelSize) ||
                ramMb < minRam / 2 ->
                CompatibilityLevel.INCOMPATIBLE

            issues.isNotEmpty() -> CompatibilityLevel.NEEDS_ATTENTION
            else -> CompatibilityLevel.COMPATIBLE
        }

        if (model.isMnnFormat) {
            suggestions += "MNN 引擎支持纯 CPU 离线推理；Vulkan 后端可加速（当前${if (vulkan) "可用" else "不可用"}），NNAPI 后端（${if (nnapi) "可用" else "不可用"}）"
        } else {
            suggestions += "该模型非 MNN 格式，需在桌面端运行兼容 OpenAI 协议的本地服务（Ollama / LM Studio / llama.cpp）后，通过「Local LLM Server」供应商接入"
        }

        EnvironmentReport(
            deviceRamMb = ramMb,
            availableStorageMb = availMb,
            abi = abi,
            supportsVulkan = vulkan,
            supportsNnapi = nnapi,
            estimatedModelSizeMb = modelSize,
            minRamMb = minRam,
            compatibility = compatibility,
            issues = issues,
            suggestions = suggestions,
        )
    }
}
