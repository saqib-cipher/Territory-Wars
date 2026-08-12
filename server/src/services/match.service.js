'use strict';

const { withTransaction } = require('../database');

const WIN_XP = 40;
const BASE_XP = 60;
const WIN_COINS = 20;
const WIN_TROPHIES = 5;

/**
 * Apply XP/coin/trophy rewards for a single player in a match.
 * Idempotent per matchId+userId — participant row is created once.
 */
async function awardMatchRewards(userId, result) {
  const { matchId, won, rank } = result;
  const rankBonus = Math.max(0, 4 - rank) * 10;

  const xp = BASE_XP + (won ? WIN_XP + rankBonus : rankBonus);
  const coins = Math.max(0, 6 - rank) * 8 + (won ? WIN_COINS : 0);
  const trophies = won ? WIN_TROPHIES : 0;

  return withTransaction(async (client) => {
    // 1) match row — only create on first participant, update winner later
    await client.query(
      `INSERT INTO matches (id, mode, winner_id, ended_at)
       VALUES ($1, $2, $3, now())
       ON CONFLICT (id) DO UPDATE
         SET winner_id = CASE WHEN $3::uuid IS NOT NULL THEN $3::uuid ELSE matches.winner_id END,
             ended_at = now()`,
      [matchId, result.mode || 'duel', won ? userId : null]
    );

    // 2) participant ledger — only credited when first written
    const { rowCount } = await client.query(
      `INSERT INTO match_participants
         (match_id, user_id, score, tiles_captured, xp_earned, coins_earned)
       VALUES ($1, $2, $3, $4, $5, $6)
       ON CONFLICT (match_id, user_id) DO NOTHING`,
      [matchId, userId, result.score || 0, result.tilesCaptured || 0, xp, coins]
    );
    if (rowCount === 0) {
      return { xp: 0, coins: 0, trophies: 0, replay: true };
    }

    // 3) grant the ledger
    const { rows } = await client.query(
      `UPDATE users
       SET xp = xp + $2,
           coins = coins + $3,
           trophies = trophies + $4,
           level = GREATEST(level, floor((xp + $2) / 1000) + 1)
       WHERE id = $1
       RETURNING level`,
      [userId, xp, coins, trophies]
    );

    return {
      xp,
      coins,
      trophies,
      level: rows[0] ? rows[0].level : undefined,
      replay: false,
    };
  });
}

/**
 * Save a complete multiplayer match with all standings at once.
 * This ensures the winner is correctly set regardless of promise ordering.
 */
async function saveMultiplayerMatch(matchId, mode, winnerId, standings) {
  return withTransaction(async (client) => {
    // Create match record with correct winner
    await client.query(
      `INSERT INTO matches (id, mode, winner_id, ended_at)
       VALUES ($1, $2, $3, now())
       ON CONFLICT (id) DO UPDATE
         SET winner_id = EXCLUDED.winner_id,
             ended_at = now()`,
      [matchId, mode, winnerId]
    );

    // Insert all participants
    for (const entry of standings) {
      const rankBonus = Math.max(0, 4 - (entry.rank || 0)) * 10;
      const won = entry.userId === winnerId;
      const xp = BASE_XP + (won ? WIN_XP + rankBonus : rankBonus);
      const coins = Math.max(0, 6 - (entry.rank || 0)) * 8 + (won ? WIN_COINS : 0);
      const trophies = won ? WIN_TROPHIES : 0;

      await client.query(
        `INSERT INTO match_participants
           (match_id, user_id, score, xp_earned, coins_earned)
         VALUES ($1, $2, $3, $4, $5)
         ON CONFLICT (match_id, user_id) DO NOTHING`,
        [matchId, entry.userId, entry.score || 0, xp, coins]
      );

      await client.query(
        `UPDATE users
         SET xp = xp + $2,
             coins = coins + $3,
             trophies = trophies + $4,
             level = GREATEST(level, floor((xp + $2) / 1000) + 1)
         WHERE id = $1`,
        [entry.userId, xp, coins, trophies]
      );
    }

    return { matchId, mode, saved: standings.length };
  });
}

module.exports = { awardMatchRewards, saveMultiplayerMatch };