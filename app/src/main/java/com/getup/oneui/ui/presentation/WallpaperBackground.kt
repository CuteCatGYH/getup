package com.getup.oneui.ui.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.getup.oneui.model.WallpaperOption
import kotlin.math.cos
import kotlin.math.sin

/**
 * Composable rendering dynamic wallpaper backgrounds for the cover display / secondary screen.
 * Supports solid colors, linear gradients with custom angles, and custom image URIs.
 */
@Composable
fun WallpaperBackground(
    wallpaper: WallpaperOption,
    modifier: Modifier = Modifier,
    dimRatio: Float = 0f
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .applyWallpaperStyle(wallpaper)
    ) {
        if (wallpaper is WallpaperOption.CustomImage && wallpaper.imageUri.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(wallpaper.imageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover Wallpaper Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dimming overlay for brightness control / readability
        if (dimRatio > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimRatio.coerceIn(0f, 0.9f)))
            )
        }
    }
}

private fun Modifier.applyWallpaperStyle(wallpaper: WallpaperOption): Modifier {
    return when (wallpaper) {
        is WallpaperOption.PresetColor -> {
            this.background(Color(wallpaper.colorHex))
        }

        is WallpaperOption.PresetGradient -> {
            val startColor = Color(wallpaper.startColorHex)
            val endColor = Color(wallpaper.endColorHex)
            val angleRad = Math.toRadians(wallpaper.angleDegrees.toDouble())

            val x = cos(angleRad).toFloat()
            val y = sin(angleRad).toFloat()

            val brush = Brush.linearGradient(
                colors = listOf(startColor, endColor),
                start = Offset(0f, 0f),
                end = Offset(1000f * x + 500f, 1000f * y + 500f)
            )
            this.background(brush)
        }

        is WallpaperOption.CustomImage -> {
            // Default background color while image is loading or if URI is empty
            this.background(Color(0xFF121212))
        }
    }
}

@Preview(name = "Gradient Midnight", widthDp = 300, heightDp = 300)
@Composable
fun WallpaperBackgroundGradientPreview() {
    WallpaperBackground(
        wallpaper = WallpaperOption.DEFAULT_GRADIENTS[0]
    )
}

@Preview(name = "Gradient Cyberpunk", widthDp = 300, heightDp = 300)
@Composable
fun WallpaperBackgroundCyberpunkPreview() {
    WallpaperBackground(
        wallpaper = WallpaperOption.DEFAULT_GRADIENTS[2]
    )
}

@Preview(name = "Preset Color", widthDp = 300, heightDp = 300)
@Composable
fun WallpaperBackgroundColorPreview() {
    WallpaperBackground(
        wallpaper = WallpaperOption.DEFAULT_COLORS[0]
    )
}
