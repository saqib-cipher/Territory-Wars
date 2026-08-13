package glab.guesscard.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;

public class FriendsActivity extends BaseActivity {

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private EditText etSearch;
    private RecyclerView rvFriends;
    private RecyclerView rvSearch;
    private TextView tvNoFriends;
    private TextView tvSearchHeader;
    private FirebaseManager firebaseManager;
    private String currentUid;
    private final Set<String> friendUids = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        firebaseManager = container().getFirebaseManager();
        currentUid = prefs().getUserId();

        etSearch = findViewById(R.id.etSearchFriends);
        rvFriends = findViewById(R.id.rvFriendsList);
        rvSearch = findViewById(R.id.rvSearchResults);
        tvNoFriends = findViewById(R.id.tvNoFriends);
        tvSearchHeader = findViewById(R.id.tvSearchResultsHeader);

        ModernFButton btnClose = findViewById(R.id.btnCloseFriends);
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        ModernFButton btnSearch = findViewById(R.id.btnSearch);
        if (btnSearch != null) btnSearch.setOnClickListener(v -> debounceSearch(0L));

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    debounceSearch(SEARCH_DEBOUNCE_MS);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (rvFriends != null) rvFriends.setLayoutManager(new LinearLayoutManager(this));
        if (rvSearch != null) rvSearch.setLayoutManager(new LinearLayoutManager(this));

        loadFriends();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
    }

    private void debounceSearch(long delayMs) {
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
        searchRunnable = this::performSearch;
        mainHandler.postDelayed(searchRunnable, delayMs);
    }

    private void loadFriends() {
        if (currentUid == null) return;
        firebaseManager.getFriends(currentUid, list -> runOnUiThread(() -> {
            friendUids.clear();
            if (list != null) {
                for (Map<String, Object> f : list) {
                    Object uid = f.get("uid");
                    if (uid != null) friendUids.add(String.valueOf(uid));
                }
            }
            if (list == null || list.isEmpty()) {
                if (tvNoFriends != null) tvNoFriends.setVisibility(View.VISIBLE);
                if (rvFriends != null) rvFriends.setVisibility(View.GONE);
            } else {
                if (tvNoFriends != null) tvNoFriends.setVisibility(View.GONE);
                if (rvFriends != null) {
                    rvFriends.setVisibility(View.VISIBLE);
                    rvFriends.setAdapter(new FriendsAdapter(list, true));
                }
            }
        }));
    }

    private void performSearch() {
        if (etSearch == null) return;
        String query = etSearch.getText().toString().trim();
        View tvMyFriendsHeader = findViewById(R.id.tvMyFriendsHeader);
        View tvNoFriendsView = findViewById(R.id.tvNoFriends);

        if (query.isEmpty()) {
            // Restore My Friends list
            if (rvSearch != null) rvSearch.setVisibility(View.GONE);
            if (tvSearchHeader != null) tvSearchHeader.setVisibility(View.GONE);
            if (tvMyFriendsHeader != null) tvMyFriendsHeader.setVisibility(View.VISIBLE);
            loadFriends();
            return;
        }

        // Hide My Friends list entirely while searching, show search results instead
        if (tvMyFriendsHeader != null) tvMyFriendsHeader.setVisibility(View.GONE);
        if (tvNoFriendsView != null) tvNoFriendsView.setVisibility(View.GONE);
        if (rvFriends != null) rvFriends.setVisibility(View.GONE);
        if (tvSearchHeader != null) {
            tvSearchHeader.setVisibility(View.VISIBLE);
            tvSearchHeader.setText("Searching for: '" + query + "'");
        }
        if (rvSearch != null) rvSearch.setVisibility(View.GONE);

        firebaseManager.searchUsers(currentUid, query, list -> runOnUiThread(() -> {
            if (isFinishing()) return;
            if (list == null || list.isEmpty()) {
                if (rvSearch != null) rvSearch.setVisibility(View.GONE);
                if (tvSearchHeader != null) {
                    tvSearchHeader.setVisibility(View.VISIBLE);
                    tvSearchHeader.setText("No users found matching: '" + query + "'");
                }
            } else {
                if (tvSearchHeader != null) {
                    tvSearchHeader.setVisibility(View.VISIBLE);
                    tvSearchHeader.setText("Search Results (" + list.size() + " found)");
                }
                if (rvSearch != null) {
                    rvSearch.setVisibility(View.VISIBLE);
                    rvSearch.setAdapter(new FriendsAdapter(list, false));
                }
            }
        }));
    }

    // ── ADAPTER ─────────────────────────────────────────────────────────────

    private class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {
        private final List<Map<String, Object>> items;
        private final boolean isMyFriendsList;

        FriendsAdapter(List<Map<String, Object>> items, boolean isMyFriendsList) {
            this.items = items;
            this.isMyFriendsList = isMyFriendsList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = items.get(position);
            String friendUid = (String) item.getOrDefault("uid", "");
            String name = (String) item.getOrDefault("displayName", "Player");
            String status = (String) item.getOrDefault("status", "Online");

            holder.tvName.setText(name);
            holder.tvStatus.setText("UID: " + friendUid + " | " + status);
            boolean alreadyFriend = currentUid != null && friendUids.contains(friendUid);

            if (isMyFriendsList) {
                holder.btnAction.setText("Remove");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setAlpha(1.0f);
            } else if (alreadyFriend) {
                holder.btnAction.setText("Added");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setAlpha(0.4f);
            } else {
                holder.btnAction.setText("Add");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setAlpha(1.0f);
            }

            holder.btnAction.setOnClickListener(v -> {
                if (friendUid == null || friendUid.isEmpty() || currentUid == null) return;
                if (isMyFriendsList) {
                    firebaseManager.removeFriend(currentUid, friendUid);
                    friendUids.remove(friendUid);
                    Toast.makeText(FriendsActivity.this, "Removed friend: " + name, Toast.LENGTH_SHORT).show();
                } else if (!friendUids.contains(friendUid)) {
                    firebaseManager.addFriend(currentUid, friendUid);
                    friendUids.add(friendUid);
                    Toast.makeText(FriendsActivity.this, "Added friend: " + name, Toast.LENGTH_SHORT).show();
                }
                // Refresh the visible list (friends list if not searching, results otherwise)
                performSearch();
            });

            holder.itemView.setOnClickListener(v -> {
                if (friendUid != null && !friendUid.isEmpty()) {
                    startActivity(PublicProfileActivity.intent(FriendsActivity.this, friendUid));
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvStatus;
            com.google.android.material.button.MaterialButton btnAction;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.friendName);
                tvStatus = itemView.findViewById(R.id.friendStatus);
                btnAction = itemView.findViewById(R.id.friendAction);
            }
        }
    }
}