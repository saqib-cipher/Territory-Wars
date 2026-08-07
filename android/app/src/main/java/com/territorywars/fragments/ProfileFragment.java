package com.territorywars.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.territorywars.R;
import com.territorywars.di.GameContainer;
import com.territorywars.models.PlayerProfile;
import com.territorywars.viewmodel.ProfileViewModel;
import com.territorywars.viewmodel.ViewModelFactory;

/**
 * Profile: avatar, username, level + XP, statistics, edit, achievements.
 */
public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);

        MaterialButton edit = view.findViewById(R.id.editProfileButton);
        edit.setOnClickListener(v -> showRenameDialog());

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> bind(view, profile));
        viewModel.getError().observe(getViewLifecycleOwner(),
                msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());

        viewModel.loadProfile();
    }

    private void bind(View view, PlayerProfile profile) {
        TextView username = view.findViewById(R.id.profileUsername);
        TextView level = view.findViewById(R.id.profileLevel);
        LinearProgressIndicator xp = view.findViewById(R.id.xpProgress);

        username.setText(profile.username);
        level.setText("Level " + profile.level);
        xp.setMax(Math.max(1, profile.xpToNext));
        xp.setProgressCompat(profile.xp, true);
    }

    private void showRenameDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("New username");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit Profile")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) ->
                        viewModel.rename(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }
}