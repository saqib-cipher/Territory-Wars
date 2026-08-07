package com.territorywars.adapters;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.territorywars.R;
import java.util.ArrayList;
import java.util.List;
/**
* Single-row end-of-match statistics displayed in the game HUD.
*/
public class ScoreboardAdapter extends RecyclerView.Adapter<ScoreboardAdapter.VH> {
private final List<String> rows = new ArrayList<>();
public ScoreboardAdapter(int rank, int score, int tiles, long coins, int xp) {
rows.add("Rank: #" + rank);
rows.add("Score: " + score);
rows.add("Tiles captured: " + tiles);
rows.add("XP earned: " + xp);
rows.add("Coins earned: " + coins);
}
@NonNull
@Override
public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
TextView text = new TextView(parent.getContext());
return new VH(text);
}
@Override
public void onBindViewHolder(@NonNull VH holder, int position) {
holder.text.setText(rows.get(position));
}
@Override
public int getItemCount() {
return rows.size();
}
static class VH extends RecyclerView.ViewHolder {
final TextView text;
VH(@NonNull TextView text) {
super(text);
this.text = text;
}
}
}
