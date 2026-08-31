package dev.campusevents.assistant

import android.content.Context
import android.util.Log

sealed class CalendarAddResult {
    data class Added(val event: CampusEvent) : CalendarAddResult()
    data object AlreadySaved : CalendarAddResult()
    data object CalendarFailed : CalendarAddResult()
}

/** Shared safe calendar path for preview confirmation and notification actions. */
object EventActions {
    fun addToCalendar(context: Context, event: CampusEvent): CalendarAddResult {
        val store = EventStore(context)
        val existing = store.all().find { it.id == event.id }
        if (existing?.calendarEventId != null) return CalendarAddResult.AlreadySaved
        val target = existing ?: event
        if (existing == null) store.addIfNew(target)
        val saved = runCatching { CalendarWriter.add(context, target) }.onFailure {
            Log.w("BatCalendar", "Calendar write failed", it)
        }.getOrElse { target }
        if (saved != target) store.replace(saved)
        if (saved.calendarEventId == null) return CalendarAddResult.CalendarFailed
        SyncClient.push(context, saved)
        return CalendarAddResult.Added(saved)
    }
}
