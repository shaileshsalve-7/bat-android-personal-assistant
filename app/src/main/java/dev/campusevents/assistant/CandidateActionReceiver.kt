package dev.campusevents.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CandidateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CANDIDATE_ID) ?: return
        val candidates = CandidateStore(context)
        val event = candidates.get(id) ?: return
        when (intent.action) {
            ACTION_ADD -> when (EventActions.addToCalendar(context, event)) {
                is CalendarAddResult.Added -> { candidates.remove(id); CandidateNotifications.success(context, event) }
                CalendarAddResult.AlreadySaved -> { candidates.remove(id); CandidateNotifications.message(context, event.id.hashCode(), "Already saved", "This event is already in Bat.") }
                CalendarAddResult.CalendarFailed -> CandidateNotifications.message(context, event.id.hashCode(), "Calendar not updated", "Check Bat's Calendar permission, then use Edit to try again.")
            }
            ACTION_IGNORE -> { candidates.remove(id); CandidateNotifications.cancel(context, event.id.hashCode()) }
        }
    }

    companion object {
        const val ACTION_ADD = "dev.campusevents.assistant.ADD_CANDIDATE"
        const val ACTION_IGNORE = "dev.campusevents.assistant.IGNORE_CANDIDATE"
        const val EXTRA_CANDIDATE_ID = "candidate_id"
    }
}
