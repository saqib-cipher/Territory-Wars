'use strict';

const { withTransaction } = require('../database');

const WIN_XP = 40;
const BASE_XP = 60;
const WIN_COINS = 20;
const WIN_TROPHIES = 5;

/**
 * Applies XP/coin/trophy rewards for a reported match result.
 *
 * Idempotent per matchId: the participant row carries a primary key on
 * (match_id, user_id) and the user ledger is only incremented when the
 * participant row is first created (ON CONFLICT DO NOTHING).
 */
async function awardMatchRewards(userId, result) {
  const { matchId, won, rank } = result;
  const rankBonus = Math.max(0, 4 - rank) * 10;

  const xp = BASE_XP + (won ? WIN_XP + rankBonus : rankBonus);
  const coins = Math.max(0, 6 - rank) * 8 + (won ? WIN_COINS : 0);
  const trophies = won ? WIN_TROPHIES : 0;

  return withTransaction(async (client) => {
    // 1) match row (authoritative; created once)
    await client.query(
      `INSERT INTO matches (id, mode, winner_id)
       VALUES ($1, $2, $3)
       ON CONFLICT (id) DO NOTHING`,
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

module.exports = { awardMatchRewards };