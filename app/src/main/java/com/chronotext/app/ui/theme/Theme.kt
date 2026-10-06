package com.chronotext.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌主色：玫红，贴合"给在乎的人定时送祝福"的产品气质
private val Rose = Color(0xFFB3234B)
private val RoseDark = Color(0xFFFFB2BE)
private val RoseContainer = Color(0xFFFFD9DD)
private val RoseContainerDark = Color(0xFF930035)

private val LightColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = RoseContainer,
    onPrimaryContainer = Color(0xFF400014),
    secondary = Color(0xFF75565B),
    secondaryContainer = Color(0xFFFFD9DD),
    onSecondaryContainer = Color(0xFF2B1518),
)

private val DarkColors = darkColorScheme(
    primary = RoseDark,
    onPrimary = Color(0xFF670025),
    primaryContainer = RoseContainerDark,
    onPrimaryContainer = RoseContainer,
    secondary = Color(0xFFE5BDC2),
    secondaryContainer = Color(0xFF5C3F44),
    onSecondaryContainer = Color(0xFFFFD9DD),
)

@Composable
fun ChronoTextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
