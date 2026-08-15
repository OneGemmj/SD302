package com.seedream.app

import com.seedream.app.model.MODEL_SEEDREAM_4_5
import com.seedream.app.model.MODEL_SEEDREAM_5
import com.seedream.app.model.MODEL_SEEDREAM_5_PRO
import com.seedream.app.model.ReferenceImage
import com.seedream.app.model.ReferenceKind
import com.seedream.app.model.RequestInput
import com.seedream.app.model.buildSeedreamRequest
import com.seedream.app.model.capabilitiesFor
import com.seedream.app.model.isSeedream5Family
import com.seedream.app.model.isSeedream5Pro
import com.seedream.app.model.normalizeBoolean
import com.seedream.app.model.parseUrlReferenceImages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class PayloadBuilderTest {
    @Test
    fun buildsPayloadWithMaxImagesOptionsAndWebSearchForSeedream5() {
        val request = buildSeedreamRequest(
            baseInput(
                model = MODEL_SEEDREAM_5,
                references = listOf(
                    ReferenceImage(kind = ReferenceKind.Url, name = "u", value = "https://example.com/a.jpg")
                ),
                seed = "42",
                stream = "true",
                sequentialImageGeneration = "auto",
                maxImages = "3",
                outputFormat = "png",
                webSearch = "true"
            )
        )

        val json = request.toJsonObject()

        assertEquals(MODEL_SEEDREAM_5, json.getString("model"))
        assertEquals("测试 prompt", json.getString("prompt"))
        assertEquals("https://example.com/a.jpg", json.getJSONArray("image").getString(0))
        assertEquals(42L, json.getLong("seed"))
        assertTrue(json.getBoolean("stream"))
        assertEquals("auto", json.getString("sequential_image_generation"))
        assertFalse("top-level max_images is not in API schema", json.has("max_images"))
        assertEquals(3, json.getJSONObject("sequential_image_generation_options").getInt("max_images"))
        assertEquals("png", json.getString("output_format"))
        assertEquals("web_search", json.getJSONArray("tools").getJSONObject(0).getString("type"))
    }

    /**
     * Official: Pro calling method is identical to 5.0 — only replace `model`.
     * Payload shape (except model id) must match 5.0 for the same options.
     */
    @Test
    fun seedream5ProMatchesSeedream5PayloadExceptModelId() {
        val shared = baseInput(
            model = MODEL_SEEDREAM_5,
            references = listOf(
                ReferenceImage(kind = ReferenceKind.Url, name = "u", value = "https://example.com/a.jpg")
            ),
            seed = "7",
            stream = "false",
            sequentialImageGeneration = "auto",
            maxImages = "4",
            outputFormat = "jpeg",
            webSearch = "true"
        )

        val five = buildSeedreamRequest(shared).toJsonObject()
        val pro = buildSeedreamRequest(shared.copy(model = MODEL_SEEDREAM_5_PRO)).toJsonObject()

        assertEquals(MODEL_SEEDREAM_5, five.getString("model"))
        assertEquals(MODEL_SEEDREAM_5_PRO, pro.getString("model"))

        // Same keys and values after swapping model.
        val fiveWithoutModel = JSONObject(five.toString()).apply { remove("model") }
        val proWithoutModel = JSONObject(pro.toString()).apply { remove("model") }
        assertEquals(fiveWithoutModel.toString(), proWithoutModel.toString())

        val caps = capabilitiesFor(MODEL_SEEDREAM_5_PRO)
        assertTrue(isSeedream5Family(MODEL_SEEDREAM_5_PRO))
        assertTrue(isSeedream5Pro(MODEL_SEEDREAM_5_PRO))
        assertTrue(caps.supportsStream)
        assertTrue(caps.supportsOutputFormat)
        assertTrue(caps.supportsWebSearch)
        assertTrue(pro.has("stream"))
        assertFalse(pro.getBoolean("stream"))
        assertEquals("jpeg", pro.getString("output_format"))
        assertEquals("web_search", pro.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertEquals(4, pro.getJSONObject("sequential_image_generation_options").getInt("max_images"))
    }

    @Test
    fun maxImagesIsCoercedToApiRange() {
        val json = buildSeedreamRequest(
            baseInput(model = MODEL_SEEDREAM_5_PRO, maxImages = "20", sequentialImageGeneration = "auto")
        ).toJsonObject()
        assertEquals(15, json.getJSONObject("sequential_image_generation_options").getInt("max_images"))
        assertFalse(json.has("max_images"))
    }

    @Test
    fun doesNotAttachWebSearchToolForSeedream45() {
        val request = buildSeedreamRequest(
            baseInput(model = MODEL_SEEDREAM_4_5, webSearch = "true", stream = "false")
        )

        val json = request.toJsonObject()
        assertFalse(json.has("tools"))
        assertFalse(json.has("output_format"))
        assertFalse(isSeedream5Family(MODEL_SEEDREAM_4_5))
        assertTrue(capabilitiesFor(MODEL_SEEDREAM_4_5).supportsStream)
        assertTrue(json.has("stream"))
        assertFalse(json.getBoolean("stream"))
    }

    @Test
    fun parsesBooleansLikeTheHtmlVersion() {
        assertEquals(true, normalizeBoolean("true"))
        assertEquals(false, normalizeBoolean("false"))
        assertEquals(null, normalizeBoolean(""))
    }

    @Test
    fun parsesDistinctUrlReferences() {
        val refs = parseUrlReferenceImages(
            """
            https://example.com/a.jpg
            not-a-url
            https://example.com/a.jpg
            http://example.com/b.png
            """.trimIndent()
        )

        assertEquals(2, refs.size)
        assertEquals("https://example.com/a.jpg", refs[0].value)
        assertEquals("http://example.com/b.png", refs[1].value)
    }

    private fun baseInput(
        model: String = MODEL_SEEDREAM_5,
        references: List<ReferenceImage> = emptyList(),
        seed: String = "",
        stream: String = "false",
        sequentialImageGeneration: String = "disabled",
        maxImages: String = "",
        outputFormat: String = "jpeg",
        webSearch: String = "false",
        responseFormat: String = "url"
    ): RequestInput {
        return RequestInput(
            model = model,
            prompt = "测试 prompt",
            references = references,
            size = "2K",
            seed = seed,
            responseFormat = responseFormat,
            watermark = "false",
            stream = stream,
            sequentialImageGeneration = sequentialImageGeneration,
            maxImages = maxImages,
            outputFormat = outputFormat,
            webSearch = webSearch
        )
    }
}
