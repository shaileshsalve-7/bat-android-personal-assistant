# Bat

Bat is a privacy-first personal campus-event assistant for one Android phone and one laptop. It watches **only the Android notification previews that WhatsApp marks as group conversations**, identifies likely college events, adds them to the device calendar, highlights technical/career priorities, and mirrors extracted details to a small token-protected laptop dashboard.

It does **not** use a private WhatsApp API, read chat history, access messages directly, send messages, bypass device security, or capture direct/personal chats automatically.

## What this first version does

- Android notification listener, enabled only through Android's explicit Notification Access setting.
- Fail-closed group filtering: automatic processing runs only when WhatsApp/Android sets `EXTRA_IS_GROUP_CONVERSATION=true`. Direct chats, saved contacts, and notifications without a reliable group flag are ignored.
- Transparent rules detect event-like messages and extract title, date, time, location, link, organizer, and technical/career priority. High-priority terms include workshops, hackathons, coding, AI, placements, internships, and talks.
- WhatsApp group detection stores a private candidate and posts an Android review notification with **Add**, **Edit**, and **Ignore** actions. It never writes a Calendar event automatically. Add uses the same Calendar path as the in-app review and only reports success after the provider returns a real event ID.
- The compact Bat home screen is input → review → confirm. Its overflow menu holds manual input, voice input, WhatsApp detection access, settings, and privacy information; settings contain detection, default duration, reminder, voice language (English India/Hindi/Marathi), and voice-feedback controls.
- Explicit date ranges such as `7th through 13th August` and durations such as `for five days` are saved as one spanning calendar event. A multi-day all-day event ends at the exclusive midnight after its final stated date, which is the Calendar provider's required format.
- High-priority events produce an Android high-importance notification.
- Manual intake: paste text or use Android Share → **Bat** to explicitly process a personal message/event. It is never collected automatically.
- Voice intake: tap **Speak an event command** and say `Bat, add AI workshop on 18 October at 3 PM in Innovation Hall`. Bat only proceeds when it hears a specific event name plus an explicit date and time; it never reads the screen or treats generic “add” speech as an event. It recognizes common harmless transcription variants such as `that add` / `bat and` and `p.m.`, then shows title, date/time, and location for confirmation before adding anything.
- The redesigned home screen keeps the voice action and manual intake up front, shows the three most recent saved events locally, and places laptop pairing in the secondary Setup area.
- Optional on-device Android TextToSpeech feedback greets Shailesh, can read a count-only summary of today's saved events, and asks for spoken confirmation before a voice-created event is written. The top-right **Voice on/off** control mutes it; Bat never reads raw notification previews aloud.
- Laptop sync carries only structured event fields—not the source notification preview—and is protected by a user-set bearer token.

## Android setup

1. Open this folder in Android Studio and install the `app` module on Android 8+.
2. Open **Bat**, choose **Grant notification access**, then enable Bat in Android Settings. This permission exposes notifications to Bat; leave it off if you do not want automatic group capture.
3. Choose **Allow calendar entries** and grant Calendar and notification permissions. Allow the microphone only if you want in-app voice commands.
4. Start the dashboard below, then enter its LAN URL and the same pairing token in Bat. Saving pairing also uploads already-saved events.

### Important WhatsApp group limitation

Group-vs-direct information is supplied by the notification posted by WhatsApp, not by an unsupported chat API. Some Android/WhatsApp versions may omit the group flag for a notification. Bat deliberately ignores those ambiguous notifications rather than risk collecting a personal chat. Use manual paste/share intake for any missed event.

### Voice limitation and shortcut

Voice is on-demand while Bat is open; it is not an always-listening wake word. Android restricts reliable background hotword launching for ordinary apps, so Bat makes no claim that saying “Bat” will launch it in the background. A supported launcher shortcut, **Bat: add an event**, is included for long-press/launcher use. Device assistants may be able to open Bat using their own supported app-launch command, but that behavior varies by device and is not required for the event flow.

## Laptop dashboard

The dashboard has no external dependencies beyond Node.js 18+.

1. In PowerShell, choose a private token (at least 16 characters) and start it:

   ```powershell
   cd dashboard
   $env:EVENT_ASSISTANT_TOKEN = "replace-with-a-long-random-secret"
   npm start
   ```

2. Find the laptop's LAN IPv4 address (for example `192.168.1.10`). In Bat, set the server URL to `http://192.168.1.10:8787` and use the same token.
3. On the laptop, open `http://localhost:8787/?token=replace-with-a-long-random-secret`. The token is saved in that browser tab's session storage and removed from the visible URL. You can enable browser desktop alerts from the dashboard.

Both devices must be on the same trusted network (or connected by a trusted VPN). The starter permits HTTP for uncomplicated LAN setup, so do not expose its port to the public internet. For use beyond a trusted network, place the dashboard behind HTTPS and a firewall/VPN; retain a strong, unique pairing token.

## Data and synchronization

Phone events are stored in app-private SharedPreferences. The dashboard stores local events in `dashboard/data/events.json`. Each phone event is deduplicated by a stable event hash. Android pushes structured fields immediately after detection; the dashboard polls every 15 seconds. There is no cloud account or third-party data service in this starter.

The Android calendar description keeps the source preview locally so the user can verify it; that preview is intentionally omitted from the sync payload. Delete an event from the phone calendar or dashboard data as appropriate if you want to remove it—this first version does not provide two-way deletion.

## Verification

The project includes focused parser tests in `app/src/test/java`. Run `./gradlew test` or Android Studio's test task after Gradle/Android SDK setup. A dashboard smoke test can be done by starting it, opening the tokenized page, and submitting an authorized `POST /api/events`; the page should show the saved event.

## Project layout

- `app/` — native Kotlin Android app (permissions, listener, parser, calendar writer, voice/manual intake, sync client)
- `dashboard/` — small Node.js server and browser dashboard
- `app/src/main/res/drawable/bat_emblem.png` — the selected original bat-inspired Bat emblem; it is not an official Batman mark or asset.
