'use strict';

const userService = require('../models/user.model');
const googleAuth = require('../services/googleAuth.service');

/** POST /v1/auth/login/guest */
async function loginGuest(req, res, next) {
  try {
    const user = await userService.createGuest();
    res.json({
      token: userService.tokenFor(user),
      userId: user.id,
      username: user.username,
      isGuest: true,
    });
  } catch (err) {
    next(err);
  }
}

/** POST /v1/auth/login/google { idToken, username } */
async function loginGoogle(req, res, next) {
  try {
    const { idToken, username } = req.body || {};
    if (!idToken) {
      return res.status(400).json({ code: 400, message: 'idToken required' });
    }

    const payload = await googleAuth.verifyIdToken(idToken);
    const user = await userService.findOrCreateGoogle({
      uid: payload.sub,
      email: payload.email,
      name: username || payload.name,
    });

    res.json({
      token: userService.tokenFor(user),
      userId: user.id,
      username: user.username,
      isGuest: false,
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { loginGuest, loginGoogle };