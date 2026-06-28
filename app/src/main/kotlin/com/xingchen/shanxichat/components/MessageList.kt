package com.xingchen.shanxichat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingchen.shanxichat.core.network.Message
import com.xingchen.shanxichat.ui.markdown.MarkdownText
import com.xingchen.shanxichat.ui.theme.ChatDesignTokens
import kotlinx.coroutines.delay

@Composable
fun MessageList(
    messages: List<Message>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val keyboardHeight = with(density) { imeInsets.getBottom(density).toDp() }

    // 当消息内容变化 或 键盘高度变化时，延迟一帧滚到底部
    LaunchedEffect(messages.size, messages.lastOrNull()?.content, keyboardHeight) {
        if (messages.isNotEmpty()) {
            // 等待布局稳定
            delay(50)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        reverseLayout = false,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            if (msg.role == "user") {
                UserBubble(msg.content)
            } else {
                BotMessage(msg.content, isStreaming = msg.isStreaming)
            }
        }
    }
}

@Composable
fun UserBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ChatDesignTokens.UserBubbleColor,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = ChatDesignTokens.BubbleMaxWidth)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color.Black,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
fun BotMessage(markdown: String, isStreaming: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.widthIn(max = 320.dp)) {
            MarkdownText(
                markdown = markdown,
                isStreaming = isStreaming
            )
        }
    }
}