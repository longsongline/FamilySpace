package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = WarmAmberSecondary,
    secondary = WarmAmberTertiary,
    tertiary = CozySage,
    background = Color(0xFF1E1A17),
    surface = Color(0xFF2B2521),
    surfaceVariant = Color(0xFF38302A),
    primaryContainer = Color(0xFF5E2D0E),
    onPrimaryContainer = Color(0xFFFFDCC7)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = WarmAmberPrimary,
    secondary = WarmAmberSecondary,
    tertiary = CozySage,
    background = WarmBackground,
    surface = WarmSurface,
    surfaceVariant = WarmSurfaceVariant,
    primaryContainer = WarmPeachContainer,
    onPrimaryContainer = WarmPeachOnContainer,
    secondaryContainer = CozySageContainer,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
