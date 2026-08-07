package com.territorywars.billing;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates Google Play Billing (v7/v8 API surface).
 *
 * <p>Purchase flow: query product details → launch billing flow → listen for
 * the purchase update → ask the backend to verify the token → acknowledge or
 * consume the purchase.</p>
 */
public class BillingManager {

    private static final String TAG = "BillingManager";

    /** Product IDs — replace with your Play Console IDs. */
    public static final String SKU_PREMIUM_MONTHLY = "premium_monthly";
    public static final String SKU_PREMIUM_YEARLY = "premium_yearly";
    public static final String SKU_REMOVE_ADS = "remove_ads";
    public static final String SKU_COINS_1000 = "coins_1000";
    public static final String SKU_GEMS_100 = "gems_100";

    public static final List<String> ALL_SKUS = Arrays.asList(
            SKU_PREMIUM_MONTHLY, SKU_PREMIUM_YEARLY, SKU_REMOVE_ADS,
            SKU_COINS_1000, SKU_GEMS_100);

    /** Notified when the purchased-product list changes. */
    public interface OnPurchasesChanged {
        void onPurchasesUpdated(List<Purchase> purchases);

        void onBillingError(String message);
    }

    /** Verifies a purchase token against the backend. */
    public interface PurchaseVerifier {
        void verify(String productId, String purchaseToken, PurchaseVerifierCallback callback);
    }

    public interface PurchaseVerifierCallback {
        void onVerified(boolean valid);
    }

    private final Context context;
    private final BillingClient billingClient;
    private final Map<String, ProductDetails> productDetails = new ConcurrentHashMap<>();
    private final PurchaseVerifier purchaseVerifier;

    private OnPurchasesChanged listener;
    private boolean connecting;

    public BillingManager(Context context) {
        this(context, (productId, purchaseToken, callback) -> callback.onVerified(true));
    }

    public BillingManager(Context context, PurchaseVerifier purchaseVerifier) {
        this.context = context.getApplicationContext();
        this.purchaseVerifier = purchaseVerifier;

        PurchasesUpdatedListener purchasesUpdatedListener = (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                if (listener != null) listener.onBillingError(billingResult.getDebugMessage());
                return;
            }
            if (purchases != null) {
                handlePurchases(purchases);
                if (listener != null) listener.onPurchasesUpdated(purchases);
            }
        };

        this.billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases()
                .build();
    }

    /** Registers a listener for purchase updates, then connects. */
    public void connect(OnPurchasesChanged listener) {
        this.listener = listener;
        if (billingClient.isReady() || connecting) return;
        connecting = true;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                connecting = false;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails();
                    queryOwned();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connecting = false;
            }
        });
    }

    /** Fetches the catalog (prices, titles). */
    public void queryProductDetails() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (String sku : ALL_SKUS) {
            boolean sub = sku.startsWith("premium_");
            products.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(sub ? BillingClient.ProductType.SUBS
                            : BillingClient.ProductType.INAPP)
                    .build());
        }
        billingClient.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(products).build(),
                (billingResult, list) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        productDetails.clear();
                        for (ProductDetails d : list) productDetails.put(d.getProductId(), d);
                    }
                });
    }

    /** Queries owned purchases (restore-purchases flow). */
    public void queryOwned() {
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                (billingResult, purchases) -> {
                    if (listener != null) listener.onPurchasesUpdated(purchases);
                });
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                (billingResult, purchases) -> {
                    if (listener != null) listener.onPurchasesUpdated(purchases);
                });
    }

    /** Returns cached localised price for a SKU, or null. */
    @Nullable
    public String getPrice(String productId) {
        ProductDetails d = productDetails.get(productId);
        if (d == null) return null;
        ProductDetails.OneTimePurchaseOfferDetails oneTime = d.getOneTimePurchaseOfferDetails();
        if (oneTime != null) return oneTime.getFormattedPrice();
        if (d.getSubscriptionOfferDetails() != null && !d.getSubscriptionOfferDetails().isEmpty()) {
            return d.getSubscriptionOfferDetails().get(0).getPricingPhases().getPricingPhaseList()
                    .get(0).getFormattedPrice();
        }
        return null;
    }

    /** Launches the purchase UI for a product. */
    public void launchPurchase(Activity activity, String productId) {
        ProductDetails details = productDetails.get(productId);
        if (details == null) {
            queryProductDetails();
            return;
        }
        BillingFlowParams.ProductDetailsParams productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken(details))
                        .build();
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(productParams))
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, params);
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK && listener != null) {
            listener.onBillingError(result.getDebugMessage());
        }
    }

    @Nullable
    private String offerToken(ProductDetails details) {
        if (!BillingClient.ProductType.SUBS.equals(details.getProductType())) return null;
        if (details.getSubscriptionOfferDetails() == null
                || details.getSubscriptionOfferDetails().isEmpty()) return null;
        return details.getSubscriptionOfferDetails().get(0).getOfferToken();
    }

    /**
     * Handles newly reported purchases: verify with the server, then consume
     * consumables and acknowledge everything else.
     */
    private void handlePurchases(List<Purchase> purchases) {
        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            if (purchase.getProducts().isEmpty()) continue;
            String productId = purchase.getProducts().get(0);
            String token = purchase.getPurchaseToken();

            purchaseVerifier.verify(productId, token, valid -> {
                if (!valid) {
                    Log.w(TAG, "server rejected purchase " + productId);
                    return;
                }
                if (isConsumable(productId)) consume(productId, token);
                else acknowledge(productId, token);
            });
        }
    }

    private boolean isConsumable(String productId) {
        return productId.startsWith("coins_") || productId.startsWith("gems_");
    }

    private void acknowledge(String productId, String token) {
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(token)
                .build();
        billingClient.acknowledgePurchase(params, (billingResult, purchaseToken) -> Log.d(TAG,
                "acknowledged " + productId + " -> " + billingResult.getResponseCode()));
    }

    private void consume(String productId, String token) {
        ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(token).build();
        billingClient.consumeAsync(params, (billingResult, purchaseToken) -> Log.d(TAG,
                "consumed " + productId + " -> " + billingResult.getResponseCode()));
    }

    public void destroy() {
        billingClient.endConnection();
    }
}