package com.karelisio.ruban.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.karelisio.ruban.ui.theme.LocalReduceMotion

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val springBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
}

private val Rect.Companion.VisibilityThreshold: Rect get() = Rect(0.5f, 0.5f, 0.5f, 0.5f)

object SharedKeys {
    fun idea(id: Long) = "idea-card-$id"
    fun ideaImage(id: Long) = "idea-image-$id"
    fun person(id: Long) = "person-card-$id"
    fun avatar(id: Long) = "person-avatar-$id"
    fun event(id: Long) = "event-card-$id"
    const val FAB = "fab-new-idea"
}

/** Container transform between two screens (card → detail). */
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = springBounds,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        )
    }
}

/** Same content shown on both screens (avatar, picture). */
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = anim,
            boundsTransform = springBounds,
        )
    }
}
