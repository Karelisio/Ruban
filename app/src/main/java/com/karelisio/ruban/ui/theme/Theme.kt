package com.karelisio.ruban.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when the system "remove animations" setting is on: decorative motion is skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }

fun Context.systemAnimationsDisabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(context.systemAnimationsDisabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = context.systemAnimationsDisabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}

@Composable
fun rubanColorScheme(dynamicColor: Boolean, dark: Boolean = isSystemInDarkTheme()): ColorScheme {
    val context = LocalContext.current
    return when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> RubanDark
        else -> RubanLight
    }
}

@Composable
fun RubanTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = rubanColorScheme(dynamicColor),
            typography = RubanTypography,
            shapes = RubanShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/** Returns [spec], or an instant snap when animations are reduced. */
@Composable
@ReadOnlyComposable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spec
