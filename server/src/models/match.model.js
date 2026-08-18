'use strict';

/**
 * Match model stub.
 * Long-term match records are stored in Firebase on the Android client.
 * This module exists to satisfy the require() in socket/index.js and provides
 * a no-op saveMatch() for future server-side persistence use.
 */

async function saveMatch(matchData) {
  // No-op: match history is persisted by the Android client via Firebase.
  // Add PostgreSQL INSERT here if server-side match records are needed later.
  return null;
}

async function findByRoomId(roomId) {
  return null;
}

module.exports = { saveMatch, findByRoomId };
