package glab.guesscard.models;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
* Full match result with standings, winner, and round history.
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

    // Multiplayer standings
    public StandingsEntry winner;
    public List<StandingsEntry> standings = new ArrayList<>();

    public static class StandingsEntry {
        public String userId;
        public String username;
        public String avatarId;
        public int score;
        public boolean isHost;
    }
}
