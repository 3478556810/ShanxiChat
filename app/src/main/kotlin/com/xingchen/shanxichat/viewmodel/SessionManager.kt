package com.xingchen.shanxichat.viewmodel

import android.content.Context
import android.util.Log
import com.xingchen.shanxichat.core.network.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SessionManager(context: Context) {
    private val sessionsFile = File(context.filesDir, "sessions.json")

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions: StateFlow<List<SessionInfo>> = _sessions.asStateFlow()

    private var _currentSessionId = "default"
    val currentSessionId: String get() = _currentSessionId

    private val sessionMessages = mutableMapOf<String, MutableList<Message>>()

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

    data class SessionInfo(
        val id: String,
        val title: String,
        val lastMessageTime: Long = System.currentTimeMillis()
    )

    fun updateSystemPrompt(newPrompt: String) { systemPrompt = newPrompt; persist() }
    fun updateBaseMemory(memory: String) { baseMemory = memory; persist() }

    fun newSession(title: String = "新对话") {
        val id = System.currentTimeMillis().toString()
        _currentSessionId = id
        sessionMessages[id] = mutableListOf()
        _sessions.update { it + SessionInfo(id, title) }
        persist()
    }

    fun switchSession(id: String) {
        if (id == _currentSessionId) return
        _currentSessionId = id
    }

    fun deleteSession(id: String) {
        sessionMessages.remove(id)
        _sessions.update { it.filter { s -> s.id != id } }
        if (_currentSessionId == id) {
            _sessions.value.firstOrNull()?.let { switchSession(it.id) } ?: newSession()
        }
        persist()
    }

    fun getMessages(sessionId: String = _currentSessionId): List<Message> =
        sessionMessages[sessionId]?.toList() ?: emptyList()

    fun addMessage(sessionId: String = _currentSessionId, message: Message) {
        sessionMessages.getOrPut(sessionId) { mutableListOf() }.add(message)
        // 如果是用户消息且标题为"新对话"，更新标题
        if (message.role == "user") {
            _sessions.update { list ->
                list.map { if (it.id == sessionId && it.title == "新对话") it.copy(title = message.content.take(20)) else it }
            }
        }
        persist()
    }

    fun loadFromDisk() {
        if (!sessionsFile.exists()) return
        try {
            val json = JSONObject(sessionsFile.readText())
            json.optJSONArray("sessions")?.let { arr ->
                val list = mutableListOf<SessionInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(SessionInfo(obj.getString("id"), obj.optString("title", "新对话"), obj.optLong("lastMessageTime", System.currentTimeMillis())))
                }
                _sessions.value = list
            }
            json.optJSONObject("messages")?.let { msgs ->
                msgs.keys().forEach { sid ->
                    val arr = msgs.getJSONArray(sid)
                    val list = mutableListOf<Message>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(Message(obj.getString("id"), obj.getString("role"), obj.optString("content", ""), obj.optLong("timestamp", System.currentTimeMillis())))
                    }
                    sessionMessages[sid] = list
                }
            }
            _currentSessionId = json.optString("currentSessionId", "default")
            systemPrompt = json.optString("systemPrompt", systemPrompt)
            baseMemory = json.optString("baseMemory", baseMemory)
        } catch (e: Exception) { Log.e("Session", "加载失败", e) }
    }

    fun persist() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject()
                json.put("sessions", JSONArray().apply { _sessions.value.forEach { put(JSONObject().apply { put("id", it.id); put("title", it.title); put("lastMessageTime", it.lastMessageTime) }) } })
                json.put("messages", JSONObject().apply { sessionMessages.forEach { (sid, msgs) -> put(sid, JSONArray().apply { msgs.forEach { put(JSONObject().apply { put("id", it.id); put("role", it.role); put("content", it.content); put("timestamp", it.timestamp) }) } }) } })
                json.put("currentSessionId", _currentSessionId)
                json.put("systemPrompt", systemPrompt)
                json.put("baseMemory", baseMemory)
                sessionsFile.writeText(json.toString())
            } catch (e: Exception) { Log.e("Session", "保存失败", e) }
        }
    }

    fun getCurrentMessages(): List<Message> = sessionMessages[_currentSessionId]?.toList() ?: emptyList()
}