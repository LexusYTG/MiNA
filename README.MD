# MiNA

**A voice assistant for Android that turns short spoken commands into actions.**

MiNA registers itself as your phone's assistant. Long-press Home (or use your device's assistant gesture), say something like *"pon alarma a las 7"* or *"what's the weather in Madrid"*, and MiNA answers out loud and, when needed, hands off to the right system app.

Current version: **1.0** · Package: `com.Lexus2026.MiNA` · License: **GPL-3.0-or-later**

---

## What MiNA is

MiNA is a lightweight, rule-based assistant. It does not use a large language model or a cloud AI backend. Instead it:

1. **Listens** with Android's built-in speech recognizer.
2. **Understands** the command with a set of phrase patterns plus a small NLP engine (keywords, stemming, synonyms, fuzzy matching).
3. **Acts** by calling Android APIs directly (flashlight, volume, notes…) or by launching the right app or web page (alarms, maps, search…).
4. **Answers** out loud with Android's text-to-speech and shows the conversation in a bottom-sheet overlay.

MiNA has a cat personality: it will happily tell you it is a cat, ask for tuna, and meow on request.

---

## What MiNA does

### Time, weather and calendar
- Current time and date.
- Current weather and tomorrow's forecast, for your location or for any city (data from [Open-Meteo](https://open-meteo.com/)).
- Alarms, timers, stopwatch, and "show my alarms" (via the system clock app).
- Create a calendar event or open the calendar (via the system calendar app).

### Control your phone
- Flashlight on/off/toggle.
- Volume up/down/mute/max for media, notifications, ringer and alarms.
- Screen brightness up/down/max/auto.
- Media keys: pause, resume, next and previous track.
- Shortcuts to the airplane-mode, NFC and location settings.
- Launch any installed app by name, or open a URL.

### Calls, camera and maps
- Open the dialer with a number, open contacts and recent calls, dial the emergency number.
- Open the camera (rear or selfie), video recorder or gallery.
- Show your location, show a map of a place, start navigation to a destination.
- Find nearby gas stations, pharmacies, restaurants and hospitals.

### Search
Opens the result in your browser or the matching app:
Google, Google Images, YouTube, Wikipedia, Play Store, Amazon, Mercado Libre, Steam, Netflix, Spotify, Reddit, X/Twitter, TikTok, flights, package tracking, and Google Translate. Recipe, meaning, synonym and "capital of…" questions are sent to a web search.

### Notes and lists
- Voice notes: add, list, read note *N*, clear all.
- Two lists: **shopping list** and **to-do list** (add, show, clear).
- Everything is stored locally on the device.

### Everyday utilities
- Calculator (one operation at a time: `+ - × ÷ ^ %`, square root), BMI, average, min/max, sum and product of a list of numbers.
- Unit conversions: km/h ↔ mph, oz ↔ g, in ↔ cm, L ↔ gal, yd ↔ m.
- Dice, coin flip, random number, "choose between A, B or C".
- Random password (4–64 characters), UUID and color.
- Text tools: upper/lower case, reverse, Morse code, Base64, word count.
- Decimal → binary/hexadecimal and prime-number check.

### Device information
Battery level and charging state, device model, Android version, free storage, free RAM, local IP address, carrier, uptime, screen resolution, Wi-Fi network name, kernel version and SIM info.

### Conversation
- Small talk: greetings, thanks, jokes, fun facts, compliments, "who are you?".
- **Multi-turn clarification:** when MiNA is only partly sure what you meant, it asks *"Did you mean…?"* and understands *yes / no / cancel*, or picks one of the options you are offered ("the first one", or a word from the option). The question expires after 90 seconds.

### Languages
The interface, voice recognition, speech output and command patterns all follow the selected language. Spanish is built into the app. Other languages (the app is set up for English, Portuguese, French, German, Italian, Japanese, Chinese and Russian) come as language packs downloaded from this repository's `lang.json`; the packs actually available are whatever that file contains. Packs are cached on the device and refreshed every 6 hours. You can switch language at any time from the main screen.

---

## What MiNA does NOT do

MiNA is intentionally small and predictable. It is **not**:

- **A chatbot or generative AI.** It only understands the commands listed above. Anything else gets a polite "I don't know that". It cannot hold an open-ended conversation, answer general-knowledge questions itself, write text or reason about your request.
- **Always listening.** There is no wake word ("Hey MiNA") and no background service. You start it with the assistant gesture, the microphone button, or the *Test assistant* button.
- **A hands-free controller for everything.** In several cases MiNA hands you over to the system app and you finish the action there:
  - **Alarms and timers** open the clock app's confirmation screen; they are not set silently.
  - **Calls** open the dialer with the number filled in; MiNA never places a call on its own, and it cannot call a contact by name.
  - **Calendar events** open a blank new-event screen; MiNA does not fill in the title or time.
  - **Searches, translations and "capital of…"-type questions** open the browser or app; MiNA does not read the results back.
  - **Maps and navigation** need a maps app installed.
- **A toggle for radios and system modes.** It cannot switch airplane mode, Wi-Fi, Bluetooth, mobile data, NFC or GPS on or off. For airplane mode, NFC and GPS it only opens the relevant settings page.
- **A messaging assistant.** No SMS, email or chat messages.
- **A music player.** It sends media keys to whatever player is active, but it cannot choose or search for songs, and "what's playing?" is not implemented (it always answers that nothing is playing).
- **A full calculator.** Expressions are limited to a single operation between two numbers; no parentheses or chained calculations.
- **A weather station.** Weather covers *now* and *tomorrow* only, no hourly or multi-day forecasts, and needs an internet connection.
- **Cloud-synced.** Notes and lists live only on your device; there is no account, backup or sync.
- **Able to delete a single note or list item by voice.** You can clear a whole list or all notes.
- **Guaranteed offline.** Speech recognition uses Android's system recognizer, which may need a connection on some devices, and language packs other than Spanish are downloaded on first use.

---

## Getting started

1. Install MiNA and open it.
2. Grant the permissions when asked (see below).
3. Tap **Set as assistant** (on older Android versions, **Open assistant settings** and pick MiNA as your digital assistant).
4. Long-press Home (or use your assistant gesture) and say a command. You can also tap **Test assistant** or the glowing microphone orb.
5. If MiNA shows **Configure system voice**, your device is missing text-to-speech data for the selected language. Install it from there.

### Example commands

MiNA's built-in phrases are in Spanish; other languages use their own language pack.

| You say | What happens |
|---|---|
| "¿Qué tiempo hace en Madrid?" | Speaks the current weather for Madrid |
| "Pon alarma a las 7:30" | Opens the clock app with a 7:30 alarm ready |
| "Pon temporizador de 10 minutos" | Opens the clock app with a 10-minute timer |
| "Enciende la linterna" | Turns the flashlight on |
| "Sube el volumen" | Raises the media volume |
| "Anota comprar pan" | Saves a note |
| "Agrega leche a la lista de la compra" | Adds an item to the shopping list |
| "Busca en YouTube gatos graciosos" | Opens YouTube search results |
| "Navega a la estación de tren" | Starts navigation in your maps app |
| "Abre WhatsApp" | Launches the app |
| "Cuánto es 15 por 4" | Answers with the result |
| "Elige entre pizza, sushi o tacos" | Picks one at random |
| "¿Cuánta batería tengo?" | Speaks your battery level |

---

## Permissions

| Permission | Why |
|---|---|
| Microphone | Speech recognition |
| Location (approximate / precise) | "Weather here" and "tomorrow's forecast here". If you deny it, MiNA asks for a city instead |
| Modify system settings | Only for the brightness commands; MiNA opens the settings screen so you can grant it |
| Internet | Weather, city lookup and language packs |

---

## Privacy and network use

- No account, no sign-in, no MiNA backend.
- MiNA connects to two servers: **Open-Meteo** (weather and city lookup) and **raw.githubusercontent.com** (language packs). As a fallback for city lookup it also uses Android's built-in geocoder, which is provided by your device.
- Your notes, lists and language settings are stored in the app's private storage on your device.
- Speech recognition and text-to-speech are performed by the recognizer and voice engine installed on your device, so their privacy behaviour depends on the provider your phone uses.

---

## How it works

```
 microphone ──▶ VoiceEngine (Android SpeechRecognizer)
                    │  text
                    ▼
              CommandRouter
   1. pending question?  ──▶ resolve yes / no / cancel / choice
   2. phrase patterns    ──▶ Lang.matchIntent()      (exact command, with parameters)
   3. NLP concepts       ──▶ NlpEngine.analyze()     (confident match → run it)
   4. medium confidence  ──▶ ask "Did you mean…?"    (remember it for 90 s)
   5. otherwise          ──▶ "I don't know that"
                    │  Response (text + optional Intent)
                    ▼
      AssistantActivity ──▶ TtsEngine (speaks) + launches the Intent
```

### Source files

| File | Role |
|---|---|
| `MainActivity` | Home screen: assistant status, set-as-default button, language picker, permissions |
| `AssistantActivity` | The assistant overlay: microphone orb, live transcript, conversation log |
| `CommandRouter` | Routes each command to its handler; holds multi-turn state; notes, lists, device info, media, torch, volume, brightness, calculator, etc. |
| `NlpEngine` (`NplEngine.java`) | Concept scoring with exact/phrase/n-gram/stem/synonym/prefix/fuzzy matching and confidence thresholds |
| `Lang` | All text, phrase patterns, keywords and synonyms; language packs (download, cache, switch) |
| `VoiceEngine` | Wrapper around Android `SpeechRecognizer` with friendly error messages |
| `TtsEngine` | Wrapper around Android `TextToSpeech`, with locale fallback and voice-data install shortcut |
| `WeatherEngine` | Open-Meteo weather and geocoding, plus last-known-location lookup |
| `Ui` | Small programmatic UI toolkit (dark theme, cards, buttons) |

MiNA has no third-party libraries: it uses only Android SDK APIs, is written in plain Java, and builds its UI in code rather than XML layouts.

### Adding or translating commands

Phrases, keywords, synonyms and responses are all data in `Lang` / `lang.json`, addressed by numeric ID, so a new language needs no code changes. Only the Spanish fallback is hard-coded in the app.

---

## License

Copyright (C) 2026 LexusYTG

MiNA is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License** as published by the Free Software Foundation, either **version 3** of the License, or (at your option) any later version.

It is distributed in the hope that it will be useful, but **without any warranty**; without even the implied warranty of merchantability or fitness for a particular purpose. See <https://www.gnu.org/licenses/> for details.
