package com.xingchen.shanxichat.core.network

sealed class ChatEvent {
    object Connected : ChatEvent()
    object Done : ChatEvent()
    object Disconnected : ChatEvent()
    data class Content(val text: String) : ChatEvent()
    data class Reasoning(val text: String) : ChatEvent()
    data class Error(val message: String) : ChatEvent()
}

