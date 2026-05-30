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

private val DarkColorScheme = darkColorScheme(
    primary = SoftPeach,
    secondary = SandboxBg,
    background = CharcoalText,
    surface = Color(0xFF2C2421),
    onPrimary = EspressoDark,
    onSecondary = CharcoalText,
    onBackground = WarmLinen,
    onSurface = WarmLinen,
    surfaceVariant = SandboxBg,
    outline = CocoaBorder
)

private val LightColorScheme = lightColorScheme(
    primary = EspressoDark,
    onPrimary = Color.White,
    secondary = EspressoBrown,
    onSecondary = Color.White,
    background = WarmLinen,
    onBackground = CharcoalText,
    surface = Color.White,
    onSurface = CharcoalText,
    surfaceVariant = SandboxBg,
    onSurfaceVariant = CharcoalText,
    outline = CocoaBorder,
    primaryContainer = SoftPeach,
    onPrimaryContainer = EspressoDark,
    secondaryContainer = SandboxBg,
    onSecondaryContainer = EspressoBrown
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force beautiful "Sleek Interface" branded colors instead of standard dynamic color matching
  dynamicColor: Boolean = false,
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
