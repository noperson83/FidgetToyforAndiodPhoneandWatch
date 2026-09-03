package bpm.munkz.pulse_wear.os.bpm.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import bpm.munkz.pulse_wear.os.bpm.BuildConfig
import bpm.munkz.pulse_wear.os.bpm.R
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var requestedFidgetToyIndex by mutableStateOf<Int?>(null)
    private var fidgetToyRequestId by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurePhoneWindow()
        refreshFidgetPhoneSurfaces()
        if (savedInstanceState == null) {
            applyFidgetToyRequest(intent)
        }
        setContent {
            FidgetApp(
                requestedToyIndex = requestedFidgetToyIndex,
                requestedToyRequestId = fidgetToyRequestId,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyFidgetToyRequest(intent)
    }

    override fun onStop() {
        refreshFidgetPhoneSurfaces()
        super.onStop()
    }

    private fun configurePhoneWindow() {
        if (BuildConfig.APP_EDITION != "fidgetphone") return

        actionBar?.hide()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun applyFidgetToyRequest(intent: Intent?) {
        intent.requestedFidgetToyIndex()?.let { toyId ->
            requestedFidgetToyIndex = toyId
            fidgetToyRequestId += 1L
        }
    }
}

@Composable
private fun FidgetApp(
    requestedToyIndex: Int?,
    requestedToyRequestId: Long,
) {
    MaterialTheme {
        var showLaunchSplash by rememberSaveable {
            mutableStateOf(requestedToyRequestId == 0L)
        }

        LaunchedEffect(Unit) {
            delay(FIDGET_LAUNCH_SPLASH_DURATION_MS)
            showLaunchSplash = false
        }

        if (showLaunchSplash) {
            FidgetLaunchSplashScreen()
        } else if (BuildConfig.APP_EDITION == "fidgettoy") {
            AppScaffold {
                FidgetToyPage(
                    requestedToyIndex = requestedToyIndex,
                    requestedToyRequestId = requestedToyRequestId,
                )
            }
        } else {
            FidgetToyPage(
                requestedToyIndex = requestedToyIndex,
                requestedToyRequestId = requestedToyRequestId,
            )
        }
    }
}

@Composable
private fun FidgetLaunchSplashScreen() {
    val logoScale = remember { Animatable(1.38f) }

    LaunchedEffect(Unit) {
        logoScale.snapTo(1.38f)
        logoScale.animateTo(
            targetValue = 0.72f,
            animationSpec = tween(
                durationMillis = 620,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.munkz_fidget_toy_logo),
            contentDescription = "Munkz Fidget Toy",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize(0.82f)
                .scale(logoScale.value),
        )
    }
}

private const val FIDGET_LAUNCH_SPLASH_DURATION_MS = 740L
