package com.territorywars.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.territorywars.R;
import com.territorywars.database.RecentMatchEntity;

import java.util.ArrayList;
import java.util.List;

public class RecentMatchAdapter extends RecyclerView.Adapter<RecentMatchAdapter.VH> {
    private final List<RecentMatchEntity> items = new ArrayList<>();

    public void submitList(List<RecentMatchEntity> list) {
        items.clear();
        if (list != null) items.addAll(list);
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
        RecentMatchEntity item = items.get(position);
        holder.title.setText("Match #" + item.matchId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.matchTitle);
        }
    }
}
