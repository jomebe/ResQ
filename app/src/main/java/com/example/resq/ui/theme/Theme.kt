package com.example.resq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ResQColorScheme = darkColorScheme(
    primary = ResQRed,
    secondary = ResQRed,
    background = ResQBlack,
    surface = ResQSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = ResQOnSurface,
    onSurface = ResQOnSurface
)

@Composable
fun ResQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ResQColorScheme,
        typography = Typography,
        content = content
    )
}