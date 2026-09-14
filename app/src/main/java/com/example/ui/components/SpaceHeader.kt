package com.example.ui.components

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
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.WarmAmberPrimary
import com.example.ui.theme.WarmAmberSecondary

import java.util.Locale

@Composable
fun SpaceHeader(
    modifier: Modifier = Modifier,
    isChildMode: Boolean = false,
    isInsideRentalZone: Boolean = false,
    distanceToRentalMeters: Double? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        WarmAmberPrimary.copy(alpha = 0.96f),
                        WarmAmberSecondary.copy(alpha = 0.88f)
                    )
                )
            )
            .padding(top = 18.dp, bottom = 18.dp, start = 18.dp, end = 18.dp)
    ) {
        // App title & status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FamilyRestroom,
                        contentDescription = "Family icon",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "亲情空间",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.6.sp
                        )
                    )
                    Text(
                        text = "我与父母 · 三人同享专属港湾",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Connection indicator badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isInsideRentalZone) Color(0xFF2E7D32).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.22f),
                modifier = Modifier.testTag("online_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isInsideRentalZone) Color(0xFF81C784) else Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isInsideRentalZone) "🏠 已在出租屋" else "实时同享中",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Three Members Badges (我、爸爸、妈妈)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val childStatus = if (isInsideRentalZone) {
                "已在出租屋 🏠"
            } else if (distanceToRentalMeters != null) {
                val d = distanceToRentalMeters
                val distStr = if (d >= 1000) String.format(Locale.US, "%.1fkm", d / 1000.0) else "${d.toInt()}m"
                "距家$distStr 🚶"
            } else {
                "在外奔波 🚶"
            }
            FamilyMemberTag(if (isChildMode) "👨 我 (孩子)" else "👨 孩子", childStatus)
            Text("•", color = Color.White.copy(alpha = 0.5f))
            FamilyMemberTag("👩 妈妈", "牵挂叮咛")
            Text("•", color = Color.White.copy(alpha = 0.5f))
            FamilyMemberTag("👴 爸爸", "温厚如山")
        }
    }
}

@Composable
private fun FamilyMemberTag(name: String, role: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = role,
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.sp
            )
        )
    }
}
