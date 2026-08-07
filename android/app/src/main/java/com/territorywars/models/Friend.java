package com.territorywars.models;

import com.google.gson.annotations.SerializedName;

/**
 * Friend list entry.
 */
public class Friend {

    @SerializedName("userId")
    public String userId;

    @SerializedName("username")
    public String username;

    @SerializedName("avatarId")
    public String avatarId;

    @SerializedName("status")
    public String status; // online | in_match | offline

    @SerializedName("level")
    public int level;

    @SerializedName("isRequest")
    public boolean isRequest; // incoming pending request
}