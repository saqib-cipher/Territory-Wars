'use strict';

const http = require('http');
const { Server } = require('socket.io');
const { createApp } = require('./app');
const { configureSocket } = require('./socket');
const redis = require('./redis/client');
const config = require('./config');

async function main() {
  await redis.connect();

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

  server.listen(config.port, () => {
    console.log(`[territory-wars] API + sockets on ${config.baseUrl}`);
  });

  // graceful shutdown
  const shutdown = () => {
    console.log('Shutting down\u2026');
    server.close(() => {
      redis.client.quit().then(() => process.exit(0));
    });
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((err) => {
  console.error('Failed to boot server:', err);
  process.exit(1);
});