'use strict';

const dotenv = require('dotenv');
dotenv.config();

const config = {
  env: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT, 10) || 4000,
  baseUrl: process.env.BASE_URL || `http://localhost:${process.env.PORT || 4000}`,

  databaseUrl: process.env.DATABASE_URL,
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',

  jwt: {
    secret: process.env.JWT_SECRET || 'insecure-dev-secret',
    expiresIn: process.env.JWT_EXPIRES_IN || '30d',
  },

  google: {
    webClientId: process.env.GOOGLE_WEB_CLIENT_ID,
    serviceAccountFile: process.env.GOOGLE_SERVICE_ACCOUNT_FILE,
  },

  play: {
    packageName: process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.territorywars',
  },

  rateLimit: {
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS, 10) || 60000,
    max: parseInt(process.env.RATE_LIMIT_MAX, 10) || 120,
  },

  isProd: () => config.env === 'production',
};

module.exports = config;