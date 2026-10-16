package com.getup.oneui.ui.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import com.getup.oneui.display.DisplayInfo
import com.getup.oneui.ui.theme.双屏并发Theme

/**
 * Status banner showing Samsung foldable rear display status, high-visibility main power toggle,
 * auto-start on unfold configuration, keep screen on toggle, Shizuku State 4 integration,
 * and external display selection.
 */
@Composable
fun StatusBanner(
    connectedDisplays: List<DisplayInfo>,
    isRearDisplayAvailable: Boolean,
    isRearScreenActive: Boolean,
    autoStartOnUnfold: Boolean,
    keepScreenOn: Boolean = true,
    isShizukuInstalled: Boolean = true,
    isShizukuRunning: Boolean = true,
    isShizukuAuthorized: Boolean = false,
    isCoverServiceRunning: Boolean = false,
    selectedDisplayId: Int?,
    onToggleRearDisplay: (Boolean) -> Unit,
    onToggleAutoStartOnUnfold: (Boolean) -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit = {},
    onRequestShizukuPermission: () -> Unit = {},
    onToggleCoverService: (Boolean) -> Unit = {},
    onSelectTargetDisplay: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isExternalConnected = connectedDisplays.isNotEmpty()
    var displayDropdownExpanded by remember { mutableStateOf(false) }

    val activeDisplay = connectedDisplays.find { it.id == selectedDisplayId } ?: connectedDisplays.firstOrNull()

    val cardBorderColor by animateColorAsState(
        targetValue = if (isRearScreenActive || isCoverServiceRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        animationSpec = tween(300),
        label = "cardBorderColor"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isRearScreenActive || isCoverServiceRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300),
        label = "containerColor"
    )

    val statusDotColor by animateColorAsState(
        targetValue = if (isRearScreenActive || isCoverServiceRunning) Color(0xFF00E676) else Color(0xFF9E9E9E),
        animationSpec = tween(300),
        label = "statusDotColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRearScreenActive || isCoverServiceRunning) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // 1. Detection Header & Live Status Indicator Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isCoverServiceRunning -> "外屏常驻服务运行中 (State 4 提权)"
                            isRearScreenActive -> "外屏显示已开启"
                            isRearDisplayAvailable -> "检测到三星外屏 (背屏并发就绪)"
                            isExternalConnected -> "检测到外接副屏 (${connectedDisplays.size}个显示器就绪)"
                            else -> "副屏虚拟镜像已就绪"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isCoverServiceRunning -> "Display 1 · 锁屏/切换应用保持常亮"
                            isRearDisplayAvailable -> "Samsung Foldable · WindowArea / Shizuku"
                            isExternalConnected -> "Secondary Display · Presentation Mode"
                            else -> "Virtual Canvas · Real-time Mirror"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Indicator Pill with pulsing dot
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isRearScreenActive || isCoverServiceRunning) Color(0xFF00E676).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isRearScreenActive || isCoverServiceRunning) Color(0xFF00E676).copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRearScreenActive || isCoverServiceRunning)
                                        statusDotColor.copy(alpha = pulseAlpha)
                                    else
                                        statusDotColor
                                )
                        )
                        Text(
                            text = if (isRearScreenActive || isCoverServiceRunning) "已开启" else "已休眠",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRearScreenActive || isCoverServiceRunning) Color(0xFF00C853)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. High-Visibility Main Action Control Card
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isRearScreenActive || isCoverServiceRunning)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(
                    1.5.dp,
                    if (isRearScreenActive || isCoverServiceRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isRearScreenActive || isCoverServiceRunning)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isRearScreenActive || isCoverServiceRunning)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "外屏背屏显示",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isRearScreenActive || isCoverServiceRunning)
                                    "外屏正在运行时钟与标语"
                                else
                                    "点击开关立即点亮外屏",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = isRearScreenActive || isCoverServiceRunning,
                        onCheckedChange = onToggleRearDisplay
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Unfold Auto-Start Configuration Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Smartphone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "展开大屏时自动开启",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "展开内屏自动点亮外屏背屏",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = autoStartOnUnfold,
                        onCheckedChange = onToggleAutoStartOnUnfold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Keep Screen On Configuration Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockClock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "背屏开启时保持常亮",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "防止系统休眠中断双屏演示",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = onToggleKeepScreenOn
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Shizuku Elevated Concurrency Mode (后台真双屏常驻)
            Text(
                text = "Shizuku 极速真双屏常驻 (State 4 提权)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isShizukuAuthorized) {
                // Fallback / Authorization prompt
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !isShizukuInstalled -> "未检测到 Shizuku 应用"
                                    !isShizukuRunning -> "Shizuku 服务未运行"
                                    else -> "未授予 Shizuku 权限"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (!isShizukuInstalled || !isShizukuRunning) {
                                "未检测到运行中的 Shizuku。当前仅可使用临时 WindowArea 模式（限前台）；启动 Shizuku 后可实现真正的后台常驻双屏。"
                            } else {
                                "未授予 Shizuku 权限，可使用临时 WindowArea 模式（限前台），或开启 Shizuku 实现真正的后台常驻双屏。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        if (isShizukuRunning && !isShizukuAuthorized) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onRequestShizukuPermission,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("一键授权 Shizuku 权限")
                            }
                        }
                    }
                }
            } else {
                // Shizuku is authorized! Show the service toggle and status
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCoverServiceRunning)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        if (isCoverServiceRunning)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isCoverServiceRunning)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isCoverServiceRunning)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Shizuku 后台常驻服务",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isCoverServiceRunning)
                                        "前台服务运行中 · 锁屏/切后台外屏持续显示"
                                    else
                                        "已提权 · 开启后退出应用或锁屏外屏仍常驻",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Switch(
                            checked = isCoverServiceRunning,
                            onCheckedChange = onToggleCoverService
                        )
                    }
                }
            }

            // 6. Dropdown for External Monitors (if multiple monitors connected)
            AnimatedVisibility(visible = isExternalConnected && connectedDisplays.size > 1) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box {
                        OutlinedButton(
                            onClick = { displayDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DisplaySettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("目标显示器: ${activeDisplay?.name ?: "默认"} (${activeDisplay?.width}x${activeDisplay?.height})")
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
fun StatusBannerRearActivePreview() {
    双屏并发Theme {
        StatusBanner(
            connectedDisplays = emptyList(),
            isRearDisplayAvailable = true,
            isRearScreenActive = true,
            autoStartOnUnfold = true,
            keepScreenOn = true,
            isShizukuInstalled = true,
            isShizukuRunning = true,
            isShizukuAuthorized = true,
            isCoverServiceRunning = true,
            selectedDisplayId = null,
            onToggleRearDisplay = {},
            onToggleAutoStartOnUnfold = {},
            onToggleKeepScreenOn = {},
            onRequestShizukuPermission = {},
            onToggleCoverService = {},
            onSelectTargetDisplay = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerRearSleepingPreview() {
    双屏并发Theme {
        StatusBanner(
            connectedDisplays = emptyList(),
            isRearDisplayAvailable = true,
            isRearScreenActive = false,
            autoStartOnUnfold = false,
            keepScreenOn = true,
            isShizukuInstalled = true,
            isShizukuRunning = true,
            isShizukuAuthorized = false,
            isCoverServiceRunning = false,
            selectedDisplayId = null,
            onToggleRearDisplay = {},
            onToggleAutoStartOnUnfold = {},
            onToggleKeepScreenOn = {},
            onRequestShizukuPermission = {},
            onToggleCoverService = {},
            onSelectTargetDisplay = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusBannerExternalConnectedPreview() {
    双屏并发Theme {
        StatusBanner(
            connectedDisplays = listOf(
                DisplayInfo(id = 1, name = "External Monitor", width = 1920, height = 1080, refreshRate = 60f, isPresentationDisplay = true)
            ),
            isRearDisplayAvailable = false,
            isRearScreenActive = true,
            autoStartOnUnfold = true,
            keepScreenOn = true,
            isShizukuInstalled = true,
            isShizukuRunning = true,
            isShizukuAuthorized = true,
            isCoverServiceRunning = true,
            selectedDisplayId = 1,
            onToggleRearDisplay = {},
            onToggleAutoStartOnUnfold = {},
            onToggleKeepScreenOn = {},
            onRequestShizukuPermission = {},
            onToggleCoverService = {},
            onSelectTargetDisplay = {}
        )
    }
}
