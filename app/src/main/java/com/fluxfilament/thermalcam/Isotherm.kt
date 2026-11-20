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

/**
 * Which temperatures the isotherm paints: everything above a threshold, below it, or
 * between two. Above finds what overheats; below finds the cold spots in a wall or a
 * draught round a window; between picks out one band, say the 30-40 °C of a warm
 * component among hot and cold ones.
 */
enum class IsothermMode { ABOVE, BELOW, BETWEEN }

/** The isotherm as the user set it, in degrees. [secondC] matters only for [IsothermMode.BETWEEN]. */
data class Isotherm(val mode: IsothermMode, val c: Int, val secondC: Int) {

    /** The band in degrees, low end first; null ends are open. */
    fun celsiusBand(): Pair<Int?, Int?> = when (mode) {
        IsothermMode.ABOVE -> c to null
        IsothermMode.BELOW -> null to c
        IsothermMode.BETWEEN -> minOf(c, secondC) to maxOf(c, secondC)
    }

    /**
     * The same band as sensor counts, for the renderer. Counts rise with temperature,
     * so the ends keep their order. Null when an end cannot be expressed - the
     * isotherm is then left off rather than painting against NaN.
     */
    fun rawBand(toRaw: (Double) -> Double): IsothermBand? {
        val (lo, hi) = celsiusBand()
        val rawLo = lo?.let { toRaw(it.toDouble()) }
        val rawHi = hi?.let { toRaw(it.toDouble()) }
        if (rawLo?.isNaN() == true || rawHi?.isNaN() == true) return null
        return IsothermBand(rawLo?.toFloat(), rawHi?.toFloat())
    }
}

/** A band of counts; a pixel inside it, ends included, is painted. */
data class IsothermBand(val lo: Float?, val hi: Float?) {
    fun contains(raw: Int): Boolean = (lo == null || raw >= lo) && (hi == null || raw <= hi)
}
