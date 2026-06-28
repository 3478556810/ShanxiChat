package com.xingchen.shanxichat.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String
)

@Serializable
data class ChatEvent(
    val type: String,
    val content: String? = null,
    val reasoning: String? = null,
    val name: String = "",
    val args: String = "",
    val error: String? = null
)

data class Message(
    val id: Long,
    val content: String,
    val role: String,
    val reasoning: String? = null,
    val toolCallState: ToolCallState? = null,
    val isStreaming: Boolean = false
)

data class ToolCallState(
    val name: String,
    val args: String
)