package glab.guesscard.activities;

import android.os.Bundle;
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
import java.util.List;
import java.util.Map;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;

public class FriendsActivity extends BaseActivity {

    private EditText etSearch;
    private RecyclerView rvFriends;
    private RecyclerView rvSearch;
    private TextView tvNoFriends;
    private TextView tvSearchHeader;
    private FirebaseManager firebaseManager;
    private String currentUid;

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
        if (btnSearch != null) btnSearch.setOnClickListener(v -> performSearch());

        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    performSearch();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        if (rvFriends != null) rvFriends.setLayoutManager(new LinearLayoutManager(this));
        if (rvSearch != null) rvSearch.setLayoutManager(new LinearLayoutManager(this));

        loadFriends();
    }

    private void loadFriends() {
        if (currentUid == null) return;
        firebaseManager.getFriends(currentUid, list -> runOnUiThread(() -> {
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

        if (query.isEmpty()) {
            if (rvSearch != null) rvSearch.setVisibility(View.GONE);
            if (tvSearchHeader != null) tvSearchHeader.setVisibility(View.GONE);
            if (tvMyFriendsHeader != null) tvMyFriendsHeader.setVisibility(View.VISIBLE);
            loadFriends();
            return;
        }

        // Hide My Friends list section when searching
        if (tvMyFriendsHeader != null) tvMyFriendsHeader.setVisibility(View.GONE);
        if (tvNoFriends != null) tvNoFriends.setVisibility(View.GONE);
        if (rvFriends != null) rvFriends.setVisibility(View.GONE);

        firebaseManager.searchUsers(query, list -> runOnUiThread(() -> {
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
            View v = LayoutInflater.from(parent.getContext()).inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> item = items.get(position);
            String friendUid = (String) item.getOrDefault("uid", "");
            String name = (String) item.getOrDefault("displayName", "Player");
            String status = (String) item.getOrDefault("status", "Online");

            holder.text1.setText(name + " (" + status + ")");
            holder.text1.setTextColor(android.graphics.Color.WHITE);
            holder.text2.setText("UID: " + friendUid + " | Tap to view profile");
            holder.text2.setTextColor(android.graphics.Color.parseColor("#94A3B8"));

            holder.itemView.setOnClickListener(v -> {
                if (friendUid != null && !friendUid.isEmpty()) {
                    startActivity(PublicProfileActivity.intent(FriendsActivity.this, friendUid));
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (isMyFriendsList) {
                    firebaseManager.removeFriend(currentUid, friendUid);
                    Toast.makeText(FriendsActivity.this, "Removed friend: " + name, Toast.LENGTH_SHORT).show();
                    loadFriends();
                } else {
                    firebaseManager.addFriend(currentUid, friendUid);
                    Toast.makeText(FriendsActivity.this, "Added friend: " + name, Toast.LENGTH_SHORT).show();
                    loadFriends();
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView text1, text2;
            ViewHolder(View itemView) {
                super(itemView);
                text1 = itemView.findViewById(android.R.id.text1);
                text2 = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
