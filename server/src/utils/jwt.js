'use strict';

const jwt = require('jsonwebtoken');
const config = require('../config');

const EXPIRES_IN = '30d';

/** Issues a signed token with the user id in the standard `sub` claim. */
function signToken(user) {
  return jwt.sign({ sub: user.id }, config.jwtSecret, {
    expiresIn: EXPIRES_IN,
    issuer: 'territory-wars',
  });
}

function verifyToken(token) {
  return jwt.verify(token, config.jwtSecret, { issuer: 'territory-wars' });
}

module.exports = { signToken, verifyToken, EXPIRES_IN };