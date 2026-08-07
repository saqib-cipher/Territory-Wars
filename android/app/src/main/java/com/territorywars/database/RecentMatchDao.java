package com.territorywars.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/** DAO for locally cached match history. */
@Dao
public interface RecentMatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecentMatchEntity match);

    @Query("SELECT * FROM recent_matches ORDER BY played_at DESC LIMIT :limit")
    List<RecentMatchEntity> getRecent(int limit);

    @Query("DELETE FROM recent_matches")
    void clear();
}