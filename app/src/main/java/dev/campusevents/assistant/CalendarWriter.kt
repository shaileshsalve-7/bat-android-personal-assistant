package dev.campusevents.assistant

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

object CalendarWriter {
    fun add(context: Context, event: CampusEvent): CampusEvent {
        if (event.calendarEventId != null || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) return event
        val calendarId = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.VISIBLE}=1", null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC")?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return event
        // Android Calendar treats all-day DTEND as exclusive. EventParser stores the next
        // midnight after the final stated date, so a 7th-through-13th event spans all seven days.
        val defaultMinutes = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("default_duration", 60)
        val end = event.endMillis ?: event.startMillis + defaultMinutes * 60L * 1000L
        val description = buildString {
            append("Captured from a WhatsApp notification preview by Bat.\n")
            if (event.needsDateConfirmation) append("DATE/TIME NEEDS CONFIRMATION — defaulted to today at 10:00.\n")
            event.organizer?.let { append("Organizer: $it\n") }; event.link?.let { append("Link: $it\n") }
            append("Preview: ${event.sourcePreview}")
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId); put(CalendarContract.Events.TITLE, event.name)
            put(CalendarContract.Events.DTSTART, event.startMillis); put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (event.allDay) "UTC" else java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, description); event.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return event
        val id = uri.lastPathSegment?.toLongOrNull() ?: return event
        val reminderMinutes = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("reminder_minutes", -1)
        if (reminderMinutes >= 0) runCatching {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, id); put(CalendarContract.Reminders.MINUTES, reminderMinutes); put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            })
        }
        return event.copy(calendarEventId = id)
    }
}
