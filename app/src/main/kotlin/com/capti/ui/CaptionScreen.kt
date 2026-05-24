package com.capti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.capti.engine.EngineManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionScreen(
    onStartCaption: () -> Unit,
    onStopCaption: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: CaptionViewModel = hiltViewModel()
) {
    val captions by viewModel.captions.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val activeEngine by viewModel.activeEngine.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) {
            listState.animateScrollToItem(captions.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capti") },
                actions = {
                    EngineChip(activeEngine)
                    IconButton(onClick = { viewModel.clearCaptions() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isRecording) onStopCaption() else onStartCaption()
                },
                containerColor = if (isRecording)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (isRecording) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isRecording) "停止" else "开始"
                )
            }
        }
    ) { padding ->
        if (captions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isRecording) "正在聆听..." else "点击麦克风按钮开始",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(captions) { entry ->
                    CaptionBubble(
                        text = entry.text,
                        speakerName = if (entry.isOverlap) "多人" else viewModel.speakerManager.getName(entry.speakerId),
                        speakerColor = if (entry.isOverlap) MaterialTheme.colorScheme.error else viewModel.speakerManager.getColor(entry.speakerId),
                        isFinal = entry.isFinal,
                        isOverlap = entry.isOverlap
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptionBubble(
    text: String,
    speakerName: String,
    speakerColor: androidx.compose.ui.graphics.Color,
    isFinal: Boolean,
    isOverlap: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(speakerColor)
            )
            Text(
                text = speakerName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = speakerColor
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (isFinal) 1f else 0.7f
            )
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EngineChip(engineType: EngineManager.EngineType) {
    val label = when (engineType) {
        EngineManager.EngineType.FUNASR -> "云端"
        EngineManager.EngineType.SHERPA -> "离线"
    }
    val color = when (engineType) {
        EngineManager.EngineType.FUNASR -> MaterialTheme.colorScheme.primary
        EngineManager.EngineType.SHERPA -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = color
        )
    }
}
