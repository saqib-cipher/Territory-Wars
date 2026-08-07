'use strict';

const { query } = require('../database');

const FRIEND_SELECT = `
  SELECT u.id AS "userId", u.username, u.avatar_id AS "avatarId",
         u.level, u.trophies, f.status,
         (f.user_id = $1) AS "isOutgoing"
  FROM friends f
  JOIN users u ON u.id = CASE
    WHEN f.user_id = $1 THEN f.friend_id
    ELSE f.user_id
  END
`;

/** GET /v1/friends */
async function getFriends(req, res, next) {
  try {
    const { rows } = await query(
      `${FRIEND_SELECT} WHERE f.user_id = $1 OR f.friend_id = $1`,
      [req.user.id]
    );
    res.json(rows);
  } catch (err) {
    next(err);
  }
}

/** POST /v1/friends/request { username } */
async function sendRequest(req, res, next) {
  try {
    const { username } = req.body || {};
    const target = await query(`SELECT id FROM users WHERE username = $1`, [username]);
    if (target.rows.length === 0) {
      return res.status(404).json({ code: 404, message: 'User not found' });
    }
    const targetId = target.rows[0].id;
    if (targetId === req.user.id) {
      return res.status(400).json({ code: 400, message: 'Cannot add yourself' });
    }

    const { rowCount } = await query(
      `INSERT INTO friends (user_id, friend_id, status)
       VALUES ($1, $2, 'pending')
       ON CONFLICT (user_id, friend_id) DO NOTHING`,
      [req.user.id, targetId]
    );
    if (rowCount === 0) {
      return res.status(409).json({ code: 409, message: 'Request already sent' });
    }
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

/** POST /v1/friends/accept { friendId } */
async function acceptRequest(req, res, next) {
  try {
    const { friendId } = req.body || {};
    const { rowCount } = await query(
      `UPDATE friends SET status = 'accepted'
       WHERE user_id = $2 AND friend_id = $1 AND status = 'pending'`,
      [friendId, req.user.id]
    );
    if (rowCount === 0) {
      return res.status(404).json({ code: 404, message: 'Pending request not found' });
    }
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

/** DELETE /v1/friends/:userId */
async function removeFriend(req, res, next) {
  try {
    const other = req.params.userId;
    const { rowCount } = await query(
      `DELETE FROM friends
       WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)`,
      [req.user.id, other]
    );
    if (rowCount === 0) {
      return res.status(404).json({ code: 404, message: 'Not friends' });
    }
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

module.exports = { getFriends, sendRequest, acceptRequest, removeFriend };