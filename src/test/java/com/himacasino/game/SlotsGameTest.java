package com.himacasino.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlotsGameTest {

    static class RecordingSign implements SlotsGame.SignView {
        final List<String> status = new ArrayList<>();
        public void setLines(String reel, String st) { status.add(st); }
        public void setReelLine(String reel) {}
    }

    private static void runAll(FakeHost host, int max) {
        for (int i = 0; i < max && !host.later.isEmpty(); i++) host.runLater();
    }

    @Test
    void fullSpinChargesThenSettlesAndResetsSign() {
        FakeHost host = new FakeHost();
        RecordingSign sign = new RecordingSign();
        SlotsGame g = new SlotsGame(host, 50, 6, sign);
        g.open();
        assertEquals(950, host.balance);
        runAll(host, 400);
        assertTrue(g.isFinished());
        assertEquals(950 + 50 * g.lastMultiplier(), host.balance, 0.001);
        assertEquals("§aClick to Start!", sign.status.get(sign.status.size() - 1));
    }

    @Test
    void shutdownMidSpinSettlesOnce() {
        FakeHost host = new FakeHost();
        SlotsGame g = new SlotsGame(host, 50, 6, new RecordingSign());
        g.open();
        host.runLater(); // 1 tick だけ回す
        g.shutdown();
        double after = host.balance;
        assertEquals(950 + 50 * g.lastMultiplier(), after, 0.001);
        for (int r : g.results()) assertTrue(r >= 1 && r <= 7);
        runAll(host, 400); // 残っていた tick が走っても何も起きない
        assertEquals(after, host.balance, 0.001);
    }

    @Test
    void insufficientBalanceEndsWithoutCharge() {
        FakeHost host = new FakeHost();
        host.balance = 10;
        SlotsGame g = new SlotsGame(host, 50, 1, new RecordingSign());
        g.open();
        assertTrue(g.isFinished());
        assertEquals(10, host.balance);
    }
}
