package com.xingchen.shanxichat.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xingchen.shanxichat.ui.components.MessageList
import com.xingchen.shanxichat.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShanxiMainScreen() {
    val viewModel: ChatViewModel = viewModel()
    val chatState by viewModel.chatState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    var drawerOpen by remember { mutableStateOf(false) }
    var showPersonaEditor by remember { mutableStateOf(false) }
    var showBaseMemoryEditor by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }

    var settingsType by remember { mutableStateOf("local_3b") }
    var settingsUrl by remember { mutableStateOf("") }
    var settingsModel by remember { mutableStateOf("") }
    var settingsApiKey by remember { mutableStateOf("") }

    LaunchedEffect(showParams) {
        if (showParams) {
            settingsType = viewModel.getCurrentRoute()
            settingsUrl = viewModel.getCurrentUrl()
            settingsModel = viewModel.getCurrentModel()
            settingsApiKey = viewModel.getCurrentApiKey()
        }
    }
    LaunchedEffect(settingsType) {
        val (url, model, key) = viewModel.getStoredConfigForRoute(settingsType)
        settingsUrl = url
        settingsModel = model
        settingsApiKey = key
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "打开菜单", tint = Color(0xFF5C4033))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFF5F0E6),
                        titleContentColor = Color(0xFF5C4033)
                    )
                )
            },
            containerColor = Color(0xFFF5F0E6)
            // ✅ 关键修改：删除了 contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // 消息列表
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MessageList(
                        messages = chatState.messages   // ✅ 不再需要 streamingMessage
                    )
                    if (!chatState.error.isNullOrBlank()) {
                        Text(
                            text = chatState.error ?: "",
                            color = Color.Red,
                            modifier = Modifier.padding(8.dp).align(Alignment.TopCenter)
                        )
                    }
                }

                // 底部输入框
                Surface(
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    color = Color(0xFFF5F0E6),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("给杉汐发消息...", fontFamily = FontFamily.Serif) },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.DarkGray,
                                focusedBorderColor = Color(0xFFD2B48C),
                                unfocusedBorderColor = Color(0xFFD2B48C).copy(alpha = 0.5f)
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (inputText.isBlank()) {
                            IconButton(onClick = { /* 语音预留 */ }) {
                                Icon(Icons.Default.Mic, contentDescription = "语音", tint = Color.DarkGray)
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Color(0xFF5C4033))
                            }
                        }
                    }
                }
            }
        }

        // 抽屉菜单（无变化）
        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Transparent).clickable { drawerOpen = false }
            ) {
                Surface(
                    modifier = Modifier.fillMaxHeight().width(280.dp).align(Alignment.CenterStart),
                    color = Color(0xFFF5F0E6),
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { drawerOpen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color(0xFF5C4033))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "人设修改",
                            modifier = Modifier.fillMaxWidth().clickable {
                                showPersonaEditor = true; drawerOpen = false
                            }.padding(vertical = 12.dp),
                            color = Color(0xFF5C4033), fontSize = 18.sp, fontFamily = FontFamily.Serif
                        )
                        HorizontalDivider(color = Color(0xFFD2B48C))

                        Text(
                            "参数设置",
                            modifier = Modifier.fillMaxWidth().clickable {
                                showParams = true; drawerOpen = false
                            }.padding(vertical = 12.dp),
                            color = Color(0xFF5C4033), fontSize = 18.sp, fontFamily = FontFamily.Serif
                        )
                        HorizontalDivider(color = Color(0xFFD2B48C))

                        Text(
                            "Base 层记忆",
                            modifier = Modifier.fillMaxWidth().clickable {
                                showBaseMemoryEditor = true; drawerOpen = false
                            }.padding(vertical = 12.dp),
                            color = Color(0xFF5C4033), fontSize = 18.sp, fontFamily = FontFamily.Serif
                        )
                        HorizontalDivider(color = Color(0xFFD2B48C))

                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.newSession(); drawerOpen = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD2B48C))
                        ) { Text("新建对话", color = Color.White) }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFD2B48C))

                        val sessions by viewModel.sessions.collectAsState()
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(sessions.size) { index ->
                                val session = sessions[index]
                                val isCurrent = session.id == viewModel.currentSessionId
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { viewModel.switchSession(session.id); drawerOpen = false }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        session.title,
                                        modifier = Modifier.weight(1f),
                                        color = if (isCurrent) Color(0xFF5C4033) else Color.Gray,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (session.id != "default") {
                                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFB22222))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 以下对话框无变化...
        if (showPersonaEditor) {
            var editingPersona by remember { mutableStateOf(viewModel.systemPrompt) }
            AlertDialog(
                onDismissRequest = { showPersonaEditor = false },
                title = { Text("修改人设", fontFamily = FontFamily.Serif) },
                text = {
                    OutlinedTextField(
                        value = editingPersona,
                        onValueChange = { editingPersona = it },
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        label = { Text("系统提示词") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateSystemPrompt(editingPersona)
                        showPersonaEditor = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showPersonaEditor = false }) { Text("取消") }
                }
            )
        }

        if (showBaseMemoryEditor) {
            var editingMemory by remember { mutableStateOf(viewModel.baseMemory) }
            AlertDialog(
                onDismissRequest = { showBaseMemoryEditor = false },
                title = { Text("Base 层记忆", fontFamily = FontFamily.Serif) },
                text = {
                    OutlinedTextField(
                        value = editingMemory,
                        onValueChange = { editingMemory = it },
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        label = { Text("永久记忆（会被加入每次对话）") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateBaseMemory(editingMemory)
                        showBaseMemoryEditor = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showBaseMemoryEditor = false }) { Text("取消") }
                }
            )
        }

        if (showParams) {
            AlertDialog(
                onDismissRequest = { showParams = false },
                title = { Text("参数设置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("后端类型")
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expanded = true }) { Text(settingsType) }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("local_3b", "local_7b_pc", "ds", "cloud_480b").forEach { type ->
                                    DropdownMenuItem(text = { Text(type) }, onClick = {
                                        settingsType = type
                                        expanded = false
                                    })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = settingsUrl, onValueChange = { settingsUrl = it }, label = { Text("API 地址") }, singleLine = true)
                        OutlinedTextField(value = settingsModel, onValueChange = { settingsModel = it }, label = { Text("模型名") }, singleLine = true)
                        OutlinedTextField(value = settingsApiKey, onValueChange = { settingsApiKey = it }, label = { Text("API Key（选填）") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.switchRoute(settingsType, settingsUrl, settingsModel, settingsApiKey)
                        showParams = false
                    }) { Text("保存") }
                },
                dismissButton = {
                    TextButton(onClick = { showParams = false }) { Text("取消") }
                }
            )
        }
    }
}