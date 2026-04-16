package dev.lostf1sh.syncthing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.lostf1sh.syncthing.ui.core.theme.SyncthingTheme
import dev.lostf1sh.syncthing.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SyncthingTheme {
                AppNavigation()
            }
        }
    }
}
