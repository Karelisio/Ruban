package com.karelisio.ruban.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.ruban.container
import com.karelisio.ruban.ui.components.AnimatedRibbonKnot
import com.karelisio.ruban.ui.components.CountdownBadge
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.permissions.AnimatedPermissionIcon
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.rememberPermissionState
import com.karelisio.ruban.ui.screens.people.ContactsImportSheet
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.PolygonShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Page {
    data class Intro(val index: Int) : Page
    data class Permission(val permission: AppPermission) : Page
    data object FirstPerson : Page
}

private val pages: List<Page> =
    List(4) { Page.Intro(it) } + AppPermission.entries.map { Page.Permission(it) } + Page.FirstPerson

@Composable
fun OnboardingScreen() {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val nav = LocalNav.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var importing by rememberSaveable { mutableStateOf(false) }

    fun next() = scope.launch {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pages.lastIndex), animationSpec = spring(0.85f, Spring.StiffnessLow))
    }

    fun finish(then: Route? = null) = scope.launch {
        context.container.settings.setOnboardingDone(true)
        nav.controller.navigate(Route.Home()) { popUpTo(0) { inclusive = true } }
        then?.let { nav.go(it) }
    }

    if (importing) ContactsImportSheet(onDismiss = { importing = false; finish() })

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        HorizontalPager(pager, Modifier.weight(1f), userScrollEnabled = true) { index ->
            when (val page = pages[index]) {
                is Page.Intro -> IntroPage(page.index, active = pager.currentPage == index)
                is Page.Permission -> PermissionPage(page.permission, onGranted = {
                    scope.launch {
                        delay(700)
                        if (pager.currentPage == index) next()
                    }
                })
                Page.FirstPerson -> FirstPersonPage(
                    onAdd = { finish(Route.PersonEdit()) },
                    onImport = { importing = true },
                    onSkip = { finish() },
                )
            }
        }
        Dots(pager)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val page = pages[pager.currentPage]
            AnimatedContent(page is Page.Permission, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "later") { isPermission ->
                if (isPermission) TextButton(onClick = { next() }) { Text("Plus tard") } else Spacer(Modifier.width(1.dp))
            }
            Spacer(Modifier.weight(1f))
            if (page !is Page.FirstPerson) {
                val permission = (page as? Page.Permission)?.permission
                if (permission == null) {
                    Button(onClick = { next() }, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                        Text(if (pager.currentPage == 0) "Commencer" else "Suivant")
                    }
                } else {
                    PermissionButton(permission, onNext = { next() })
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(permission: AppPermission, onNext: () -> Unit) {
    val state = rememberPermissionState(permission)
    AnimatedContent(state.granted, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "permBtn") { granted ->
        if (granted) {
            FilledTonalButton(onClick = onNext, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) { Text("Suivant") }
        } else {
            Button(onClick = state::request, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                Text(if (permission.viaSettings) "Ouvrir les réglages" else "Autoriser")
            }
        }
    }
}

@Composable
private fun Dots(pager: PagerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        pages.indices.forEach { i ->
            val selected = pager.currentPage == i
            val width by animateDpAsState(if (selected) 22.dp else 7.dp, spring(0.6f, Spring.StiffnessMedium), label = "dotW")
            val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotC")
            Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun PageLayout(title: String, text: String, illustration: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) { illustration() }
        Spacer(Modifier.height(32.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IntroPage(index: Int, active: Boolean) {
    when (index) {
        0 -> PageLayout("Ruban", "Les idées cadeaux de tes proches, notées au fil de l'eau. Pour ne plus jamais être pris de court.") {
            if (active) AnimatedRibbonKnot(Modifier.size(220.dp))
        }
        1 -> PageLayout("Une idée ? Hop, notée", "Partage un lien depuis n'importe quelle appli, prends une photo, dicte : Ruban récupère le titre, l'image et le prix.") {
            FloatingBadges(listOf("💡", "🔗", "📷", "🎙️"))
        }
        2 -> PageLayout("Toujours à temps", "Anniversaires, fêtes, Noël, fête des mères… Ruban te prévient à J-30, J-7 et J-1, et te dit si tu as déjà une idée.") {
            Box(contentAlignment = Alignment.Center) {
                FloatingBadges(listOf("🎂", "🎄", "💐", "🌸"))
                CountdownBadge(7, size = 96.dp)
            }
        }
        else -> PageLayout("Rien que pour toi", "100 % local : sans compte, sans pub, sans pistage. Verrouille l'appli et sauvegarde tout dans un fichier chiffré.") {
            FloatingBadges(listOf("🔒", "🎁", "🤫", "✨"))
        }
    }
}

/** Emojis orbiting softly in scalloped shapes. */
@Composable
private fun FloatingBadges(emojis: List<String>) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "orbit")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "phase")
    val p = if (reduce) 0.5f else phase
    val positions = listOf(-60 to -60, 60 to -50, -55 to 60, 65 to 55)
    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(150.dp).graphicsLayer { rotationZ = p * 30f }.clip(PolygonShape(MaterialShapes.Cookie12Sided)).background(MaterialTheme.colorScheme.primaryContainer))
        emojis.forEachIndexed { i, e ->
            val (x, y) = positions[i % positions.size]
            val dy = (if (i % 2 == 0) p else 1f - p) * 14f - 7f
            ShapeBadge(
                Modifier.offset(x.dp, (y + dy).dp),
                size = 64.dp,
                polygonIndex = i * 3,
                container = if (i % 2 == 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            ) { Text(e, fontSize = 28.sp) }
        }
    }
}

@Composable
private fun PermissionPage(permission: AppPermission, onGranted: () -> Unit) {
    val state = rememberPermissionState(permission) { if (it) onGranted() }
    PageLayout(permission.title, permission.why) {
        AnimatedPermissionIcon(permission.icon, state.granted, size = 180.dp)
    }
}

@Composable
private fun FirstPersonPage(onAdd: () -> Unit, onImport: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FloatingBadges(listOf("🫶", "🎂", "🎁", "🌸"))
        Spacer(Modifier.height(24.dp))
        Text("Pour qui cherches-tu ?", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Ajoute un premier proche, ou importe d'un coup les anniversaires de tes contacts.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAdd, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Ajouter un proche")
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onImport, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.Contacts, null)
            Spacer(Modifier.width(8.dp))
            Text("Importer les anniversaires")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onSkip) { Text("Plus tard, découvrir Ruban") }
    }
}
