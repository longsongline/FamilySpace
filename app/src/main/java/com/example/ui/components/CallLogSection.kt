package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallRecord
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun CallLogSection(
    calls: List<CallRecord>,
    onAddCall: (person: String, durationMinutes: Int, topics: String, audioUri: String?, audioDurationSeconds: Int) -> Unit,
    onDeleteCall: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val daysSinceLastCall = if (calls.isNotEmpty()) {
        val latest = calls.first().callTimestamp
        val diffMs = System.currentTimeMillis() - latest
        TimeUnit.MILLISECONDS.toDays(diffMs).toInt().coerceAtLeast(0)
    } else {
        0
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_log_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8F5E9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneInTalk,
                            contentDescription = "Calls",
                            tint = CozySage,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "亲情通话与关怀提醒",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "常打电话常牵挂，听听爸妈的声音",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_call_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("记一次通话", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Reminder pill if > 4 days
            if (daysSinceLastCall >= 4) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧡 已经有 $daysSinceLastCall 天没跟爸妈通电话啦，今晚洗漱后给妈妈拨个视频吧～",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = WarmAmberPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Calls list
            if (calls.isEmpty()) {
                Text(
                    text = "暂无通话记录，点击右上角“记一次通话”记录温馨交谈吧～",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                val latest = calls.first()
                CallItem(call = latest, onDelete = { onDeleteCall(latest.id) })

                if (calls.size > 1) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isExpanded) "收起更多通话" else "查看往期 ${calls.size - 1} 条通话记录")
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            calls.drop(1).forEach { record ->
                                CallItem(call = record, onDelete = { onDeleteCall(record.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCallDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { person, dur, topics, audioUri, audioDuration ->
                onAddCall(person, dur, topics, audioUri, audioDuration)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CallItem(
    call: CallRecord,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CozySage.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "与${call.person}通话",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = CozySage,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${call.durationMinutes} 分钟",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = call.topics,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
            )

            // Audio Player if audio recorded/uploaded
            if (!call.audioUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AudioVoicePlayer(
                    audioUri = call.audioUri,
                    totalDurationSec = call.audioDurationSeconds
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = call.dateString,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun AudioVoicePlayer(
    audioUri: String,
    totalDurationSec: Int
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentSec by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUri) {
        onDispose {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && mediaPlayer != null) {
                try {
                    val mp = mediaPlayer
                    if (mp != null && mp.isPlaying) {
                        val pos = mp.currentPosition
                        val dur = mp.duration.coerceAtLeast(1)
                        currentProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        currentSec = (pos / 1000).coerceAtLeast(0)
                    }
                } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = WarmAmberPrimary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        try {
                            if (mediaPlayer == null) {
                                val mp = MediaPlayer()
                                if (audioUri.startsWith("/")) {
                                    mp.setDataSource(audioUri)
                                } else {
                                    mp.setDataSource(context, Uri.parse(audioUri))
                                }
                                mp.setOnCompletionListener {
                                    isPlaying = false
                                    currentProgress = 0f
                                    currentSec = 0
                                }
                                mp.prepare()
                                mediaPlayer = mp
                            }
                            mediaPlayer?.start()
                            isPlaying = true
                        } catch (e: Exception) {
                            android.util.Log.e("AudioVoicePlayer", "Failed to play audio", e)
                            isPlaying = false
                        }
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = WarmAmberPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "亲情通话录音 / 语音留言",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = WarmAmberPrimary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    val durText = if (totalDurationSec > 0) {
                        val m = totalDurationSec / 60
                        val s = totalDurationSec % 60
                        String.format("%02d:%02d", m, s)
                    } else {
                        "音频"
                    }
                    val curText = String.format("%02d:%02d", currentSec / 60, currentSec % 60)
                    Text(
                        text = if (isPlaying) "$curText / $durText" else durText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.5.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = WarmAmberPrimary,
                    trackColor = WarmAmberPrimary.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun AddCallDialog(
    onDismiss: () -> Unit,
    onConfirm: (person: String, durationMinutes: Int, topics: String, audioUri: String?, audioDuration: Int) -> Unit
) {
    val context = LocalContext.current
    var person by remember { mutableStateOf("妈妈") }
    var durationText by remember { mutableStateOf("25") }
    var topics by remember { mutableStateOf("") }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioName by remember { mutableStateOf("") }
    var selectedAudioDuration by remember { mutableIntStateOf(0) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAudioUri = uri
            selectedAudioName = "已选择通话录音音频文件"
            // Measure duration
            try {
                val (tempFile, dur) = com.example.util.FamilyMediaStorage.copyAudioUriToLocalStorage(context, uri)
                if (tempFile != null) {
                    selectedAudioDuration = dur
                }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录亲情通话") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("通话对象：", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("妈妈", "爸爸", "父母全家").forEach { p ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (person == p) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { person = p }
                        ) {
                            Text(
                                text = p,
                                color = if (person == p) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("通话时长 (分钟)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text("聊了什么 (如: 添衣保暖、工作进展、老家趣事)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("call_topics_input")
                )

                // Audio upload / voice memo section
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🎙️ 通话录音 / 语音留言 (可选)：",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (selectedAudioUri != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WarmAmberPrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = WarmAmberPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (selectedAudioDuration > 0) "录音 (${selectedAudioDuration}秒)" else "录音音频已就绪",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = WarmAmberPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        selectedAudioUri = null
                                        selectedAudioName = ""
                                        selectedAudioDuration = 0
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Audio",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { audioPickerLauncher.launch("audio/*") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = WarmAmberPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "选择上传通话录音 / 语音音频",
                                    style = MaterialTheme.typography.labelSmall.copy(color = WarmAmberPrimary)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = durationText.toIntOrNull() ?: 15
                    if (topics.isNotBlank()) {
                        onConfirm(
                            person,
                            dur,
                            topics,
                            selectedAudioUri?.toString(),
                            selectedAudioDuration
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                modifier = Modifier.testTag("submit_call_btn")
            ) {
                Text("保存记录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

