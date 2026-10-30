package com.karelisio.ruban.ui.screens.ideas

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.PermissionExplainer
import com.karelisio.ruban.ui.permissions.rememberPermissionState
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.MorphShape

/** Dictation sheet: an animated blob breathing with the voice level, live transcript. */
@Composable
fun VoiceCaptureSheet(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val permission = rememberPermissionState(AppPermission.MICROPHONE)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (!permission.granted) {
            PermissionExplainer(AppPermission.MICROPHONE, onLater = onDismiss)
        } else {
            Listening(onResult, onDismiss)
        }
    }
}

@Composable
private fun Listening(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableStateOf(0) }

    if (available) {
        DisposableEffect(attempt) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; error = null }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(code: Int) {
                    listening = false
                    error = when (code) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Je n'ai rien entendu."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Le service vocal a besoin du réseau."
                        else -> "La dictée n'a pas fonctionné."
                    }
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isNotBlank()) onResult(text) else error = "Je n'ai rien entendu."
                }
                override fun onPartialResults(partial: Bundle?) {
                    partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { transcript = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            recognizer.startListening(intent)
            onDispose { recognizer.destroy() }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VoiceBlob(level, listening)
        Text(
            when {
                !available -> "La reconnaissance vocale n'est pas disponible sur ce téléphone."
                error != null -> error!!
                transcript.isNotBlank() -> transcript
                listening -> "Je t'écoute…"
                else -> "Un instant…"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 64.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss) { Text("Fermer") }
            if (available && error != null) {
                Button(onClick = { transcript = ""; attempt++ }, shapes = ButtonDefaults.shapes()) { Text("Réessayer") }
            }
        }
    }
}

@Composable
private fun VoiceBlob(level: Float, active: Boolean) {
    val reduce = LocalReduceMotion.current
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val amp = remember { Animatable(0f) }
    LaunchedEffect(level) { amp.animateTo(level, spring(0.5f, Spring.StiffnessMedium)) }
    val t = rememberInfiniteTransition(label = "voice")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "spin")
    Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer {
                    val s = 0.75f + 0.25f * amp.value
                    scaleX = s; scaleY = s
                    if (!reduce && active) rotationZ = spin
                }
                .clip(MorphShape(morph, amp.value))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Icon(Icons.Rounded.Mic, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
