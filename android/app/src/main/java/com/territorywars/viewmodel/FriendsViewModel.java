package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.models.Friend;
import com.territorywars.repository.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Friends state: list + requests + add/accept/remove actions.
 */
public class FriendsViewModel extends ViewModel {

    private final Repository repository;

    private final MutableLiveData<List<Friend>> friends = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public FriendsViewModel(GameContainer container) {
        this.repository = container.getRepository();
    }

    public void loadFriends() {
        repository.getFriends(new Repository.Callback<List<Friend>>() {
            @Override
            public void onResult(List<Friend> value) {
                friends.setValue(value);
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
    }

    public void addFriend(String usernameOrId) {
        repository.addFriend(usernameOrId, new Repository.Callback<Void>() {
            @Override
            public void onResult(Void value) {
                message.setValue("Friend request sent");
                loadFriends();
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
    }

    public void accept(String userId) {
        repository.acceptFriend(userId, new Repository.Callback<Void>() {
            @Override
            public void onResult(Void value) {
                loadFriends();
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
    }

    public void remove(String userId) {
        repository.removeFriend(userId, new Repository.Callback<Void>() {
            @Override
            public void onResult(Void value) {
                loadFriends();
            }

            @Override
            public void onError(String err) {
                message.setValue(err);
            }
        });
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    public LiveData<String> getMessage() {
        return message;
    }
}