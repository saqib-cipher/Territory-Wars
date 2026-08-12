package glab.guesscard.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import glab.guesscard.R;
import glab.guesscard.adapters.FriendAdapter;
import glab.guesscard.di.GameContainer;
import glab.guesscard.models.Friend;
import glab.guesscard.viewmodel.FriendsViewModel;
import glab.guesscard.viewmodel.ViewModelFactory;

/**
 * Friends: list + add + accept + invite.
 */
public class FriendsFragment extends Fragment {

    private FriendsViewModel viewModel;
    private FriendAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = glab.guesscard.GuessCardApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(FriendsViewModel.class);

        RecyclerView list = view.findViewById(R.id.friendsList);
        adapter = new FriendAdapter();
        adapter.setListener(this::onFriendAction);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        view.findViewById(R.id.addFriendButton).setOnClickListener(v -> showAddDialog());

        viewModel.getFriends().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getMessage().observe(getViewLifecycleOwner(),
                msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show());

        viewModel.loadFriends();
    }

    private void onFriendAction(Friend friend) {
        if (friend.isRequest) {
            viewModel.accept(friend.userId);
        } else {
            Toast.makeText(requireContext(), "Invite sent to " + friend.username,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("username or player id");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Friend")
                .setView(input)
                .setPositiveButton("Send", (dialog, which) ->
                        viewModel.addFriend(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}