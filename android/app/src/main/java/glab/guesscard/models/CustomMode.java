package glab.guesscard.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Custom Mode configuration and card pack model.
 * Supports host-defined custom packs with add/edit/delete/reorder and JSON import/export.
 */
public class CustomMode {
    public String id;
    public String name;
    public String category;
    public String description;
    public int questionLimit;
    public int roundTimeSeconds;
    public int maxPlayers;
    public int roundCount;
    public List<Card> cards;

    public CustomMode() {
        this.id = UUID.randomUUID().toString();
        this.name = "Custom Party Pack";
        this.category = "Custom";
        this.description = "User created custom cards";
        this.questionLimit = 10;
        this.roundTimeSeconds = 60;
        this.maxPlayers = 5;
        this.roundCount = 5;
        this.cards = new ArrayList<>();
    }

    public void addCard(String answer, String... aliases) {
        String cardId = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        Card c = new Card(cardId, answer, category, aliases);
        cards.add(c);
    }

    public void removeCard(int index) {
        if (index >= 0 && index < cards.size()) {
            cards.remove(index);
        }
    }

    public void reorderCard(int fromIndex, int toIndex) {
        if (fromIndex >= 0 && fromIndex < cards.size() && toIndex >= 0 && toIndex < cards.size()) {
            Card moved = cards.remove(fromIndex);
            cards.add(toIndex, moved);
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("category", category);
        obj.put("description", description);
        obj.put("questionLimit", questionLimit);
        obj.put("roundTimeSeconds", roundTimeSeconds);
        obj.put("maxPlayers", maxPlayers);
        obj.put("roundCount", roundCount);

        JSONArray cardArray = new JSONArray();
        for (Card c : cards) {
            JSONObject cardObj = new JSONObject();
            cardObj.put("id", c.id);
            cardObj.put("word", c.word);
            cardObj.put("category", c.category);

            JSONArray aliasArray = new JSONArray();
            if (c.aliases != null) {
                for (String a : c.aliases) {
                    aliasArray.put(a);
                }
            }
            cardObj.put("aliases", aliasArray);
            cardArray.put(cardObj);
        }
        obj.put("cards", cardArray);
        return obj;
    }

    public static CustomMode fromJson(JSONObject obj) {
        CustomMode mode = new CustomMode();
        if (obj == null) return mode;

        mode.id = obj.optString("id", UUID.randomUUID().toString());
        mode.name = obj.optString("name", "Custom Party Pack");
        mode.category = obj.optString("category", "Custom");
        mode.description = obj.optString("description", "");
        mode.questionLimit = obj.optInt("questionLimit", 10);
        mode.roundTimeSeconds = obj.optInt("roundTimeSeconds", 60);
        mode.maxPlayers = obj.optInt("maxPlayers", 5);
        mode.roundCount = obj.optInt("roundCount", 5);

        JSONArray cardArray = obj.optJSONArray("cards");
        if (cardArray != null) {
            for (int i = 0; i < cardArray.length(); i++) {
                JSONObject cardObj = cardArray.optJSONObject(i);
                if (cardObj != null) {
                    Card c = new Card();
                    c.id = cardObj.optString("id", "c_" + i);
                    c.word = cardObj.optString("word", "");
                    c.category = cardObj.optString("category", mode.category);
                    JSONArray aliasArray = cardObj.optJSONArray("aliases");
                    if (aliasArray != null) {
                        for (int j = 0; j < aliasArray.length(); j++) {
                            c.aliases.add(aliasArray.optString(j));
                        }
                    }
                    mode.cards.add(c);
                }
            }
        }
        return mode;
    }
}
