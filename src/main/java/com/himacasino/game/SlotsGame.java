package com.himacasino.game;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * スロットマシン (看板表示)。専用 GUI は無く、看板の数字がぐるぐる回る従来の見た目のまま。
 * 3 リールが 60/80/100 tick で順に止まり、組み合わせで配当が決まる。
 */
public class SlotsGame extends CasinoGame {

    /** 看板への表示 (テストでは記録するだけの実装を渡す) */
    public interface SignView {
        /** 2 行目 (リール) と 4 行目 (状態) を更新 */
        void setLines(String reelLine, String statusLine);

        /** リール行のみ更新 */
        void setReelLine(String reelLine);
    }

    private static final int REELS = 3;
    private static final int SYMBOLS = 7;
    private static final int BASE_SPIN_TICKS = 60;
    private static final int REEL_DELAY = 20;
    private static final String[] SYM_COLORS = {"§f", "§b", "§a", "§e", "§6", "§c", "§d"};

    private final double bet;
    private final int setting;
    private final SignView sign;
    private final int[] results = new int[REELS];
    private final boolean[] stopped = new boolean[REELS];
    private int tick = 0;
    private double lastMultiplier = 0;
    private boolean charged = false;
    private boolean settled = false;

    public SlotsGame(GameHost host, double bet, int setting, SignView sign) {
        super(host);
        this.bet = bet;
        this.setting = setting;
        this.sign = sign;
    }

    @Override public String id() { return "slots"; }

    @Override public JsonObject state() { return base("SIGN"); }

    @Override public void handle(String action, JsonObject args) {}

    @Override public void open() { start(); }

    public double lastMultiplier() { return lastMultiplier; }
    public int[] results() { return results.clone(); }

    /** 賭け金を引き落としてスピン開始 (残高不足なら即終了) */
    public void start() {
        if (!charge(bet)) {
            finish();
            return;
        }
        charged = true;
        sign.setLines("§e? §7| §e? §7| §e?", "§cPlaying...");
        host.message("§6§l╔══════════════════╗");
        host.message(String.format("§6§l║  §eSLOTS §7(設定 %d)  §6§l║", setting));
        host.message("§6§l╚══════════════════╝");
        host.message(String.format("§7ベット: §e%.0f %s", bet, sym()));
        host.sound("minecraft:block.lever.click", 1f, 1f);
        host.later(1, this::tick);
    }

    private void tick() {
        if (isFinished()) return;
        tick++;
        for (int i = 0; i < REELS; i++) {
            int stopAt = BASE_SPIN_TICKS + i * REEL_DELAY;
            if (stopped[i]) continue;
            if (tick < stopAt) {
                if (tick % 3 == 0) host.sound("minecraft:block.note_block.hat", 0.3f, 1.5f - i * 0.1f);
            } else {
                results[i] = rollSymbol();
                stopped[i] = true;
                host.sound("minecraft:block.note_block.pling", 1f, 1.0f + i * 0.1f);
            }
        }
        if (tick % 2 == 0) {
            sign.setReelLine(reelStr(true));
        }
        if (stopped[0] && stopped[1] && stopped[2]) {
            host.later(15, this::evaluate);
        } else {
            host.later(1, this::tick);
        }
    }

    private void evaluate() {
        if (isFinished()) return;
        boolean won = settle();
        host.later(won ? 120 : 100, this::finish);
    }

    /** 結果を精算する (1 回だけ)。当たりなら true。 */
    private boolean settle() {
        if (settled || !charged) return false;
        settled = true;
        for (int i = 0; i < REELS; i++) {
            if (!stopped[i]) { results[i] = rollSymbol(); stopped[i] = true; } // 途中終了時は残りのリールも抽選
        }
        host.message("§6結果: " + reelStr(false));
        double mult = multiplier();
        lastMultiplier = mult;
        if (mult > 0) {
            payout(bet, mult);
            sign.setLines(reelStr(false), "§aYou Win! +" + shortNum(bet * mult));
            host.sound("minecraft:entity.player.levelup", 1f, 1f);
            host.effect("win");
            return true;
        }
        host.message(String.format("§cハズレ… §e%.0f %s §cを失いました。", bet, sym()));
        sign.setLines(reelStr(false), "§cTry Again!");
        host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
        return false;
    }

    double multiplier() {
        var cfg = host.config();
        int a = results[0], b = results[1], c = results[2];
        if (a == 7 && b == 7 && c == 7) {
            host.message("§6§l★ JACKPOT! 7-7-7 ★");
            return cfg.slotsPayout("7-7-7", 100.0);
        }
        if (a == b && b == c) { host.message("§a§l3つ揃い！"); return cfg.slotsPayout("3-of-a-kind", 10.0); }
        int sevens = countOf(7);
        if (sevens == 2) { host.message("§a7が2つ！"); return cfg.slotsPayout("two-7s", 5.0); }
        if (sevens == 1) { host.message("§a7が1つ！"); return cfg.slotsPayout("one-7", 2.0); }
        if (a == b || b == c || a == c) { host.message("§a2つ揃い！"); return cfg.slotsPayout("two-of-a-kind", 1.5); }
        return 0;
    }

    private int countOf(int sym) {
        int n = 0;
        for (int r : results) if (r == sym) n++;
        return n;
    }

    private int rollSymbol() {
        List<Integer> w = host.config().slotsWeights(setting);
        if (w == null || w.size() < SYMBOLS) return host.random().nextInt(SYMBOLS) + 1;
        int total = w.stream().mapToInt(Integer::intValue).sum();
        int roll = host.random().nextInt(total);
        int cum = 0;
        for (int i = 0; i < w.size(); i++) {
            cum += w.get(i);
            if (roll < cum) return i + 1;
        }
        return SYMBOLS;
    }

    @Override
    protected void onFinish() {
        settle(); // 回転中に切断/サーバー停止しても精算する
        String reel = results[0] > 0 ? reelStr(false) : "§7? §7| §7? §7| §7?";
        sign.setLines(reel, "§aClick to Start!");
    }

    private String reelStr(boolean spinning) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < REELS; i++) {
            if (i > 0) sb.append(" §7| ");
            if (spinning && !stopped[i]) sb.append("§e?");
            else sb.append(SYM_COLORS[results[i] - 1]).append(results[i]);
        }
        return sb.toString();
    }

    private static String shortNum(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }

}
