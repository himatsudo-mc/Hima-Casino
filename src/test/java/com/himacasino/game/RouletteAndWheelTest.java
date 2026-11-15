package com.himacasino.game;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouletteAndWheelTest {

    /** 指定の番号で必ず止まる卓 */
    static class StubTable implements RouletteGame.TableView {
        Runnable landed;
        int started = -1;
        boolean active;
        public void setNumberCoin(int n, double t) {}
        public void setColorCoin(String c, double t) {}
        public void clearCoins() {}
        public void setActive(boolean a) { active = a; }
        public void startSpin(int n, int ticks, Runnable l) { started = n; landed = l; }
        public void showResult(int n) {}
        public void reset() {}
    }

    private static JsonObject kind(String k, int n) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", k);
        o.addProperty("n", n);
        return o;
    }

    @Test
    void closingBeforeSpinRefundsBets() {
        FakeHost host = new FakeHost();
        RouletteGame g = new RouletteGame(host, new StubTable());
        g.open();
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("bet", kind("number", 17));
        assertEquals(900, host.balance);
        g.onClientClosed();
        assertEquals(1000, host.balance);
        assertTrue(g.isFinished());
    }

    @Test
    void spinWithoutBetIsRejected() {
        FakeHost host = new FakeHost();
        RouletteGame g = new RouletteGame(host, new StubTable());
        g.handle("spin", null);
        assertEquals(RouletteGame.Phase.BET, g.phase());
    }

    @Test
    void straightWinPays36x() {
        FakeHost host = new FakeHost();
        StubTable t = new StubTable();
        RouletteGame g = new RouletteGame(host, t);
        g.open();
        g.handle("chip", FakeHost.args("v", 10));
        // winner is chosen by RNG; bet every number once so exactly one wins
        for (int n = 0; n <= 36; n++) g.handle("bet", kind("number", n));
        assertEquals(1000 - 370, host.balance);
        g.handle("spin", null);
        assertEquals(1, host.closeCalls);
        t.landed.run();
        assertEquals(1000 - 370 + 360, host.balance);
        assertEquals(360, g.winnings());
    }

    @Test
    void colorBetWinsOnMatchingColorOnly() {
        FakeHost host = new FakeHost();
        StubTable t = new StubTable();
        RouletteGame g = new RouletteGame(host, t);
        g.open();
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("bet", kind("red", 0));
        g.handle("spin", null);
        int n = t.started;
        t.landed.run();
        boolean win = n != 0 && RouletteGame.isRed(n);
        assertEquals(win ? 1100 : 900, host.balance);
    }

    @Test
    void spreadBetsSplitStakeAcrossNumbers() {
        FakeHost host = new FakeHost();
        RouletteGame g = new RouletteGame(host, new StubTable());
        g.open();
        g.handle("chip", FakeHost.args("v", 500));
        g.handle("bet", kind("even", 0));
        assertEquals(500, g.totalBet());
        assertEquals(18, g.state().getAsJsonArray("covered").size());
        assertEquals(0, g.state().getAsJsonObject("numbers").size()); // 分配ベットは単一数字のコインに出さない
        assertEquals(500, g.state().getAsJsonObject("outside").get("even").getAsDouble());
    }

    @Test
    void betsAreIgnoredWhileSpinning() {
        FakeHost host = new FakeHost();
        StubTable t = new StubTable();
        RouletteGame g = new RouletteGame(host, t);
        g.open();
        g.handle("chip", FakeHost.args("v", 100));
        g.handle("bet", kind("red", 0));
        g.handle("spin", null);
        g.handle("bet", kind("black", 0));
        assertEquals(100, g.totalBet());
    }

    @Test
    void wheelHasConsistentLayout() {
        assertEquals(37, RouletteGame.WHEEL_ORDER.length);
        assertEquals(37, java.util.Arrays.stream(RouletteGame.WHEEL_ORDER).distinct().count());
        assertEquals(24, HorseWheelGame.WHEEL_ORDER.length);
    }

    @Test
    void horseWheelPaysMultiplierOnWinningHorse() {
        FakeHost host = new FakeHost();
        final Runnable[] done = new Runnable[1];
        final int[] seg = new int[1];
        HorseWheelGame g = new HorseWheelGame(host, (s, r) -> { seg[0] = s; done[0] = r; });
        g.open();
        for (int h = 0; h < 6; h++) {
            JsonObject sel = new JsonObject(); sel.addProperty("i", h);
            g.handle("select", sel);
            g.handle("chip", FakeHost.args("v", 10));
        }
        g.handle("spin", null);
        assertEquals(940, host.balance);
        done[0].run();
        int horse = HorseWheelGame.WHEEL_ORDER[seg[0]];
        assertEquals(940 + 10 * HorseWheelGame.MULTIPLIERS[horse], host.balance, 0.001);
        host.runLater();
        assertEquals(HorseWheelGame.Phase.BET, g.phase()); // ベット画面に戻る
        assertEquals(0, g.state().get("total").getAsDouble());
    }

    @Test
    void horseWheelRejectsSpinWithoutFunds() {
        FakeHost host = new FakeHost();
        host.balance = 5;
        HorseWheelGame g = new HorseWheelGame(host, (s, r) -> fail("must not spin"));
        g.handle("chip", FakeHost.args("v", 10));
        g.handle("spin", null);
        assertEquals(HorseWheelGame.Phase.BET, g.phase());
    }
}
