'use strict';

/**
 * Database schema migration. Run with: npm run db:migrate
 *
 * Idempotent: every statement is CREATE ... IF NOT EXISTS, so it is safe
 * to run repeatedly against the same database.
 */

const { query } = require('./index');
const config = require('../config');

const ACHIEVEMENTS = [
  { id: 'first_win', name: 'First Blood', description: 'Win your first match' },
  { id: 'capture_100', name: 'Tile Taker', description: 'Capture 100 tiles in a match' },
  { id: 'win_10', name: 'Warlord', description: 'Win 10 matches' },
  { id: 'level_5', name: 'Rising Star', description: 'Reach level 5' },
  { id: 'friend_3', name: 'Socialite', description: 'Add 3 friends' },
  { id: 'streak_3', name: 'On Fire', description: 'Claim 3 daily rewards in a row' },
];

async function migrate() {
  const statements = [
    `CREATE EXTENSION IF NOT EXISTS pgcrypto`,

    `CREATE TABLE IF NOT EXISTS users (
       id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
       username      text NOT NULL UNIQUE,
       avatar_id     text NOT NULL DEFAULT 'default',
       firebase_uid  text UNIQUE,
       is_guest      boolean NOT NULL DEFAULT false,
       coins         integer NOT NULL DEFAULT 0,
       gems          integer NOT NULL DEFAULT 0,
       xp            integer NOT NULL DEFAULT 0,
       level         integer NOT NULL DEFAULT 1,
       trophies      integer NOT NULL DEFAULT 0,
       is_premium    boolean NOT NULL DEFAULT false,
       premium_until timestamptz,
       created_at    timestamptz NOT NULL DEFAULT now(),
       updated_at    timestamptz NOT NULL DEFAULT now()
     )`,

    `CREATE TABLE IF NOT EXISTS matches (
       id           uuid PRIMARY KEY,
       mode         text NOT NULL DEFAULT 'duel',
       winner_id    uuid REFERENCES users(id),
       started_at   timestamptz NOT NULL DEFAULT now(),
       ended_at     timestamptz NOT NULL DEFAULT now(),
       duration_ms  integer NOT NULL DEFAULT 120000
     )`,

    `CREATE TABLE IF NOT EXISTS match_participants (
       match_id       uuid NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
       user_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       score          integer NOT NULL DEFAULT 0,
       tiles_captured integer NOT NULL DEFAULT 0,
       xp_earned      integer NOT NULL DEFAULT 0,
       coins_earned   integer NOT NULL DEFAULT 0,
       PRIMARY KEY (match_id, user_id)
     )`,
    `CREATE INDEX IF NOT EXISTS idx_participants_user ON match_participants(user_id)`,

    `CREATE TABLE IF NOT EXISTS friends (
       user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       friend_id  uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       status     text NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'accepted')),
       created_at timestamptz NOT NULL DEFAULT now(),
       PRIMARY KEY (user_id, friend_id)
     )`,
    `CREATE INDEX IF NOT EXISTS idx_friends_friend ON friends(friend_id)`,

    `CREATE TABLE IF NOT EXISTS inventory (
       user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       item_id    text NOT NULL,
       equipped   boolean NOT NULL DEFAULT false,
       created_at timestamptz NOT NULL DEFAULT now(),
       PRIMARY KEY (user_id, item_id)
     )`,

    `CREATE TABLE IF NOT EXISTS purchases (
       id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
       user_id        uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       product_id     text NOT NULL,
       purchase_token text NOT NULL UNIQUE,
       package_name   text NOT NULL,
       acknowledged   boolean NOT NULL DEFAULT false,
       created_at     timestamptz NOT NULL DEFAULT now()
     )`,

    `CREATE TABLE IF NOT EXISTS daily_rewards (
       user_id    uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
       streak     integer NOT NULL DEFAULT 1,
       last_claim timestamptz NOT NULL DEFAULT now()
     )`,

    `CREATE TABLE IF NOT EXISTS achievements (
       id          text PRIMARY KEY,
       name        text NOT NULL,
       description text NOT NULL DEFAULT ''
     )`,

    `CREATE TABLE IF NOT EXISTS user_achievements (
       user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       achievement text NOT NULL REFERENCES achievements(id) ON DELETE CASCADE,
       unlocked_at timestamptz NOT NULL DEFAULT now(),
       PRIMARY KEY (user_id, achievement)
     )`,

    `CREATE TABLE IF NOT EXISTS custom_modes (
       id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
       owner_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
       name           text NOT NULL,
       description    text DEFAULT '',
       question_limit integer NOT NULL DEFAULT 10,
       round_time     integer NOT NULL DEFAULT 60,
       max_players    integer NOT NULL DEFAULT 5,
       created_at     timestamptz NOT NULL DEFAULT now()
     )`,

    `CREATE TABLE IF NOT EXISTS custom_cards (
       id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
       mode_id     uuid NOT NULL REFERENCES custom_modes(id) ON DELETE CASCADE,
       answer      text NOT NULL,
       category    text DEFAULT 'Custom',
       image_url   text DEFAULT '',
       created_at  timestamptz NOT NULL DEFAULT now()
     )`,
  ];

  for (const sql of statements) {
    await query(sql);
  }
  console.log('[migrate] schema is up to date');
}

async function seed() {
  for (const a of ACHIEVEMENTS) {
    await query(
      `INSERT INTO achievements (id, name, description)
       VALUES ($1, $2, $3) ON CONFLICT (id) DO NOTHING`,
      [a.id, a.name, a.description]
    );
  }
  console.log('[seed] achievements ready');
}

async function run() {
  if (!config.databaseUrl) {
    console.error('[migrate] DATABASE_URL is not set');
    process.exit(1);
  }
  await migrate();
  if (process.argv.includes('--seed')) {
    await seed();
  }
  process.exit(0);
}

run().catch((err) => {
  console.error('[migrate] failed:', err);
  process.exit(1);
});