package com.getup.oneui.ui.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getup.oneui.model.BatteryDisplayOption

/**
 * Real-time Battery Status indicator Composable bound to Android system battery events.
 * Supports horizontal, vertical, circular, and minimal visual styles.
 */
@Composable
fun BatteryView(
    batteryOption: BatteryDisplayOption,
    modifier: Modifier = Modifier,
    overrideLevel: Int? = null,
    overrideIsCharging: Boolean? = null
) {
    if (!batteryOption.isVisible) return

    val context = LocalContext.current
    var batteryLevel by remember { mutableIntStateOf(overrideLevel ?: 100) }
    var isCharging by remember { mutableStateOf(overrideIsCharging ?: false) }

    DisposableEffect(context, overrideLevel, overrideIsCharging) {
        if (overrideLevel != null && overrideIsCharging != null) {
            batteryLevel = overrideLevel
            isCharging = overrideIsCharging
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                        if (level >= 0 && scale > 0) {
                            batteryLevel = ((level / scale.toFloat()) * 100).toInt()
                        }
                        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                    }
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(receiver, filter)

            // Initial state evaluation from sticky intent
            stickyIntent?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                if (level >= 0 && scale > 0) {
                    batteryLevel = ((level / scale.toFloat()) * 100).toInt()
                }
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore if receiver already unregistered
                }
            }
        }
    }

    val batteryColor = remember(batteryLevel, isCharging, batteryOption.lowBatteryThreshold) {
        when {
            isCharging -> Color(0xFF34C759) // Charging Green
            batteryLevel <= batteryOption.lowBatteryThreshold -> Color(0xFFFF3B30) // Low Battery Red
            else -> Color.White
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (batteryOption.style) {
            BatteryDisplayOption.BatteryStyle.HORIZONTAL -> HorizontalBattery(
                level = batteryLevel,
                isCharging = isCharging,
                color = batteryColor,
                showPercentage = batteryOption.showPercentage
            )

            BatteryDisplayOption.BatteryStyle.VERTICAL -> VerticalBattery(
                level = batteryLevel,
                isCharging = isCharging,
                color = batteryColor,
                showPercentage = batteryOption.showPercentage
            )

            BatteryDisplayOption.BatteryStyle.CIRCULAR -> CircularBattery(
                level = batteryLevel,
                isCharging = isCharging,
                color = batteryColor,
                showPercentage = batteryOption.showPercentage
            )

            BatteryDisplayOption.BatteryStyle.MINIMAL -> MinimalBattery(
                level = batteryLevel,
                isCharging = isCharging,
                color = batteryColor,
                showPercentage = batteryOption.showPercentage
            )
        }
    }
}

@Composable
private fun HorizontalBattery(
    level: Int,
    isCharging: Boolean,
    color: Color,
    showPercentage: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        if (showPercentage) {
            Text(
                text = "$level%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(end = 6.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(16.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((level / 100f).coerceIn(0.05f, 1.0f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )

                if (isCharging) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Charging",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            // Battery Tip / Cap
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                    .background(Color.White.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
private fun VerticalBattery(
    level: Int,
    isCharging: Boolean,
    color: Color,
    showPercentage: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Battery Top Cap
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(Color.White.copy(alpha = 0.8f))
            )

            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(36.dp)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight((level / 100f).coerceIn(0.05f, 1.0f))
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )

                if (isCharging) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Charging",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.Center)
                    )
                }
            }
        }

        if (showPercentage) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$level%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CircularBattery(
    level: Int,
    isCharging: Boolean,
    color: Color,
    showPercentage: Boolean
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(54.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(54.dp)) {
            val strokeWidth = 4.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Background track
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Progress arc
            val sweepAngle = (level / 100f) * 360f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isCharging) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Charging",
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showPercentage) {
                Text(
                    text = "$level%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun MinimalBattery(
    level: Int,
    isCharging: Boolean,
    color: Color,
    showPercentage: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))

        if (isCharging) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "Charging",
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }

        if (showPercentage) {
            Text(
                text = "$level%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Preview(name = "Horizontal Charging", widthDp = 150, heightDp = 60)
@Composable
fun BatteryViewHorizontalPreview() {
    BatteryView(
        batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.HORIZONTAL),
        overrideLevel = 85,
        overrideIsCharging = true
    )
}

@Preview(name = "Vertical Low Battery", widthDp = 100, heightDp = 100)
@Composable
fun BatteryViewVerticalPreview() {
    BatteryView(
        batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.VERTICAL),
        overrideLevel = 15,
        overrideIsCharging = false
    )
}

@Preview(name = "Circular Style", widthDp = 100, heightDp = 100)
@Composable
fun BatteryViewCircularPreview() {
    BatteryView(
        batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.CIRCULAR),
        overrideLevel = 62,
        overrideIsCharging = false
    )
}

@Preview(name = "Minimal Style", widthDp = 120, heightDp = 50)
@Composable
fun BatteryViewMinimalPreview() {
    BatteryView(
        batteryOption = BatteryDisplayOption(style = BatteryDisplayOption.BatteryStyle.MINIMAL),
        overrideLevel = 90,
        overrideIsCharging = true
    )
}
