package com.ninef.rikkallm.data.huggingface

/**
 * 模型市场后端抽象。Hugging Face 与魔搭社区均实现该接口，
 * 使上层（VM / UI）无需关心具体源，仅通过 [ModelMarketSource] 切换。
 */
interface ModelMarketApi {
    /** 该实现对应的模型源 */
    val source: ModelMarketSource

    /** 按查询条件拉取模型列表（已映射为统一的 [HfModel]） */
    suspend fun getModels(query: HfQuery): List<HfModel>

    /** 拉取单个模型详情（已映射为统一的 [HfModel]） */
    suspend fun getModel(id: String): HfModel?

    /** 拉取模型 README 文本 */
    suspend fun getReadme(id: String): String?
}
