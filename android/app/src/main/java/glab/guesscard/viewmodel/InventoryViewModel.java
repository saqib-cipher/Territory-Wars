package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.di.GameContainer;
import glab.guesscard.models.ShopItem;
import glab.guesscard.repository.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory state (owned cosmetics, equip actions).
 */
public class InventoryViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<List<ShopItem>> items = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public InventoryViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void loadInventory() {
        repository.getInventory(new Repository.Callback<List<ShopItem>>() {
            @Override
            public void onSuccess(List<ShopItem> value) {
                items.setValue(value);
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public void equip(String itemId) {
        repository.equipItem(itemId, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loadInventory();
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public LiveData<List<ShopItem>> getItems() {
        return items;
    }

    public LiveData<String> getMessage() {
        return message;
    }
}