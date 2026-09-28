package com.mhma.smartlearn.data.network.scraper

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Turns the human dates Moodle prints ("Monday, 15 January 2024, 11:59 PM")
 * into epoch millis. The portal is set to English, so that's what we handle,
 * with a few extra shapes just in case an admin changes the format.
 */
object MoodleDates {

    // The weekday is stripped before parsing (see [clean]); it adds nothing and a
    // mismatch between it and the date would make java.time reject the whole string.
    private val fullPatterns = listOf(
        "d MMMM yyyy, h:mm a",
        "d MMMM yyyy, H:mm",
        "d MMM yyyy, h:mm a",
        "d MMM yyyy, H:mm",
        "MMMM d, yyyy, h:mm a",
        "MMM d, yyyy, h:mm a",
        "d/MM/yy, HH:mm",
        "d/MM/yyyy, HH:mm",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm",
    ).map(::formatter)

    private val dateOnlyPatterns = listOf(
        "d MMMM yyyy",
        "d MMM yyyy",
        "MMMM d, yyyy",
        "d/MM/yy",
        "d/MM/yyyy",
        "yyyy-MM-dd",
    ).map(::formatter)

    // Some calendar views drop the year for dates in the current year.
    private val noYearPatterns = listOf(
        "d MMMM, h:mm a",
        "d MMMM, H:mm",
        "d MMM, h:mm a",
        "d MMM, H:mm",
        "d MMMM",
        "d MMM",
    ).map(::formatter)

    private val timeOnlyPatterns = listOf("h:mm a", "H:mm", "h a").map(::formatter)

    private val labels = Regex(
        "^(due|opens?|opened|closes?|closed|expected completion|until|by)\\s*:?\\s*",
        RegexOption.IGNORE_CASE,
    )
    private val weekday = Regex(
        "^(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun),?\\s+",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        raw: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zone),
    ): Long? {
        val text = clean(raw) ?: return null

        for (f in fullPatterns) {
            tryParse { LocalDateTime.parse(text, f) }?.let { return it.toMillis(zone) }
        }
        for (f in dateOnlyPatterns) {
            tryParse { LocalDate.parse(text, f) }?.let { return it.atTime(END_OF_DAY).toMillis(zone) }
        }
        for (f in noYearPatterns) {
            tryParse { f.parse(text) }?.let { parsed ->
                val month = parsed.get(ChronoField.MONTH_OF_YEAR)
                val day = parsed.get(ChronoField.DAY_OF_MONTH)
                val hour = if (parsed.isSupported(ChronoField.HOUR_OF_DAY)) parsed.get(ChronoField.HOUR_OF_DAY) else 23
                val minute = if (parsed.isSupported(ChronoField.MINUTE_OF_HOUR)) parsed.get(ChronoField.MINUTE_OF_HOUR) else 59
                var date = tryParse { LocalDate.of(now.year, month, day).atTime(hour, minute) } ?: return null
                // "Upcoming" means the future; if we ended up far in the past it's next year.
                if (date.isBefore(now.minusDays(45))) date = date.plusYears(1)
                return date.toMillis(zone)
            }
        }

        relativeDay(text, now)?.let { return it.toMillis(zone) }
        return null
    }

    /** "Today, 11:59 PM" / "Tomorrow, 8:00 AM" as the timeline block prints them. */
    private fun relativeDay(text: String, now: LocalDateTime): LocalDateTime? {
        val lower = text.lowercase(Locale.ENGLISH)
        val day = when {
            lower.startsWith("today") -> now.toLocalDate()
            lower.startsWith("tomorrow") -> now.toLocalDate().plusDays(1)
            lower.startsWith("yesterday") -> now.toLocalDate().minusDays(1)
            else -> return null
        }
        val rest = text.substringAfter(',', "").trim()
        if (rest.isEmpty()) return day.atTime(END_OF_DAY)
        for (f in timeOnlyPatterns) {
            tryParse { LocalTime.parse(rest, f) }?.let { return day.atTime(it) }
        }
        return day.atTime(END_OF_DAY)
    }

    private fun clean(raw: String?): String? {
        if (raw == null) return null
        var s = raw.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
        s = labels.replace(s, "").trim()
        s = weekday.replace(s, "").trim().trimEnd('.', ',', ' ')
        return s.ifEmpty { null }
    }

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH)

    private inline fun <T> tryParse(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    private fun LocalDateTime.toMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()

    private val END_OF_DAY: LocalTime = LocalTime.of(23, 59)
}
