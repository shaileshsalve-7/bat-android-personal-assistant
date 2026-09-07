package dev.campusevents.assistant

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Deliberately small, transparent rule set; no notification content leaves the phone except selected events. */
object EventParser {
    private val eventWords = Regex("\\b(workshop|hackathon|coding|code ?fest|ai|artificial intelligence|placement|internship|career|seminar|webinar|talk|lecture|bootcamp|competition|event|register)\\b", RegexOption.IGNORE_CASE)
    private val highWords = Regex("\\b(hackathon|workshop|coding|programming|ai|artificial intelligence|placement|internship|career|dsa|developer|tech talk|machine learning)\\b", RegexOption.IGNORE_CASE)
    private val dateNumeric = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
    private val dateNamed = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b", RegexOption.IGNORE_CASE)
    private val numericRange = Regex("(?:from\\s+)?(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\s*(?:to|through|until|-)\\s*(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val namedRange = Regex("(?:from\\s+(?:the\\s+)?)?(\\d{1,2})(?:st|nd|rd|th)?\\s*(?:to|through|until|-)\\s*(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\\s+(\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val time = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b", RegexOption.IGNORE_CASE)
    private val durationDays = Regex("\\b(?:for|over|lasting)\\s+(one|two|three|four|five|six|seven|eight|nine|ten|\\d{1,2})\\s+days?\\b", RegexOption.IGNORE_CASE)
    private val link = Regex("https?://[^\\s>]+", RegexOption.IGNORE_CASE)
    private val place = Regex("(?:venue|location|at|room|hall)\\s*[:\\-]?\\s*([^\\n.;]{3,60})", RegexOption.IGNORE_CASE)
    private val relativeDate = Regex("\\b(today|tomorrow|day after tomorrow)\\b", RegexOption.IGNORE_CASE)
    private val genericVoiceWords = setOf("add", "an", "a", "event", "workshop", "hackathon", "talk", "seminar", "webinar", "lecture", "bootcamp", "coding", "career", "placement", "register", "on", "at", "in", "the", "for", "pm", "am", "today", "tomorrow")

    fun parse(preview: String, organizer: String?): CampusEvent? {
        val clean = preview.replace(Regex("(?i)\\b([ap])\\.?m\\.?\\b"), "\$1m").replace(Regex("\\s+"), " ").trim()
        if (clean.length < 12 || !eventWords.containsMatchIn(clean)) return null
        val today = LocalDate.now()
        val explicitRange = extractExplicitRange(clean, today)
        val (date, unknownDate) = if (explicitRange != null) explicitRange.first to false else extractDate(clean)
        // An event mention with no date is kept as a tentative calendar item rather than silently discarded.
        val eventDate = date ?: today
        val endDate = explicitRange?.second ?: extractDurationEnd(clean, eventDate)
        val match = time.find(clean)
        val timeOfDay = match?.let { parseTime(it) }
        val allDay = endDate != null && timeOfDay == null
        val start = if (allDay) allDayMillis(eventDate) else timedMillis(eventDate, timeOfDay ?: LocalTime.of(10, 0))
        val end = endDate?.let { finalDate ->
            if (allDay) allDayMillis(finalDate.plusDays(1)) else timedMillis(finalDate, timeOfDay ?: LocalTime.of(10, 0))
        }
        val name = clean.split(Regex("[.!?]"), limit = 2).first().take(100).ifBlank { "Campus event" }
        val priority = if (highWords.containsMatchIn(clean)) 2 else 1
        val stable = (name.lowercase() + "|" + eventDate + "|" + (endDate ?: "") + "|" + (organizer ?: "")).take(250)
        val id = MessageDigest.getInstance("SHA-256").digest(stable.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
        return CampusEvent(
            id = id, name = name, startMillis = start, location = place.find(clean)?.groupValues?.get(1)?.trim(),
            link = link.find(clean)?.value, organizer = organizer, sourcePreview = clean.take(1200), priority = priority,
            needsDateConfirmation = unknownDate, endMillis = end, allDay = allDay
        )
    }

    /** Voice must contain a named event plus an explicit date and time; no screen context is ever used. */
    fun isCompleteVoiceEvent(text: String): Boolean {
        val normalized = text.lowercase(Locale.US)
        val hasDate = relativeDate.containsMatchIn(normalized) || dateNumeric.containsMatchIn(normalized) || dateNamed.containsMatchIn(normalized) || namedRange.containsMatchIn(normalized)
        val hasTime = time.containsMatchIn(normalized)
        val meaningful = Regex("[a-z]{2,}").findAll(normalized).map { it.value }.any { it !in genericVoiceWords } || Regex("\\bai\\b").containsMatchIn(normalized)
        return hasDate && hasTime && meaningful
    }

    private fun extractExplicitRange(text: String, today: LocalDate): Pair<LocalDate, LocalDate>? {
        numericRange.find(text)?.let { m ->
            val startYear = year(m.groupValues[3], today.year)
            val start = validDate(startYear, m.groupValues[2].toInt(), m.groupValues[1].toInt()) ?: return@let
            val endYear = year(m.groupValues[6], start.year)
            val end = validDate(endYear, m.groupValues[5].toInt(), m.groupValues[4].toInt()) ?: return@let
            return normalizeRange(start, end)
        }
        namedRange.find(text)?.let { m ->
            val month = monthNumber(m.groupValues[3]) ?: return@let
            val baseYear = year(m.groupValues[4], today.year)
            val start = validDate(baseYear, month, m.groupValues[1].toInt()) ?: return@let
            val end = validDate(baseYear, month, m.groupValues[2].toInt()) ?: return@let
            return normalizeRange(start, end)
        }
        return null
    }

    private fun normalizeRange(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate>? =
        if (end.isBefore(start)) start to end.plusYears(1) else start to end

    private fun extractDurationEnd(text: String, start: LocalDate): LocalDate? {
        val token = durationDays.find(text)?.groupValues?.get(1)?.lowercase(Locale.US) ?: return null
        val days = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10)[token] ?: token.toIntOrNull()
        return days?.takeIf { it > 1 }?.let { start.plusDays((it - 1).toLong()) }
    }

    private fun extractDate(text: String): Pair<LocalDate?, Boolean> {
        val now = LocalDate.now()
        if (Regex("\\bday after tomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return now.plusDays(2) to false
        if (Regex("\\btomorrow\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return now.plusDays(1) to false
        if (Regex("\\btoday\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return now to false
        dateNumeric.find(text)?.let { m ->
            val year = year(m.groupValues[3], now.year)
            return validDate(year, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { date ->
                (if (date.isBefore(now.minusDays(1)) && m.groupValues[3].isBlank()) date.plusYears(1) else date) to false
            } ?: (null to true)
        }
        dateNamed.find(text)?.let { m ->
            val month = monthNumber(m.groupValues[2]) ?: return null to true
            return validDate(now.year, month, m.groupValues[1].toInt())?.let { date ->
                (if (date.isBefore(now.minusDays(1))) date.plusYears(1) else date) to false
            } ?: (null to true)
        }
        return null to true
    }

    private fun parseTime(match: MatchResult): LocalTime {
        val hour = match.groupValues[1].toIntOrNull() ?: 10
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val hour24 = when (match.groupValues[3].lowercase(Locale.US)) {
            "pm" -> if (hour == 12) 12 else hour + 12
            "am" -> if (hour == 12) 0 else hour
            else -> hour.coerceIn(0, 23)
        }
        return LocalTime.of(hour24, minute)
    }

    private fun timedMillis(date: LocalDate, time: LocalTime) = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun allDayMillis(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun validDate(year: Int, month: Int, day: Int) = runCatching { LocalDate.of(year, month, day) }.getOrNull()
    private fun year(raw: String, default: Int) = raw.toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: default
    private fun monthNumber(raw: String): Int? = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(raw.take(3).lowercase(Locale.US)).takeIf { it >= 0 }?.plus(1)
}
