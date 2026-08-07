# Territory Wars

Realtime multiplayer territory capture game for Android (Java, MVVM, Material 3 Expressive) with a Node.js/PostgreSQL/Redis backend.

Players race to claim and hold tiles on a shared map in timed 2-minute matches, using powerups, cosmetics, friends, leaderboards, and premium purchases.

## Repository layout

```
android/   Android Studio project (Java, minSdk 24, targetSdk 35)
server/    Node.js API + Socket.IO realtime server (Express 4, pg, redis)
```

## Android app

Requirements: Android Studio (Ladybug or newer), JDK 17, Android SDK 35.

1. Open `android/` in Android Studio and let Gradle sync.
2. Create a `local.properties` with `sdk.dir` (Android Studio does this automatically).
3. Supply API keys before running against live services:

| Key | Where | Purpose |
| --- | --- | --- |
| AdMob App ID | `AndroidManifest.xml` → `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID">` | Ads (test IDs are already configured) |
| Firebase `google-services.json` | `android/app/` | Google sign-in (`default_web_client_id` in `strings.xml`) |
| Play Billing product IDs | Google Play Console + `BillingManager.java` product constants | IAP products `coins_1000`, `gems_100`, `remove_ads`, `premium_monthly`, `premium_yearly` |

Run config: debug builds point at `http://10.0.2.2:8080/v1` (API) and `ws://10.0.2.2:8080` (sockets) so the local server works from an emulator. Override via gradle properties `tw.apiBaseUrlDebug`, `tw.socketUrlDebug`, and the release variants.

## Server

Requirements: Node.js 18+, PostgreSQL 14+, Redis 6+.

```bash
cd server
cp .env.example .env      # set DATABASE_URL, REDIS_URL, JWT_SECRET
npm install
npm run db:migrate        # create schema (users, matches, friends, inventory, purchases, …)
npm run dev               # API + Socket.IO on :8080
```

- `npm start` runs the production entrypoint.
- Purchase verification (`POST /v1/purchase/verify`) talks to the Play Developer API using the service account at `GOOGLE_SERVICE_ACCOUNT_FILE`; when that path is absent it runs in dev-stub mode (accepts any token), so client flows work end-to-end locally.
- Sockets require `token` in the handshake auth (the same JWT as the REST API).
- Rooms are in-memory (`server/src/matchmaking/roomManager.js`); swap the maps for Redis when scaling horizontally (interface is kept Redis-shaped).

## Architecture notes

- Server-authoritative scoring: the socket layer records `captureTile` events and broadcasts periodic `scoreUpdate` payloads; `gameEnd` carries the final standings. The client interpolates movement for responsiveness.
- Single-activity navigation shell (`MainActivity` switches between bottom bar / nav rail), splash → auth → lobby → game flow with Room persistence for recent matches.
- Manual DI via `di/GameContainer`; no Hilt/Dagger to keep the build light.

See `docs/API.md` and `docs/ARCHITECTURE.md` for endpoint and data-flow details.
