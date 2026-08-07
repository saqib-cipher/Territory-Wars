package com.territorywars.ads;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdOptions;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.territorywars.R;
import com.territorywars.network.PreferenceManager;

/**
 * Centralised ad manager implementing the monetisation spec:
 *
 * <ul>
 *   <li>Banner — Home</li>
 *   <li>Interstitial — after every 3 completed matches, never during gameplay</li>
 *   <li>Rewarded — double coins, extra daily reward, continue after defeat</li>
 *   <li>App open — app launch</li>
 *   <li>Native — news feed, store, results screen</li>
 *   <li>Remove Ads / Premium removes all ads</li>
 * </ul>
 *
 * <p>Uses the official AdMob test ad units by default; swap for your real IDs
 * before release.</p>
 */
public class AdManager {

    private static final String TAG = "AdManager";

    // Test ad units (production: replace via AdManager ids below)
    public static final String TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    public static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    public static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    public static final String TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921";
    public static final String TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110";

    private final PreferenceManager preferences;

    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private AppOpenAd appOpenAd;
    private boolean appOpenShown;

    public AdManager(Context context) {
        this.preferences = new PreferenceManager(context.getApplicationContext());
    }

    // ---- Visibility rules ----

    /** Ads are hidden entirely for premium / remove-ads users. */
    public boolean shouldShowAds() {
        return !preferences.isAdsRemoved() && !preferences.isPremiumActive();
    }

    /**
     * Interstitials must never interrupt gameplay. Call after a match ends and
     * only when a multiple of 3 matches has been completed.
     */
    public boolean shouldShowInterstitial(int matchesPlayedTotal) {
        return shouldShowAds() && matchesPlayedTotal > 0 && matchesPlayedTotal % 3 == 0;
    }

    // ---- Banner ----

    public void loadBanner(Activity activity, ViewGroup container) {
        if (!shouldShowAds()) {
            container.setVisibility(View.GONE);
            return;
        }
        AdView adView = new AdView(activity);
        adView.setAdUnitId(TEST_BANNER);
        adView.setAdSize(AdSize.BANNER);
        adView.loadAd(new AdRequest.Builder().build());
        container.setVisibility(View.VISIBLE);
        container.removeAllViews();
        container.addView(adView);
    }

    // ---- Interstitial ----

    public void loadInterstitial(Activity activity) {
        if (!shouldShowAds()) return;
        InterstitialAd.load(activity, TEST_INTERSTITIAL, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitial = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        Log.w(TAG, "interstitial load failed: " + loadAdError.getMessage());
                    }
                });
    }

    /** Shows the loaded interstitial; the caller decides cadence. */
    public void showInterstitial(Activity activity, @Nullable Runnable onDismissed) {
        if (interstitial == null) {
            if (onDismissed != null) onDismissed.run();
            loadInterstitial(activity);
            return;
        }
        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                interstitial = null;
                if (onDismissed != null) onDismissed.run();
                loadInterstitial(activity);
            }
        });
        interstitial.show(activity);
    }

    // ---- Rewarded ----

    public void loadRewarded(Activity activity) {
        if (!shouldShowAds()) return;
        RewardedAd.load(activity, TEST_REWARDED, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        rewarded = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        Log.w(TAG, "rewarded load failed: " + loadAdError.getMessage());
                    }
                });
    }

    /** Shows a rewarded ad; grants the reward only if the user watched it fully. */
    public void showRewarded(Activity activity, Runnable onRewardEarned) {
        if (rewarded == null) {
            loadRewarded(activity);
            return;
        }
        rewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                rewarded = null;
                loadRewarded(activity);
            }
        });
        rewarded.show(activity, rewardItem -> onRewardEarned.run());
    }

    // ---- App open ----

    public void loadAppOpen(Activity activity) {
        if (!shouldShowAds() || appOpenShown) return;
        AppOpenAd.load(activity, TEST_APP_OPEN, new AdRequest.Builder().build(),
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                new AppOpenAd.AppOpenAdLoadCallback() {
                    @Override
                    public void onAdLoaded(AppOpenAd ad) {
                        appOpenAd = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        Log.w(TAG, "app open load failed: " + loadAdError.getMessage());
                    }
                });
    }

    /** Shows the pre-loaded app-open ad once per launch. */
    public void showAppOpenIfAvailable(Activity activity) {
        if (appOpenAd == null) return;
        appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                appOpenAd = null;
                appOpenShown = true;
            }
        });
        appOpenAd.show(activity);
    }
}