package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

/**
 * Catalog item sold in the Shop / owned in Inventory.
 */
public class ShopItem {

    public enum Category {
        SKIN, TRAIL, EFFECT, EMOTE, TITLE, COINS, GEMS, BUNDLE, SUBSCRIPTION
    }

    @SerializedName("id")
    public String id;

    @SerializedName("name")
    public String name;

    @SerializedName("description")
    public String description;

    @SerializedName("category")
    public String category;

    @SerializedName("priceCoins")
    public long priceCoins;

    @SerializedName("priceGems")
    public long priceGems;

    @SerializedName("owned")
    public boolean owned;

    @SerializedName("equipped")
    public boolean equipped;

    public Category getCategory() {
        try {
            return Category.valueOf(category == null ? "SKIN" : category.toUpperCase());
        } catch (Exception e) {
            return Category.SKIN;
        }
    }
}