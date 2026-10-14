package com.getup.oneui.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getup.oneui.model.BatteryDisplayOption
import com.getup.oneui.model.ClockStyle
import com.getup.oneui.model.SecondaryDisplayConfig
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.model.WallpaperOption
import com.getup.oneui.ui.theme.双屏并发Theme

/**
 * Top-level Presentation Composable rendered on the secondary/cover screen display.
 * Integrates dynamic wallpaper background, status bar battery indicator, real-time clock face,
 * and customizable slogan announcement.
 */
@Composable
fun PresentationScreen(
    config: SecondaryDisplayConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Dynamic Wallpaper Background with Brightness Dim Control
        WallpaperBackground(
            wallpaper = config.wallpaper,
            dimRatio = (1.0f - config.brightnessLevel).coerceIn(0f, 0.9f)
        )

        // 2. Responsive Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Battery status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatteryView(
                    batteryOption = config.batteryOption
                )
            }

            // Center Area: Main Clock Face
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ClockView(
                    clockStyle = config.clockStyle,
                    is24HourFormat = config.is24HourFormat,
                    showSeconds = config.showSeconds
                )
            }

            // Bottom Area: Customizable Text Slogan / Announcement
            if (config.sloganConfig.isVisible && config.sloganConfig.text.isNotBlank()) {
                SloganView(
                    sloganConfig = config.sloganConfig
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Preview(name = "Cover Screen Square - Digital Bold", widthDp = 300, heightDp = 300)
@Composable
fun PresentationScreenSquarePreview() {
    双屏并发Theme {
        PresentationScreen(
            config = SecondaryDisplayConfig(
                clockStyle = ClockStyle.DIGITAL_BOLD,
                wallpaper = WallpaperOption.DEFAULT_GRADIENTS[0],
                sloganConfig = SloganConfig(text = "Dual-Screen Active", textColorHex = 0xFFFFFFFF),
                batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.HORIZONTAL)
            )
        )
    }
}

@Preview(name = "Cover Screen Cyberpunk - Digital Neon", widthDp = 300, heightDp = 300)
@Composable
fun PresentationScreenCyberpunkPreview() {
    双屏并发Theme {
        PresentationScreen(
            config = SecondaryDisplayConfig(
                clockStyle = ClockStyle.DIGITAL_NEON,
                wallpaper = WallpaperOption.DEFAULT_GRADIENTS[2],
                sloganConfig = SloganConfig(text = "Future Is Now", textColorHex = 0xFF00E5FF, isMarquee = true),
                batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.CIRCULAR)
            )
        )
    }
}

@Preview(name = "Cover Screen Analog Modern", widthDp = 300, heightDp = 300)
@Composable
fun PresentationScreenAnalogPreview() {
    双屏并发Theme {
        PresentationScreen(
            config = SecondaryDisplayConfig(
                clockStyle = ClockStyle.ANALOG_MODERN,
                wallpaper = WallpaperOption.DEFAULT_GRADIENTS[1],
                sloganConfig = SloganConfig(text = "Enjoy Your Day", textColorHex = 0xFFFFD700),
                batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.MINIMAL)
            )
        )
    }
}

@Preview(name = "External Display Tall - Digital Minimal", widthDp = 360, heightDp = 640)
@Composable
fun PresentationScreenTallPreview() {
    双屏并发Theme {
        PresentationScreen(
            config = SecondaryDisplayConfig(
                clockStyle = ClockStyle.DIGITAL_MINIMAL,
                wallpaper = WallpaperOption.DEFAULT_GRADIENTS[3],
                sloganConfig = SloganConfig(text = "OneLab Cover Display", textColorHex = 0xFFFFFFFF),
                batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.HORIZONTAL)
            )
        )
    }
}
