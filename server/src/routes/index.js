'use strict';

const router = require('express').Router();

const { authRequired } = require('../middleware/auth.middleware');
const authController = require('../controllers/auth.controller');
const profileController = require('../controllers/profile.controller');
const leaderboardController = require('../controllers/leaderboard.controller');
const friendsController = require('../controllers/friends.controller');
const shopController = require('../controllers/shop.controller');
const matchController = require('../controllers/match.controller');
const dailyRewardController = require('../controllers/dailyReward.controller');
const purchaseController = require('../controllers/purchase.controller');

// Health
router.get('/health', (req, res) => res.json({ ok: true, service: 'territory-wars' }));

// Auth (no JWT required)
router.post('/auth/login/guest', authController.loginGuest);
router.post('/auth/login/google', authController.loginGoogle);

// Everything below requires a valid JWT
router.use(authRequired);

// Profile
router.get('/profile', profileController.getProfile);
router.post('/profile', profileController.updateProfile);

// Leaderboard
router.get('/leaderboard', leaderboardController.getLeaderboard);

// Friends
router.get('/friends', friendsController.getFriends);
router.post('/friends/request', friendsController.sendRequest);
router.post('/friends/accept', friendsController.acceptRequest);
router.delete('/friends/:userId', friendsController.removeFriend);

// Shop & Inventory
router.get('/shop', shopController.getShop);
router.get('/inventory', shopController.getInventory);
router.post('/inventory/equip', shopController.equipItem);
router.post('/shop/buy/coins', shopController.buyWithCoins);

// Matches
router.post('/match/report', matchController.reportMatch);
router.get('/match/history', matchController.getMatchHistory);

// Daily rewards
router.post('/daily-reward/claim', dailyRewardController.claimDailyReward);

// Purchases
router.post('/purchase/verify', purchaseController.verifyPurchase);

// Cloud save (thin passthroughs)
router.get('/cloud-save', async (req, res) =>
  res.json({ state: 'ok', profile: req.user, isPremium: req.user.is_premium }));
router.post('/cloud-save', async (req, res) => res.json({ ok: true }));

module.exports = router;