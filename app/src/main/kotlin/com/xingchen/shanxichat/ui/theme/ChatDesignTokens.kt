package com.xingchen.shanxichat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChatDesignTokens {
    // 颜色
    val UserBubbleColor = Color(0xFFE8E6E1)
    val BotTextColor = Color(0xFF222222)
    val PageBackground = Color(0xFFFAF7F0)

    // 宽高与圆角
    val BubbleMaxWidth = 280.dp
    val BubbleCornerUser = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    val BotContentMaxWidth = 520.dp // Vue 中栏宽度的对应值
}