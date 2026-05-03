package unsa.rfr.com.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import unsa.rfr.com.R

val SilverFont = FontFamily(Font(R.font.silver))

enum class ThemeColor(val colorScheme: ColorScheme) {
    DYNAMIC(lightColorScheme()), // 占位，实际运行时替换
    BLUE(darkColorScheme(
        primary = Color(0xFF9ECAFF),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF00497D),
        onPrimaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFFBBC7DB),
        onSecondary = Color(0xFF253140),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    SMOKE(darkColorScheme(
        primary = Color(0xFFB3B3B3),
        onPrimary = Color(0xFF1A1C1E),
        primaryContainer = Color(0xFF3E3E3E),
        onPrimaryContainer = Color(0xFFE0E0E0),
        secondary = Color(0xFF9E9E9E),
        onSecondary = Color(0xFF1A1C1E),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    ROSE(darkColorScheme(
        primary = Color(0xFFFFB0CB),
        onPrimary = Color(0xFF5C1130),
        primaryContainer = Color(0xFF7B2946),
        onPrimaryContainer = Color(0xFFFFD9E3),
        secondary = Color(0xFFE0BFC8),
        onSecondary = Color(0xFF412B32),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    MIST(darkColorScheme(
        primary = Color(0xFFB0D0D3),
        onPrimary = Color(0xFF1A3B3D),
        primaryContainer = Color(0xFF2F5356),
        onPrimaryContainer = Color(0xFFCEE9EC),
        secondary = Color(0xFFBCC9CB),
        onSecondary = Color(0xFF263233),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    GLACIER(darkColorScheme(
        primary = Color(0xFFA0C8FF),
        onPrimary = Color(0xFF00315E),
        primaryContainer = Color(0xFF004884),
        onPrimaryContainer = Color(0xFFD4E3FF),
        secondary = Color(0xFFBEC6DC),
        onSecondary = Color(0xFF283142),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    DUSK(darkColorScheme(
        primary = Color(0xFFFFB871),
        onPrimary = Color(0xFF502D00),
        primaryContainer = Color(0xFF704400),
        onPrimaryContainer = Color(0xFFFFDCC1),
        secondary = Color(0xFFD7C3A6),
        onSecondary = Color(0xFF3A2E1A),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    TITANIUM(darkColorScheme(
        primary = Color(0xFFCFCFCF),
        onPrimary = Color(0xFF202020),
        primaryContainer = Color(0xFF484848),
        onPrimaryContainer = Color(0xFFEBEBEB),
        secondary = Color(0xFFB5B5B5),
        onSecondary = Color(0xFF202020),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    )),
    FOREST_MORNING(darkColorScheme(
        primary = Color(0xFFA3D9A5),
        onPrimary = Color(0xFF003910),
        primaryContainer = Color(0xFF005319),
        onPrimaryContainer = Color(0xFFBFF6C1),
        secondary = Color(0xFFB9CCBA),
        onSecondary = Color(0xFF243424),
        background = Color(0xFF1A1C1E),
        surface = Color(0xFF1A1C1E),
        error = Color(0xFFFFB4AB),
    ))
}

@Composable
fun RefractorTheme(
    themeColor: ThemeColor = ThemeColor.BLUE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && themeColor == ThemeColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        themeColor == ThemeColor.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        else -> themeColor.colorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = SilverFont),
            bodyMedium = TextStyle(fontFamily = SilverFont),
            labelLarge = TextStyle(fontFamily = SilverFont),
        ),
        content = content
    )
}
