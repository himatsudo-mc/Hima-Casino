package com.himacasino.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** 52 枚のデッキ。空になったら自動でシャッフルし直します。 */
public class Deck {

    private final Deque<Card> cards = new ArrayDeque<>();
    private final Random rng;

    public Deck(Random rng) {
        this.rng = rng;
        reshuffle();
    }

    public void reshuffle() {
        List<Card> fresh = new ArrayList<>(52);
        for (Card.Suit s : Card.Suit.values()) {
            for (int r = 1; r <= 13; r++) fresh.add(new Card(s, r));
        }
        Collections.shuffle(fresh, rng);
        cards.clear();
        cards.addAll(fresh);
    }

    public Card draw() {
        if (cards.isEmpty()) reshuffle();
        return cards.poll();
    }

    /** テスト用: 次に引くカードを指定順で積む */
    public void stack(List<Card> top) {
        for (int i = top.size() - 1; i >= 0; i--) cards.addFirst(top.get(i));
    }
}
