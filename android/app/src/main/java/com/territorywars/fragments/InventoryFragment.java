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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.territorywars.R;
import com.territorywars.adapters.ShopItemAdapter;
import com.territorywars.di.GameContainer;
import com.territorywars.models.ShopItem;
import com.territorywars.viewmodel.InventoryViewModel;
import com.territorywars.viewmodel.ViewModelFactory;

/**
 * Inventory with category chips (Skins / Trails / Effects / Emotes / Titles).
 */
public class InventoryFragment extends Fragment {

    private InventoryViewModel viewModel;
    private ShopItemAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inventory, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
        viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(InventoryViewModel.class);

        RecyclerView list = view.findViewById(R.id.inventoryList);
        adapter = new ShopItemAdapter();
        adapter.setListener(item -> viewModel.equip(item.id));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        ChipGroup chips = view.findViewById(R.id.inventoryChipGroup);
        String[] cats = {"All", "Skins", "Trails", "Effects", "Emotes", "Titles"};
        for (String cat : cats) {
            Chip chip = new Chip(requireContext());
            chip.setText(cat);
            chip.setCheckable(true);
            chips.addView(chip);
        }

        viewModel.getItems().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getMessage().observe(getViewLifecycleOwner(),
                msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());

        viewModel.loadInventory();
    }
}