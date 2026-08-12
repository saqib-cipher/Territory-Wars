'use strict';

const { createClient } = require('redis');
const config = require('../config');

const client = createClient({ url: config.redisUrl });

client.on('error', (err) => {
  console.warn('[redis] client warning:', err.message);
});

/** Idempotent connection with fallback for cloud deployment without Redis */
async function connect() {
  try {
    if (!client.isOpen) {
      await client.connect();
    }
    console.log('[redis] connected');
  } catch (err) {
    console.warn('[redis] could not connect to Redis, continuing in standalone mode:', err.message);
  }
}

module.exports = { client, connect };