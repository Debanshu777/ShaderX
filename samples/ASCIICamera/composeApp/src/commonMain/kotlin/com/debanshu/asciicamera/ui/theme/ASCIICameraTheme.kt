package com.debanshu.asciicamera.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private object ASCIICameraColors {
    val Primary = Color(0xFF00897B)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFF004D40)
    val OnPrimaryContainer = Color(0xFFB2DFDB)

    val Secondary = Color(0xFFFFB300)
    val OnSecondary = Color(0xFF1A1A1A)
    val SecondaryContainer = Color(0xFF5D4037)
    val OnSecondaryContainer = Color(0xFFFFE082)

    val Tertiary = Color(0xFF7C4DFF)
    val OnTertiary = Color(0xFFFFFFFF)
    val TertiaryContainer = Color(0xFF311B92)
    val OnTertiaryContainer = Color(0xFFD1C4E9)

    val SurfaceDark = Color(0xFF121212)
    val SurfaceVariantDark = Color(0xFF1E1E1E)
    val OnSurfaceDark = Color(0xFFE0E0E0)
    val OnSurfaceVariantDark = Color(0xFFB0B0B0)

    val SurfaceLight = Color(0xFFF5F5F5)
    val SurfaceVariantLight = Color(0xFFE8E8E8)
    val OnSurfaceLight = Color(0xFF1A1A1A)
    val OnSurfaceVariantLight = Color(0xFF5A5A5A)

    val BackgroundDark = Color(0xFF0A0A0A)
    val OnBackgroundDark = Color(0xFFE0E0E0)
    val BackgroundLight = Color(0xFFFAFAFA)
    val OnBackgroundLight = Color(0xFF1A1A1A)

    val Error = Color(0xFFCF6679)
    val OnError = Color(0xFF000000)
    val ErrorContainer = Color(0xFFB71C1C)
    val OnErrorContainer = Color(0xFFFFCDD2)

    val OutlineDark = Color(0xFF3A3A3A)
    val OutlineLight = Color(0xFFBDBDBD)
}

private val DarkColorScheme =
    darkColorScheme(
        primary = ASCIICameraColors.Primary,
        onPrimary = ASCIICameraColors.OnPrimary,
        primaryContainer = ASCIICameraColors.PrimaryContainer,
        onPrimaryContainer = ASCIICameraColors.OnPrimaryContainer,
        secondary = ASCIICameraColors.Secondary,
        onSecondary = ASCIICameraColors.OnSecondary,
        secondaryContainer = ASCIICameraColors.SecondaryContainer,
        onSecondaryContainer = ASCIICameraColors.OnSecondaryContainer,
        tertiary = ASCIICameraColors.Tertiary,
        onTertiary = ASCIICameraColors.OnTertiary,
        tertiaryContainer = ASCIICameraColors.TertiaryContainer,
        onTertiaryContainer = ASCIICameraColors.OnTertiaryContainer,
        background = ASCIICameraColors.BackgroundDark,
        onBackground = ASCIICameraColors.OnBackgroundDark,
        surface = ASCIICameraColors.SurfaceDark,
        onSurface = ASCIICameraColors.OnSurfaceDark,
        surfaceVariant = ASCIICameraColors.SurfaceVariantDark,
        onSurfaceVariant = ASCIICameraColors.OnSurfaceVariantDark,
        error = ASCIICameraColors.Error,
        onError = ASCIICameraColors.OnError,
        errorContainer = ASCIICameraColors.ErrorContainer,
        onErrorContainer = ASCIICameraColors.OnErrorContainer,
        outline = ASCIICameraColors.OutlineDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = ASCIICameraColors.Primary,
        onPrimary = ASCIICameraColors.OnPrimary,
        primaryContainer = Color(0xFFB2DFDB),
        onPrimaryContainer = Color(0xFF00251A),
        secondary = ASCIICameraColors.Secondary,
        onSecondary = ASCIICameraColors.OnSecondary,
        secondaryContainer = Color(0xFFFFE082),
        onSecondaryContainer = Color(0xFF3E2723),
        tertiary = ASCIICameraColors.Tertiary,
        onTertiary = ASCIICameraColors.OnTertiary,
        tertiaryContainer = Color(0xFFD1C4E9),
        onTertiaryContainer = Color(0xFF1A0033),
        background = ASCIICameraColors.BackgroundLight,
        onBackground = ASCIICameraColors.OnBackgroundLight,
        surface = ASCIICameraColors.SurfaceLight,
        onSurface = ASCIICameraColors.OnSurfaceLight,
        surfaceVariant = ASCIICameraColors.SurfaceVariantLight,
        onSurfaceVariant = ASCIICameraColors.OnSurfaceVariantLight,
        error = Color(0xFFB00020),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD4),
        onErrorContainer = Color(0xFF410002),
        outline = ASCIICameraColors.OutlineLight,
    )

private val ASCIICameraTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

@Composable fun ASCIICameraTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ASCIICameraTypography,
        content = content,
    )
}
