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
 * The thermal index of a wall seen from inside: where its surface sits between the
 * outdoor and the indoor temperature, in percent. 100 % is a wall as warm as the room
 * - perfect insulation; 0 % is a wall as cold as outdoors. Missing or wet insulation
 * and thermal bridges show up as patches with a low index.
 *
 * The same index FLIR's insulation alarm compares against a limit; typical limits
 * are 60-80 %, higher for new buildings. It only means something with a real
 * difference between inside and outside - in the heating season, and ideally with
 * a steady few degrees' difference for hours beforehand.
 */
object Insulation {

    /** The smallest indoor-outdoor difference the index is worth working out for. */
    const val MIN_DIFFERENCE_C = 5.0

    fun thermalIndex(surfaceC: Double, insideC: Double, outsideC: Double): Double =
        (surfaceC - outsideC) / (insideC - outsideC) * 100.0

    /**
     * The surface temperature at and below which the index is under [indexPercent],
     * or null when inside is not warmer than outside by [MIN_DIFFERENCE_C].
     */
    fun surfaceLimit(insideC: Double, outsideC: Double, indexPercent: Double): Double? {
        if (insideC - outsideC < MIN_DIFFERENCE_C) return null
        return outsideC + indexPercent / 100.0 * (insideC - outsideC)
    }
}
