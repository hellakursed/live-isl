package com.liveisl.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF2EC4B6)
private val Warm = Color(0xFFF4A261)
private val Bg = Color(0xFF0B1F2A)
private val Surface = Color(0xFF143447)
private val OnBg = Color(0xFFF7F9FB)
private val Muted = Color(0xFF9BB4C4)

private val DarkColors = darkColorScheme(
    primary = Teal,
    secondary = Warm,
    background = Bg,
    surface = Surface,
    onPrimary = Bg,
    onSecondary = Bg,
    onBackground = OnBg,
    onSurface = OnBg,
    onSurfaceVariant = Muted,
)

@Composable
fun LiveIslTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = OnBg,
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = OnBg,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = OnBg,
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                color = Muted,
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnBg,
            ),
        ),
        content = content,
    )
}
