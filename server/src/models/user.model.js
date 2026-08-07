'use strict';

const { v4: uuidv4 } = require('uuid');
const bcrypt = require('bcryptjs');
const { query } = require('../database');
const { signToken } = require('../utils/jwt');

/**
 * User data-access + auth flow helpers.
 */
const userService = {
  /** Finds a user by Firebase UID, if linked. */
  async findByFirebaseUid(uid) {
    const { rows } = await query('SELECT * FROM users WHERE firebase_uid = $1', [uid]);
    return rows[0] || null;
  },

  async findByUsername(username) {
    const { rows } = await query('SELECT * FROM users WHERE username = $1', [username]);
    return rows[0] || null;
  },

  async findById(id) {
    const { rows } = await query('SELECT * FROM users WHERE id = $1', [id]);
    return rows[0] || null;
  },

  /** Creates an anonymous guest account. */
  async createGuest() {
    const username = `Guest${Math.floor(1000 + Math.random() * 9000)}`;
    const { rows } = await query(
      `INSERT INTO users (id, username, is_guest)
       VALUES ($1, $2, true)
       ON CONFLICT (username) DO NOTHING
       RETURNING *`,
      [uuidv4(), username]
    );
    return rows[0];
  },

  /** Creates (or links) a Google-backed account. */
  async findOrCreateGoogle({ uid, email, name }) {
    const existing = await this.findByFirebaseUid(uid);
    if (existing) return existing;

    const username = (name || 'Player').replace(/\s+/g, '_').slice(0, 20);
    const { rows } = await query(
      `INSERT INTO users (id, username, email, firebase_uid, is_guest)
       VALUES ($1, $2, $3, $4, false)
       ON CONFLICT (email) DO UPDATE SET firebase_uid = EXCLUDED.firebase_uid
       RETURNING *`,
      [uuidv4(), username, email, uid]
    );
    return rows[0];
  },

  /** Issues a JWT for the user row. */
  tokenFor(user) {
    return signToken(user);
  },
};

module.exports = userService;