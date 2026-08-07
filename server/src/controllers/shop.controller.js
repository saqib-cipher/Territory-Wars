'use strict';

const { query } = require('../database');

/**
 * Static shop catalog. Prices are server-authoritative; client only displays.
 */
const CATALOG = [
  { id: 'skin_blaze', name: 'Blaze Skin', description: 'Molten trail skin', category: 'SKIN', priceCoins: 1500, priceGems: 0 },
  { id: 'skin_ghost', name: 'Ghost Skin', description: 'Translucent phantom', category: 'SKIN', priceCoins: 2500, priceGems: 0 },
  { id: 'trail_rainbow', name: 'Rainbow Trail', description: 'Spectral colour trail', category: 'TRAIL', priceCoins: 800, priceGems: 0 },
  { id: 'effect_confetti', name: 'Confetti Burst', description: 'Celebration on capture', category: 'EFFECT', priceCoins: 1200, priceGems: 0 },
  { id: 'emote_dance', name: 'Dance Emote', description: 'Show off in lobby', category: 'EMOTE', priceGems: 300, priceCoins: 0 },
  { id: 'title_commander', name: 'Commander Title', description: 'Show your rank flair', category: 'TITLE', priceCoins: 5000, priceGems: 0 },
  { id: 'coins_1000', name: '1,000 Coins', description: 'In-app purchase', category: 'COINS', priceCoins: 0, priceGems: 0 },
  { id: 'gems_100', name: '100 Gems', description: 'In-app purchase', category: 'GEMS', priceCoins: 0, priceGems: 0 },
  { id: 'bundle_rookie', name: 'Rookie Bundle', description: 'Starter cosmetics pack', category: 'BUNDLE', priceCoins: 0, priceGems: 500 },
];

/** GET /v1/shop */
async function getShop(req, res, next) {
  try {
    const { rows } = await query('SELECT item_id FROM inventory WHERE user_id = $1', [req.user.id]);
    const owned = new Set(rows.map((r) => r.item_id));
    const equipped = new Set(
      rows.filter((r) => r.equipped).map((r) => r.item_id)
    );

    const items = CATALOG.map((item) => ({
      ...item,
      owned: owned.has(item.id),
      equipped: equipped.has(item.id),
    }));
    res.json(items);
  } catch (err) {
    next(err);
  }
}

/** GET /v1/inventory */
async function getInventory(req, res, next) {
  try {
    const { rows } = await query(
      `SELECT item_id AS id, equipped FROM inventory WHERE user_id = $1`,
      [req.user.id]
    );
    const catalogById = new Map(CATALOG.map((item) => [item.id, item]));

    const items = rows.map((r) => {
      const meta = catalogById.get(r.id) || {
        name: r.id,
        description: '',
        category: 'SKIN',
      };
      return {
        id: r.id,
        owned: true,
        equipped: r.equipped,
        ...meta,
      };
    });
    res.json(items);
  } catch (err) {
    next(err);
  }
}

/** POST /v1/inventory/equip { itemId } */
async function equipItem(req, res, next) {
  try {
    const { itemId } = req.body || {};
    if (!itemId) {
      return res.status(400).json({ code: 400, message: 'itemId required' });
    }
    const { rowCount } = await query(
      `UPDATE inventory SET equipped = true
       WHERE user_id = $1 AND item_id = $2 AND EXISTS (
         SELECT 1 FROM users WHERE id = $1
       )`,
      [req.user.id, itemId]
    );
    if (rowCount === 0) {
      return res.status(404).json({ code: 404, message: 'Item not owned' });
    }
    await query(
      `UPDATE inventory SET equipped = false
       WHERE user_id = $1 AND item_id <> $2 AND equipped = true`,
      [req.user.id, itemId]
    );
    res.json({ ok: true });
  } catch (err) {
    next(err);
  }
}

/** POST /v1/shop/buy/coins { itemId } — coin-only purchases. */
async function buyWithCoins(req, res, next) {
  try {
    const { itemId } = req.body || {};
    const item = CATALOG.find((i) => i.id === itemId);
    if (!item || item.priceCoins <= 0) {
      return res.status(400).json({ code: 400, message: 'Not purchasable with coins' });
    }

    const { rows } = await query(
      `UPDATE users SET coins = coins - $2
       WHERE id = $1 AND coins >= $2
       RETURNING coins`,
      [req.user.id, item.priceCoins]
    );
    if (rows.length === 0) {
      return res.status(402).json({ code: 402, message: 'Not enough coins' });
    }

    await query(
      `INSERT INTO inventory (user_id, item_id, equipped)
       VALUES ($1, $2, false) ON CONFLICT DO NOTHING`,
      [req.user.id, itemId]
    );

    const profile = await query(
      'SELECT id, username, coins, gems FROM users WHERE id = $1',
      [req.user.id]
    );
    res.json(profile.rows[0]);
  } catch (err) {
    next(err);
  }
}

module.exports = { getShop, getInventory, equipItem, buyWithCoins };