'use strict';

const { query } = require('../database');

/** GET /v1/profile */
async function getProfile(req, res, next) {
  try {
    const { rows } = await query(
      `SELECT id, username, avatar_id AS "avatarId", is_guest AS "isGuest",
              coins, gems, xp, level, trophies,
              is_premium AS "isPremium",
              (premium_until > now()) AS "premiumActive",
              (SELECT count(*) FROM matches m
                 JOIN match_participants mp ON mp.match_id = m.id
                WHERE mp.user_id = u.id AND m.winner_id = u.id) AS "matchesWon",
              (SELECT count(*) FROM match_participants mp WHERE mp.user_id = u.id) AS "matchesPlayed"
       FROM users u
       WHERE u.id = $1`,
      [req.user.id]
    );
    res.json(rows[0] || null);
  } catch (err) {
    next(err);
  }
}

/** POST /v1/profile { username?, avatarId? } */
async function updateProfile(req, res, next) {
  try {
    const { username, avatarId } = req.body || {};

    if (username !== undefined && (typeof username !== 'string' || username.length < 3 || username.length > 16)) {
      return res.status(400).json({ code: 400, message: 'Username must be 3-16 characters' });
    }

    const updates = [];
    const params = [req.user.id];
    if (username !== undefined) {
      updates.push(`username = $${params.length + 1}`);
      params.push(username);
    }
    if (avatarId !== undefined) {
      updates.push(`avatar_id = $${params.length + 1}`);
      params.push(avatarId);
    }
    if (updates.length === 0) {
      return res.status(400).json({ code: 400, message: 'Nothing to update' });
    }

    const { rows } = await query(
      `UPDATE users SET ${updates.join(', ')}, updated_at = now()
       WHERE id = $1
       RETURNING id, username, avatar_id AS "avatarId", coins, gems, xp, level, trophies`,
      params
    );
    res.json(rows[0]);
  } catch (err) {
    if (err.code === '23505') {
      return res.status(409).json({ code: 409, message: 'Username already taken' });
    }
    next(err);
  }
}

module.exports = { getProfile, updateProfile };