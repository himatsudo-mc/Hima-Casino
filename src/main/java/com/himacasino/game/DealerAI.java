package com.himacasino.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** ファイブカードドローのハウス側 AI。役の強さに基づく簡易ヒューリスティック。 */
final class DealerAI {

    private final Random rng;

    DealerAI(Random rng) {
        this.rng = rng;
    }

    /** 0.0〜1.0 の手の強さ (5 枚) */
    static double strength(List<Card> hand) {
        HandEvaluator.HandRank rank = HandEvaluator.evaluate(hand);
        double base = rank.category().ordinal() / 8.0;
        double kicker = rank.tiebreak().isEmpty() ? 0 : rank.tiebreak().get(0) / 14.0;
        return Math.min(1.0, base + kicker * 0.08);
    }

    /** ストレート以上は交換しない。ペア系は揃っているカードを残し、ノーペアは J 以上だけ残す。 */
    static Set<Integer> discardIndices(List<Card> hand) {
        HandEvaluator.HandRank rank = HandEvaluator.evaluate(hand);
        if (rank.category().ordinal() >= HandEvaluator.Category.STRAIGHT.ordinal()) return Set.of();

        Map<Integer, List<Integer>> byRank = new HashMap<>();
        for (int i = 0; i < hand.size(); i++) {
            byRank.computeIfAbsent(hand.get(i).evalRank(), k -> new ArrayList<>()).add(i);
        }
        Set<Integer> keep = new HashSet<>();
        for (List<Integer> idxs : byRank.values()) {
            if (idxs.size() >= 2) keep.addAll(idxs);
        }
        if (keep.isEmpty()) {
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).evalRank() >= 11) keep.add(i);
            }
        }
        Set<Integer> discard = new HashSet<>();
        for (int i = 0; i < hand.size(); i++) {
            if (!keep.contains(i)) discard.add(i);
        }
        return discard;
    }

    /** プレイヤーの bet に対してコールするか */
    boolean shouldCall(double strength, double betAmount, double potBefore) {
        double potOdds = betAmount / (potBefore + betAmount);
        double bluffCatcher = 0.10 * rng.nextDouble();
        return strength + bluffCatcher >= potOdds * 1.3 + 0.15;
    }

    /** チェックされた場合のディーラーのベット額 (0 ならチェック) */
    double decideBet(double strength, double pot, double maxBet) {
        if (strength >= 0.62 && rng.nextDouble() < 0.8) {
            double fraction = 0.5 + strength * 0.5;
            return clampedBet(pot * fraction, maxBet);
        }
        if (strength < 0.35 && rng.nextDouble() < 0.12) {
            return clampedBet(pot * 0.4, maxBet);
        }
        return 0;
    }

    private static double clampedBet(double raw, double maxBet) {
        double rounded = Math.round(raw / 10.0) * 10.0;
        // プレイヤーが払える上限 (maxBet) を優先し、払えない額のベットで降りるしかなくなるのを防ぐ
        return Math.min(Math.max(10.0, rounded), maxBet);
    }
}
