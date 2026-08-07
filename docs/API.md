# API reference

Base URL: `http://localhost:8080/v1` (emulator: `http://10.0.2.2:8080/v1`)

All endpoints below `/auth/*` require `Authorization: Bearer <jwt>`.

## Auth

| Method | Path | Body | Notes |
| --- | --- | --- | --- |
| POST | `/auth/login/guest` | `{ deviceId }` | Creates or returns a `Guest####` account |
| POST | `/auth/login/google` | `{ idToken }` | Firebase ID token → verify → find-or-create by `firebase_uid` |

Both return `{ token, user }`.

## Profile

| Method | Path | Body |
| --- | --- | --- |
| GET | `/profile` | — |
| POST | `/profile` | `{ username?, avatarId?, isPremium }` |

## Leaderboard

| Method | Path | Query |
| --- | --- | --- |
| GET | `/leaderboard` | `scope=global\|friends\|weekly\|monthly`, `limit` (≤200) |

Returns ranked rows: `rank, userId, username, avatarId, trophies, matchesWon, isSelf, isFriend`.

## Friends

| Method | Path | Body |
| --- | --- | --- |
| GET | `/friends` | — |
| POST | `/friends/request` | `{ username }` |
| POST | `/friends/accept` | `{ friendId }` |
| DELETE | `/friends/:userId` | — |

## Shop / Inventory

| Method | Path | Body |
| --- | --- | --- |
| GET | `/shop` | — (catalog + owned/equipped flags) |
| GET | `/inventory` | — |
| POST | `/inventory/equip` | `{ itemId }` |
| POST | `/shop/buy/coins` | `{ itemId }` (coin-only items) |

## Matches

| Method | Path | Body |
| --- | --- | --- |
| POST | `/match/report` | `{ matchId?, won, rank, score, tilesCaptured }` |
| GET | `/match/history` | `limit` (≤100) |

`report` also grants XP/coin rewards via `services/match.service.js` (idempotent for the same `matchId`).

## Daily reward

| Method | Path | Body |
| --- | --- | --- |
| POST | `/daily-reward/claim` | — |

Streak rewards: `[100, 150, 200, 250, 300, 400, 500]` coins.

## Purchases

| Method | Path | Body |
| --- | --- | --- |
| POST | `/purchase/verify` | `{ productId, purchaseToken, packageName?, subscription? }` |

Server-verified with the Play Developer API (dev-stub mode without a service account). Tokens are deduped; entitlements grant coins/gems/premium days/ads-removed. Always acknowledge the purchase client-side only after this call succeeds.

## Cloud save

| Method | Path | Notes |
| --- | --- | --- |
| GET | `/cloud-save` | Returns profile + premium status |
| POST | `/cloud-save` | Passthrough stub |

## Socket.IO

Connect with `auth: { token: <jwt> }`.

Client → server:

- `createRoom(mode, mapId)`
- `joinRoomByCode(code)` / `joinRoom(roomId)` / `leaveRoom()`
- `setReady(boolean)`
- `chatMessage(roomId, text)`
- `playerMove({ x, y, vx?, vy? })`
- `captureTile({ tx, ty })`
- `powerupCollected({ powerupId })`

Server → client:

- `authError` / `roomError`
- `roomJoined`, `roomUpdate`
- `gameStart({ mapId, mode, durationMillis, startedAt })` (fires when all players are ready; map is the same for everyone)
- `scoreUpdate({ roomId, elapsed, remainingMs, leaderboard[] })` every 2 s
- `gameEnd({ roomId, endedAt, standings[] })`
- `playerMove`, `captureTile`, `powerupCollected`, `chatMessage` (echoed to the room)

Matches last `GAME_DURATION_MS = 120_000` ms; capture scoring is server-authoritative (`score += 10` per tile).
