package com.psychat.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.psychat.app.domain.model.Message

/**
 * 简单的聊天界面 - 用于测试Step 3和Step 4的功能
 * 订阅ChatState，显示对话滚动刷新，错误时Toast
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    // Step 4: 订阅state
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // 错误处理 - 显示Toast
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    // 自动滚动到最新消息
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "PsyChat - Step 3&4 测试",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // 状态指示器
        if (state.loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isTyping) "AI正在思考..." else "发送中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.isEmpty) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "开始对话吧！输入 'hello' 测试连接。",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            items(state.messages) { message ->
                SimpleMessageItem(message = message)
            }
        }
        
        // 输入区域
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("输入消息...") },
                modifier = Modifier.weight(1f),
                enabled = !state.loading,
                singleLine = true
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        // Step 4: 调用新的send方法
                        viewModel.send(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = !state.loading && inputText.isNotBlank()
            ) {
                Text("发送")
            }
        }
        
        // 调试信息
        if (state.conversationId != null) {
            Text(
                text = "会话ID: ${state.conversationId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SimpleMessageItem(message: Message) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isFromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (message.isFromUser) "你" else "AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.isFromUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                
                Text(
                    text = message.timestamp.toString().substring(11, 19), // HH:mm:ss
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
    }
}
