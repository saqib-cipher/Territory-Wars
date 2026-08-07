'use strict';

const fs = require('fs');
const path = require('path');
const { JWT } = require('google-auth-library');
const config = require('../config');

let cachedClient = null;

/**
 * Returns an authenticated API client for the Google Play Developer API.
 * Falls back to a stub client when the service account file is missing
 * (development mode only — purchases are treated as valid).
 */
function getPlayClient() {
  if (cachedClient) return cachedClient;

  const file = config.google.serviceAccountFile;
  if (file && fs.existsSync(path.resolve(file))) {
    const credentials = JSON.parse(fs.readFileSync(file, 'utf8'));
    cachedClient = new JWT({
      email: credentials.client_email,
      key: credentials.private_key,
      scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    return cachedClient;
  }

  if (!config.isProd()) {
    // dev stub: always succeeds so the whole loop is testable locally
    cachedClient = {
      isStub: true,
      request: async ({ url }) => {
        console.warn('[dev] Play verification stub used for', url);
        return { data: { purchaseState: 0, acknowledgementState: 1 } };
      },
    };
    return cachedClient;
  }

  throw new Error('GOOGLE_SERVICE_ACCOUNT_FILE is required in production');
}

/**
 * Verifies a purchase token with Google Play.
 *
 * @returns {Promise<{purchaseState:number, acknowledged:boolean}>}
 */
async function verifyPurchase({ productId, purchaseToken, subscription }) {
  const client = getPlayClient();
  const kind = subscription ? 'subscriptions' : 'products';
  const url =
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
    `${config.play.packageName}/purchases/${kind}/${productId}/tokens/${purchaseToken}`;

  const { data } = await client.request({ url });
  return {
    purchaseState: data.purchaseState ?? 0, // 0 = purchased
    acknowledged: (data.acknowledgementState ?? 0) === 1,
  };
}

/** Grants the entitlement corresponding to a product id. */
const ENTITLEMENTS = {
  coins_1000: { coins: 1000 },
  gems_100: { gems: 100 },
  remove_ads: { adsRemoved: true },
  premium_monthly: { premiumDays: 30 },
  premium_yearly: { premiumDays: 365 },
};

function entitlementFor(productId) {
  return ENTITLEMENTS[productId] || null;
}

module.exports = { verifyPurchase, entitlementFor, getPlayClient };