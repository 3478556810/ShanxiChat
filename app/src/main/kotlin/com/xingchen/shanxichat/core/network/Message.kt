// core/network/Message.kt
package com.xingchen.shanxichat.core.network

data class Message(
    val id: String,
    val role: String,
    val content: String,        // val 不可直接修改，用 copy
    val timestamp: Long,
    val isStreaming: Boolean = false
)