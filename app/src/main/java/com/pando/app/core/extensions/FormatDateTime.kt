package com.pando.app.core.extensions

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun String.formatDateTime(): String {
    val now = LocalDateTime.now()

    val targetDateTime: LocalDateTime = LocalDateTime.parse(this)

    val hoursBetween = ChronoUnit.HOURS.between(targetDateTime, now)
    val daysBetween = ChronoUnit.DAYS.between(targetDateTime, now)
    val yearsBetween = ChronoUnit.YEARS.between(targetDateTime, now)

    val localeVi = Locale.Builder()
        .setLanguage("vi")
        .setRegion("VN")
        .build()

    return when {
        hoursBetween in 0..23 -> {
            val formatter = DateTimeFormatter.ofPattern("hh:mm a", localeVi)
            targetDateTime.format(formatter)
        }

        daysBetween in 1..6 -> {
            val dayOfWeek = targetDateTime.dayOfWeek.value
            if (dayOfWeek == 7) "T.CN" else "T.${dayOfWeek + 1}"
        }

        yearsBetween < 1 -> {
            val formatter = DateTimeFormatter.ofPattern("dd 'Th'MM", localeVi)
            targetDateTime.format(formatter)
        }

        else -> {
            val formatter = DateTimeFormatter.ofPattern("dd 'Th'MM, yyyy", localeVi)
            targetDateTime.format(formatter)
        }
    }
}