package glab.guesscard.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.utils.AvatarManager;

public class FriendsActivity extends BaseActivity {

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private EditText etSearch;
    private RecyclerView rvFriends;
    private RecyclerView rvSearch;
    private RecyclerView rvIncoming;
    private TextView tvNoFriends;
    private TextView tvSearchHeader;
    private TextView tvIncomingHeader;
    private View scrollContent;
    private FirebaseManager firebaseManager;
    private String currentUid;
    private final Set<String> friendUids = new HashSet<>();
    private final Set<String> sentRequestUids = new HashSet<>();
    private final Set<String> incomingRequestUids = new HashSet<>();
    private final Map<String, Boolean> onlineStatusMap = new HashMap<>();
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
        rvIncoming = findViewById(R.id.rvIncomingRequests);
        tvNoFriends = findViewById(R.id.tvNoFriends);
        tvSearchHeader = findViewById(R.id.tvSearchResultsHeader);
        tvIncomingHeader = findViewById(R.id.tvIncomingRequestsHeader);
        scrollContent = findViewById(R.id.scrollFriendsContent);

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
        if (rvIncoming != null) rvIncoming.setLayoutManager(new LinearLayoutManager(this));

        loadIncomingRequests();
        loadFriends();
    }

    private com.google.firebase.database.ValueEventListener friendRequestsLiveListener;
    private com.google.firebase.database.ValueEventListener friendsLiveListener;

    @Override
    protected void onStart() {
        super.onStart();
        attachLiveListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachLiveListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadIncomingRequests();
        loadFriends();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachLiveListeners();
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
    }

    private void attachLiveListeners() {
        if (currentUid == null || firebaseManager == null) return;
        detachLiveListeners();

        friendRequestsLiveListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                loadIncomingRequests();
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };
        firebaseManager.getDatabaseRef().child("users").child(currentUid).child("friendRequests")
                .addValueEventListener(friendRequestsLiveListener);

        friendsLiveListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                loadFriends();
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };
        firebaseManager.getDatabaseRef().child("users").child(currentUid).child("friends")
                .addValueEventListener(friendsLiveListener);
    }

    private void detachLiveListeners() {
        if (currentUid == null || firebaseManager == null) return;
        if (friendRequestsLiveListener != null) {
            firebaseManager.getDatabaseRef().child("users").child(currentUid).child("friendRequests")
                    .removeEventListener(friendRequestsLiveListener);
            friendRequestsLiveListener = null;
        }
        if (friendsLiveListener != null) {
            firebaseManager.getDatabaseRef().child("users").child(currentUid).child("friends")
                    .removeEventListener(friendsLiveListener);
            friendsLiveListener = null;
        }
    }

    private void debounceSearch(long delayMs) {
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
        searchRunnable = this::performSearch;
        mainHandler.postDelayed(searchRunnable, delayMs);
    }

    private void loadIncomingRequests() {
        if (currentUid == null) return;
        firebaseManager.getIncomingFriendRequests(currentUid, list -> runOnUiThread(() -> {
            incomingRequestUids.clear();
            if (list != null) {
                for (Map<String, Object> req : list) {
                    Object uid = req.get("uid");
                    if (uid != null) incomingRequestUids.add(String.valueOf(uid));
                }
            }

            if (list == null || list.isEmpty()) {
                if (tvIncomingHeader != null) tvIncomingHeader.setVisibility(View.GONE);
                if (rvIncoming != null) rvIncoming.setVisibility(View.GONE);
            } else {
                if (tvIncomingHeader != null) {
                    tvIncomingHeader.setVisibility(View.VISIBLE);
                    tvIncomingHeader.setText("PENDING FRIEND REQUESTS (" + list.size() + ")");
                }
                if (rvIncoming != null) {
                    rvIncoming.setVisibility(View.VISIBLE);
                    rvIncoming.setAdapter(new IncomingRequestsAdapter(list));
                }
            }
        }));
    }

    private void loadFriends() {
        if (currentUid == null) return;
        firebaseManager.getFriends(currentUid, list -> {
            friendUids.clear();
            List<String> uidsToCheck = new ArrayList<>();
            if (list != null) {
                for (Map<String, Object> f : list) {
                    Object uid = f.get("uid");
                    if (uid != null) {
                        String uidStr = String.valueOf(uid);
                        friendUids.add(uidStr);
                        uidsToCheck.add(uidStr);
                    }
                }
            }

            // Check live online status from socket backend
            if (container() != null && container().getSocketClient() != null && !uidsToCheck.isEmpty()) {
                container().getSocketClient().checkOnlineStatus(uidsToCheck, statusMap -> {
                    if (statusMap != null) onlineStatusMap.putAll(statusMap);
                    displayFriends(list);
                });
            } else {
                displayFriends(list);
            }
        });
    }

    private void displayFriends(List<Map<String, Object>> list) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
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
        });
    }

    private void performSearch() {
        if (etSearch == null) return;
        String query = etSearch.getText().toString().trim();

        if (query.isEmpty()) {
            if (rvSearch != null) rvSearch.setVisibility(View.GONE);
            if (tvSearchHeader != null) tvSearchHeader.setVisibility(View.GONE);
            if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
            loadIncomingRequests();
            loadFriends();
            return;
        }

        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        if (tvSearchHeader != null) {
            tvSearchHeader.setVisibility(View.VISIBLE);
            tvSearchHeader.setText("Searching for: '" + query + "'");
        }
        if (rvSearch != null) rvSearch.setVisibility(View.GONE);

        if (currentUid != null) {
            firebaseManager.getSentFriendRequests(currentUid, sent -> {
                sentRequestUids.clear();
                if (sent != null) sentRequestUids.addAll(sent);

                firebaseManager.getFriends(currentUid, friendList -> {
                    friendUids.clear();
                    if (friendList != null) {
                        for (Map<String, Object> f : friendList) {
                            Object fUid = f.get("uid");
                            if (fUid != null) friendUids.add(String.valueOf(fUid));
                        }
                    }
                    doSearchQuery(query);
                });
            });
        } else {
            doSearchQuery(query);
        }
    }

    private void doSearchQuery(String query) {
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

    // ── INCOMING REQUESTS ADAPTER ───────────────────────────────────────────

    private class IncomingRequestsAdapter extends RecyclerView.Adapter<IncomingRequestsAdapter.VH> {
        private final List<Map<String, Object>> requests;

        IncomingRequestsAdapter(List<Map<String, Object>> requests) {
            this.requests = requests;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request_manage, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> item = requests.get(position);
            String senderUid = String.valueOf(item.getOrDefault("uid", ""));
            String name = String.valueOf(item.getOrDefault("username", item.getOrDefault("displayName", "Player")));
            String avatar = String.valueOf(item.getOrDefault("avatarFileName", "avatar1.png"));

            holder.tvName.setText(name);
            holder.tvSub.setText("UID: " + senderUid);
            AvatarManager.getInstance().loadAvatarIntoImageView(FriendsActivity.this, holder.ivAvatar, avatar);

            holder.btnAccept.setOnClickListener(v -> {
                if (currentUid != null && !senderUid.isEmpty()) {
                    firebaseManager.addFriend(currentUid, senderUid);
                    friendUids.add(senderUid);
                    incomingRequestUids.remove(senderUid);
                    Toast.makeText(FriendsActivity.this, "Accepted friend request from " + name + " 🤝", Toast.LENGTH_SHORT).show();
                    loadIncomingRequests();
                    loadFriends();
                }
            });

            holder.btnDecline.setOnClickListener(v -> {
                if (currentUid != null && !senderUid.isEmpty()) {
                    firebaseManager.declineFriendRequest(currentUid, senderUid);
                    incomingRequestUids.remove(senderUid);
                    Toast.makeText(FriendsActivity.this, "Declined request", Toast.LENGTH_SHORT).show();
                    loadIncomingRequests();
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (!senderUid.isEmpty()) {
                    startActivity(PublicProfileActivity.intent(FriendsActivity.this, senderUid));
                }
            });
        }

        @Override
        public int getItemCount() {
            return requests.size();
        }

        class VH extends RecyclerView.ViewHolder {
            com.google.android.material.imageview.ShapeableImageView ivAvatar;
            TextView tvName, tvSub;
            com.google.android.material.button.MaterialButton btnAccept, btnDecline;

            VH(View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.ivRequestAvatar);
                tvName = v.findViewById(R.id.tvRequestName);
                tvSub = v.findViewById(R.id.tvRequestSub);
                btnAccept = v.findViewById(R.id.btnAcceptRequest);
                btnDecline = v.findViewById(R.id.btnDeclineRequest);
            }
        }
    }

    // ── FRIENDS & SEARCH ADAPTER ────────────────────────────────────────────

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
            String friendUid = String.valueOf(item.getOrDefault("uid", ""));
            String name = String.valueOf(item.getOrDefault("displayName", item.getOrDefault("username", "Player")));

            // Check backend online status
            boolean isOnline = Boolean.TRUE.equals(onlineStatusMap.get(friendUid));
            String statusText = isOnline ? "🟢 Online" : "⚪ Offline";

            holder.tvName.setText(name);
            holder.tvStatus.setText("UID: " + friendUid + " • " + statusText);

            boolean isFriend = currentUid != null && friendUids.contains(friendUid);
            boolean isSentRequest = currentUid != null && sentRequestUids.contains(friendUid);
            boolean isIncoming = currentUid != null && incomingRequestUids.contains(friendUid);

            if (isMyFriendsList) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Remove");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        firebaseManager.removeFriend(currentUid, friendUid);
                        friendUids.remove(friendUid);
                        Toast.makeText(FriendsActivity.this, "Removed friend: " + name, Toast.LENGTH_SHORT).show();
                        loadFriends();
                    }
                });
            } else if (isFriend) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Friends ✓");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
                holder.btnAction.setTextColor(Color.parseColor("#94A3B8"));
            } else if (isSentRequest) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Requested ⏳");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E293B")));
                holder.btnAction.setTextColor(Color.parseColor("#F59E0B"));
            } else if (isIncoming) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Accept 🤝");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        firebaseManager.addFriend(currentUid, friendUid);
                        friendUids.add(friendUid);
                        incomingRequestUids.remove(friendUid);
                        Toast.makeText(FriendsActivity.this, "Accepted friend request from " + name + " 🤝", Toast.LENGTH_SHORT).show();
                        loadIncomingRequests();
                        loadFriends();
                        notifyItemChanged(holder.getAdapterPosition());
                    }
                });
            } else {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("+ Add");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        firebaseManager.sendFriendRequest(currentUid, friendUid);
                        sentRequestUids.add(friendUid);
                        if (container() != null && container().getSocketClient() != null) {
                            container().getSocketClient().sendFriendRequest(friendUid);
                        }
                        Toast.makeText(FriendsActivity.this, "Friend request sent to " + name + " ✉️", Toast.LENGTH_SHORT).show();
                        holder.btnAction.setText("Requested ⏳");
                        holder.btnAction.setEnabled(false);
                        holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E293B")));
                        holder.btnAction.setTextColor(Color.parseColor("#F59E0B"));
                    }
                });
            }

            holder.itemView.setOnClickListener(v -> {
                if (!friendUid.isEmpty()) {
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