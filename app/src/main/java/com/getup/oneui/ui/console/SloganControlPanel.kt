package com.getup.oneui.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getup.oneui.model.SloganConfig
import com.getup.oneui.ui.theme.双屏并发Theme

/**
 * Color preset option for slogan text color selection.
 */
private data class ColorPreset(val name: String, val colorHex: Long)

private val SLOGAN_COLOR_PRESETS = listOf(
    ColorPreset("Pure White", 0xFFFFFFFF),
    ColorPreset("Cyber Cyan", 0xFF00E5FF),
    ColorPreset("Gold Yellow", 0xFFFFD700),
    ColorPreset("Lime Green", 0xFF00E676),
    ColorPreset("Neon Pink", 0xFFFF4081),
    ColorPreset("Coral Orange", 0xFFFF6E40),
    ColorPreset("Bright Purple", 0xFFE040FB),
    ColorPreset("Slate Grey", 0xFF9E9E9E)
)

/**
 * Control panel for slogan text editor (text input, font size slider, text color picker, marquee, alignment).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SloganControlPanel(
    sloganConfig: SloganConfig,
    onSloganConfigChange: (SloganConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Slogan & Announcement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Switch(
                    checked = sloganConfig.isVisible,
                    onCheckedChange = { onSloganConfigChange(sloganConfig.copy(isVisible = it)) }
                )
            }

            AnimatedVisibility(visible = sloganConfig.isVisible) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Text Field Input
                    OutlinedTextField(
                        value = sloganConfig.text,
                        onValueChange = { onSloganConfigChange(sloganConfig.copy(text = it)) },
                        label = { Text("Slogan Text") },
                        placeholder = { Text("Enter text announcement...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (sloganConfig.text.isNotEmpty()) {
                                IconButton(onClick = { onSloganConfigChange(sloganConfig.copy(text = "")) }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Font Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${sloganConfig.fontSizeSp} sp",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = sloganConfig.fontSizeSp.toFloat(),
                        onValueChange = { onSloganConfigChange(sloganConfig.copy(fontSizeSp = it.toInt())) },
                        valueRange = 16f..48f,
                        steps = 32
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Text Color Swatches
                    Text(
                        text = "Text Color",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SLOGAN_COLOR_PRESETS.forEach { preset ->
                            val isSelected = sloganConfig.textColorHex == preset.colorHex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.colorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { onSloganConfigChange(sloganConfig.copy(textColorHex = preset.colorHex)) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    val iconColor = if (preset.colorHex == 0xFFFFFFFFL || preset.colorHex == 0xFFFFD700L) Color.Black else Color.White
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Text Alignment Buttons
                    Text(
                        text = "Alignment",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val aligns = listOf(
                            SloganConfig.TextAlignment.LEFT to Icons.AutoMirrored.Default.FormatAlignLeft,
                            SloganConfig.TextAlignment.CENTER to Icons.Default.FormatAlignCenter,
                            SloganConfig.TextAlignment.RIGHT to Icons.AutoMirrored.Default.FormatAlignRight
                        )

                        aligns.forEach { (align, icon) ->
                            val isSelected = sloganConfig.alignment == align
                            OutlinedButton(
                                onClick = { onSloganConfigChange(sloganConfig.copy(alignment = align)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else null
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = align.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Marquee Toggle Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Marquee Animation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Continuous scrolling effect for long announcement texts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = sloganConfig.isMarquee,
                            onCheckedChange = { onSloganConfigChange(sloganConfig.copy(isMarquee = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SloganControlPanelPreview() {
    双屏并发Theme {
        SloganControlPanel(
            sloganConfig = SloganConfig(),
            onSloganConfigChange = {}
        )
    }
}
