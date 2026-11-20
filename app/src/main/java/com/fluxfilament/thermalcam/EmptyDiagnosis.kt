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
 * Why the picture is empty, told apart rather than summed up as "no camera".
 *
 * Other thermal apps answer every one of these with the same "enable OTG", and the
 * user is left to guess which step failed. The cases need different fixes: plugging
 * the camera in, granting the app USB access, or waiting out (or restarting) a stream
 * that has not started.
 */
enum class EmptyDiagnosis {
    /** Frames are arriving: nothing to explain. */
    NONE,

    /** No FLIR ONE among the phone's USB devices. */
    NOT_PLUGGED,

    /** The camera is there, but the app was not allowed to open it. */
    NO_PERMISSION,

    /** Opened and running, but no frame has come through yet. */
    WAITING,

    /** It was running and stopped by itself; the reason is in the log. */
    STOPPED;

    companion object {
        fun of(
            devicePresent: Boolean,
            hasPermission: Boolean,
            running: Boolean,
            haveGeometry: Boolean,
            stoppedWithReason: Boolean,
        ): EmptyDiagnosis = when {
            running && haveGeometry -> NONE
            running -> WAITING
            !devicePresent -> NOT_PLUGGED
            !hasPermission -> NO_PERMISSION
            stoppedWithReason -> STOPPED
            // Present and allowed, but not started: the start is a moment away.
            else -> WAITING
        }
    }
}
