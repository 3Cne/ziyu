package com.moyu.writer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tag: String,
    val url: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
)

object UpdateChecker {
    suspend fun latest(repo: String): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank()) return@withContext null
        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ziyu-android")
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val tag = o.optString("tag_name").trim()
            val url = o.optString("html_url").trim()
            val notes = o.optString("body").trim()
            var apkUrl = ""
            var apkName = ""
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val download = asset.optString("browser_download_url")
                    if (download.isNotBlank()) {
                        apkName = name
                        apkUrl = download
                        break
                    }
                }
            }
            if (tag.isBlank() || url.isBlank()) null
            else UpdateInfo(tag, url, notes, apkUrl, apkName)
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
