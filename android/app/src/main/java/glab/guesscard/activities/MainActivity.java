package glab.guesscard.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import glab.guesscard.R;

/**
 * Shell activity hosting the NavHostFragment with a bottom navigation bar.
 */
public class MainActivity extends BaseActivity {

    private NavController navController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavHostFragment navHost = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        if (navHost != null && bottomNav != null) {
            navController = navHost.getNavController();
            NavigationUI.setupWithNavController(bottomNav, navController);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkActiveRoomAndRejoin();
    }

    private void checkActiveRoomAndRejoin() {
        String myUid = prefs() != null ? prefs().getUserId() : null;
        if (myUid == null || myUid.isEmpty()) return;

        if (container() == null || container().getFirebaseManager() == null) return;

        container().getFirebaseManager().getDatabaseRef().child("rooms")
                .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        for (com.google.firebase.database.DataSnapshot roomSnap : snapshot.getChildren()) {
                            String status = roomSnap.child("status").getValue(String.class);
                            if ("CLOSED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) continue;

                            if (roomSnap.child("players").child(myUid).exists()) {
                                String rId = roomSnap.getKey();
                                String code = roomSnap.child("code").getValue(String.class);
                                String mode = roomSnap.child("mode").getValue(String.class);
                                String ansUid = roomSnap.child("answererUid").getValue(String.class);

                                if ("PLAYING".equalsIgnoreCase(status) || "IN_PROGRESS".equalsIgnoreCase(status)) {
                                    Intent intent = GameActivity.intent(MainActivity.this, 
                                            glab.guesscard.models.GameMode.valueOf(mode != null ? mode : "ANIMALS"), 
                                            rId, 60000L, ansUid);
                                    startActivity(intent);
                                    break;
                                } else if ("WAITING".equalsIgnoreCase(status)) {
                                    Intent intent = new Intent(MainActivity.this, LobbyActivity.class);
                                    intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, rId);
                                    if (code != null) intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                                    if (mode != null) intent.putExtra(LobbyActivity.EXTRA_MODE, mode);
                                    startActivity(intent);
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
                });
    }

    @Override
    protected void applyEdgeToEdgeInsets() {
        View root = findViewById(android.R.id.content);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        if (root != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, 0);

                if (bottomNav != null) {
                    bottomNav.setPadding(
                            bottomNav.getPaddingLeft(),
                            bottomNav.getPaddingTop(),
                            bottomNav.getPaddingRight(),
                            insets.bottom
                    );
                }
                return windowInsets;
            });
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (navController != null) {
            return navController.navigateUp() || super.onSupportNavigateUp();
        }
        return super.onSupportNavigateUp();
    }
}
