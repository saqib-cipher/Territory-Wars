'use strict';

const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const { apiLimiter, authLimiter } = require('./middleware/rateLimit.middleware');
const { errorHandler } = require('./middleware/error.middleware');
const routes = require('./routes');
const config = require('./config');

/**
 * Express app factory. Mounts security middleware, the v1 API, and the
 * JSON error handler.
 */
function createApp() {
  const app = express();

  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: '256kb' }));

  app.get('/', (req, res) => res.json({ name: 'Guess Card API', status: 'ok', timestamp: new Date() }));
  app.get('/health', (req, res) => res.json({ status: 'ok', timestamp: new Date() }));

  app.use(apiLimiter);
  app.use('/v1/auth', authLimiter);

  app.use('/v1', routes);

  app.use((req, res) =>
    res.status(404).json({ code: 404, message: `No route for ${req.method} ${req.path}` }));

  app.use(errorHandler);

  return app;
}

module.exports = { createApp, config };