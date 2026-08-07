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
- **Data**: `Repository` wraps `ApiService` (Retrofit) + `GameSocketClient` (Socket.IO, JWT handshake) + Room (`RecentMatchDao`). ViewModels expose observable state consumed by fragments.
- **Game engine** (`game.*`): `GameMap` (64×48 grid, `TILE_SIZE=48`), `Player`/`Bot` entities with tile capture, `Game` simulation engine stepping at fixed dt, `GameLoop` thread, `GameView` (Canvas, camera pan/zoom, particles, powerup sprites), `Collision` circle checks, `PowerupSpawner` placing timed powerups.
- **Monetization**: `BillingManager` (Play Billing 7, products `coins_1000`, `gems_100`, `remove_ads`, `premium_monthly`, `premium_yearly`) verifies server-side via `/purchase/verify` before acknowledging; `AdManager` (AdMob test IDs; banner/interstitial/rewarded/app-open; suppressed when ads removed).
- **Auth**: guest (`POST /auth/login/guest`) or Google (Firebase ID token → `POST /auth/login/google`); JWT cached in encrypted prefs.
- **Settings**: theme (light/dark/system with dynamic color via `ThemeManager`), sound/music toggles, haptics; enforced through `ThemeManager` and `GameAudio`.

## Server side

- **Layers**: `routes` → `controllers` → `services` → `db`. Middleware handles JWT (`auth.middleware`), rate limits (per-IP API + stricter auth limit), JSON errors.
- **Schema** (`database/migrate.js`): `users` (coins, gems, trophies, xp, level, is_premium, premium_until, firebase_uid), `matches` + `match_participants` (xp/coin ledger, PK `(match_id, user_id)`), `friends` (pending/accepted), `inventory`, `purchases` (token-deduped), `daily_rewards` (streak), `achievements` + `user_achievements`.
- **Realtime**: `RoomManager` keeps rooms in memory; `socket/index.js` owns the event surface. Rooms are destroyed when the last player disconnects. `runMatchLoop` broadcasts `scoreUpdate` every 2 s and finalizes with `gameEnd`.
- **Anti-cheat anchor**: capture scoring lives server-side (`captureTile` → per-user score/tile counters); client positions are display-only echoes.

## Contracts / gotchas

- Debug endpoints `http://10.0.2.2:8080` for the emulator loopback.
- `users.premium_until` semantics: `is_premium` is the flag, `premium_until` the optional expiry.
- Input lengths are clamped server-side (username ≤ 16, chat ≤ 256 chars).
- Socket room events are plain payloads; no rooms/namespaces beyond per-game room ids. client sockets must rejoin after reconnect.

## Known placeholders

- Ad unit IDs are Google test IDs (`ca-app-pub-3940256099942544/…`).
- Purchase verification is dev-stub until `GOOGLE_SERVICE_ACCOUNT_FILE` is configured.
- Cloud save endpoints are thin passthroughs.