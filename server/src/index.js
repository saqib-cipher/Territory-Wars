'use strict';

const http = require('http');
const { Server } = require('socket.io');
const { createApp } = require('./app');
const { configureSocket } = require('./socket');
const redis = require('./redis/client');
const config = require('./config');

async function main() {
  const app = createApp();
  const server = http.createServer(app);

  const io = new Server(server, {
    cors: {
      origin: config.isProd() ? false : '*',
      methods: ['GET', 'POST'],
    },
    maxHttpBufferSize: 1e6,
  });

  configureSocket(io);

  const port = process.env.PORT || config.port || 8080;

  server.listen(port, '0.0.0.0', () => {
    console.log(`[territory-wars] API + sockets listening on 0.0.0.0:${port}`);
    // Connect to Redis in background without blocking HTTP server boot
    redis.connect().catch((err) => {
      console.warn('[redis] background connection warning:', err.message);
    });
  });

  // graceful shutdown
  const shutdown = () => {
    console.log('Shutting down…');
    server.close(() => {
      if (redis.client && redis.client.isOpen) {
        redis.client.quit().then(() => process.exit(0)).catch(() => process.exit(0));
      } else {
        process.exit(0);
      }
    });
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((err) => {
  console.error('Failed to boot server:', err);
  process.exit(1);
});