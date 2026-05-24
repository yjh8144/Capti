package com.capti.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val latencyMode by viewModel.latencyMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务器地址
            Text(
                text = "FunASR 服务器",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text("WebSocket 地址") },
                placeholder = { Text("ws://192.168.1.100:10095") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "输入你部署 Capti 服务器的 WebSocket 地址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 延迟模式
            Text(
                text = "识别模式",
                style = MaterialTheme.typography.titleMedium
            )

            LatencyModeSelector(
                selected = latencyMode,
                onSelect = { viewModel.updateLatencyMode(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 离线引擎说明
            Text(
                text = "离线引擎",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "断网时自动切换到本地 sherpa-onnx 引擎。\n首次使用需下载模型文件（约 100MB）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LatencyModeSelector(selected: String, onSelect: (String) -> Unit) {
    val modes = listOf(
        Triple("low", "低延迟", "~1秒出字幕，说话人后台标注"),
        Triple("medium", "均衡", "~3秒出字幕，说话人分离较准"),
        Triple("high", "高精度", "~5秒出字幕，说话人标注最准确"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (value, title, desc) ->
            Surface(
                onClick = { onSelect(value) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected == value)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected == value)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected == value)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
