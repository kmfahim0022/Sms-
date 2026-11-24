package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.data.preferences.AccentPreset
import com.example.data.preferences.ThemeMode

@Composable
fun AfxSmsManagerTheme(
    themeMode: ThemeMode = ThemeMode.DARK_AMOLED,
    accentPreset: AccentPreset = AccentPreset.CYAN_ELECTRIC,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK_AMOLED -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val accent = Color(accentPreset.hexColor)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = VoidBlack,
            primaryContainer = accent.copy(alpha = 0.18f),
            onPrimaryContainer = accent,
            secondary = IndigoAccent,
            onSecondary = VoidBlack,
            secondaryContainer = ObsidianContainer,
            onSecondaryContainer = TextPrimaryDark,
            tertiary = EmeraldSuccess,
            background = VoidBlack,
            onBackground = TextPrimaryDark,
            surface = ObsidianSurface,
            onSurface = TextPrimaryDark,
            surfaceVariant = ObsidianElevated,
            onSurfaceVariant = TextSecondaryDark,
            outline = Color.White.copy(alpha = 0.12f),
            outlineVariant = Color.White.copy(alpha = 0.06f),
            error = RoseError,
            onError = VoidBlack
        )
    } else {
        lightColorScheme(
            primary = accent.copy(alpha = 1f).let {
                // Ensure strong contrast on light surfaces
                when (accentPreset) {
                    AccentPreset.CYAN_ELECTRIC -> Color(0xFF0891B2)
                    AccentPreset.INDIGO_VIOLET -> Color(0xFF4F46E5)
                    AccentPreset.EMERALD_MINT -> Color(0xFF059669)
                    AccentPreset.AMBER_GOLD -> Color(0xFFD97706)
                    AccentPreset.ROSE_CRIMSON -> Color(0xFFE11D48)
                }
            },
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.14f),
            onPrimaryContainer = TextPrimaryLight,
            secondary = Color(0xFF4F46E5),
            onSecondary = Color.White,
            secondaryContainer = LightContainer,
            onSecondaryContainer = TextPrimaryLight,
            tertiary = Color(0xFF059669),
            background = LightCanvas,
            onBackground = TextPrimaryLight,
            surface = LightSurface,
            onSurface = TextPrimaryLight,
            surfaceVariant = LightContainer,
            onSurfaceVariant = TextSecondaryLight,
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0),
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AfxTypography,
        content = content
    )
}
