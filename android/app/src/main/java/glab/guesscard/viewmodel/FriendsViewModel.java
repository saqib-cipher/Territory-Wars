package glab.guesscard.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import glab.guesscard.di.GameContainer;
import glab.guesscard.models.Friend;
import glab.guesscard.repository.Repository;

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
            public void onSuccess(List<Friend> value) {
                friends.setValue(value);
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public void addFriend(String usernameOrId) {
        repository.addFriend(usernameOrId, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                message.setValue("Friend request sent");
                loadFriends();
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public void accept(String userId) {
        repository.acceptFriend(userId, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loadFriends();
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
            }
        });
    }

    public void remove(String userId) {
        repository.removeFriend(userId, new Repository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loadFriends();
            }

            @Override
            public void onError(Throwable t) {
                message.setValue(t != null ? t.getMessage() : "Error");
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