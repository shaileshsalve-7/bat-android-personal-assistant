package dev.campusevents.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("whatsapp_detection", true)) return
        val extras = sbn.notification.extras ?: return
        // Fail closed: only Android/WhatsApp's explicit group metadata enables automatic detection.
        if (!extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return
        val group = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = listOfNotNull(extras.getCharSequence(Notification.EXTRA_BIG_TEXT), extras.getCharSequence(Notification.EXTRA_TEXT))
            .joinToString(" ").trim()
        val candidate = EventParser.parse(text, group) ?: return
        CandidateStore(this).put(candidate)
        // Detection only proposes an event. It never creates a Calendar entry without Add confirmation.
        CandidateNotifications.showCandidate(this, candidate)
    }

    private companion object { const val WHATSAPP_PACKAGE = "com.whatsapp" }
}
