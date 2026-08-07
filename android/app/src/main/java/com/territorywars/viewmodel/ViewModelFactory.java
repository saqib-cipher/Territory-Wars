package com.territorywars.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.territorywars.di.GameContainer;

/**
 * Simple factory wired to the manual DI container.
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final GameContainer container;

    public ViewModelFactory(GameContainer container) {
        this.container = container;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (HomeViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new HomeViewModel(container);
        }
        if (PlayViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new PlayViewModel(container);
        }
        if (ProfileViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new ProfileViewModel(container);
        }
        if (ShopViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new ShopViewModel(container);
        }
        if (InventoryViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new InventoryViewModel(container);
        }
        if (LeaderboardViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new LeaderboardViewModel(container);
        }
        if (FriendsViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new FriendsViewModel(container);
        }
        if (SettingsViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new SettingsViewModel(container);
        }
        if (GameViewModel.class.isAssignableFrom(modelClass)) {
            return (T) new GameViewModel(container);
        }
        throw new IllegalArgumentException("Unknown ViewModel " + modelClass.getName());
    }
}