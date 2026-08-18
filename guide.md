# Guess Card — Developer Setup Guide

## Table of Contents
1. [Firebase Project Setup](#1-firebase-project-setup)
2. [Firebase Authentication](#2-firebase-authentication)
3. [Firebase Realtime Database](#3-firebase-realtime-database)
4. [Android SHA-1 Fingerprint](#4-android-sha-1-fingerprint)
5. [Socket.IO Backend Setup](#5-socketio-backend-setup)
6. [Build & Run](#6-build--run)
7. [Gradle Properties](#7-gradle-properties)

---

## 1. Firebase Project Setup

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. Select the project: **guess-card-challange** (ID: `guess-card-challange`)
3. In **Project Settings > General**, verify your Android app:
   - Package name: `glab.guesscard`
   - App nickname: Guess Card
4. Download `google-services.json` → place it in: `android/app/google-services.json`

**IMPORTANT**: The `google-services.json` must have `package_name: "glab.guesscard"` (already correct in your file).

---

## 2. Firebase Authentication

### Enable Providers

In Firebase Console → **Authentication → Sign-in method**:

| Provider | Status | Notes |
|---|---|---|
| **Google** | Enable | Requires SHA-1 fingerprint (see section 4) |
| **Anonymous** | Enable | Used for Guest login |
| Email/Password | Optional | Not currently used |

### Google Sign-in Web Client ID

After enabling Google, copy the **Web client ID** from the OAuth credentials.
Add it to `android/app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Guess Card</string>
    <string name="default_web_client_id">YOUR_WEB_CLIENT_ID_HERE.apps.googleusercontent.com</string>
</resources>
```

---

## 3. Firebase Realtime Database

### Enable Database

1. Firebase Console → **Realtime Database → Create database**
2. Choose region: `us-central1` (or closest)
3. Start in **locked mode**, then apply rules below

### Security Rules

Paste these rules in **Realtime Database → Rules**:

```json
{
  "rules": {
    "users": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "leaderboard": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "rooms": {
      ".read": "auth != null",
      "$roomId": {
        ".write": "auth != null",
        "players": {
          "$uid": {
            ".write": "auth != null && auth.uid == $uid"
          }
        },
        "qaHistory": {
          ".write": "auth != null"
        }
      }
    },
    "roomCodes": {
      ".read": "auth != null",
      ".write": "auth != null"
    },
    "friends": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null && auth.uid == $uid"
      }
    },
    "invites": {
      ".read": "auth != null",
      "$uid": {
        ".write": "auth != null"
      }
    }
  }
}
```

### Database Structure

```
guess-card-challange-default-rtdb/
├── users/{uid}/ → displayName, avatarFileName, isAnonymous, level, xp, lastSeen
├── leaderboard/{uid}/ → displayName, score, mode, timestamp
├── rooms/{roomId}/ → roomId, name, code, mode, hostUid, hostName, hostAvatar, status, createdAt
│   ├── players/{uid}/ → uid, displayName, avatarFileName, joinedAt, ready
│   └── qaHistory/{pushId}/ → askerName, question, answererName, answer, timestamp
├── roomCodes/{code}/ → hostUid
├── friends/{uid}/{friendUid}/ → true
└── invites/{uid}/{inviteId}/ → roomId, code, senderName, timestamp
```

---

## 4. Android SHA-1 Fingerprint

Google Sign-in requires your app's SHA-1 fingerprint in Firebase.

```bash
cd android
./gradlew signingReport
```

Look for: `SHA1: AA:BB:CC:DD:...`

Register in Firebase Console → **Project Settings → Your apps → Add fingerprint**

**WARNING**: Without SHA-1, Google Sign-In fails with `ApiException: 10`

---

## 5. Socket.IO Backend Setup

Current server: `https://territory-wars-9o4f.onrender.com`

### Local Dev

```bash
cd server && npm install && npm run dev
```

### Key Socket Events

| Event | Direction | Payload |
|---|---|---|
| `join_room` | Client → Server | `{mode, code}` |
| `room_updated` | Server → Client | `RoomInfo` JSON |
| `ask_question` | Client → Server | `{question}` |
| `question_asked` | Server → Client | `{question, askerName}` |
| `answer_question` | Client → Server | `{answer: "YES"/"NO"}` |
| `answer_given` | Server → Client | `{question, answer, answererName}` |
| `submit_guess` | Client → Server | `{guess}` |
| `guess_result` | Server → Client | `{isCorrect, scoreAwarded, guessedBy}` |
| `game_end` | Server → Client | `{score, winner}` |

---

## 6. Build & Run

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### First Launch Checklist

- [ ] `google-services.json` in `android/app/`
- [ ] SHA-1 registered in Firebase console
- [ ] Firebase Auth: Google + Anonymous enabled
- [ ] Firebase RTDB: created with rules applied
- [ ] `strings.xml` has `default_web_client_id`

---

## 7. Gradle Properties

Create `android/gradle.properties`:

```properties
gc.socketUrl=https://territory-wars-9o4f.onrender.com
gc.socketUrlDebug=http://10.0.2.2:8080
gc.socketUrlRelease=https://territory-wars-9o4f.onrender.com
```

---

## 8. Professional Multiplayer Game Voice Architecture (How It Works)

### Why Professional Games Never Use Database Polling for Voice
| Approach | Protocol | Latency | Bad Network Performance | Bandwidth / User | Used By |
|---|---|---|---|---|---|
| **Firebase RTDB (Database)** | HTTP/TCP | 300ms - 1500ms ❌ | Disastrous (rate limits, lag spikes, DB throttling) ❌ | ~40 KB/s ❌ | Not suitable for audio |
| **Direct Socket.IO Binary Stream** | WebSocket | 40ms - 120ms ⚡ | Moderate (TCP retransmission on packet drops) ⚠️ | ~8 KB/s ⚡ | Indie web games |
| **WebRTC SFU (LiveKit / Opus)** | UDP / DTLS | 20ms - 50ms 🚀 | **Flawless** (Opus PLC, FEC, dynamic bitrate, jitter buffer) 🏆 | ~2 KB/s (95% compression) 🏆 | Discord, Among Us, Roblox, PUBG |

---

### In-App Voice Engine: `PartyVoiceCallManager`
Guess Card uses a single dedicated real-time audio pipeline:
1. **Audio Recording**: `AudioRecord` (16kHz Mono 16-bit PCM) on `MediaRecorder.AudioSource.VOICE_COMMUNICATION`.
2. **Audio Processing**:
   - Hardware **Acoustic Echo Cancellation (AEC)** prevents loudspeaker-to-microphone feedback loops.
   - Hardware **Automatic Gain Control (AGC)** normalizes voice loudness.
   - Hardware **Noise Suppression (NS)** eliminates background room hiss.
3. **Audio Routing**:
   - Audio is routed directly to the **bottom Media Loudspeaker** (`AudioAttributes.USAGE_MEDIA` + `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` + `STREAM_MUSIC` max volume), completely bypassing the phone's top earpiece.
4. **Microphone Sharing with Speech-to-Text**:
   - When tapping the Speech-to-Text mic (`🎤` → `🔴`), `pauseRecording()` yields the hardware microphone to Android's `SpeechRecognizer`.
   - Once speech is transcribed or cancelled, `resumeRecording()` seamlessly restores background party voice calling.

---

### Upgrading to LiveKit SFU (Step-by-Step)
For studio-grade voice over 2G/3G/poor mobile connections, you can plug in **LiveKit SFU** with 3 steps:

1. **Get Free LiveKit Cloud Credentials**:
   - Create a free project at [https://livekit.io/cloud](https://livekit.io/cloud) (Free tier: 50GB bandwidth/month = ~25,000 game minutes).
   - Obtain `LIVEKIT_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET`.

2. **Add Android LiveKit Dependency** (`android/app/build.gradle`):
   ```groovy
   implementation "io.livekit:livekit-android:2.6.0"
   ```

3. **Connect to Room Token**:
   - Server generates token with room name (`roomId`) and user ID (`userId`).
   - Android client connects:
   ```kotlin
   val room = LiveKit.create(context)
   room.connect("wss://your-project.livekit.cloud", token)
   room.localParticipant.setMicrophoneEnabled(true)
   ```

---

## 9. Socket.IO Real-Time Voice Signaling Reference

| Event | Direction | Payload | Description |
|---|---|---|---|
| `voice_join` | Client → Server | `roomId` | Join room voice channel |
| `voice_leave` | Client → Server | `roomId` | Leave room voice channel |
| `voice_audio_chunk` | Client → Server | `{data: base64Pcm, ts}` | Stream 30ms voice frame |
| `voice_audio_received` | Server → Client | `{sender, data, ts}` | Deliver peer voice frame |
| `voice_speaking` | Client → Server | `boolean` | Broadcast voice energy status |
| `voice_player_speaking` | Server → Client | `{userId, isSpeaking}` | Active speaker flicker indicator |
| `matchAbandoned` | Server → Client | `{roomId, message}` | Disconnect cleanup when 0-1 players left |

---

## 10. Voice-to-Text Feature

- Requires `RECORD_AUDIO` permission (auto-requested at runtime)
- Works with Android SpeechRecognizer (Google Speech Services)
- Only the **guesser/questioner** sees the mic button
- Tap mic (`🎤`) → turns red (`🔴`) → speak question → auto-sent to room
- Tap mic again (`🔴` → `🎤`) → immediately stops and cancels recording
- Answerers see **YES 👍 / NO 👎 / MAYBE 🤷** response buttons

---

## 11. App Architecture

```
glab.guesscard/
├── GuessCardApp.java              ← Application, DI container, Firebase init
├── firebase/FirebaseManager       ← Auth, RTDB rooms, profiles, match history
├── audio/
│   ├── GameAudio.java             ← SoundPool sound effects (cards, correct, timer)
│   └── PartyVoiceCallManager.java ← Real-time group voice call engine (AudioRecord/AudioTrack)
├── socket/GameSocketClient.java   ← Socket.IO multiplayer client & voice events
├── activities/
│   ├── SplashActivity             ← Auth gate check
│   ├── AuthActivity               ← Google + Anonymous login
│   ├── MainActivity               ← Home tab host & active match auto-rejoin
│   ├── LobbyActivity              ← Room presence, dynamic rounds, invite dialogs
│   ├── GameActivity               ← Match shell activity
│   └── WinnerActivity             ← RecyclerView leaderboard, bonus points & stats save
├── fragments/
│   ├── HomeFragment               ← Live rooms list, host re-enter, quick play
│   └── GameFragment               ← 20 Questions game, voice chat, mic flicker, card views
├── views/
│   ├── GameCardView.java          ← 3D Flip Card (no unwanted flips during Q&A)
│   ├── TimerView.java             ← Dynamic 60s circular timer
│   └── QuestionBubbleView.java    ← Chat speech bubble with YES/NO/MAYBE badges
├── game/GuessTheCardEngine        ← Offline smart AI question answering
└── utils/
    ├── VoiceRecognitionHelper.java ← Android SpeechRecognizer wrapper
    ├── AvatarManager.java          ← Local & remote avatar loading
    └── HapticsHelper.java          ← Tactile haptic feedback
```

*Package: glab.guesscard | Firebase: guess-card-challange*

