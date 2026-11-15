package com.himacasino.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * ヨーロピアンルーレット (0〜36)。ベット画面は Chromium GUI、回転演出は 3D 卓 ({@link TableView}) が担当。
 * Phase: BET → SPINNING → DONE
 */
public class RouletteGame extends CasinoGame {

    public static final int[] WHEEL_ORDER = {
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
        24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    private static final int[] RED_NUMBERS = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};

    /** 3D 卓の見た目 (テストでは何もしない実装を渡す) */
    public interface TableView {
        void setNumberCoin(int number, double total);
        void setColorCoin(String color, double total);
        void clearCoins();
        void setActive(boolean active);
        /** 回転を開始し、ボールが止まったら onLanded を呼ぶ */
        void startSpin(int winningNumber, int spinTicks, Runnable onLanded);
        void showResult(int winningNumber);
        void reset();
    }

    public enum Phase { BET, SPINNING, DONE }

    private final TableView table;
    private Phase phase = Phase.BET;
    private double chip = 10;
    private final Map<Integer, Double> numberBets = new HashMap<>();
    private final Map<String, Double> colorBets = new HashMap<>();
    /** 画面表示用: 単一数字へのベットだけ / 外側ベット (EVEN・ダース等) の累計 */
    private final Map<Integer, Double> straightBets = new HashMap<>();
    private final Map<String, Double> outsideBets = new HashMap<>();
    private double totalBet = 0;
    private int winningNumber = -1;
    private double winnings = 0;

    public RouletteGame(GameHost host, TableView table) {
        super(host);
        this.table = table;
    }

    @Override public String id() { return "roulette"; }

    public Phase phase() { return phase; }
    public double totalBet() { return totalBet; }
    public double winnings() { return winnings; }

    public static boolean isRed(int n) {
        for (int r : RED_NUMBERS) if (r == n) return true;
        return false;
    }

    // ── actions ───────────────────────────────────────────────────────────

    @Override
    public void open() {
        table.setActive(true);
        super.open();
    }

    @Override
    public void handle(String action, JsonObject args) {
        if (isFinished() || phase != Phase.BET) return;
        switch (action) {
            case "chip" -> {
                double v = num(args, "v", 0);
                if (!isChip(v)) return;
                chip = v;
                refresh();
            }
            case "bet" -> placeBet(str(args, "kind", ""), intArg(args, "n", 0));
            case "clear" -> {
                refund();
                refresh();
            }
            case "spin" -> spin();
            case "exit" -> finish();
            default -> {}
        }
    }

    private void placeBet(String kind, int n) {
        double amount = chip;
        if (amount > host.config().roulette.maxBet) return;
        switch (kind) {
            case "number" -> {
                if (n < 0 || n > 36) return;
                if (!charge(amount)) return;
                numberBets.merge(n, amount, Double::sum);
                straightBets.merge(n, amount, Double::sum);
                totalBet += amount;
                table.setNumberCoin(n, numberBets.get(n));
                host.message(String.format("§7ベット §e%d §7→ §6%.0f %s", n, amount, sym()));
            }
            case "red", "black" -> {
                if (!charge(amount)) return;
                colorBets.merge(kind, amount, Double::sum);
                totalBet += amount;
                table.setColorCoin(kind, colorBets.get(kind));
                host.message(String.format("§7ベット %s §7→ §6%.0f %s", kind.equals("red") ? "§cRED" : "§8BLACK", amount, sym()));
            }
            case "even", "odd" -> spread(kind, amount, 18, kind.equals("even") ? "§bEVEN" : "§eODD",
                    x -> x >= 1 && (x % 2 == 0) == kind.equals("even"));
            case "low" -> spread(kind, amount, 18, "§aLOW", x -> x >= 1 && x <= 18);
            case "high" -> spread(kind, amount, 18, "§6HIGH", x -> x >= 19 && x <= 36);
            case "dozen" -> {
                if (n < 1 || n > 3) return;
                int start = (n - 1) * 12 + 1;
                spread("dozen" + n, amount, 12, "§d" + n + "st/nd/rd 12", x -> x >= start && x < start + 12);
            }
            default -> { return; }
        }
        refresh();
    }

    private void spread(String key, double amount, int count, String label, java.util.function.IntPredicate pick) {
        if (!charge(amount)) return;
        outsideBets.merge(key, amount, Double::sum);
        double each = amount / count;
        for (int n = 1; n <= 36; n++) if (pick.test(n)) numberBets.merge(n, each, Double::sum);
        totalBet += amount; // 浮動小数の誤差を避けるため、合計は賭け額そのものを加算する
        host.message(String.format("§7%s に §6%.0f %s §7をベット", label, amount, sym()));
    }

    private void refund() {
        if (totalBet > 0 && host.economyEnabled()) host.deposit(totalBet);
        numberBets.clear();
        colorBets.clear();
        straightBets.clear();
        outsideBets.clear();
        totalBet = 0;
        table.clearCoins();
    }

    // ── spin ──────────────────────────────────────────────────────────────

    private void spin() {
        if (totalBet <= 0) {
            host.message("§cまずベットしてください！");
            return;
        }
        phase = Phase.SPINNING;
        winningNumber = WHEEL_ORDER[host.random().nextInt(WHEEL_ORDER.length)];
        host.message("§6§l══ ROULETTE SPIN ══");
        host.sound("minecraft:block.wooden_button.click_on", 1f, 0.8f);
        host.closeUi();
        table.startSpin(winningNumber, host.config().roulette.spinTicks, this::landed);
    }

    private void landed() {
        if (isFinished()) return;
        table.showResult(winningNumber);
        host.sound("minecraft:block.stone.place", 1f, 1.2f);
        host.sound("minecraft:block.note_block.bell", 0.6f, 0.9f);
        boolean won = settle();
        host.later(won ? 140 : 120, this::finish);
    }

    /** 結果を精算する (1 回だけ)。当たりなら true。 */
    private boolean settle() {
        phase = Phase.DONE;

        boolean red = isRed(winningNumber);
        String col = winningNumber == 0 ? "§a" : (red ? "§c" : "§8");
        String label = winningNumber == 0 ? "§a(GREEN)" : (red ? "§c(RED)" : "§8(BLACK)");
        host.message(String.format("§6§lボールは %s§l%d %s §6§lに止まりました", col, winningNumber, label));

        winnings = numberBets.getOrDefault(winningNumber, 0.0) * 36;
        for (var e : colorBets.entrySet()) {
            if (winningNumber != 0 && e.getKey().equals("red") == red) winnings += e.getValue() * 2;
        }
        if (winnings > 0) {
            payout(totalBet, winnings / totalBet);
            host.sound("minecraft:entity.player.levelup", 1f, 1f);
            host.effect("win");
            return true;
        }
        host.message(String.format("§cまた挑戦してね！ §e%.0f %s §cを失いました。", totalBet, sym()));
        host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
        return false;
    }

    @Override
    public void onClientClosed() {
        if (phase == Phase.BET) finish(); // 回転中は UI を自分で閉じているので無視
    }

    @Override
    protected void onFinish() {
        if (phase == Phase.BET && totalBet > 0) {
            // スピン前に画面を閉じた/退出した場合は返金
            if (host.economyEnabled()) host.deposit(totalBet);
            host.message(String.format("§7ルーレットを終了しました。§e%.0f %s §7を返金します。", totalBet, sym()));
        } else if (phase == Phase.SPINNING) {
            // 回転中に切断/サーバー停止: 結果は確定済みなので、演出を待たずに精算する
            settle();
        }
        table.reset();
        table.clearCoins();
        table.setActive(false);
    }

    // ── state ─────────────────────────────────────────────────────────────

    @Override
    public JsonObject state() {
        JsonObject o = base(phase.name());
        o.addProperty("min", host.config().roulette.minBet);
        o.addProperty("max", host.config().roulette.maxBet);
        o.addProperty("chip", chip);
        o.addProperty("total", totalBet);
        o.addProperty("canSpin", totalBet > 0);
        JsonObject nb = new JsonObject();
        for (var e : straightBets.entrySet()) nb.addProperty(String.valueOf(e.getKey()), e.getValue());
        o.add("numbers", nb); // 単一数字へのベット額 (コイン表示用)
        JsonArray covered = new JsonArray();
        for (int n : numberBets.keySet()) covered.add(n);
        o.add("covered", covered); // 何らかのベットが掛かっている数字
        JsonObject ob = new JsonObject();
        for (var e : outsideBets.entrySet()) ob.addProperty(e.getKey(), e.getValue());
        o.add("outside", ob);
        JsonObject cb = new JsonObject();
        for (var e : colorBets.entrySet()) cb.addProperty(e.getKey(), e.getValue());
        o.add("colors", cb);
        JsonArray reds = new JsonArray();
        for (int r : RED_NUMBERS) reds.add(r);
        o.add("reds", reds);
        return o;
    }
}
