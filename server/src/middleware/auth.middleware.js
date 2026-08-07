'use strict';

const jwt = require('../utils/jwt');
const { query } = require('../database');
const { JsonWebTokenError, TokenExpiredError } = require('jsonwebtoken');

/**
 * Express middleware: validates the `Authorization: Bearer <jwt>` header and
 * attaches `req.user` (fresh row from PostgreSQL) for downstream handlers.
 */
async function authRequired(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;

  if (!token) {
    return res.status(401).json({ code: 401, message: 'Missing token' });
  }

  try {
    const payload = jwt.verify(token);
    const { rows } = await query(
      'SELECT id, username, avatar_id, is_guest, coins, gems, level, xp, trophies, is_premium, premium_until FROM users WHERE id = $1',
      [payload.sub]
    );
    if (rows.length === 0) {
      return res.status(401).json({ code: 401, message: 'Unknown user' });
    }
    req.user = rows[0];
    next();
  } catch (err) {
    if (err instanceof TokenExpiredError) {
      return res.status(401).json({ code: 401, message: 'Token expired' });
    }
    if (err instanceof JsonWebTokenError) {
      return res.status(401).json({ code: 401, message: 'Invalid token' });
    }
    next(err);
  }
}

module.exports = { authRequired };