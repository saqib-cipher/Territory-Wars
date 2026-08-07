'use strict';

const { query } = require('../database');

const PROFILE_FIELDS = `
  id, username, avatar_id, level, xp,
  coins, gems, trophies, is_premium, is_guest,
  (SELECT count(*) FROM matches m
     JOIN match_participants mp ON mp.match_id = m.id
     WHERE mp.user_id = users.id) AS matches_played,
  (SELECT count(*) FROM matches m
     JOIN match_participants mp ON mp.match_id = m.id
     WHERE mp.user_id = users.id AND m.winner_id = users.id) AS matches_won,
  (SELECT coalesce(sum(tiles_captured), 0) FROM match_participants WHERE user_id = users.id) AS tiles_captured,
  (SELECT coalesce(sum(score), 0) FROM match_participants WHERE user_id = users.id) AS total_score
`;

/** GET /v1/profile */
async function getProfile(req, res, next) {
  try {
    const { rows } = await query(
      `SELECT ${PROFILE_FIELDS} FROM users WHERE id = $1`,
      [req.user.id]
    );
    if (rows.length === 0) {
      return res.status(404).json({ code: 404, message: 'Profile not found' });
    }
    res.json(rows[0]);
  } catch (err) {
    next(err);
  }
}

/** POST /v1/profile { username?, avatarId? } */
async function updateProfile(req, res, next) {
  try {
    const { username, avatarId } = req.body || {};
    if (username) {
      await query('UPDATE users SET username = $1 WHERE id = $2', [username, req.user.id]);
    }
    if (avatarId) {
      await query('UPDATE users SET avatar_id = $1 WHERE id = $2', [avatarId, req.user.id]);
    }
    return getProfile(req, res, next);
  } catch (err) {
    next(err);
  }
}

module.exports = { getProfile, updateProfile };