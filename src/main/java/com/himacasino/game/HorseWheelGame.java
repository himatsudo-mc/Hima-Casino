package com.himacasino.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Arrays;

/**
 * ホースホイール。6 色の馬に賭け、縦型ホイールを回す。
 * ベット画面は Chromium GUI、ホイールの回転演出は 3D 卓 ({@link WheelView}) が担当。
 */
public class HorseWheelGame extends CasinoGame {

    public static final int[] WHEEL_ORDER = {5, 0, 1, 3, 0, 2, 1, 4, 0, 1, 2, 0, 3, 1, 0, 4, 0, 1, 2, 3, 0, 1, 2, 0};
    public static final double[] MULTIPLIERS = {2.0, 3.0, 5.0, 8.0, 10.0, 20.0};
    public static final String[] HORSE_NAMES = {"WHITE", "YELLOW", "BLUE", "GREEN", "RED", "GOLD"};
    public static final String[] HORSE_COLORS = {"§f", "§e", "§b", "§a", "§c", "§6"};

    public interface WheelView {
        /** targetSeg (0..23) で止まる回転を開始し、止まったら onFinished を呼ぶ */
        void startSpin(int targetSeg, Runnable onFinished);
    }

    public enum Phase { BET, SPINNING }

    private final WheelView wheel;
    private Phase phase = Phase.BET;
    private final double[] bets = new double[6];
    private int selected = 0;
    private double lastBet = 0;
    private int resultHorse = -1;
    private double lastWin = 0;

    public HorseWheelGame(GameHost host, WheelView wheel) {
        super(host);
        this.wheel = wheel;
    }

    @Override public String id() { return "horsewheel"; }

    public Phase phase() { return phase; }

    private double total() {
        double s = 0;
        for (double b : bets) s += b;
        return s;
    }

    @Override
    public void handle(String action, JsonObject args) {
        if (isFinished() || phase != Phase.BET) return;
        switch (action) {
            case "select" -> {
                int i = intArg(args, "i", -1);
                if (i < 0 || i > 5) return;
                selected = i;
                refresh();
            }
            case "chip" -> {
                double v = num(args, "v", 0);
                if (!isChip(v)) return;
                bets[selected] = Math.min(bets[selected] + v, host.config().highlow.maxBet);
                refresh();
            }
            case "clear" -> {
                bets[selected] = 0;
                refresh();
            }
            case "spin" -> {
                double t = total();
                if (t > 0) startSpin(t);
            }
            case "exit" -> finish();
            default -> {}
        }
    }

    @Override
    public void onClientClosed() {
        if (phase == Phase.BET) finish(); // 回転中は UI を自分で閉じているので無視
    }

    private void startSpin(double total) {
        if (!charge(total)) {
            refresh();
            return;
        }
        phase = Phase.SPINNING;
        lastBet = total;
        int targetSeg = host.random().nextInt(WHEEL_ORDER.length);
        int horse = WHEEL_ORDER[targetSeg];
        double winnerBet = bets[horse];
        host.message("§d§l══ HORSE WHEEL ══ §d§l▶ SPIN!");
        host.closeUi();

        wheel.startSpin(targetSeg, () -> {
            if (isFinished()) return;
            resultHorse = horse;
            double winnings = winnerBet > 0 ? winnerBet * MULTIPLIERS[horse] : 0;
            lastWin = winnings;
            host.message("§d§l══ HORSE WHEEL RESULT ══");
            host.message("§7結果: " + HORSE_COLORS[horse] + "§l" + HORSE_NAMES[horse]
                    + String.format(" §7(×%.0f)", MULTIPLIERS[horse]));
            if (winnings > 0) {
                if (host.economyEnabled()) host.deposit(winnings);
                host.message(String.format("§a§l★ WIN! §a+%.0f %s", winnings, sym()));
                host.sound("minecraft:entity.player.levelup", 1f, 1f);
                host.effect("win");
            } else {
                host.message(String.format("§c§l✗ LOSE! §c-%.0f %s", lastBet, sym()));
                host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
            }
            // ホイールが止まって 4 秒後にベット画面を開き直す
            host.later(80, () -> {
                if (isFinished()) return;
                Arrays.fill(bets, 0);
                phase = Phase.BET;
                open();
            });
        });
    }

    @Override
    public JsonObject state() {
        JsonObject o = base(phase.name());
        o.addProperty("max", host.config().highlow.maxBet);
        o.addProperty("selected", selected);
        o.addProperty("total", total());
        o.addProperty("canSpin", total() > 0 && canAfford(total()));
        o.addProperty("lastHorse", resultHorse);
        o.addProperty("lastWin", lastWin);
        JsonArray horses = new JsonArray();
        for (int i = 0; i < 6; i++) {
            JsonObject h = new JsonObject();
            h.addProperty("name", HORSE_NAMES[i]);
            h.addProperty("multiplier", MULTIPLIERS[i]);
            h.addProperty("bet", bets[i]);
            horses.add(h);
        }
        o.add("horses", horses);
        return o;
    }
}
