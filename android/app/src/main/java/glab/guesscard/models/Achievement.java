package glab.guesscard.models;

import com.google.gson.annotations.SerializedName;

/**
 * Unlockable achievement.
 */
public class Achievement {

    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("icon")
    public String icon;

    @SerializedName("progress")
    public int progress;

    @SerializedName("target")
    public int target;

    @SerializedName("unlocked")
    public boolean unlocked;

    @SerializedName("rewardCoins")
    public long rewardCoins;
}