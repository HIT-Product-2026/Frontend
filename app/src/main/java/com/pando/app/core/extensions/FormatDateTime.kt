package com.pando.app.core.extensions

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun LocalDateTime.formatDateTime(): String {
    val now = LocalDateTime.now()

    val hoursBetween = ChronoUnit.HOURS.between(this, now)
    val daysBetween = ChronoUnit.DAYS.between(this, now)
    val yearsBetween = ChronoUnit.YEARS.between(this, now)

    val localeVi = Locale.Builder()
        .setLanguage("vi")
        .setRegion("VN")
        .build()

    return when {
        hoursBetween in 0..23 -> {
            val formatter = DateTimeFormatter.ofPattern("hh:mm a", localeVi)
            this.format(formatter)
        }

        daysBetween in 1..6 -> {
            val dayOfWeek = this.dayOfWeek.value
            if (dayOfWeek == 7) "T.CN" else "T.${dayOfWeek + 1}"
        }

        yearsBetween < 1 -> {
            val formatter = DateTimeFormatter.ofPattern("dd 'Th'MM", localeVi)
            this.format(formatter)
        }

        else -> {
            val formatter = DateTimeFormatter.ofPattern("dd 'Th'MM, yyyy", localeVi)
            this.format(formatter)
        }
    }
}

fun String.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.parse(this)
}