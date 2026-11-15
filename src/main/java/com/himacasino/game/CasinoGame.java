package com.himacasino.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * サーバー権威のゲーム状態機械。クライアントの Chromium GUI は
 * {@link #state()} の JSON を描画し、{@link #handle(String, JsonObject)} にアクションを送るだけです。
 */
public abstract class CasinoGame {

    public static final double[] CHIPS = {10, 50, 100, 500, 1000};

    protected final GameHost host;
    private boolean finished;

    protected CasinoGame(GameHost host) {
        this.host = host;
    }

    /** "blackjack" など。Web 側の画面名と一致します。 */
    public abstract String id();

    /** 現在の状態 (クライアントへそのまま送る) */
    public abstract JsonObject state();

    /** クライアントからのアクション。不正な入力は無視すること。 */
    public abstract void handle(String action, JsonObject args);

    /** UI を開く (ゲーム開始時) */
    public void open() {
        host.openUi(id(), state());
    }

    /** プレイヤーが GUI を閉じた (Esc など) */
    public void onClientClosed() {
        finish();
    }

    /** サーバー停止・切断などによる強制終了 */
    public void shutdown() {
        finish();
    }

    public final boolean isFinished() {
        return finished;
    }

    /** ゲーム終了 (冪等) */
    protected final void finish() {
        if (finished) return;
        finished = true;
        onFinish();
        host.ended();
    }

    protected void onFinish() {}

    protected final void refresh() {
        if (!finished) host.sendState(id(), state());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    protected JsonObject base(String phase) {
        JsonObject o = new JsonObject();
        o.addProperty("game", id());
        o.addProperty("phase", phase);
        o.addProperty("symbol", host.config().currencySymbol);
        o.addProperty("economy", host.economyEnabled());
        o.addProperty("balance", host.economyEnabled() ? host.balance() : -1);
        JsonArray chips = new JsonArray();
        for (double c : CHIPS) chips.add(c);
        o.add("chips", chips);
        return o;
    }

    protected final String sym() {
        return host.config().currencySymbol;
    }

    protected static String fmt(double v) {
        return String.format("%.0f", v);
    }

    /** 残高が足りるか (無料モードは常に true) */
    protected final boolean canAfford(double amount) {
        return !host.economyEnabled() || host.balance() >= amount;
    }

    /** 賭け金の引き落とし。失敗時はメッセージを出して false */
    protected final boolean charge(double amount) {
        if (!host.economyEnabled()) return true;
        if (host.balance() < amount || !host.withdraw(amount)) {
            host.message("§c残高が不足しています。");
            return false;
        }
        return true;
    }

    protected final void payout(double betAmount, double multiplier) {
        double winnings = betAmount * multiplier;
        if (host.economyEnabled()) host.deposit(winnings);
        host.message(String.format("§a§l+%.0f %s 獲得！ (x%.1f)", winnings, sym(), multiplier));
    }

    protected static double num(JsonObject args, String key, double def) {
        return args != null && args.has(key) && args.get(key).isJsonPrimitive() ? args.get(key).getAsDouble() : def;
    }

    protected static int intArg(JsonObject args, String key, int def) {
        return args != null && args.has(key) && args.get(key).isJsonPrimitive() ? args.get(key).getAsInt() : def;
    }

    protected static String str(JsonObject args, String key, String def) {
        return args != null && args.has(key) && args.get(key).isJsonPrimitive() ? args.get(key).getAsString() : def;
    }

    /** 許可されたチップ額か (改ざん防止) */
    protected static boolean isChip(double v) {
        for (double c : CHIPS) if (c == v) return true;
        return false;
    }
}
