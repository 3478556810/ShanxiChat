package com.xingchen.shanxichat.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic

@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    enabled: Boolean
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("给杉汐发消息...", color = Color.White.copy(alpha = 0.5f)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                    focusedBorderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = Color.White
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (text.isBlank()) {
                IconButton(onClick = { /* 预留语音输入 */ }) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "语音",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                IconButton(onClick = {
                    if (enabled && text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}