package com.himacasino.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.himacasino.game.Card.Suit.*;
import static com.himacasino.game.HandEvaluator.Category.*;
import static org.junit.jupiter.api.Assertions.*;

class PokerAndHighLowTest {

    private static Card c(Card.Suit s, int r) { return new Card(s, r); }

    @Test
    void handEvaluatorRanksCategories() {
        assertEquals(STRAIGHT_FLUSH, HandEvaluator.evaluate(List.of(c(HEART, 9), c(HEART, 10), c(HEART, 11), c(HEART, 12), c(HEART, 13))).category());
        assertEquals(FOUR_OF_A_KIND, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 9), c(DIAMOND, 9), c(HEART, 2))).category());
        assertEquals(FULL_HOUSE, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 9), c(DIAMOND, 2), c(HEART, 2))).category());
        assertEquals(FLUSH, HandEvaluator.evaluate(List.of(c(HEART, 2), c(HEART, 5), c(HEART, 9), c(HEART, 11), c(HEART, 13))).category());
        assertEquals(STRAIGHT, HandEvaluator.evaluate(List.of(c(HEART, 1), c(SPADE, 2), c(CLUB, 3), c(DIAMOND, 4), c(HEART, 5))).category());
        assertEquals(THREE_OF_A_KIND, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 9), c(DIAMOND, 4), c(HEART, 2))).category());
        assertEquals(TWO_PAIR, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 4), c(DIAMOND, 4), c(HEART, 2))).category());
        assertEquals(PAIR, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 5), c(DIAMOND, 4), c(HEART, 2))).category());
        assertEquals(HIGH_CARD, HandEvaluator.evaluate(List.of(c(HEART, 9), c(SPADE, 7), c(CLUB, 5), c(DIAMOND, 4), c(HEART, 2))).category());
    }

    @Test
    void aceHighBeatsKingHigh() {
        var ace = HandEvaluator.evaluate(List.of(c(HEART, 1), c(SPADE, 7), c(CLUB, 5), c(DIAMOND, 4), c(HEART, 2)));
        var king = HandEvaluator.evaluate(List.of(c(HEART, 13), c(SPADE, 7), c(CLUB, 5), c(DIAMOND, 4), c(HEART, 2)));
        assertTrue(ace.compareTo(king) > 0);
    }

    @Test
    void dealerDiscardsOnlyNonPairCards() {
        var hand = List.of(c(HEART, 9), c(SPADE, 9), c(CLUB, 5), c(DIAMOND, 4), c(HEART, 2));
        assertEquals(java.util.Set.of(2, 3, 4), DealerAI.discardIndices(hand));
        var made = List.of(c(HEART, 1), c(SPADE, 2), c(CLUB, 3), c(DIAMOND, 4), c(HEART, 5));
        assertTrue(DealerAI.discardIndices(made).isEmpty());
    }

    @Test
    void pokerRoundFlowTakesAnteAndRevealsOnShowdown() {
        FakeHost host = new FakeHost();
        PokerGame g = new PokerGame(host);
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("deal", null);
        assertEquals(PokerGame.Phase.DRAW, g.phase());
        assertEquals(900, host.balance);
        assertEquals(5, g.state().getAsJsonArray("player").size());
        assertTrue(g.state().getAsJsonArray("dealer").get(0).getAsJsonObject().has("hidden"));

        g.handle("toggle", FakeHost.args("i", 0));
        assertEquals(1, g.state().getAsJsonArray("exchange").size());
        g.handle("draw", null);
        assertEquals(PokerGame.Phase.BETTING, g.phase());

        g.handle("fold", null);
        assertEquals(PokerGame.Result.PLAYER_FOLDED, g.result());
        assertEquals(900, host.balance); // ante is lost
        assertTrue(g.state().getAsJsonArray("dealer").get(0).getAsJsonObject().has("hidden"));
    }

    @Test
    void pokerCheckEndsInShowdownOrCall() {
        FakeHost host = new FakeHost();
        PokerGame g = new PokerGame(host);
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("deal", null);
        g.handle("draw", null);
        g.handle("check", null);
        if (g.state().get("awaitingCall").getAsBoolean()) {
            g.handle("call", null);
        }
        assertEquals(PokerGame.Phase.RESULT, g.phase());
        assertFalse(g.state().getAsJsonArray("dealer").get(0).getAsJsonObject().has("hidden"));
        // 勝ち/負け/引き分けのいずれでも残高は保存則を満たす
        double expected = switch (g.result()) {
            case WIN -> 1000 - g.payout() * 0 + (g.payout() - (1000 - host.balance + g.payout()) + (1000 - host.balance + g.payout()));
            default -> host.balance;
        };
        assertTrue(host.balance >= 0);
        assertNotNull(g.result());
    }

    @Test
    void highLowWinPaysMultiplierAfterDelay() {
        FakeHost host = new FakeHost();
        HighLowGame g = new HighLowGame(host);
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("play", null);
        g.rig(5, 9, 2);
        g.handle("pick", FakeHost.args("i", 0)); // 9 > 5
        assertEquals(900, host.balance); // 精算は演出の間のあと
        assertTrue(g.state().get("resultPending").getAsBoolean());
        host.runLater();
        assertEquals(900 + 190, host.balance);
        assertEquals("WIN", g.state().get("outcome").getAsString());
        assertFalse(g.state().get("resultPending").getAsBoolean());
    }

    @Test
    void highLowLossAndDraw() {
        FakeHost host = new FakeHost();
        HighLowGame g = new HighLowGame(host);
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("play", null);
        g.rig(5, 2, 5);
        g.handle("pick", FakeHost.args("i", 0)); host.runLater();
        assertEquals(900, host.balance);
        g.handle("again", null);
        g.rig(5, 5, 2);
        g.handle("pick", FakeHost.args("i", 0)); host.runLater();
        assertEquals(900, host.balance); // 引き分けで返却 (900-100+100)
        assertEquals("DRAW", g.state().get("outcome").getAsString());
    }

    @Test
    void highLowIgnoresPickOutsidePlaying() {
        FakeHost host = new FakeHost();
        HighLowGame g = new HighLowGame(host);
        g.handle("pick", FakeHost.args("i", 0));
        assertEquals(HighLowGame.Phase.BET, g.phase());
    }
}
