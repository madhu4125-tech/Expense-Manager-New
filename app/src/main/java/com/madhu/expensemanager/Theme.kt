package com.madhu.expensemanager

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncomeGreen = Color(0xFF22C55E)
val ExpenseRed = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF9FAFB),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFDC2626),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF3F4F6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFF87171),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainer = Color(0xFF1E293B),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF334155),
)

@Composable
fun ExpenseTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
