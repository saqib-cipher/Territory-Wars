package glab.guesscard.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import glab.guesscard.R;
import glab.guesscard.models.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;

/** Leaderboard rows (rank, name, trophy count). */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {

    private final List<LeaderboardEntry> items = new ArrayList<>();

    public void submitList(List<LeaderboardEntry> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        LeaderboardEntry e = items.get(position);
        holder.rank.setText(String.valueOf(e.rank));
        holder.name.setText(e.username);
        holder.trophies.setText(String.valueOf(e.trophies));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView rank, name, trophies;

        VH(@NonNull View itemView) {
            super(itemView);
            rank = itemView.findViewById(R.id.rankText);
            name = itemView.findViewById(R.id.playerName);
            trophies = itemView.findViewById(R.id.trophyText);
        }
    }
}