'use strict';

const { OAuth2Client } = require('google-auth-library');
const config = require('../config');

const client = new OAuth2Client(config.google.webClientId);

/**
 * Verifies a Firebase/Google ID token. Returns the decoded payload on
 * success or throws if the token is invalid.
 */
async function verifyIdToken(idToken) {
  const ticket = await client.verifyIdToken({
    idToken,
    audience: config.google.webClientId,
  });
  return ticket.getPayload();
}

module.exports = { verifyIdToken };