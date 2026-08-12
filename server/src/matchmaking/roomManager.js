'use strict';

const crypto = require('crypto');
const { getModeConfig, getRandomCard, validateGuess } = require('../game/cardDatabase');

const MAX_PLAYERS = 5;
const MIN_PLAYERS = 2;

/**
 * Authoritative Guess the Card Room Manager.
 * Handles 2-5 player rooms, mode configuration, server-side card masking,
 * question counts, turns, and authoritative scoring.
 *
 * Uses host UID as the stable room identifier so reconnection/re-login
 * returns to the same room instead of creating a new one.
 */
class RoomManager {
  constructor() {
    this.rooms = new Map(); // roomId -> room
    this.hostRoomIndex = new Map(); // hostUid -> roomId (persistent rooms)
  }

  static generateCode() {
    const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 5; i++) {
      code += alphabet[crypto.randomInt(alphabet.length)];
    }
    return code;
  }

  /**
   * Create or rejoin a room keyed by the host's UID.
   * If the host already has an active room, returns it instead of creating a new one.
   */
  createRoom(host, mode = 'ANIMALS', customConfig = null) {
    // Reuse existing room owned by this host (supports re-login)
    const existingRoomId = this.hostRoomIndex.get(host.id);
    if (existingRoomId) {
      const existing = this.rooms.get(existingRoomId);
      if (existing && !existing.startedAt) {
        // Update host data (avatar may have changed since last login)
        existing.players.set(host.id, {
          ...host,
          isReady: false,
          isHost: true,
          score: existing.players.get(host.id)?.score || 0,
        });
        existing.hostId = host.id;
        return existing;
      }
      // Old room was started/finished — clean up and create fresh
      this.hostRoomIndex.delete(host.id);
      if (existing) this.rooms.delete(existingRoomId);
    }

    const roomId = `room_${host.id}`; // stable room id from host UID
    const code = RoomManager.generateCode();
    const modeConfig = getModeConfig(mode);

    const room = {
      roomId,
      code,
      uniqueCode: `${host.username || 'Host'}-${code}`, // human-readable unique code
      mode: (mode || 'ANIMALS').toUpperCase(),
      maxPlayers: Math.min(MAX_PLAYERS, customConfig?.maxPlayers || MAX_PLAYERS),
      questionLimit: customConfig?.questionLimit || modeConfig.questionLimit || 10,
      roundTimeSeconds: customConfig?.roundTimeSeconds || modeConfig.roundTimeSeconds || 60,
      hostId: host.id,
      players: new Map([[host.id, { ...host, isReady: false, isHost: true, score: 0 }]]),
      startedAt: null,
      finishedAt: null,
      currentTurnIndex: 0,
      currentTurnPlayerId: null,
      currentCard: null,
      usedCardIds: [],
      questionsRemaining: 10,
      questionHistory: [],
      turnStartTime: null,
      totalRounds: 1,
      currentRound: 1,
      matchHistory: [], // per-round results for final summary
    };
    this.rooms.set(roomId, room);
    this.hostRoomIndex.set(host.id, roomId);
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
    // If this user is the host, update their data (avatar sync on re-login)
    if (room.hostId === user.id) {
      room.players.set(user.id, {
        ...user,
        isReady: room.players.get(user.id)?.isReady || false,
        isHost: true,
        score: room.players.get(user.id)?.score || 0,
      });
      return true;
    }
    if (room.players.has(user.id)) return true; // already in room
    if (room.players.size >= room.maxPlayers) return false;
    if (room.startedAt) return false;
    room.players.set(user.id, { ...user, isReady: false, isHost: false, score: 0 });
    return true;
  }

  leave(room, userId) {
    room.players.delete(userId);
    if (room.players.size > 0) {
      if (room.hostId === userId) {
        const nextHostId = room.players.keys().next().value;
        room.players.get(nextHostId).isHost = true;
        room.hostId = nextHostId;
      }
    }
    return room.players.size;
  }

  setReady(room, userId, ready) {
    const p = room.players.get(userId);
    if (!p) return false;
    p.isReady = ready;
    return true;
  }

  maybeStart(room) {
    if (room.startedAt) return null;
    const everyoneReady =
      room.players.size >= MIN_PLAYERS &&
      [...room.players.values()].every((p) => p.isReady);
    if (!everyoneReady) return null;

    room.startedAt = Date.now();
    room.totalRounds = room.players.size; // each player gets a turn holding the card
    room.currentRound = 1;
    room.currentTurnIndex = 0;
    this.startNextTurn(room);
    return room;
  }

  startNextTurn(room) {
    const playerList = [...room.players.values()];
    if (playerList.length === 0) return null;

    if (room.currentTurnIndex >= playerList.length) {
      room.currentTurnIndex = 0;
      room.currentRound += 1;
    }

    const currentTurnPlayer = playerList[room.currentTurnIndex];
    room.currentTurnPlayerId = currentTurnPlayer.id;

    // Pick card
    const card = getRandomCard(room.mode, room.usedCardIds);
    room.currentCard = card;
    if (card) room.usedCardIds.push(card.id);

    room.questionsRemaining = room.questionLimit;
    room.questionHistory = [];
    room.turnStartTime = Date.now();
    return room;
  }

  askQuestion(room, questionText, askedByUserId) {
    if (room.questionsRemaining <= 0) return false;
    room.questionHistory.push({
      id: crypto.randomUUID(),
      question: questionText,
      askedBy: askedByUserId,
      answer: null,
    });
    return true;
  }

  answerQuestion(room, answer) {
    if (room.questionHistory.length === 0) return false;
    const lastQ = room.questionHistory[room.questionHistory.length - 1];
    if (lastQ.answer !== null) return false;

    lastQ.answer = answer; // 'YES', 'NO', 'MAYBE', 'NOT_SURE'
    room.questionsRemaining = Math.max(0, room.questionsRemaining - 1);
    return true;
  }

  submitGuess(room, userId, guessText) {
    if (!room.currentCard || room.currentTurnPlayerId !== userId) {
      return { isCorrect: false, scoreAwarded: 0, cardAnswer: room.currentCard?.word || '' };
    }

    const isCorrect = validateGuess(room.currentCard, guessText);
    let scoreAwarded = 0;

    if (isCorrect) {
      const elapsedSeconds = Math.round((Date.now() - room.turnStartTime) / 1000);
      const speedBonus = Math.max(0, Math.round(25 - elapsedSeconds));
      const remainingQBonus = room.questionsRemaining * 5;
      scoreAwarded = 100 + speedBonus + remainingQBonus;

      const player = room.players.get(userId);
      if (player) {
        player.score = (player.score || 0) + scoreAwarded;
      }
    }

    // Record this round in match history
    room.matchHistory.push({
      round: room.currentRound,
      turnPlayerId: userId,
      cardWord: room.currentCard.word,
      guess: guessText,
      isCorrect,
      scoreAwarded,
      timestamp: Date.now(),
    });

    return { isCorrect, scoreAwarded, cardAnswer: room.currentCard.word };
  }

  /**
   * Get final standings for a completed match.
   */
  getStandings(room) {
    return [...room.players.values()]
      .map((p) => ({
        userId: p.id,
        username: p.username,
        avatarId: p.avatarId || 'default',
        score: p.score || 0,
        isHost: p.isHost,
      }))
      .sort((a, b) => b.score - a.score);
  }

  /** Public view of room state for a given player socket (role-aware card masking). */
  toPublic(room, viewerId) {
    const isGuesser = room.currentTurnPlayerId === viewerId;
    const cardDisplay = room.currentCard
      ? isGuesser
        ? '?????'
        : room.currentCard.word
      : null;

    return {
      roomId: room.roomId,
      code: room.code,
      uniqueCode: room.uniqueCode,
      mode: room.mode,
      maxPlayers: room.maxPlayers,
      questionLimit: room.questionLimit,
      roundTimeSeconds: room.roundTimeSeconds,
      hostId: room.hostId,
      startedAt: room.startedAt,
      finishedAt: room.finishedAt,
      currentTurnPlayerId: room.currentTurnPlayerId,
      currentRound: room.currentRound,
      totalRounds: room.totalRounds,
      card: cardDisplay,
      cardCategory: room.currentCard?.category || '',
      isGuesser,
      questionsRemaining: room.questionsRemaining,
      questionHistory: room.questionHistory,
      players: [...room.players.values()].map((p) => ({
        userId: p.id,
        username: p.username,
        avatarId: p.avatarId || 'default',
        isReady: p.isReady,
        isHost: p.isHost,
        score: p.score || 0,
      })),
    };
  }

  destroy(room) {
    this.hostRoomIndex.delete(room.hostId);
    this.rooms.delete(room.roomId);
  }
}

module.exports = { RoomManager, MAX_PLAYERS, MIN_PLAYERS };