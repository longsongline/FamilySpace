package com.example.ui.components

import android.Manifest
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SafeArrivalLog
import com.example.ui.theme.CozySage
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary
import com.example.ui.theme.WarmPeachContainer
import com.example.util.BatteryOptimizationUtil
import java.util.Locale

@Composable
fun SafeArrivalCard(
    isChildMode: Boolean,
    onEnterChildMode: (passcode: String) -> Boolean,
    onSwitchToParentMode: () -> Unit,
    onChangePasscode: (newCode: String) -> Boolean,
    rentalAddress: String,
    rentalLatitude: Double = 0.0,
    rentalLongitude: Double = 0.0,
    rentalRadiusMeters: Int = 500,
    distanceToRentalMeters: Double? = null,
    isInsideRentalZone: Boolean,
    arrivalLogs: List<SafeArrivalLog>,
    gpsInfo: String? = null,
    isGpsLoading: Boolean = false,
    isBackgroundLocationEnabled: Boolean = true,
    onToggleBackgroundLocation: (Boolean) -> Unit = {},
    onFetchGps: () -> Unit = {},
    onSetCurrentGpsAsBase: (address: String, radius: Int) -> Unit = { _, _ -> },
    onUpdateFullConfig: (address: String, lat: Double, lng: Double, radius: Int) -> Unit = { _, _, _, _ -> },
    onTriggerArrived: (String) -> Unit,
    onUpdateAddress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onFetchGps()
        }
    }

    // Dialog states
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var passcodeInput by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf(false) }
    var showPasscodeText by remember { mutableStateOf(false) }

    var showChangePasscodeDialog by remember { mutableStateOf(false) }
    var newPasscodeInput by remember { mutableStateOf("") }
    var changePasscodeError by remember { mutableStateOf<String?>(null) }

    var showAddressEditDialog by remember { mutableStateOf(false) }
    var addressInput by remember(rentalAddress) { mutableStateOf(rentalAddress) }
    var radiusInput by remember(rentalRadiusMeters) { mutableIntStateOf(rentalRadiusMeters) }
    var latInput by remember(rentalLatitude) {
        mutableStateOf(if (rentalLatitude != 0.0) String.format(Locale.US, "%.4f", rentalLatitude) else "")
    }
    var lngInput by remember(rentalLongitude) {
        mutableStateOf(if (rentalLongitude != 0.0) String.format(Locale.US, "%.4f", rentalLongitude) else "")
    }

    var showCustomMsgDialog by remember { mutableStateOf(false) }
    var customMsgInput by remember {
        mutableStateOf("爸妈，我顺利回到出租屋了，已经到家冲了热水澡，一切安好，请放心～")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("safe_arrival_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =================================================================
        // 1. Top Identity & Mode Switch Bar (身份切换横条)
        // =================================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isChildMode) CozySage.copy(alpha = 0.14f) else Color(0xFFFFF3E0),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isChildMode) CozySage else WarmAmberPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isChildMode) Icons.Default.GpsFixed else Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isChildMode) "当前模式：👦 孩子模式 (我的模式)" else "当前模式：👨‍👩‍👧 父母模式 (默认)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChildMode) CozySage else WarmAmberPrimary
                                )
                            )
                        }
                        Text(
                            text = if (isChildMode) "已解锁坐标设置、实时测距与报平安" else "状态只读视图 · 无繁琐设置干扰",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        )
                    }
                }

                if (!isChildMode) {
                    // Parents mode: prominent Child Passcode button
                    OutlinedButton(
                        onClick = {
                            passcodeInput = ""
                            passcodeError = false
                            showPasscodeDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WarmAmberPrimary
                        ),
                        modifier = Modifier.testTag("child_passcode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "孩子口令",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    // Child mode: change passcode & exit to parent mode
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                newPasscodeInput = ""
                                changePasscodeError = null
                                showChangePasscodeDialog = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "修改口令",
                                tint = CozySage,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = onSwitchToParentMode,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CozySage.copy(alpha = 0.2f),
                                contentColor = CozySage
                            ),
                            modifier = Modifier.testTag("exit_child_mode_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "退出孩子模式",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // =================================================================
        // 2. Main Safe Arrival Card (Differentiated for Parent vs Child)
        // =================================================================
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
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
                if (!isChildMode) {
                    // -------------------------------------------------------------
                    // 👨‍👩‍👧 PARENT MODE VIEW: Pure Status Display (无设置/无操作)
                    // -------------------------------------------------------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isInsideRentalZone) CozySage.copy(alpha = 0.15f)
                                        else Color(0xFFFFF3E0)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isInsideRentalZone) Icons.Default.CheckCircle else Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = if (isInsideRentalZone) CozySage else WarmAmberPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "孩子到家安全状态",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "出租屋电子围栏实时守护",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                )
                            }
                        }

                        // Big Status Badge for Parents
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isInsideRentalZone) CozySage.copy(alpha = 0.15f) else Color(0xFFFFF3E0)
                        ) {
                            val statusLabel = if (isInsideRentalZone) {
                                "已在出租房 🏠"
                            } else if (distanceToRentalMeters != null) {
                                val d = distanceToRentalMeters
                                val distStr = if (d >= 1000) String.format(Locale.US, "%.1fkm", d / 1000.0) else "${d.toInt()}m"
                                "距出租屋 $distStr 🚶"
                            } else {
                                "在外奔波中 🚶"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isInsideRentalZone) CozySage else WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hero Status Card for Parents
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isInsideRentalZone) Color(0xFFF1F8F4) else Color(0xFFFFF8F0),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val distStr = if (distanceToRentalMeters != null) {
                                val d = distanceToRentalMeters
                                if (d >= 1000) String.format(Locale.US, "%.1f公里", d / 1000.0) else "${d.toInt()}米"
                            } else null

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isInsideRentalZone) Icons.Outlined.Home else Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = if (isInsideRentalZone) CozySage else WarmAmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isInsideRentalZone) {
                                        "孩子已在出租屋范围内 🏠"
                                    } else if (distStr != null) {
                                        "孩子在外奔波 · 距出租屋约 $distStr 🚶"
                                    } else {
                                        "孩子目前在外奔波 🚶"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isInsideRentalZone) CozySage else WarmAmberPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (distStr != null) {
                                Text(
                                    text = "📏 实时直线距离：$distStr（${rentalRadiusMeters}米内判定为到家）",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            Text(
                                text = "📍 孩子租住地：$rentalAddress",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            )
                        }
                    }

                    // Latest arrival record or message from child
                    if (arrivalLogs.isNotEmpty()) {
                        val latest = arrivalLogs.first()
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CozySage,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "最近报平安：${latest.timeString}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "“${latest.alertMessage}”",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }

                    // Parent comfort interaction
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "孩子平安到家，父母已知悉，心里踏实了 ❤️", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = CozySage
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "已放心 · 暖心回执",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Button(
                            onClick = {
                                passcodeInput = ""
                                passcodeError = false
                                showPasscodeDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmAmberPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "我是孩子·接口令",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                } else {
                    // -------------------------------------------------------------
                    // 👦 CHILD MODE VIEW: Full Controls & GPS & Coordinates Settings
                    // -------------------------------------------------------------
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
                                    .background(CozySage.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Safe Arrival",
                                    tint = CozySage,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "出租房安全守护 · 自动报平安",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "进入电子围栏自动/手动推送平安弹窗",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isInsideRentalZone) CozySage.copy(alpha = 0.15f) else Color(0xFFFFF3E0)
                        ) {
                            val statusLabel = if (isInsideRentalZone) {
                                "已在出租房 🏠"
                            } else if (distanceToRentalMeters != null) {
                                val d = distanceToRentalMeters
                                val distStr = if (d >= 1000) String.format(Locale.US, "%.1fkm", d / 1000.0) else "${d.toInt()}m"
                                "距家 $distStr 🚶"
                            } else {
                                "在外奔波中 🚶"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isInsideRentalZone) CozySage else WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Current rental home address banner (Editable in Child Mode)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    tint = WarmAmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "出租房安全围栏基准点 (半径 ${rentalRadiusMeters}米)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    )
                                    Text(
                                        text = rentalAddress,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (rentalLatitude != 0.0) {
                                        Text(
                                            text = "基准GPS: ${String.format(Locale.US, "%.4f", rentalLatitude)}°N, ${String.format(Locale.US, "%.4f", rentalLongitude)}°E",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = CozySage,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showAddressEditDialog = true },
                                modifier = Modifier.testTag("edit_rental_address_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditLocation,
                                    contentDescription = "Edit address",
                                    tint = WarmAmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Real-time GPS status banner (Child Mode feature)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF5F5F0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isGpsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                    color = CozySage
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = CozySage,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = gpsInfo ?: "支持读取手机硬件实时 GPS 经纬度",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            ) {
                                Text(
                                    text = if (isGpsLoading) "定位中..." else "读GPS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = CozySage,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            TextButton(
                                onClick = {
                                    onSetCurrentGpsAsBase(rentalAddress, rentalRadiusMeters)
                                }
                            ) {
                                Text(
                                    text = "设为出租屋",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = WarmAmberPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Action: Arrived trigger & simulated notification
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCustomMsgDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_arrival_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInsideRentalZone) "已到出租屋 · 报平安" else "手动报平安 · 通知父母",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    // Recent arrival record in Child Mode
                    if (arrivalLogs.isNotEmpty()) {
                        val latest = arrivalLogs.first()
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CozySage,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "最近报平安：${latest.timeString}（已通知父母）",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // =================================================================
                    // 🚀 BACKGROUND GUARDIAN & AUTO-SYNC SETTINGS (无需点开App)
                    // =================================================================
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF7FAF7),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CozySage.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(CozySage.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = CozySage,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "后台静默守护 (无需打开App)",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = if (isBackgroundLocationEnabled) "已开启 · 锁屏/切后台自动测距同步" else "已暂停后台位置自动同步",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (isBackgroundLocationEnabled) CozySage else Color.Gray,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = isBackgroundLocationEnabled,
                                    onCheckedChange = { checked ->
                                        onToggleBackgroundLocation(checked)
                                        if (checked) {
                                            locationPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CozySage
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "💡 原理说明：开启后，App 将在后台启动常驻位置守护服务，即使您手机切出、锁屏或息屏，也会定期采样 GPS 距离并自动推送到云端给父母端。进出出租屋围栏时自动记录并通知。",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action buttons for system permissions & manufacturer whitelist
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        BatteryOptimizationUtil.requestIgnoreBatteryOptimizations(context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "🔋 忽略电池优化",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        BatteryOptimizationUtil.openAutoStartSettings(context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "⚡ 手机自启动设置",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        BatteryOptimizationUtil.openAppDetailsSettings(context)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "📍 始终定位权限",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =================================================================
        // 3. Historical Safe Arrival Logs List (历史记录，让父母孩子都能查看)
        // =================================================================
        if (arrivalLogs.size > 1) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isChildMode) "📋 我的历史报备记录" else "📋 孩子历史到家记录流水",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    arrivalLogs.drop(1).take(5).forEach { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = CozySage,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = log.timeString,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = log.alertMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =================================================================
    // Dialog 1: 孩子口令验证对话框 (Passcode login dialog)
    // =================================================================
    if (showPasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = WarmAmberPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "孩子口令验证",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入孩子专属口令，解锁【孩子模式（我的模式）】。进入后可设定出租房具体坐标、电子围栏半径并测试测距报备。\n\n（初始默认口令：1234）",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 18.sp
                        )
                    )

                    OutlinedTextField(
                        value = passcodeInput,
                        onValueChange = {
                            passcodeInput = it
                            passcodeError = false
                        },
                        label = { Text("输入孩子口令") },
                        visualTransformation = if (showPasscodeText) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showPasscodeText = !showPasscodeText }) {
                                Icon(
                                    imageVector = if (showPasscodeText) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "切换可见性"
                                )
                            }
                        },
                        isError = passcodeError,
                        supportingText = {
                            if (passcodeError) {
                                Text("口令错误，请重新输入（默认口令为 1234）", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("passcode_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = onEnterChildMode(passcodeInput)
                        if (success) {
                            showPasscodeDialog = false
                            Toast.makeText(context, "口令正确，已切换为孩子模式", Toast.LENGTH_SHORT).show()
                        } else {
                            passcodeError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                    modifier = Modifier.testTag("verify_passcode_btn")
                ) {
                    Text("验证进入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // =================================================================
    // Dialog 2: 修改孩子口令对话框 (Change passcode dialog)
    // =================================================================
    if (showChangePasscodeDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasscodeDialog = false },
            title = { Text("修改孩子口令", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "设置新的孩子口令（建议 4 位以上好记的数字或字母）：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPasscodeInput,
                        onValueChange = {
                            newPasscodeInput = it
                            changePasscodeError = null
                        },
                        label = { Text("新口令") },
                        singleLine = true,
                        isError = changePasscodeError != null,
                        supportingText = {
                            if (changePasscodeError != null) {
                                Text(changePasscodeError ?: "", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasscodeInput.trim().length < 4) {
                            changePasscodeError = "口令长度至少为4位"
                        } else {
                            val ok = onChangePasscode(newPasscodeInput.trim())
                            if (ok) {
                                showChangePasscodeDialog = false
                                Toast.makeText(context, "孩子口令修改成功！", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CozySage)
                ) {
                    Text("保存口令")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasscodeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // =================================================================
    // Dialog 3: 出租房围栏与坐标设定 (Child Mode Only)
    // =================================================================
    if (showAddressEditDialog) {
        AlertDialog(
            onDismissRequest = { showAddressEditDialog = false },
            title = { Text("出租房围栏与坐标设定", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "当您进入设定的围栏半径内，手机将自动识别已安全回到出租屋，并向父母报平安：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("出租房具体地址与门牌号") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rental_address_input")
                    )

                    // Radius selector
                    Column {
                        Text(
                            text = "电子围栏有效半径：",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(200, 500, 1000, 2000).forEach { r ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (radiusInput == r) WarmAmberPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { radiusInput = r }
                                ) {
                                    Text(
                                        text = if (r >= 1000) "${r / 1000}公里" else "${r}米",
                                        color = if (radiusInput == r) Color.White else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Coordinates manual or 1-tap
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = latInput,
                            onValueChange = { latInput = it },
                            label = { Text("纬度 (如 31.23)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lngInput,
                            onValueChange = { lngInput = it },
                            label = { Text("经度 (如 121.47)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CozySage.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetCurrentGpsAsBase(addressInput, radiusInput)
                                showAddressEditDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, tint = CozySage, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "一键将手机当前实时 GPS 设为基准点",
                                style = MaterialTheme.typography.labelMedium.copy(color = CozySage, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = latInput.toDoubleOrNull() ?: rentalLatitude
                        val lng = lngInput.toDoubleOrNull() ?: rentalLongitude
                        onUpdateFullConfig(addressInput, lat, lng, radiusInput)
                        showAddressEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary)
                ) {
                    Text("保存设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // =================================================================
    // Dialog 4: 孩子手动向父母报平安留言对话框 (Child Mode Only)
    // =================================================================
    if (showCustomMsgDialog) {
        AlertDialog(
            onDismissRequest = { showCustomMsgDialog = false },
            title = { Text("向父母报平安") },
            text = {
                Column {
                    val distText = if (distanceToRentalMeters != null && rentalLatitude != 0.0) {
                        val d = distanceToRentalMeters
                        val distStr = if (d >= 1000) String.format(Locale.US, "%.1f公里", d / 1000.0) else "${d.toInt()}米"
                        if (isInsideRentalZone) "【已进入出租房电子围栏 (距离约$distStr)】"
                        else "【当前距出租房约$distStr】"
                    } else ""

                    Text(
                        text = "目的地：【$rentalAddress】$distText\n将立即向父母端推送以下报平安卡片：",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = customMsgInput,
                        onValueChange = { customMsgInput = it },
                        label = { Text("给父母的报平安留言") },
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("arrival_msg_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTriggerArrived(customMsgInput)
                        showCustomMsgDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary),
                    modifier = Modifier.testTag("send_arrival_alert_btn")
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("确认发送平安通知")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomMsgDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
