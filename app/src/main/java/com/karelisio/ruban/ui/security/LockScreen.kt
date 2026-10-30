package com.karelisio.ruban.ui.security

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.components.RibbonKnot
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.PolygonShape

/** Shown above everything while the app is locked. */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "lock")
    val breathe by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { /* swallow touches */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(150.dp).graphicsLayer { if (!reduce) { scaleX = breathe; scaleY = breathe } },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(150.dp).clip(PolygonShape(MaterialShapes.Cookie12Sided)).background(MaterialTheme.colorScheme.primaryContainer))
                RibbonKnot(Modifier.size(96.dp), progress = 1f)
            }
            Spacer(Modifier.height(24.dp))
            Text("Ruban est verrouillé", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onUnlock, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                Icon(Icons.Rounded.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Déverrouiller")
            }
        }
    }
}
