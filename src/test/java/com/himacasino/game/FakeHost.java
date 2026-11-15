package com.himacasino.game;

import com.google.gson.JsonObject;
import com.himacasino.config.CasinoConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** テスト用ホスト: 残高・メッセージ・遅延タスクを手元で観察できる。 */
class FakeHost implements GameHost {
    final CasinoConfig cfg = new CasinoConfig();
    final Random rng = new Random(42);
    boolean economy = true;
    double balance = 1000;
    final List<String> messages = new ArrayList<>();
    final List<Runnable> later = new ArrayList<>();
    JsonObject lastState;
    String openedGame;
    int closeCalls;
    boolean ended;

    @Override public CasinoConfig config() { return cfg; }
    @Override public Random random() { return rng; }
    @Override public boolean economyEnabled() { return economy; }
    @Override public double balance() { return balance; }
    @Override public boolean withdraw(double a) { if (balance < a) return false; balance -= a; return true; }
    @Override public void deposit(double a) { balance += a; }
    @Override public void message(String t) { messages.add(t); }
    @Override public void sound(String id, float v, float p) {}
    @Override public void effect(String n) {}
    @Override public void later(int t, Runnable r) { later.add(r); }
    @Override public void openUi(String g, JsonObject s) { openedGame = g; lastState = s; }
    @Override public void sendState(String g, JsonObject s) { lastState = s; }
    @Override public void closeUi() { closeCalls++; }
    @Override public void ended() { ended = true; }

    void runLater() {
        List<Runnable> copy = new ArrayList<>(later);
        later.clear();
        copy.forEach(Runnable::run);
    }

    static JsonObject args(String k, double v) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        return o;
    }
}
