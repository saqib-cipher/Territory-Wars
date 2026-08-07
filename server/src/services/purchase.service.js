'use strict';

const config = require('../config');

/**
 * Play Developer API entitlement map: productId -> what the user receives.
 * Client product ids must match these exactly.
 */
const ENTITLEMENTS = {
  coins_1000: { coins: 1000 },
  gems_100: { gems: 100 },
  remove_ads: { adsRemoved: true },
  premium_monthly: { premiumDays: 30 },
  premium_yearly: { premiumDays: 365 },
};

/**
 * Verifies a Google Play purchase token.
 *
 * Production: uses the Play Developer API service account (needs the
 * `google-play-developer-api` client + GOOGLE_SERVICE_ACCOUNT_FILE).
 * Development: when no service account is configured, accepts the token
 * without a round trip so local billing flows can be exercised.
 *
 * Returns { purchaseState, purchaseToken, productId } or { purchaseState: -1 }.
 */
async function verifyPurchase({ productId, purchaseToken, subscription }) {
  if (!config.play.serviceAccountFile) {
    console.warn('[purchase] no GOOGLE_SERVICE_ACCOUNT_FILE - accepting token without verification');
    return {
      purchaseState: 0,
      productId,
      purchaseToken,
      purchaseTimeMillis: Date.now(),
      isSubscription: !!subscription,
    };
  }

  throw new Error(
    'Play Developer API verification requires the google-play-developer-api dependency ' +
    'and a service account; wire it here.'
  );
}

/** Maps a product id to its entitlement (null when unknown). */
function entitlementFor(productId) {
  return ENTITLEMENTS[productId] || null;
}

module.exports = { verifyPurchase, entitlementFor, ENTITLEMENTS };