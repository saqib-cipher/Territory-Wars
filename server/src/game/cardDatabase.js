'use strict';

/**
 * Predefined Card Database for Guess the Card modes:
 * - ANIMALS
 * - FOOD
 * - COUNTRIES
 * - CELEBRITIES
 *
 * Provides authoritative card selection and fuzzy answer matching.
 */

const CARD_DATA = {
  ANIMALS: {
    questionLimit: 10,
    roundTimeSeconds: 60,
    cards: [
      { id: 'anim_1', word: 'Lion', aliases: ['Lion', 'Lions'] },
      { id: 'anim_2', word: 'Tiger', aliases: ['Tiger', 'Tigers'] },
      { id: 'anim_3', word: 'Elephant', aliases: ['Elephant', 'Elephants'] },
      { id: 'anim_4', word: 'Dog', aliases: ['Dog', 'Dogs', 'Puppy'] },
      { id: 'anim_5', word: 'Cat', aliases: ['Cat', 'Cats', 'Kitten'] },
      { id: 'anim_6', word: 'Penguin', aliases: ['Penguin', 'Penguins'] },
      { id: 'anim_7', word: 'Shark', aliases: ['Shark', 'Sharks'] },
      { id: 'anim_8', word: 'Dolphin', aliases: ['Dolphin', 'Dolphins'] },
      { id: 'anim_9', word: 'Eagle', aliases: ['Eagle', 'Eagles'] },
      { id: 'anim_10', word: 'Horse', aliases: ['Horse', 'Horses'] },
      { id: 'anim_11', word: 'Giraffe', aliases: ['Giraffe', 'Giraffes'] },
      { id: 'anim_12', word: 'Zebra', aliases: ['Zebra', 'Zebras'] },
      { id: 'anim_13', word: 'Crocodile', aliases: ['Crocodile', 'Crocodiles', 'Croc', 'Alligator'] },
      { id: 'anim_14', word: 'Kangaroo', aliases: ['Kangaroo', 'Kangaroos', 'Roo'] },
      { id: 'anim_15', word: 'Panda', aliases: ['Panda', 'Pandas', 'Panda Bear'] },
      { id: 'anim_16', word: 'Bear', aliases: ['Bear', 'Bears', 'Grizzly'] },
      { id: 'anim_17', word: 'Wolf', aliases: ['Wolf', 'Wolves'] },
      { id: 'anim_18', word: 'Rabbit', aliases: ['Rabbit', 'Rabbits', 'Bunny'] },
      { id: 'anim_19', word: 'Snake', aliases: ['Snake', 'Snakes', 'Serpent'] },
      { id: 'anim_20', word: 'Turtle', aliases: ['Turtle', 'Turtles', 'Tortoise'] },
    ],
  },
  FOOD: {
    questionLimit: 8,
    roundTimeSeconds: 60,
    cards: [
      { id: 'food_1', word: 'Pizza', aliases: ['Pizza', 'Pizzas'] },
      { id: 'food_2', word: 'Burger', aliases: ['Burger', 'Hamburger', 'Cheeseburger', 'Burgers'] },
      { id: 'food_3', word: 'Biryani', aliases: ['Biryani', 'Briyani'] },
      { id: 'food_4', word: 'Sushi', aliases: ['Sushi'] },
      { id: 'food_5', word: 'Apple', aliases: ['Apple', 'Apples'] },
      { id: 'food_6', word: 'Banana', aliases: ['Banana', 'Bananas'] },
      { id: 'food_7', word: 'Mango', aliases: ['Mango', 'Mangoes'] },
      { id: 'food_8', word: 'Pasta', aliases: ['Pasta', 'Spaghetti', 'Macaroni'] },
      { id: 'food_9', word: 'Ice Cream', aliases: ['Ice Cream', 'Icecream'] },
      { id: 'food_10', word: 'Chocolate', aliases: ['Chocolate', 'Chocolates'] },
      { id: 'food_11', word: 'Sandwich', aliases: ['Sandwich', 'Sandwiches'] },
      { id: 'food_12', word: 'Taco', aliases: ['Taco', 'Tacos'] },
      { id: 'food_13', word: 'Noodles', aliases: ['Noodles', 'Noodle', 'Ramen'] },
      { id: 'food_14', word: 'Cake', aliases: ['Cake', 'Cakes', 'Pastry'] },
      { id: 'food_15', word: 'Dosa', aliases: ['Dosa', 'Dosai'] },
    ],
  },
  COUNTRIES: {
    questionLimit: 10,
    roundTimeSeconds: 60,
    cards: [
      { id: 'cntry_1', word: 'India', aliases: ['India', 'Bharat'] },
      { id: 'cntry_2', word: 'Japan', aliases: ['Japan', 'Nippon'] },
      { id: 'cntry_3', word: 'Brazil', aliases: ['Brazil', 'Brasil'] },
      { id: 'cntry_4', word: 'USA', aliases: ['USA', 'United States', 'United States of America', 'America'] },
      { id: 'cntry_5', word: 'France', aliases: ['France'] },
      { id: 'cntry_6', word: 'Italy', aliases: ['Italy', 'Italia'] },
      { id: 'cntry_7', word: 'Australia', aliases: ['Australia'] },
      { id: 'cntry_8', word: 'Canada', aliases: ['Canada'] },
      { id: 'cntry_9', word: 'Germany', aliases: ['Germany', 'Deutschland'] },
      { id: 'cntry_10', word: 'Egypt', aliases: ['Egypt'] },
      { id: 'cntry_11', word: 'China', aliases: ['China'] },
      { id: 'cntry_12', word: 'UK', aliases: ['UK', 'United Kingdom', 'Britain', 'Great Britain', 'England'] },
      { id: 'cntry_13', word: 'Spain', aliases: ['Spain', 'Espana'] },
      { id: 'cntry_14', word: 'Mexico', aliases: ['Mexico'] },
      { id: 'cntry_15', word: 'South Korea', aliases: ['South Korea', 'Korea'] },
    ],
  },
  CELEBRITIES: {
    questionLimit: 12,
    roundTimeSeconds: 60,
    cards: [
      {
        id: 'celeb_1',
        word: 'Cristiano Ronaldo',
        aliases: ['Cristiano Ronaldo', 'Ronaldo', 'CR7'],
        category: 'Athletes',
        profession: 'Footballer',
        country: 'Portugal',
        knownFor: '5-time Ballon dOr winner',
      },
      {
        id: 'celeb_2',
        word: 'Lionel Messi',
        aliases: ['Lionel Messi', 'Messi', 'Leo Messi'],
        category: 'Athletes',
        profession: 'Footballer',
        country: 'Argentina',
        knownFor: 'World Cup champion',
      },
      {
        id: 'celeb_3',
        word: 'Taylor Swift',
        aliases: ['Taylor Swift', 'Swift'],
        category: 'Musicians',
        profession: 'Singer-Songwriter',
        country: 'USA',
        knownFor: 'Eras Tour',
      },
      {
        id: 'celeb_4',
        word: 'Shah Rukh Khan',
        aliases: ['Shah Rukh Khan', 'SRK', 'Shahrukh Khan'],
        category: 'Actors',
        profession: 'Actor',
        country: 'India',
        knownFor: 'King of Bollywood',
      },
      {
        id: 'celeb_5',
        word: 'Elon Musk',
        aliases: ['Elon Musk', 'Musk'],
        category: 'Creators',
        profession: 'Entrepreneur',
        country: 'USA',
        knownFor: 'Tesla and SpaceX',
      },
    ],
  },
};

function getModeConfig(modeKey) {
  const normalized = (modeKey || 'ANIMALS').toUpperCase();
  return CARD_DATA[normalized] || CARD_DATA.ANIMALS;
}

function getRandomCard(modeKey, excludeIds = []) {
  const config = getModeConfig(modeKey);
  const available = config.cards.filter((c) => !excludeIds.includes(c.id));
  const pool = available.length > 0 ? available : config.cards;
  const index = Math.floor(Math.random() * pool.length);
  return pool[index];
}

function validateGuess(card, userGuess) {
  if (!card || !userGuess) return false;
  const cleanInput = userGuess.trim().toLowerCase();
  if (cleanInput.length === 0) return false;

  const mainWordMatch = card.word.trim().toLowerCase() === cleanInput;
  if (mainWordMatch) return true;

  if (card.aliases && Array.isArray(card.aliases)) {
    return card.aliases.some((a) => a.trim().toLowerCase() === cleanInput);
  }
  return false;
}

module.exports = {
  CARD_DATA,
  getModeConfig,
  getRandomCard,
  validateGuess,
};
