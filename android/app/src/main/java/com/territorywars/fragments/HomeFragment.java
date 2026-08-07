package com.territorywars.fragments;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.territorywars.R;
import com.territorywars.activities.GameActivity;
import com.territorywars.adapters.RecentMatchAdapter;
import com.territorywars.di.GameContainer;
import com.territorywars.models.GameMode;
import com.territorywars.network.ApiService;
import com.territorywars.repository.Repository;
import com.territorywars.viewmodel.HomeViewModel;
import com.territorywars.viewmodel.ViewModelFactory;

/**
 * Home: avatar summary, daily reward card, quick play, recent matches.
 */
public class HomeFragment extends Fragment {

    private HomeViewModel viewModel;
    private RecentMatchAdapter recentAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(HomeViewModel.class);

        RecyclerView recentList = view.findViewById(R.id.recentMatchesList);
        recentAdapter = new RecentMatchAdapter();
        recentList.setLayoutManager(new LinearLayoutManager(requireContext()));
        recentList.setAdapter(recentAdapter);

        MaterialButton quickPlay = view.findViewById(R.id.quickPlayButton);
        quickPlay.setOnClickListener(v -> startActivity(
                GameActivity.intent(requireContext(), GameMode.CLASSIC, "classic_plains", 120_000L)));

        MaterialCardView rewardCard = view.findViewById(R.id.dailyRewardCard);
        rewardCard.setOnClickListener(v -> claimDailyReward());

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                // avatar, coins, gems, level, xp are populated by the adapter/UI in a full build
            }
        });

        viewModel.getRecentMatches().observe(getViewLifecycleOwner(), recentAdapter::submitList);

        viewModel.getError().observe(getViewLifecycleOwner(),
                msg -> Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show());

        viewModel.refresh();
    }

    private void claimDailyReward() {
        viewModel.claimDailyReward(new Repository.Callback<ApiService.DailyRewardResponse>() {
            @Override
            public void onResult(ApiService.DailyRewardResponse value) {
                Toast.makeText(requireContext(),
                        "+" + value.amount + " " + value.rewardType, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}