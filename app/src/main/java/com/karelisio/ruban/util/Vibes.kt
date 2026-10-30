package com.karelisio.ruban.util

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Short, expressive vibrations built from haptic primitives when the device supports them. */
object Vibes {
    private fun vibrator(context: Context) = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** The lid pops off: a quick rise then a crisp click. */
    fun unwrap(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        val primitives = intArrayOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_CLICK)
        val effect = if (v.areAllPrimitivesSupported(*primitives)) {
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                .compose()
        } else {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
        v.vibrate(effect)
    }
}
