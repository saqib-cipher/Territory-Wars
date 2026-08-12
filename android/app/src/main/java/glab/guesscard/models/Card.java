package glab.guesscard.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Card model representing a Guess the Card entity.
 * Includes embedded local card banks for Animals, Food, Countries, and Celebrities
 * to support instant offline practice mode.
 */
public class Card {
    public String id;
    public String word;
    public String category;
    public List<String> aliases;
    public String profession;
    public String country;
    public String knownFor;
    public String hint;

    public Card() {
        this.aliases = new ArrayList<>();
    }

    public Card(String id, String word, String category, String... aliases) {
        this.id = id;
        this.word = word;
        this.category = category;
        this.aliases = new ArrayList<>(Arrays.asList(aliases));
        if (!this.aliases.contains(word)) {
            this.aliases.add(0, word);
        }
    }

    public static Card createCelebrity(String id, String word, String category, String profession, String country, String knownFor, String... aliases) {
        Card c = new Card(id, word, category, aliases);
        c.profession = profession;
        c.country = country;
        c.knownFor = knownFor;
        return c;
    }

    public boolean matchesGuess(String guess) {
        if (guess == null) return false;
        String cleanInput = guess.trim().toLowerCase();
        if (cleanInput.isEmpty()) return false;

        if (word != null && word.trim().toLowerCase().equals(cleanInput)) {
            return true;
        }

        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && alias.trim().toLowerCase().equals(cleanInput)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Embedded Card Banks for Offline Mode ----

    public static List<Card> getAnimals() {
        List<Card> list = new ArrayList<>();
        list.add(new Card("anim_1", "Lion", "Animals", "Lion", "Lions"));
        list.add(new Card("anim_2", "Tiger", "Animals", "Tiger", "Tigers"));
        list.add(new Card("anim_3", "Elephant", "Animals", "Elephant", "Elephants"));
        list.add(new Card("anim_4", "Dog", "Animals", "Dog", "Dogs", "Puppy"));
        list.add(new Card("anim_5", "Cat", "Animals", "Cat", "Cats", "Kitten"));
        list.add(new Card("anim_6", "Penguin", "Animals", "Penguin", "Penguins"));
        list.add(new Card("anim_7", "Shark", "Animals", "Shark", "Sharks"));
        list.add(new Card("anim_8", "Dolphin", "Animals", "Dolphin", "Dolphins"));
        list.add(new Card("anim_9", "Eagle", "Animals", "Eagle", "Eagles"));
        list.add(new Card("anim_10", "Horse", "Animals", "Horse", "Horses"));
        list.add(new Card("anim_11", "Giraffe", "Animals", "Giraffe", "Giraffes"));
        list.add(new Card("anim_12", "Zebra", "Animals", "Zebra", "Zebras"));
        list.add(new Card("anim_13", "Crocodile", "Animals", "Crocodile", "Crocodiles", "Croc", "Alligator"));
        list.add(new Card("anim_14", "Kangaroo", "Animals", "Kangaroo", "Kangaroos", "Roo"));
        list.add(new Card("anim_15", "Panda", "Animals", "Panda", "Pandas", "Panda Bear"));
        list.add(new Card("anim_16", "Bear", "Animals", "Bear", "Bears", "Grizzly"));
        list.add(new Card("anim_17", "Wolf", "Animals", "Wolf", "Wolves"));
        list.add(new Card("anim_18", "Rabbit", "Animals", "Rabbit", "Rabbits", "Bunny"));
        list.add(new Card("anim_19", "Snake", "Animals", "Snake", "Snakes", "Serpent"));
        list.add(new Card("anim_20", "Turtle", "Animals", "Turtle", "Turtles", "Tortoise"));
        return list;
    }

    public static List<Card> getFood() {
        List<Card> list = new ArrayList<>();
        list.add(new Card("food_1", "Pizza", "Food", "Pizza", "Pizzas"));
        list.add(new Card("food_2", "Burger", "Food", "Burger", "Hamburger", "Cheeseburger"));
        list.add(new Card("food_3", "Biryani", "Food", "Biryani", "Briyani"));
        list.add(new Card("food_4", "Sushi", "Food", "Sushi"));
        list.add(new Card("food_5", "Apple", "Food", "Apple", "Apples"));
        list.add(new Card("food_6", "Banana Pro", "Food / Gadgets", "Banana Pro", "Banana", "Bananas", "Banana Pi Pro", "Banana Board"));
        list.add(new Card("food_7", "Mango", "Food", "Mango", "Mangoes"));
        list.add(new Card("food_8", "Pasta", "Food", "Pasta", "Spaghetti", "Macaroni"));
        list.add(new Card("food_9", "Ice Cream", "Food", "Ice Cream", "Icecream"));
        list.add(new Card("food_10", "Chocolate", "Food", "Chocolate", "Chocolates"));
        list.add(new Card("food_11", "Sandwich", "Food", "Sandwich", "Sandwiches"));
        list.add(new Card("food_12", "Taco", "Food", "Taco", "Tacos"));
        list.add(new Card("food_13", "Noodles", "Food", "Noodles", "Noodle", "Ramen"));
        list.add(new Card("food_14", "Cake", "Food", "Cake", "Cakes", "Pastry"));
        list.add(new Card("food_15", "Dosa", "Food", "Dosa", "Dosai"));
        return list;
    }

    public static List<Card> getCountries() {
        List<Card> list = new ArrayList<>();
        list.add(new Card("cntry_1", "India", "Countries", "India", "Bharat"));
        list.add(new Card("cntry_2", "Japan", "Countries", "Japan", "Nippon"));
        list.add(new Card("cntry_3", "Brazil", "Countries", "Brazil", "Brasil"));
        list.add(new Card("cntry_4", "USA", "Countries", "USA", "United States", "United States of America", "America"));
        list.add(new Card("cntry_5", "France", "Countries", "France"));
        list.add(new Card("cntry_6", "Italy", "Countries", "Italy", "Italia"));
        list.add(new Card("cntry_7", "Australia", "Countries", "Australia"));
        list.add(new Card("cntry_8", "Canada", "Countries", "Canada"));
        list.add(new Card("cntry_9", "Germany", "Countries", "Germany", "Deutschland"));
        list.add(new Card("cntry_10", "Egypt", "Countries", "Egypt"));
        list.add(new Card("cntry_11", "China", "Countries", "China"));
        list.add(new Card("cntry_12", "UK", "Countries", "UK", "United Kingdom", "Britain", "England"));
        list.add(new Card("cntry_13", "Spain", "Countries", "Spain", "Espana"));
        list.add(new Card("cntry_14", "Mexico", "Countries", "Mexico"));
        list.add(new Card("cntry_15", "South Korea", "Countries", "South Korea", "Korea"));
        return list;
    }

    public static List<Card> getCelebrities() {
        List<Card> list = new ArrayList<>();
        list.add(Card.createCelebrity("celeb_1", "Cristiano Ronaldo", "Celebrities", "Footballer", "Portugal", "5-time Ballon dOr winner", "Ronaldo", "CR7"));
        list.add(Card.createCelebrity("celeb_2", "Lionel Messi", "Celebrities", "Footballer", "Argentina", "World Cup champion", "Messi", "Leo Messi"));
        list.add(Card.createCelebrity("celeb_3", "Taylor Swift", "Celebrities", "Singer-Songwriter", "USA", "Eras Tour", "Swift"));
        list.add(Card.createCelebrity("celeb_4", "Shah Rukh Khan", "Celebrities", "Actor", "India", "King of Bollywood", "SRK", "Shahrukh Khan"));
        list.add(Card.createCelebrity("celeb_5", "Elon Musk", "Celebrities", "Entrepreneur", "USA", "Tesla and SpaceX", "Musk"));
        return list;
    }

    public static Card getRandomCardForMode(GameMode mode) {
        List<Card> pool;
        switch (mode) {
            case FOOD:
                pool = getFood();
                break;
            case COUNTRIES:
                pool = getCountries();
                break;
            case CELEBRITIES:
                pool = getCelebrities();
                break;
            case ANIMALS:
            default:
                pool = getAnimals();
                break;
        }
        Random r = new Random();
        return pool.get(r.nextInt(pool.size()));
    }
}
