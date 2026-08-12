package glab.guesscard.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import glab.guesscard.R;
import glab.guesscard.fragments.GameFragment;
import glab.guesscard.models.GameMode;

public class GameActivity extends BaseActivity {
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_ROOM_ID = "extra_room_id";
    public static final String EXTRA_DURATION = "extra_duration";

    public static Intent intent(Context context, GameMode mode, String roomId, long durationMillis) {
        Intent intent = new Intent(context, GameActivity.class);
        intent.putExtra(EXTRA_MODE, mode.name());
        intent.putExtra(EXTRA_ROOM_ID, roomId);
        intent.putExtra(EXTRA_DURATION, durationMillis);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new GameFragment())
                    .commit();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (container() != null && container().getAudio() != null) {
            container().getAudio().stopAllSounds();
        }
    }

    @Override
    public void onBackPressed() {
        if (container() != null && container().getAudio() != null) {
            container().getAudio().stopAllSounds();
        }
        super.onBackPressed();
        returnToMain();
    }

    public void returnToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
