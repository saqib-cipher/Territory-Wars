'use strict';

const { query } = require('../database');

/**
 * GET /v1/leaderboard?scope=global|friends|weekly|monthly&limit=50
 */
async function getLeaderboard(req, res, next) {
  try {
    const scope = req.query.scope || 'global';
    const limit = Math.min(parseInt(req.query.limit, 10) || 50, 200);
    const me = req.user.id;

    let where = '';
    let params = [limit, me];

    if (scope === 'friends') {
      where = `WHERE u.id IN (
        SELECT f.friend_id FROM friends f WHERE f.user_id = $2 AND f.status = 'accepted'
        UNION
        SELECT f.user_id FROM friends f WHERE f.friend_id = $2 AND f.status = 'accepted'
      ) OR u.id = $2`;
    } else if (scope === 'weekly' || scope === 'monthly') {
      const days = scope === 'weekly' ? 7 : 30;
      where = `WHERE u.id IN (
        SELECT mp.user_id FROM match_participants mp
        JOIN matches m ON m.id = mp.match_id
        WHERE m.ended_at > now() - interval '${days} days'
      ) OR u.id = $2`;
    }

    const { rows } = await query(
      `SELECT
         u.id AS "userId", u.username, u.avatar_id AS "avatarId", u.trophies,
         (SELECT count(*) FROM matches m
            JOIN match_participants mp ON mp.match_id = m.id
            WHERE mp.user_id = u.id AND m.winner_id = u.id) AS "matchesWon",
         (u.id = $2) AS "isSelf",
         EXISTS (SELECT 1 FROM friends f
            WHERE f.status = 'accepted'
              AND ((f.user_id = $2 AND f.friend_id = u.id)
                OR (f.friend_id = $2 AND f.user_id = u.id))) AS "isFriend"
       FROM users u
       ${where}
       ORDER BY u.trophies DESC
       LIMIT $1`,
      params
    );

    const ranked = rows.map((row, i) => ({ rank: i + 1, ...row }));
    res.json(ranked);
  } catch (err) {
    next(err);
  }
}

module.exports = { getLeaderboard };