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
import org.junit.Assert.assertNull
import org.junit.Test

class InsulationTest {

    @Test
    fun theIndexRunsFromOutdoorsToTheRoom() {
        assertEquals(100.0, Insulation.thermalIndex(20.0, 20.0, -5.0), 1e-9)
        assertEquals(0.0, Insulation.thermalIndex(-5.0, 20.0, -5.0), 1e-9)
        assertEquals(80.0, Insulation.thermalIndex(15.0, 20.0, -5.0), 1e-9)
    }

    @Test
    fun theLimitIsTheSurfaceTemperatureAtThatIndex() {
        // 20 °C in, -5 °C out, 70 %: -5 + 0.7 * 25 = 12.5 °C.
        assertEquals(12.5, Insulation.surfaceLimit(20.0, -5.0, 70.0)!!, 1e-9)
        assertEquals(70.0, Insulation.thermalIndex(12.5, 20.0, -5.0), 1e-9)
    }

    @Test
    fun withoutAWarmInsideThereIsNoIndex() {
        assertNull(Insulation.surfaceLimit(20.0, 18.0, 70.0))
        assertNull(Insulation.surfaceLimit(20.0, 25.0, 70.0))
    }
}
