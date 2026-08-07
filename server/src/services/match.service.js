'use strict';

const { query, withTransaction } = require('../database');

/** XP required per level-up. */
const XP_PER_LEVEL = 500;

/** Rolls raw XP into (level, xpWithinLevel). */
function applyXp(baseXp) {
  let level = 1;
  let xp = baseXp;
  while (xp >= XP_PER_LEVEL) {
    xp -= XP_PER_LEVEL;
    level += 1;
  }
  return { level, xp };
}

/**
 * Awards rewards for a finished match.
 *
 * @param {string} userId
 * @param {object} result { matchId, won, rank, score, tilesCaptured }
 * @returns {Promise<{xpEarned:number, coinsEarned:number, rankChange:number, seasonProgress:number}>}
 */
async function awardMatchRewards(userId, result) {
  const xpEarned = 50 + Math.floor(result.score / 10) + (result.won ? 25 : 0);
  const coinsEarned = 100 + result.score;
  const rankChange = result.won ? 5 : result.rank > 2 ? -3 : -1;

  const outcome = await withTransaction(async (client) => {
    // coins + trophies
    await client.query(
      `UPDATE users
       SET coins = coins + $2,
           trophies = GREATEST(0, trophies + $3)
       WHERE id = $1`,
      [userId, coinsEarned, rankChange]
    );

    // xp + level rollover
    const { rows } = await client.query(
      'SELECT xp FROM users WHERE id = $1', [userId]
    );
    const { level, xp } = applyXp((rows[0]?.xp || 0) + xpEarned);
    await client.query(
      'UPDATE users SET xp = $2, level = $3 WHERE id = $1',
      [userId, xp, level]
    );

    // participant ledger
    await client.query(
      `UPDATE match_participants
       SET xp_earned = $3, coins_earned = $4
       WHERE match_id = $2 AND user_id = $1`,
      [userId, result.matchId, xpEarned, coinsEarned]
    );

    return { level };
  });

  return {
    xpEarned,
    coinsEarned,
    rankChange,
    seasonProgress: Math.min(100, Math.round((outcome.level % 50) * 2)),
  };
}

module.exports = { awardMatchRewards, applyXp, XP_PER_LEVEL };