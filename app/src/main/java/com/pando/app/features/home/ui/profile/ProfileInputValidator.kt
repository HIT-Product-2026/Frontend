package com.pando.app.features.home.ui.profile

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object ProfileInputValidator {
    private val vietnamPhoneRegex = Regex("^0\\d{9}$")
    private val displayDateFormatter = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(ResolverStyle.STRICT)

    fun isValidVietnamPhone(phoneNumber: String): Boolean {
        return vietnamPhoneRegex.matches(phoneNumber.trim())
    }

    fun parseDisplayBirthday(value: String): LocalDate? {
        return runCatching {
            LocalDate.parse(value.trim(), displayDateFormatter)
        }.getOrNull()
    }

    fun formatDisplayBirthday(date: LocalDate): String {
        return date.format(displayDateFormatter)
    }

    fun isFutureBirthday(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean {
        return date.isAfter(today)
    }
}
