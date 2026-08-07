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

public class LobbyPlayerAdapter extends RecyclerView.Adapter<LobbyPlayerAdapter.VH> {
    private final List<RoomInfo.LobbyPlayer> players = new ArrayList<>();

    public void submitList(List<RoomInfo.LobbyPlayer> list) {
        players.clear();
        if (list != null) players.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_match, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RoomInfo.LobbyPlayer u = players.get(position);
        holder.name.setText(u.username != null ? u.username : "Player");
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.matchTitle);
        }
    }
}
