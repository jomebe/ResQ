package app.resq

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import app.resq.ui.HomeScreen
import app.resq.ui.OnboardScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("resq_prefs", Context.MODE_PRIVATE)
        val shown = prefs.getBoolean("onboard_shown", false)

        setContent {
            var showOnboard by remember { mutableStateOf(!shown) }

            if (showOnboard) {
                OnboardScreen(onContinue = {
                    prefs.edit().putBoolean("onboard_shown", true).apply()
                    showOnboard = false
                })
            } else {
                HomeScreen(
                    onMicTap = { /* TODO: hook to ViewModel */ },
                    onOcrTap = { /* TODO */ },
                    onFlashlight = { /* TODO */ },
                    onSiren = { /* TODO */ },
                    onCall119 = { /* TODO */ },
                )
            }
        }
    }
}
