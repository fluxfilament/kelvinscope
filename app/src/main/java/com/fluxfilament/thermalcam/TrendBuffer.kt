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
 * The last [capacity] rows of the temperature log, kept for drawing: Max, Min and
 * each spot over time. The CSV keeps everything; this keeps what fits on screen, so
 * the graph shows the recent run - is it still warming up, has it levelled off -
 * without the file having to be opened.
 *
 * Series are fixed: index 0 is Max, 1 is Min, 2.. are spots 1..9. A spot that was
 * not placed at that moment is NaN there and the line breaks.
 */
class TrendBuffer(private val capacity: Int = 600) {

    private val values = Array(SERIES) { FloatArray(capacity) { Float.NaN } }
    private var next = 0
    private var size = 0

    @Synchronized
    fun add(max: Double, min: Double, spots: List<Double>) {
        values[0][next] = max.toFloat()
        values[1][next] = min.toFloat()
        for (s in 0 until SpotLog.MAX_SPOTS) values[2 + s][next] = spots.getOrNull(s)?.toFloat() ?: Float.NaN
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    @Synchronized
    fun clear() {
        next = 0
        size = 0
    }

    /** Each series oldest first, [size] long; series that are NaN throughout are dropped as null. */
    @Synchronized
    fun snapshot(): Array<FloatArray?> = Array(SERIES) { s ->
        val out = FloatArray(size) { i -> values[s][(next - size + i + capacity) % capacity] }
        if (out.all { it.isNaN() }) null else out
    }

    companion object {
        const val SERIES = 2 + SpotLog.MAX_SPOTS
    }
}
