package com.xingchen.shanxichat.viewmodel

import com.xingchen.shanxichat.core.network.ChatEvent
import com.xingchen.shanxichat.core.network.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

class StreamProcessor(private val onUpdate: (Message) -> Unit) {
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun process(events: Flow<ChatEvent>, initialMessage: Message) {
        val charQueue = mutableListOf<Char>()
        var streamingMsg = initialMessage

        fun startTimer() {
            if (timerJob?.isActive == true) return
            timerJob = scope.launch {
                while (isActive) {
                    // ★ 调慢节奏：200ms 更新一次
                    delay(200)

                    val chars: List<Char> = synchronized(charQueue) {
                        if (charQueue.isEmpty()) emptyList()
                        else {
                            // ★ 每次只取 1-2 个字符，不加速
                            val count = if (charQueue.size >= 2) 2 else 1
                            val taken = charQueue.take(count)
                            repeat(taken.size) { charQueue.removeAt(0) }
                            taken
                        }
                    }
                    if (chars.isNotEmpty()) {
                        streamingMsg = streamingMsg.copy(content = streamingMsg.content + chars.joinToString(""))
                        onUpdate(streamingMsg)
                    }
                }
            }
        }

        startTimer()

        scope.launch(Dispatchers.IO) {
            events.collect { event ->
                when (event) {
                    is ChatEvent.Content -> synchronized(charQueue) {
                        charQueue.addAll(event.text.toCharArray().toList())
                    }
                    is ChatEvent.Reasoning -> synchronized(charQueue) {
                        charQueue.addAll("\n[思考] ${event.text}".toCharArray().toList())
                    }
                    is ChatEvent.Done -> {
                        timerJob?.cancel()
                        synchronized(charQueue) {
                            if (charQueue.isNotEmpty()) {
                                streamingMsg = streamingMsg.copy(content = streamingMsg.content + charQueue.joinToString(""))
                                charQueue.clear()
                            }
                        }
                        onUpdate(streamingMsg.copy(isStreaming = false))
                    }
                    is ChatEvent.Error, is ChatEvent.Disconnected -> {
                        timerJob?.cancel()
                    }
                    else -> {}
                }
            }
        }
    }
}