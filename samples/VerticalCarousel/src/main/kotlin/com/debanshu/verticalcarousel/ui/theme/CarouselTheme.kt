package com.debanshu.verticalcarousel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Cinematic color palette for the vertical carousel.
 * Deep blacks with warm gold and crimson accents create a movie theater atmosphere.
 */
object CarouselColors {
    // Primary - Rich Gold
    val Primary = Color(0xFFD4AF37)
    val OnPrimary = Color(0xFF1A1A1A)
    val PrimaryContainer = Color(0xFF3D3419)
    val OnPrimaryContainer = Color(0xFFFFE082)

    // Secondary - Deep Crimson
    val Secondary = Color(0xFFB22222)
    val OnSecondary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFF3D1414)
    val OnSecondaryContainer = Color(0xFFFFCDD2)

    // Tertiary - Platinum
    val Tertiary = Color(0xFFE5E4E2)
    val OnTertiary = Color(0xFF1A1A1A)
    val TertiaryContainer = Color(0xFF3A3A3A)
    val OnTertiaryContainer = Color(0xFFE8E8E8)

    // Background - True Black Cinema
    val Background = Color(0xFF0A0A0A)
    val OnBackground = Color(0xFFF5F5F5)

    // Surface - Charcoal
    val Surface = Color(0xFF121212)
    val SurfaceVariant = Color(0xFF1E1E1E)
    val OnSurface = Color(0xFFF0F0F0)
    val OnSurfaceVariant = Color(0xFFB0B0B0)

    // Error
    val Error = Color(0xFFCF6679)
    val OnError = Color(0xFF000000)

    // Outline
    val Outline = Color(0xFF3A3A3A)
}

private val DarkColorScheme = darkColorScheme(
    primary = CarouselColors.Primary,
    onPrimary = CarouselColors.OnPrimary,
    primaryContainer = CarouselColors.PrimaryContainer,
    onPrimaryContainer = CarouselColors.OnPrimaryContainer,
    secondary = CarouselColors.Secondary,
    onSecondary = CarouselColors.OnSecondary,
    secondaryContainer = CarouselColors.SecondaryContainer,
    onSecondaryContainer = CarouselColors.OnSecondaryContainer,
    tertiary = CarouselColors.Tertiary,
    onTertiary = CarouselColors.OnTertiary,
    tertiaryContainer = CarouselColors.TertiaryContainer,
    onTertiaryContainer = CarouselColors.OnTertiaryContainer,
    background = CarouselColors.Background,
    onBackground = CarouselColors.OnBackground,
    surface = CarouselColors.Surface,
    onSurface = CarouselColors.OnSurface,
    surfaceVariant = CarouselColors.SurfaceVariant,
    onSurfaceVariant = CarouselColors.OnSurfaceVariant,
    error = CarouselColors.Error,
    onError = CarouselColors.OnError,
    outline = CarouselColors.Outline,
)

/**
 * Typography with a cinematic, elegant feel.
 */
val CarouselTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Cinematic theme for the Vertical Carousel sample app.
 * Always uses dark theme for the movie theater atmosphere.
 */
@Composable
fun CarouselTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CarouselTypography,
        content = content,
    )
}

