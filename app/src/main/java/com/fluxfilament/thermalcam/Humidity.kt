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

import kotlin.math.exp
import kotlin.math.ln

/**
 * Where moisture will settle: the surface temperature below which the air at a
 * surface reaches a given relative humidity.
 *
 * Air cooled against a cold surface keeps its water but can hold less of it, so the
 * humidity right at the surface is higher than in the room. At 100 % water condenses
 * (the dew point); from about 80 % held for days, mould grows - which is why building
 * thermography paints surfaces against a limit rather than against the dew point
 * alone. FLIR's condensation alarm on its E and T cameras works the same way.
 *
 * Magnus formula over water with the Sonntag constants, good to a few tenths of a
 * degree between -45 and 60 °C, far finer than the camera.
 */
object Humidity {
    private const val A = 6.112 // hPa
    private const val B = 17.62
    private const val C = 243.12 // °C

    /** Saturation vapour pressure over water, hPa. */
    fun saturation(celsius: Double): Double = A * exp(B * celsius / (C + celsius))

    /** The temperature at which [hPa] of vapour saturates the air. */
    private fun saturatedAt(hPa: Double): Double {
        val g = ln(hPa / A)
        return C * g / (B - g)
    }

    fun dewPoint(airC: Double, relativeHumidity: Double): Double =
        saturatedAt(relativeHumidity / 100.0 * saturation(airC))

    /**
     * The surface temperature at and below which the air touching it reaches
     * [surfaceLimit] percent: 100 gives the dew point, 80 the mould threshold.
     */
    fun surfaceLimit(airC: Double, relativeHumidity: Double, surfaceLimit: Double): Double =
        saturatedAt(relativeHumidity / 100.0 * saturation(airC) / (surfaceLimit / 100.0))
}
