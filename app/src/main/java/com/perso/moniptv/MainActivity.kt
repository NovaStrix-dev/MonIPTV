package com.perso.moniptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                if (vm.account == null) LoginScreen(vm) else MainScreen(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Retour du lecteur : met à jour la section « En cours ».
        vm.reloadWatching()
    }
}

private val Scheme = darkColorScheme(
    primary = Color(0xFF4F8CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF7AA7FF),
    background = Color(0xFF0E1116),
    surface = Color(0xFF0E1116),
    surfaceVariant = Color(0xFF1B2230),
    surfaceContainer = Color(0xFF151B26),
    onBackground = Color(0xFFE8ECF3),
    onSurface = Color(0xFFE8ECF3),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
