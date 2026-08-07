'use strict';

const userService = require('../models/user.model');
const googleAuthService = require('../services/googleAuth.service');
const { signToken } = require('../utils/jwt');

/** POST /v1/auth/login/guest { deviceId } */
async function loginGuest(req, res, next) {
  try {
    const deviceId = req.body && req.body.deviceId;
    const user = await userService.createGuest(deviceId);
    const token = signToken(user);
    res.json({ token, user });
  } catch (err) {
    next(err);
  }
}

/** POST /v1/auth/login/google { idToken } */
async function loginGoogle(req, res, next) {
  try {
    const { idToken } = req.body || {};
    const profile = await googleAuthService.verifyIdToken(idToken);
    if (!profile) {
      return res.status(401).json({ code: 401, message: 'Invalid Google ID token' });
    }

    let user = await userService.findByFirebaseUid(profile.uid);
    if (!user) {
      // Prefer a stable display name, fall back to uid-derived username.
      const base = (profile.name || 'Player')
        .replace(/[^a-zA-Z0-9]/g, '')
        .slice(0, 16) || `Player${profile.uid.slice(0, 6)}`;
      let username = base;
      let taken = await userService.findByUsername(username);
      if (taken) {
        username = `${base}_${profile.uid.slice(0, 6)}`;
        taken = await userService.findByUsername(username);
      }
      if (taken) {
        username = `${base}_${Date.now().toString(36)}`;
      }
      user = await userService.createGoogleUser({
        firebaseUid: profile.uid,
        username,
        avatarId: profile.picture || 'default',
      });
    }

    const token = signToken(user);
    res.json({ token, user });
  } catch (err) {
    next(err);
  }
}

module.exports = { loginGuest, loginGoogle };