package com.territorywars.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.territorywars.R;
import com.territorywars.models.RoomInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lobby player list with ready state. */
public class LobbyPlayerAdapter extends RecyclerView.Adapter<LobbyPlayerAdapter.VH> {

    private final List<RoomInfo.LobbyPlayer> players = new ArrayList<>();

    public void submitList(List<RoomInfo.LobbyPlayer> data) {
        players.clear();
        if (data != null) players.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lobby_player, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RoomInfo.LobbyPlayer p = players.get(position);
        holder.name.setText(p.username + (p.isHost ? " 👑" : ""));
        holder.badge.setText(p.isReady ? "READY" : "waiting");
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, badge;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.playerName);
            badge = itemView.findViewById(R.id.readyBadge);
        }
    }
}