package com.parkback.pro

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun validCoordinates(lat: Double, lon: Double) = lat in -90.0..90.0 && lon in -180.0..180.0

fun directions(lat: Double, lon: Double): String {
    require(validCoordinates(lat, lon))
    return "https://www.google.com/maps/dir/?api=1&destination=$lat,$lon&travelmode=walking"
}

fun elapsed(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    return when {
        minutes < 1 -> "less than a minute"
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / 1440} d ${minutes % 1440 / 60} h"
    }
}

fun localDay(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun stamp(ms: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(ms))
