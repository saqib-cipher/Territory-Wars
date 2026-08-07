'use strict';

const { createClient } = require('redis');
const config = require('../config');

const client = createClient({ url: config.redisUrl });

client.on('error', (err) => {
  console.error('Redis client error:', err);
});

/** Idempotent connection; boot blocks so Redis is a hard dependency. */
async function connect() {
  if (!client.isOpen) {
    await client.connect();
  }
  console.log('[redis] connected');
}

module.exports = { client, connect };