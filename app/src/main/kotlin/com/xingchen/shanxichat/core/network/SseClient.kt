package com.xingchen.shanxichat.core.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class SseClient(private val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(requestJson: String, apiKey: String? = null): Flow<ChatEvent> = callbackFlow {
        val body = requestJson.toRequestBody("application/json".toMediaType())
        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .post(body)
            .header("Content-Type", "application/json")
        if (!apiKey.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
            Log.e("SseClient", "添加 Bearer 认证: ${apiKey.take(4)}...")
        }

        val request = requestBuilder.build()

        try {
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.IGNORE)
                .onUnmappableCharacter(CodingErrorAction.IGNORE)
            val reader = BufferedReader(InputStreamReader(response.body?.byteStream(), decoder))

            trySend(ChatEvent.Connected)

            withContext(Dispatchers.IO) {
                val contentBuffer = StringBuilder()
                var lastEmitTime = System.currentTimeMillis()
                var pendingReasoning = ""

                // 辅助函数：定时批量发射 Content
                fun flushContent(force: Boolean = false) {
                    val now = System.currentTimeMillis()
                    if (contentBuffer.isNotEmpty() && (force || now - lastEmitTime >= 50)) {
                        trySend(ChatEvent.Content(contentBuffer.toString()))
                        contentBuffer.clear()
                        lastEmitTime = now
                    }
                }

                reader.useLines { lines ->
                    lines.forEach { line ->
                        Log.d("SseClient", "原始行: >$line<")
                        if (!line.startsWith("data: ")) {
                            if (line.isNotBlank()) {
                                try {
                                    val err = JSONObject(line)
                                    val msg = err.optString("error", err.optString("message", line))
                                    trySend(ChatEvent.Error(msg))
                                } catch (e: Exception) {
                                    trySend(ChatEvent.Error(line))
                                }
                            }
                            return@forEach
                        }

                        val data = line.removePrefix("data: ").trim()
                        if (data.isBlank() || data == "[DONE]") {
                            if (data == "[DONE]") {
                                flushContent(force = true)       // 发完剩余内容
                                trySend(ChatEvent.Done)
                            }
                            return@forEach
                        }

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: return@forEach
                            if (choices.length() == 0) return@forEach
                            val first = choices.getJSONObject(0)

                            val delta = first.optJSONObject("delta")
                            if (delta != null) {
                                // 处理 DeepSeek 的思考过程
                                val reasoning = delta.optString("reasoning_content", "")
                                if (reasoning.isNotEmpty()) {
                                    // reasoning 单独发送，不加批量，保持实时
                                    trySend(ChatEvent.Reasoning(reasoning))
                                    return@forEach
                                }

                                val content = delta.optString("content", "")
                                if (content.isNotEmpty() && content != "null") {
                                    // ★ 收集内容，定时批量发送
                                    contentBuffer.append(content)
                                    flushContent()
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略单行解析错误
                        }
                    }
                }
                // 读取结束后，清空缓冲区
                flushContent(force = true)
                trySend(ChatEvent.Disconnected)
            }
        } catch (e: Exception) {
            Log.e("SseClient", "连接或读取异常", e)
            trySend(ChatEvent.Error(e.message ?: "未知错误"))
        }
        awaitClose {}
    }
}