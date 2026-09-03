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
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.firebase.FirebaseManager;
import glab.guesscard.firebase.FriendshipManager;
import glab.guesscard.utils.AvatarManager;

public class FriendsActivity extends BaseActivity {

    private static final long SEARCH_DEBOUNCE_MS = 350L;
    private static final long STATUS_REFRESH_MS = 15000L;

    private EditText etSearch;
    private RecyclerView rvFriends;
    private RecyclerView rvSearch;
    private TextView tvNoFriends;
    private TextView tvSearchHeader;
    private TextView tvMyFriendsHeader;
    private TextView tvNotificationBadge;
    private View scrollContent;
    private FirebaseManager firebaseManager;
    private String currentUid;
    private final Set<String> friendUids = new HashSet<>();
    private final Set<String> sentRequestUids = new HashSet<>();
    private final Set<String> incomingRequestUids = new HashSet<>();
    private final Map<String, Boolean> onlineStatusMap = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private Runnable statusRefreshRunnable;
    private List<Map<String, Object>> lastFriendsList = new ArrayList<>();
    private List<Map<String, Object>> lastSearchList = new ArrayList<>();
    private List<Map<String, Object>> lastIncomingRequests = new ArrayList<>();
    private boolean statusRefreshActive;

    private AlertDialog incomingRequestsDialog = null;
    private IncomingRequestsDialogAdapter dialogAdapter = null;

    private com.google.firebase.database.ValueEventListener friendRequestsLiveListener;
    private com.google.firebase.database.ValueEventListener friendsLiveListener;
    private com.google.firebase.database.ValueEventListener sentRequestsLiveListener;

    private String resolveCurrentUid() {
        FirebaseUser fUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fUser != null) {
            String uid = fUser.getUid();
            if (prefs() != null) prefs().saveUserId(uid);
            return uid;
        }
        return prefs() != null ? prefs().getUserId() : null;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        firebaseManager = container().getFirebaseManager();
        currentUid = resolveCurrentUid();

        etSearch = findViewById(R.id.etSearchFriends);
        rvFriends = findViewById(R.id.rvFriendsList);
        rvSearch = findViewById(R.id.rvSearchResults);
        tvNoFriends = findViewById(R.id.tvNoFriends);
        tvSearchHeader = findViewById(R.id.tvSearchResultsHeader);
        tvMyFriendsHeader = findViewById(R.id.tvMyFriendsHeader);
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge);
        scrollContent = findViewById(R.id.scrollFriendsContent);

        ModernFButton btnClose = findViewById(R.id.btnCloseFriends);
        if (btnClose != null) btnClose.setOnClickListener(v -> finish());

        View flNotification = findViewById(R.id.flNotificationContainer);
        View btnNotification = findViewById(R.id.btnFriendRequestsNotification);
        View.OnClickListener notifClick = v -> {
            loadIncomingRequests();
            showReceivedRequestsDialog();
        };
        if (flNotification != null) flNotification.setOnClickListener(notifClick);
        if (btnNotification != null) btnNotification.setOnClickListener(notifClick);

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

        loadIncomingRequests();
        loadFriends();
    }

    @Override
    protected void onStart() {
        super.onStart();
        currentUid = resolveCurrentUid();
        attachLiveListeners();
        startStatusRefresh();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachLiveListeners();
        stopStatusRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentUid = resolveCurrentUid();
        loadIncomingRequests();
        loadFriends();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachLiveListeners();
        stopStatusRefresh();
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
        dismissRequestsDialog();
    }

    @Override
    protected void onGlobalFriendRequestReceived(String senderId, String senderName, String senderAvatar) {
        super.onGlobalFriendRequestReceived(senderId, senderName, senderAvatar);
        incomingRequestUids.add(senderId);
        loadIncomingRequests();
        if (rvSearch != null && rvSearch.getAdapter() != null) rvSearch.getAdapter().notifyDataSetChanged();
    }

    @Override
    protected void onGlobalFriendRequestAccepted(String acceptorId, String acceptorName, String acceptorAvatar) {
        super.onGlobalFriendRequestAccepted(acceptorId, acceptorName, acceptorAvatar);
        Toast.makeText(FriendsActivity.this, acceptorName + " accepted your friend request 🤝", Toast.LENGTH_SHORT).show();
        loadFriends();
    }

    private void startStatusRefresh() {
        statusRefreshActive = true;
        statusRefreshRunnable = () -> {
            if (!statusRefreshActive) return;
            refreshAllOnlineStatuses();
            mainHandler.postDelayed(statusRefreshRunnable, STATUS_REFRESH_MS);
        };
        mainHandler.postDelayed(statusRefreshRunnable, STATUS_REFRESH_MS);
    }

    private void stopStatusRefresh() {
        statusRefreshActive = false;
        if (statusRefreshRunnable != null) mainHandler.removeCallbacks(statusRefreshRunnable);
        statusRefreshRunnable = null;
    }

    private void refreshAllOnlineStatuses() {
        List<String> uids = new ArrayList<>();
        if (lastFriendsList != null) {
            for (Map<String, Object> f : lastFriendsList) {
                Object uid = f.get("uid");
                if (uid != null) uids.add(String.valueOf(uid));
            }
        }
        if (lastSearchList != null) {
            for (Map<String, Object> s : lastSearchList) {
                Object uid = s.get("uid");
                if (uid != null && !uids.contains(String.valueOf(uid))) uids.add(String.valueOf(uid));
            }
        }
        if (uids.isEmpty()) return;
        if (container() != null && container().getSocketClient() != null) {
            container().getSocketClient().checkOnlineStatus(uids, statusMap -> {
                if (statusMap == null) return;
                runOnUiThread(() -> {
                    onlineStatusMap.putAll(statusMap);
                    if (rvFriends != null && rvFriends.getAdapter() != null) rvFriends.getAdapter().notifyDataSetChanged();
                    if (rvSearch != null && rvSearch.getAdapter() != null) rvSearch.getAdapter().notifyDataSetChanged();
                });
            });
        }
    }

    private void attachLiveListeners() {
        currentUid = resolveCurrentUid();
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

        sentRequestsLiveListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                sentRequestUids.clear();
                for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                    if (child.getKey() != null) sentRequestUids.add(child.getKey());
                }
                if (rvSearch != null && rvSearch.getAdapter() != null) {
                    rvSearch.getAdapter().notifyDataSetChanged();
                }
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        };
        firebaseManager.getDatabaseRef().child("users").child(currentUid).child("sentRequests")
                .addValueEventListener(sentRequestsLiveListener);
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
        if (sentRequestsLiveListener != null) {
            firebaseManager.getDatabaseRef().child("users").child(currentUid).child("sentRequests")
                    .removeEventListener(sentRequestsLiveListener);
            sentRequestsLiveListener = null;
        }
    }

    private void debounceSearch(long delayMs) {
        if (searchRunnable != null) mainHandler.removeCallbacks(searchRunnable);
        searchRunnable = this::performSearch;
        mainHandler.postDelayed(searchRunnable, delayMs);
    }

    private void loadIncomingRequests() {
        currentUid = resolveCurrentUid();
        if (currentUid == null) return;
        firebaseManager.getIncomingFriendRequests(currentUid, list -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            incomingRequestUids.clear();
            lastIncomingRequests = list != null ? list : new ArrayList<>();
            for (Map<String, Object> req : lastIncomingRequests) {
                Object uid = req.get("uid");
                if (uid != null) incomingRequestUids.add(String.valueOf(uid));
            }

            int count = lastIncomingRequests.size();
            if (tvNotificationBadge != null) {
                if (count > 0) {
                    tvNotificationBadge.setVisibility(View.VISIBLE);
                    tvNotificationBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                } else {
                    tvNotificationBadge.setVisibility(View.GONE);
                }
            }

            if (incomingRequestsDialog != null && incomingRequestsDialog.isShowing() && dialogAdapter != null) {
                dialogAdapter.updateList(lastIncomingRequests);
            }
        }));
    }

    private void showReceivedRequestsDialog() {
        if (isFinishing() || isDestroyed()) return;
        dismissRequestsDialog();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_received_friend_requests, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        ModernFButton btnClose = dialogView.findViewById(R.id.btnDialogClose);
        TextView tvEmpty = dialogView.findViewById(R.id.tvEmptyRequests);
        RecyclerView rvDialogRequests = dialogView.findViewById(R.id.rvDialogRequests);

        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());

        if (rvDialogRequests != null) {
            rvDialogRequests.setLayoutManager(new LinearLayoutManager(this));
            dialogAdapter = new IncomingRequestsDialogAdapter(lastIncomingRequests, dialog, tvTitle, tvEmpty, rvDialogRequests);
            rvDialogRequests.setAdapter(dialogAdapter);
        }

        updateDialogUI(tvTitle, tvEmpty, rvDialogRequests, lastIncomingRequests.size());

        incomingRequestsDialog = dialog;
        dialog.show();
    }

    private void updateDialogUI(TextView tvTitle, TextView tvEmpty, RecyclerView rv, int count) {
        if (tvTitle != null) {
            tvTitle.setText(count > 0 ? "Received Requests (" + count + ") 📬" : "Received Requests 📬");
        }
        if (count == 0) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            if (rv != null) rv.setVisibility(View.GONE);
        } else {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (rv != null) rv.setVisibility(View.VISIBLE);
        }
    }

    private void dismissRequestsDialog() {
        if (incomingRequestsDialog != null && incomingRequestsDialog.isShowing()) {
            try {
                incomingRequestsDialog.dismiss();
            } catch (Exception ignored) {}
        }
        incomingRequestsDialog = null;
        dialogAdapter = null;
    }

    private void loadFriends() {
        currentUid = resolveCurrentUid();
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
            lastFriendsList = list != null ? list : new ArrayList<>();

            // Check live online status from socket backend
            if (container() != null && container().getSocketClient() != null && !uidsToCheck.isEmpty()) {
                container().getSocketClient().checkOnlineStatus(uidsToCheck, statusMap -> {
                    if (statusMap != null) onlineStatusMap.putAll(statusMap);
                    displayFriends(lastFriendsList);
                });
            } else {
                displayFriends(lastFriendsList);
            }
        });
    }

    private void displayFriends(List<Map<String, Object>> list) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            int count = list != null ? list.size() : 0;
            if (tvMyFriendsHeader != null) {
                tvMyFriendsHeader.setText("MY FRIENDS (" + count + ")");
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
        });
    }

    private void performSearch() {
        if (etSearch == null) return;
        String query = etSearch.getText().toString().trim();

        if (query.isEmpty()) {
            if (rvSearch != null) rvSearch.setVisibility(View.GONE);
            if (tvSearchHeader != null) tvSearchHeader.setVisibility(View.GONE);
            if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
            loadFriends();
            return;
        }

        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        if (tvSearchHeader != null) {
            tvSearchHeader.setVisibility(View.VISIBLE);
            tvSearchHeader.setText("Searching for: '" + query + "'");
        }
        if (rvSearch != null) rvSearch.setVisibility(View.GONE);

        currentUid = resolveCurrentUid();
        if (currentUid != null) {
            firebaseManager.getSentFriendRequests(currentUid, sent -> {
                sentRequestUids.clear();
                if (sent != null) sentRequestUids.addAll(sent);

                firebaseManager.getIncomingFriendRequests(currentUid, incoming -> {
                    incomingRequestUids.clear();
                    if (incoming != null) {
                        for (Map<String, Object> inReq : incoming) {
                            Object inUid = inReq.get("uid");
                            if (inUid != null) incomingRequestUids.add(String.valueOf(inUid));
                        }
                    }

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
            });
        } else {
            doSearchQuery(query);
        }
    }

    private void doSearchQuery(String query) {
        firebaseManager.searchUsers(currentUid, query, list -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (list == null || list.isEmpty()) {
                lastSearchList = new ArrayList<>();
                if (rvSearch != null) rvSearch.setVisibility(View.GONE);
                if (tvSearchHeader != null) {
                    tvSearchHeader.setVisibility(View.VISIBLE);
                    tvSearchHeader.setText("No users found matching: '" + query + "'");
                }
            } else {
                lastSearchList = list;
                List<String> uidsToCheck = new ArrayList<>();
                for (Map<String, Object> u : list) {
                    Object uid = u.get("uid");
                    if (uid != null) uidsToCheck.add(String.valueOf(uid));
                }
                if (container() != null && container().getSocketClient() != null && !uidsToCheck.isEmpty()) {
                    container().getSocketClient().checkOnlineStatus(uidsToCheck, statusMap -> runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (statusMap != null) onlineStatusMap.putAll(statusMap);
                        renderSearchResults(list, query);
                    }));
                } else {
                    renderSearchResults(list, query);
                }
            }
        }));
    }

    private void renderSearchResults(List<Map<String, Object>> list, String query) {
        if (isFinishing() || isDestroyed()) return;
        if (tvSearchHeader != null) {
            tvSearchHeader.setVisibility(View.VISIBLE);
            tvSearchHeader.setText("SEARCH RESULTS (" + list.size() + " found)");
        }
        if (rvSearch != null) {
            rvSearch.setVisibility(View.VISIBLE);
            rvSearch.setAdapter(new FriendsAdapter(list, false));
        }
    }

    // ── RECEIVED REQUESTS DIALOG ADAPTER ─────────────────────────────────────

    private class IncomingRequestsDialogAdapter extends RecyclerView.Adapter<IncomingRequestsDialogAdapter.VH> {
        private final List<Map<String, Object>> requests;
        private final AlertDialog dialog;
        private final TextView tvTitle;
        private final TextView tvEmpty;
        private final RecyclerView rv;

        IncomingRequestsDialogAdapter(List<Map<String, Object>> requests,
                                     AlertDialog dialog,
                                     TextView tvTitle,
                                     TextView tvEmpty,
                                     RecyclerView rv) {
            this.requests = new ArrayList<>(requests);
            this.dialog = dialog;
            this.tvTitle = tvTitle;
            this.tvEmpty = tvEmpty;
            this.rv = rv;
        }

        void updateList(List<Map<String, Object>> newList) {
            requests.clear();
            if (newList != null) requests.addAll(newList);
            notifyDataSetChanged();
            updateDialogUI(tvTitle, tvEmpty, rv, requests.size());
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
            String name = String.valueOf(item.getOrDefault("displayName", item.getOrDefault("username", "Player")));
            String avatar = String.valueOf(item.getOrDefault("avatarFileName", "avatar1.png"));

            holder.tvName.setText(name);
            holder.tvSub.setText("UID: " + senderUid);
            AvatarManager.getInstance().loadAvatarIntoImageView(FriendsActivity.this, holder.ivAvatar, avatar);

            holder.btnAccept.setOnClickListener(v -> {
                if (currentUid != null && !senderUid.isEmpty()) {
                    FriendshipManager.acceptFriendRequest(FriendsActivity.this, currentUid, senderUid, name, new FriendshipManager.FriendshipActionCallback() {
                        @Override
                        public void onSuccess() {
                            friendUids.add(senderUid);
                            incomingRequestUids.remove(senderUid);
                            requests.remove(holder.getAdapterPosition());
                            notifyDataSetChanged();
                            updateDialogUI(tvTitle, tvEmpty, rv, requests.size());
                            loadIncomingRequests();
                            loadFriends();
                        }

                        @Override
                        public void onError(String message) {}
                    });
                }
            });

            holder.btnDecline.setOnClickListener(v -> {
                if (currentUid != null && !senderUid.isEmpty()) {
                    FriendshipManager.declineFriendRequest(FriendsActivity.this, currentUid, senderUid, new FriendshipManager.FriendshipActionCallback() {
                        @Override
                        public void onSuccess() {
                            incomingRequestUids.remove(senderUid);
                            requests.remove(holder.getAdapterPosition());
                            notifyDataSetChanged();
                            updateDialogUI(tvTitle, tvEmpty, rv, requests.size());
                            loadIncomingRequests();
                        }

                        @Override
                        public void onError(String message) {}
                    });
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
            String avatar = String.valueOf(item.getOrDefault("avatarFileName", "avatar1.png"));

            if (holder.ivAvatar != null) {
                AvatarManager.getInstance().loadAvatarIntoImageView(FriendsActivity.this, holder.ivAvatar, avatar);
            }

            // Online & Last Seen status formatting
            boolean isOnline = Boolean.TRUE.equals(onlineStatusMap.get(friendUid));
            if (!isOnline && item.get("isOnline") != null) {
                isOnline = Boolean.TRUE.equals(item.get("isOnline"));
            }
            Long lastSeenTs = null;
            Object lsObj = item.get("lastSeen");
            if (lsObj instanceof Long) lastSeenTs = (Long) lsObj;

            String statusFormatted = FirebaseManager.formatLastSeen(isOnline, lastSeenTs);
            holder.tvName.setText(name);
            holder.tvStatus.setText(statusFormatted);

            if (holder.vOnlineDot != null) {
                holder.vOnlineDot.setBackgroundTintList(ColorStateList.valueOf(
                        isOnline ? Color.parseColor("#10B981") : Color.parseColor("#64748B")));
            }

            boolean isSelf = currentUid != null && currentUid.equals(friendUid);
            boolean isFriend = currentUid != null && friendUids.contains(friendUid);
            boolean isSentRequest = currentUid != null && sentRequestUids.contains(friendUid);
            boolean isIncoming = currentUid != null && incomingRequestUids.contains(friendUid);

            if (isSelf) {
                holder.btnAction.setVisibility(View.GONE);
            } else if (isMyFriendsList) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Remove");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#EF4444")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        FriendshipManager.removeFriend(FriendsActivity.this, currentUid, friendUid, name, new FriendshipManager.FriendshipActionCallback() {
                            @Override
                            public void onSuccess() {
                                friendUids.remove(friendUid);
                                loadFriends();
                            }

                            @Override public void onError(String message) {}
                        });
                    }
                });
            } else if (isFriend) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Friends ✓");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#334155")));
                holder.btnAction.setTextColor(Color.parseColor("#94A3B8"));
            } else if (isIncoming) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Accept 🤝");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#10B981")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        FriendshipManager.acceptFriendRequest(FriendsActivity.this, currentUid, friendUid, name, new FriendshipManager.FriendshipActionCallback() {
                            @Override
                            public void onSuccess() {
                                friendUids.add(friendUid);
                                incomingRequestUids.remove(friendUid);
                                loadIncomingRequests();
                                loadFriends();
                                notifyItemChanged(holder.getAdapterPosition());
                            }

                            @Override public void onError(String message) {}
                        });
                    }
                });
            } else if (isSentRequest) {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("Requested ⏳");
                holder.btnAction.setEnabled(false);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E293B")));
                holder.btnAction.setTextColor(Color.parseColor("#F59E0B"));
            } else {
                holder.btnAction.setVisibility(View.VISIBLE);
                holder.btnAction.setText("+ Add");
                holder.btnAction.setEnabled(true);
                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2563EB")));
                holder.btnAction.setTextColor(Color.WHITE);
                holder.btnAction.setOnClickListener(v -> {
                    if (currentUid != null && !friendUid.isEmpty()) {
                        FriendshipManager.sendFriendRequest(FriendsActivity.this, currentUid, friendUid, name, new FriendshipManager.FriendshipActionCallback() {
                            @Override
                            public void onSuccess() {
                                sentRequestUids.add(friendUid);
                                holder.btnAction.setText("Requested ⏳");
                                holder.btnAction.setEnabled(false);
                                holder.btnAction.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E293B")));
                                holder.btnAction.setTextColor(Color.parseColor("#F59E0B"));
                            }

                            @Override public void onError(String message) {}
                        });
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
            com.google.android.material.imageview.ShapeableImageView ivAvatar;
            View vOnlineDot;
            TextView tvName, tvStatus;
            com.google.android.material.button.MaterialButton btnAction;

            ViewHolder(View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.friendAvatar);
                vOnlineDot = itemView.findViewById(R.id.friendOnlineDot);
                tvName = itemView.findViewById(R.id.friendName);
                tvStatus = itemView.findViewById(R.id.friendStatus);
                btnAction = itemView.findViewById(R.id.friendAction);
            }
        }
    }
}