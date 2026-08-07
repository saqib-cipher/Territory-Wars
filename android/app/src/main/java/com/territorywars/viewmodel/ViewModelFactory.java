package com.territorywars.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.territorywars.di.GameContainer;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final GameContainer container;

    public ViewModelFactory(GameContainer container) {
        this.container = container;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(container.getRepository());
        } else if (modelClass.isAssignableFrom(GameViewModel.class)) {
            return (T) new GameViewModel(container);
        } else if (modelClass.isAssignableFrom(PlayViewModel.class)) {
            return (T) new PlayViewModel(container.getRepository());
        } else if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(container.getRepository());
        } else if (modelClass.isAssignableFrom(ShopViewModel.class)) {
            return (T) new ShopViewModel(container.getRepository());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
