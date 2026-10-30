package com.karelisio.ruban

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.karelisio.ruban.data.settings.Settings
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.nav.RubanNavHost
import com.karelisio.ruban.ui.theme.RubanTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val settingsState = MutableStateFlow<Settings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { settingsState.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        lifecycleScope.launch {
            container.settings.settings.collect { settingsState.value = it }
        }

        setContent {
            val settings by settingsState.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            RubanTheme(dynamicColor = current.dynamicColor) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    val nav = rememberNavController()
                    val start: Route = if (current.onboardingDone) Route.Home() else Route.Onboarding
                    RubanNavHost(nav, start)
                }
            }
        }
    }
}
