package com.moyu.writer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Defaults for the one provider the writer is using. The model name is not a
 *  guess: DeepSeek's own 400 response lists the names it accepts
 *  ("deepseek-flash", "deepseek-v4-pro"). */
object AiDefaults {
    const val BASE_URL = "https://api.deepseek.com"
    const val MODEL = "deepseek-flash"
}

/**
 * The API key lives in an AndroidKeyStore-wrapped file, never in `moyu.json`.
 *
 * A backup is a plain JSON file the writer can put in the Download folder and
 * share; a key inside it would leave the phone with the first share. Keeping it
 * in a separate AES-GCM blob means "导出备份" can stay a single obvious file
 * without silently exporting a credential.
 */
object AiKey {
    private const val ALIAS = "moyu-ai-key"
    private const val FILE_NAME = "ai.key"

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun has(ctx: Context): Boolean = file(ctx).exists() && file(ctx).length() > 0

    fun save(ctx: Context, key: String): Boolean {
        val raw = key.trim()
        if (raw.isEmpty()) return false
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val body = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
            val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(body, Base64.NO_WRAP)
            file(ctx).writeText(payload, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    fun load(ctx: Context): String? = runCatching {
        val f = file(ctx)
        if (!f.exists()) return@runCatching null
        val parts = f.readText(Charsets.UTF_8).split(":", limit = 2)
        if (parts.size != 2) return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    /** Last four characters, for the "already saved" hint. Never the whole key. */
    fun tail(key: String): String = if (key.length <= 4) "••••" else "••••" + key.takeLast(4)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}

data class AiResult(
    val text: String = "",
    val error: String? = null,
    /** A model name the provider said it accepts, when a 400 listed them. */
    val modelHint: String? = null,
) {
    val ok: Boolean get() = error == null && text.isNotBlank()
}

/** OpenAI-compatible chat completions. No SDK: the app already speaks HTTP. */
object AiClient {
    suspend fun chat(
        baseUrl: String,
        model: String,
        key: String,
        temperature: Float,
        system: String,
        user: String,
        maxTokens: Int = 900,
        /** V4 默认 thinking；校对这种短判断关掉，免得预算全花在 reasoning 上。 */
        disableThinking: Boolean = false,
    ): AiResult = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext AiResult(error = "还没填 Key")
        if (baseUrl.isBlank()) return@withContext AiResult(error = "还没填服务地址")
        val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"
        runCatching {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", temperature.toDouble().coerceIn(0.0, 1.4))
                put("max_tokens", maxTokens)
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", system) })
                    put(JSONObject().apply { put("role", "user"); put("content", user) })
                })
                if (disableThinking) {
                    put("thinking", JSONObject().put("type", "disabled"))
                }
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                return@runCatching AiResult(error = explain(code, body), modelHint = suggestedModel(body))
            }
            val content = JSONObject(body).optJSONArray("choices")
                ?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            if (content.isBlank()) {
                AiResult(error = "服务返回了空内容，多半是模型名不对")
            } else {
                AiResult(text = content.trim())
            }
        }.getOrElse { e -> AiResult(error = networkError(e)) }
    }

    fun explain(code: Int, body: String): String {
        // Providers put the useful sentence in error.message; the rest of the
        // body is JSON noise that used to be dumped whole into the bubble.
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val hint = (message ?: body).take(180).replace(Regex("\\s+"), " ").trim()
        return when (code) {
            400 -> "请求被拒（400）：" + if (hint.isBlank()) "模型名或参数不对" else hint
            401, 403 -> "Key 不对，或没有这个模型的权限"
            404 -> "地址不对：服务地址要填到 /v1 这一级"
            402 -> "这个账号余额不足"
            429 -> "请求太频繁，等几秒再试"
            in 500..599 -> "服务端故障（$code）" + if (hint.isBlank()) "" else "：$hint"
            else -> "请求失败（$code）" + if (hint.isBlank()) "" else "：$hint"
        }
    }

    /**
     * When a 400 says which model names the endpoint does accept, hand one back
     * so the UI can offer a one-tap fix instead of making the writer retype it.
     */
    fun suggestedModel(body: String): String? {
        val m = Regex("supported API model names are ([^.。]+)", RegexOption.IGNORE_CASE).find(body) ?: return null
        return m.groupValues[1]
            .split(',', '，', '、')
            .map { it.trim().trim('"', '\'', ' ', '.') }
            .firstOrNull { it.isNotBlank() && it.length < 40 }
    }

    fun networkError(e: Throwable): String = when (e) {
        is UnknownHostException -> "连不上这个地址，检查网络或服务地址"
        is SocketTimeoutException -> "等太久了，网络慢或被墙"
        else -> "网络错误：" + (e.message ?: e.javaClass.simpleName)
    }
}
