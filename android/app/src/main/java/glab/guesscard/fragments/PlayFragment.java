package glab.guesscard.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.Random;

import glab.guesscard.GuessCardApp;
import glab.guesscard.ModernFButton;
import glab.guesscard.R;
import glab.guesscard.activities.GameActivity;
import glab.guesscard.activities.LobbyActivity;
import glab.guesscard.bluetooth.BluetoothMeshManager;
import glab.guesscard.bluetooth.BluetoothPermissionHelper;
import glab.guesscard.models.GameMode;

public class PlayFragment extends Fragment {

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

        ModernFButton createRoom = view.findViewById(R.id.createRoomButton);
        ModernFButton btnCustom = view.findViewById(R.id.customModeButton);
        ModernFButton joinRoom = view.findViewById(R.id.joinRoomButton);
        ModernFButton offline = view.findViewById(R.id.offlineButton);

        if (createRoom != null) {
            createRoom.setOnClickListener(v -> showCreateRoomModeDialog());
        }

        if (joinRoom != null) {
            joinRoom.setOnClickListener(v -> showJoinDialog());
        }

        if (btnCustom != null) {
            btnCustom.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), glab.guesscard.activities.CustomModeActivity.class);
                startActivity(intent);
            });
        }

        if (offline != null) {
            offline.setOnClickListener(v -> openGameOffline());
        }
    }

    private void showCreateRoomModeDialog() {
        String[] modes = new String[]{"ANIMALS 🐾", "FOOD & DISHES 🍔", "COUNTRIES 🌍", "CELEBRITIES 🎬"};
        final GameMode[] modeEnums = new GameMode[]{GameMode.ANIMALS, GameMode.FOOD, GameMode.COUNTRIES, GameMode.CELEBRITIES};

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText etName = new android.widget.EditText(requireContext());
        etName.setHint("Room name (e.g. Friday Night)");
        etName.setSingleLine(true);
        etName.setMaxLines(1);
        etName.setText("");
        etName.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        layout.addView(etName, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.TextView tvModeLabel = new android.widget.TextView(requireContext());
        tvModeLabel.setText("Select Game Mode");
        tvModeLabel.setTextColor(0xFFF59E0B);
        tvModeLabel.setTextSize(13);
        tvModeLabel.setPadding(0, pad, 0, pad / 2);
        layout.addView(tvModeLabel);

        final int[] selectedModeIndex = {0};
        final android.widget.RadioGroup rgModes = new android.widget.RadioGroup(requireContext());
        for (int i = 0; i < modes.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(requireContext());
            rb.setText(modes[i]);
            rb.setTextColor(0xFFFFFFFF);
            rb.setId(android.view.View.generateViewId());
            rgModes.addView(rb);
            if (i == 0) rb.setChecked(true);
        }
        rgModes.setOnCheckedChangeListener((group, checkedId) -> {
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                if (group.getChildAt(i).getId() == checkedId) {
                    selectedModeIndex[0] = i;
                    break;
                }
            }
        });
        layout.addView(rgModes);

        new AlertDialog.Builder(requireContext())
                .setTitle("Host Game Room")
                .setView(layout)
                .setPositiveButton("Host Room", (dialog, which) -> {
                    String roomName = etName.getText().toString().trim();
                    createBluetoothHostRoom(modeEnums[selectedModeIndex[0]], roomName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createBluetoothHostRoom(GameMode mode, String roomName) {
        Context context = getContext();
        if (context == null) return;

        if (!BluetoothPermissionHelper.hasBluetoothPermissions(context)) {
            BluetoothPermissionHelper.showPermissionExplanationDialog(getActivity(), () -> {
                BluetoothPermissionHelper.requestBluetoothPermissions(getActivity(), BluetoothPermissionHelper.REQ_BLUETOOTH_PERMISSIONS);
            });
            return;
        }

        if (!BluetoothPermissionHelper.isBluetoothEnabled()) {
            BluetoothPermissionHelper.promptEnableBluetooth(getActivity());
            return;
        }

        String roomCode = String.valueOf(100000 + new Random().nextInt(900000));
        String finalName = (roomName != null && !roomName.trim().isEmpty()) ? roomName.trim() : (mode.name() + " MESH");

        BluetoothMeshManager bmMgr = GuessCardApp.from(context).getBluetoothMeshManager();
        bmMgr.startHostRoom(roomCode, finalName, mode.name());

        Intent intent = new Intent(requireContext(), LobbyActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_MODE, mode.name());
        intent.putExtra(LobbyActivity.EXTRA_ROOM_ID, "bt_" + roomCode);
        intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, roomCode);
        intent.putExtra(LobbyActivity.EXTRA_ROOM_NAME, finalName);
        intent.putExtra(LobbyActivity.EXTRA_IS_BLUETOOTH, true);
        startActivity(intent);
    }

    private void showJoinDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("6-digit numeric room code");
        new AlertDialog.Builder(requireContext())
                .setTitle("Join Game Room")
                .setView(input)
                .setPositiveButton("Join", (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() < 4) {
                        Toast.makeText(requireContext(), "Enter a valid room code", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(requireContext(), LobbyActivity.class);
                    intent.putExtra(LobbyActivity.EXTRA_ROOM_CODE, code);
                    intent.putExtra(LobbyActivity.EXTRA_IS_BLUETOOTH, true);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openGameOffline() {
        startActivity(GameActivity.intent(
                requireContext(), GameMode.OFFLINE, "offline", 90_000L));
    }
}
