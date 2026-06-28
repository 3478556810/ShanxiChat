package com.xingchen.shanxichat.core.network

data class Message(
    val id: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false
)