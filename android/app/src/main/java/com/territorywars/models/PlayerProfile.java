package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

/**
 * Player profile returned by the backend.
 */
public class PlayerProfile {

    @SerializedName("id")
    public String id;

    @SerializedName("username")
    public String username;

    @SerializedName("avatar")
    public String avatarId;

    @SerializedName("level")
    public int level;

    @SerializedName("xp")
    public int xp;

    @SerializedName("xpToNext")
    public int xpToNext;

    @SerializedName("coins")
    public long coins;

    @SerializedName("gems")
    public long gems;

    @SerializedName("rank")
    public int rank;

    @SerializedName("trophies")
    public int trophies;

    @SerializedName("isPremium")
    public boolean isPremium;

    @SerializedName("isGuest")
    public boolean isGuest;

    @SerializedName("matchesPlayed")
    public int matchesPlayed;

    @SerializedName("matchesWon")
    public int matchesWon;

    @SerializedName("tilesCaptured")
    public long tilesCaptured;

    @SerializedName("totalScore")
    public long totalScore;
}