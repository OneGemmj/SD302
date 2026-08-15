package com.seedream.app.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SearchProvider(val id: String, val label: String, val requiresApiKey: Boolean) {
    Tavily("tavily", "Tavily", true),
    Brave("brave", "Brave Search", true),
    Bing("bing", "Bing Web Search", true),
    DuckDuckGo("duckduckgo", "DuckDuckGo", false);

    companion object {
        fun fromId(id: String): SearchProvider {
            return entries.firstOrNull { it.id == id } ?: Tavily
        }
    }
}

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class SearchClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun search(provider: SearchProvider, query: String, apiKey: String, maxResults: Int = 5): List<SearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()
        if (provider.requiresApiKey && apiKey.isBlank()) {
            throw IOException("${provider.label} API Key is empty")
        }
        return when (provider) {
            SearchProvider.Tavily -> searchTavily(trimmedQuery, apiKey, maxResults)
            SearchProvider.Brave -> searchBrave(trimmedQuery, apiKey, maxResults)
            SearchProvider.Bing -> searchBing(trimmedQuery, apiKey, maxResults)
            SearchProvider.DuckDuckGo -> searchDuckDuckGo(trimmedQuery, maxResults)
        }
    }

    private fun searchTavily(query: String, apiKey: String, maxResults: Int): List<SearchResult> {
        val body = JSONObject()
            .put("query", query)
            .put("search_depth", "basic")
            .put("include_answer", true)
            .put("max_results", maxResults)
            .toString()
            .toRequestBody(JSON)

        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return executeJson(request) { json ->
            val items = mutableListOf<SearchResult>()
            val answer = json.optString("answer").trim()
            if (answer.isNotBlank()) {
                items += SearchResult("Tavily answer", "", answer)
            }
            json.optJSONArray("results")
                ?.takeObjects(maxResults)
                ?.mapNotNullTo(items) { item ->
                    SearchResult(
                        title = item.optString("title").ifBlank { item.optString("url") },
                        url = item.optString("url"),
                        snippet = item.optString("content")
                    ).takeIf { it.title.isNotBlank() || it.snippet.isNotBlank() }
                }
            items.take(maxResults)
        }
    }

    private fun searchBrave(query: String, apiKey: String, maxResults: Int): List<SearchResult> {
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", maxResults.coerceIn(1, 10).toString())
            .addQueryParameter("extra_snippets", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Subscription-Token", apiKey)
            .header("User-Agent", "SD302Android/2.0.0")
            .get()
            .build()

        return executeJson(request) { json ->
            json.optJSONObject("web")
                ?.optJSONArray("results")
                ?.takeObjects(maxResults)
                ?.mapNotNull { item ->
                    val extra = item.optJSONArray("extra_snippets")?.joinStrings(" ").orEmpty()
                    SearchResult(
                        title = item.optString("title"),
                        url = item.optString("url"),
                        snippet = listOf(item.optString("description"), extra).filter { it.isNotBlank() }.joinToString(" ")
                    ).takeIf { it.title.isNotBlank() || it.snippet.isNotBlank() }
                }
                .orEmpty()
        }
    }

    private fun searchBing(query: String, apiKey: String, maxResults: Int): List<SearchResult> {
        val url = "https://api.bing.microsoft.com/v7.0/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", maxResults.coerceIn(1, 10).toString())
            .addQueryParameter("responseFilter", "Webpages")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Ocp-Apim-Subscription-Key", apiKey)
            .get()
            .build()

        return executeJson(request) { json ->
            json.optJSONObject("webPages")
                ?.optJSONArray("value")
                ?.takeObjects(maxResults)
                ?.mapNotNull { item ->
                    SearchResult(
                        title = item.optString("name"),
                        url = item.optString("url"),
                        snippet = item.optString("snippet")
                    ).takeIf { it.title.isNotBlank() || it.snippet.isNotBlank() }
                }
                .orEmpty()
        }
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): List<SearchResult> {
        val url = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .addQueryParameter("skip_disambig", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        return executeJson(request) { json ->
            val items = mutableListOf<SearchResult>()
            val abstractText = json.optString("AbstractText").trim()
            if (abstractText.isNotBlank()) {
                items += SearchResult(
                    title = json.optString("Heading").ifBlank { "DuckDuckGo answer" },
                    url = json.optString("AbstractURL"),
                    snippet = abstractText
                )
            }
            collectDuckDuckGoTopics(json.optJSONArray("RelatedTopics"), items, maxResults)
            items.take(maxResults)
        }
    }

    private fun executeJson(request: Request, parser: (JSONObject) -> List<SearchResult>): List<SearchResult> {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Search failed: HTTP ${response.code} ${text.take(300)}")
            }
            return parser(JSONObject(text))
        }
    }

    private fun collectDuckDuckGoTopics(array: JSONArray?, out: MutableList<SearchResult>, maxResults: Int) {
        if (array == null || out.size >= maxResults) return
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val nested = item.optJSONArray("Topics")
            if (nested != null) {
                collectDuckDuckGoTopics(nested, out, maxResults)
            } else {
                val text = item.optString("Text")
                if (text.isNotBlank()) {
                    out += SearchResult(
                        title = text.substringBefore(" - ").take(120),
                        url = item.optString("FirstURL"),
                        snippet = text
                    )
                }
            }
            if (out.size >= maxResults) return
        }
    }

    private fun JSONArray.takeObjects(limit: Int): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        for (index in 0 until length().coerceAtMost(limit)) {
            optJSONObject(index)?.let(items::add)
        }
        return items
    }

    private fun JSONArray.joinStrings(separator: String): String {
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(items::add)
        }
        return items.joinToString(separator)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
