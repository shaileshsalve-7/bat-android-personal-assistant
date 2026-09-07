package dev.campusevents.assistant

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object SyncClient {
    private val executor = Executors.newSingleThreadExecutor()
    fun configured(context: Context): Boolean = context.getSharedPreferences("sync", Context.MODE_PRIVATE).getString("url", "")!!.isNotBlank()
    fun push(context: Context, event: CampusEvent) {
        val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
        val base = prefs.getString("url", "")!!.trimEnd('/'); val token = prefs.getString("token", "")!!
        if (base.isBlank() || token.isBlank()) return
        executor.execute {
            runCatching {
                val body = JSONObject().apply {
                    put("id", event.id); put("name", event.name); put("startMillis", event.startMillis); put("endMillis", event.endMillis); put("allDay", event.allDay); put("location", event.location)
                    put("link", event.link); put("organizer", event.organizer); put("priority", event.priority); put("needsDateConfirmation", event.needsDateConfirmation)
                }.toString().toByteArray()
                (URL("$base/api/events").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000; readTimeout = 8000; doOutput = true; outputStream.use { it.write(body) }; inputStream.close(); disconnect()
                }
            }
        }
    }
    fun pushAll(context: Context) = EventStore(context).all().forEach { push(context, it) }
}
