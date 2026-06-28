package com.xingchen.shanxichat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xingchen.shanxichat.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

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

    fun updateSystemPrompt(newPrompt: String) {
        systemPrompt = newPrompt
        persistSessionsToDisk()
    }

    fun updateBaseMemory(memory: String) {
        baseMemory = memory
        persistSessionsToDisk()
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                loadSessionsFromFile()
            }
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
        _chatState.update {
            it.copy(messages = it.messages + userMsg, isLoading = true, error = null)
        }
        val session = _sessions.value.find { it.id == _currentSessionId }
        if (session != null && session.title == "新对话") {
            _sessions.update { list ->
                list.map { if (it.id == _currentSessionId) it.copy(title = text.take(20)) else it }
            }
            persistSessionsToDisk()
        }

        viewModelScope.launch {
            try {
                val config = currentConfig
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", if (baseMemory.isNotBlank()) systemPrompt + "\n\n" + baseMemory else systemPrompt)
                    })
                    val history = _chatState.value.messages.takeLast(10)
                    for (msg in history) {
                        put(JSONObject().apply {
                            put("role", msg.role)
                            put("content", msg.content ?: "")
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

                val streamingId = "streaming_${System.currentTimeMillis()}"
                var streamingMsg = Message(
                    id = streamingId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis(),
                    isStreaming = true
                )
                _chatState.update {
                    it.copy(messages = it.messages + streamingMsg)
                }

                // 字符队列 + 定时器
                val charQueue = mutableListOf<Char>()
                var timerJob: Job? = null

                fun startDisplayTimer() {
                    if (timerJob?.isActive == true) return
                    timerJob = viewModelScope.launch {
                        while (isActive && _chatState.value.isLoading) {
                            delay(60)

                            val charsToTake: List<Char>
                            synchronized(charQueue) {
                                if (charQueue.isEmpty()) {
                                    charsToTake = emptyList()
                                } else {
                                    val count = when {
                                        charQueue.size > 50 -> 8
                                        charQueue.size > 30 -> 6
                                        charQueue.size > 10 -> 4
                                        else -> 3
                                    }
                                    charsToTake = charQueue.take(count)
                                    repeat(charsToTake.size) { charQueue.removeAt(0) }
                                }
                            }

                            if (charsToTake.isNotEmpty()) {
                                val newContent = charsToTake.joinToString("")
                                streamingMsg = streamingMsg.copy(content = streamingMsg.content + newContent)
                                val updated = _chatState.value.messages.map { msg ->
                                    if (msg.id == streamingId) streamingMsg else msg
                                }
                                _chatState.update { it.copy(messages = updated) }
                            }
                        }
                    }
                }

                startDisplayTimer()

                sseClient.connect(requestJson, config.apiKey).collect { event ->
                    when (event) {
                        is ChatEvent.Connected -> {}
                        is ChatEvent.Content -> {
                            val content = event.text
                            if (content.isNotEmpty() && content != "null") {
                                synchronized(charQueue) {
                                    charQueue.addAll(content.toCharArray().toList())
                                }
                            }
                        }
                        is ChatEvent.Reasoning -> {
                            synchronized(charQueue) {
                                charQueue.addAll("\n[思考] ${event.text}".toCharArray().toList())
                            }
                        }
                        is ChatEvent.Done -> {
                            timerJob?.cancel()

                            // 清空剩余队列，一次性合并
                            synchronized(charQueue) {
                                if (charQueue.isNotEmpty()) {
                                    val remaining = charQueue.joinToString("")
                                    streamingMsg = streamingMsg.copy(content = streamingMsg.content + remaining)
                                    charQueue.clear()
                                }
                            }

                            streamingMsg = streamingMsg.copy(isStreaming = false)
                            sessionMessages.getOrPut(_currentSessionId) { mutableListOf() }.add(streamingMsg)
                            val updated = _chatState.value.messages.map { msg ->
                                if (msg.id == streamingId) streamingMsg else msg
                            }
                            _chatState.update {
                                it.copy(messages = updated, isLoading = false)
                            }
                            persistSessionsToDisk()
                        }
                        is ChatEvent.Error -> {
                            Log.e("ChatVM", "SSE 错误: ${event.message}")
                            timerJob?.cancel()
                            _chatState.update { it.copy(isLoading = false, error = event.message) }
                        }
                        is ChatEvent.Disconnected -> {
                            if (_chatState.value.isLoading) {
                                timerJob?.cancel()
                                _chatState.update { it.copy(isLoading = false, error = "连接意外断开") }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val friendlyError = when (e) {
                    is UnknownHostException -> "无法解析服务器地址: ${e.message}"
                    is ConnectException -> "连接被拒绝，请检查服务是否启动: ${e.message}"
                    is SocketTimeoutException -> "连接超时: ${e.message}"
                    is IOException -> "网络错误: ${e.message}"
                    else -> "未知错误: ${e.message}"
                }
                _chatState.update { it.copy(isLoading = false, error = friendlyError) }
            }
        }
    }

    fun newSession(title: String = "新对话") {
        val id = System.currentTimeMillis().toString()
        _currentSessionId = id
        sessionMessages[id] = mutableListOf()
        _sessions.update { it + SessionInfo(id, title) }
        _chatState.value = ChatState()
        persistSessionsToDisk()
    }

    fun switchSession(id: String) {
        if (id == _currentSessionId) return
        _currentSessionId = id
        val messages = sessionMessages[id] ?: mutableListOf()
        _chatState.value = ChatState(messages = messages.toList())
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
        persistSessionsToDisk()
    }

    private fun persistSessionsToDisk() {
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
                    sessionMessages.forEach { (sessionId, messages) ->
                        val msgArray = JSONArray()
                        messages.forEach { msg ->
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

    private fun loadSessionsFromFile() {
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
                    val messages = mutableListOf<Message>()
                    for (i in 0 until msgArray.length()) {
                        val obj = msgArray.getJSONObject(i)
                        messages.add(Message(
                            id = obj.getString("id"),
                            role = obj.getString("role"),
                            content = obj.optString("content", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        ))
                    }
                    sessionMessages[sessionId] = messages
                }
            }
            _currentSessionId = json.optString("currentSessionId", "default")
            systemPrompt = json.optString("systemPrompt", systemPrompt)
            baseMemory = json.optString("baseMemory", baseMemory)
        } catch (e: Exception) {
            Log.e("ChatVM", "加载会话失败", e)
        }
    }

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
                _chatState.update { it.copy(error = "保存配置失败: ${e.message}") }
            }
        }
    }
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)