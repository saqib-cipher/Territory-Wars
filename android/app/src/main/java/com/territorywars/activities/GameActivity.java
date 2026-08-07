package com.territorywars.activities;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import com.territorywars.R;
import com.territorywars.models.GameMode;
/**
* Hosts the GameFragment (game view + HUD). Kept separate from Main so the
* game can run in its own landscape activity.
*/
public class GameActivity extends BaseActivity {
public static final String EXTRA_MODE = "extra_mode";
public static final String EXTRA_MAP_ID = "extra_map_id";
public static final String EXTRA_DURATION = "extra_duration";
public static Intent intent(Context context, GameMode mode, String mapId, long durationMillis) {
Intent intent = new Intent(context, GameActivity.class);
intent.putExtra(EXTRA_MODE, mode.name());
intent.putExtra(EXTRA_MAP_ID, mapId);
intent.putExtra(EXTRA_DURATION, durationMillis);
return intent;
}
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
super.onCreate(savedInstanceState);
setContentView(R.layout.activity_game);
}
}
