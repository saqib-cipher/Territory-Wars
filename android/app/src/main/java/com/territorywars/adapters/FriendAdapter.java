package com.territorywars.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.territorywars.R;
import com.territorywars.models.Friend;

import java.util.ArrayList;
import java.util.List;

/**
 * Friends + incoming requests. The action button is "Accept" for requests,
 * "Invite" for online friends, "Remove" otherwise.
 */
public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.VH> {

    public interface Listener {
        void onAction(Friend friend);
    }

    private final List<Friend> items = new ArrayList<>();
    private Listener listener;

    public void submitList(List<Friend> data) {
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
                .inflate(R.layout.item_friend, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Friend f = items.get(position);
        holder.name.setText(f.username);
        holder.status.setText(f.isRequest ? "wants to be friends" : f.status);
        holder.action.setText(f.isRequest ? "Accept" : "Invite");
        holder.action.setOnClickListener(v -> {
            if (listener != null) listener.onAction(f);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, status;
        com.google.android.material.button.MaterialButton action;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.friendName);
            status = itemView.findViewById(R.id.friendStatus);
            action = itemView.findViewById(R.id.friendAction);
        }
    }
}