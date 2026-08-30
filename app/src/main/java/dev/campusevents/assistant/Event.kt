package dev.campusevents.assistant

import org.json.JSONObject

data class CampusEvent(
    val id: String,
    val name: String,
    val startMillis: Long,
    val location: String?,
    val link: String?,
    val organizer: String?,
    val sourcePreview: String,
    val priority: Int,
    val needsDateConfirmation: Boolean = false,
    val calendarEventId: Long? = null,
    /** Calendar end boundary. For all-day events this is the exclusive midnight after the final date. */
    val endMillis: Long? = null,
    val allDay: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("startMillis", startMillis)
        put("location", location); put("link", link); put("organizer", organizer)
        put("sourcePreview", sourcePreview); put("priority", priority)
        put("needsDateConfirmation", needsDateConfirmation); put("calendarEventId", calendarEventId)
        put("endMillis", endMillis); put("allDay", allDay)
    }
    companion object {
        fun fromJson(o: JSONObject) = CampusEvent(
            id = o.getString("id"), name = o.getString("name"), startMillis = o.getLong("startMillis"),
            location = o.optString("location").ifBlank { null }, link = o.optString("link").ifBlank { null },
            organizer = o.optString("organizer").ifBlank { null }, sourcePreview = o.optString("sourcePreview"),
            priority = o.optInt("priority"), needsDateConfirmation = o.optBoolean("needsDateConfirmation"),
            calendarEventId = if (o.isNull("calendarEventId")) null else o.optLong("calendarEventId"),
            endMillis = if (o.isNull("endMillis")) null else o.optLong("endMillis"), allDay = o.optBoolean("allDay")
        )
    }
}
