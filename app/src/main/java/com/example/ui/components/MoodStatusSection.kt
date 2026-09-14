package com.example.ui.components

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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import com.example.ui.theme.WarmPeachContainer

@Composable
fun MoodStatusSection(
    childMood: String,
    childEnergy: Int,
    childUpdated: String,
    parentMood: String,
    parentEnergy: Int,
    parentUpdated: String,
    onUpdateChildMood: (String, Int) -> Unit,
    onUpdateParentMood: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditChildDialog by remember { mutableStateOf(false) }
    var showEditParentDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CozySage.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mood,
                        contentDescription = null,
                        tint = CozySage,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "今日心情与互报平安",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "异地生活，随时感知彼此的情绪与健康",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Child Mood Card
            MoodCard(
                title = "👦 我的今日状态",
                mood = childMood,
                energy = childEnergy,
                updated = childUpdated,
                accentColor = WarmAmberPrimary,
                bg = Color(0xFFFFF9F5),
                onEdit = { showEditChildDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("child_mood_card")
            )

            // Parents Mood Card
            MoodCard(
                title = "🏡 父母今日状态",
                mood = parentMood,
                energy = parentEnergy,
                updated = parentUpdated,
                accentColor = CozySage,
                bg = Color(0xFFF4F9F5),
                onEdit = { showEditParentDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("parent_mood_card")
            )
        }
    }

    if (showEditChildDialog) {
        EditMoodDialog(
            title = "更新我的今日状态与心情电量",
            currentMood = childMood,
            currentEnergy = childEnergy,
            onDismiss = { showEditChildDialog = false },
            onConfirm = { m, e ->
                onUpdateChildMood(m, e)
                showEditChildDialog = false
            }
        )
    }

    if (showEditParentDialog) {
        EditMoodDialog(
            title = "更新父母的日常状态与健康电量",
            currentMood = parentMood,
            currentEnergy = parentEnergy,
            onDismiss = { showEditParentDialog = false },
            onConfirm = { m, e ->
                onUpdateParentMood(m, e)
                showEditParentDialog = false
            }
        )
    }
}

@Composable
fun MoodCard(
    title: String,
    mood: String,
    energy: Int,
    updated: String,
    accentColor: Color,
    bg: Color,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = modifier.clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Energy progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "心情能量",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
                Text(
                    text = "$energy%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { energy / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = accentColor.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = mood,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "更新于 $updated",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            )
        }
    }
}

@Composable
fun EditMoodDialog(
    title: String,
    currentMood: String,
    currentEnergy: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var moodText by remember { mutableStateOf(currentMood) }
    var energySlider by remember { mutableFloatStateOf(currentEnergy.toFloat()) }

    val quickPills = listOf(
        "准时下班，吃了大餐 🍜",
        "今天状态极佳，能量满满 ✨",
        "稍微有点累，准备早睡 🌙",
        "身体很好，正在公园散步 🌳",
        "想念家里的红烧肉了 🍲"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "能量状态：${energySlider.toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Slider(
                    value = energySlider,
                    onValueChange = { energySlider = it },
                    valueRange = 10f..100f,
                    steps = 8
                )

                OutlinedTextField(
                    value = moodText,
                    onValueChange = { moodText = it },
                    label = { Text("心情或碎碎念") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "快捷词句：",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    quickPills.take(3).forEach { pill ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { moodText = pill }
                        ) {
                            Text(
                                text = pill,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(moodText, energySlider.toInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
            ) {
                Text("保存状态")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
