'use strict';

const { query, withTransaction } = require('../database');
const config = require('../config');
const purchaseService = require('../services/purchase.service');

/**
 * POST /v1/purchase/verify
 * { productId, purchaseToken, packageName, subscription }
 *
 * 1. Verify the token with the Play Developer API.
 * 2. Persist the purchase (dedupe by token).
 * 3. Grant the entitlement and persist for cloud restore.
 */
async function verifyPurchase(req, res, next) {
  try {
    const { productId, purchaseToken, packageName, subscription } = req.body || {};

    if (!productId || !purchaseToken) {
      return res.status(400).json({ code: 400, message: 'productId and purchaseToken required' });
    }
    if (packageName && packageName !== config.play.packageName) {
      return res.status(400).json({ code: 400, message: 'package mismatch' });
    }

    const verdict = await purchaseService.verifyPurchase({ productId, purchaseToken, subscription });
    if (verdict.purchaseState !== 0) {
      return res.status(402).json({ code: 402, message: 'Purchase not completed' });
    }

    const entitlement = purchaseService.entitlementFor(productId);
    if (!entitlement) {
      return res.status(400).json({ code: 400, message: 'Unknown product' });
    }

    const granted = await withTransaction(async (client) => {
      // dedupe: same token cannot be applied twice
      const dup = await client.query(
        'SELECT 1 FROM purchases WHERE purchase_token = $1', [purchaseToken]
      );
      if (dup.rows.length > 0) {
        return { already: true };
      }

      await client.query(
        `INSERT INTO purchases (user_id, product_id, purchase_token, package_name, acknowledged)
         VALUES ($1, $2, $3, $4, true)`,
        [req.user.id, productId, purchaseToken, packageName || config.play.packageName]
      );

      if (entitlement.coins) {
        await client.query('UPDATE users SET coins = coins + $2 WHERE id = $1',
          [req.user.id, entitlement.coins]);
      }
      if (entitlement.gems) {
        await client.query('UPDATE users SET gems = gems + $2 WHERE id = $1',
          [req.user.id, entitlement.gems]);
      }
      if (entitlement.premiumDays) {
        await client.query(
          `UPDATE users
           SET is_premium = true,
               premium_until = GREATEST(coalesce(premium_until, now()), now()) + $2::interval
           WHERE id = $1`,
          [req.user.id, `${entitlement.premiumDays} days`]
        );
      }
      if (entitlement.adsRemoved) {
        await client.query('UPDATE users SET is_premium = true WHERE id = $1', [req.user.id]);
      }
      return { already: false };
    });

    res.json({
      ok: true,
      alreadyProcessed: granted.already,
      granted: productId,
      acknowledged: true,
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { verifyPurchase };