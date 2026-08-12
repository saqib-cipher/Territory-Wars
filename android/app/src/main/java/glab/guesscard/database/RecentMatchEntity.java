package glab.guesscard.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recent_matches")
public class RecentMatchEntity {
    @PrimaryKey
    @NonNull
    public String matchId = "";

    public long timestamp;
    public int score;
    public boolean victory;
}
