package com.himacasino.games.poker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Standard 52-card deck, shuffled with {@link Collections#shuffle(List)}. */
public class Deck {

    private final Deque<Card> cards = new ArrayDeque<>();

    public Deck() {
        reshuffle();
    }

    /** Rebuilds a fresh 52-card deck and shuffles it. */
    public void reshuffle() {
        List<Card> fresh = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 1; rank <= 13; rank++) {
                fresh.add(new Card(suit, rank));
            }
        }
        Collections.shuffle(fresh);
        cards.clear();
        cards.addAll(fresh);
    }

    /** Draws the next card, reshuffling a fresh deck first if empty. */
    public Card draw() {
        if (cards.isEmpty()) reshuffle();
        return cards.poll();
    }
}
