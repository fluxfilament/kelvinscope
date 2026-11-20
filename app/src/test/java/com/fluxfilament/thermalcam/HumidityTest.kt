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

import org.junit.Assert.assertEquals
import org.junit.Test

class HumidityTest {

    @Test
    fun dewPointsMatchTheTables() {
        // Psychrometric tables: 20 °C at 50 % -> 9.3 °C, at 60 % -> 12.0 °C, 25 °C at 70 % -> 19.1 °C.
        assertEquals(9.3, Humidity.dewPoint(20.0, 50.0), 0.15)
        assertEquals(12.0, Humidity.dewPoint(20.0, 60.0), 0.15)
        assertEquals(19.1, Humidity.dewPoint(25.0, 70.0), 0.15)
    }

    @Test
    fun saturatedAirHasItsDewPointAtItsOwnTemperature() {
        assertEquals(15.0, Humidity.dewPoint(15.0, 100.0), 1e-9)
    }

    @Test
    fun theMouldLimitSitsAboveTheDewPoint() {
        // 20 °C, 50 %: vapour 11.7 hPa; 80 % at the surface needs 14.6 hPa saturation, about 12.6 °C.
        val mould = Humidity.surfaceLimit(20.0, 50.0, 80.0)
        assertEquals(12.6, mould, 0.15)
        assertEquals(Humidity.dewPoint(20.0, 50.0), Humidity.surfaceLimit(20.0, 50.0, 100.0), 1e-9)
    }
}
