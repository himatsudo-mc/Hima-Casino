package com.himacasino.games.poker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Simple dealer opponent logic for Five Card Draw. Not a full equity solver — just a hand
 * strength heuristic ({@link HandEvaluator} category) driving draw/call/fold/bet decisions,
 * good enough for a casino minigame opponent.
 */
final class DealerAI {

    private DealerAI() {}

    private static final Random RNG = new Random();

    /** Normalized hand strength in [0.0, 1.0], higher = stronger. {@code hand} must be exactly 5 cards. */
    static double strength(List<Card> hand) {
        HandEvaluator.HandRank rank = HandEvaluator.evaluate(hand);
        double base = rank.category().ordinal() / 8.0;
        double kicker = rank.tiebreak().isEmpty() ? 0 : rank.tiebreak().get(0) / 14.0;
        return Math.min(1.0, base + kicker * 0.08);
    }

    /**
     * Which of the dealer's 5 cards to discard and redraw. A made straight or better always
     * stands pat; otherwise any card belonging to a pair/trips/quads is kept and the rest
     * discarded, and a hand with no pair at all keeps only its Jack-or-better cards.
     */
    static Set<Integer> discardIndices(List<Card> hand) {
        HandEvaluator.HandRank rank = HandEvaluator.evaluate(hand);
        if (rank.category().ordinal() >= HandEvaluator.Category.STRAIGHT.ordinal()) {
            return Set.of();
        }

        Map<Integer, List<Integer>> byRank = new HashMap<>();
        for (int i = 0; i < hand.size(); i++) {
            byRank.computeIfAbsent(hand.get(i).getEvalRank(), k -> new ArrayList<>()).add(i);
        }
        Set<Integer> keep = new HashSet<>();
        for (List<Integer> idxs : byRank.values()) {
            if (idxs.size() >= 2) keep.addAll(idxs);
        }
        if (keep.isEmpty()) {
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).getEvalRank() >= 11) keep.add(i); // Jack or better
            }
        }

        Set<Integer> discard = new HashSet<>();
        for (int i = 0; i < hand.size(); i++) {
            if (!keep.contains(i)) discard.add(i);
        }
        return discard;
    }

    /** Facing a player bet of {@code betAmount} into a pot of {@code potBefore} — call or fold? */
    static boolean shouldCall(double strength, double betAmount, double potBefore) {
        double potOdds = betAmount / (potBefore + betAmount);
        double bluffCatcher = 0.10 * RNG.nextDouble();
        return strength + bluffCatcher >= potOdds * 1.3 + 0.15;
    }

    /** Checked to — returns a bet amount (rounded to 10, capped at maxBet), or 0 to check. */
    static double decideBet(double strength, double pot, double maxBet) {
        if (strength >= 0.62 && RNG.nextDouble() < 0.8) {
            double fraction = 0.5 + strength * 0.5; // half-pot .. pot-sized value bet
            return clampedBet(pot * fraction, maxBet);
        }
        if (strength < 0.35 && RNG.nextDouble() < 0.12) { // occasional small bluff
            return clampedBet(pot * 0.4, maxBet);
        }
        return 0;
    }

    private static double clampedBet(double raw, double maxBet) {
        double rounded = Math.round(raw / 10.0) * 10.0;
        // maxBet caps what the player can still call, so it has to win over the 10-coin floor —
        // otherwise a low-balance player gets bet at an amount they cannot match, and can only fold.
        return Math.min(Math.max(10.0, rounded), maxBet);
    }
}
