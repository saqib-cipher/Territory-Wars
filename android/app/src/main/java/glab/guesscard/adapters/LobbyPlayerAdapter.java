package glab.guesscard.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.PublicProfileActivity;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.utils.AvatarManager;

import java.util.ArrayList;
import java.util.List;

public class LobbyPlayerAdapter extends RecyclerView.Adapter<LobbyPlayerAdapter.VH> {
    private final List<RoomInfo.LobbyPlayer> players = new ArrayList<>();
    private String hostUid = "";

    public void submitList(List<RoomInfo.LobbyPlayer> list, String hostUid) {
        this.hostUid = hostUid != null ? hostUid : "";
        players.clear();
        if (list != null) players.addAll(list);
        notifyDataSetChanged();
    }

    public void submitList(List<RoomInfo.LobbyPlayer> list) {
        submitList(list, "");
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lobby_player, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RoomInfo.LobbyPlayer u = players.get(position);
        Context context = holder.itemView.getContext();
        String currentUid = GuessCardApp.from(context).getPreferences().getUserId();

        boolean isSelf = u.userId != null && u.userId.equals(currentUid);
        boolean isHost = u.isHost || (u.userId != null && u.userId.equals(hostUid));
        String name = u.username != null ? u.username : "Player";

        holder.tvName.setText(name);
        holder.tvYouTag.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        if (holder.tvHostTag != null) holder.tvHostTag.setVisibility(isHost ? View.VISIBLE : View.GONE);

        String avatarFile = u.avatarFileName != null && !u.avatarFileName.isEmpty() ? u.avatarFileName :
                (isSelf ? GuessCardApp.from(context).getPreferences().getAvatarFileName() : "avatar_01.png");
        AvatarManager.getInstance().loadAvatarIntoImageView(context, holder.imgAvatar, avatarFile);

        if (u.isLeft) {
            holder.tvStatus.setText("LEFT / OFFLINE 🔴");
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"));
        } else if (isHost) {
            holder.tvStatus.setText("HOST 👑");
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
        } else if (u.isReady) {
            holder.tvStatus.setText("READY 🟢");
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981"));
        } else {
            holder.tvStatus.setText("NOT READY 🟡");
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#F59E0B"));
        }

        if (isSelf) {
            holder.btnAddFriend.setVisibility(View.GONE);
        } else {
            holder.btnAddFriend.setVisibility(View.VISIBLE);
            holder.btnAddFriend.setOnClickListener(v -> {
                if (u.userId != null && currentUid != null) {
                    GuessCardApp.from(context).getFirebaseManager().addFriend(currentUid, u.userId);
                    Toast.makeText(context, "Added " + name + " to friends list", Toast.LENGTH_SHORT).show();
                }
            });
        }

        holder.itemView.setOnClickListener(v -> {
            if (u.userId != null && !u.userId.isEmpty()) {
                context.startActivity(PublicProfileActivity.intent(context, u.userId));
            }
        });
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgAvatar;
        TextView tvName, tvYouTag, tvHostTag, tvLevel, tvStatus;
        ModernFButton btnAddFriend;

        VH(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgLobbyPlayerAvatar);
            tvName = itemView.findViewById(R.id.tvLobbyPlayerName);
            tvYouTag = itemView.findViewById(R.id.tvYouTag);
            tvHostTag = itemView.findViewById(R.id.tvHostTag);
            tvLevel = itemView.findViewById(R.id.tvLobbyPlayerLevel);
            tvStatus = itemView.findViewById(R.id.tvLobbyPlayerStatus);
            btnAddFriend = itemView.findViewById(R.id.btnAddFriendInLobby);
        }
    }
}
