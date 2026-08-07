'use strict';

const jwt = require('../utils/jwt');
const userService = require('../models/user.model');
const { RoomManager, GAME_DURATION_MS } = require('../matchmaking/roomManager');

/**
 * Configures Socket.IO with an authenticated handshake and the real-time room
 * + game event surface:
 *   connect, disconnect, joinRoom, leaveRoom, playerMove, captureTile,
 *   powerupCollected, chatMessage, gameStart, gameEnd, scoreUpdate.
 *
 * The server records capture events so scoring/final standings are
 * authoritative (anti-cheat anchor), while position echoes are latency-tolerant.
 */
function configureSocket(io) {
  const rooms = new RoomManager();
  const roomScores = new Map(); // roomId -> Map(userId -> {score,tiles})

  io.on('connection', (socket) => {
    handleConnection(io, socket, rooms, roomScores);
  });

  return io;
}

function handleConnection(io, socket, rooms, roomScores) {
  // ---- auth handshake ----
  authenticate(socket, (user) => {
    if (!user) return; // disconnected in authenticate

    // ---- room lifecycle ----
    socket.on('createRoom', (mode, mapId) => {
      const room = rooms.createRoom(user, mode, mapId);
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
    });

    socket.on('joinRoomByCode', (code) => {
      const room = rooms.findByCode(code);
      if (!room) {
        socket.emit('roomError', { message: 'Room not found' });
        return;
      }
      if (!rooms.join(room, user)) {
        socket.emit('roomError', { message: 'Unable to join room' });
        return;
      }
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcast(io, room.roomId, 'roomUpdate', rooms.toPublic(room, user.id));
    });

    socket.on('joinRoom', (roomId) => {
      const room = rooms.findById(roomId);
      if (!room || !rooms.join(room, user)) return;
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcast(io, room.roomId, 'roomUpdate', rooms.toPublic(room, user.id));
    });

    socket.on('leaveRoom', () => leaveRoom(socket, rooms, io));

    socket.on('setReady', (ready) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      rooms.setReady(room, user.id, !!ready);
      broadcast(io, room.roomId, 'roomUpdate', rooms.toPublic(room, user.id));

      const started = rooms.maybeStart(room);
      if (started) {
        const payload = {
          mapId: started.mapId,
          mode: started.mode,
          durationMillis: GAME_DURATION_MS,
          startedAt: Date.now(),
        };
        broadcast(io, room.roomId, 'gameStart', payload);
        runMatchLoop(io, room, roomScores);
      }
    });

    // ---- chat ----
    socket.on('chatMessage', (roomId, text) => {
      if (typeof text !== 'string') return;
      const clipped = text.slice(0, 256);
      const room = rooms.findById(roomId) || rooms.findById(socket.data.roomId);
      if (!room) return;
      broadcast(io, room.roomId, 'chatMessage', {
        from: user.id,
        username: user.username,
        text: clipped,
        roomId: room.roomId,
        timestamp: Date.now(),
      });
    });

    // ---- in-game ----
    socket.on('playerMove', (data) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      if (!data || typeof data.x !== 'number' || typeof data.y !== 'number') return;
      broadcast(io, room.roomId, 'playerMove', {
        userId: user.id,
        x: data.x,
        y: data.y,
        vx: data.vx || 0,
        vy: data.vy || 0,
      });
    });

    socket.on('captureTile', (data) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      const tx = Math.round(Number(data?.tx));
      const ty = Math.round(Number(data?.ty));
      if (!roomInBounds(tx, ty)) return;

      // authoritative per-user scoring
      const scores = roomScores.get(room.roomId) || new Map();
      const entry = scores.get(user.id) || { score: 0, tiles: 0 };
      entry.score += 10;
      entry.tiles += 1;
      scores.set(user.id, entry);
      roomScores.set(room.roomId, scores);

      broadcast(io, room.roomId, 'captureTile', { userId: user.id, tx, ty });
    });

    socket.on('powerupCollected', (data) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      broadcast(io, room.roomId, 'powerupCollected', {
        userId: user.id,
        powerupId: String(data?.powerupId || ''),
      });
    });

    socket.on('disconnect', () => {
      if (socket.data.roomId) {
        leaveRoom(socket, rooms, io);
      }
    });
  });
}

// ---- helpers ----

/** Last stage of the auth handshake; calls back with the fresh user row. */
function authenticate(socket, done) {
  const token = socket.handshake.auth && socket.handshake.auth.token;
  if (!token) {
    socket.emit('authError', { message: 'Missing token' });
    socket.disconnect(true);
    done(null);
    return;
  }
  try {
    const payload = jwt.verifyToken(token);
    userService.findById(payload.sub).then((user) => {
      if (!user) {
        socket.emit('authError', { message: 'Unknown user' });
        socket.disconnect(true);
        done(null);
      } else {
        socket.data.user = user;
        done(user);
      }
    });
  } catch (_) {
    socket.emit('authError', { message: 'Invalid token' });
    socket.disconnect(true);
    done(null);
  }
}

function leaveRoom(socket, rooms, io) {
  const roomId = socket.data.roomId;
  if (!roomId) return;
  const room = rooms.findById(roomId);
  socket.data.roomId = null;
  socket.leave(roomId);
  if (!room) return;

  const remaining = rooms.leave(room, socket.data.user.id);
  if (remaining === 0) {
    rooms.destroy(room);
  } else {
    broadcast(io, roomId, 'roomUpdate', rooms.toPublic(room, socket.data.user.id));
  }
}

/** Runs the countdown: periodic scoreUpdate broadcast, then finalize. */
function runMatchLoop(io, room, roomScores) {
  const startedAt = room.startedAt;
  const ticker = setInterval(() => {
    const elapsed = Date.now() - startedAt;
    if (elapsed < GAME_DURATION_MS) {
      broadcast(io, room.roomId, 'scoreUpdate', {
        roomId: room.roomId,
        elapsed,
        remainingMs: GAME_DURATION_MS - elapsed,
        leaderboard: computeScores(room, roomScores),
      });
    } else {
      clearInterval(ticker);
      finalizeMatch(io, room, roomScores);
    }
  }, 2000);
  room.ticker = ticker;
}

/** Sorted leaderboard for the room with server-authoritative scores. */
function computeScores(room, roomScores) {
  const scores = roomScores.get(room.roomId) || new Map();
  return [...room.players.values()]
    .map((p) => {
      const s = scores.get(p.id) || { score: 0, tiles: 0 };
      return { userId: p.id, username: p.username, score: s.score, tiles: s.tiles };
    })
    .sort((a, b) => b.score - a.score)
    .map((row, i) => ({ ...row, rank: i + 1 }));
}

/** Emits the authoritative match result and cleans up the room. */
function finalizeMatch(io, room, roomScores) {
  if (room.ticker) {
    clearInterval(room.ticker);
    room.ticker = null;
  }
  broadcast(io, room.roomId, 'gameEnd', {
    roomId: room.roomId,
    endedAt: Date.now(),
    standings: computeScores(room, roomScores),
  });
}

function broadcast(io, roomId, event, payload) {
  io.to(roomId).emit(event, payload);
}

/** Loose bounds guard for capture tiles (map geometry is 64x48). */
function roomInBounds(tx, ty) {
  return tx >= 0 && ty >= 0 && tx < 64 && ty < 48;
}

module.exports = { configureSocket };