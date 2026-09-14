package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HomeVisitRecord
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import com.example.ui.theme.WarmPeachContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeVisitSection(
    visits: List<HomeVisitRecord>,
    onAddVisit: (dateString: String, note: String, meals: String) -> Unit,
    onDeleteVisit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCheckInDialog by remember { mutableStateOf(false) }
    var isExpandedList by remember { mutableStateOf(false) }

    val currentYearCount = visits.size
    val daysSinceLastVisit = if (visits.isNotEmpty()) {
        val latestTimestamp = visits.first().visitTimestamp
        val diffMs = System.currentTimeMillis() - latestTimestamp
        TimeUnit.MILLISECONDS.toDays(diffMs).toInt().coerceAtLeast(0)
    } else {
        0
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_visit_card"),
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
                            .background(WarmAmberSecondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home Visits",
                            tint = WarmAmberSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "回家足迹 · 次数与打卡",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "每一次奔赴家门，都是最珍贵的陪伴",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                Button(
                    onClick = { showCheckInDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                    modifier = Modifier.testTag("home_checkin_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("回家打卡", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Two stats boxes: Annual visit count & Days since last visit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Annual visit count
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = WarmPeachContainer,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "今年已回家",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$currentYearCount",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = WarmAmberPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "次",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                // Days since last visit
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF2F7F2),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "距上次回家",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (visits.isEmpty()) "0" else "$daysSinceLastVisit",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = CozySage
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "天",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // Caring hint
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (daysSinceLastVisit > 20) "💡 已经有 $daysSinceLastVisit 天没回老家了，周末如果有空，回去喝碗妈妈熬的汤吧。"
                    else "💡 陪伴是给父母最好的礼物，随时在下方记录每一次回家的饭菜与欢笑。",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Latest visit item preview
            if (visits.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val latest = visits.first()
                Text(
                    text = "最近一次回家打卡记录：",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                VisitDetailCard(
                    visit = latest,
                    onDelete = { onDeleteVisit(latest.id) }
                )

                if (visits.size > 1) {
                    TextButton(
                        onClick = { isExpandedList = !isExpandedList },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isExpandedList) "收起更多打卡" else "查看往期 ${visits.size - 1} 次回家记录")
                    }

                    AnimatedVisibility(visible = isExpandedList) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visits.drop(1).forEach { record ->
                                VisitDetailCard(
                                    visit = record,
                                    onDelete = { onDeleteVisit(record.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCheckInDialog) {
        AddVisitDialog(
            onDismiss = { showCheckInDialog = false },
            onConfirm = { dateStr, note, meals ->
                onAddVisit(dateStr, note, meals)
                showCheckInDialog = false
            }
        )
    }
}

@Composable
fun VisitDetailCard(
    visit: HomeVisitRecord,
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
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = visit.dateString,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
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

            if (visit.mealNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.DinnerDining,
                        contentDescription = null,
                        tint = WarmAmberSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "吃到了：${visit.mealNotes}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = visit.note,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            )
        }
    }
}

@Composable
fun AddVisitDialog(
    onDismiss: () -> Unit,
    onConfirm: (dateStr: String, note: String, meals: String) -> Unit
) {
    val sdf = SimpleDateFormat("M月d日", Locale.CHINA)
    var dateStr by remember { mutableStateOf(sdf.format(Date()) + " (周末)") }
    var meals by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏠 回家打卡登记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("回家日期") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = meals,
                    onValueChange = { meals = it },
                    label = { Text("妈妈/爸爸做的家常菜 (如: 红烧排骨、鸡汤)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visit_meals_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("回家的陪伴感悟或点滴") },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visit_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (note.isNotBlank() || meals.isNotBlank()) {
                        onConfirm(dateStr, note, meals)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                modifier = Modifier.testTag("submit_visit_btn")
            ) {
                Text("打卡保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
