package id.mtsrth.exam.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.mtsrth.exam.R
import id.mtsrth.exam.ui.theme.PrestigeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded Madrasah splash overlay rendered smoothly inside DashboardActivity.
 * Pairs seamlessly with AndroidX SplashScreen API: displays the full unclipped
 * school logo, title, and tagline with crisp modern Outfit typography, then
 * cross-fades cleanly into the dashboard without a second Activity launch flicker.
 */
@Composable
fun BrandedSplashOverlay(
    visible: Boolean,
    schoolName: String,
    tagline: String,
    onSplashFinished: () -> Unit
) {
    val colors = PrestigeTheme.colors

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(320)) + scaleOut(targetScale = 1.04f, animationSpec = tween(320))
    ) {
        val scale = remember { Animatable(0.88f) }
        val alpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480)
                )
            }
            delay(1150L)
            onSplashFinished()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.coolAlabaster,
                            colors.pureCrispWhite,
                            colors.softMintContainer.copy(alpha = if (colors.isDark) 0.65f else 0.55f)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = colors.deepEmerald.copy(alpha = 0.06f),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.9f, size.height * 0.12f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = colors.vibrantTeal.copy(alpha = 0.07f),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.1f, size.height * 0.85f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        this.alpha = alpha.value
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = colors.pureCrispWhite,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shadowElevation = if (colors.isDark) 0.dp else 6.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_sekolah),
                        contentDescription = "Logo Sekolah",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "SELAMAT DATANG DI E-UJIAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.deepEmerald,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = schoolName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.inkSlate,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(152.dp)
                        .height(5.dp)
                        .clip(CircleShape),
                    color = colors.deepEmerald,
                    trackColor = colors.softMintContainer,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Memuat Sistem Ujian Terenkripsi...",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateSecondary
                )
            }
        }
    }
}
