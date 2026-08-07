'use strict';

const jwt = require('../utils/jwt');
const userService = require('../models/user.model');

/**
 * Requires a valid `Authorization: Bearer <token>` header and attaches the
 * fresh user row to `req.user`.
 */
async function authRequired(req, res, next) {
  const header = req.headers.authorization || '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : null;

  if (!token) {
    return res.status(401).json({ code: 401, message: 'Missing token' });
  }

  try {
    const payload = jwt.verifyToken(token);
    const user = await userService.findById(payload.sub);
    if (!user) {
      return res.status(401).json({ code: 401, message: 'Unknown user' });
    }
    req.user = user;
    next();
  } catch (_) {
    return res.status(401).json({ code: 401, message: 'Invalid or expired token' });
  }
}

module.exports = { authRequired };