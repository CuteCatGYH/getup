package com.getup.oneui.ui.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getup.oneui.model.ClockStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/**
 * Responsive real-time Clock Composable rendering digital or analog clock faces
 * on the cover display according to the selected [ClockStyle].
 */
@Composable
fun ClockView(
    clockStyle: ClockStyle,
    is24HourFormat: Boolean = true,
    showSeconds: Boolean = true,
    modifier: Modifier = Modifier,
    overrideTimeMs: Long? = null
) {
    var currentTimeMs by remember { mutableLongStateOf(overrideTimeMs ?: System.currentTimeMillis()) }

    LaunchedEffect(overrideTimeMs) {
        if (overrideTimeMs == null) {
            while (isActive) {
                currentTimeMs = System.currentTimeMillis()
                // Refresh interval: shorter for smooth hand animation or second counter
                delay(100)
            }
        } else {
            currentTimeMs = overrideTimeMs
        }
    }

    val localTime = remember(currentTimeMs) {
        Instant.ofEpochMilli(currentTimeMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (clockStyle) {
            ClockStyle.DIGITAL_MINIMAL -> DigitalMinimalClock(localTime, is24HourFormat, showSeconds)
            ClockStyle.DIGITAL_BOLD -> DigitalBoldClock(localTime, is24HourFormat, showSeconds)
            ClockStyle.DIGITAL_NEON -> DigitalNeonClock(localTime, is24HourFormat, showSeconds)
            ClockStyle.ANALOG_CLASSIC -> AnalogClassicClock(localTime, showSeconds)
            ClockStyle.ANALOG_MODERN -> AnalogModernClock(localTime, showSeconds, currentTimeMs)
        }
    }
}

@Composable
private fun DigitalMinimalClock(
    time: LocalTime,
    is24Hour: Boolean,
    showSeconds: Boolean
) {
    val pattern = when {
        is24Hour && showSeconds -> "HH:mm:ss"
        is24Hour -> "HH:mm"
        showSeconds -> "hh:mm:ss"
        else -> "hh:mm"
    }
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val formattedTime = time.format(formatter)
    val amPm = if (!is24Hour) if (time.hour >= 12) "PM" else "AM" else ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = formattedTime,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = Color.White,
            letterSpacing = 2.sp
        )
        if (amPm.isNotEmpty()) {
            Text(
                text = amPm,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun DigitalBoldClock(
    time: LocalTime,
    is24Hour: Boolean,
    showSeconds: Boolean
) {
    val hoursStr = if (is24Hour) {
        String.format("%02d", time.hour)
    } else {
        val h = if (time.hour % 12 == 0) 12 else time.hour % 12
        String.format("%02d", h)
    }
    val minutesStr = String.format("%02d", time.minute)
    val secondsStr = String.format("%02d", time.second)
    val amPm = if (!is24Hour) if (time.hour >= 12) "PM" else "AM" else ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$hoursStr:$minutesStr",
                fontSize = 64.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-1).sp
            )
            if (showSeconds) {
                Text(
                    text = ":$secondsStr",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                    modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                )
            }
        }
        if (amPm.isNotEmpty()) {
            Text(
                text = amPm,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun DigitalNeonClock(
    time: LocalTime,
    is24Hour: Boolean,
    showSeconds: Boolean
) {
    val hoursStr = if (is24Hour) {
        String.format("%02d", time.hour)
    } else {
        val h = if (time.hour % 12 == 0) 12 else time.hour % 12
        String.format("%02d", h)
    }
    val minutesStr = String.format("%02d", time.minute)
    val secondsStr = String.format("%02d", time.second)
    val amPm = if (!is24Hour) if (time.hour >= 12) "PM" else "AM" else ""

    val neonCyan = Color(0xFF00E5FF)
    val neonMagenta = Color(0xFFFF007F)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = hoursStr,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = neonCyan
            )
            Text(
                text = ":",
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                color = neonMagenta
            )
            Text(
                text = minutesStr,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = neonCyan
            )
            if (showSeconds) {
                Text(
                    text = ":$secondsStr",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = neonMagenta,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        if (amPm.isNotEmpty()) {
            Text(
                text = amPm,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = neonCyan.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun AnalogClassicClock(
    time: LocalTime,
    showSeconds: Boolean
) {
    val hour = time.hour % 12
    val minute = time.minute
    val second = time.second

    val hourAngle = (hour + minute / 60f) * 30f
    val minuteAngle = (minute + second / 60f) * 6f
    val secondAngle = second * 6f

    Canvas(
        modifier = Modifier
            .size(200.dp)
            .aspectRatio(1f)
            .padding(12.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f

        // Outer rim
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            center = center,
            radius = radius,
            style = Stroke(width = 4.dp.toPx())
        )

        // Ticks for hours
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val innerRadius = radius - 12.dp.toPx()
            val outerRadius = radius - 4.dp.toPx()

            val start = Offset(
                x = center.x + innerRadius * sin(angle).toFloat(),
                y = center.y - innerRadius * cos(angle).toFloat()
            )
            val end = Offset(
                x = center.x + outerRadius * sin(angle).toFloat(),
                y = center.y - outerRadius * cos(angle).toFloat()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Hour Hand
        val hourRad = Math.toRadians(hourAngle.toDouble())
        val hourHandLength = radius * 0.5f
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(
                x = center.x + hourHandLength * sin(hourRad).toFloat(),
                y = center.y - hourHandLength * cos(hourRad).toFloat()
            ),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Minute Hand
        val minRad = Math.toRadians(minuteAngle.toDouble())
        val minHandLength = radius * 0.72f
        drawLine(
            color = Color.White.copy(alpha = 0.9f),
            start = center,
            end = Offset(
                x = center.x + minHandLength * sin(minRad).toFloat(),
                y = center.y - minHandLength * cos(minRad).toFloat()
            ),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Second Hand
        if (showSeconds) {
            val secRad = Math.toRadians(secondAngle.toDouble())
            val secHandLength = radius * 0.85f
            drawLine(
                color = Color(0xFFFF3B30),
                start = center,
                end = Offset(
                    x = center.x + secHandLength * sin(secRad).toFloat(),
                    y = center.y - secHandLength * cos(secRad).toFloat()
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Center Pin
        drawCircle(
            color = Color(0xFFFF3B30),
            center = center,
            radius = 5.dp.toPx()
        )
    }
}

@Composable
private fun AnalogModernClock(
    time: LocalTime,
    showSeconds: Boolean,
    currentTimeMs: Long
) {
    val hour = time.hour % 12
    val minute = time.minute
    val millis = currentTimeMs % 1000
    val secondFraction = time.second + (millis / 1000f)

    val hourAngle = (hour + minute / 60f) * 30f
    val minuteAngle = (minute + secondFraction / 60f) * 6f
    val secondAngle = secondFraction * 6f

    Canvas(
        modifier = Modifier
            .size(200.dp)
            .aspectRatio(1f)
            .padding(12.dp)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f

        // Minimal cardinal dots (12, 3, 6, 9)
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90).toDouble())
            val dotPos = Offset(
                x = center.x + (radius - 8.dp.toPx()) * sin(angle).toFloat(),
                y = center.y - (radius - 8.dp.toPx()) * cos(angle).toFloat()
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                center = dotPos,
                radius = 3.dp.toPx()
            )
        }

        // Hour Hand (Sleek modern blade)
        val hourRad = Math.toRadians(hourAngle.toDouble())
        val hourHandLength = radius * 0.48f
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(
                x = center.x + hourHandLength * sin(hourRad).toFloat(),
                y = center.y - hourHandLength * cos(hourRad).toFloat()
            ),
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Minute Hand
        val minRad = Math.toRadians(minuteAngle.toDouble())
        val minHandLength = radius * 0.75f
        drawLine(
            color = Color(0xFF00E5FF),
            start = center,
            end = Offset(
                x = center.x + minHandLength * sin(minRad).toFloat(),
                y = center.y - minHandLength * cos(minRad).toFloat()
            ),
            strokeWidth = 4.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Second Hand (Continuous smooth sweep)
        if (showSeconds) {
            val secRad = Math.toRadians(secondAngle.toDouble())
            val secHandLength = radius * 0.88f
            drawLine(
                color = Color(0xFFFF007F),
                start = center,
                end = Offset(
                    x = center.x + secHandLength * sin(secRad).toFloat(),
                    y = center.y - secHandLength * cos(secRad).toFloat()
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Center hub
        drawCircle(
            color = Color.White,
            center = center,
            radius = 4.dp.toPx()
        )
    }
}

@Preview(name = "Digital Minimal", widthDp = 250, heightDp = 250)
@Composable
fun ClockViewDigitalMinimalPreview() {
    ClockView(
        clockStyle = ClockStyle.DIGITAL_MINIMAL,
        is24HourFormat = true,
        showSeconds = true,
        overrideTimeMs = 1700000000000L
    )
}

@Preview(name = "Digital Bold", widthDp = 250, heightDp = 250)
@Composable
fun ClockViewDigitalBoldPreview() {
    ClockView(
        clockStyle = ClockStyle.DIGITAL_BOLD,
        is24HourFormat = true,
        showSeconds = true,
        overrideTimeMs = 1700000000000L
    )
}

@Preview(name = "Digital Neon", widthDp = 250, heightDp = 250)
@Composable
fun ClockViewDigitalNeonPreview() {
    ClockView(
        clockStyle = ClockStyle.DIGITAL_NEON,
        is24HourFormat = true,
        showSeconds = true,
        overrideTimeMs = 1700000000000L
    )
}

@Preview(name = "Analog Classic", widthDp = 250, heightDp = 250)
@Composable
fun ClockViewAnalogClassicPreview() {
    ClockView(
        clockStyle = ClockStyle.ANALOG_CLASSIC,
        showSeconds = true,
        overrideTimeMs = 1700000000000L
    )
}

@Preview(name = "Analog Modern", widthDp = 250, heightDp = 250)
@Composable
fun ClockViewAnalogModernPreview() {
    ClockView(
        clockStyle = ClockStyle.ANALOG_MODERN,
        showSeconds = true,
        overrideTimeMs = 1700000000000L
    )
}
