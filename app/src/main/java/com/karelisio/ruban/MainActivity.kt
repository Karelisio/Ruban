package com.karelisio.ruban

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.karelisio.ruban.data.settings.Settings
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.nav.RubanNavHost
import com.karelisio.ruban.ui.security.Biometric
import com.karelisio.ruban.ui.security.LockScreen
import com.karelisio.ruban.ui.theme.RubanTheme
import com.karelisio.ruban.ui.theme.systemAnimationsDisabled
import com.karelisio.ruban.ui.update.UpdateSheetHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val RELOCK_AFTER_MS = 60_000L

class MainActivity : FragmentActivity() {

    private val settingsState = MutableStateFlow<Settings?>(null)
    private val pendingRoute = MutableStateFlow<Route?>(null)
    private val locked = MutableStateFlow(false)
    private var lockChecked = false
    private var stoppedAt = 0L

    /** Locks again when the app comes back after more than a minute in the background. */
    private val relock = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { stoppedAt = System.currentTimeMillis() }
        override fun onStart(owner: LifecycleOwner) {
            val s = settingsState.value ?: return
            if (s.biometricLock && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > RELOCK_AFTER_MS) locked.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { settingsState.value == null }
        // Signature #1, end: once tied, the bow springs forward and the splash melts into the app.
        splash.setOnExitAnimationListener { provider ->
            if (systemAnimationsDisabled()) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            val icon = runCatching { provider.iconView }.getOrNull()
            val fade = { provider.view.animate().alpha(0f).setDuration(200).withEndAction { provider.remove() }.start() }
            if (icon == null) fade()
            else icon.animate().scaleX(1.18f).scaleY(1.18f).setDuration(240)
                .setInterpolator(android.view.animation.OvershootInterpolator(3f))
                .withEndAction { fade() }.start()
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )

        lifecycleScope.launch {
            container.settings.settings.collect { s ->
                if (!lockChecked) {
                    lockChecked = true
                    locked.value = s.biometricLock && savedInstanceState == null
                }
                if (!s.biometricLock) locked.value = false
                if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                settingsState.value = s
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(relock)
        if (savedInstanceState == null) handle(intent)

        setContent {
            val settings by settingsState.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val pending by pendingRoute.collectAsStateWithLifecycle()
            val isLocked by locked.collectAsStateWithLifecycle()
            RubanTheme(dynamicColor = current.dynamicColor) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    val nav = rememberNavController()
                    val start: Route = remember { if (current.onboardingDone) Route.Home() else Route.Onboarding }
                    RubanNavHost(nav, start)
                    if (current.onboardingDone) {
                        UpdateSheetHost()
                        LaunchedEffect(Unit) { container.updates.check() }
                    }
                    if (isLocked) {
                        LockScreen(onUnlock = ::unlock)
                        LaunchedEffect(Unit) { unlock() }
                    }
                    LaunchedEffect(pending, current.onboardingDone) {
                        val route = pending ?: return@LaunchedEffect
                        if (!current.onboardingDone) return@LaunchedEffect
                        pendingRoute.value = null
                        nav.navigate(route) { launchSingleTop = true }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(relock)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun unlock() {
        Biometric.authenticate(this, onSuccess = { locked.value = false })
    }

    private fun handle(intent: Intent?) {
        lifecycleScope.launch { IntentRouter.route(this@MainActivity, intent)?.let { pendingRoute.value = it } }
    }
}
