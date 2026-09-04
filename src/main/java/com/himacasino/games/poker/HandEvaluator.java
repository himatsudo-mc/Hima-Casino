package com.himacasino.games.poker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Evaluates the best 5-card poker hand out of 5-7 cards (2 hole cards + up to 5 community cards). */
public final class HandEvaluator {

    public enum Category {
        HIGH_CARD, PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT,
        FLUSH, FULL_HOUSE, FOUR_OF_A_KIND, STRAIGHT_FLUSH
    }

    /** {@code tiebreak} lists ranks (Ace-high = 14) in descending priority order for comparing equal-category hands. */
    public record HandRank(Category category, List<Integer> tiebreak) implements Comparable<HandRank> {
        @Override
        public int compareTo(HandRank other) {
            int c = category.compareTo(other.category);
            if (c != 0) return c;
            int n = Math.min(tiebreak.size(), other.tiebreak.size());
            for (int i = 0; i < n; i++) {
                int cmp = Integer.compare(tiebreak.get(i), other.tiebreak.get(i));
                if (cmp != 0) return cmp;
            }
            return 0;
        }

        public String displayName() {
            return switch (category) {
                case HIGH_CARD -> "ハイカード";
                case PAIR -> "ワンペア";
                case TWO_PAIR -> "ツーペア";
                case THREE_OF_A_KIND -> "スリーカード";
                case STRAIGHT -> "ストレート";
                case FLUSH -> "フラッシュ";
                case FULL_HOUSE -> "フルハウス";
                case FOUR_OF_A_KIND -> "フォーカード";
                case STRAIGHT_FLUSH -> "ストレートフラッシュ";
            };
        }
    }

    private HandEvaluator() {}

    /** Best 5-card {@link HandRank} achievable from {@code cards} (must contain at least 5). */
    public static HandRank evaluate(List<Card> cards) {
        if (cards.size() < 5) throw new IllegalArgumentException("Need at least 5 cards, got " + cards.size());

        HandRank best = null;
        for (int[] combo : combinations(cards.size(), 5)) {
            List<Card> five = new ArrayList<>(5);
            for (int i : combo) five.add(cards.get(i));
            HandRank rank = evaluateFive(five);
            if (best == null || rank.compareTo(best) > 0) best = rank;
        }
        return best;
    }

    private static List<int[]> combinations(int n, int k) {
        List<int[]> result = new ArrayList<>();
        combine(result, new int[k], 0, n, k, 0);
        return result;
    }

    private static void combine(List<int[]> result, int[] combo, int start, int n, int k, int depth) {
        if (depth == k) {
            result.add(combo.clone());
            return;
        }
        for (int i = start; i < n; i++) {
            combo[depth] = i;
            combine(result, combo, i + 1, n, k, depth + 1);
        }
    }

    private static HandRank evaluateFive(List<Card> five) {
        List<Integer> ranksDesc = new ArrayList<>();
        for (Card c : five) ranksDesc.add(c.getEvalRank());
        ranksDesc.sort(Collections.reverseOrder());

        boolean flush = five.stream().map(Card::getSuit).distinct().count() == 1;

        List<Integer> distinctDesc = new ArrayList<>(new TreeSet<>(ranksDesc));
        Collections.reverse(distinctDesc);
        int straightHigh = -1;
        if (distinctDesc.size() == 5) {
            if (distinctDesc.get(0) - distinctDesc.get(4) == 4) {
                straightHigh = distinctDesc.get(0);
            } else if (distinctDesc.equals(List.of(14, 5, 4, 3, 2))) {
                straightHigh = 5; // wheel: A-2-3-4-5, Ace plays low
            }
        }
        boolean straight = straightHigh > 0;

        Map<Integer, Integer> freq = new HashMap<>();
        for (int r : ranksDesc) freq.merge(r, 1, Integer::sum);
        List<Map.Entry<Integer, Integer>> byCount = new ArrayList<>(freq.entrySet());
        byCount.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : Integer.compare(b.getKey(), a.getKey());
        });
        List<Integer> counts = byCount.stream().map(Map.Entry::getValue).toList();
        List<Integer> byCountRanks = byCount.stream().map(Map.Entry::getKey).toList();

        if (straight && flush) return new HandRank(Category.STRAIGHT_FLUSH, List.of(straightHigh));
        if (counts.get(0) == 4) return new HandRank(Category.FOUR_OF_A_KIND, byCountRanks);
        if (counts.get(0) == 3 && counts.size() > 1 && counts.get(1) == 2) {
            return new HandRank(Category.FULL_HOUSE, byCountRanks);
        }
        if (flush) return new HandRank(Category.FLUSH, ranksDesc);
        if (straight) return new HandRank(Category.STRAIGHT, List.of(straightHigh));
        if (counts.get(0) == 3) return new HandRank(Category.THREE_OF_A_KIND, byCountRanks);
        if (counts.get(0) == 2 && counts.size() > 1 && counts.get(1) == 2) {
            return new HandRank(Category.TWO_PAIR, byCountRanks);
        }
        if (counts.get(0) == 2) return new HandRank(Category.PAIR, byCountRanks);
        return new HandRank(Category.HIGH_CARD, ranksDesc);
    }
}
