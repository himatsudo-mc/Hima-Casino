package com.himacasino.games.poker;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

/**
 * A single playing card for the Poker (Sow) game.
 *
 * <p>CustomModelData follows the design spec's {@code Suit.offset + rank} formula, but the
 * offsets are shifted from the spec's 100/200/300/400 to 300/400/500/600 to avoid colliding
 * with CustomModelData already used elsewhere in the shared resource pack — Blackjack's action
 * buttons sit at 100-102 and the slot-machine reels reserve 201-213 (see
 * {@code resource-pack/assets/himacasino/models/item/paper.json}). Poker's card textures are
 * not new assets: the overrides simply point at the existing
 * {@code himacasino:item/cards/card_<suit>_<rank>} models already shipped for Blackjack, so both
 * games render identical card art from separate CustomModelData ranges.
 */
public class Card {

    public enum Suit {
        SPADE(300, "♠"),
        HEART(400, "♥"),
        DIAMOND(500, "♦"),
        CLUB(600, "♣");

        private final int offset;
        private final String symbol;

        Suit(int offset, String symbol) {
            this.offset = offset;
            this.symbol = symbol;
        }

        public int getOffset() { return offset; }
        public String getSymbol() { return symbol; }
    }

    /** CustomModelData for the face-down card back (reuses Blackjack's card_back texture). */
    public static final int BACK_CUSTOM_MODEL_DATA = 699;

    private final Suit suit;
    private final int rank; // 1 (Ace) .. 13 (King)

    public Card(Suit suit, int rank) {
        if (rank < 1 || rank > 13) throw new IllegalArgumentException("rank must be 1-13: " + rank);
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public int getRank() { return rank; }

    /** Ace-high rank for hand evaluation / preflop-strength comparisons (Ace = 14). */
    public int getEvalRank() { return rank == 1 ? 14 : rank; }

    public String getRankName() {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(rank);
        };
    }

    public String getSuitSymbol() { return suit.getSymbol(); }

    /** CustomModelData for this card's face-up texture (see class javadoc). */
    public int customModelData() { return suit.getOffset() + rank; }

    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f§l" + getRankName() + getSuitSymbol());
        meta.setCustomModelData(customModelData());
        item.setItemMeta(meta);
        return item;
    }

    public String shortName() { return getRankName() + getSuitSymbol(); }

    @Override
    public String toString() { return shortName(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card other)) return false;
        return suit == other.suit && rank == other.rank;
    }

    @Override
    public int hashCode() { return Objects.hash(suit, rank); }
}
