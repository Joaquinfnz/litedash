package com.irvingguerra.litedash.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Colores de la marca (los mismos de brand/logo).
object Brand {
    val Graphite = Color(0xFF101317)
    val GraphiteLight = Color(0xFF1F252D)
    val LightWhite = Color(0xFFF4F6F8)
    val Amber = Color(0xFFFFB224)
}

@Immutable
data class Colors(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    /** Fondo lleno de acento (CarPlay): más oscuro que [accent] para que el texto blanco se lea. */
    val accentFill: Color,
    /** Texto sobre [accentFill]. */
    val onAccent: Color,
    val danger: Color,
)

// Tema de Joaquín: negro con el rojo de la iluminación de la cabina del XV.
// Contraste: texto 16:1, apagado 7.5:1, rojo de texto 4.9:1, blanco sobre rojo de fondo 5.8:1.
val DarkColors = Colors(
    dark = true,
    background = Color(0xFF0B0B0C),
    surface = Color(0xFF151517),
    surfaceHigh = Color(0xFF202024),
    line = Color(0xFF2B2B30),
    text = Color(0xFFF3F3F4),
    textMuted = Color(0xFFA6A6AD),
    accent = Color(0xFFF2453A),
    accentFill = Color(0xFFC2261F),
    onAccent = Color.White,
    danger = Color(0xFFFFB020),
)

val LightColors = Colors(
    dark = false,
    background = Color(0xFFF3F3F4),
    surface = Color.White,
    surfaceHigh = Color(0xFFE6E6E9),
    line = Color(0xFFD5D5DA),
    text = Color(0xFF111113),
    textMuted = Color(0xFF55555C),
    accent = Color(0xFFC2261F),
    accentFill = Color(0xFFC2261F),
    onAccent = Color.White,
    danger = Color(0xFFB36B00),
)

val LocalColors = staticCompositionLocalOf { DarkColors }

/**
 * Sin Material: la app usa solo foundation para mantenerse ligera.
 * [scale] agranda o achica toda la interfaz (pantallas que mienten con su dpi).
 */
@Composable
fun LiteDashTheme(dark: Boolean = true, scale: Float = 1f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalColors provides if (dark) DarkColors else LightColors,
        LocalDensity provides Density(d.density * scale, d.fontScale),
        content = content,
    )
}

/** Nunito (OFL, solo latín): redondeada y cálida, de números claros a un brazo de distancia. */
val Rounded = FontFamily(
    Font(R.font.nunito_medium, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

object Typography {
    val clock = TextStyle(fontFamily = Rounded, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = Rounded, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    val subtitle = TextStyle(fontFamily = Rounded, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = Rounded, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontFamily = Rounded, fontSize = 14.sp)
    val label = TextStyle(fontFamily = Rounded, fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun Label(
    text: String,
    style: TextStyle,
    color: Color = LocalColors.current.text,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    BasicText(text, modifier, style = style.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Tarjeta base del tablero: superficie con borde fino y esquinas de 20. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(20.dp)
    // La pieza de acento (CarPlay) es la única de color lleno: sin borde, se lee primero.
    var m = if (accent) modifier.clip(shape).background(c.accentFill) else modifier.clip(shape).background(c.surface).border(1.dp, c.line, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m, content = content)
}

/** Botón redondo con ícono, objetivo táctil de 56 dp como pide el plan. */
@Composable
fun IconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    background: Color = Color.Transparent,
    color: Color = LocalColors.current.text,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, color, size * 0.45f) }
}

/** Botón de texto en forma de píldora. */
@Composable
fun Button(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (primary) c.text else c.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Label(text, Typography.body.copy(fontWeight = FontWeight.Bold), if (primary) c.background else c.text, 1) }
}
