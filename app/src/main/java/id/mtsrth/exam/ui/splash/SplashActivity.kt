package id.mtsrth.exam.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import id.mtsrth.exam.ExamApp
import id.mtsrth.exam.R
import id.mtsrth.exam.ui.dashboard.DashboardActivity
import id.mtsrth.exam.ui.theme.ExamKioskTheme
import id.mtsrth.exam.ui.theme.PrestigeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val storage = ExamApp.instance.secureStorage
        val isDarkMode = storage.isDarkMode

        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
        )
        super.onCreate(savedInstanceState)

        val schoolName = storage.schoolName.ifBlank { "MTS ROUDHOTUT THOWALIB" }
        val tagline = storage.schoolTagline.ifBlank { "Madrasah Tsanawiyah Unggulan & Berkarakter" }

        setContent {
            ExamKioskTheme(darkTheme = isDarkMode) {
                val colors = PrestigeTheme.colors
                val scale = remember { Animatable(0.84f) }
                val alpha = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                    launch {
                        alpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 700)
                        )
                    }
                    delay(1600L)
                    startActivity(Intent(this@SplashActivity, DashboardActivity::class.java))
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    colors.coolAlabaster,
                                    colors.pureCrispWhite,
                                    colors.softMintContainer.copy(alpha = 0.55f)
                                )
                            )
                        )
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
                            modifier = Modifier.size(116.dp),
                            shape = RoundedCornerShape(28.dp),
                            color = colors.pureCrispWhite,
                            shadowElevation = if (colors.isDark) 0.dp else 6.dp
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_sekolah),
                                contentDescription = "Logo Sekolah",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
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
                            .navigationBarsPadding()
                            .padding(bottom = 42.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(148.dp)
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
    }
}
