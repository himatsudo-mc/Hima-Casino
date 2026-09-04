package com.himacasino.games.poker;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simple heads-up dealer opponent logic. Not a full equity/Monte-Carlo solver — just a hand
 * strength heuristic (preflop hole-card score, postflop {@link HandEvaluator} category) driving
 * call/fold/bet decisions, good enough for a casino minigame opponent.
 */
final class DealerAI {

    private DealerAI() {}

    private static final Random RNG = new Random();

    /** Normalized hand strength in [0.0, 1.0], higher = stronger. */
    static double strength(List<Card> hole, List<Card> board) {
        if (board.isEmpty()) return preflopStrength(hole);
        List<Card> all = new ArrayList<>(hole);
        all.addAll(board);
        HandEvaluator.HandRank rank = HandEvaluator.evaluate(all);
        double base = rank.category().ordinal() / 8.0;
        double kicker = rank.tiebreak().isEmpty() ? 0 : rank.tiebreak().get(0) / 14.0;
        return Math.min(1.0, base + kicker * 0.08);
    }

    private static double preflopStrength(List<Card> hole) {
        int r1 = hole.get(0).getEvalRank();
        int r2 = hole.get(1).getEvalRank();
        boolean pair = r1 == r2;
        boolean suited = hole.get(0).getSuit() == hole.get(1).getSuit();
        int hi = Math.max(r1, r2);
        int lo = Math.min(r1, r2);
        int gap = hi - lo;

        double score = (hi + lo) / 28.0;
        if (pair) score += 0.30 + hi / 100.0;
        if (suited) score += 0.08;
        if (gap == 1) score += 0.05;
        else if (gap == 2) score += 0.02;
        return Math.min(1.0, score);
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
        return Math.max(10.0, Math.min(rounded, maxBet));
    }
}
