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
      "$uid": {
        ".read": "auth != null",
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
      "$roomId": {
        ".read": "auth != null",
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
    }
  }
}
```

### Database Structure

```
guess-card-challange-default-rtdb/
├── users/{uid}/
│   ├── displayName, email, isAnonymous, level, xp, lastSeen
├── leaderboard/{uid}/
│   ├── displayName, score, mode, timestamp
└── rooms/{roomId}/
    ├── players/{uid}/ → displayName, joinedAt, ready
    └── qaHistory/{pushId}/ → askerName, question, answererName, answer, timestamp
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

## Voice-to-Text Feature

- Requires `RECORD_AUDIO` permission (auto-requested at runtime)
- Works with Android SpeechRecognizer (Google Speech Services required)
- Only the **guesser/questioner** sees the mic button
- Tap mic → speak → text is auto-sent as question
- Answerers see **YES / NO only** — no MAYBE

---

## App Architecture

```
glab.guesscard/
├── GuessCardApp.java          ← Application, Firebase init
├── firebase/FirebaseManager   ← Auth + RTDB
├── activities/
│   ├── SplashActivity         ← Auth gate check
│   ├── AuthActivity           ← Google + Anonymous
│   ├── LobbyActivity          ← RTDB player presence
│   └── GameActivity           ← Hosts GameFragment
├── fragments/
│   ├── PlayFragment           ← Online/offline, auth-gated
│   └── GameFragment           ← Voice Q, YES/NO answers
├── game/GuessTheCardEngine    ← Offline AI smart answers
└── utils/VoiceRecognitionHelper ← Mic to text
```

*Package: glab.guesscard | Firebase: guess-card-challange*
