package com.ninef.rikkallm.data.huggingface

/**
 * 模型市场可选的模型源。
 *
 * - [AUTO]：根据网络环境自动推荐访问速度更快的源（见 [ModelSourceManager]）。
 * - [HUGGINGFACE]：Hugging Face Hub，全球最大开源模型社区，模型最全，但国内访问可能较慢或不稳定。
 * - [MODELSCOPE]：魔搭社区（ModelScope），国内访问速度快、稳定，覆盖主流开源模型。
 */
enum class ModelMarketSource(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    AUTO("自动推荐", "自动", "根据你的网络环境，自动选择访问速度更快的模型源"),
    HUGGINGFACE("Hugging Face", "HF", "全球最大的开源模型社区，模型资源最丰富；国内访问可能较慢或不稳定"),
    MODELSCOPE("魔搭社区", "魔搭", "阿里达摩院推出的国内模型社区，国内访问速度快、稳定，覆盖主流开源模型"),
    ;

    /** 是否为"自动推荐"模式 */
    val isAuto: Boolean get() = this == AUTO
}
