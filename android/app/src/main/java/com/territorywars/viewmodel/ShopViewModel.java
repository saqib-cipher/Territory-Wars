package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.models.ShopItem;
import com.territorywars.models.User;
import com.territorywars.repository.Repository;

import java.util.List;

public class ShopViewModel extends ViewModel {
    private final Repository repository;
    private final MutableLiveData<List<ShopItem>> items = new MutableLiveData<>();
    private final MutableLiveData<User> balance = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public ShopViewModel(Repository repository) {
        this.repository = repository;
    }

    public LiveData<List<ShopItem>> getItems() { return items; }
    public LiveData<User> getBalance() { return balance; }
    public LiveData<String> getMessage() { return message; }
    public void loadShop() {}
    public void buyItem(String id) {}
}
