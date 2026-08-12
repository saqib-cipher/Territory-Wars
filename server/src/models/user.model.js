'use strict';

const { query } = require('../database');

/** Maps a database row to the camelCase shape controllers and the client expect. */
function toPublic(row) {
  return {
    id: row.id,
    username: row.username,
    avatarId: row.avatar_id || 'default',
    avatarFileName: row.avatar_id || 'avatar_01.png',
    isGuest: row.is_guest,
    coins: row.coins,
    gems: row.gems,
    xp: row.xp,
    level: row.level,
    trophies: row.trophies,
    isPremium: row.is_premium,
  };
}

const mask = `id, username, avatar_id, is_guest, coins, gems, xp, level, trophies, is_premium, premium_until`;

/** Returns full profile including avatar for sync */
async function getFullProfile(id) {
  const { rows } = await query(
    `SELECT ${mask} FROM users WHERE id = $1`,
    [id]
  );
  return rows[0] ? toPublic(rows[0]) : null;
}

async function findById(id) {
  const { rows } = await query(`SELECT ${mask} FROM users WHERE id = $1`, [id]);
  return rows[0] ? toPublic(rows[0]) : null;
}

async function findByUsername(username) {
  const { rows } = await query(`SELECT ${mask} FROM users WHERE username = $1`, [username]);
  return rows[0] ? toPublic(rows[0]) : null;
}

async function findByFirebaseUid(uid) {
  const { rows } = await query(`SELECT ${mask} FROM users WHERE firebase_uid = $1`, [uid]);
  return rows[0] ? toPublic(rows[0]) : null;
}

async function createGuest(deviceId) {
  const username = `Guest${(deviceId || '')
    .replace(/[^a-zA-Z0-9]/g, '')
    .slice(0, 8) || Math.floor(Math.random() * 10000)}`;
  const { rows } = await query(
    `INSERT INTO users (username, is_guest)
     VALUES ($1, true)
     ON CONFLICT (username) DO NOTHING
     RETURNING ${mask}`,
    [username]
  );
  if (rows.length > 0) {
    return toPublic(rows[0]);
  }
  // Extremely unlikely collision; retry once with a fresh suffix.
  const { rows: rows2 } = await query(
    `INSERT INTO users (username, is_guest)
     VALUES (concat($1, '_', floor(random()*9000+1000)::int), true)
     RETURNING ${mask}`,
    [username]
  );
  return toPublic(rows2[0]);
}

async function createGoogleUser({ firebaseUid, username, avatarId }) {
  const { rows } = await query(
    `INSERT INTO users (username, avatar_id, firebase_uid, is_guest)
     VALUES ($1, $2, $3, false)
     RETURNING ${mask}`,
    [username, avatarId || 'default', firebaseUid]
  );
  return toPublic(rows[0]);
}

module.exports = { findById, findByUsername, findByFirebaseUid, createGuest, createGoogleUser, getFullProfile, toPublic };