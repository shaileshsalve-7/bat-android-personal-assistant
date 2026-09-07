package dev.campusevents.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var manualText: EditText
    private lateinit var status: TextView
    private lateinit var voiceStatus: TextView
    private lateinit var voiceTranscript: TextView
    private lateinit var voiceButton: Button
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { showStatus("Permissions updated. Review an event before adding it.") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val scroll = findViewById<ScrollView>(R.id.homeScroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padding = dp(16)
            view.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
        manualText = findViewById(R.id.manualText)
        status = findViewById(R.id.status)
        voiceStatus = findViewById(R.id.voiceStatus)
        voiceTranscript = findViewById(R.id.voiceTranscript)
        voiceButton = findViewById(R.id.voiceButton)
        tts = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    if (utteranceId == VOICE_START_PROMPT) runOnUiThread { beginRecognition() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) {
                    if (utteranceId == VOICE_START_PROMPT) runOnUiThread { beginRecognition() }
                }
            })
        }
        findViewById<Button>(R.id.reviewButton).setOnClickListener { reviewManual() }
        voiceButton.setOnClickListener { startVoiceCapture() }
        findViewById<Button>(R.id.menuButton).setOnClickListener { showMenu(it) }
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { manualText.setText(it) }
        openCandidate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { manualText.setText(it) }
        openCandidate(intent)
    }

    override fun onDestroy() { speech?.destroy(); tts?.shutdown(); super.onDestroy() }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, MENU_MANUAL, 0, "Add manually")
            menu.add(0, MENU_VOICE, 1, "Voice input")
            menu.add(0, MENU_WHATSAPP, 2, "WhatsApp detection")
            menu.add(0, MENU_SETTINGS, 3, "Settings")
            menu.add(0, MENU_ABOUT, 4, "About")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_MANUAL -> { manualText.requestFocus(); showStatus("Paste details, then choose Review event.") }
                    MENU_VOICE -> startVoiceCapture()
                    MENU_WHATSAPP -> startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    MENU_SETTINGS -> showSettings()
                    MENU_ABOUT -> AlertDialog.Builder(this@MainActivity).setTitle("About Bat").setMessage("Bat only proposes events from WhatsApp notifications Android explicitly marks as group conversations. It never reads chats, screen content, or sends messages. Nothing is added until you confirm.").setPositiveButton("Close", null).show()
                }
                true
            }
        }.show()
    }

    private fun reviewManual() {
        val event = EventParser.parse(manualText.text.toString(), "Manual intake")
        if (event == null) { showStatus("Add an event title plus a date or event keyword, then review it."); return }
        showPreview(event)
    }

    private fun openCandidate(intent: Intent) {
        if (intent.action != ACTION_EDIT_CANDIDATE) return
        val id = intent.getStringExtra(CandidateActionReceiver.EXTRA_CANDIDATE_ID) ?: return
        CandidateStore(this).get(id)?.let { showPreview(it, id) }
            ?: showStatus("That event review is no longer available.")
    }

    /** One explicit review path for manual input, voice input, and group notification candidates. */
    private fun showPreview(event: CampusEvent, candidateId: String? = null) {
        val message = "Title: ${event.name.take(100)}\nWhen: ${formatWhen(event)}\nLocation: ${event.location ?: "Not specified"}\n\nAdd this event to your Calendar?"
        showStatus("Review the event details before adding it.")
        AlertDialog.Builder(this).setTitle("Review event").setMessage(message)
            .setNegativeButton("Cancel") { _, _ -> showStatus("Not added.") }
            .setPositiveButton("Add to calendar") { _, _ ->
                when (EventActions.addToCalendar(this, event)) {
                    is CalendarAddResult.Added -> {
                        candidateId?.let { CandidateStore(this).remove(it); CandidateNotifications.cancel(this, event.id.hashCode()) }
                        manualText.text.clear(); showStatus("Done, Shailesh."); speak("Done, Shailesh.")
                    }
                    CalendarAddResult.AlreadySaved -> showStatus("This event is already saved in Bat.")
                    CalendarAddResult.CalendarFailed -> { showStatus("Bat could not add this event to Calendar. Check Calendar permission and try again."); speak("Shailesh, Bat could not add this event to Calendar.") }
                }
            }.show()
    }

    private fun startVoiceCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO)); voiceFailure("Microphone permission is needed. Allow it, then tap again."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { voiceFailure("Speech recognition is unavailable. Paste the event instead."); return }
        speech?.destroy(); voiceButton.isEnabled = false; voiceTranscript.visibility = View.GONE; showVoice("Listening… include a name, date, and time.")
        if (settings().getBoolean("voice_feedback", true) && ttsReady) {
            showVoice("Bat is ready. Listening after the prompt…")
            tts?.speak("Hey Shailesh, what would you like to add today?", TextToSpeech.QUEUE_FLUSH, null, VOICE_START_PROMPT)
        } else beginRecognition()
    }

    /** Starts only after the optional spoken prompt finishes, so Bat never recognizes itself. */
    private fun beginRecognition() {
        if (isFinishing || isDestroyed) return
        speech = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = showVoice("Listening…")
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = showVoice("Reviewing your event command…")
                override fun onError(error: Int) { voiceButton.isEnabled = true; voiceFailure(voiceError(error)) }
                override fun onResults(results: Bundle?) {
                    voiceButton.isEnabled = true
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isBlank()) { voiceFailure("No speech was received. Please try again."); return }
                    voiceTranscript.text = "Heard: $heard"; voiceTranscript.visibility = View.VISIBLE
                    reviewVoice(heard)
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings().getString("voice_language", "en-IN"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        }
    }

    private fun reviewVoice(heard: String) {
        var command = heard.trim().replace(Regex("(?i)\\b([ap])\\.?m\\.?\\b"), "\$1m")
        command = command.replace(Regex("^\\s*(?:bat|that|bad|but)[,:]?\\s*", RegexOption.IGNORE_CASE), "")
        // Recognizers may render "Bat, add" as "bat and". This still requires complete event details.
        if (command.startsWith("and ", true)) command = "add " + command.substringAfter(" ")
        if (!command.startsWith("add ", true)) { voiceFailure("Say “Bat, add” followed by a specific event name, date, and time."); return }
        val details = command.replaceFirst(Regex("^add\\s+", RegexOption.IGNORE_CASE), "")
        if (!EventParser.isCompleteVoiceEvent(details)) { voiceFailure("I need a specific event name, date, and time before I can show a review."); return }
        val event = EventParser.parse(details, "Voice command")
        if (event == null || event.needsDateConfirmation) { voiceFailure("I need a readable event date and time before I can show a review."); return }
        showVoice("Event found. Review the confirmation."); speak("Shailesh, I found an event. Shall I add it to your calendar?"); showPreview(event)
    }

    private fun showSettings() {
        val prefs = settings()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val detection = Switch(this).apply { text = "WhatsApp group detection"; isChecked = prefs.getBoolean("whatsapp_detection", true) }
        val feedback = Switch(this).apply { text = "Voice feedback"; isChecked = prefs.getBoolean("voice_feedback", true) }
        val duration = Spinner(this); duration.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Default duration: 30 minutes", "Default duration: 1 hour", "Default duration: 2 hours")); duration.setSelection(listOf(30, 60, 120).indexOf(prefs.getInt("default_duration", 60)).coerceAtLeast(0))
        val reminder = Spinner(this); reminder.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Reminder: none", "Reminder: 10 minutes before", "Reminder: 30 minutes before")); reminder.setSelection(listOf(-1, 10, 30).indexOf(prefs.getInt("reminder_minutes", -1)).coerceAtLeast(0))
        val language = Spinner(this); language.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("English (India)", "Hindi", "Marathi")); language.setSelection(listOf("en-IN", "hi-IN", "mr-IN").indexOf(prefs.getString("voice_language", "en-IN")).coerceAtLeast(0))
        val access = Button(this).apply { text = "Open notification access"; setOnClickListener { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } }
        val calendar = Button(this).apply { text = "Allow Calendar access"; setOnClickListener { permissions.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())) } }
        listOf(detection, duration, reminder, language, feedback, access, calendar).forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("Settings").setView(box).setNegativeButton("Close", null).setPositiveButton("Save") { _, _ ->
            prefs.edit().putBoolean("whatsapp_detection", detection.isChecked).putBoolean("voice_feedback", feedback.isChecked)
                .putInt("default_duration", listOf(30, 60, 120)[duration.selectedItemPosition]).putInt("reminder_minutes", listOf(-1, 10, 30)[reminder.selectedItemPosition])
                .putString("voice_language", listOf("en-IN", "hi-IN", "mr-IN")[language.selectedItemPosition]).apply()
            showStatus("Settings saved.")
        }.show()
    }

    private fun settings() = getSharedPreferences("settings", MODE_PRIVATE)
    private fun showStatus(message: String) { status.text = message }
    private fun showVoice(message: String) { voiceStatus.text = message; status.text = message }
    private fun voiceFailure(message: String) { showVoice(message); speak("Shailesh, $message") }
    private fun speak(message: String) { if (settings().getBoolean("voice_feedback", true) && ttsReady) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "bat") }
    private fun formatWhen(event: CampusEvent): String {
        if (event.allDay) {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val start = date.format(Date(event.startMillis)); val end = event.endMillis?.let { date.format(Date(it - 1)) }
            return if (end == null || end == start) "$start · All day" else "$start – $end · All day"
        }
        val start = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(event.startMillis))
        val end = event.endMillis?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) }
        return if (end == null || end == start) start else "$start – $end"
    }
    private fun voiceError(error: Int) = when (error) { SpeechRecognizer.ERROR_NO_MATCH -> "I could not match that speech. Try again."; SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear speech. Try again."; SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a connection."; else -> "Voice input is unavailable (code $error)." }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_EDIT_CANDIDATE = "dev.campusevents.assistant.EDIT_CANDIDATE"
        private const val VOICE_START_PROMPT = "voice-start-prompt"
        private const val MENU_MANUAL = 1; private const val MENU_VOICE = 2; private const val MENU_WHATSAPP = 3; private const val MENU_SETTINGS = 4; private const val MENU_ABOUT = 5
    }
}
