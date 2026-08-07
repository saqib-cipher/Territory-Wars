'use strict';

const { createClient } = require('redis');
const config = require('../config');

const client = createClient({ url: config.redisUrl });

client.on('error', (err) => console.error('Redis error:', err.message));

/** Connects lazily; call at boot. */
async function connect() {
  if (!client.isOpen) {
    await client.connect();
  }
}

module.exports = { client, connect };