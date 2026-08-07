package com.territorywars.adapters;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.territorywars.R;
import com.territorywars.models.ShopItem;
import java.util.ArrayList;
import java.util.List;
/** Shop catalog rows: name, price, buy/owned/equipped state. */
public class ShopItemAdapter extends RecyclerView.Adapter<ShopItemAdapter.VH> {
public interface Listener {
void onAction(ShopItem item);
}
private final List<ShopItem> items = new ArrayList<>();
private Listener listener;
public void submitList(List<ShopItem> data) {
items.clear();
if (data != null) items.addAll(data);
notifyDataSetChanged();
}
public void setListener(Listener listener) {
this.listener = listener;
}
@NonNull
@Override
public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
return new VH(LayoutInflater.from(parent.getContext())
.inflate(R.layout.item_shop, parent, false));
}
@Override
public void onBindViewHolder(@NonNull VH holder, int position) {
ShopItem item = items.get(position);
holder.name.setText(item.name);
holder.desc.setText(item.description);
String action;
if (item.equipped) action = "Equipped";
else if (item.owned) action = "Equip";
else if (item.priceCoins > 0) action = item.priceCoins + " 🪙";
else if (item.priceGems > 0) action = item.priceGems + " 💎";
else action = "Free";
holder.action.setText(action);
holder.action.setEnabled(!item.equipped);
holder.action.setOnClickListener(v -> {
if (listener != null) listener.onAction(item);
});
}
@Override
public int getItemCount() {
return items.size();
}
static class VH extends RecyclerView.ViewHolder {
TextView name, desc;
com.google.android.material.button.MaterialButton action;
VH(@NonNull View itemView) {
super(itemView);
name = itemView.findViewById(R.id.itemName);
desc = itemView.findViewById(R.id.itemDesc);
action = itemView.findViewById(R.id.itemAction);
}
}
}
