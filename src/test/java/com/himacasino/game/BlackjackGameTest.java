package com.himacasino.game;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.himacasino.game.Card.Suit.*;
import static org.junit.jupiter.api.Assertions.*;

class BlackjackGameTest {

    private final FakeHost host = new FakeHost();
    private final BlackjackGame game = new BlackjackGame(host);

    private void bet(double amount) {
        for (double chip : new double[]{1000, 500, 100, 50, 10}) {
            while (amount >= chip) { game.handle("chip", FakeHost.args("v", chip)); amount -= chip; }
        }
    }

    // 配り順: player, dealer(伏せ), player, dealer(表)
    private void stack(Card... cards) { game.deck().stack(List.of(cards)); }

    @Test
    void naturalPays3to2() {
        stack(new Card(SPADE, 1), new Card(HEART, 5), new Card(SPADE, 13), new Card(HEART, 6));
        bet(100);
        game.handle("deal", null);
        assertEquals(BlackjackGame.Result.BLACKJACK, game.result());
        assertEquals(1000 - 100 + 250, host.balance);
        assertEquals("RESULT", game.state().get("phase").getAsString());
    }

    @Test
    void standBeatsDealerAndPaysDouble() {
        stack(new Card(SPADE, 10), new Card(HEART, 10), new Card(SPADE, 9), new Card(HEART, 7));
        bet(100);
        game.handle("deal", null);
        assertEquals("PLAYING", game.state().get("phase").getAsString());
        game.handle("stand", null); // dealer 17 stands, player 19 wins
        assertEquals(BlackjackGame.Result.WIN, game.result());
        assertEquals(1100, host.balance);
    }

    @Test
    void bustLosesBet() {
        stack(new Card(SPADE, 10), new Card(HEART, 10), new Card(SPADE, 6), new Card(HEART, 7),
              new Card(CLUB, 10));
        bet(100);
        game.handle("deal", null);
        game.handle("hit", null);
        assertEquals(BlackjackGame.Result.LOSE, game.result());
        assertEquals(900, host.balance);
    }

    @Test
    void pushReturnsBet() {
        stack(new Card(SPADE, 10), new Card(HEART, 10), new Card(SPADE, 8), new Card(HEART, 8));
        bet(100);
        game.handle("deal", null);
        game.handle("stand", null); // 18 vs 18
        assertEquals(BlackjackGame.Result.PUSH, game.result());
        assertEquals(1000, host.balance);
    }

    @Test
    void doubleDownDoublesStake() {
        stack(new Card(SPADE, 5), new Card(HEART, 10), new Card(SPADE, 6), new Card(HEART, 7),
              new Card(CLUB, 10)); // player 11 + 10 = 21, dealer 17
        bet(100);
        game.handle("deal", null);
        game.handle("double", null);
        assertEquals(BlackjackGame.Result.WIN, game.result());
        assertEquals(1000 - 200 + 400, host.balance);
    }

    @Test
    void dealerHoleCardIsHiddenInStateWhilePlaying() {
        stack(new Card(SPADE, 10), new Card(HEART, 10), new Card(SPADE, 9), new Card(HEART, 7));
        bet(100);
        game.handle("deal", null);
        JsonObject s = game.state();
        assertTrue(s.getAsJsonArray("dealer").get(0).getAsJsonObject().has("hidden"));
        assertTrue(s.get("dealerValue").isJsonNull());
        assertEquals(1, s.getAsJsonArray("dealer").get(1).getAsJsonObject().size() > 0 ? 1 : 0);
    }

    @Test
    void cannotDealWithoutMinimumBetOrBalance() {
        game.handle("deal", null);
        assertEquals(BlackjackGame.Phase.BET, game.phase());
        host.balance = 50;
        bet(100);
        game.handle("deal", null);
        assertEquals(BlackjackGame.Phase.BET, game.phase());
        assertEquals(50, host.balance);
    }

    @Test
    void invalidChipValuesAreIgnored() {
        game.handle("chip", FakeHost.args("v", 12345));
        assertEquals(0, game.state().get("bet").getAsDouble());
    }

    @Test
    void freeModeNeverTouchesBalance() {
        host.economy = false;
        stack(new Card(SPADE, 1), new Card(HEART, 5), new Card(SPADE, 13), new Card(HEART, 6));
        bet(100);
        game.handle("deal", null);
        assertEquals(1000, host.balance);
        assertEquals(BlackjackGame.Result.BLACKJACK, game.result());
    }

    @Test
    void exitEndsGame() {
        game.handle("exit", null);
        assertTrue(game.isFinished());
        assertTrue(host.ended);
    }
}
