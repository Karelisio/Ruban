package com.karelisio.ruban.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.karelisio.ruban.R

private fun fraunces(weight: Int, opsz: Float) = Font(
    R.font.fraunces,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", opsz),
        FontVariation.Setting("SOFT", 100f),
        FontVariation.Setting("WONK", 1f),
    ),
)

private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Soft, expressive serif for big moments (titles, counters). */
val DisplayFamily = FontFamily(fraunces(500, 144f), fraunces(600, 72f), fraunces(700, 48f), fraunces(800, 36f))

/** Rounded geometric sans for everything else. */
val TextFamily = FontFamily(outfit(400), outfit(500), outfit(600), outfit(700), outfit(800))

private val base = Typography()

val RubanTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(600), letterSpacing = (-0.02).em),
    displayMedium = base.displayMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(600), letterSpacing = (-0.02).em),
    displaySmall = base.displaySmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(600)),
    headlineLarge = base.headlineLarge.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(700)),
    headlineMedium = base.headlineMedium.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(700)),
    headlineSmall = base.headlineSmall.copy(fontFamily = DisplayFamily, fontWeight = FontWeight(700)),
    titleLarge = base.titleLarge.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = base.titleSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = TextFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = TextFamily),
    bodySmall = base.bodySmall.copy(fontFamily = TextFamily),
    labelLarge = base.labelLarge.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = TextFamily, fontWeight = FontWeight.SemiBold),
)

/** Big animated numbers (budgets, countdowns). */
val CounterStyle = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(700), fontSize = 44.sp, letterSpacing = (-0.02).em)
