package com.territorywars.models;
import com.google.gson.annotations.SerializedName;
/**
* Lightweight result of a finished match, used on Home and in history.
*/
public class MatchResult {
@SerializedName("matchId")
public String matchId;
@SerializedName("mode")
public String mode;
@SerializedName("won")
public boolean won;
@SerializedName("rank")
public int rank;
@SerializedName("score")
public int score;
@SerializedName("tilesCaptured")
public int tilesCaptured;
@SerializedName("xpEarned")
public int xpEarned;
@SerializedName("coinsEarned")
public long coinsEarned;
@SerializedName("durationSeconds")
public int durationSeconds;
@SerializedName("playedAt")
public long playedAtEpochMillis;
}
