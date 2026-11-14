package id.mtsrth.exam.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.mtsrth.exam.ui.theme.JetBrainsMonoFontFamily
import id.mtsrth.exam.ui.theme.PrestigeTheme

/**
 * Tactile scale-down bounce modifier for buttons and interactive cards.
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounce_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material.ripple.rememberRipple(),
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Shimmer loading sweep modifier for data loading and connection test states.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val colors = PrestigeTheme.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )
    val baseColor = colors.shimmerBase
    val highlightColor = colors.shimmerHighlight.copy(alpha = 0.85f)
    background(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(progress * 600f, 0f),
            end = Offset((progress + 0.5f) * 600f, 220f)
        )
    )
}

/**
 * Tonal Elevation card without heavy outlines, using surface tint & soft elevation.
 */
@Composable
fun TonalPrestigeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = PrestigeTheme.colors.pureCrispWhite,
    shadowElevation: Dp = 2.dp,
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    val effectiveShadow = if (PrestigeTheme.colors.isDark) 0.dp else shadowElevation
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        shadowElevation = effectiveShadow,
        tonalElevation = 1.dp,
        content = content
    )
}

@Composable
fun StatusPillBadge(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedBg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(250),
        label = "badge_bg"
    )
    val animatedFg by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(250),
        label = "badge_fg"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = animatedFg,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Animated Sliding Segmented Control for selecting Kelas 7 / 8 / 9.
 */
@Composable
fun AnimatedGradeSegmentedControl(
    options: List<Int>,
    selectedGrade: Int,
    onSelectGrade: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val haptic = LocalHapticFeedback.current
    val selectedIndex = options.indexOf(selectedGrade).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.tonalSlateSurface)
            .padding(4.dp)
    ) {
        val segmentWidth = maxWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "segment_slide"
        )

        Surface(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight(),
            shape = RoundedCornerShape(10.dp),
            color = if (colors.isDark) colors.softMintContainer else colors.pureCrispWhite,
            shadowElevation = if (colors.isDark) 0.dp else 2.dp
        ) {}

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { grade ->
                val isSelected = grade == selectedGrade
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colors.deepEmerald else colors.slateSecondary,
                    animationSpec = tween(200),
                    label = "segment_text"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .testTag("grade_segment_$grade")
                        .clickable {
                            if (grade != selectedGrade) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectGrade(grade)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Kelas $grade",
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Circular Progress Gauge / Ring for Exam Execution Statistics.
 */
@Composable
fun ExamStatsRingChart(
    total: Int,
    finished: Int,
    modifier: Modifier = Modifier
) {
    val colors = PrestigeTheme.colors
    val targetProgress = if (total > 0) (finished.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 700),
        label = "stats_ring_progress"
    )
    val percent = if (total > 0) ((finished * 100f) / total).toInt() else 0

    Box(
        modifier = modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = colors.softMintContainer,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            colors.deepEmerald,
                            colors.vibrantTeal,
                            colors.deepEmerald
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent%",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.darkForest
            )
            Text(
                text = "Tuntas",
                style = MaterialTheme.typography.labelSmall,
                color = colors.slateSecondary
            )
        }
    }
}

/**
 * Shimmer skeleton placeholder list shown while loading/syncing exam packages.
 */
@Composable
fun ShimmerExamSkeletonList(
    count: Int = 3,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(count) {
            TonalPrestigeCard(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .shimmerEffect()
                        )
                        Box(
                            modifier = Modifier
                                .width(64.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(50))
                                .shimmerEffect()
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.75f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .shimmerEffect()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .shimmerEffect()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .width(92.dp)
                                .height(38.dp)
                                .clip(CircleShape)
                                .shimmerEffect()
                        )
                    }
                }
            }
        }
    }
}
