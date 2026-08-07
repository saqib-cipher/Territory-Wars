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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.territorywars.R;
import com.territorywars.adapters.ShopItemAdapter;
import com.territorywars.di.GameContainer;
import com.territorywars.models.ShopItem;
import com.territorywars.viewmodel.ShopViewModel;
import com.territorywars.viewmodel.ViewModelFactory;
/**
* Shop: Premium / Coins / Gems / Bundles tabs; buy actions route through the
* ViewModel (coin purchases) or BillingManager (premium packs).
*/
public class ShopFragment extends Fragment {
    private com.territorywars.di.GameContainer container() {
        return com.territorywars.di.GameContainer.getInstance(requireContext());
    }

private ShopViewModel viewModel;
private ShopItemAdapter adapter;
@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater,
@Nullable ViewGroup container,
@Nullable Bundle savedInstanceState) {
return inflater.inflate(R.layout.fragment_shop, container, false);
}
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
super.onViewCreated(view, savedInstanceState);
GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
.get(ShopViewModel.class);
TextView coins = view.findViewById(R.id.coinBalance);
TextView gems = view.findViewById(R.id.gemBalance);
RecyclerView list = view.findViewById(R.id.shopList);
adapter = new ShopItemAdapter();
adapter.setListener(this::onBuy);
list.setLayoutManager(new LinearLayoutManager(requireContext()));
list.setAdapter(adapter);
TabLayout tabs = view.findViewById(R.id.shopTabs);
tabs.addTab(tabs.newTab().setText("Premium"));
tabs.addTab(tabs.newTab().setText("Coins"));
tabs.addTab(tabs.newTab().setText("Gems"));
tabs.addTab(tabs.newTab().setText("Bundles"));
viewModel.getItems().observe(getViewLifecycleOwner(), adapter::submitList);
viewModel.getBalance().observe(getViewLifecycleOwner(), profile -> {
if (profile == null) return;
coins.setText("🪙 " + profile.coins);
gems.setText("💎 " + profile.gems);
});
viewModel.getMessage().observe(getViewLifecycleOwner(),
msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());
viewModel.loadShop();
}
private void onBuy(ShopItem item) {
if (item.owned) {
// equip cosmetics from the shop too
container().getRepository().equipItem(item.id,
new com.territorywars.repository.Repository.Callback<Void>() {
@Override
public void onSuccess(Void value) {
viewModel.loadShop();
}
@Override
public void onError(Throwable t) {
Toast.makeText(requireContext(), t != null ? t.getMessage() : "Error", Toast.LENGTH_SHORT).show();
}
});
return;
}
viewModel.buyItem(item.id);
}
}
