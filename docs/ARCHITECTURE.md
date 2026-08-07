# Architecture

## Overview

```
┌─────────────────────────────┐        ┌─────────────────────────────┐
│  Android app (Java/MVVM)    │  REST  │  Node.js server             │
│  ─────────────────────────  │ ◄────► │  ─────────────────────────  │
│  activities/fragments       │  JWT   │  express routes/controllers │
│  viewmodels → Repository    │        │  pg (PostgreSQL)            │
│  GameContainer (manual DI)  │        │  redis (cache/future rooms) │
│                             │  WS    │                             │
│  GameView (Canvas engine)   │ ◄────► │  Socket.IO                 │
│  GameSocketClient           │        │  RoomManager (in-memory)   │
└─────────────────────────────┘        └─────────────────────────────┘
```

## Android side

- **Navigation shell**: `MainActivity` hosts the bottom bar (phones) / nav rail (tablets, `values-sw600dp`) and swaps the five main fragments via `nav_graph.xml`.
- **Flow**: `SplashActivity` → bootstrap (`GameContainer`, auth restore) → `MainActivity`; Play → `LobbyActivity` (create/join rooms, chat, ready state) → `GameActivity` hosting `GameFragment`; offline practice runs the same `GameFragment` against bots.
- **Data**: `Repository` wraps `ApiService` (Retrofit) + `GameSocketClient` (Socket.IO, JWT handshake) + Room (`RecentMatchDao`). ViewModels expose `LiveData`/`MutableStateFlow`-style observable fields consumed by fragments; UI writes are funneled through `UiState` in the `viewmodel` package.
- **Game engine** (`game.*`): `GameMap` (64×48 grid, `TILE_SIZE=48`), `Player`/`Bot` entities with tile capture, `Game` simulation engine stepping at fixed dt, `GameLoop` thread, `GameView` (Canvas, camera pan/zoom, particles, powerup sprites), `Collision` circle checks, `PowerupSpawner` placing timed powerups.
- **Monetization**: `BillingManager` (Play Billing 7, products `coins_1000`, `gems_100`, `remove_ads`, `premium_monthly`, `premium_yearly`) verifies server-side via `/purchase/verify` before acknowledging; `AdManager` (AdMob test IDs; banner/interstitial/rewarded/app-open; suppressed when ads removed).
- **Auth**: guest (`POST /auth/login/guest`) or Google (Firebase ID token → `POST /auth/login/google`); JWT cached in encrypted prefs (`PreferenceManager`).
- **Settings**: theme (light/dark/system, dynamic color), sound/music toggles, haptics; enforced through `ThemeManager`, `GameAudio`, and `Haptic` in `animations`.

## Server side

- **Layers**: `routes` → `controllers` → `services` → `database`/`redis`. Middleware handles JWT (`auth.middleware`), rate limits (per-IP API + stricter auth limit), and JSON errors.
- **Schema** (`database/migrate.js`): `users` (coins, gems, trophies, xp, level, is_premium, premium_until, firebase_uid), `matches` + `match_participants` (xp/coin ledger), `friends` (pending/accepted), `inventory`, `purchases` (token-deduped), `daily_rewards` (streak), `achievements` + `user_achievements`.
- **Realtime**: `RoomManager` keeps rooms in memory; `socket/index.js` owns the event surface. When the last player of a room disconnects the room is destroyed. `runMatchLoop` broadcasts `scoreUpdate` every 2 s and finalizes with `gameEnd`.
- **Anti-cheat anchor**: capture scoring lives server-side (`captureTile` → per-user score/tile counters); the client's per-frame positions are treated as display-only echoes.

## Environment / config

- Debug endpoints `10.0.2.2:8080` for emulator loopback; override with gradle properties (`tw.apiBaseUrlDebug`, `tw.socketUrlDebug`, release equivalents).
- Server reads `.env` (see `.env.example`): `PORT`, `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `GOOGLE_SERVICE_ACCOUNT_FILE`, `PLAY_PACKAGE_NAME`.
- Redis is a hard boot dependency; PostgreSQL holds all persistent state.

## Known placeholders

- Ad unit IDs are Google test IDs (`ca-app-pub-3940256099942544/…`).
- `GameAudio` loads are commented; drop OGG assets under `android/app/src/main/res/raw/` to enable SFX.
- Play Developer API verification is a stub until a service account JSON is provided.
- Cloud save endpoints are thin passthroughs; extend `CloudSaveService` + a save table for full sync.
