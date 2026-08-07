'use strict';

/**
 * Idempotent schema bootstrap. Run with `npm run db:migrate`.
 */
const { pool } = require('./index');

const statements = [
  `
  CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT NOT NULL UNIQUE,
    avatar_id     TEXT NOT NULL DEFAULT 'default',
    email         TEXT,
    password_hash TEXT,
    firebase_uid  TEXT,
    is_guest      BOOLEAN NOT NULL DEFAULT false,
    coins         BIGINT NOT NULL DEFAULT 0,
    gems          BIGINT NOT NULL DEFAULT 0,
    level         INT NOT NULL DEFAULT 1,
    xp            INT NOT NULL DEFAULT 0,
    trophies      INT NOT NULL DEFAULT 0,
    is_premium    BOOLEAN NOT NULL DEFAULT false,
    premium_until TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  `,
  `CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);`,
  `CREATE INDEX IF NOT EXISTS idx_users_trophies ON users (trophies DESC);`,

  `
  CREATE TABLE IF NOT EXISTS matches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id        TEXT,
    mode           TEXT NOT NULL,
    map_id         TEXT NOT NULL,
    winner_id      UUID REFERENCES users (id),
    duration_ms    BIGINT NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at       TIMESTAMPTZ
  );
  `,
`
  CREATE TABLE IF NOT EXISTS match_participants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    match_id    UUID REFERENCES matches(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
    score       INT NOT NULL DEFAULT 0,
    tiles_captured INT NOT NULL DEFAULT 0,
    xp_earned   INT NOT NULL DEFAULT 0,
    coins_earned BIGINT NOT NULL DEFAULT 0
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS friends (
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    friend_id  UUID REFERENCES users(id) ON DELETE CASCADE,
    status     TEXT NOT NULL DEFAULT 'pending', -- pending | accepted
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS inventory (
    user_id    UUID REFERENCES users(id) ON DELETE CASCADE,
    item_id    TEXT NOT NULL,
    equipped   BOOLEAN NOT NULL DEFAULT false,
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS purchases (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE CASCADE,
    product_id    TEXT NOT NULL,
    purchase_token TEXT UNIQUE NOT NULL,
    package_name  TEXT NOT NULL,
    acknowledged  BOOLEAN NOT NULL DEFAULT false,
    consumed      BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS daily_rewards (
    user_id    UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    streak     INT NOT NULL DEFAULT 1,
    last_claim TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS achievements (
    id          TEXT NOT NULL,
    title       TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    icon        TEXT,
    target      INT NOT NULL DEFAULT 1,
    reward_coins BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
  );
  `,
  `
  CREATE TABLE IF NOT EXISTS user_achievements (
    user_id   UUID REFERENCES users(id) ON DELETE CASCADE,
    ach_id    TEXT REFERENCES achievements(id) ON DELETE CASCADE,
    progress  INT NOT NULL DEFAULT 0,
    unlocked_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, ach_id)
  );
  `,
];

async function migrate() {
  console.log('Applying schema…');
  for (const sql of statements) {
    await pool.query(sql);
  }
  await pool.end();
  console.log('Schema ready.');
}

migrate().catch((err) => {
  console.error('Migration failed:', err);
  process.exit(1);
});