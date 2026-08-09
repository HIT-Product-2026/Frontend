package com.pando.app.features.home.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProfileInputValidatorTest {
    @Test
    fun `accepts Vietnamese phone with 10 digits starting with zero`() {
        assertTrue(ProfileInputValidator.isValidVietnamPhone("0987654321"))
    }

    @Test
    fun `rejects invalid Vietnamese phone numbers`() {
        assertFalse(ProfileInputValidator.isValidVietnamPhone("987654321"))
        assertFalse(ProfileInputValidator.isValidVietnamPhone("1987654321"))
        assertFalse(ProfileInputValidator.isValidVietnamPhone("09876543210"))
        assertFalse(ProfileInputValidator.isValidVietnamPhone("+84987654321"))
        assertFalse(ProfileInputValidator.isValidVietnamPhone("09876abcde"))
    }

    @Test
    fun `parses and formats a valid display birthday strictly`() {
        val birthday = ProfileInputValidator.parseDisplayBirthday("29/02/2024")

        assertEquals(LocalDate.of(2024, 2, 29), birthday)
        assertEquals("29/02/2024", ProfileInputValidator.formatDisplayBirthday(birthday!!))
    }

    @Test
    fun `rejects invalid calendar date`() {
        assertNull(ProfileInputValidator.parseDisplayBirthday("31/02/2024"))
        assertNull(ProfileInputValidator.parseDisplayBirthday("1/2/2024"))
    }

    @Test
    fun `only dates after today are future birthdays`() {
        val today = LocalDate.of(2026, 8, 8)

        assertFalse(ProfileInputValidator.isFutureBirthday(today.minusDays(1), today))
        assertFalse(ProfileInputValidator.isFutureBirthday(today, today))
        assertTrue(ProfileInputValidator.isFutureBirthday(today.plusDays(1), today))
    }
}
