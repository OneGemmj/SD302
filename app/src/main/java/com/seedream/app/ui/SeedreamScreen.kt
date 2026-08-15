package com.seedream.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.seedream.app.model.MODEL_SEEDREAM_4_5
import com.seedream.app.model.MODEL_SEEDREAM_5
import com.seedream.app.model.MODEL_SEEDREAM_5_PRO
import com.seedream.app.model.ReferenceImage
import com.seedream.app.model.ReferenceKind
import com.seedream.app.model.ResultImage
import com.seedream.app.model.StatusKind
import com.seedream.app.model.supportsOutputFormat
import com.seedream.app.model.supportsStream
import com.seedream.app.model.supportsWebSearch
import com.seedream.app.network.SearchProvider
import com.seedream.app.storage.HistoryEntity

private enum class AppTab(
    val label: String,
    val icon: ImageVector
) {
    Create("创作", Icons.Default.Send),
    Results("结果", Icons.Default.PhotoLibrary),
    History("历史", Icons.Default.History),
    Debug("调试", Icons.Default.ContentCopy)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedreamScreen(state: SeedreamUiState, viewModel: SeedreamViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selectedTab by remember { mutableStateOf(AppTab.Create) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showParamsDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> viewModel.addLocalImages(context, uris) }

    // SAF launchers for backup export (zip) and restore import (zip).
    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.createBackupToUri(uri, context)
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "SD302",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "图像生成与编辑",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showApiDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "API")
                    }
                    IconButton(onClick = { showParamsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "参数")
                    }
                    IconButton(onClick = { showBackupDialog = true }) {
                        Icon(Icons.Default.Backup, contentDescription = "备份")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(12.dp)
        ) {
            when (selectedTab) {
                AppTab.Create -> CreateTab(
                    state = state,
                    onModelChange = viewModel::setModel,
                    onPromptChange = viewModel::setPrompt,
                    onPickImages = { pickImages.launch("image/*") },
                    onOpenUrlDialog = { showUrlDialog = true },
                    onClearReferences = viewModel::clearReferences,
                    onMoveReference = viewModel::moveReference,
                    onDeleteReference = viewModel::deleteReference,
                    onDeleteMultipleReferences = viewModel::deleteMultipleReferences,
                    onPreview = viewModel::openImage,
                    onSend = { viewModel.send(context) },
                    onStop = { viewModel.stop(context) },
                    onRetry = { viewModel.retryLast(context) },
                    onSaveResult = viewModel::saveResultImage,
                    onGoResults = { selectedTab = AppTab.Results }
                )

                AppTab.Results -> ResultsTab(
                    state = state,
                    onPreview = viewModel::openImage,
                    onSave = viewModel::saveResultImage
                )

                AppTab.History -> HistoryTab(
                    state = state,
                    viewModel = viewModel,
                    onSearch = viewModel::setHistorySearch,
                    onLoadMore = viewModel::loadMoreHistory,
                    onClearAll = { confirmClearHistory = true },
                    onDownloadSelected = viewModel::saveHistoryImages,
                    onDeleteSelected = viewModel::deleteHistoryItems,
                    onPreview = viewModel::openImage,
                    onCopyPrompt = { prompt ->
                        clipboard.setText(AnnotatedString(prompt))
                        viewModel.notifyPromptCopied()
                    },
                    onCopyLinks = { links ->
                        clipboard.setText(AnnotatedString(links.joinToString("\n")))
                        viewModel.notifyHistoryLinksCopied(links.size)
                    }
                )

                AppTab.Debug -> DebugTab(
                    state = state,
                    onLoggingChange = viewModel::setLoggingEnabled,
                    onLoadLog = viewModel::loadLogContent,
                    onClearLog = viewModel::clearLog,
                    onExportLog = { viewModel.exportLog(context) }
                )
            }
        }
    }

    if (showApiDialog) {
        ApiDialog(
            state = state,
            onDismiss = { showApiDialog = false },
            onPaste = { viewModel.pasteApiKey(clipboard.getText()?.text) },
            onToggleMask = viewModel::toggleMask,
            onSaveKey = viewModel::saveApiKey,
            onClearKey = viewModel::clearApiKey,
            onTestNetwork = viewModel::testNetworkLatency,
            onApiKeyChange = viewModel::setApiKey,
            onEndpointChange = viewModel::setEndpoint
        )
    }

    if (showParamsDialog) {
        ParamsDialog(
            state = state,
            onDismiss = { showParamsDialog = false },
            onSizeChange = viewModel::setSize,
            onSeedChange = viewModel::setSeed,
            onFormatChange = viewModel::setResponseFormat,
            onOutputFormatChange = viewModel::setOutputFormat,
            onWatermarkChange = viewModel::setWatermark,
            onStreamChange = viewModel::setStream,
            onSequentialChange = viewModel::setSequentialMode,
            onMaxImagesChange = viewModel::setMaxImages,
            onWebSearchChange = viewModel::setWebSearch,
            onExternalSearchChange = viewModel::setExternalSearch,
            onSearchProviderChange = viewModel::setSearchProvider,
            onSearchApiKeyChange = viewModel::setSearchApiKey,
            onSaveSearchApiKey = viewModel::saveSearchApiKey,
            onClearSearchApiKey = viewModel::clearSearchApiKey
        )
    }

    if (showUrlDialog) {
        UrlImagesDialog(
            text = state.urlImagesText,
            onTextChange = viewModel::setUrlImagesText,
            onDismiss = { showUrlDialog = false }
        )
    }

    state.fullScreenImage?.let { src ->
        Dialog(onDismissRequest = viewModel::closeImage) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .clickable { viewModel.closeImage() }
                    .padding(8.dp)
            ) {
                AsyncImage(
                    model = src,
                    contentDescription = "预览大图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 260.dp, max = 680.dp)
                )
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("清空全部历史记录") },
            text = { Text("此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearHistory = false
                        viewModel.clearHistory()
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("取消") }
            }
        )
    }

    if (showBackupDialog) {
        BackupDialog(
            state = state,
            onDismiss = { showBackupDialog = false },
            onBackup = { createBackupLauncher.launch("SD302-backup-${System.currentTimeMillis()}.zip") },
            onRestore = { openBackupLauncher.launch(arrayOf("application/zip")) },
            onRestoreConfirm = { pendingRestoreUri = it }
        )
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("一键还原") },
            text = { Text("还原会覆盖当前的历史记录、设置和 API Key（恢复为备份中的内容）。确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        viewModel.restoreFromUri(uri, context)
                    }
                ) { Text("还原") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CreateTab(
    state: SeedreamUiState,
    onModelChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onPickImages: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onClearReferences: () -> Unit,
    onMoveReference: (String, Int) -> Unit,
    onDeleteReference: (String) -> Unit,
    onDeleteMultipleReferences: (List<String>) -> Unit,
    onPreview: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onSaveResult: (String) -> Unit,
    onGoResults: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusPanel(state = state, onRetry = onRetry)

        SurfacePanel {
            OptionDropdown(
                label = "模型",
                value = state.model,
                options = listOf(
                    MODEL_SEEDREAM_5_PRO to "Seedream 5.0 Pro",
                    MODEL_SEEDREAM_5 to "Seedream 5.0",
                    MODEL_SEEDREAM_4_5 to "Seedream 4.5"
                ),
                onChange = onModelChange
            )
            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = { Text("Prompt") },
                minLines = 4,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }

        ReferenceStrip(
            references = state.references,
            onPickImages = onPickImages,
            onOpenUrlDialog = onOpenUrlDialog,
            onClearReferences = onClearReferences,
            onMoveReference = onMoveReference,
            onDeleteReference = onDeleteReference,
            onDeleteMultipleReferences = onDeleteMultipleReferences,
            onPreview = onPreview,
            modifier = Modifier.weight(1f)
        )

        state.resultImages.lastOrNull()?.let { image ->
            RecentResult(
                image = image,
                onPreview = onPreview,
                onSave = onSaveResult,
                onGoResults = onGoResults
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSend,
                enabled = !state.isGenerating,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("发送")
            }
            OutlinedButton(
                onClick = onStop,
                enabled = state.isGenerating,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("停止")
            }
        }
    }
}

@Composable
private fun StatusPanel(state: SeedreamUiState, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.status,
                    color = statusColor(state.statusKind),
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (state.isGenerating) {
                    Text("防休眠", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
            state.retryMessage?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 2)
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReferenceStrip(
    references: List<ReferenceImage>,
    onPickImages: () -> Unit,
    onOpenUrlDialog: () -> Unit,
    onClearReferences: () -> Unit,
    onMoveReference: (String, Int) -> Unit,
    onDeleteReference: (String) -> Unit,
    onDeleteMultipleReferences: (List<String>) -> Unit,
    onPreview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    val currentIds = remember(references) { references.map { it.id }.toSet() }
    LaunchedEffect(currentIds) {
        selectedIds = selectedIds.intersect(currentIds)
        if (currentIds.isEmpty()) selectionMode = false
    }

    SurfacePanel(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("参考图", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (selectionMode) "已选 ${selectedIds.size}/${references.size} 张" else "${references.size} 张",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectionMode) {
                IconButton(onClick = {
                    onDeleteMultipleReferences(selectedIds.toList())
                    selectedIds = emptySet()
                    selectionMode = false
                }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = "删除已选", tint = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = {
                    selectedIds = if (selectedIds.size == references.size) emptySet() else references.map { it.id }.toSet()
                }) {
                    Text(if (selectedIds.size == references.size) "取消" else "全选")
                }
                TextButton(onClick = {
                    selectedIds = emptySet()
                    selectionMode = false
                }) {
                    Text("完成")
                }
            } else {
                TextButton(onClick = { selectionMode = true }, enabled = references.isNotEmpty()) {
                    Text("选择")
                }
            }
            IconButton(onClick = onPickImages) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "本地图")
            }
            IconButton(onClick = onOpenUrlDialog) {
                Icon(Icons.Default.ContentPaste, contentDescription = "URL 图")
            }
            IconButton(onClick = onClearReferences, enabled = references.isNotEmpty()) {
                Icon(Icons.Default.Clear, contentDescription = "清空参考图")
            }
        }

        if (references.isEmpty()) {
            EmptyText("点击图片或粘贴 URL 添加参考图。")
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                itemsIndexed(references, key = { _, item -> item.id }) { index, item ->
                    ReferenceTile(
                        index = index,
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in selectedIds,
                        onSelectedChange = { checked ->
                            selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                        },
                        canMoveUp = index > 0,
                        canMoveDown = index < references.lastIndex,
                        onMove = onMoveReference,
                        onDelete = {
                            onDeleteReference(item.id)
                            selectedIds = selectedIds - item.id
                        },
                        onPreview = onPreview
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceTile(
    index: Int,
    item: ReferenceImage,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onPreview: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(132.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = item.preview,
                    contentDescription = "参考图 ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (selectionMode) {
                                onSelectedChange(!selected)
                            } else {
                                onPreview(item.preview)
                            }
                        }
                )
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onSelectedChange,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                    )
                }
            }
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { onMove(item.id, -1) }, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "前移")
                }
                IconButton(onClick = { onMove(item.id, 1) }, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "后移")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun RecentResult(
    image: ResultImage,
    onPreview: (String) -> Unit,
    onSave: (String) -> Unit,
    onGoResults: () -> Unit
) {
    SurfacePanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = image.src,
                contentDescription = "最近结果",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPreview(image.src) }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("最近结果", fontWeight = FontWeight.SemiBold)
                Text(
                    image.note.ifBlank { "已生成" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onPreview(image.src) }) {
                Icon(Icons.Default.OpenInFull, contentDescription = "预览")
            }
            IconButton(onClick = { onSave(image.src) }) {
                Icon(Icons.Default.Download, contentDescription = "保存")
            }
            TextButton(onClick = onGoResults) { Text("全部") }
        }
    }
}

@Composable
private fun ResultsTab(
    state: SeedreamUiState,
    onPreview: (String) -> Unit,
    onSave: (String) -> Unit
) {
    if (state.resultImages.isEmpty()) {
        CenterMessage("暂无结果", "生成完成后会显示在这里。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        itemsIndexed(state.resultImages, key = { _, image -> image.id }) { index, image ->
            ResultImageCard(index = index, image = image, onPreview = onPreview, onSave = onSave)
        }
    }
}

@Composable
private fun ResultImageCard(
    index: Int,
    image: ResultImage,
    onPreview: (String) -> Unit,
    onSave: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            AsyncImage(
                model = image.src,
                contentDescription = "结果图片 ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 460.dp)
                    .background(Color(0xFF020617))
                    .clickable { onPreview(image.src) }
            )
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${index + 1} ${image.note}",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { onPreview(image.src) }) {
                    Icon(Icons.Default.OpenInFull, contentDescription = "预览")
                }
                IconButton(onClick = { onSave(image.src) }) {
                    Icon(Icons.Default.Download, contentDescription = "保存")
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(
    state: SeedreamUiState,
    viewModel: SeedreamViewModel,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onClearAll: () -> Unit,
    onDownloadSelected: (List<HistoryEntity>) -> Unit,
    onDeleteSelected: (List<HistoryEntity>) -> Unit,
    onPreview: (String) -> Unit,
    onCopyPrompt: (String) -> Unit,
    onCopyLinks: (List<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val visibleItems = state.history
    val currentIds = remember(visibleItems) { visibleItems.map { it.id }.toSet() }
    LaunchedEffect(currentIds) {
        selectedIds = selectedIds.intersect(currentIds)
    }
    val selectedItems = visibleItems.filter { it.id in selectedIds }
    val selectedLinks = selectedItems.mapNotNull { it.originalLink() }
    val canLoadMore = visibleItems.size < state.historyTotalCount

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SurfacePanel {
            OutlinedTextField(
                value = state.historySearch,
                onValueChange = onSearch,
                label = { Text("搜索 Prompt") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.historySearch.isBlank()) {
                        "已加载 ${visibleItems.size}/${state.historyTotalCount} 条"
                    } else {
                        "匹配 ${state.historyTotalCount} 条，已加载 ${visibleItems.size}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (visibleItems.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedItems.size == visibleItems.size) {
                                emptySet()
                            } else {
                                visibleItems.map { it.id }.toSet()
                            }
                        }
                    ) {
                        Text(if (selectedItems.size == visibleItems.size) "取消全选" else "全选")
                    }
                }
                OutlinedButton(
                    onClick = onClearAll,
                    enabled = state.history.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("清空")
                }
            }
            if (selectedItems.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onDownloadSelected(selectedItems) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("下载 ${selectedItems.size}")
                    }
                    OutlinedButton(
                        onClick = { onCopyLinks(selectedLinks) },
                        enabled = selectedLinks.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("链接 ${selectedLinks.size}")
                    }
                    OutlinedButton(
                        onClick = {
                            onDeleteSelected(selectedItems)
                            selectedIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }

        if (visibleItems.isEmpty() && !state.historyLoading) {
            CenterMessage(
                if (state.historySearch.isBlank()) "暂无历史" else "没有匹配历史",
                if (state.historySearch.isBlank()) "生成图片后会自动缓存到历史。" else "换个关键词再试试。",
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleItems, key = { it.id }) { item ->
                    HistoryItemRow(
                        item = item,
                        imageSrc = viewModel.historyDisplaySource(item),
                        selected = item.id in selectedIds,
                        onSelectedChange = { checked ->
                            selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                        },
                        onPreview = onPreview,
                        onSave = { viewModel.saveHistoryImage(item) },
                        onCopyPrompt = onCopyPrompt,
                        onCopyLink = { link -> onCopyLinks(listOf(link)) },
                        onDelete = { viewModel.deleteHistory(item) }
                    )
                }
                item {
                    if (state.historyLoading && visibleItems.isEmpty()) {
                        Text(
                            "正在加载历史...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    } else if (canLoadMore) {
                        OutlinedButton(
                            onClick = onLoadMore,
                            enabled = !state.historyLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.historyLoading) "加载中..." else "加载更多")
                        }
                    } else if (state.historyTotalCount > 0) {
                        Text(
                            "已显示全部 ${state.historyTotalCount} 条",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: HistoryEntity,
    imageSrc: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onPreview: (String) -> Unit,
    onSave: () -> Unit,
    onCopyPrompt: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onDelete: () -> Unit
) {
    val originalLink = item.originalLink()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)
        AsyncImage(
            model = imageSrc,
            contentDescription = item.prompt.ifBlank { "历史图片" },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onPreview(imageSrc) }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.model, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.prompt.ifBlank { "(无 Prompt)" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (originalLink != null) "原始链接可用" else "仅本地缓存",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (originalLink != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onSave) {
            Icon(Icons.Default.Download, contentDescription = "下载")
        }
        IconButton(onClick = { onCopyPrompt(item.prompt) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制 Prompt")
        }
        IconButton(onClick = { originalLink?.let(onCopyLink) }, enabled = originalLink != null) {
            Icon(Icons.Default.OpenInFull, contentDescription = "复制原始链接")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除")
        }
    }
}

@Composable
private fun DebugTab(
    state: SeedreamUiState,
    onLoggingChange: (String) -> Unit,
    onLoadLog: () -> Unit,
    onClearLog: () -> Unit,
    onExportLog: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SurfacePanel {
            Text("请求体预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CodeBlock(state.payloadPreview, modifier = Modifier.heightIn(min = 110.dp, max = 220.dp))
        }
        if (state.searchSummary.isNotBlank()) {
            SurfacePanel {
                Text("外部搜索摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                CodeBlock(state.searchSummary, modifier = Modifier.heightIn(min = 90.dp, max = 180.dp))
            }
        }
        SurfacePanel {
            Text("实时日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OptionDropdown(
                "日志记录",
                state.loggingEnabled,
                listOf("false" to "关闭", "true" to "开启"),
                onLoggingChange
            )
            Text(
                "开启后会将运行日志（含停止/取消/崩溃前及崩溃瞬间的信息）实时写入本地文件，用于排查问题。关闭时不再写入。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(state.logFileInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompactButtonRow {
                ToolButton("刷新", Icons.Default.Refresh, onLoadLog)
                ToolButton("清空", Icons.Default.Delete, onClearLog, danger = true)
                ToolButton("导出", Icons.Default.Download, onExportLog)
            }
            if (state.logContent.isNotBlank()) {
                CodeBlock(state.logContent, modifier = Modifier.heightIn(min = 200.dp, max = 320.dp))
            } else {
                Text(
                    "（暂无日志内容，开启日志记录后在此查看）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        SurfacePanel(modifier = Modifier.weight(1f)) {
            Text("原始返回", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CodeBlock(state.rawResponse, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ApiDialog(
    state: SeedreamUiState,
    onDismiss: () -> Unit,
    onPaste: () -> Unit,
    onToggleMask: () -> Unit,
    onSaveKey: () -> Unit,
    onClearKey: () -> Unit,
    onTestNetwork: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onEndpointChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    visualTransformation = if (state.keyMasked) PasswordVisualTransformation() else VisualTransformation.None,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                CompactButtonRow {
                    ToolButton("粘贴", Icons.Default.ContentPaste, onPaste)
                    ToolButton(if (state.keyMasked) "显示" else "隐藏", if (state.keyMasked) Icons.Default.Visibility else Icons.Default.VisibilityOff, onToggleMask)
                    ToolButton("保存", Icons.Default.Save, onSaveKey)
                    ToolButton("清空", Icons.Default.Clear, onClearKey, danger = true)
                }
                OutlinedTextField(
                    value = state.endpoint,
                    onValueChange = onEndpointChange,
                    label = { Text("接口地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(onClick = onTestNetwork, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("测试延迟")
                }
                if (state.netResult.isNotBlank()) {
                    Text(state.netResult, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun BackupDialog(
    state: SeedreamUiState,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreConfirm: (Uri) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("一键备份 / 还原") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "备份：将图片缓存、历史记录（链接+提示词）、设置和 API Key 打包为 zip 文件导出。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "注意：备份文件包含 API Key 明文，请妥善保管，勿分享给他人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                CompactButtonRow {
                    ToolButton("备份", Icons.Default.Backup, onBackup)
                    ToolButton("还原", Icons.Default.Restore, onRestore, danger = true)
                }
                if (state.backupBusy) {
                    Text("正在处理...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (state.backupStatus.isNotBlank()) {
                    Text(state.backupStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun ParamsDialog(
    state: SeedreamUiState,
    onDismiss: () -> Unit,
    onSizeChange: (String) -> Unit,
    onSeedChange: (String) -> Unit,
    onFormatChange: (String) -> Unit,
    onOutputFormatChange: (String) -> Unit,
    onWatermarkChange: (String) -> Unit,
    onStreamChange: (String) -> Unit,
    onSequentialChange: (String) -> Unit,
    onMaxImagesChange: (String) -> Unit,
    onWebSearchChange: (String) -> Unit,
    onExternalSearchChange: (String) -> Unit,
    onSearchProviderChange: (String) -> Unit,
    onSearchApiKeyChange: (String) -> Unit,
    onSaveSearchApiKey: () -> Unit,
    onClearSearchApiKey: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("高级参数") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 560.dp)
            ) {
                item { OptionDropdown("尺寸 size", state.size, listOf("" to "默认", "2K" to "2K", "4K" to "4K"), onSizeChange) }
                item {
                    OutlinedTextField(
                        value = state.seed,
                        onValueChange = onSeedChange,
                        label = { Text("seed（-1~2147483647）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item { OptionDropdown("response_format", state.responseFormat, listOf("url" to "url", "b64_json" to "b64_json"), onFormatChange) }
                if (supportsOutputFormat(state.model)) {
                    item {
                        OptionDropdown(
                            "output_format（5.0 / 5.0 Pro）",
                            state.outputFormat,
                            listOf("" to "默认", "jpeg" to "jpeg", "png" to "png"),
                            onOutputFormatChange
                        )
                    }
                }
                item { OptionDropdown("watermark", state.watermark, listOf("" to "默认", "false" to "false", "true" to "true"), onWatermarkChange) }
                if (supportsStream(state.model)) {
                    item {
                        OptionDropdown(
                            "stream（流式）",
                            state.stream,
                            listOf("false" to "false", "true" to "true"),
                            onStreamChange
                        )
                    }
                }
                item {
                    OptionDropdown(
                        "sequential_image_generation（组图）",
                        state.sequentialMode,
                        listOf("" to "默认", "auto" to "auto", "disabled" to "disabled"),
                        onSequentialChange
                    )
                }
                item {
                    OutlinedTextField(
                        value = state.maxImages,
                        onValueChange = onMaxImagesChange,
                        label = { Text("max_images（1-15）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (supportsWebSearch(state.model)) {
                    item {
                        OptionDropdown(
                            "联网搜索（5.0 / 5.0 Pro，写入 tools）",
                            state.webSearch,
                            listOf("false" to "关闭", "true" to "开启：tools[{type=web_search}]"),
                            onWebSearchChange
                        )
                    }
                    item {
                        Text(
                            "开启后请求体会加入 tools: [{ type: \"web_search\" }]；模型会根据提示词自主判断是否搜索，可能增加延迟和费用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    OptionDropdown(
                        "外部联网搜索（发送前强制检索）",
                        state.externalSearch,
                        listOf("false" to "关闭", "true" to "开启"),
                        onExternalSearchChange
                    )
                }
                if (state.externalSearch == "true") {
                    item {
                        OptionDropdown(
                            "搜索服务",
                            state.searchProvider,
                            SearchProvider.entries.map { it.id to it.label },
                            onSearchProviderChange
                        )
                    }
                    item {
                        val provider = SearchProvider.fromId(state.searchProvider)
                        if (provider.requiresApiKey) {
                            OutlinedTextField(
                                value = state.searchApiKey,
                                onValueChange = onSearchApiKeyChange,
                                label = { Text("${provider.label} API Key") },
                                visualTransformation = PasswordVisualTransformation(),
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            CompactButtonRow {
                                ToolButton("保存搜索 Key", Icons.Default.Save, onSaveSearchApiKey)
                                ToolButton("清空", Icons.Default.Clear, onClearSearchApiKey, danger = true)
                            }
                        } else {
                            Text(
                                "DuckDuckGo 使用公开 Instant Answer API，不需要 API Key；它不是完整搜索结果 API，结果可能较少。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item {
                        Text(
                            "外部搜索会先把检索摘要追加到 Prompt，再发送给 302.ai 生图接口；这比模型 tools 更可控。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.searchSummary.isNotBlank()) {
                        item {
                            CodeBlock(state.searchSummary, modifier = Modifier.heightIn(min = 80.dp, max = 160.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun UrlImagesDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("URL 参考图") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("每行一个图片 URL") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun SurfacePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun OptionDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "默认" }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Text(display, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        expanded = false
                        onChange(optionValue)
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RowScope.ToolButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = if (danger) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CenterMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CodeBlock(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF020617))
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(10.dp),
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun statusColor(kind: StatusKind): Color {
    return when (kind) {
        StatusKind.Ok -> MaterialTheme.colorScheme.primary
        StatusKind.Error -> MaterialTheme.colorScheme.error
        StatusKind.Warn -> MaterialTheme.colorScheme.tertiary
        StatusKind.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusKind.Normal -> MaterialTheme.colorScheme.onSurface
    }
}

private fun HistoryEntity.originalLink(): String? {
    return source.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
}
