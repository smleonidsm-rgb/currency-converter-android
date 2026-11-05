package com.leonidsm.rsdconverter.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = NavyDeep,
    secondary = GoldSoft,
    background = NavyDeep,
    onBackground = TextPrimaryDark,
    surface = NavyCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyMid,
    onSurfaceVariant = TextSecondaryDark,
    error = ErrorSoft,
)

private val LightColors = lightColorScheme(
    primary = GoldDeep,
    onPrimary = CreamCard,
    secondary = GoldDeep,
    background = CreamBg,
    onBackground = TextPrimaryLight,
    surface = CreamCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = CreamBg,
    onSurfaceVariant = TextSecondaryLight,
    error = ErrorDeep,
)

@Composable
fun RsdConverterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
