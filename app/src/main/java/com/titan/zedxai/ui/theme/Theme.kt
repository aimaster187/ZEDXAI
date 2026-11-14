package com.titan.zedxai.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ZedxThemeMode(val id: String, val label: String) {
    WHITE_CRYSTAL("white_crystal", "White Crystal"),
    DARK_SPACE("dark_space", "Dark Space"),
    SYSTEM_ADAPTIVE("system", "System Adaptive");

    companion object {
        fun fromId(id: String): ZedxThemeMode =
            entries.firstOrNull { it.id == id } ?: WHITE_CRYSTAL
    }
}

data class CrystalTokens(
    val isDark: Boolean,
    val glassSurface: Color,
    val glassBorder: Color,
    val glowCyan: Color,
    val glowViolet: Color,
    val backdropGradient: Brush,
    val heroShimmerBrush: Brush
)

val LocalCrystalTokens = staticCompositionLocalOf {
    CrystalTokens(
        isDark = false,
        glassSurface = CrystalGlass,
        glassBorder = CrystalBorder,
        glowCyan = CyanAccent,
        glowViolet = VioletAccent,
        backdropGradient = Brush.verticalGradient(listOf(CrystalWhite, CrystalFrost, CrystalSurface)),
        heroShimmerBrush = Brush.linearGradient(listOf(IceBluePrimary, CyanAccent, VioletAccent))
    )
}

private val WhiteCrystalScheme = lightColorScheme(
    primary = IceBluePrimary,
    onPrimary = Color.White,
    primaryContainer = IceBlueContainer,
    onPrimaryContainer = CrystalInk,
    secondary = CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = CyanSoft,
    onSecondaryContainer = CrystalInk,
    tertiary = VioletAccent,
    onTertiary = Color.White,
    tertiaryContainer = VioletSoft,
    onTertiaryContainer = CrystalInk,
    background = CrystalWhite,
    onBackground = CrystalInk,
    surface = CrystalSurface,
    onSurface = CrystalInk,
    surfaceVariant = CrystalFrost,
    onSurfaceVariant = CrystalMuted,
    outline = CrystalBorder,
    error = RoseError,
    onError = Color.White
)

private val DarkSpaceScheme = darkColorScheme(
    primary = SpaceIce,
    onPrimary = SpaceVoid,
    primaryContainer = Color(0xFF132D54),
    onPrimaryContainer = SpaceInk,
    secondary = SpaceCyan,
    onSecondary = SpaceVoid,
    secondaryContainer = Color(0xFF0A394A),
    onSecondaryContainer = SpaceInk,
    tertiary = SpaceViolet,
    onTertiary = SpaceVoid,
    tertiaryContainer = Color(0xFF281D4D),
    onTertiaryContainer = SpaceInk,
    background = SpaceVoid,
    onBackground = SpaceInk,
    surface = SpaceSurface,
    onSurface = SpaceInk,
    surfaceVariant = Color(0xFF13223C),
    onSurfaceVariant = SpaceMuted,
    outline = SpaceBorder,
    error = Color(0xFFF87171),
    onError = SpaceVoid
)

@Composable
fun ZedxTheme(
    themeMode: ZedxThemeMode = ZedxThemeMode.WHITE_CRYSTAL,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ZedxThemeMode.WHITE_CRYSTAL -> false
        ZedxThemeMode.DARK_SPACE -> true
        ZedxThemeMode.SYSTEM_ADAPTIVE -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkSpaceScheme
        else -> WhiteCrystalScheme
    }

    val tokens = if (dark) {
        CrystalTokens(
            isDark = true,
            glassSurface = SpaceGlass,
            glassBorder = SpaceBorder,
            glowCyan = SpaceCyan,
            glowViolet = SpaceViolet,
            backdropGradient = Brush.verticalGradient(
                listOf(SpaceVoid, Color(0xFF081428), SpaceSurface)
            ),
            heroShimmerBrush = Brush.linearGradient(
                listOf(SpaceIce, SpaceCyan, SpaceViolet)
            )
        )
    } else {
        CrystalTokens(
            isDark = false,
            glassSurface = CrystalGlass,
            glassBorder = CrystalBorder,
            glowCyan = CyanAccent,
            glowViolet = VioletAccent,
            backdropGradient = Brush.verticalGradient(
                listOf(CrystalWhite, CrystalFrost, Color(0xFFF9FBFF))
            ),
            heroShimmerBrush = Brush.linearGradient(
                listOf(IceBluePrimary, CyanAccent, VioletAccent)
            )
        )
    }

    CompositionLocalProvider(LocalCrystalTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZedxTypography,
            shapes = ZedxShapes,
            content = content
        )
    }
}

@Composable
fun CrystalBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalCrystalTokens.current
    Box(
        modifier = modifier.background(tokens.backdropGradient),
        content = content
    )
}

@Composable
fun CrystalGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val tokens = LocalCrystalTokens.current
    val shape = RoundedCornerShape(cornerRadius)
    val shadowColor = if (tokens.isDark) Color.Black.copy(alpha = 0.5f) else IceBluePrimary.copy(alpha = 0.12f)
    val cardModifier = modifier.shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = tokens.glassSurface),
            border = BorderStroke(1.dp, tokens.glassBorder)
        ) {
            content()
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = tokens.glassSurface),
            border = BorderStroke(1.dp, tokens.glassBorder)
        ) {
            content()
        }
    }
}
