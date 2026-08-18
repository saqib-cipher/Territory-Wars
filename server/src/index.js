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
      origin: '*',
      methods: ['GET', 'POST'],
      credentials: true,
    },
    allowEIO3: true,
    pingTimeout: 60000,
    pingInterval: 25000,
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

    // Keep Render free-tier awake by self-pinging every 14 minutes.
    // Render sleeps instances after 15 min of inactivity — this prevents it.
    const selfUrl = process.env.RENDER_EXTERNAL_URL || `http://localhost:${port}`;
    if (process.env.RENDER_EXTERNAL_URL) {
      setInterval(() => {
        http.get(`${selfUrl}/health`, (res) => {
          console.log(`[keepalive] ping → ${res.statusCode}`);
        }).on('error', (e) => {
          console.warn('[keepalive] ping failed:', e.message);
        });
      }, 14 * 60 * 1000); // every 14 minutes
      console.log(`[keepalive] self-ping enabled → ${selfUrl}/health`);
    }
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