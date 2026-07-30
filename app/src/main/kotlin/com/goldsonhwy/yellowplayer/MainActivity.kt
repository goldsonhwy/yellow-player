package com.goldsonhwy.yellowplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.goldsonhwy.yellowplayer.ui.navigation.AppNavGraph
import com.goldsonhwy.yellowplayer.ui.theme.DarkBackground
import com.goldsonhwy.yellowplayer.ui.theme.YellowPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            YellowPlayerTheme {
                val privacyEnabled = remember {
                    getSharedPreferences(PRIVACY_PREFS, MODE_PRIVATE)
                        .getBoolean(PRIVACY_ENABLED, false)
                }
                var unlocked by remember { mutableStateOf(!privacyEnabled) }

                if (unlocked) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = DarkBackground
                    ) {
                        val navController = rememberNavController()
                        AppNavGraph(navController = navController)
                    }
                } else {
                    PrivacyGate(onUnlocked = { unlocked = true })
                }
            }
        }
    }
}

private const val PRIVACY_PREFS = "privacy_prefs"
private const val PRIVACY_ENABLED = "privacy_mode_enabled"

@Composable
private fun PrivacyGate(onUnlocked: () -> Unit) {
    BackHandler(enabled = true) { }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var successfulSwipes = 0
                var lastSuccessAt = 0L
                awaitPointerEventScope {
                    while (true) {
                        var startX = 0f
                        var startY = 0f
                        var endX = 0f
                        var endY = 0f
                        var started = false
                        var invalid = false
                        var maxPointers = 0

                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
                            if (event.changes.size > 1 || maxPointers > 1) invalid = true
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                if (!started && change.pressed) {
                                    startX = change.position.x
                                    startY = change.position.y
                                    endX = startX
                                    endY = startY
                                    started = true
                                } else if (started) {
                                    endX = change.position.x
                                    endY = change.position.y
                                }
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        if (!started) continue
                        val dx = endX - startX
                        val dy = endY - startY
                        val minDistance = size.width * 0.15f
                        val now = System.currentTimeMillis()
                        val validRightSwipe = !invalid && dx >= minDistance &&
                            kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f

                        if (validRightSwipe) {
                            if (lastSuccessAt != 0L && now - lastSuccessAt > 2_000L) successfulSwipes = 0
                            successfulSwipes += 1
                            lastSuccessAt = now
                            if (successfulSwipes >= 5) {
                                onUnlocked()
                                return@awaitPointerEventScope
                            }
                        } else {
                            successfulSwipes = 0
                            lastSuccessAt = 0L
                        }
                    }
                }
            }
    )
}
