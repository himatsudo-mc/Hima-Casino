package com.himacasino.game;

import com.google.gson.JsonObject;

/**
 * HIGH & LOW。ディーラーのカードより大きい方を 2 枚の伏せ札から選ぶ。
 * Phase: BET → PLAYING → RESULT
 */
public class HighLowGame extends CasinoGame {

    public enum Phase { BET, PLAYING, RESULT }

    private Phase phase = Phase.BET;
    private double draftBet = 0;
    private double bet = 0;
    private int dealerCard;
    private final int[] hidden = new int[2];
    private int chosen = -1;
    private boolean resultPending = false;
    private double payout = 0;

    public HighLowGame(GameHost host) {
        super(host);
    }

    @Override public String id() { return "highlow"; }

    public Phase phase() { return phase; }

    /** テスト用 */
    void rig(int dealer, int left, int right) {
        dealerCard = dealer;
        hidden[0] = left;
        hidden[1] = right;
    }

    @Override
    public void handle(String action, JsonObject args) {
        if (isFinished()) return;
        switch (action) {
            case "chip" -> {
                if (phase != Phase.BET) return;
                double v = num(args, "v", 0);
                if (!isChip(v)) return;
                draftBet = Math.min(draftBet + v, host.config().highlow.maxBet);
                refresh();
            }
            case "clear" -> {
                if (phase != Phase.BET) return;
                draftBet = 0;
                refresh();
            }
            case "play" -> { if (phase == Phase.BET) startRound(); }
            case "pick" -> {
                if (phase != Phase.PLAYING) return;
                int idx = intArg(args, "i", -1);
                if (idx == 0 || idx == 1) reveal(idx);
            }
            case "again" -> {
                if (phase != Phase.RESULT || resultPending) return;
                draftBet = bet;
                startRound();
            }
            case "change" -> {
                if (phase != Phase.RESULT || resultPending) return;
                draftBet = bet;
                phase = Phase.BET;
                refresh();
            }
            case "exit" -> finish();
            default -> {}
        }
    }

    private void startRound() {
        double min = host.config().highlow.minBet;
        if (draftBet < min) {
            host.message(String.format("§c最低ベットは §e%.0f §cです。", min));
            return;
        }
        bet = draftBet;
        if (!charge(bet)) {
            draftBet = 0;
            phase = Phase.BET;
            refresh();
            return;
        }
        var rng = host.random();
        dealerCard = rng.nextInt(13) + 1;
        hidden[0] = rng.nextInt(13) + 1;
        hidden[1] = rng.nextInt(13) + 1;
        chosen = -1;
        payout = 0;
        phase = Phase.PLAYING;
        host.message(String.format("§6ディーラー: §f§l%s  §7(ベット: §e%.0f§7)", Card.rankName(dealerCard), bet));
        refresh();
    }

    private void reveal(int idx) {
        chosen = idx;
        int mine = hidden[idx];
        host.sound("minecraft:item.armor.equip_leather", 1f, 1.8f);
        host.message(String.format("§eあなたのカード: §f§l%s", Card.rankName(mine)));

        phase = Phase.RESULT;
        resultPending = true;
        refresh();

        // 演出のための少しの間のあとに精算する
        host.later(20, () -> {
            if (isFinished()) return;
            resultPending = false;
            if (mine > dealerCard) {
                double mult = host.config().highlow.winMultiplier;
                payout = bet * mult;
                payout(bet, mult);
                host.sound("minecraft:entity.player.levelup", 1f, 1f);
                host.effect("win");
            } else if (mine < dealerCard) {
                host.message(String.format("§c§lディーラーの勝ち！ §c%.0f %s を失いました。", bet, sym()));
                host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
            } else {
                payout = bet;
                if (host.economyEnabled()) host.deposit(bet);
                host.message("§7§lDRAW! ベットが返却されました。");
                host.sound("minecraft:block.note_block.chime", 1f, 1f);
            }
            refresh();
        });
    }

    @Override
    public JsonObject state() {
        JsonObject o = base(phase.name());
        var cfg = host.config().highlow;
        o.addProperty("min", cfg.minBet);
        o.addProperty("max", cfg.maxBet);
        o.addProperty("multiplier", cfg.winMultiplier);
        o.addProperty("bet", phase == Phase.BET ? draftBet : bet);
        o.addProperty("canPlay", draftBet >= cfg.minBet && canAfford(draftBet));
        o.addProperty("resultPending", resultPending);
        o.addProperty("payout", payout);
        if (phase != Phase.BET) {
            o.addProperty("dealer", Card.rankName(dealerCard));
            o.addProperty("dealerValue", dealerCard);
        }
        if (phase == Phase.RESULT) {
            int mine = hidden[chosen];
            o.addProperty("chosen", chosen);
            o.addProperty("left", Card.rankName(hidden[0]));
            o.addProperty("right", Card.rankName(hidden[1]));
            o.addProperty("outcome", mine > dealerCard ? "WIN" : mine < dealerCard ? "LOSE" : "DRAW");
        }
        o.addProperty("canAgain", phase == Phase.RESULT && !resultPending && canAfford(bet) && bet >= cfg.minBet);
        return o;
    }
}
