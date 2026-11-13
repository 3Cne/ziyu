package com.moyu.writer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * DeepSeek's own Search / Fetch endpoints. Both use the same bearer Key as the
 * chat API and are billed per successful request (not per token), which is why
 * the caller keeps a per-answer counter instead of letting the model loop.
 *
 * The endpoints live under `/v1/tools/…`; a writer who points the app at some
 * other OpenAI-compatible host gets a plain "this host has no search" message
 * rather than a puzzling 404.
 */
data class WebHit(
    val title: String,
    val url: String,
    val snippet: String,
    val summary: String,
    val site: String,
    val date: String,
)

data class WebSearchResult(
    val hits: List<WebHit> = emptyList(),
    val error: String? = null,
)

data class WebPage(
    val title: String = "",
    val content: String = "",
    val error: String? = null,
)

object AiWeb {
    private const val PATH_SEARCH = "/v1/tools/search"
    private const val PATH_FETCH = "/v1/tools/fetch"

    private fun supported(baseUrl: String): Boolean =
        baseUrl.contains("api.deepseek.com", ignoreCase = true)

    suspend fun search(
        baseUrl: String,
        key: String,
        query: String,
        count: Int = 5,
        oriQuestion: String = "",
    ): WebSearchResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext WebSearchResult(error = "还没填 Key")
        if (!supported(baseUrl)) {
            return@withContext WebSearchResult(error = "当前服务地址没有联网搜索，只有官方 api.deepseek.com 提供")
        }
        val q = query.trim()
        if (q.isBlank()) return@withContext WebSearchResult(error = "搜索词是空的")
        runCatching {
            val payload = JSONObject().apply {
                put("query", q.take(128))
                put("count", count.coerceIn(1, 8))
                put("need_content", false)
                if (oriQuestion.isNotBlank()) put("ori_question", oriQuestion.take(128))
            }
            val (code, body) = post(baseUrl, key, PATH_SEARCH, payload, 30_000)
            if (code !in 200..299) {
                return@runCatching WebSearchResult(error = AiClient.explain(code, body))
            }
            val arr = JSONObject(body).optJSONArray("results") ?: return@runCatching WebSearchResult()
            val hits = ArrayList<WebHit>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                hits += WebHit(
                    title = o.optString("title").trim(),
                    url = o.optString("url").trim(),
                    snippet = o.optString("snippet").trim(),
                    summary = o.optString("summary").trim(),
                    site = o.optString("site_name").trim(),
                    date = o.optString("date").trim(),
                )
            }
            WebSearchResult(hits = hits)
        }.getOrElse { e -> WebSearchResult(error = AiClient.networkError(e)) }
    }

    suspend fun fetch(
        baseUrl: String,
        key: String,
        url: String,
    ): WebPage = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext WebPage(error = "还没填 Key")
        if (!supported(baseUrl)) {
            return@withContext WebPage(error = "当前服务地址没有网页抓取，只有官方 api.deepseek.com 提供")
        }
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return@withContext WebPage(error = "只抓 http / https 地址")
        }
        runCatching {
            val (code, body) = post(baseUrl, key, PATH_FETCH, JSONObject().put("url", target), 40_000)
            if (code !in 200..299) {
                val note = if (code == 403) "这一页被站点安全策略拒绝" else AiClient.explain(code, body)
                return@runCatching WebPage(error = note)
            }
            val o = JSONObject(body)
            val text = o.optString("content").trim()
            if (text.isBlank()) return@runCatching WebPage(error = "这一页没有可读正文")
            WebPage(title = o.optString("title").trim(), content = text)
        }.getOrElse { e -> WebPage(error = AiClient.networkError(e)) }
    }

    /** POST one JSON object and hand back the status plus the raw body. */
    private fun post(
        baseUrl: String,
        key: String,
        path: String,
        payload: JSONObject,
        readTimeout: Int,
    ): Pair<Int, String> {
        val endpoint = baseUrl.trim().trimEnd('/') + path
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            this.readTimeout = readTimeout
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}
