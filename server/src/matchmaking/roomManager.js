'use strict';

const crypto = require('crypto');

const GAME_DURATION_MS = 120_000;
const MAX_PLAYERS = 50;

/**
 * In-memory room manager. Rooms are authoritative buckets that coordinate
 * Socket.IO payload fan-out. Match balance/fairness logic lives in the
 * matchmaking service; this class only models state transitions.
 *
 * For horizontal scale, promote these maps to Redis (Hash + pub/sub); the
 * interface is kept Redis-shaped so migrating later is mechanical.
 */
class RoomManager {
  constructor() {
    this.rooms = new Map(); // roomId -> room
  }

  /** Generates a short human-joinable room code. */
  static generateCode() {
    const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += alphabet[crypto.randomInt(alphabet.length)];
    }
    return code;
  }

  createRoom(host, mode, mapId) {
    const roomId = crypto.randomUUID();
    const code = RoomManager.generateCode();
    const room = {
      roomId,
      code,
      mode,
      mapId: mapId || 'classic_plains',
      maxPlayers: Math.min(MAX_PLAYERS, 50),
      hostId: host.id,
      players: new Map([[host.id, { ...host, isReady: false, isHost: true }]]),
      startedAt: null,
      countdown: 0,
    };
    this.rooms.set(roomId, room);
    return room;
  }

  findById(roomId) {
    return this.rooms.get(roomId) || null;
  }

  findByCode(code) {
    const upper = (code || '').trim().toUpperCase();
    for (const room of this.rooms.values()) {
      if (room.code === upper) return room;
    }
    return null;
  }

  join(room, user) {
    if (room.players.has(user.id)) return false;
    if (room.players.size >= room.maxPlayers) return false;
    if (room.startedAt) return false;
    room.players.set(user.id, { ...user, isReady: false, isHost: false });
    return true;
  }

  leave(room, userId) {
    room.players.delete(userId);
    // host left → promote the first remaining player
    if (room.players.size > 0) {
      const next = room.players.keys().next().value;
      room.players.get(next).isHost = true;
      room.hostId = next;
    }
    return room.players.size;
  }

  setReady(room, userId, ready) {
    const p = room.players.get(userId);
    if (!p) return false;
    p.isReady = ready;
    return true;
  }

  /** All players ready → begin. Returns the ready room or null. */
  maybeStart(room) {
    if (room.startedAt) return null;
    const everyoneReady =
      room.players.size >= 2 &&
      [...room.players.values()].every((p) => p.isReady);
    if (!everyoneReady) return null;
    room.startedAt = Date.now();
    return room;
  }

  toPublic(room, viewerId) {
    return {
      roomId: room.roomId,
      code: room.code,
      mode: room.mode,
      mapId: room.mapId,
      maxPlayers: room.maxPlayers,
      hostId: room.hostId,
      countdownSeconds: room.startedAt
        ? Math.max(0, Math.round((room.startedAt - Date.now()) / 1000))
        : 0,
      players: [...room.players.values()].map((p) => ({
        userId: p.id,
        username: p.username,
        avatarId: p.avatarId || 'default',
        isReady: p.isReady,
        isHost: p.isHost,
      })),
    };
  }

  destroy(room) {
    this.rooms.delete(room.roomId);
  }
}

module.exports = { RoomManager, GAME_DURATION_MS };