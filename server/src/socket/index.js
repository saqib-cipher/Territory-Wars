'use strict';

const jwt = require('../utils/jwt');
const userService = require('../models/user.model');
const matchService = require('../models/match.model');
const { RoomManager } = require('../matchmaking/roomManager');

/**
 * Real-time Socket.IO handler for Guess the Card party game:
 * - Room creation/joining (2-5 players)
 * - Server-authoritative role-aware card broadcasting
 * - Question asking and YES/NO/MAYBE answering
 * - Authoritative guess submission and scoring
 * - Turn and round progression
 * - Reconnection support and leave penalty
 */
function configureSocket(io) {
  const rooms = new RoomManager();
  const userSocketMap = new Map(); // userId -> socketId

  io.on('connection', (socket) => {
    handleConnection(io, socket, rooms, userSocketMap);
  });

  return io;
}

function handleConnection(io, socket, rooms, userSocketMap) {
  authenticate(socket, (user) => {
    if (!user) return;

    userSocketMap.set(user.id, socket.id);

    // ---- Reconnection & Sync ----
    socket.on('reconnectToGame', (roomId) => {
      const room = rooms.findById(roomId);
      if (room && room.players.has(user.id)) {
        if (room.disconnectTimers && room.disconnectTimers.has(user.id)) {
          clearTimeout(room.disconnectTimers.get(user.id));
          room.disconnectTimers.delete(user.id);
        }
        socket.join(room.roomId);
        socket.data.roomId = room.roomId;
        rooms.join(room, user); // Marks online
        socket.emit('roomJoined', rooms.toPublic(room, user.id));
        broadcastRoomState(io, room, rooms);
      }
    });

    // ---- Room Discovery & Management ----
    socket.on('getPublicRooms', () => {
      socket.emit('publicRoomsList', rooms.getPublicRooms());
    });

    socket.on('createRoom', (mode, customConfig) => {
      const room = rooms.createRoom(user, mode, customConfig);
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcastPublicRooms(io, rooms);
    });

    socket.on('joinRoomByCode', (code) => {
      const room = rooms.findByCode(code);
      if (!room) {
        socket.emit('roomError', { message: 'Room not found. Check the code.' });
        return;
      }
      if (!rooms.join(room, user)) {
        socket.emit('roomError', { message: 'Unable to join room (full or in progress).' });
        return;
      }
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcastRoomState(io, room, rooms);
    });

    socket.on('joinRoom', (roomId) => {
      const room = rooms.findById(roomId);
      if (!room) {
        socket.emit('roomError', { message: 'Cannot join room.' });
        return;
      }
      // If player is already in room (e.g., reconnecting mid-game), just re-register socket
      if (room.players.has(user.id)) {
        socket.join(room.roomId);
        socket.data.roomId = room.roomId;
        // Cancel any pending disconnect timer
        if (room.disconnectTimers && room.disconnectTimers.has(user.id)) {
          clearTimeout(room.disconnectTimers.get(user.id));
          room.disconnectTimers.delete(user.id);
        }
        const p = room.players.get(user.id);
        if (p) p.isOnline = true;
        socket.emit('roomJoined', rooms.toPublic(room, user.id));
        broadcastRoomState(io, room, rooms);
        return;
      }
      if (!rooms.join(room, user)) {
        socket.emit('roomError', { message: 'Room is full or already started.' });
        return;
      }
      socket.join(room.roomId);
      socket.data.roomId = room.roomId;
      socket.emit('roomJoined', rooms.toPublic(room, user.id));
      broadcastRoomState(io, room, rooms);
    });

    socket.on('leaveRoom', () => leaveRoom(socket, rooms, io));

    socket.on('setReady', (ready) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      rooms.setReady(room, user.id, !!ready);
      broadcastRoomState(io, room, rooms);
    });

    socket.on('startGame', () => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      if (room.hostId !== user.id) return; // Only host starts game

      const started = rooms.maybeStart(room);
      if (started) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'gameStart', {
          roomId: room.roomId,
          mode: room.mode,
          totalRounds: room.totalRounds,
          currentRound: room.currentRound,
          currentTurnPlayerId: room.currentAnswererId,
          durationMillis: room.roundTimeSeconds * 1000,
        });
      }
    });

    // ---- Friend Invites & Requests ----
    socket.on('sendRoomInvite', (targetUserId) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room) return;
      const targetSocketId = userSocketMap.get(targetUserId);
      if (targetSocketId) {
        io.to(targetSocketId).emit('roomInvitation', {
          senderId: user.id,
          senderName: user.username,
          senderAvatar: user.avatarFileName || 'avatar1.png',
          roomId: room.roomId,
          code: room.code,
          mode: room.mode,
        });
      }
    });

    socket.on('sendFriendRequest', (payload) => {
      const targetUserId = typeof payload === 'string' ? payload : (payload && payload.targetUserId);
      if (!targetUserId) return;
      const targetSocketId = userSocketMap.get(targetUserId);
      if (targetSocketId) {
        io.to(targetSocketId).emit('friendRequestReceived', {
          senderId: user.id,
          senderName: user.username,
          senderAvatar: user.avatarFileName || 'avatar1.png',
          timestamp: Date.now(),
        });
      }
    });

    socket.on('checkOnlineStatus', (userIds, callback) => {
      if (!Array.isArray(userIds) || typeof callback !== 'function') return;
      const statusMap = {};
      for (const uid of userIds) {
        statusMap[uid] = userSocketMap.has(uid);
      }
      callback(statusMap);
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

    // ---- WebRTC & LiveKit SFU Group Voice Call ----
    socket.on('get_livekit_token', (roomId) => {
      const targetRoomId = roomId || socket.data.roomId;
      const apiKey = process.env.LIVEKIT_API_KEY;
      const apiSecret = process.env.LIVEKIT_API_SECRET;
      const livekitUrl = process.env.LIVEKIT_URL;

      if (!apiKey || !apiSecret || !livekitUrl) {
        socket.emit('livekit_token_fallback', {
          message: 'LiveKit credentials not configured in server/.env, using direct low-latency socket voice engine',
          roomId: targetRoomId,
        });
        return;
      }

      try {
        const payload = {
          iss: apiKey,
          sub: user.id,
          name: user.username || 'Player',
          video: {
            roomJoin: true,
            room: targetRoomId,
            canPublish: true,
            canSubscribe: true,
            canPublishData: true,
          },
        };
        const jwt = require('jsonwebtoken');
        const token = jwt.sign(payload, apiSecret, { expiresIn: '6h' });
        socket.emit('livekit_token_received', {
          url: livekitUrl,
          token: token,
          roomId: targetRoomId,
        });
      } catch (err) {
        socket.emit('livekit_token_error', { message: err.message });
      }
    });

    socket.on('voice_join', (roomId) => {
      const targetRoomId = roomId || socket.data.roomId;
      if (!targetRoomId) {
        console.warn(`[VOICE] voice_join from ${user.id} but no roomId! socket.data.roomId=${socket.data.roomId}`);
        return;
      }
      socket.data.voiceRoomId = targetRoomId;
      socket.join(`voice_${targetRoomId}`);
      socket.join(targetRoomId);
      console.log(`[VOICE] ${user.username}(${user.id}) joined voice_${targetRoomId}`);
      socket.to(`voice_${targetRoomId}`).emit('voice_user_joined', {
        userId: user.id,
        username: user.username,
        socketId: socket.id,
      });
      socket.to(targetRoomId).emit('voice_user_joined', {
        userId: user.id,
        username: user.username,
        socketId: socket.id,
      });
    });

    socket.on('voice_leave', (roomId) => {
      const targetRoomId = roomId || socket.data.voiceRoomId || socket.data.roomId;
      if (!targetRoomId) return;
      socket.leave(`voice_${targetRoomId}`);
      socket.to(`voice_${targetRoomId}`).emit('voice_user_left', {
        userId: user.id,
      });
      socket.to(targetRoomId).emit('voice_user_left', {
        userId: user.id,
      });
    });

    socket.on('voice_offer', (payload) => {
      if (!payload || !payload.to) return;
      const targetSocketId = userSocketMap.get(payload.to);
      if (targetSocketId) {
        io.to(targetSocketId).emit('voice_offer', {
          from: user.id,
          offer: payload.offer,
        });
      }
    });

    socket.on('voice_answer', (payload) => {
      if (!payload || !payload.to) return;
      const targetSocketId = userSocketMap.get(payload.to);
      if (targetSocketId) {
        io.to(targetSocketId).emit('voice_answer', {
          from: user.id,
          answer: payload.answer,
        });
      }
    });

    socket.on('voice_ice_candidate', (payload) => {
      if (!payload || !payload.to) return;
      const targetSocketId = userSocketMap.get(payload.to);
      if (targetSocketId) {
        io.to(targetSocketId).emit('voice_ice_candidate', {
          from: user.id,
          candidate: payload.candidate,
        });
      }
    });

    socket.on('voice_speaking', (payload) => {
      const isSpeaking = typeof payload === 'boolean' ? payload : (payload && payload.isSpeaking);
      const roomId = (payload && payload.roomId) || socket.data.voiceRoomId || socket.data.roomId;
      if (!roomId) return;
      const data = { userId: user.id, isSpeaking: !!isSpeaking };
      socket.to(`voice_${roomId}`).emit('voice_player_speaking', data);
      socket.to(roomId).emit('voice_player_speaking', data);
    });

    socket.on('voice_audio_chunk', (chunk) => {
      const roomId = (chunk && chunk.roomId) || socket.data.voiceRoomId || socket.data.roomId;
      if (!roomId || !chunk) {
        console.warn(`[VOICE] voice_audio_chunk from ${user.id} DROPPED — no roomId. data.roomId=${socket.data.roomId} data.voiceRoomId=${socket.data.voiceRoomId} chunk.roomId=${chunk && chunk.roomId}`);
        return;
      }
      const audioData = typeof chunk === 'string' ? chunk : (chunk.data || chunk);
      const ts = (chunk && chunk.ts) || Date.now();
      const payload = {
        sender: user.id,
        data: audioData,
        ts: ts,
      };
      // Broadcast to voice room AND game room (covers all socket join strategies)
      const voiceRoom = io.sockets.adapter.rooms.get(`voice_${roomId}`);
      const gameRoom = io.sockets.adapter.rooms.get(roomId);
      console.log(`[VOICE] audio chunk from ${user.id} → room=${roomId} voice_room_size=${voiceRoom ? voiceRoom.size : 0} game_room_size=${gameRoom ? gameRoom.size : 0}`);
      socket.to(`voice_${roomId}`).emit('voice_audio_received', payload);
      socket.to(roomId).emit('voice_audio_received', payload);
    });

    // ---- Gameplay Events ----
    socket.on('askQuestion', (questionText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt || room.roundRevealed) return;
      if (typeof questionText !== 'string' || questionText.trim().length === 0) return;

      const success = rooms.askQuestion(room, questionText.trim(), user.id);
      if (success) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'questionAsked', {
          askedBy: user.id,
          askerName: user.username,
          question: questionText.trim(),
          questionsRemaining: room.questionsRemaining,
        });
      }
    });

    socket.on('answerQuestion', (answerText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt || room.roundRevealed) return;
      // Only the Answerer answers questions
      if (room.currentAnswererId !== user.id) return;

      const normalizedAnswer = (answerText || '').toUpperCase();
      const validAnswers = ['YES', 'NO', 'MAYBE', 'YES 👍', 'NO 👎', 'MAYBE 🤷'];
      const canonical = normalizedAnswer.includes('YES') ? 'YES 👍' :
                        normalizedAnswer.includes('NO') ? 'NO 👎' : 'MAYBE 🤷';

      const success = rooms.answerQuestion(room, canonical);
      if (success) {
        broadcastRoomState(io, room, rooms);
        broadcastToRoom(io, room.roomId, 'questionAnswered', {
          answer: canonical,
          answererName: user.username,
          questionsRemaining: room.questionsRemaining,
        });

        // If no questions remaining, reveal card & advance
        if (room.questionsRemaining <= 0) {
          handleRoundTimeout(io, room, rooms);
        }
      }
    });

    socket.on('submitGuess', (guessText) => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt || room.roundRevealed) return;
      if (room.currentAnswererId === user.id) return; // Answerer cannot guess

      const result = rooms.submitGuess(room, user.id, String(guessText || ''));

      broadcastToRoom(io, room.roomId, 'guessResult', {
        guessedBy: user.username,
        guessedById: user.id,
        guess: guessText,
        isCorrect: result.isCorrect,
        scoreAwarded: result.scoreAwarded,
        cardAnswer: result.cardAnswer,
      });

      if (result.isCorrect) {
        broadcastRoomState(io, room, rooms);
        setTimeout(() => {
          advanceOrEndGame(io, room, rooms);
        }, 3500);
      } else {
        broadcastRoomState(io, room, rooms);
      }
    });

    socket.on('passTurn', () => {
      const room = rooms.findById(socket.data.roomId);
      if (!room || !room.startedAt) return;
      advanceOrEndGame(io, room, rooms);
    });

    socket.on('disconnect', () => {
      userSocketMap.delete(user.id);
      if (socket.data.roomId) {
        leaveRoom(socket, rooms, io);
      }
    });
  });
}

function handleRoundTimeout(io, room, rooms) {
  room.roundRevealed = true;
  // Answerer gets bonus for stumping questioners
  const answerer = room.players.get(room.currentAnswererId);
  if (answerer) {
    answerer.score = (answerer.score || 0) + 50;
  }

  broadcastToRoom(io, room.roomId, 'roundTimeout', {
    cardAnswer: room.currentCard ? room.currentCard.word : '',
    answererName: answerer ? answerer.username : 'Answerer',
    bonusAwarded: 50,
  });
  broadcastRoomState(io, room, rooms);

  setTimeout(() => {
    advanceOrEndGame(io, room, rooms);
  }, 3500);
}

function advanceOrEndGame(io, room, rooms) {
  room.currentTurnIndex += 1;

  if (room.currentTurnIndex >= room.answererQueue.length) {
    // Match complete
    room.endedAt = Date.now();
    const standings = [...room.players.values()]
      .map((p) => ({
        userId: p.id,
        username: p.username,
        avatarFileName: p.avatarFileName || 'avatar_01.png',
        score: p.score || 0,
        correctGuesses: p.correctGuesses || 0,
        questionsAsked: p.questionsAsked || 0,
      }))
      .sort((a, b) => b.score - a.score);

    broadcastToRoom(io, room.roomId, 'gameEnd', {
      roomId: room.roomId,
      winner: standings[0] || null,
      standings,
      totalRounds: room.totalRounds,
    });

    // Persist match result to database if match model available
    try {
      if (matchService && typeof matchService.recordMatch === 'function') {
        matchService.recordMatch({
          roomId: room.roomId,
          mode: room.mode,
          winnerId: standings[0] ? standings[0].userId : null,
          standings,
          durationSeconds: Math.round((Date.now() - room.startedAt) / 1000),
        });
      }
    } catch (_) {}
  } else {
    rooms.startRound(room);
    broadcastRoomState(io, room, rooms);
    broadcastToRoom(io, room.roomId, 'turnStarted', {
      currentTurnPlayerId: room.currentAnswererId,
      currentRound: room.currentRound,
      totalRounds: room.totalRounds,
    });
  }
}

function broadcastPublicRooms(io, rooms) {
  io.emit('publicRoomsList', rooms.getPublicRooms());
}

function broadcastRoomState(io, room, rooms) {
  const sockets = io.sockets.adapter.rooms.get(room.roomId);
  if (!sockets) return;

  for (const socketId of sockets) {
    const s = io.sockets.sockets.get(socketId);
    if (s && s.data && s.data.user) {
      s.emit('roomUpdate', rooms.toPublic(room, s.data.user.id));
    }
  }
  broadcastPublicRooms(io, rooms);
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

  // If game is not active, standard leave
  if (!room.startedAt || room.endedAt) {
    const remaining = rooms.leave(room, socket.data.user.id);
    if (remaining === 0) {
      rooms.destroy(room);
    } else {
      broadcastRoomState(io, room, rooms);
    }
    return;
  }

  // Active game: mark player temporarily offline and allow 15-second grace period for activity transition/reconnect
  const player = room.players.get(socket.data.user.id);
  if (player) {
    player.isOnline = false;
  }

  broadcastRoomState(io, room, rooms);

  // Set grace timer
  if (!room.disconnectTimers) room.disconnectTimers = new Map();
  if (room.disconnectTimers.has(socket.data.user.id)) {
    clearTimeout(room.disconnectTimers.get(socket.data.user.id));
  }

  const timer = setTimeout(() => {
    room.disconnectTimers.delete(socket.data.user.id);
    const p = room.players.get(socket.data.user.id);
    if (p && !p.isOnline) {
      const remaining = rooms.leave(room, socket.data.user.id);
      if (remaining === 0) {
        rooms.destroy(room);
      } else if (remaining === 1 && room.startedAt && !room.endedAt) {
        broadcastToRoom(io, room.roomId, 'matchAbandoned', {
          roomId: room.roomId,
          message: 'All other players left the match.',
        });
        rooms.destroy(room);
      } else if (room.startedAt && !room.endedAt && room.currentAnswererId === socket.data.user.id) {
        advanceOrEndGame(io, room, rooms);
      } else {
        broadcastRoomState(io, room, rooms);
      }
    }
  }, 10000); // 10s grace period

  room.disconnectTimers.set(socket.data.user.id, timer);
}

function authenticate(socket, done) {
  const auth = socket.handshake.auth || {};
  const query = socket.handshake.query || {};
  const token = auth.token || query.token;
  const uid = auth.uid || query.uid || token;
  const name = auth.name || query.name || auth.username || 'Player';

  if (!token && !uid) {
    socket.emit('authError', { message: 'Missing token or user ID' });
    socket.disconnect(true);
    done(null);
    return;
  }

  // First try JWT verification
  if (token && typeof token === 'string' && token.includes('.')) {
    try {
      const payload = jwt.verifyToken(token);
      userService.findById(payload.sub).then((user) => {
        if (user) {
          socket.data.user = user;
          done(user);
          return;
        }
        const u = {
          id: payload.sub || uid,
          username: payload.username || name,
          avatarId: payload.avatarId || 'avatar_01.png',
          avatarFileName: payload.avatarFileName || 'avatar_01.png',
        };
        socket.data.user = u;
        done(u);
      }).catch(() => {
        const u = { id: uid || 'user_' + socket.id, username: name, avatarId: 'avatar_01.png', avatarFileName: 'avatar_01.png' };
        socket.data.user = u;
        done(u);
      });
      return;
    } catch (_) {
      // Proceed to UID fallback
    }
  }

  // Firebase UID / Guest Auth Fallback
  const resolvedUid = uid || (token ? String(token) : 'guest_' + socket.id.substring(0, 5));
  const user = {
    id: resolvedUid,
    username: name || 'Player_' + resolvedUid.substring(0, 4),
    avatarId: 'avatar_01.png',
    avatarFileName: 'avatar_01.png',
  };
  socket.data.user = user;
  done(user);
}

module.exports = { configureSocket };