package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.models.PlayerProfile;
import com.territorywars.models.ShopItem;
import com.territorywars.repository.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop state with catalog + balances.
 */
public class ShopViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<List<ShopItem>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<PlayerProfile> balance = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public ShopViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void loadShop() {
        repository.getShop(new Repository.Callback<List<ShopItem>>() {
            @Override
            public void onResult(List<ShopItem> value) {
                items.setValue(value);
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
        repository.getProfile(new Repository.Callback<PlayerProfile>() {
            @Override
            public void onResult(PlayerProfile value) {
                balance.setValue(value);
            }

            @Override
            public void onError(String err) {
                // balance stays stale until next load
            }
        });
    }

    public void buyItem(String itemId) {
        repository.buyWithCoins(itemId, new Repository.Callback<PlayerProfile>() {
            @Override
            public void onResult(PlayerProfile value) {
                balance.setValue(value);
                loadShop();
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
    }

    public LiveData<List<ShopItem>> getItems() {
        return items;
    }

    public LiveData<PlayerProfile> getBalance() {
        return balance;
    }

    public LiveData<String> getMessage() {
        return message;
    }
}