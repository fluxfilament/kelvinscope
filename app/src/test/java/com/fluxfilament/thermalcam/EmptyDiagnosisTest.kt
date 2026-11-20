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

import com.fluxfilament.thermalcam.EmptyDiagnosis.Companion.of
import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyDiagnosisTest {

    @Test
    fun streamingNeedsNoExplanation() =
        assertEquals(EmptyDiagnosis.NONE, of(true, true, running = true, haveGeometry = true, stoppedWithReason = false))

    @Test
    fun nothingPluggedIn() =
        assertEquals(EmptyDiagnosis.NOT_PLUGGED, of(false, false, running = false, haveGeometry = false, stoppedWithReason = false))

    @Test
    fun pluggedInButNotAllowed() =
        assertEquals(EmptyDiagnosis.NO_PERMISSION, of(true, false, running = false, haveGeometry = false, stoppedWithReason = false))

    @Test
    fun openedButNoFrameYet() =
        assertEquals(EmptyDiagnosis.WAITING, of(true, true, running = true, haveGeometry = false, stoppedWithReason = false))

    @Test
    fun aStreamThatGaveUp() =
        assertEquals(EmptyDiagnosis.STOPPED, of(true, true, running = false, haveGeometry = true, stoppedWithReason = true))

    @Test
    fun unpluggingWinsOverAnOldStopReason() =
        assertEquals(EmptyDiagnosis.NOT_PLUGGED, of(false, true, running = false, haveGeometry = true, stoppedWithReason = true))
}
