package com.seedream.app.ui

import com.seedream.app.model.DEFAULT_ENDPOINT
import com.seedream.app.model.MODEL_SEEDREAM_5
import com.seedream.app.model.ReferenceImage
import com.seedream.app.model.ResultImage
import com.seedream.app.model.StatusKind
import com.seedream.app.storage.HistoryEntity

data class SeedreamUiState(
    val apiKey: String = "",
    val keyMasked: Boolean = false,
    val endpoint: String = DEFAULT_ENDPOINT,
    val model: String = MODEL_SEEDREAM_5,
    val prompt: String = "",
    val references: List<ReferenceImage> = emptyList(),
    val urlImagesText: String = "",
    val size: String = "2K",
    val seed: String = "",
    val responseFormat: String = "url",
    val watermark: String = "false",
    val stream: String = "false",
    val sequentialMode: String = "disabled",
    val maxImages: String = "",
    val outputFormat: String = "jpeg",
    val webSearch: String = "false",
    val externalSearch: String = "false",
    val searchProvider: String = "tavily",
    val searchApiKey: String = "",
    val searchSummary: String = "",
    val loggingEnabled: String = "false",
    val logContent: String = "",
    val logFileInfo: String = "",
    val resultImages: List<ResultImage> = emptyList(),
    val payloadPreview: String = "{}",
    val rawResponse: String = "(暂无)",
    val status: String = "就绪：可直接粘贴 Key、上传多图并发送 | 支持锁屏自动恢复",
    val statusKind: StatusKind = StatusKind.Ok,
    val netResult: String = "",
    val isGenerating: Boolean = false,
    val retryMessage: String? = null,
    val history: List<HistoryEntity> = emptyList(),
    val historyTotalCount: Int = 0,
    val historyLoadedLimit: Int = 100,
    val historyLoading: Boolean = false,
    val historyOpen: Boolean = false,
    val historySearch: String = "",
    val fullScreenImage: String? = null
)
