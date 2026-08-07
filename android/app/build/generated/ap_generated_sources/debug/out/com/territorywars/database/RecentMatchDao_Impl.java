package com.territorywars.database;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class RecentMatchDao_Impl implements RecentMatchDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<RecentMatchEntity> __insertionAdapterOfRecentMatchEntity;

  public RecentMatchDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfRecentMatchEntity = new EntityInsertionAdapter<RecentMatchEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `recent_matches` (`matchId`,`timestamp`,`score`,`victory`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final RecentMatchEntity entity) {
        if (entity.matchId == null) {
          statement.bindNull(1);
        } else {
          statement.bindString(1, entity.matchId);
        }
        statement.bindLong(2, entity.timestamp);
        statement.bindLong(3, entity.score);
        final int _tmp = entity.victory ? 1 : 0;
        statement.bindLong(4, _tmp);
      }
    };
  }

  @Override
  public void insertMatch(final RecentMatchEntity match) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __insertionAdapterOfRecentMatchEntity.insert(match);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public List<RecentMatchEntity> getRecentMatches() {
    final String _sql = "SELECT * FROM recent_matches ORDER BY timestamp DESC LIMIT 20";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfMatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "matchId");
      final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
      final int _cursorIndexOfScore = CursorUtil.getColumnIndexOrThrow(_cursor, "score");
      final int _cursorIndexOfVictory = CursorUtil.getColumnIndexOrThrow(_cursor, "victory");
      final List<RecentMatchEntity> _result = new ArrayList<RecentMatchEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final RecentMatchEntity _item;
        _item = new RecentMatchEntity();
        if (_cursor.isNull(_cursorIndexOfMatchId)) {
          _item.matchId = null;
        } else {
          _item.matchId = _cursor.getString(_cursorIndexOfMatchId);
        }
        _item.timestamp = _cursor.getLong(_cursorIndexOfTimestamp);
        _item.score = _cursor.getInt(_cursorIndexOfScore);
        final int _tmp;
        _tmp = _cursor.getInt(_cursorIndexOfVictory);
        _item.victory = _tmp != 0;
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
