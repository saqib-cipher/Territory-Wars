package com.territorywars.fragments;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.territorywars.R;
import com.territorywars.activities.LobbyActivity;
import com.territorywars.di.GameContainer;
import com.territorywars.models.GameMode;
import com.territorywars.viewmodel.PlayViewModel;
import com.territorywars.viewmodel.ViewModelFactory;
/**
* Play: Quick Match, Ranked, Create Room, Join Room, Offline Practice.
*/
public class PlayFragment extends Fragment {
private PlayViewModel viewModel;
@Nullable
@Override
public View onCreateView(@NonNull LayoutInflater inflater,
@Nullable ViewGroup container,
@Nullable Bundle savedInstanceState) {
return inflater.inflate(R.layout.fragment_play, container, false);
}
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
super.onViewCreated(view, savedInstanceState);
GameContainer container = com.territorywars.TerritoryWarsApp.from(requireContext());
viewModel = new ViewModelProvider(this, new ViewModelFactory(container))
.get(PlayViewModel.class);
view.findViewById(R.id.quickMatchButton)
.setOnClickListener(v -> viewModel.quickMatch());
view.findViewById(R.id.rankedButton)
.setOnClickListener(v -> viewModel.quickRanked());
view.findViewById(R.id.createRoomButton)
.setOnClickListener(v -> viewModel.createPrivateRoom());
view.findViewById(R.id.joinRoomButton)
.setOnClickListener(v -> showJoinDialog());
view.findViewById(R.id.offlineButton)
.setOnClickListener(v -> openGameOffline());
viewModel.getError().observe(getViewLifecycleOwner(),
msg -> Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show());
viewModel.getJoinEvent().observe(getViewLifecycleOwner(), mode -> {
Intent intent = new Intent(requireContext(), LobbyActivity.class);
intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
startActivity(intent);
});
}
private void showJoinDialog() {
android.widget.EditText input = new android.widget.EditText(requireContext());
input.setHint("6-char room code");
new MaterialAlertDialogBuilder(requireContext())
.setTitle("Join Room")
.setView(input)
.setPositiveButton("Join", (dialog, which) -> {
String code = input.getText().toString().trim();
Intent intent = new Intent(requireContext(), LobbyActivity.class);
intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
startActivity(intent);
})
.setNegativeButton("Cancel", null)
.show();
}
private void openGameOffline() {
// offline practice hosts a local bottleneck
startActivity(com.territorywars.activities.GameActivity.intent(
requireContext(), GameMode.OFFLINE, "practice_islands", 90_000L));
}
@Override
public void onResume() {
super.onResume();
// refresh connectivity
}
}
