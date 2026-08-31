package dev.campusevents.assistant

import android.content.Context
import org.json.JSONArray

class EventStore(context: Context) {
    private val prefs = context.getSharedPreferences("events", Context.MODE_PRIVATE)
    @Synchronized fun all(): List<CampusEvent> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]")); (0 until a.length()).map { CampusEvent.fromJson(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())
    /** Returns true only for a newly seen event. */
    @Synchronized fun addIfNew(event: CampusEvent): Boolean {
        val old = all().toMutableList()
        if (old.any { it.id == event.id }) return false
        old.add(event); save(old); return true
    }
    @Synchronized fun replace(event: CampusEvent) { save(all().map { if (it.id == event.id) event else it }) }
    private fun save(events: List<CampusEvent>) = prefs.edit().putString("items", JSONArray(events.map { it.toJson() }).toString()).apply()
}
