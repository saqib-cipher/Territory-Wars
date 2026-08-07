package com.territorywars.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecentMatchDao {
    @Query("SELECT * FROM recent_matches ORDER BY timestamp DESC LIMIT 20")
    List<RecentMatchEntity> getRecentMatches();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMatch(RecentMatchEntity match);
}
