package com.daftar.deyoun

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

/** Colours that Material's scheme has no slot for (good = green, bad = red, warnings, header gradient). */
data class Extra(
    val green: Color,
    val red: Color,
    val warnBg: Color,
    val warnInk: Color,
    val soft: Color,
    val softInk: Color,
    val head1: Color,
    val head2: Color,
)

val LocalExtra = staticCompositionLocalOf<Extra> { error("Extra colours not provided") }
val LocalTr = staticCompositionLocalOf<Tr> { Tr("ar") }

private val LightScheme = lightColorScheme(
    primary = Color(0xFFA6533D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DFD4),
    onPrimaryContainer = Color(0xFF91452F),
    secondary = Color(0xFF90796C),
    onSecondary = Color.White,
    background = Color(0xFFF5ECDF),
    onBackground = Color(0xFF49352C),
    surface = Color(0xFFFFFAF2),
    onSurface = Color(0xFF49352C),
    surfaceVariant = Color(0xFFF3E9DC),
    onSurfaceVariant = Color(0xFF90796C),
    outline = Color(0xFFE0CDB9),
    outlineVariant = Color(0xFFE8D7C6),
    error = Color(0xFFC94E4E),
    errorContainer = Color(0xFFF8DFDC),
    onErrorContainer = Color(0xFFA83E39),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE49279),
    onPrimary = Color(0xFF3B1409),
    primaryContainer = Color(0xFF3A2A23),
    onPrimaryContainer = Color(0xFFF0B49F),
    secondary = Color(0xFFB3A094),
    onSecondary = Color(0xFF241C17),
    background = Color(0xFF17110E),
    onBackground = Color(0xFFF1E5DB),
    surface = Color(0xFF241C17),
    onSurface = Color(0xFFF1E5DB),
    surfaceVariant = Color(0xFF2F251F),
    onSurfaceVariant = Color(0xFFB3A094),
    outline = Color(0xFF4A3C32),
    outlineVariant = Color(0xFF3A2E26),
    error = Color(0xFFEF7F7F),
    errorContainer = Color(0xFF46262A),
    onErrorContainer = Color(0xFFF5A09B),
)

private val LightExtra = Extra(
    green = Color(0xFF27845B),
    red = Color(0xFFC94E4E),
    warnBg = Color(0xFFFDEBC8),
    warnInk = Color(0xFF8A5A00),
    soft = Color(0xFFF4DFD4),
    softInk = Color(0xFF91452F),
    head1 = Color(0xFF8F402F),
    head2 = Color(0xFFBD6B51),
)

private val DarkExtra = Extra(
    green = Color(0xFF4CC38A),
    red = Color(0xFFEF7F7F),
    warnBg = Color(0xFF4A3A14),
    warnInk = Color(0xFFF0C76A),
    soft = Color(0xFF3A2A23),
    softInk = Color(0xFFF0B49F),
    head1 = Color(0xFF4E241A),
    head2 = Color(0xFF78402E),
)

@Composable
fun DaftarTheme(dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalExtra provides (if (dark) DarkExtra else LightExtra),
        LocalLayoutDirection provides (if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

@Composable
fun systemDark(): Boolean = isSystemInDarkTheme()
