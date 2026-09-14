package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FamilyMemory
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items

@Composable
fun FamilyTimelineSection(
    memories: List<FamilyMemory>,
    isSyncing: Boolean = false,
    highlightedMemoryId: Long? = null,
    onClearHighlight: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onAddMemory: (
        title: String,
        dateString: String,
        location: String,
        content: String,
        photoUri: String?,
        tag: String,
        mediaType: String,
        videoUri: String?
    ) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var playingVideoUri by remember { mutableStateOf<String?>(null) }
    var selectedFilterTag by remember { mutableStateOf("全部") }

    // If a specific memory is targeted for highlight, ensure filter doesn't hide it
    androidx.compose.runtime.LaunchedEffect(highlightedMemoryId) {
        if (highlightedMemoryId != null) {
            selectedFilterTag = "全部"
        }
    }

    // Aggregate tags from memories + default common tags
    val availableFilterTags = remember(memories) {
        val tagList = mutableListOf("全部", "聚餐合影", "出去玩/出游", "全家福", "老家日常", "温馨视频")
        memories.forEach { m ->
            if (m.tag.isNotBlank() && !tagList.contains(m.tag)) {
                tagList.add(m.tag)
            }
        }
        tagList
    }

    // Filter memories by tag
    val filteredMemories = remember(memories, selectedFilterTag) {
        if (selectedFilterTag == "全部") {
            memories
        } else {
            memories.filter { memory ->
                val mTag = memory.tag
                when (selectedFilterTag) {
                    "聚餐合影" -> mTag == "聚餐合影" || mTag.contains("聚餐") || mTag.contains("合影") || mTag.contains("餐")
                    "出去玩/出游" -> mTag == "出去玩/出游" || mTag.contains("玩") || mTag.contains("出游") || mTag.contains("旅行") || mTag.contains("出街")
                    "全家福" -> mTag == "全家福" || mTag.contains("全家") || mTag.contains("合照")
                    "老家日常" -> mTag == "老家日常" || mTag.contains("日常") || mTag.contains("老家")
                    "温馨视频" -> mTag == "温馨视频" || memory.mediaType == "VIDEO" || !memory.videoUri.isNullOrBlank()
                    else -> mTag.equals(selectedFilterTag, ignoreCase = true) || mTag.contains(selectedFilterTag)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WarmAmberPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "聚餐合照与时光轴",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "定格每一次相聚 · 图文视频实时同步",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Real-time sync button
                OutlinedButton(
                    onClick = onSyncNow,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("timeline_sync_btn")
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
                    val angle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(1000)),
                        label = "spin_angle"
                    )
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier
                            .size(15.dp)
                            .then(if (isSyncing) Modifier.rotate(angle) else Modifier),
                        tint = WarmAmberPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSyncing) "同步中" else "实时同步",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = WarmAmberPrimary
                    )
                }

                // Add photo/video button
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_memory_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("上传记录", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tag Filter Chips Bar (Horizontal Scrollable)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableFilterTags) { tagItem ->
                val isSelected = (tagItem == selectedFilterTag)
                val countForTag = if (tagItem == "全部") memories.size else {
                    memories.count { m ->
                        val mTag = m.tag
                        when (tagItem) {
                            "聚餐合影" -> mTag == "聚餐合影" || mTag.contains("聚餐") || mTag.contains("合影") || mTag.contains("餐")
                            "出去玩/出游" -> mTag == "出去玩/出游" || mTag.contains("玩") || mTag.contains("出游") || mTag.contains("旅行") || mTag.contains("出街")
                            "全家福" -> mTag == "全家福" || mTag.contains("全家") || mTag.contains("合照")
                            "老家日常" -> mTag == "老家日常" || mTag.contains("日常") || mTag.contains("老家")
                            "温馨视频" -> mTag == "温馨视频" || m.mediaType == "VIDEO" || !m.videoUri.isNullOrBlank()
                            else -> mTag.equals(tagItem, ignoreCase = true) || mTag.contains(tagItem)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedFilterTag = tagItem }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val iconStr = when (tagItem) {
                            "全部" -> "✨"
                            "聚餐合影" -> "🍲"
                            "出去玩/出游" -> "🚗"
                            "全家福" -> "👨‍👩‍👧"
                            "老家日常" -> "🏡"
                            "温馨视频" -> "🎬"
                            else -> "🏷️"
                        }
                        Text(text = iconStr, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (countForTag > 0) "$tagItem ($countForTag)" else tagItem,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredMemories.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (selectedFilterTag == "全部") "暂无时光轴记忆，点击右上角“上传记录”记录合照或温馨视频吧～"
                               else "暂无「$selectedFilterTag」相关的记忆，点击右上角上传属于这个分类的温馨瞬间吧～",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                filteredMemories.forEachIndexed { index, memory ->
                    TimelineItemCard(
                        memory = memory,
                        isFirst = index == 0,
                        isHighlighted = (memory.id == highlightedMemoryId),
                        onClearHighlight = onClearHighlight,
                        onDelete = { onDeleteMemory(memory.id) },
                        onPlayVideo = { uri -> playingVideoUri = uri }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, dateStr, loc, content, photoUri, tag, mediaType, videoUri ->
                onAddMemory(title, dateStr, loc, content, photoUri, tag, mediaType, videoUri)
                showAddDialog = false
            }
        )
    }

    playingVideoUri?.let { uri ->
        VideoPlayerDialog(
            videoUri = uri,
            onDismiss = { playingVideoUri = null }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineItemCard(
    memory: FamilyMemory,
    isFirst: Boolean,
    isHighlighted: Boolean = false,
    onClearHighlight: () -> Unit = {},
    onDelete: () -> Unit,
    onPlayVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVideo = memory.mediaType == "VIDEO" || !memory.videoUri.isNullOrBlank()
    val playableUri = memory.videoUri ?: memory.photoUri

    val bringIntoViewRequester = androidx.compose.runtime.remember { BringIntoViewRequester() }

    androidx.compose.runtime.LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // Smoothly scroll directly to the memory item
            kotlinx.coroutines.delay(100)
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (_: Exception) {}
            // Reset highlight flag after reaching position
            kotlinx.coroutines.delay(600)
            onClearHighlight()
        }
    }

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .testTag("memory_card_${memory.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Media display: photo or video thumbnail with play overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color(0xFF1E1E1E))
                    .then(
                        if (isVideo && !playableUri.isNullOrBlank()) {
                            Modifier.clickable { onPlayVideo(playableUri) }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!memory.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = memory.photoUri,
                        contentDescription = memory.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (isFirst) {
                    // Feature the warm dining hero banner for the first seed memory
                    Image(
                        painter = painterResource(id = R.drawable.img_family_warm_home),
                        contentDescription = "Family home dinner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2C3E50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // If it's a video, render play overlay
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放视频",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Video badge in top right
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "视频回忆",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVideo) CozySage.copy(alpha = 0.15f) else WarmAmberPrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = memory.tag,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isVideo) CozySage else WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = memory.dateString,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = memory.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (memory.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = CozySage,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = memory.location,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = CozySage,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                if (memory.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = memory.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        )
                    )
                }

                // If it's a video, add an explicit play button bar
                if (isVideo && !playableUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onPlayVideo(playableUri) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "点击播放温馨视频",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = WarmAmberPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * In-app Video Player Dialog with system playback fallback
 */
@Composable
fun VideoPlayerDialog(
    videoUri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎬 温馨视频播放",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val uri = if (videoUri.startsWith("http") || videoUri.startsWith("content")) {
                                    Uri.parse(videoUri)
                                } else {
                                    Uri.fromFile(File(videoUri))
                                }
                                setVideoURI(uri)
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    false
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "轻触画面可唤起暂停/进度条",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    )

                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                val uri = if (videoUri.startsWith("content") || videoUri.startsWith("http")) {
                                    Uri.parse(videoUri)
                                } else {
                                    val f = File(videoUri)
                                    val uriForFile = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        f
                                    )
                                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    uriForFile
                                }
                                intent.setDataAndType(uri, "video/*")
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // Fallback directly with file URI if FileProvider not registered
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.setDataAndType(Uri.parse(videoUri), "video/*")
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "系统播放器",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = WarmAmberPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog to add memory: Supports both Photos and Videos seamlessly!
 */
@Composable
fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        dateStr: String,
        location: String,
        content: String,
        photoUri: String?,
        tag: String,
        mediaType: String,
        videoUri: String?
    ) -> Unit
) {
    var selectedMediaType by remember { mutableStateOf("IMAGE") } // "IMAGE" or "VIDEO"
    var title by remember { mutableStateOf("") }
    val sdf = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)
    var dateStr by remember { mutableStateOf(sdf.format(Date())) }
    var location by remember { mutableStateOf("老家餐厅") }
    var content by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("聚餐合影") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Android Photo Picker launcher for Images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri
            selectedVideoUri = null
            selectedMediaType = "IMAGE"
        }
    }

    // Android Photo Picker launcher for Videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedPhotoUri = null
            selectedMediaType = "VIDEO"
            if (tag == "聚餐合影") tag = "温馨视频"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (selectedMediaType == "VIDEO") "上传家庭温馨视频" else "上传聚餐/合影时光记忆",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Media Type Segmented Tabs: Photo vs Video
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedMediaType == "IMAGE") WarmAmberPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedMediaType = "IMAGE" }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = if (selectedMediaType == "IMAGE") Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "合照/图片",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selectedMediaType == "IMAGE") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedMediaType == "VIDEO") WarmAmberPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedMediaType = "VIDEO"
                                if (tag == "聚餐合影") tag = "温馨视频"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (selectedMediaType == "VIDEO") Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "温馨视频",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (selectedMediaType == "VIDEO") Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Media selection preview box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clickable {
                            if (selectedMediaType == "VIDEO") {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            } else {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        }
                ) {
                    if (selectedMediaType == "IMAGE" && selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = "Selected photo",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (selectedMediaType == "VIDEO" && selectedVideoUri != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF212529))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(WarmAmberPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "已选取视频：${selectedVideoUri?.lastPathSegment?.takeLast(20) ?: "视频文件"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "点击可重新选择视频",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selectedMediaType == "VIDEO") Icons.Default.Videocam else Icons.Default.PhotoCamera,
                                contentDescription = "Add media",
                                tint = WarmAmberPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selectedMediaType == "VIDEO") "点击选取手机中的温馨视频短片" else "点击选择聚餐照片或全家福",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            )
                            Text(
                                text = if (selectedMediaType == "VIDEO") "支持视频自动提取封面并跨手机实时同步" else "支持高清压缩跨手机实时同步",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("记忆主题 (如: 中秋聚餐、妈妈拿手菜、院子小憩)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_title_input")
                )

                // Tags based on media type
                val tagOptions = if (selectedMediaType == "VIDEO") {
                    listOf("温馨视频", "家庭聚餐", "出去玩/出游", "欢声笑语")
                } else {
                    listOf("聚餐合影", "出去玩/出游", "全家福", "老家日常")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tagOptions.forEach { t ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (tag == t) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { tag = t }
                        ) {
                            Text(
                                text = t,
                                color = if (tag == t) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("地点 (如: 老家餐厅、小院)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("记下温馨的一刻与好吃的菜品...") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title,
                            dateStr,
                            location,
                            content,
                            selectedPhotoUri?.toString(),
                            tag,
                            selectedMediaType,
                            selectedVideoUri?.toString()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                modifier = Modifier.testTag("submit_memory_btn")
            ) {
                Text("保存时光轴")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
