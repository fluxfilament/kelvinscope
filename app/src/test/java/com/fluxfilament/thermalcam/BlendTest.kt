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
import org.junit.Assert.assertTrue
import org.junit.Test

/** The share of the visible layer, as the slider sets it and the compositor paints it. */
class BlendTest {

    @Test
    fun theEndsAreThermalAndVisibleOnly() {
        assertTrue(Blend.THERMAL.isThermal)
        assertEquals(0, Blend.THERMAL.visibleAlpha)
        val visible = Blend.of(100)
        assertTrue(visible.isVisibleOnly)
        assertFalse(visible.isThermal)
        assertEquals(255, visible.visibleAlpha)
    }

    @Test
    fun theOldStepsPaintAsBefore() {
        // 0.1.x had fixed 35 % and 60 % modes with these alphas; a snapshot taken at
        // the same share must look the same after the update.
        assertEquals(89, Blend.of(35).visibleAlpha)
        assertEquals(153, Blend.of(60).visibleAlpha)
    }

    @Test
    fun alphaRoundsToNearest() {
        assertEquals(3, Blend.of(1).visibleAlpha) // 2.55
        assertEquals(128, Blend.of(50).visibleAlpha) // 127.5
    }

    @Test
    fun outOfRangeIsClamped() {
        assertEquals(0, Blend.of(-10).visiblePercent)
        assertEquals(100, Blend.of(140).visiblePercent)
    }
}
