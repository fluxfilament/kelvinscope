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

import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialsTest {

    @Test
    fun everyValueIsOneTheSliderCanSetAndAboveTheUnreliableRange() {
        for (m in Materials.ALL) assertTrue("${m.emissivity}", m.emissivity in 0.30..1.00)
    }

    @Test
    fun theListRunsFromHighToLow() {
        val values = Materials.ALL.map { it.emissivity }
        assertTrue(values == values.sortedDescending())
    }
}
