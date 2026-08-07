'use strict';

const jwt = require('jsonwebtoken');
const config = require('../config');

/** Signs a JWT for a user. */
function signToken(user) {
  return jwt.sign(
    { sub: user.id, username: user.username, guest: !!user.is_guest },
    config.jwt.secret,
    { expiresIn: config.jwt.expiresIn }
  );
}

/** Verifies and decodes a JWT; throws on failure. */
function verifyToken(token) {
  return jwt.verify(token, config.jwt.secret);
}

module.exports = { signToken, verifyToken };