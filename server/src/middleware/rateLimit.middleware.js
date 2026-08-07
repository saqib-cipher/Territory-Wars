'use strict';

const rateLimit = require('express-rate-limit');
const config = require('../config');

/** Global API limiter (slash-protection). */
const apiLimiter = rateLimit({
  windowMs: config.rateLimit.windowMs,
  max: config.rateLimit.max,
  standardHeaders: true,
  legacyHeaders: false,
});

/** Stricter limiter for auth endpoints. */
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { code: 429, message: 'Too many attempts, slow down' },
});

module.exports = { apiLimiter, authLimiter };