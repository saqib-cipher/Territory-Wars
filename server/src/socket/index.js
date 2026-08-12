'use strict';

const jwt = require('../utils/jwt');
const userService = require('../models/user.model');
const matchService = require('../services/match.service');
const { RoomManager } = require('../matchmaking/roomManager');

/**
 * Real-time Socket.IO handler for Guess the Card party game:
 * - Room creation/joining (2-5 players) with persistent host rooms
 * - Server-authoritative role-aware card broadcasting
 * - Question asking and YES/NO/MAYBE answering
 * - Authoritative guess submission and scoring
 * - Turn and round progression with position switching
 * - Match history saving and room cleanup on game end
 */
function configureSocket(io) {
  const rooms = new RoomManager();

  io.on('connection', (socket) => {
    handleConnection(io, socket, rooms);
  });

  return io;
}

function handleConnection(io, socket, rooms) {
  authenticate(socket, (user) => {
    if (!user) return;

    // ---- Room Management ----
    socket.on('createRoom', (mode, customConfig) => {
      const room = rooms.createRoom(user, mode, customConfig);
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      // Broadcast to all in room so the host's avatar/name updates
      broadcastRoomState(io, room, rooms);
    });

    socket.on('joinRoomByCode', (code) => {
      const room = rooms.findByCode(code);
      if (!room) {
        socket.emit('roomError', { message: 'Room not found' });
        return;
      }
      if (!rooms.join(room, user)) {
        socket.emit('roomError', { message: 'Unable to join room (full or in progress)' });
        return;
      }
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcastRoomState(io, room, rooms);
    });

    socket.on('joinRoom', (roomId) => {
      const room = rooms.findById(roomId);
      if (!room || !rooms.join(room, user)) {
        socket.emit('roomError', { message: 'Cannot join room' });
        return;
      }
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcastRoomState(io, room, rooms);
      // Notify others a new player joined
      broadcastToRoom(io, room.roomId, 'playerJoined', {
        userId: user.id,
        username: user.username,
        avatarId: user.avatarId || 'default',
      });
    });

    socket.on('leaveRoom', () => leaveRoom(socket, rooms, io));

    socket.on('setReady', (ready) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      rooms.setReady(room, user.id, !!ready);
      broadcastRoomState(io, room, rooms);

      const started = rooms.maybeStart(room);
      if (started) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'gameStart', {
          roomId: room.roomId,
          mode: room.mode,
          totalRounds: room.totalRounds,
          currentTurnPlayerId: room.currentTurnPlayerId,
        });
      }
    });

    // ---- Chat ----
    socket.on('chatMessage', (roomId, text) => {
      if (typeof text !== 'string') return;
      const clipped = text.slice(0, 256);
      const room = rooms.findById(roomId) || rooms.findById(socket.data.roomId);
      if (!room) return;
      broadcastToRoom(io, room.roomId, 'chatMessage', {
        from: user.id,
        username: user.username,
        text: clipped,
        roomId: room.roomId,
        timestamp: Date.now(),
      });
    });

    // ---- Gameplay Events ----
    socket.on('askQuestion', (questionText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      if (typeof questionText !== 'string' || questionText.trim().length === 0) return;

      const success = rooms.askQuestion(room, questionText.trim(), user.id);
      if (success) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'questionAsked', {
          askedBy: user.id,
          askedByName: user.username,
          question: questionText.trim(),
          questionsRemaining: room.questionsRemaining,
        });
      }
    });

    socket.on('answerQuestion', (answerText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      // Only the card holder answers
      if (room.currentTurnPlayerId !== user.id) return;

      const normalizedAnswer = (answerText || '').toUpperCase();
      if (!['YES', 'NO', 'MAYBE', 'NOT_SURE'].includes(normalizedAnswer)) return;

      const success = rooms.answerQuestion(room, normalizedAnswer);
      if (success) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'questionAnswered', {
          answer: normalizedAnswer,
          questionsRemaining: room.questionsRemaining,
        });
      }
    });

    socket.on('submitGuess', (guessText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      if (room.currentTurnPlayerId !== user.id) return;

      const result = rooms.submitGuess(room, user.id, String(guessText || ''));

      broadcastToRoom(io, room.roomId, 'guessResult', {
        guessedBy: user.id,
        guessedByName: user.username,
        guess: guessText,
        isCorrect: result.isCorrect,
        scoreAwarded: result.scoreAwarded,
        cardAnswer: result.cardAnswer,
        cardCategory: room.currentCard?.category || '',
      });

      if (result.isCorrect) {
        // Advance to next turn after short delay
        setTimeout(() => {
          advanceOrEndGame(io, room, rooms);
        }, 3000);
      } else {
        broadcastRoomState(io, room, rooms);
      }
    });

    socket.on('passTurn', () => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      if (room.currentTurnPlayerId !== user.id) return;
      advanceOrEndGame(io, room, rooms);
    });

    socket.on('disconnect', () => {
      if (socket.data.roomId) {
        leaveRoom(socket, rooms, io);
      }
    });
  });
}

function advanceOrEndGame(io, room, rooms) {
  room.currentTurnIndex += 1;
  const playerList = [...room.players.values()];

  if (room.currentRound >= room.totalRounds && room.currentTurnIndex >= playerList.length) {
    // Game over - calculate winner and save match history
    const standings = rooms.getStandings(room);
    const winner = standings[0] || null;

    // Save match results for all players atomically
    const matchId = `match_${room.roomId}_${Date.now()}`;
    room.finishedAt = Date.now();

    // Persist multiplayer match results
    const standingsWithRank = standings.map((p, i) => ({
      userId: p.userId,
      score: p.score,
      rank: i + 1,
    }));

    matchService.saveMultiplayerMatch(
      matchId,
      room.mode,
      winner ? winner.userId : null,
      standingsWithRank
    ).then(() => {
      console.log(`[match] ${matchId} saved for ${standings.length} players`);
    }).catch((err) => {
      console.error('[match] Failed to save multiplayer match:', err);
    });

    broadcastToRoom(io, room.roomId, 'gameEnd', {
      roomId: room.roomId,
      matchId,
      winner,
      standings,
      matchHistory: room.matchHistory,
      mode: room.mode,
    });

    // Schedule room cleanup after players have received the results
    setTimeout(() => {
      rooms.destroy(room);
      console.log(`[room] ${room.roomId} cleaned up after game end`);
    }, 30000);

    room.startedAt = null;
  } else {
    rooms.startNextTurn(room);
    broadcastRoomState(io, room, rooms);
    broadcastToRoom(io, room.roomId, 'turnStarted', {
      currentTurnPlayerId: room.currentTurnPlayerId,
      currentRound: room.currentRound,
      // Announce position switch
      switchedPositions: true,
    });
  }
}

function broadcastRoomState(io, room, rooms) {
  // Role-aware broadcast: send masked state to guessing player, revealed state to clue givers
  const sockets = io.sockets.adapter.rooms.get(room.roomId);
  if (!sockets) return;

  for (const socketId of sockets) {
    const s = io.sockets.sockets.get(socketId);
    if (s && s.data && s.data.user) {
      s.emit('roomUpdate', rooms.toPublic(room, s.data.user.id));
    }
  }
}

function broadcastToRoom(io, roomId, event, payload) {
  io.to(roomId).emit(event, payload);
}

function leaveRoom(socket, rooms, io) {
  const roomId = socket.data.roomId;
  if (!roomId) return;
  const room = rooms.findById(roomId);
  socket.data.roomId = null;
  socket.leave(roomId);
  if (!room) return;

  const userId = socket.data.user?.id;
  const remaining = rooms.leave(room, userId);

  // Notify others that this player left
  if (userId) {
    broadcastToRoom(io, room.roomId, 'playerLeft', {
      userId,
      username: socket.data.user?.username || 'Player',
    });
  }

  if (remaining === 0) {
    rooms.destroy(room);
  } else {
    broadcastRoomState(io, room, rooms);
  }
}

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

module.exports = { configureSocket };