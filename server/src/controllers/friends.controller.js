'use strict';

const { query } = require('../database');

/** GET /v1/friends */
async function getFriends(req, res, next) {
  try {
    const me = req.user.id;
    const { rows } = await query(
      `SELECT
         u.id AS "userId", u.username, u.avatar_id AS "avatarId", u.level,
         (f.user_id = $1) AS "isRequest",
         CASE WHEN f.status = 'pending' THEN 'pending' ELSE 'offline' END AS status
       FROM friends f
       JOIN users u ON u.id = CASE WHEN f.user_id = $1 THEN f.friend_id ELSE f.user_id END
       WHERE (f.user_id = $1 OR f.friend_id = $1)`,
      [me]
    );
    res.json(rows);
  } catch (err) {
    next(err);
  }
}

/** POST /v1/friends/request { target } */
async function sendRequest(req, res, next) {
  try {
    const target = (req.body || {}).target;
    if (!target) {
      return res.status(400).json({ code: 400, message: 'target required' });
    }
    const { rows } = await query(
      'SELECT id FROM users WHERE username = $1 OR id::text = $1',
      [target]
    );
    if (rows.length === 0) {
      return res.status(404).json({ code: 404, message: 'User not found' });
    }
    const friendId = rows[0].id;
    if (friendId === req.user.id) {
      return res.status(400).json({ code: 400, message: 'You cannot add yourself' });
    }

    await query(
      `INSERT INTO friends (user_id, friend_id, status) VALUES ($1, $2, 'pending')
       ON CONFLICT DO NOTHING`,
      [req.user.id, friendId]
    );
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

/** POST /v1/friends/accept { userId } */
async function acceptRequest(req, res, next) {
  try {
    const { userId } = req.body || {};
    if (!userId) {
      return res.status(400).json({ code: 400, message: 'userId required' });
    }
    const { rowCount } = await query(
      `UPDATE friends SET status = 'accepted'
       WHERE friend_id = $1 AND user_id = $2 AND status = 'pending'`,
      [req.user.id, userId]
    );
    if (rowCount === 0) {
      return res.status(404).json({ code: 404, message: 'No pending request' });
    }
    // mirror row so friendship is bidirectional
    await query(
      `INSERT INTO friends (user_id, friend_id, status)
       VALUES ($1, $2, 'accepted') ON CONFLICT DO NOTHING`,
      [req.user.id, userId]
    );
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

/** DELETE /v1/friends/:userId */
async function removeFriend(req, res, next) {
  try {
    const { userId } = req.params;
    await query(
      `DELETE FROM friends
       WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)`,
      [req.user.id, userId]
    );
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

module.exports = { getFriends, sendRequest, acceptRequest, removeFriend };