package com.getup.oneui.ui.presentation

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getup.oneui.model.SloganConfig

/**
 * Customizable Slogan/Announcement text view rendered on the secondary cover display.
 * Supports custom color, font size, marquee scrolling, and text alignment.
 */
@Composable
fun SloganView(
    sloganConfig: SloganConfig,
    modifier: Modifier = Modifier
) {
    if (!sloganConfig.isVisible || sloganConfig.text.isBlank()) return

    val textAlign = when (sloganConfig.alignment) {
        SloganConfig.TextAlignment.LEFT -> TextAlign.Left
        SloganConfig.TextAlignment.CENTER -> TextAlign.Center
        SloganConfig.TextAlignment.RIGHT -> TextAlign.Right
    }

    val boxAlignment = when (sloganConfig.alignment) {
        SloganConfig.TextAlignment.LEFT -> Alignment.CenterStart
        SloganConfig.TextAlignment.CENTER -> Alignment.Center
        SloganConfig.TextAlignment.RIGHT -> Alignment.CenterEnd
    }

    val baseModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)

    val sloganModifier = if (sloganConfig.isMarquee) {
        baseModifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            velocity = 40.dp
        )
    } else {
        baseModifier
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = boxAlignment
    ) {
        Text(
            text = sloganConfig.text,
            style = TextStyle(
                color = Color(sloganConfig.textColorHex),
                fontSize = sloganConfig.fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = textAlign,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.6f),
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                )
            ),
            maxLines = if (sloganConfig.isMarquee) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = sloganModifier
        )
    }
}

@Preview(name = "Slogan Center", widthDp = 300, heightDp = 100)
@Composable
fun SloganViewCenterPreview() {
    SloganView(
        sloganConfig = SloganConfig(
            text = "Welcome to Dual Screen Console",
            textColorHex = 0xFF00E5FF,
            fontSizeSp = 24,
            alignment = SloganConfig.TextAlignment.CENTER
        )
    )
}

@Preview(name = "Slogan Marquee", widthDp = 250, heightDp = 80)
@Composable
fun SloganViewMarqueePreview() {
    SloganView(
        sloganConfig = SloganConfig(
            text = "🚀 Breaking News: Dual-Screen Concurrency Active on Cover Display! 🚀",
            textColorHex = 0xFFE94057,
            fontSizeSp = 20,
            isMarquee = true,
            alignment = SloganConfig.TextAlignment.LEFT
        )
    )
}
