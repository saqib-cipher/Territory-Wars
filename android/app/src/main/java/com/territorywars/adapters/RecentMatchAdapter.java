package com.territorywars.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.territorywars.R;
import com.territorywars.models.MatchResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent-match rows on Home.
 */
public class RecentMatchAdapter extends RecyclerView.Adapter<RecentMatchAdapter.VH> {

    private final List<MatchResult> items = new ArrayList<>();

    public void submitList(List<MatchResult> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_match, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MatchResult m = items.get(position);
        holder.mode.setText(m.mode);
        holder.result.setText(m.won ? "W" : "L");
        holder.score.setText(m.score + " pts");
        holder.detail.setText(m.tilesCaptured + " tiles · #" + m.rank);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView mode, result, score, detail;

        VH(@NonNull View itemView) {
            super(itemView);
            mode = itemView.findViewById(com.territorywars.R.id.matchMode);
            result = itemView.findViewById(com.territorywars.R.id.matchResult);
            score = itemView.findViewById(com.territorywars.R.id.matchScore);
            detail = itemView.findViewById(com.territorywars.R.id.matchDetail);
        }
    }
}