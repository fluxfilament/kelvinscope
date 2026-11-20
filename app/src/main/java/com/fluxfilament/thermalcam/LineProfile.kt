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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A measuring line: Max, Min and the mean along a segment, and the counts along it
 * for a profile graph. For a pipe, an underfloor heating loop or a radiator from
 * inlet to outlet, where the question is how the temperature runs along something,
 * not what it is at one point.
 *
 * The ends are in fractions of the sensor frame, like spots. The line is walked one
 * pixel at a time along its longer axis, so a line across the whole 80x60 frame gives
 * 80 samples and every pixel it crosses is read once.
 */
class LineProfile {

    @Volatile
    var start: Spot = Spot(0.2f, 0.5f)
        private set

    @Volatile
    var end: Spot = Spot(0.8f, 0.5f)
        private set

    fun moveEnd(which: Int, u: Float, v: Float) {
        val p = Spot(u.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        if (which == 0) start = p else end = p
    }

    class Result(
        /** Counts along the line, from [start] to [end]. */
        val samples: IntArray,
        val maxRaw: Int,
        val minRaw: Int,
        val meanRaw: Double,
        /** Where along the line the maximum is, 0 at the start and 1 at the end. */
        val maxAt: Float,
    )

    companion object {
        fun sample(raw: IntArray, width: Int, height: Int, a: Spot, b: Spot): Result {
            val x0 = a.u * width - 0.5f
            val y0 = a.v * height - 0.5f
            val x1 = b.u * width - 0.5f
            val y1 = b.v * height - 0.5f
            val steps = max(abs(x1 - x0), abs(y1 - y0)).roundToInt().coerceAtLeast(0)
            val n = steps + 1
            val samples = IntArray(n)
            var maxRaw = Int.MIN_VALUE
            var minRaw = Int.MAX_VALUE
            var sum = 0L
            var maxIndex = 0
            for (i in 0 until n) {
                val t = if (steps == 0) 0f else i.toFloat() / steps
                val x = (x0 + (x1 - x0) * t).roundToInt().coerceIn(0, width - 1)
                val y = (y0 + (y1 - y0) * t).roundToInt().coerceIn(0, height - 1)
                val v = raw[y * width + x]
                samples[i] = v
                if (v > maxRaw) {
                    maxRaw = v
                    maxIndex = i
                }
                if (v < minRaw) minRaw = v
                sum += v
            }
            return Result(
                samples, maxRaw, minRaw, sum.toDouble() / n,
                if (steps == 0) 0f else maxIndex.toFloat() / steps,
            )
        }
    }
}
