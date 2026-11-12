package jr.brian.thorkbm.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The slate-and-blue palette of the ThorKBM panel. The panel is always dark; [Oled] goes all the way to black. */
@Immutable
data class ErgoColors(
    val background: Color = Color(0xFF161A21),
    val keyTop: Color = Color(0xFF323946),
    val keyBottom: Color = Color(0xFF272D37),
    val keyPressedTop: Color = Color(0xFF465063),
    val keyPressedBottom: Color = Color(0xFF39414F),
    val keyBorder: Color = Color(0xFF4B5363),
    val accentTop: Color = Color(0xFF2A6B9C),
    val accentBottom: Color = Color(0xFF1A4669),
    val accentBorder: Color = Color(0xFF3D8AC4),
    val padTop: Color = Color(0xFF262C37),
    val padBottom: Color = Color(0xFF1D222B),
    val padBorder: Color = Color(0xFF8C96A8),
    val text: Color = Color(0xFFF3F5F8),
    val textMuted: Color = Color(0xFFA9B1BF),
    val latched: Color = Color(0xFF6FB6EE),
    val danger: Color = Color(0xFFE5827A),
) {
    val key: Brush get() = Brush.verticalGradient(listOf(keyTop, keyBottom))
    val keyPressed: Brush get() = Brush.verticalGradient(listOf(keyPressedTop, keyPressedBottom))
    val accent: Brush get() = Brush.verticalGradient(listOf(accentTop, accentBottom))
    val pad: Brush get() = Brush.linearGradient(listOf(padTop, padBottom))

    companion object {
        val Slate = ErgoColors()

        /** True-black background and near-black keys, so unlit AMOLED pixels stay off; borders keep keys readable. */
        val Oled = ErgoColors(
            background = Color.Black,
            keyTop = Color(0xFF16181C),
            keyBottom = Color(0xFF0F1114),
            keyPressedTop = Color(0xFF30353F),
            keyPressedBottom = Color(0xFF262A32),
            keyBorder = Color(0xFF30353F),
            padTop = Color.Black,
            padBottom = Color.Black,
            padBorder = Color(0xFF5C6475),
        )
    }
}

val LocalErgoColors = staticCompositionLocalOf { ErgoColors.Slate }

object ErgoTheme {
    val colors: ErgoColors
        @Composable get() = LocalErgoColors.current
}

private val typography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
)

/** [oledBlack] swaps in the [ErgoColors.Oled] palette; everything below reads it through [LocalErgoColors]. */
@Composable
fun ErgoTheme(oledBlack: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (oledBlack) ErgoColors.Oled else ErgoColors.Slate
    val scheme = darkColorScheme(
        primary = colors.accentBorder,
        onPrimary = colors.text,
        primaryContainer = colors.accentBottom,
        onPrimaryContainer = colors.text,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.background,
        onSurface = colors.text,
        surfaceVariant = colors.keyBottom,
        onSurfaceVariant = colors.textMuted,
        outline = colors.keyBorder,
        error = colors.danger,
    )
    CompositionLocalProvider(LocalErgoColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
