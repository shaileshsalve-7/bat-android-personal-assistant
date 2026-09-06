package dev.campusevents.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

object CandidateNotifications {
    private const val CHANNEL = "event_candidates"

    fun showCandidate(context: Context, event: CampusEvent) {
        val id = event.id.hashCode()
        val edit = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_EDIT_CANDIDATE
            data = Uri.parse("bat://candidate/${event.id}")
            putExtra(CandidateActionReceiver.EXTRA_CANDIDATE_ID, event.id)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val add = action(context, CandidateActionReceiver.ACTION_ADD, event.id, id)
        val ignore = action(context, CandidateActionReceiver.ACTION_IGNORE, event.id, id + 1)
        notify(context, id, NotificationCompat.Builder(context, channel(context))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Review campus event")
            .setContentText(event.name.take(90))
            .setStyle(NotificationCompat.BigTextStyle().bigText("${event.name.take(180)}\nReview details before adding to Calendar."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(edit)
            .addAction(0, "Add", add)
            .addAction(0, "Edit", edit)
            .addAction(0, "Ignore", ignore)
            .build())
    }

    fun success(context: Context, event: CampusEvent) = message(context, event.id.hashCode(), "Done, Shailesh.", "${event.name.take(90)} was added to your Calendar.")
    fun message(context: Context, id: Int, title: String, body: String) = notify(context, id, NotificationCompat.Builder(context, channel(context))
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).build())
    fun cancel(context: Context, id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)

    private fun action(context: Context, action: String, id: String, requestCode: Int) = PendingIntent.getBroadcast(context, requestCode,
        Intent(context, CandidateActionReceiver::class.java).apply { this.action = action; data = Uri.parse("bat://candidate/$id/$action"); putExtra(CandidateActionReceiver.EXTRA_CANDIDATE_ID, id) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun channel(context: Context): String {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Bat event reviews", NotificationManager.IMPORTANCE_HIGH))
        return CHANNEL
    }
    private fun notify(context: Context, id: Int, notification: android.app.Notification) = context.getSystemService(NotificationManager::class.java).notify(id, notification)
}
