package com.himacasino.game;

import com.google.gson.JsonObject;

/** トランプ 1 枚。rank は 1(A)〜13(K)。 */
public record Card(Suit suit, int rank) {

    public enum Suit {
        SPADE("S", "♠", false), HEART("H", "♥", true), DIAMOND("D", "♦", true), CLUB("C", "♣", false);

        public final String code;
        public final String symbol;
        public final boolean red;

        Suit(String code, String symbol, boolean red) {
            this.code = code;
            this.symbol = symbol;
            this.red = red;
        }
    }

    public Card {
        if (rank < 1 || rank > 13) throw new IllegalArgumentException("rank must be 1-13: " + rank);
    }

    public String rankName() {
        return rankName(rank);
    }

    public static String rankName(int rank) {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(rank);
        };
    }

    /** ポーカー用: Ace = 14 */
    public int evalRank() {
        return rank == 1 ? 14 : rank;
    }

    public String shortName() {
        return rankName() + suit.symbol;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("r", rankName());
        o.addProperty("s", suit.symbol);
        o.addProperty("red", suit.red);
        return o;
    }

    public static JsonObject hiddenJson() {
        JsonObject o = new JsonObject();
        o.addProperty("hidden", true);
        return o;
    }
}
