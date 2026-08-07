package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

/**
 * A single row of a leaderboard.
 */
public class LeaderboardEntry {

    @SerializedName("rank")
    public int rank;

    @SerializedName("userId")
    public String userId;

    @SerializedName("username")
    public String username;

    @SerializedName("avatarId")
    public String avatarId;

    @SerializedName("trophies")
    public int trophies;

    @SerializedName("matchesWon")
    public int matchesWon;

    @SerializedName("isFriend")
    public boolean isFriend;

    @SerializedName("isSelf")
    public boolean isSelf;
}