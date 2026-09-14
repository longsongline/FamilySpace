package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.SyncConfig
import com.example.notification.FamilyNotificationHelper
import com.example.ui.theme.WarmAmberPrimary

@Composable
fun GitHubSyncCard(
    config: SyncConfig,
    isSyncing: Boolean,
    syncStatusMessage: String?,
    onPush: (token: String, gistId: String, owner: String, repo: String, path: String, isGist: Boolean) -> Unit,
    onPull: (token: String, gistId: String, owner: String, repo: String, path: String, isGist: Boolean) -> Unit,
    onSaveConfig: (SyncConfig) -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConfigDialog by remember { mutableStateOf(false) }
    val isConfigured = config.gistId.isNotBlank() || config.repoName.isNotBlank() || config.githubToken.isNotBlank()

    // Permission launcher for Android 13+ (POST_NOTIFICATIONS)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "通知权限已开启，将及时接收到家提醒", Toast.LENGTH_SHORT).show()
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("github_sync_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFFDF9)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WarmAmberPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = WarmAmberPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Auto Sync",
                                tint = WarmAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GitHub 中转站 · 三人同享",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            ) {
                                Text(
                                    text = if (isConfigured) "自动同步中" else "未配置",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isConfigured) Color(0xFF2E7D32) else Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                        }

                        Text(
                            text = if (isConfigured) "后台周期轮询已开启 · 上次: ${config.lastSyncTime}"
                            else "点击右侧齿轮配置中转站，即可开启三人自动共享",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isConfigured) {
                        IconButton(
                            onClick = {
                                onPull(
                                    config.githubToken,
                                    config.gistId,
                                    config.repoOwner,
                                    config.repoName,
                                    config.filePath,
                                    config.isGistMode
                                )
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = WarmAmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("sync_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Config",
                            tint = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick actions row: Share config to parents & test notification
            if (isConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val shareCode = generateShareCode(config)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("FamilySyncCode", shareCode))
                            Toast.makeText(context, "已复制配置口令！发给爸妈，爸妈端点“一键导入”即可同享", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = WarmAmberPrimary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("分享口令给父母", style = MaterialTheme.typography.labelSmall, color = WarmAmberPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            FamilyNotificationHelper.showSafeArrivalNotification(
                                context = context,
                                title = "🏠 孩子已安全回到出租屋 (测试提醒)",
                                message = "到达时间: 21:15\n留言: 爸妈，我顺利回到出租屋了，已经冲好热水澡，一切安好，请放心～",
                                location = "阳光里青年社区 3号楼 502室"
                            )
                            Toast.makeText(context, "已发送测试通知，请查看手机通知栏！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF388E3C))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("测试到家通知", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C))
                    }
                }
            }

            // Sync notification feedback banner
            AnimatedVisibility(visible = syncStatusMessage != null) {
                syncStatusMessage?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = WarmAmberPrimary.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { onDismissStatusMessage() }
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        GitHubConfigDialog(
            currentConfig = config,
            onDismiss = { showConfigDialog = false },
            onSave = { updated ->
                onSaveConfig(updated)
                showConfigDialog = false
                Toast.makeText(context, "中转站配置已保存，已开启后台轮询守护", Toast.LENGTH_SHORT).show()
            },
            onCopyGistId = { id ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Family Gist ID", id)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制 Gist ID", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private fun generateShareCode(config: SyncConfig): String {
    // Light-weight code: FSYNC#{token}#{gistId}#{owner}#{repo}#{isGist}
    return "FSYNC#" +
            config.githubToken + "#" +
            config.gistId + "#" +
            config.repoOwner + "#" +
            config.repoName + "#" +
            config.isGistMode
}

private fun parseShareCode(raw: String): SyncConfig? {
    val clean = raw.trim()
    val parts = if (clean.contains("FSYNC#")) {
        clean.substringAfter("FSYNC#").split("#")
    } else {
        clean.split("#")
    }
    if (parts.size >= 5) {
        return SyncConfig(
            githubToken = parts[0],
            gistId = parts[1],
            repoOwner = parts[2],
            repoName = parts[3],
            filePath = "family_space_sync.json",
            isGistMode = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
            lastSyncTime = "已导入口令配置"
        )
    }
    return null
}

@Composable
fun GitHubConfigDialog(
    currentConfig: SyncConfig,
    onDismiss: () -> Unit,
    onSave: (SyncConfig) -> Unit,
    onCopyGistId: (String) -> Unit
) {
    val context = LocalContext.current
    var isGistMode by remember { mutableStateOf(currentConfig.isGistMode) }
    var token by remember { mutableStateOf(currentConfig.githubToken) }
    var gistId by remember { mutableStateOf(currentConfig.gistId) }
    var repoOwner by remember { mutableStateOf(currentConfig.repoOwner) }
    var repoName by remember { mutableStateOf(currentConfig.repoName) }
    var filePath by remember { mutableStateOf(currentConfig.filePath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("GitHub 中转站配置", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // One-tap import button for parents
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text?.toString() ?: ""
                            val parsed = parseShareCode(text)
                            if (parsed != null) {
                                token = parsed.githubToken
                                gistId = parsed.gistId
                                repoOwner = parsed.repoOwner
                                repoName = parsed.repoName
                                isGistMode = parsed.isGistMode
                                Toast.makeText(context, "已成功识别并填入口令配置！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "未在剪贴板中检测到有效口令", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("父母端：读取剪贴板口令一键配好", style = MaterialTheme.typography.labelSmall)
                }

                Text(
                    text = "配置后，后台将每15分钟自动轮询（方案B），孩子到家打卡时长辈手机将主动收到系统通知！",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                )

                // Mode Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isGistMode) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGistMode = true }
                    ) {
                        Text(
                            text = "极简 Gist 模式 (推荐)",
                            color = if (isGistMode) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isGistMode) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGistMode = false }
                    ) {
                        Text(
                            text = "GitHub 仓库模式",
                            color = if (!isGistMode) Color.White else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (isGistMode) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("GitHub Token (含 gist 权限)") },
                        placeholder = { Text("ghp_xxxx") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = gistId,
                        onValueChange = { gistId = it },
                        label = { Text("Gist ID (留空首次推送会自动创建)") },
                        placeholder = { Text("留空或输入已有 Gist ID") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (gistId.isNotBlank()) {
                        Button(
                            onClick = { onCopyGistId(gistId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("复制 Gist ID 发给父母端", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("GitHub Token (含 repo 权限)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repoOwner,
                        onValueChange = { repoOwner = it },
                        label = { Text("GitHub 用户名 (Owner)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = repoName,
                        onValueChange = { repoName = it },
                        label = { Text("仓库名称 (Repo)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text("数据同步文件路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentConfig.copy(
                        githubToken = token.trim(),
                        gistId = gistId.trim(),
                        repoOwner = repoOwner.trim(),
                        repoName = repoName.trim(),
                        filePath = if (filePath.isBlank()) "family_space_sync.json" else filePath.trim(),
                        isGistMode = isGistMode
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
            ) {
                Text("保存并自动同步")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
