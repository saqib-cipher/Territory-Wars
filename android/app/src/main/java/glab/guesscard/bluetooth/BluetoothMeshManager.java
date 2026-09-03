package glab.guesscard.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import glab.guesscard.models.Card;
import glab.guesscard.models.ChatMessage;
import glab.guesscard.models.GameMode;
import glab.guesscard.models.MatchResult;
import glab.guesscard.models.RoomInfo;
import glab.guesscard.network.PreferenceManager;
import glab.guesscard.socket.GameSocketListener;

/**
 * High-performance Bluetooth Mesh & Local P2P Multiplayer Manager for Guess the Card.
 * Enables zero-pairing room broadcasting, multi-peer socket routing (up to 5 players),
 * and zero-latency real-time packet exchange completely offline.
 */
@SuppressLint("MissingPermission")
public class BluetoothMeshManager {

    private static final String TAG = "BluetoothMesh";
    public static final UUID SERVICE_UUID = UUID.fromString(BluetoothRoomModel.SERVICE_UUID_STRING);
    private static final String SERVICE_NAME = "GuessTheCardMesh";

    public interface RoomDiscoveryListener {
        void onRoomsDiscovered(List<BluetoothRoomModel> rooms);
    }

    private final Context context;
    private final PreferenceManager preferences;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isHosting = false;
    private boolean isConnected = false;
    private boolean isScanning = false;

    private BluetoothRoomModel currentHostedRoom;
    private RoomInfo currentRoomInfo;
    private GameSocketListener activeGameListener;
    private RoomDiscoveryListener activeDiscoveryListener;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private final List<ConnectedThread> connectedPeerThreads = new CopyOnWriteArrayList<>();
    private final Map<String, BluetoothRoomModel> discoveredRoomsMap = Collections.synchronizedMap(new HashMap<>());

    private BluetoothLeAdvertiser bleAdvertiser;
    private AdvertiseCallback bleAdvertiseCallback;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleScanCallback;
    private BroadcastReceiver classicBtReceiver;

    public BluetoothMeshManager(Context context, PreferenceManager preferences) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean isHosting() {
        return isHosting;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public RoomInfo getCurrentRoomInfo() {
        return currentRoomInfo;
    }

    public void setGameListener(GameSocketListener listener) {
        this.activeGameListener = listener;
    }

    public void setDiscoveryListener(RoomDiscoveryListener listener) {
        this.activeDiscoveryListener = listener;
    }

    // ── 1. ROOM SCANNING & DISCOVERY ──────────────────────────────────────────

    public void startRoomDiscovery(RoomDiscoveryListener listener) {
        this.activeDiscoveryListener = listener;
        if (!BluetoothPermissionHelper.hasBluetoothPermissions(context) || !isBluetoothEnabled()) {
            return;
        }

        discoveredRoomsMap.clear();
        isScanning = true;

        // 1. BLE Scanner (High performance zero-pairing room detection)
        try {
            if (bluetoothAdapter != null) {
                bleScanner = bluetoothAdapter.getBluetoothLeScanner();
                if (bleScanner != null) {
                    bleScanCallback = new ScanCallback() {
                        @Override
                        public void onScanResult(int callbackType, ScanResult result) {
                            handleScanResult(result);
                        }

                        @Override
                        public void onBatchScanResults(List<ScanResult> results) {
                            for (ScanResult r : results) handleScanResult(r);
                        }

                        @Override
                        public void onScanFailed(int errorCode) {
                            Log.w(TAG, "BLE scan failed with code: " + errorCode);
                        }
                    };
                    bleScanner.startScan(bleScanCallback);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to start BLE scanner", e);
        }

        // 2. Classic BT Discovery fallback
        try {
            if (bluetoothAdapter != null) {
                if (bluetoothAdapter.isDiscovering()) bluetoothAdapter.cancelDiscovery();
                registerClassicDiscoveryReceiver();
                bluetoothAdapter.startDiscovery();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to start Classic BT discovery", e);
        }
    }

    private void handleScanResult(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        BluetoothDevice device = result.getDevice();
        String deviceName = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : device.getName();
        if (deviceName == null) deviceName = device.getName();

        if (deviceName != null && deviceName.startsWith(BluetoothRoomModel.BLE_ADVERTISE_PREFIX)) {
            BluetoothRoomModel room = BluetoothRoomModel.fromCompactBleName(deviceName, device.getAddress());
            if (room != null) {
                discoveredRoomsMap.put(room.roomId, room);
                notifyDiscoveryListener();
            }
        }
    }

    private void registerClassicDiscoveryReceiver() {
        if (classicBtReceiver != null) return;
        classicBtReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (dev != null && dev.getName() != null && dev.getName().startsWith(BluetoothRoomModel.BLE_ADVERTISE_PREFIX)) {
                        BluetoothRoomModel room = BluetoothRoomModel.fromCompactBleName(dev.getName(), dev.getAddress());
                        if (room != null) {
                            discoveredRoomsMap.put(room.roomId, room);
                            notifyDiscoveryListener();
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(classicBtReceiver, filter);
    }

    public void stopRoomDiscovery() {
        isScanning = false;
        try {
            if (bleScanner != null && bleScanCallback != null) {
                bleScanner.stopScan(bleScanCallback);
                bleScanCallback = null;
            }
        } catch (Exception ignored) {}

        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            if (classicBtReceiver != null) {
                context.unregisterReceiver(classicBtReceiver);
                classicBtReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    private void notifyDiscoveryListener() {
        mainHandler.post(() -> {
            if (activeDiscoveryListener != null) {
                List<BluetoothRoomModel> list = new ArrayList<>(discoveredRoomsMap.values());
                activeDiscoveryListener.onRoomsDiscovered(list);
            }
        });
    }

    // ── 2. HOSTING A BLUETOOTH MESH ROOM ──────────────────────────────────────

    public void startHostRoom(String roomCode, String roomName, String gameMode) {
        stopAllConnections();
        isHosting = true;
        isConnected = true;

        String myUid = preferences != null ? preferences.getUserId() : "host_uid";
        String myName = preferences != null ? preferences.getUsername() : "Host";
        String myAvatar = preferences != null ? preferences.getAvatarFileName() : "avatar_01.png";
        String address = bluetoothAdapter != null ? bluetoothAdapter.getAddress() : "00:11:22:33:44:55";

        currentHostedRoom = new BluetoothRoomModel(roomCode, roomName, gameMode, myUid, myName, myAvatar, address);

        // Initialize Lobby Room Info
        currentRoomInfo = new RoomInfo();
        currentRoomInfo.roomId = currentHostedRoom.roomId;
        currentRoomInfo.code = roomCode;
        currentRoomInfo.name = currentHostedRoom.roomName;
        currentRoomInfo.mode = gameMode;
        currentRoomInfo.hostId = myUid;
        currentRoomInfo.maxPlayers = 5;

        RoomInfo.LobbyPlayer hostPlayer = new RoomInfo.LobbyPlayer();
        hostPlayer.userId = myUid;
        hostPlayer.username = myName;
        hostPlayer.avatarFileName = myAvatar;
        hostPlayer.isHost = true;
        hostPlayer.isReady = true;
        currentRoomInfo.players.add(hostPlayer);

        // Start RFCOMM Server Socket listener
        acceptThread = new AcceptThread();
        acceptThread.start();

        // Start BLE Room Advertisement so nearby clients discover without pairing
        startBleRoomAdvertising(currentHostedRoom);
    }

    private void startBleRoomAdvertising(BluetoothRoomModel room) {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isMultipleAdvertisementSupported()) {
                bleAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                if (bleAdvertiser != null) {
                    AdvertiseSettings settings = new AdvertiseSettings.Builder()
                            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                            .setConnectable(true)
                            .build();

                    AdvertiseData data = new AdvertiseData.Builder()
                            .setIncludeDeviceName(false)
                            .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                            .build();

                    AdvertiseData scanResp = new AdvertiseData.Builder()
                            .setIncludeDeviceName(true)
                            .build();

                    // Set adapter name to compact room payload during hosting
                    bluetoothAdapter.setName(room.toCompactBleName());

                    bleAdvertiseCallback = new AdvertiseCallback() {
                        @Override
                        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                            Log.d(TAG, "BLE Room Advertisement started: " + room.toCompactBleName());
                        }

                        @Override
                        public void onStartFailure(int errorCode) {
                            Log.w(TAG, "BLE Advertisement failed: " + errorCode);
                        }
                    };

                    bleAdvertiser.startAdvertising(settings, data, scanResp, bleAdvertiseCallback);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to start BLE Advertising", e);
        }
    }

    // ── 3. CONNECTING TO A BLUETOOTH ROOM AS CLIENT ───────────────────────────

    public void joinBluetoothRoom(String hostDeviceAddress, String roomCode, String mode) {
        stopAllConnections();
        isHosting = false;

        if (bluetoothAdapter == null || hostDeviceAddress == null) return;
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(hostDeviceAddress);
        if (device == null) return;

        connectThread = new ConnectThread(device, roomCode, mode);
        connectThread.start();
    }

    // ── 4. THREADED SOCKET NETWORKING (RFCOMM) ────────────────────────────────

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
            } catch (Exception e) {
                Log.e(TAG, "listenUsingRfcomm failed", e);
            }
            serverSocket = tmp;
        }

        @Override
        public void run() {
            setName("BT-AcceptThread");
            while (isHosting && serverSocket != null) {
                try {
                    BluetoothSocket socket = serverSocket.accept();
                    if (socket != null) {
                        if (connectedPeerThreads.size() < 4) { // Max 5 players (host + 4 clients)
                            ConnectedThread conn = new ConnectedThread(socket);
                            connectedPeerThreads.add(conn);
                            conn.start();
                        } else {
                            try { socket.close(); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) serverSocket.close();
            } catch (Exception ignored) {}
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private final String roomCode;
        private final String mode;

        public ConnectThread(BluetoothDevice device, String roomCode, String mode) {
            this.device = device;
            this.roomCode = roomCode;
            this.mode = mode;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
            } catch (Exception e) {
                Log.e(TAG, "createRfcommSocket failed", e);
            }
            socket = tmp;
        }

        @Override
        public void run() {
            setName("BT-ConnectThread");
            try {
                if (bluetoothAdapter != null) bluetoothAdapter.cancelDiscovery();
                socket.connect();
            } catch (Exception connectException) {
                try { socket.close(); } catch (Exception ignored) {}
                mainHandler.post(() -> {
                    if (activeGameListener != null) activeGameListener.onError("Failed to connect to Bluetooth room");
                });
                return;
            }

            ConnectedThread conn = new ConnectedThread(socket);
            connectedPeerThreads.add(conn);
            conn.start();

            isConnected = true;

            // Send Join message to Host
            String myUid = preferences != null ? preferences.getUserId() : ("peer_" + System.currentTimeMillis());
            String myName = preferences != null ? preferences.getUsername() : "Player";
            String myAvatar = preferences != null ? preferences.getAvatarFileName() : "avatar_01.png";

            try {
                JSONObject joinObj = new JSONObject();
                joinObj.put("type", "LOBBY_JOIN");
                joinObj.put("uid", myUid);
                joinObj.put("username", myName);
                joinObj.put("avatarFileName", myAvatar);
                conn.writeMessage(joinObj.toString());
            } catch (Exception ignored) {}
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;
        private boolean isRunning = true;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (Exception ignored) {}
            inStream = tmpIn;
            outStream = tmpOut;
        }

        @Override
        public void run() {
            setName("BT-ConnectedThread-" + getId());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
            while (isRunning) {
                try {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) {
                        handleIncomingPacket(line.trim(), this);
                    }
                } catch (Exception e) {
                    break;
                }
            }
            connectedPeerThreads.remove(this);
            try { socket.close(); } catch (Exception ignored) {}
        }

        public void writeMessage(String message) {
            try {
                if (outStream != null) {
                    byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
                    outStream.write(bytes);
                    outStream.flush();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to write Bluetooth message", e);
            }
        }

        public void cancel() {
            isRunning = false;
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {}
        }
    }

    // ── 5. PACKET ROUTING & GAME PROTOCOL ─────────────────────────────────────

    private void handleIncomingPacket(String jsonStr, ConnectedThread sourceThread) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String type = obj.optString("type");

            if (isHosting) {
                // Host relays message to all other connected peers (Mesh / Star routing)
                for (ConnectedThread peer : connectedPeerThreads) {
                    if (peer != sourceThread) {
                        peer.writeMessage(jsonStr);
                    }
                }
            }

            mainHandler.post(() -> processGamePacket(type, obj));
        } catch (Exception e) {
            Log.e(TAG, "Error handling packet: " + jsonStr, e);
        }
    }

    private void processGamePacket(String type, JSONObject obj) {
        if ("LOBBY_JOIN".equals(type)) {
            if (isHosting && currentRoomInfo != null) {
                String uid = obj.optString("uid");
                String name = obj.optString("username", "Player");
                String avatar = obj.optString("avatarFileName", "avatar_01.png");

                boolean exists = false;
                for (RoomInfo.LobbyPlayer p : currentRoomInfo.players) {
                    if (uid.equals(p.userId)) { exists = true; break; }
                }
                if (!exists) {
                    RoomInfo.LobbyPlayer p = new RoomInfo.LobbyPlayer();
                    p.userId = uid;
                    p.username = name;
                    p.avatarFileName = avatar;
                    p.isReady = false;
                    currentRoomInfo.players.add(p);
                }
                broadcastLobbyUpdate();
            }
        } else if ("LOBBY_UPDATE".equals(type)) {
            JSONObject roomObj = obj.optJSONObject("room");
            if (roomObj != null) {
                currentRoomInfo = RoomInfo.fromJson(roomObj);
                if (activeGameListener != null) {
                    activeGameListener.onRoomUpdated(currentRoomInfo);
                }
            }
        } else if ("TOGGLE_READY".equals(type)) {
            if (isHosting && currentRoomInfo != null) {
                String uid = obj.optString("uid");
                boolean ready = obj.optBoolean("isReady", false);
                for (RoomInfo.LobbyPlayer p : currentRoomInfo.players) {
                    if (uid.equals(p.userId)) {
                        p.isReady = ready;
                        break;
                    }
                }
                broadcastLobbyUpdate();
            }
        } else if ("GAME_START".equals(type)) {
            String mode = obj.optString("mode", "ANIMALS");
            long duration = obj.optLong("duration", 60000L);
            String card = obj.optString("card", "");
            String ansUid = obj.optString("answererUid", "");

            if (currentRoomInfo != null) {
                currentRoomInfo.card = card;
                currentRoomInfo.currentTurnPlayerId = ansUid;
            }

            if (activeGameListener != null) {
                activeGameListener.onGameStart(mode, duration);
            }
        } else if ("QUESTION_ASKED".equals(type)) {
            String q = obj.optString("question");
            String asker = obj.optString("askerName");
            if (activeGameListener != null) {
                activeGameListener.onQuestionAsked(q, asker);
            }
        } else if ("ANSWER_GIVEN".equals(type)) {
            String q = obj.optString("question");
            String a = obj.optString("answer");
            String answerer = obj.optString("answererName");
            if (activeGameListener != null) {
                activeGameListener.onAnswerGiven(q, a, answerer);
            }
        } else if ("SUBMIT_GUESS".equals(type)) {
            String guessedBy = obj.optString("guessedBy");
            String guess = obj.optString("guess");
            boolean isCorrect = obj.optBoolean("isCorrect", false);
            int score = obj.optInt("scoreAwarded", 0);
            String cardAns = obj.optString("cardAnswer", "");
            if (activeGameListener != null) {
                activeGameListener.onGuessResult(guessedBy, guess, isCorrect, score, cardAns);
            }
        } else if ("TURN_STARTED".equals(type)) {
            String nextAns = obj.optString("nextTurnPlayerId");
            int round = obj.optInt("currentRound", 1);
            if (activeGameListener != null) {
                activeGameListener.onTurnStarted(nextAns, round);
            }
        } else if ("CHAT_MESSAGE".equals(type)) {
            String sName = obj.optString("senderName", "Player");
            String txt = obj.optString("text", "");
            long ts = obj.optLong("timestamp", System.currentTimeMillis());
            ChatMessage msg = new ChatMessage(sName, txt, ts);
            if (activeGameListener != null) {
                activeGameListener.onChatMessage(msg);
            }
        }
    }

    // ── 6. PUBLIC API FOR LOBBY & GAME ACTIONS ────────────────────────────────

    public void broadcastLobbyUpdate() {
        if (!isHosting || currentRoomInfo == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "LOBBY_UPDATE");

            JSONObject roomObj = new JSONObject();
            roomObj.put("roomId", currentRoomInfo.roomId);
            roomObj.put("code", currentRoomInfo.code);
            roomObj.put("name", currentRoomInfo.name);
            roomObj.put("mode", currentRoomInfo.mode);
            roomObj.put("hostId", currentRoomInfo.hostId);

            JSONArray pArr = new JSONArray();
            for (RoomInfo.LobbyPlayer p : currentRoomInfo.players) {
                JSONObject pObj = new JSONObject();
                pObj.put("userId", p.userId);
                pObj.put("username", p.username);
                pObj.put("avatarFileName", p.avatarFileName);
                pObj.put("isReady", p.isReady);
                pObj.put("isHost", p.isHost);
                pObj.put("score", p.score);
                pArr.put(pObj);
            }
            roomObj.put("players", pArr);
            obj.put("room", roomObj);

            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onRoomUpdated(currentRoomInfo);
            }
        } catch (Exception ignored) {}
    }

    public void toggleReady(boolean ready) {
        String myUid = preferences != null ? preferences.getUserId() : "";
        if (isHosting) {
            if (currentRoomInfo != null) {
                for (RoomInfo.LobbyPlayer p : currentRoomInfo.players) {
                    if (myUid.equals(p.userId)) {
                        p.isReady = ready;
                        break;
                    }
                }
                broadcastLobbyUpdate();
            }
        } else {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", "TOGGLE_READY");
                obj.put("uid", myUid);
                obj.put("isReady", ready);
                sendBroadcastMessage(obj.toString());
            } catch (Exception ignored) {}
        }
    }

    public void startBluetoothGame(String mode, String firstAnswererUid, String secretCard) {
        if (!isHosting) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "GAME_START");
            obj.put("mode", mode);
            obj.put("duration", 60000L);
            obj.put("answererUid", firstAnswererUid);
            obj.put("card", secretCard);
            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onGameStart(mode, 60000L);
            }
        } catch (Exception ignored) {}
    }

    public void askQuestion(String question, String askerName) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "QUESTION_ASKED");
            obj.put("question", question);
            obj.put("askerName", askerName);
            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onQuestionAsked(question, askerName);
            }
        } catch (Exception ignored) {}
    }

    public void answerQuestion(String question, String answer, String answererName) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "ANSWER_GIVEN");
            obj.put("question", question);
            obj.put("answer", answer);
            obj.put("answererName", answererName);
            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onAnswerGiven(question, answer, answererName);
            }
        } catch (Exception ignored) {}
    }

    public void submitGuess(String guess, String guessedBy, boolean isCorrect, int scoreAwarded, String cardAnswer) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "SUBMIT_GUESS");
            obj.put("guess", guess);
            obj.put("guessedBy", guessedBy);
            obj.put("isCorrect", isCorrect);
            obj.put("scoreAwarded", scoreAwarded);
            obj.put("cardAnswer", cardAnswer);
            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onGuessResult(guessedBy, guess, isCorrect, scoreAwarded, cardAnswer);
            }
        } catch (Exception ignored) {}
    }

    public void sendTurnStarted(String nextTurnPlayerId, int currentRound) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "TURN_STARTED");
            obj.put("nextTurnPlayerId", nextTurnPlayerId);
            obj.put("currentRound", currentRound);
            sendBroadcastMessage(obj.toString());

            if (activeGameListener != null) {
                activeGameListener.onTurnStarted(nextTurnPlayerId, currentRound);
            }
        } catch (Exception ignored) {}
    }

    public void sendChatMessage(String text) {
        String name = preferences != null ? preferences.getUsername() : "Player";
        try {
            long ts = System.currentTimeMillis();
            JSONObject obj = new JSONObject();
            obj.put("type", "CHAT_MESSAGE");
            obj.put("senderName", name);
            obj.put("text", text);
            obj.put("timestamp", ts);
            sendBroadcastMessage(obj.toString());

            ChatMessage msg = new ChatMessage(name, text, ts);
            if (activeGameListener != null) {
                activeGameListener.onChatMessage(msg);
            }
        } catch (Exception ignored) {}
    }

    public void sendBroadcastMessage(String message) {
        for (ConnectedThread peer : connectedPeerThreads) {
            peer.writeMessage(message);
        }
    }

    // ── 7. CLEANUP & TEARDOWN ─────────────────────────────────────────────────

    public void stopAllConnections() {
        isHosting = false;
        isConnected = false;

        stopRoomDiscovery();

        try {
            if (bleAdvertiser != null && bleAdvertiseCallback != null) {
                bleAdvertiser.stopAdvertising(bleAdvertiseCallback);
                bleAdvertiseCallback = null;
            }
        } catch (Exception ignored) {}

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        for (ConnectedThread conn : connectedPeerThreads) {
            conn.cancel();
        }
        connectedPeerThreads.clear();
        currentHostedRoom = null;
        currentRoomInfo = null;
    }

    public void release() {
        stopAllConnections();
        activeGameListener = null;
        activeDiscoveryListener = null;
    }
}
