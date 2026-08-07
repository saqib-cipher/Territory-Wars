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

import com.google.android.material.tabs.TabLayout;
import com.territorywars.R;
import com.territorywars.adapters.LeaderboardAdapter;
import com.territorywars.di.GameContainer;
import com.territorywars.viewmodel.LeaderboardViewModel;
import com.territorywars.viewmodel.ViewModelFactory;

/**
 * Leaderboard with Global / Friends / Weekly / Monthly tabs.
 */
public class LeaderboardFragment extends Fragment {

    private LeaderboardViewModel viewModel;
    private LeaderboardAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(LeaderboardViewModel.class);

        RecyclerView list = view.findViewById(R.id.leaderboardList);
        adapter = new LeaderboardAdapter();
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        TabLayout tabs = view.findViewById(R.id.leaderboardTabs);
        tabs.addTab(tabs.newTab().setText("Global"));
        tabs.addTab(tabs.newTab().setText("Friends"));
        tabs.addTab(tabs.newTab().setText("Weekly"));
        tabs.addTab(tabs.newTab().setText("Monthly"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewModel.setScope(tab.getText().toString().toLowerCase());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        viewModel.getEntries().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getError().observe(getViewLifecycleOwner(),
                msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());

        viewModel.load();
    }
}