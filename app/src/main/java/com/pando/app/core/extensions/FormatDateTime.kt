package com.pando.app.core.extensions

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

fun LocalDateTime.formatDateTime(): String {
    if (this == null) {
        return ""
    }

    val now = LocalDateTime.now()
    val messageDate = this.toLocalDate()
    val today = now.toLocalDate()

    val localeVi = Locale.Builder()
        .setLanguage("vi")
        .setRegion("VN")
        .build()

    return when {
        messageDate == today -> {
            val formatter = DateTimeFormatter.ofPattern("hh:mm a", localeVi)
            this.format(formatter)
        }

        messageDate == today.minusDays(1) -> {
            "Hôm qua"
        }

        messageDate.isAfter(today.minusDays(7)) -> {
            val dayOfWeek = this.dayOfWeek.value
            if (dayOfWeek == 7) "T.CN" else "T.${dayOfWeek + 1}"
        }

        this.year == now.year -> {
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