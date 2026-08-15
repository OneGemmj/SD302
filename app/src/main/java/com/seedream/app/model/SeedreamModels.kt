package com.seedream.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val DEFAULT_ENDPOINT = "https://api.302.ai/doubao/images/generations"
const val MODEL_SEEDREAM_5_PRO = "doubao-seedream-5-0-pro-260628"
const val MODEL_SEEDREAM_5 = "doubao-seedream-5-0-260128"
const val MODEL_SEEDREAM_4_5 = "doubao-seedream-4-5-251128"

/**
 * Per-model request field support.
 *
 * Official guidance for Seedream 5.0 Pro
 * (`doubao-seedream-5-0-pro-260628`): calling method is the same as 5.0 —
 * only replace the `model` field. So Pro shares the full 5.x capability set
 * (stream, tools.web_search, output_format, sequential options, etc.).
 */
data class SeedreamModelCapabilities(
    val supportsStream: Boolean,
    val supportsOutputFormat: Boolean,
    val supportsWebSearch: Boolean
)

/** Seedream 5.x family (5.0 / 5.0 Pro). */
fun isSeedream5Family(model: String): Boolean = model.startsWith("doubao-seedream-5-")

fun isSeedream5Pro(model: String): Boolean =
    model == MODEL_SEEDREAM_5_PRO || model.contains("seedream-5-0-pro", ignoreCase = true)

fun capabilitiesFor(model: String): SeedreamModelCapabilities = when {
    isSeedream5Family(model) -> SeedreamModelCapabilities(
        supportsStream = true,
        supportsOutputFormat = true,
        supportsWebSearch = true
    )
    else -> SeedreamModelCapabilities(
        // Seedream 4.5 and other non-5.x models
        supportsStream = true,
        supportsOutputFormat = false,
        supportsWebSearch = false
    )
}

fun supportsStream(model: String): Boolean = capabilitiesFor(model).supportsStream
fun supportsOutputFormat(model: String): Boolean = capabilitiesFor(model).supportsOutputFormat
fun supportsWebSearch(model: String): Boolean = capabilitiesFor(model).supportsWebSearch

enum class ReferenceKind {
    File,
    Url
}

enum class StatusKind {
    Normal,
    Ok,
    Error,
    Muted,
    Warn
}

data class ReferenceImage(
    val id: String = UUID.randomUUID().toString(),
    val kind: ReferenceKind,
    val name: String,
    val value: String,
    val preview: String = value
)

data class ResultImage(
    val id: String = UUID.randomUUID().toString(),
    val src: String,
    val note: String = ""
)

data class SeedreamRequest(
    val model: String,
    val prompt: String,
    val image: List<String> = emptyList(),
    val size: String? = null,
    val seed: Long? = null,
    val responseFormat: String? = "url",
    val watermark: Boolean? = false,
    val stream: Boolean? = false,
    val sequentialImageGeneration: String? = "disabled",
    val maxImages: Int? = null,
    val outputFormat: String? = "jpeg",
    val webSearch: Boolean = false
) {
    fun toJsonObject(): JSONObject {
        val caps = capabilitiesFor(model)
        val json = JSONObject()
            .put("model", model)
            .put("prompt", prompt)

        // OpenAPI: image is string[]; single-item arrays are valid for 图文生图.
        if (image.isNotEmpty()) {
            json.put("image", JSONArray().also { array -> image.forEach(array::put) })
        }
        // size enum in docs: 2K | 4K (description text "2K, 3K" is a doc typo)
        if (!size.isNullOrBlank()) json.put("size", size)
        if (seed != null) json.put("seed", seed)
        if (!responseFormat.isNullOrBlank()) json.put("response_format", responseFormat)
        if (watermark != null) json.put("watermark", watermark)

        // Only emit stream when the model actually accepts it.
        if (caps.supportsStream && stream != null) {
            json.put("stream", stream)
        }

        if (!sequentialImageGeneration.isNullOrBlank()) {
            json.put("sequential_image_generation", sequentialImageGeneration)
        }
        // Docs: max_images lives under sequential_image_generation_options only (range 1-15).
        if (maxImages != null) {
            json.put(
                "sequential_image_generation_options",
                JSONObject().put("max_images", maxImages.coerceIn(1, 15))
            )
        }

        if (caps.supportsOutputFormat && !outputFormat.isNullOrBlank()) {
            json.put("output_format", outputFormat)
        }
        if (webSearch && caps.supportsWebSearch) {
            json.put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        }
        return json
    }

    fun toJsonString(indentSpaces: Int = 0): String {
        val json = toJsonObject()
        return if (indentSpaces > 0) json.toString(indentSpaces) else json.toString()
    }

    fun toPreviewJsonString(indentSpaces: Int = 2): String {
        val json = toJsonObject()
        if (json.has("image")) {
            val previewImages = JSONArray()
            image.forEach { value -> previewImages.put(value.compactForPreview()) }
            json.put("image", previewImages)
        }
        return json.toString(indentSpaces)
    }

    private fun String.compactForPreview(): String {
        if (startsWith("data:image", ignoreCase = true)) {
            val header = substringBefore(",", missingDelimiterValue = "data:image/*;base64")
            val base64Length = substringAfter(",", missingDelimiterValue = "").length
            return "$header,... <base64 $base64Length chars>"
        }
        return if (length > 300) take(300) + "... <truncated ${length - 300} chars>" else this
    }
}

data class RequestInput(
    val model: String,
    val prompt: String,
    val references: List<ReferenceImage>,
    val size: String,
    val seed: String,
    val responseFormat: String,
    val watermark: String,
    val stream: String,
    val sequentialImageGeneration: String,
    val maxImages: String,
    val outputFormat: String,
    val webSearch: String
)

fun buildSeedreamRequest(input: RequestInput): SeedreamRequest {
    val seed = input.seed.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
    val maxImages = input.maxImages.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    return SeedreamRequest(
        model = input.model,
        prompt = input.prompt.trim(),
        image = input.references.map { it.value },
        size = input.size.takeIf { it.isNotBlank() },
        seed = seed,
        responseFormat = input.responseFormat.takeIf { it.isNotBlank() },
        watermark = normalizeBoolean(input.watermark),
        stream = normalizeBoolean(input.stream),
        sequentialImageGeneration = input.sequentialImageGeneration.takeIf { it.isNotBlank() },
        maxImages = maxImages,
        outputFormat = input.outputFormat.takeIf { it.isNotBlank() },
        webSearch = input.webSearch == "true"
    )
}

fun normalizeBoolean(value: String): Boolean? = when (value) {
    "true" -> true
    "false" -> false
    else -> null
}

fun parseUrlReferenceImages(text: String): List<ReferenceImage> {
    return text
        .lines()
        .map { it.trim() }
        .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        .distinct()
        .mapIndexed { index, url ->
            ReferenceImage(
                kind = ReferenceKind.Url,
                name = "URL ${index + 1}",
                value = url,
                preview = url
            )
        }
}
