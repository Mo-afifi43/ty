package com.mhma.smartlearn.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Dates and times the way you'd say them out loud. */
object TimeText {

    enum class Urgency { OVERDUE, TODAY, SOON, LATER }

    data class DueLabel(val text: String, val urgency: Urgency)

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthTime = DateTimeFormatter.ofPattern("EEE, d MMM '·' h:mm a", Locale.ENGLISH)
    private val fullDay = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

    fun dueLabel(dueMillis: Long, nowMillis: Long = System.currentTimeMillis()): DueLabel {
        val zone = ZoneId.systemDefault()
        val due = Instant.ofEpochMilli(dueMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)

        if (dueMillis < nowMillis) {
            val days = ChronoUnit.DAYS.between(due.toLocalDate(), now.toLocalDate())
            val text = when {
                days == 0L -> "Was due today"
                days == 1L -> "Was due yesterday"
                days < 7L -> "Overdue by $days days"
                else -> "Overdue"
            }
            return DueLabel(text, Urgency.OVERDUE)
        }

        val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
        val hours = ChronoUnit.HOURS.between(now, due)
        return when {
            days == 0L && hours < 1L -> DueLabel("Due within the hour", Urgency.TODAY)
            days == 0L -> DueLabel("Due today", Urgency.TODAY)
            days == 1L -> DueLabel("Due tomorrow", Urgency.SOON)
            days < 7L -> DueLabel("Due in $days days", Urgency.SOON)
            days < 14L -> DueLabel("Due next week", Urgency.LATER)
            days < 60L -> DueLabel("Due in ${days / 7} weeks", Urgency.LATER)
            else -> DueLabel("Due ${dayMonth.format(due)}", Urgency.LATER)
        }
    }

    /** "Mon, 15 Jan · 11:59 PM" */
    fun dateTime(millis: Long): String =
        dayMonthTime.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** "Updated 5 min ago", "Updated yesterday", "Never updated". */
    fun updatedAgo(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return "Never updated"
        val diff = nowMillis - millis
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated $minutes min ago"
            hours < 24 -> "Updated $hours h ago"
            days == 1L -> "Updated yesterday"
            days < 7 -> "Updated $days days ago"
            else -> "Updated on " + dayMonth.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
        }
    }

    fun greeting(now: LocalDateTime = LocalDateTime.now()): String = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up?"
    }

    /** "Monday, 28 September" */
    fun todayHeading(now: ZonedDateTime = ZonedDateTime.now()): String = fullDay.format(now)
}
