package com.example.pardadns.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProtectionScheduleTest {

    // 2026-10-19 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    private val daytime = ProtectionSchedule(enabled = true, startMinute = 7 * 60, endMinute = 23 * 60)
    private val overnight = ProtectionSchedule(enabled = true, startMinute = 22 * 60, endMinute = 6 * 60)
    private val weekdays = daytime.copy(days = 0b0011111)

    @Test fun `daytime window`() {
        assertFalse(daytime.isActiveAt(at(19, 6, 59)))
        assertTrue(daytime.isActiveAt(at(19, 7, 0)))
        assertTrue(daytime.isActiveAt(at(19, 22, 59)))
        assertFalse(daytime.isActiveAt(at(19, 23, 0)))
    }

    @Test fun `overnight window spans midnight`() {
        assertTrue(overnight.isActiveAt(at(19, 23)))
        assertTrue(overnight.isActiveAt(at(20, 5, 59)))
        assertFalse(overnight.isActiveAt(at(20, 6, 0)))
        assertFalse(overnight.isActiveAt(at(20, 12)))
    }

    @Test fun `unselected days are off, including overnight spill from them`() {
        assertFalse(weekdays.isActiveAt(at(24, 12)))           // Saturday
        assertTrue(weekdays.isActiveAt(at(23, 12)))            // Friday
        val friNight = overnight.copy(days = 0b0010000)        // Friday only
        assertTrue(friNight.isActiveAt(at(24, 2)))             // Saturday 02:00 belongs to Friday's window
        assertFalse(friNight.isActiveAt(at(25, 2)))            // Sunday 02:00 does not
    }

    @Test fun `next change and next start`() {
        assertEquals(at(19, 23), daytime.nextChangeAfter(at(19, 12)))
        assertEquals(at(20, 7), daytime.nextChangeAfter(at(19, 23)))
        assertEquals(at(20, 7), daytime.nextStartAfter(at(19, 12)))
        // Friday evening with weekdays-only: next start is Monday.
        assertEquals(at(26, 7), weekdays.nextStartAfter(at(23, 23, 30)))
        assertEquals(at(20, 6), overnight.nextChangeAfter(at(19, 23)))
    }

    @Test fun `no selected days means no boundaries`() {
        assertNull(daytime.copy(days = 0).nextChangeAfter(at(19, 12)))
        assertFalse(daytime.copy(days = 0).isActiveAt(at(19, 12)))
    }
}
