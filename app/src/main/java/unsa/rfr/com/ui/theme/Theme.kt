package unsa.rfr.com.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌色
private val LightBrandColors = lightColorScheme(
    primary = Color(0xFF006D3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9AF5B3),
    secondary = Color(0xFF526050),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E7D3),
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    error = Color(0xFFBA1A1A),
)

private val DarkBrandColors = darkColorScheme(
    primary = Color(0xFF6BDB92),
    onPrimary = Color(0xFF003917),
    primaryContainer = Color(0xFF005227),
    secondary = Color(0xFFB9CCBA),
    onSecondary = Color(0xFF253424),
    secondaryContainer = Color(0xFF3B4A3A),
    background = Color(0xFF1A1C19),
    surface = Color(0xFF1A1C19),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RefractorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // 由设置开关控制
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkBrandColors
        else -> LightBrandColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
