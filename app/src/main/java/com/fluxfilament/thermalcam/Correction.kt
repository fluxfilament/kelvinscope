/*
 * Copyright (C) 2026 Damirusnik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fluxfilament.thermalcam

import kotlin.math.abs

/**
 * A user correction laid over the Planck conversion: `gain * celsius + offset`.
 *
 * The coefficients in [Planck] come from the camera's own metadata and are not
 * touched; this sits on top of them and moves the result to agree with references
 * the user actually has at hand - melting ice, boiling water, a contact thermometer.
 * One reference can only say how far off the camera is, so it gives a pure shift;
 * two references far enough apart also give the slope.
 *
 * It is a home check, not a laboratory calibration. Water surfaces are not black
 * bodies and boiling water is not exactly 100 C everywhere, which is why the limits
 * below are tight: a correction big enough to break them is far more likely a bad
 * measurement than a camera that far off.
 */
data class Correction(val gain: Double = 1.0, val offset: Double = 0.0) {

    val isIdentity: Boolean get() = gain == 1.0 && offset == 0.0

    fun apply(celsius: Double): Double = gain * celsius + offset

    fun invert(celsius: Double): Double = (celsius - offset) / gain

    companion object {
        val NONE = Correction()

        /** Furthest a correction may move a reading at either reference. */
        const val MAX_SHIFT = 10.0

        /** Slope limits for a two-point fit: ±10 % across the span. */
        const val MIN_GAIN = 0.90
        const val MAX_GAIN = 1.10

        /**
         * Closest two references may be and still define a slope. Nearer than this,
         * the noise in each measurement turns into a large error in the gain, and it
         * is applied across the whole range.
         */
        const val MIN_SPAN = 30.0

        fun fit(points: List<ReferencePoint>): FitResult {
            if (points.isEmpty()) return FitResult.NoPoints
            val correction = if (points.size == 1) {
                val p = points[0]
                Correction(offset = p.reference - p.measured)
            } else {
                val (a, b) = points.sortedBy { it.reference }.let { it.first() to it.last() }
                if (b.reference - a.reference < MIN_SPAN || b.measured - a.measured < MIN_SPAN / 2) {
                    return FitResult.TooClose
                }
                val gain = (b.reference - a.reference) / (b.measured - a.measured)
                if (gain !in MIN_GAIN..MAX_GAIN) return FitResult.TooSteep(gain)
                Correction(gain, a.reference - gain * a.measured)
            }
            val worst = points.maxOf { abs(correction.apply(it.measured) - it.measured) }
            if (worst > MAX_SHIFT) return FitResult.TooFar(worst)
            return FitResult.Ok(correction)
        }
    }
}

/** One reference measured: what it should read and what the camera read. */
data class ReferencePoint(
    val reference: Double,
    val measured: Double,
    /** Max minus min of the per-frame readings while it was being measured. */
    val spread: Double,
    val kind: ReferenceKind,
)

enum class ReferenceKind { ICE, BOILING, CUSTOM }

/**
 * Which references an applied correction was fitted on, as kept in the settings:
 * "ICE+BOILING", worded only when shown, in the language of the moment.
 *
 * Up to 0.2.0 the label was stored already worded ("ice + boiling water",
 * "лёд + кипяток"), so after a change of language the calibration screen went on
 * showing the old one. Those versions spoke only English and Russian, so such a
 * label is read back by its words.
 */
object CorrectionSource {

    fun encode(kinds: List<ReferenceKind>): String = kinds.joinToString("+") { it.name }

    /** The kinds in [stored], or null when it cannot be read - then show it as it is. */
    fun decode(stored: String): List<ReferenceKind>? {
        val kinds = stored.split('+').map { part ->
            val word = part.trim()
            ReferenceKind.entries.firstOrNull { it.name == word } ?: LEGACY[word.lowercase()] ?: return null
        }
        return kinds.ifEmpty { null }
    }

    private val LEGACY = mapOf(
        "ice" to ReferenceKind.ICE,
        "лёд" to ReferenceKind.ICE,
        "boiling water" to ReferenceKind.BOILING,
        "кипяток" to ReferenceKind.BOILING,
        "own reference" to ReferenceKind.CUSTOM,
        "свой эталон" to ReferenceKind.CUSTOM,
    )
}

sealed interface FitResult {
    data class Ok(val correction: Correction) : FitResult
    data object NoPoints : FitResult
    data object TooClose : FitResult
    data class TooSteep(val gain: Double) : FitResult
    data class TooFar(val shift: Double) : FitResult
}

/**
 * The correction as one short figure: "+1,6 °C" for a shift, "×1,012 −1,6 °C" when
 * there is a slope too. Shared by the calibration screen and the snapshot card, so
 * the two never describe the same correction differently.
 */
fun Correction.label(locale: java.util.Locale): String {
    fun signed(value: Double) =
        (if (value < 0) "−" else "+") + String.format(locale, "%.1f", abs(value))
    return if (gain == 1.0) "${signed(offset)} °C"
    else "×${String.format(locale, "%.3f", gain)} ${signed(offset)} °C"
}
