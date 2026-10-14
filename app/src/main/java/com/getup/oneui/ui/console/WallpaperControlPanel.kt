package com.getup.oneui.ui.console

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getup.oneui.model.WallpaperOption
import com.getup.oneui.ui.theme.双屏并发Theme

private enum class WallpaperTab {
    GRADIENTS, SOLIDS, CUSTOM
}

/**
 * Control panel for wallpaper selection (gradient presets, solid colors, custom image loader, brightness slider).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallpaperControlPanel(
    wallpaper: WallpaperOption,
    brightnessLevel: Float,
    onWallpaperChange: (WallpaperOption) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember {
        mutableStateOf(
            when (wallpaper) {
                is WallpaperOption.PresetColor -> WallpaperTab.SOLIDS
                is WallpaperOption.PresetGradient -> WallpaperTab.GRADIENTS
                is WallpaperOption.CustomImage -> WallpaperTab.CUSTOM
            }
        )
    }

    var customUriText by remember {
        mutableStateOf((wallpaper as? WallpaperOption.CustomImage)?.imageUri ?: "")
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val uriString = it.toString()
            customUriText = uriString
            onWallpaperChange(WallpaperOption.CustomImage(imageUri = uriString))
        }
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Wallpaper & Brightness",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == WallpaperTab.GRADIENTS,
                    onClick = { selectedTab = WallpaperTab.GRADIENTS },
                    label = { Text("Gradients") }
                )
                FilterChip(
                    selected = selectedTab == WallpaperTab.SOLIDS,
                    onClick = { selectedTab = WallpaperTab.SOLIDS },
                    label = { Text("Solid Colors") }
                )
                FilterChip(
                    selected = selectedTab == WallpaperTab.CUSTOM,
                    onClick = { selectedTab = WallpaperTab.CUSTOM },
                    label = { Text("Custom Image") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content according to selected tab
            when (selectedTab) {
                WallpaperTab.GRADIENTS -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WallpaperOption.DEFAULT_GRADIENTS.forEach { preset ->
                            val isSelected = (wallpaper as? WallpaperOption.PresetGradient)?.id == preset.id
                            val brush = Brush.linearGradient(
                                listOf(Color(preset.startColorHex), Color(preset.endColorHex))
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brush)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onWallpaperChange(preset) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                WallpaperTab.SOLIDS -> {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WallpaperOption.DEFAULT_COLORS.forEach { preset ->
                            val isSelected = (wallpaper as? WallpaperOption.PresetColor)?.id == preset.id

                            Box(
                                modifier = Modifier
                                    .size(width = 90.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(preset.colorHex))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onWallpaperChange(preset) }
                                    .padding(6.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (preset.colorHex == 0xFF000000L || preset.colorHex == 0xFF121212L) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                WallpaperTab.CUSTOM -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Image from Device Gallery")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customUriText,
                            onValueChange = { text ->
                                customUriText = text
                                if (text.isNotBlank()) {
                                    onWallpaperChange(WallpaperOption.CustomImage(imageUri = text))
                                }
                            },
                            label = { Text("Image Content URI") },
                            placeholder = { Text("content:// or https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Brightness Level Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Display Brightness",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(brightnessLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = brightnessLevel,
                onValueChange = onBrightnessChange,
                valueRange = 0.1f..1.0f
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WallpaperControlPanelPreview() {
    双屏并发Theme {
        WallpaperControlPanel(
            wallpaper = WallpaperOption.DEFAULT_GRADIENTS[0],
            brightnessLevel = 0.9f,
            onWallpaperChange = {},
            onBrightnessChange = {}
        )
    }
}
