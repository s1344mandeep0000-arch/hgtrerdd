package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.model.MinecraftEdition

private val MinecraftStudioDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = EmeraldSecondary,
    onSecondary = EmeraldOnPrimary,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = EmeraldOnContainer,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainer = ObsidianCard,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutline,
    error = StatusNotSupported,
    onError = ObsidianTextPrimary
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun CraftForgeTheme(
    edition: MinecraftEdition = MinecraftEdition.JAVA,
    content: @Composable () -> Unit
) {
    // Unified clean dark theme with subtle Minecraft emerald-green accents across both engines
    @Suppress("UNUSED_VARIABLE")
    val activeEngine = edition

    MaterialTheme(
        colorScheme = MinecraftStudioDarkColorScheme,
        typography = CraftForgeTypography,
        shapes = StudioShapes,
        content = content
    )
}
