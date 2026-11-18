package com.parkback.pro

import org.junit.Assert.*
import org.junit.Test

class FormattingTest {
    @Test
    fun coordinatesIncludeBoundariesAndZero() {
        assertTrue(validCoordinates(-90.0, 180.0))
        assertTrue(validCoordinates(90.0, -180.0))
        assertTrue(validCoordinates(0.0, 0.0))
    }

    @Test
    fun rejectsInvalidFixes() {
        assertFalse(validCoordinates(Double.NaN, 0.0))
        assertFalse(validCoordinates(0.0, 181.0))
        assertFalse(validCoordinates(-91.0, 0.0))
        assertFalse(validCoordinates(0.0, Double.POSITIVE_INFINITY))
    }

    @Test
    fun elapsedRolloversAndClockChange() {
        assertEquals("less than a minute", elapsed(-100))
        assertEquals("less than a minute", elapsed(59_999))
        assertEquals("24 min", elapsed(24 * 60_000L))
        assertEquals("1 h 0 min", elapsed(3_600_000))
        assertEquals("1 d 1 h", elapsed(25 * 3_600_000L))
    }

    @Test
    fun generatesWalkingDestination() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=12.94,77.69&travelmode=walking",
            directions(12.94, 77.69),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDestinationFails() {
        directions(91.0, 0.0)
    }
}
