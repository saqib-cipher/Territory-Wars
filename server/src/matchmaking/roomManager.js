'use strict';

const crypto = require('crypto');
const { getModeConfig, getRandomCard, validateGuess } = require('../game/cardDatabase');

const MAX_PLAYERS = 5;
const MIN_PLAYERS = 2;
const LEAVE_PENALTY = 50;

/**
 * Authoritative Guess the Card Room Manager.
 * 
 * Rules:
 * - Exactly one player is the Answerer (can see the card, answers YES/NO/MAYBE).
 * - All other players are Questioners (masked card, ask questions & submit guesses).
 * - Hidden Card Security: The secret card word is never sent to Questioners during active round.
 * - Fair turn system: every player gets a turn as Answerer once per match.
 * - Authoritative scoring and penalty calculations.
 */
class RoomManager {
  constructor() {
    this.rooms = new Map(); // roomId -> room
  }

  static generateCode() {
    const alphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    let code = '';
    for (let i = 0; i < 5; i++) {
      code += alphabet[crypto.randomInt(alphabet.length)];
    }
    return code;
  }

  generateUniqueCode() {
    let code = RoomManager.generateCode();
    let attempts = 0;
    while (this.findByCode(code) && attempts < 50) {
      code = RoomManager.generateCode();
      attempts += 1;
    }
    return code;
  }

  createRoom(host, mode = 'ANIMALS', customConfig = null) {
    const roomId = crypto.randomUUID();
    const code = this.generateUniqueCode();
    const modeConfig = getModeConfig(mode);

    const room = {
      roomId,
      code,
      name: customConfig?.name || `${host.username}'s Room`,
      mode: (mode || 'ANIMALS').toUpperCase(),
      maxPlayers: Math.min(MAX_PLAYERS, customConfig?.maxPlayers || MAX_PLAYERS),
      questionLimit: customConfig?.questionLimit || modeConfig.questionLimit || 10,
      roundTimeSeconds: customConfig?.roundTimeSeconds || modeConfig.roundTimeSeconds || 60,
      hostId: host.id,
      players: new Map([[host.id, {
        id: host.id,
        username: host.username,
        avatarId: host.avatarId || 'avatar_01.png',
        avatarFileName: host.avatarFileName || 'avatar_01.png',
        isReady: false,
        isHost: true,
        score: 0,
        correctGuesses: 0,
        questionsAsked: 0,
        isOnline: true,
      }]]),
      startedAt: null,
      endedAt: null,
      answererQueue: [],        // Ordered list of player IDs who will take turns as Answerer
      currentTurnIndex: 0,      // Index in answererQueue
      currentAnswererId: null,  // Current Answerer
      currentCard: null,
      usedCardIds: [],
      questionsRemaining: 10,
      questionHistory: [],
      roundRevealed: false,
      turnStartTime: null,
      totalRounds: 1,
      currentRound: 1,
      roundWinner: null,
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
    if (room.players.has(user.id)) {
      // Reconnection support: mark player online
      const existing = room.players.get(user.id);
      existing.isOnline = true;
      return true;
    }
    if (room.players.size >= room.maxPlayers) return false;
    if (room.startedAt && !room.endedAt) return false; // Game already in progress

    room.players.set(user.id, {
      id: user.id,
      username: user.username,
      avatarId: user.avatarId || 'avatar_01.png',
      avatarFileName: user.avatarFileName || 'avatar_01.png',
      isReady: false,
      isHost: false,
      score: 0,
      correctGuesses: 0,
      questionsAsked: 0,
      isOnline: true,
    });
    return true;
  }

  leave(room, userId) {
    const player = room.players.get(userId);
    if (!player) return room.players.size;

    // Apply leave penalty if match is active
    if (room.startedAt && !room.endedAt) {
      player.score = Math.max(0, (player.score || 0) - LEAVE_PENALTY);
    }

    room.players.delete(userId);

    // Remove from answerer queue if present
    const qIndex = room.answererQueue.indexOf(userId);
    if (qIndex !== -1) {
      room.answererQueue.splice(qIndex, 1);
    }

    // Host migration
    if (room.players.size > 0 && room.hostId === userId) {
      const nextHostId = room.players.keys().next().value;
      const nextHost = room.players.get(nextHostId);
      if (nextHost) {
        nextHost.isHost = true;
        room.hostId = nextHostId;
      }
    }

    return room.players.size;
  }

  setReady(room, userId, ready) {
    const p = room.players.get(userId);
    if (!p) return false;
    p.isReady = !!ready;
    return true;
  }

  /**
   * Starts game if at least 2 players are ready.
   * Creates a randomized, fair turn order for Answerers.
   */
  maybeStart(room) {
    if (room.startedAt) return null;
    const playerList = [...room.players.values()];
    const everyoneReady =
      playerList.length >= MIN_PLAYERS &&
      playerList.every((p) => p.isReady || p.isHost);
    if (!everyoneReady) return null;

    room.startedAt = Date.now();
    room.totalRounds = playerList.length; // 1 round per player as Answerer
    room.currentRound = 1;
    room.currentTurnIndex = 0;

    // Fair randomized queue of player IDs for Answerer role
    const playerIds = playerList.map((p) => p.id);
    for (let i = playerIds.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [playerIds[i], playerIds[j]] = [playerIds[j], playerIds[i]];
    }
    room.answererQueue = playerIds;

    this.startRound(room);
    return room;
  }

  startRound(room) {
    if (room.currentTurnIndex >= room.answererQueue.length) return null;

    room.currentAnswererId = room.answererQueue[room.currentTurnIndex];
    room.currentRound = room.currentTurnIndex + 1;
    room.roundRevealed = false;
    room.roundWinner = null;

    // Pick a card
    const card = getRandomCard(room.mode, room.usedCardIds);
    room.currentCard = card;
    if (card) room.usedCardIds.push(card.id);

    room.questionsRemaining = room.questionLimit;
    room.questionHistory = [];
    room.turnStartTime = Date.now();
    return room;
  }

  askQuestion(room, questionText, askedByUserId) {
    if (room.questionsRemaining <= 0 || room.roundRevealed) return false;
    // Questioners ask, not the Answerer
    if (askedByUserId === room.currentAnswererId) return false;

    const asker = room.players.get(askedByUserId);
    if (asker) asker.questionsAsked = (asker.questionsAsked || 0) + 1;

    room.questionHistory.push({
      id: crypto.randomUUID(),
      question: questionText,
      askedBy: askedByUserId,
      askerName: asker ? asker.username : 'Player',
      answer: null,
      answererName: null,
      timestamp: Date.now(),
    });
    return true;
  }

  answerQuestion(room, answer) {
    if (room.questionHistory.length === 0 || room.roundRevealed) return false;
    const lastQ = room.questionHistory[room.questionHistory.length - 1];
    if (lastQ.answer !== null) return false;

    const answerer = room.players.get(room.currentAnswererId);
    lastQ.answer = answer; // 'YES', 'NO', 'MAYBE'
    lastQ.answererName = answerer ? answerer.username : 'Answerer';

    room.questionsRemaining = Math.max(0, room.questionsRemaining - 1);
    return true;
  }

  submitGuess(room, userId, guessText) {
    if (!room.currentCard || room.roundRevealed) {
      return { isCorrect: false, scoreAwarded: 0 };
    }
    // Answerer cannot guess their own card
    if (userId === room.currentAnswererId) {
      return { isCorrect: false, scoreAwarded: 0 };
    }

    const isCorrect = validateGuess(room.currentCard, guessText);
    let scoreAwarded = 0;

    if (isCorrect) {
      const elapsedSeconds = Math.max(1, Math.round((Date.now() - room.turnStartTime) / 1000));
      const speedBonus = Math.max(0, Math.round(30 - elapsedSeconds));
      const remainingQBonus = room.questionsRemaining * 5;
      scoreAwarded = 100 + speedBonus + remainingQBonus;

      const player = room.players.get(userId);
      if (player) {
        player.score = (player.score || 0) + scoreAwarded;
        player.correctGuesses = (player.correctGuesses || 0) + 1;
        room.roundWinner = player.username;
      }
      room.roundRevealed = true;
    }

    return {
      isCorrect,
      scoreAwarded,
      cardAnswer: room.currentCard.word,
      winnerName: room.roundWinner,
    };
  }

  /**
   * Public role-aware room state snapshot.
   * 
   * CRITICAL SECURITY:
   * The secret card is ONLY sent if:
   * 1. The viewer IS the designated Answerer for this round, OR
   * 2. The round has ended / revealed.
   * 
   * Questioners receive '?????' during active round so the secret word never leaks.
   */
  toPublic(room, viewerId) {
    const isAnswerer = room.currentAnswererId === viewerId;
    const isGuesser = !isAnswerer;

    const shouldRevealCard = isAnswerer || room.roundRevealed;
    const cardDisplay = room.currentCard
      ? shouldRevealCard
        ? room.currentCard.word
        : '?????'
      : null;

    return {
      roomId: room.roomId,
      code: room.code,
      name: room.name,
      mode: room.mode,
      maxPlayers: room.maxPlayers,
      questionLimit: room.questionLimit,
      roundTimeSeconds: room.roundTimeSeconds,
      hostId: room.hostId,
      startedAt: room.startedAt,
      endedAt: room.endedAt,
      currentTurnPlayerId: room.currentAnswererId,
      currentAnswererId: room.currentAnswererId,
      currentRound: room.currentRound,
      totalRounds: room.totalRounds,
      card: cardDisplay,
      isGuesser,
      isAnswerer,
      roundRevealed: room.roundRevealed,
      roundWinner: room.roundWinner,
      questionsRemaining: room.questionsRemaining,
      questionHistory: room.questionHistory,
      players: [...room.players.values()].map((p) => ({
        userId: p.id,
        username: p.username,
        avatarId: p.avatarId || 'avatar_01.png',
        avatarFileName: p.avatarFileName || 'avatar_01.png',
        isReady: p.isReady,
        isHost: p.isHost,
        isOnline: p.isOnline !== false,
        score: p.score || 0,
        correctGuesses: p.correctGuesses || 0,
        questionsAsked: p.questionsAsked || 0,
      })),
    };
  }

  getPublicRooms() {
    const list = [];
    for (const room of this.rooms.values()) {
      if (!room.endedAt) {
        const host = room.players.get(room.hostId);
        list.push({
          roomId: room.roomId,
          code: room.code,
          name: room.name,
          mode: room.mode,
          hostId: room.hostId,
          hostName: host ? host.username : 'Host',
          currentPlayers: room.players.size,
          maxPlayers: room.maxPlayers,
          status: room.startedAt ? 'PLAYING' : 'WAITING',
          isFull: room.players.size >= room.maxPlayers,
        });
      }
    }
    return list;
  }

  destroy(room) {
    this.rooms.delete(room.roomId);
  }
}

module.exports = { RoomManager, MAX_PLAYERS, MIN_PLAYERS, LEAVE_PENALTY };