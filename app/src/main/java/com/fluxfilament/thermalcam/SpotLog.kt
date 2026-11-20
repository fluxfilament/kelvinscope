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

import java.util.Locale

/**
 * Spot temperatures over time, as CSV: one row a second while recording.
 *
 * For watching something warm up or cool down - a transistor under load, a radiator
 * after the heating comes on - where a video at 80x60 would show little and a column
 * of numbers is what gets plotted afterwards. Nine spot columns always, empty where a
 * spot is not placed, so adding or removing a spot mid-recording does not shift the
 * columns under the rows already written. Dots for decimals and commas between
 * fields whatever the phone's language, so a spreadsheet anywhere reads it.
 */
class SpotLog(private val startedAtMillis: Long, private val intervalMillis: Long = 1000) {

    private val text = StringBuilder(HEADER)
    private var lastRowAt = Long.MIN_VALUE

    var rows = 0
        private set

    /**
     * Adds a row if [intervalMillis] has passed since the last one. [spots] are in
     * degrees Celsius, numbered from one.
     */
    @Synchronized
    fun offer(nowMillis: Long, spots: List<Double>, max: Double, min: Double, emissivity: Double): Boolean {
        if (lastRowAt != Long.MIN_VALUE && nowMillis - lastRowAt < intervalMillis) return false
        lastRowAt = nowMillis
        val cells = ArrayList<String>(4 + MAX_SPOTS)
        cells += String.format(Locale.US, "%.1f", (nowMillis - startedAtMillis) / 1000.0)
        for (i in 0 until MAX_SPOTS) cells += spots.getOrNull(i)?.let(::degrees) ?: ""
        cells += degrees(max)
        cells += degrees(min)
        cells += String.format(Locale.US, "%.2f", emissivity)
        text.append(cells.joinToString(",")).append('\n')
        rows++
        return true
    }

    @Synchronized
    fun csv(): String = text.toString()

    private fun degrees(c: Double) = if (c.isNaN()) "" else String.format(Locale.US, "%.2f", c)

    companion object {
        const val MAX_SPOTS = 9
        val HEADER = (listOf("seconds") + (1..MAX_SPOTS).map { "spot$it" } + listOf("max", "min", "emissivity"))
            .joinToString(",") + "\n"
    }
}
