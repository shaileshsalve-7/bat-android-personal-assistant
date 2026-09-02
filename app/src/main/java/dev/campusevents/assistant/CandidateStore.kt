package dev.campusevents.assistant

import android.content.Context
import org.json.JSONObject

/** Private, short-lived candidates awaiting an explicit user choice. */
class CandidateStore(context: Context) {
    private val prefs = context.getSharedPreferences("candidates", Context.MODE_PRIVATE)

    fun put(event: CampusEvent) = prefs.edit().putString(event.id, event.toJson().toString()).apply()
    fun get(id: String): CampusEvent? = runCatching {
        prefs.getString(id, null)?.let { CampusEvent.fromJson(JSONObject(it)) }
    }.getOrNull()
    fun remove(id: String) = prefs.edit().remove(id).apply()
}
