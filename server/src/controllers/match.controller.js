'use strict';

const { v4: uuidv4 } = require('uuid');
const { query, withTransaction } = require('../database');
const matchService = require('../services/match.service');

/**
 * POST /v1/match/report { matchId?, mode, won, rank, score, tilesCaptured }
 *
 * The room's authoritative result is normally inserted by the socket layer;
 * this endpoint exists for the client's offline/edge case reporting and to
 * grant rewards when a socket match ended.
 */
async function reportMatch(req, res, next) {
  try {
    const body = req.body || {};
    const matchId = body.matchId || uuidv4();
    const result = {
      matchId,
      mode: body.mode || 'duel',
      won: !!body.won,
      rank: parseInt(body.rank, 10) || 1,
      score: parseInt(body.score, 10) || 0,
      tilesCaptured: parseInt(body.tilesCaptured, 10) || 0,
    };

    // ensure the participant row exists so the ledger update applies
    await query(
      `INSERT INTO match_participants (match_id, user_id, score, tiles_captured)
       VALUES ($1, $2, $3, $4)
       ON CONFLICT DO NOTHING`,
      [matchId, req.user.id, result.score, result.tilesCaptured]
    );

    const rewards = await matchService.awardMatchRewards(req.user.id, result);
    res.json(rewards);
  } catch (err) {
    next(err);
  }
}

/** GET /v1/match/history?limit=20 */
async function getMatchHistory(req, res, next) {
  try {
    const limit = Math.min(parseInt(req.query.limit, 10) || 20, 100);
    const { rows } = await query(
      `SELECT m.id AS "matchId", m.mode, m.duration_ms / 1000 AS "durationSeconds",
              (m.winner_id = $1) AS won,
              mp.score, mp.tiles_captured AS "tilesCaptured",
              mp.xp_earned AS "xpEarned", mp.coins_earned AS "coinsEarned",
              m.started_at AS "playedAtEpochMillis"
       FROM match_participants mp
       JOIN matches m ON m.id = mp.match_id
       WHERE mp.user_id = $1
       ORDER BY m.started_at DESC
       LIMIT $2`,
      [req.user.id, limit]
    );
    res.json(rows.map((r) => ({ ...r, playedAtEpochMillis: Date.parse(r.playedAtEpochMillis) })));
  } catch (err) {
    next(err);
  }
}

module.exports = { reportMatch, getMatchHistory };