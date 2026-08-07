'use strict';

require('dotenv').config();

/** Runtime configuration loaded from environment variables. */
const config = {
  env: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT, 10) || 8080,
  databaseUrl:
    process.env.DATABASE_URL ||
    'postgres://postgres:postgres@localhost:5432/territory_wars',
  redisUrl: process.env.REDIS_URL || 'redis://localhost:6379',
  jwtSecret: process.env.JWT_SECRET || 'dev-insecure-secret-change-me',

  google: {
    clientId: process.env.GOOGLE_CLIENT_ID || '',
  },

  play: {
    packageName: process.env.PLAY_PACKAGE_NAME || 'com.territorywars',
    serviceAccountFile: process.env.GOOGLE_SERVICE_ACCOUNT_FILE || '',
  },

  isProd() {
    return this.env === 'production';
  },

  get baseUrl() {
    if (this.isProd()) return `https://api.territorywars.example/v1`;
    return `http://localhost:${this.port}`;
  },
};

module.exports = config;