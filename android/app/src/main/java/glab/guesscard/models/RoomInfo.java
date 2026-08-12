package glab.guesscard.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class RoomInfo {
    public String roomId;
    public String code;
    public String mode;
    public String hostId;
    public int maxPlayers = 5;
    public int questionLimit = 10;
    public int roundTimeSeconds = 60;
    public String currentTurnPlayerId;
    public int currentRound = 1;
    public int totalRounds = 5;
    public String card;
    public boolean isGuesser;
    public int questionsRemaining = 10;
    public List<QuestionItem> questionHistory = new ArrayList<>();
    public List<LobbyPlayer> players = new ArrayList<>();

    public static class QuestionItem {
        public String id;
        public String question;
        public String askedBy;
        public String askerName;   // Display name of the person who asked
        public String answer;       // 'YES 👍', 'NO 👎', or null
        public String answererName; // Display name of the person who answered
    }

    public static class LobbyPlayer {
        public String userId;
        public String username;
        public String avatarId;
        public String avatarFileName;
        public boolean isReady;
        public boolean isHost;
        public int score;
    }

    public static RoomInfo fromJson(JSONObject json) {
        RoomInfo r = new RoomInfo();
        if (json == null) return r;

        r.roomId = json.optString("roomId");
        r.code = json.optString("code", r.roomId);
        r.mode = json.optString("mode", "ANIMALS");
        r.hostId = json.optString("hostId");
        r.maxPlayers = json.optInt("maxPlayers", 5);
        r.questionLimit = json.optInt("questionLimit", 10);
        r.roundTimeSeconds = json.optInt("roundTimeSeconds", 60);
        r.currentTurnPlayerId = json.optString("currentTurnPlayerId", "");
        r.currentRound = json.optInt("currentRound", 1);
        r.totalRounds = json.optInt("totalRounds", 5);
        r.card = json.optString("card", null);
        r.isGuesser = json.optBoolean("isGuesser", false);
        r.questionsRemaining = json.optInt("questionsRemaining", 10);

        JSONArray playerArray = json.optJSONArray("players");
        if (playerArray != null) {
            for (int i = 0; i < playerArray.length(); i++) {
                JSONObject pObj = playerArray.optJSONObject(i);
                if (pObj != null) {
                    LobbyPlayer p = new LobbyPlayer();
                    p.userId = pObj.optString("userId");
                    p.username = pObj.optString("username", "Player");
                    p.avatarId = pObj.optString("avatarId", "default");
                    p.isReady = pObj.optBoolean("isReady", false);
                    p.isHost = pObj.optBoolean("isHost", false);
                    p.score = pObj.optInt("score", 0);
                    r.players.add(p);
                }
            }
        }

        JSONArray qArray = json.optJSONArray("questionHistory");
        if (qArray != null) {
            for (int i = 0; i < qArray.length(); i++) {
                JSONObject qObj = qArray.optJSONObject(i);
                if (qObj != null) {
                    QuestionItem item = new QuestionItem();
                    item.id = qObj.optString("id");
                    item.question = qObj.optString("question");
                    item.askedBy = qObj.optString("askedBy");
                    item.answer = qObj.optString("answer", null);
                    r.questionHistory.add(item);
                }
            }
        }

        return r;
    }
}
