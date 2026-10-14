package com.getup.oneui.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.ui.theme.双屏并发Theme

/**
 * Status banner showing secondary display connection state, active display selector,
 * and presentation activation toggle switch.
 */
@Composable
fun StatusBanner(
    connectedDisplays: List<DisplayInfo>,
    isPresentationEnabled: Boolean,
    isPresentationShowing: Boolean,
    selectedDisplayId: Int?,
    onTogglePresentation: (Boolean) -> Unit,
    onSelectTargetDisplay: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectedDisplays.isNotEmpty()
    var displayDropdownExpanded by remember { mutableStateOf(false) }

    val activeDisplay = connectedDisplays.find { it.id == selectedDisplayId } ?: connectedDisplays.firstOrNull()

    val containerColor = if (isConnected) {
        if (isPresentationShowing) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val statusDotColor by animateColorAsState(
        targetValue = when {
            isConnected && isPresentationShowing -> Color(0xFF00E676)
            isConnected -> Color(0xFFFFB300)
            else -> MaterialTheme.colorScheme.outline
        },
        label = "statusDotColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (isConnected) "Secondary Display Detected" else "No Secondary Display Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = when {
                                isConnected && isPresentationShowing -> "Presentation active on ${activeDisplay?.name ?: "Display #${activeDisplay?.id}"}"
                                isConnected -> "Connected (${connectedDisplays.size} available). Ready to activate."
                                else -> "Virtual mirror preview enabled below"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isPresentationEnabled,
                    onCheckedChange = onTogglePresentation,
                    thumbContent = {
                        Icon(
                            imageVector = if (isPresentationEnabled) Icons.Default.Tv else Icons.Default.TvOff,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                )
            }

            AnimatedVisibility(visible = isConnected && connectedDisplays.size > 1) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box {
                        OutlinedButton(
                            onClick = { displayDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DisplaySettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Target Display: ${activeDisplay?.name ?: "Default"} (${activeDisplay?.width}x${activeDisplay?.height})")
                        }

                        DropdownMenu(
                            expanded = displayDropdownExpanded,
                            onDismissRequest = { displayDropdownExpanded = false }
                        ) {
                            connectedDisplays.forEach { display ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (display.id == activeDisplay?.id) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text("${display.name} (${display.width}x${display.height} @ ${display.refreshRate.toInt()}Hz)")
                                        }
                                    },
                                    onClick = {
                                        onSelectTargetDisplay(display.id)
                                        displayDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerConnectedPreview() {
    双屏并发Theme {
        StatusBanner(
            connectedDisplays = listOf(
                DisplayInfo(id = 1, name = "External Monitor", width = 1920, height = 1080, refreshRate = 60f, isPresentationDisplay = true)
            ),
            isPresentationEnabled = true,
            isPresentationShowing = true,
            selectedDisplayId = 1,
            onTogglePresentation = {},
            onSelectTargetDisplay = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerDisconnectedPreview() {
    双屏并发Theme {
        StatusBanner(
            connectedDisplays = emptyList(),
            isPresentationEnabled = false,
            isPresentationShowing = false,
            selectedDisplayId = null,
            onTogglePresentation = {},
            onSelectTargetDisplay = {}
        )
    }
}
