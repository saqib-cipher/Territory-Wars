'use strict';

const { OAuth2Client } = require('google-auth-library');
const config = require('../config');

const client = new OAuth2Client(config.google.clientId);

/**
 * Verifies a Firebase ID token. In development without a configured
 * GOOGLE_CLIENT_ID the payload is decoded without signature verification
 * so local flows (emulators) work end-to-end.
 *
 * Returns { uid, email, name, picture } or null on failure.
 */
async function verifyIdToken(idToken) {
  if (!idToken || typeof idToken !== 'string') return null;

  if (!config.google.clientId) {
    try {
      const payload = decodeIdToken(idToken);
      if (!payload || !payload.sub) return null;
      return {
        uid: payload.sub,
        email: payload.email || '',
        name: payload.name || '',
        picture: payload.picture || '',
      };
    } catch (_) {
      return null;
    }
  }

  try {
    const ticket = await client.verifyIdToken({
      idToken,
      audience: config.google.clientId,
    });
    const payload = ticket.getPayload();
    if (!payload || !payload.sub) return null;
    return {
      uid: payload.sub,
      email: payload.email || '',
      name: payload.name || '',
      picture: payload.picture || '',
    };
  } catch (_) {
    return null;
  }
}

/** Decodes (without verifying) a JWT for local dev flows. */
function decodeIdToken(token) {
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Malformed token');
  return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
}

module.exports = { verifyIdToken };