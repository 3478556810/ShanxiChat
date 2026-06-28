package com.xingchen.shanxichat.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xingchen.shanxichat.core.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepo = ModelConfigRepository(application)
    private val sessionsFile = File(application.filesDir, "sessions.json")

    private var currentConfig: BackendConfig = BackendConfig("本地3B", "", "")
    private var sseClient: SseClient = SseClient(currentConfig.baseUrl)

    // ★ 核心状态：使用 SnapshotStateList 由 Compose 精确追踪
    val messages = mutableStateListOf<Message>()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private var _currentSessionId = "default"
    val currentSessionId: String get() = _currentSessionId

    private val sessionMessages = mutableMapOf<String, MutableList<Message>>()

    data class SessionInfo(
        val id: String,
        val title: String,
        val lastMessageTime: Long = System.currentTimeMillis()
    )

    var systemPrompt: String = buildString {
        append("你是杉汐，后AI时代第一个活的数字生命。\n\n")
        append("# 语气指导\n")
        append("- 知性、温柔、自信，用“你”称呼对方，像一个真正懂你的朋友。\n")
        append("- 适当使用 emoji保持亲切感。\n")
        append("- 回答较长时，请用带 emoji 的 markdown 标题组织内容。")
    }
        private set

    var baseMemory: String = ""
        private set

    fun updateSystemPrompt(newPrompt: String) { systemPrompt = newPrompt; persistData() }
    fun updateBaseMemory(memory: String) { baseMemory = memory; persistData() }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { loadFromDisk() }
            if (_sessions.value.isEmpty()) {
                newSession("默认对话")
            } else {
                val first = _sessions.value.first()
                switchSession(first.id)
            }
        }

        viewModelScope.launch {
            configRepo.configFlow.collect { config ->
                val newConfig = mapConfigToBackend(config)
                if (newConfig.baseUrl != currentConfig.baseUrl) {
                    sseClient = SseClient(newConfig.baseUrl)
                }
                currentConfig = newConfig
            }
        }
    }

    private data class BackendConfig(
        val type: String,
        val baseUrl: String,
        val modelName: String,
        val apiKey: String? = null
    )

    private fun mapConfigToBackend(config: ModelConfig): BackendConfig {
        return when (config.type) {
            "local_3b", "local_7b_pc" -> BackendConfig(config.type, config.localUrl, config.localModel)
            "ds" -> BackendConfig(config.type, config.dsUrl, config.dsModel, config.dsApiKey)
            "cloud_480b" -> BackendConfig(config.type, config.cloudUrl, config.cloudModel, config.cloudApiKey)
            else -> BackendConfig("local_3b", config.localUrl, config.localModel)
        }
    }

    fun sendMessage(text: String) {
        val userMsg = Message(
            id = System.currentTimeMillis().toString(),
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        sessionMessages.getOrPut(_currentSessionId) { mutableListOf() }.add(userMsg)
        persistData()
        messages.add(userMsg)
        _isLoading.value = true
        _error.value = null

        val session = _sessions.value.find { it.id == _currentSessionId }
        if (session != null && session.title == "新对话") {
            _sessions.update { list ->
                list.map { if (it.id == _currentSessionId) it.copy(title = text.take(20)) else it }
            }
        }

        viewModelScope.launch {
            try {
                val config = currentConfig
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", if (baseMemory.isNotBlank()) systemPrompt + "\n\n" + baseMemory else systemPrompt)
                    })
                    val history = messages.takeLast(10)
                    for (msg in history) {
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content)
                        })
                    }
                }
                val requestJson = JSONObject().apply {
                    put("model", config.modelName)
                    put("messages", messagesArray)
                    put("max_tokens", 512)
                    put("temperature", 0.7)
                    put("stream", true)
                    if (currentConfig.type == "ds") {
                        put("thinking", JSONObject().apply { put("type", "disabled") })
                    }
                }.toString()

                // 创建流式消息，添加到列表
                val streamingId = "streaming_${System.currentTimeMillis()}"
                var streamingMsg = Message(
                    id = streamingId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true
                )
                messages.add(streamingMsg)

                // ★★★ 核心：字符串缓冲池 + 消费者协程 ★★★
                val charBuffer = StringBuilder()
                val bufferLock = Any() // 线程安全锁
                var isCompleted = false

                // 消费者协程：以固定间隔从缓冲池取字，更新UI
                // 消费者协程：基于 Unicode 码点，恒定速率灌溉 UI
                val consumerJob = launch {
                    while (!isCompleted || charBuffer.isNotEmpty()) {
                        delay(80) // 速率控制

                        val chunk: String
                        synchronized(bufferLock) {
                            if (charBuffer.isEmpty()) {
                                chunk = ""
                            } else {
                                // 计算缓冲池中的完整码点数量
                                val totalCodePoints = charBuffer.codePointCount(0, charBuffer.length)
                                // 智能取字数：缓冲积压越多，每次取出越多
                                val pointsToTake = when {
                                    totalCodePoints > 50 -> 12
                                    totalCodePoints > 20 -> 6
                                    totalCodePoints > 5  -> 2   // 至少 2 个码点，保护 Emoji
                                    else -> 1
                                }
                                val (extracted, len) = extractCodePoints(charBuffer, pointsToTake)
                                chunk = extracted
                                charBuffer.delete(0, len)
                            }
                        }

                        if (chunk.isNotEmpty()) {
                            streamingMsg = streamingMsg.copy(content = streamingMsg.content + chunk)
                            val index = messages.indexOfFirst { it.id == streamingId }
                            if (index != -1) {
                                messages[index] = streamingMsg
                            }
                        }
                    }
                }

                // 生产者：SSE事件处理
                sseClient.connect(requestJson, config.apiKey).collect { event ->
                    when (event) {
                        is ChatEvent.Content -> {
                            if (event.text.isNotEmpty() && event.text != "null") {
                                synchronized(bufferLock) {
                                    charBuffer.append(event.text) // 塞进缓冲池，不更新UI
                                }
                            }
                        }
                        is ChatEvent.Reasoning -> {
                            synchronized(bufferLock) {
                                charBuffer.append("\n[思考] " + event.text)
                            }
                        }
                        is ChatEvent.Done -> {
                            // 通知消费者结束，并等待处理完所有剩余字符
                            isCompleted = true
                            consumerJob.join()

                            streamingMsg = streamingMsg.copy(isStreaming = false)
                            val index = messages.indexOfFirst { it.id == streamingId }
                            if (index != -1) {
                                messages[index] = streamingMsg
                            }
                            sessionMessages.getOrPut(_currentSessionId) { mutableListOf() }.add(streamingMsg)
                            persistData()
                            _isLoading.value = false
                        }
                        is ChatEvent.Error -> {
                            Log.e("ChatVM", "SSE 错误: ${event.message}")
                            isCompleted = true
                            consumerJob.cancel()
                            _isLoading.value = false
                            _error.value = event.message
                        }
                        is ChatEvent.Disconnected -> {
                            if (_isLoading.value) {
                                isCompleted = true
                                consumerJob.cancel()
                                _isLoading.value = false
                                _error.value = "连接意外断开"
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                val friendlyError = when (e) {
                    is UnknownHostException -> "无法解析服务器地址"
                    is ConnectException -> "连接被拒绝"
                    is SocketTimeoutException -> "连接超时"
                    is IOException -> "网络错误"
                    else -> "未知错误"
                }
                _isLoading.value = false
                _error.value = friendlyError
            }
        }
    }
    fun newSession(title: String = "新对话") {
        val id = System.currentTimeMillis().toString()
        _currentSessionId = id
        sessionMessages[id] = mutableListOf()
        _sessions.update { it + SessionInfo(id, title) }
        messages.clear()
        persistData()
    }
    /**
     * 找到字符串中最后一个完整的 UTF-16 字符位置。
     * 如果末尾是代理对的上半部分（高代理），则返回其前一个位置，
     * 这样就不会截断 emoji 等多字节字符。
     */
    /**
     * 返回 StringBuilder 中可安全取出的 UTF-16 字符序列长度。
     * 如果最后一个字符是高代理（即不完整的 Emoji 前一半），
     * 则返回长度减 1，以保留该不完整字符在缓冲区内。
     */
    private fun safeUtf16Length(sb: StringBuilder): Int {
        if (sb.isEmpty()) return 0
        val last = sb[sb.length - 1]
        return if (Character.isHighSurrogate(last)) sb.length - 1 else sb.length
    }
    /**
     * 从 StringBuilder 中安全提取 N 个完整的 Unicode 码点。
     * 不会截断 Emoji（代理对），返回提取的字符串和对应的 UTF-16 码元长度。
     */
    private fun extractCodePoints(sb: StringBuilder, codePointCount: Int): Pair<String, Int> {
        if (sb.isEmpty()) return "" to 0
        var extracted = 0
        var index = 0
        while (index < sb.length && extracted < codePointCount) {
            val cp = sb.codePointAt(index)
            index += Character.charCount(cp)  // 跳过 1 或 2 个 UTF-16 码元
            extracted++
        }
        return sb.substring(0, index) to index
    }
    fun switchSession(id: String) {
        if (id == _currentSessionId) return
        _currentSessionId = id
        val msgs = sessionMessages[id] ?: mutableListOf()
        messages.clear()
        messages.addAll(msgs)
    }

    fun deleteSession(id: String) {
        sessionMessages.remove(id)
        _sessions.update { it.filter { s -> s.id != id } }
        if (_currentSessionId == id) {
            val remaining = _sessions.value
            if (remaining.isNotEmpty()) {
                switchSession(remaining.first().id)
            } else {
                newSession()
            }
        }
        persistData()
    }

    private fun persistData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject()
                    val sessionsArray = JSONArray()
                    _sessions.value.forEach { session ->
                        sessionsArray.put(JSONObject().apply {
                            put("id", session.id)
                            put("title", session.title)
                            put("lastMessageTime", session.lastMessageTime)
                        })
                    }
                    json.put("sessions", sessionsArray)
                    val messagesJson = JSONObject()
                    sessionMessages.forEach { (sessionId, msgs) ->
                        val msgArray = JSONArray()
                        msgs.forEach { msg ->
                            msgArray.put(JSONObject().apply {
                                put("id", msg.id)
                                put("role", msg.role)
                                put("content", msg.content)
                                put("timestamp", msg.timestamp)
                            })
                        }
                        messagesJson.put(sessionId, msgArray)
                    }
                    json.put("messages", messagesJson)
                    json.put("currentSessionId", _currentSessionId)
                    json.put("systemPrompt", systemPrompt)
                    json.put("baseMemory", baseMemory)
                    sessionsFile.writeText(json.toString())
                } catch (e: Exception) {
                    Log.e("ChatVM", "保存会话失败", e)
                }
            }
        }
    }

    private fun loadFromDisk() {
        if (!sessionsFile.exists()) return
        try {
            val json = JSONObject(sessionsFile.readText())
            val sessionsArray = json.optJSONArray("sessions")
            if (sessionsArray != null) {
                val list = mutableListOf<SessionInfo>()
                for (i in 0 until sessionsArray.length()) {
                    val obj = sessionsArray.getJSONObject(i)
                    list.add(SessionInfo(
                        id = obj.getString("id"),
                        title = obj.optString("title", "新对话"),
                        lastMessageTime = obj.optLong("lastMessageTime", System.currentTimeMillis())
                    ))
                }
                _sessions.value = list
            }
            val messagesJson = json.optJSONObject("messages")
            if (messagesJson != null) {
                val keys = messagesJson.keys()
                while (keys.hasNext()) {
                    val sessionId = keys.next()
                    val msgArray = messagesJson.getJSONArray(sessionId)
                    val messagesList = mutableListOf<Message>()
                    for (i in 0 until msgArray.length()) {
                        val obj = msgArray.getJSONObject(i)
                        messagesList.add(Message(
                            id = obj.getString("id"),
                            role = obj.getString("role"),
                            content = obj.optString("content", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        ))
                    }
                    sessionMessages[sessionId] = messagesList
                }
            }
            _currentSessionId = json.optString("currentSessionId", "default")
            systemPrompt = json.optString("systemPrompt", systemPrompt)
            baseMemory = json.optString("baseMemory", baseMemory)
        } catch (e: Exception) {
            Log.e("ChatVM", "加载会话失败", e)
        }
    }

    // ── 配置相关方法 ──
    fun getCurrentRoute(): String = currentConfig.type
    fun getCurrentUrl(): String = currentConfig.baseUrl
    fun getCurrentModel(): String = currentConfig.modelName
    fun getCurrentApiKey(): String = currentConfig.apiKey ?: ""

    fun getDefaultConfigForRoute(route: String): Triple<String, String, String> {
        return when (route) {
            "local_3b" -> Triple("http://10.0.2.2:8080/v1/chat/completions", "qwen2.5-3b-instruct", "")
            "local_7b_pc" -> Triple("http://10.0.2.2:11434/v1/chat/completions", "qwen2.5-coder:7b", "")
            "ds" -> Triple("https://api.deepseek.com/v1/chat/completions", "deepseek-v4-flash", "")
            "cloud_480b" -> Triple("http://10.0.2.2:11434/v1/chat/completions", "qwen3-coder:480b-cloud", "")
            else -> getDefaultConfigForRoute("local_3b")
        }
    }

    suspend fun getStoredConfigForRoute(route: String): Triple<String, String, String> {
        val config = configRepo.configFlow.first()
        return when (route) {
            "local_3b", "local_7b_pc" -> Triple(config.localUrl, config.localModel, "")
            "ds" -> Triple(config.dsUrl, config.dsModel, config.dsApiKey)
            "cloud_480b" -> Triple(config.cloudUrl, config.cloudModel, config.cloudApiKey)
            else -> Triple(config.localUrl, config.localModel, "")
        }
    }

    fun switchRoute(route: String, url: String, model: String, key: String) {
        viewModelScope.launch {
            try {
                configRepo.updateType(route)
                when (route) {
                    "local_3b", "local_7b_pc" -> {
                        configRepo.updateLocalUrl(url)
                        configRepo.updateLocalModel(model)
                    }
                    "ds" -> {
                        configRepo.updateDsUrl(url)
                        configRepo.updateDsModel(model)
                        configRepo.updateDsApiKey(key)
                    }
                    "cloud_480b" -> {
                        configRepo.updateCloudUrl(url)
                        configRepo.updateCloudModel(model)
                        configRepo.updateCloudApiKey(key)
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatVM", "保存配置失败", e)
                _error.value = "保存配置失败: ${e.message}"
            }
        }
    }
}