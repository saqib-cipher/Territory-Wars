'use strict';

const { query } = require('../database');

const STREAK_REWARDS = [100, 150, 200, 250, 300, 400, 500];

/** POST /v1/daily-reward/claim */
async function claimDailyReward(req, res, next) {
  try {
    const userId = req.user.id;
    const { rows } = await query(
      'SELECT streak, last_claim FROM daily_rewards WHERE user_id = $1',
      [userId]
    );

    const now = Date.now();
    const last = rows[0] ? Date.parse(rows[0].last_claim) : 0;
    const isNewDay = now - last >= 24 * 60 * 60 * 1000;

    if (!isNewDay && rows[0]) {
      return res.status(409).json({ code: 409, message: 'Already claimed today' });
    }

    // consecutive streak: claimed yesterday? then +1, else reset to 1
    const claimedYesterday = rows[0] && now - last < 48 * 60 * 60 * 1000;
    const streak = rows[0] && claimedYesterday ? rows[0].streak + 1 : 1;
    const reward = STREAK_REWARDS[Math.min(streak - 1, STREAK_REWARDS.length - 1)];

    await query(
      `INSERT INTO daily_rewards (user_id, streak, last_claim)
       VALUES ($1, $2, now())
       ON CONFLICT (user_id) DO UPDATE
       SET streak = $2, last_claim = now()`,
      [userId, streak]
    );
    await query('UPDATE users SET coins = coins + $2 WHERE id = $1', [userId, reward]);

    res.json({ rewardType: 'coins', amount: reward, streak });
  } catch (err) {
    next(err);
  }
}

module.exports = { claimDailyReward };