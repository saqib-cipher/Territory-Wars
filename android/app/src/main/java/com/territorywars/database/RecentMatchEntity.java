package com.territorywars.database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Locally cached recent match for the Home screen. Cloud save remains
 * authoritative; this cache powers offline-friendly history.
 */
@Entity(tableName = "recent_matches")
public class RecentMatchEntity {

    @PrimaryKey
    @ColumnInfo(name = "match_id")
    public String matchId;

    @ColumnInfo(name = "mode")
    public String mode;

    @ColumnInfo(name = "won")
    public boolean won;

    @ColumnInfo(name = "rank")
    public int rank;

    @ColumnInfo(name = "score")
    public int score;

    @ColumnInfo(name = "xp_earned")
    public int xpEarned;

    @ColumnInfo(name = "coins_earned")
    public long coinsEarned;

    @ColumnInfo(name = "played_at")
    public long playedAt;
}