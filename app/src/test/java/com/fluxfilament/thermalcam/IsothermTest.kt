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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsothermTest {

    private val planck = Planck.DEVELOPMENT_UNIT

    @Test
    fun aboveBelowAndBetweenPickTheRightCounts() {
        val at30 = planck.celsiusToRaw(30.0).toInt()
        val at50 = planck.celsiusToRaw(50.0).toInt()
        val at70 = planck.celsiusToRaw(70.0).toInt()
        val above = Isotherm(IsothermMode.ABOVE, 40, 0).rawBand(planck::celsiusToRaw)!!
        val below = Isotherm(IsothermMode.BELOW, 40, 0).rawBand(planck::celsiusToRaw)!!
        val between = Isotherm(IsothermMode.BETWEEN, 60, 45).rawBand(planck::celsiusToRaw)!!
        assertTrue(above.contains(at50))
        assertFalse(above.contains(at30))
        assertTrue(below.contains(at30))
        assertFalse(below.contains(at50))
        assertTrue(between.contains(at50))
        assertFalse(between.contains(at30))
        assertFalse(between.contains(at70))
    }

    @Test
    fun betweenTakesItsEndsInEitherOrder() {
        assertEquals(45 to 60, Isotherm(IsothermMode.BETWEEN, 60, 45).celsiusBand())
        assertEquals(45 to 60, Isotherm(IsothermMode.BETWEEN, 45, 60).celsiusBand())
    }

    @Test
    fun anEndTheEquationCannotReachLeavesItOff() {
        assertNull(Isotherm(IsothermMode.ABOVE, 40, 0).rawBand { Double.NaN })
    }
}
