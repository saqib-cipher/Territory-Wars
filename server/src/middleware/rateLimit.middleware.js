'use strict';

const rateLimit = require('express-rate-limit');

/** Broad per-IP cap for all API routes. */
const apiLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 600,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { code: 429, message: 'Too many requests, slow down' },
});

/** Stricter cap for guest/Google login to slow credential stuffing. */
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 20,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { code: 429, message: 'Too many login attempts, try again later' },
});

module.exports = { apiLimiter, authLimiter };