package com.territorywars.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.territorywars.di.GameContainer;
import com.territorywars.models.GameMode;
import com.territorywars.socket.GameSocketClient;

/**
 * Handles matchmaking start and room code joins from the Play screen.
 *
 * <p>Creates/joins rooms through the socket; success is surfaced as a
 * room-joined socket event, inviting {@code LobbyActivity}.</p>
 */
public class PlayViewModel extends ViewModel {

    private final GameSocketClient socket;

    private final MutableLiveData<GameMode> joinEvent = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public PlayViewModel(GameContainer container) {
        this.socket = container.getSocketClient();
    }

    /** Requests the matchmaker to place the player in a Classic room. */
    public void quickMatch() {
        socket.createRoom(GameMode.CLASSIC.name(), "classic_plains");
        joinEvent.setValue(GameMode.CLASSIC);
    }

    public void quickRanked() {
        socket.createRoom(GameMode.RANKED.name(), "ranked_arena");
        joinEvent.setValue(GameMode.RANKED);
    }

    /** Hosts a private room with a shareable code. */
    public void createPrivateRoom() {
        socket.createRoom(GameMode.PRIVATE_ROOM.name(), "private_islands");
        joinEvent.setValue(GameMode.PRIVATE_ROOM);
    }

    public LiveData<GameMode> getJoinEvent() {
        return joinEvent;
    }

    public LiveData<String> getError() {
        return error;
    }
}