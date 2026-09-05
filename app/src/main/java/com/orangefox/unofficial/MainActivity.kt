package com.orangefox.unofficial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orangefox.unofficial.data.local.ThemeMode
import com.orangefox.unofficial.ui.nav.AppRoot
import com.orangefox.unofficial.ui.theme.FoxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as FoxApp
        setContent {
            val prefs by app.settingsRepository.prefs.collectAsStateWithLifecycle(initialValue = null)
            val current = prefs ?: return@setContent
            val darkTheme = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            FoxTheme(darkTheme = darkTheme, dynamicColor = current.useDynamicColor) {
                AppRoot()
            }
        }
    }
}
