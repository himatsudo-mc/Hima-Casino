package com.himacasino.games.blackjack;

/**
 * A single playing card. CustomModelData assignment follows the SOW spec:
 * <pre>
 *   1-13   Spades  (A-K)
 *   14-26  Hearts  (A-K)
 *   27-39  Diamonds(A-K)
 *   40-52  Clubs   (A-K)
 *   53     Card back (face-down)
 * </pre>
 */
public record Card(Suit suit, Rank rank) {

    public static final int BACK_CUSTOM_MODEL_DATA = 53;

    public enum Suit {
        SPADES(1, "♠", "Spades"),
        HEARTS(14, "♥", "Hearts"),
        DIAMONDS(27, "♦", "Diamonds"),
        CLUBS(40, "♣", "Clubs");

        private final int baseCustomModelData;
        private final String symbol;
        private final String displayName;

        Suit(int baseCustomModelData, String symbol, String displayName) {
            this.baseCustomModelData = baseCustomModelData;
            this.symbol = symbol;
            this.displayName = displayName;
        }

        public String symbol() { return symbol; }
        public String displayName() { return displayName; }
    }

    public enum Rank {
        ACE("A"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"),
        EIGHT("8"), NINE("9"), TEN("10"), JACK("J"), QUEEN("Q"), KING("K");

        private final String label;

        Rank(String label) { this.label = label; }

        public String label() { return label; }

        /** Base blackjack value (Ace counts as 11 here; hand-level logic reduces to 1 as needed). */
        public int baseValue() {
            return switch (this) {
                case ACE -> 11;
                case JACK, QUEEN, KING -> 10;
                default -> ordinal() + 1; // TWO=1+1=2 ... TEN=9+1=10
            };
        }
    }

    /** CustomModelData for the face-up texture of this card (see class-level table). */
    public int customModelData() {
        return suit.baseCustomModelData + rank.ordinal();
    }

    public String shortName() {
        return rank.label() + suit.symbol();
    }

    @Override
    public String toString() {
        return shortName();
    }
}
