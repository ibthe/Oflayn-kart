package app.georgia.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.georgia.offline.data.LocalSettings
import app.georgia.offline.ui.GeorgiaOfflineApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = LocalSettings(applicationContext)
        setContent {
            GeorgiaOfflineApp(settings = settings)
        }
    }
}
