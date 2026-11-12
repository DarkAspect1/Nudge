package com.nudge.edge.ui.theme

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

private val FallbackDarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFA8DAB5),
    onTertiary = Color(0xFF10381F),
    tertiaryContainer = Color(0xFF284F34),
    onTertiaryContainer = Color(0xFFC4F6D0),
    background = Color(0xFF111114),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF111114),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLow = Color(0xFF19191D),
    surfaceContainer = Color(0xFF1F1F24),
    surfaceContainerHigh = Color(0xFF28282E),
    outlineVariant = Color(0xFF44424A)
)

private val FallbackLightColors = lightColorScheme(
    primary = Color(0xFF5B45A0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1A0062),
    secondary = Color(0xFF5F5B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5DFF9),
    onSecondaryContainer = Color(0xFF1C192B),
    tertiary = Color(0xFF246C40),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F5BD),
    onTertiaryContainer = Color(0xFF00210D),
    background = Color(0xFFFBF8FD),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFBF8FD),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE6E0EC),
    onSurfaceVariant = Color(0xFF48454F),
    surfaceContainerLow = Color(0xFFF4F1F8),
    surfaceContainer = Color(0xFFEEEAF4),
    surfaceContainerHigh = Color(0xFFE8E3EE),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun NudgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColors
        else -> FallbackLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
