package com.xingchen.shanxichat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ShanxiColors {
    val Background = Color(0xFFF5F0E6)          // 羊皮纸底色
    val Surface = Color(0xFFFFFBF5)             // 卡片白
    val TextPrimary = Color(0xFF5C4033)         // 深褐主文字
    val TextSecondary = Color(0xFF8B847A)       // 辅助灰褐
    val Border = Color(0xFFD2B48C)              // 暖棕边框
    val BubbleUser = Color(0xFFEDE4D8)          // 用户气泡
    val BubbleBot = Color.White                 // 助手气泡
    val Accent = Color(0xFFC96442)              // 陶土色强调
    val GlassHighlight = Color.White.copy(alpha = 0.06f) // 玻璃高光
}

object ShanxiShapes {
    val Bubble = RoundedCornerShape(20.dp)
    val Input = RoundedCornerShape(24.dp)
    val Card = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(12.dp)
}

object ShanxiSpaces {
    val Small = 6.dp
    val Medium = 12.dp
    val Large = 20.dp
}

object ShanxiTypography {
    val Title = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 20.sp,
        color = ShanxiColors.TextPrimary
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = ShanxiColors.TextPrimary
    )
    val Caption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        color = ShanxiColors.TextSecondary
    )
}