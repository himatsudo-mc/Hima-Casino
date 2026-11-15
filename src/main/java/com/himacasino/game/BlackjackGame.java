package com.himacasino.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * ブラックジャック (ディーラーは 17 でスタンド / ナチュラルは 3:2 / ダブルダウン可)。
 * Phase: BET → PLAYING → RESULT → (BET | PLAYING)
 */
public class BlackjackGame extends CasinoGame {

    public enum Phase { BET, PLAYING, RESULT }
    public enum Result { NONE, WIN, BLACKJACK, PUSH, LOSE }

    private Phase phase = Phase.BET;
    private Result result = Result.NONE;
    private double draftBet = 0;
    private double bet = 0;
    private double payout = 0;
    private boolean dealerHidden = true;

    private final Deck deck;
    private final List<Card> player = new ArrayList<>();
    private final List<Card> dealer = new ArrayList<>();

    public BlackjackGame(GameHost host) {
        super(host);
        this.deck = new Deck(host.random());
    }

    /** テスト用 */
    Deck deck() { return deck; }

    @Override public String id() { return "blackjack"; }

    public Phase phase() { return phase; }
    public Result result() { return result; }
    public double payout() { return payout; }

    // ── actions ───────────────────────────────────────────────────────────

    @Override
    public void handle(String action, JsonObject args) {
        if (isFinished()) return;
        switch (action) {
            case "chip" -> {
                if (phase != Phase.BET) return;
                double v = num(args, "v", 0);
                if (!isChip(v)) return;
                draftBet = Math.min(draftBet + v, host.config().blackjack.maxBet);
                refresh();
            }
            case "clear" -> {
                if (phase != Phase.BET) return;
                draftBet = 0;
                refresh();
            }
            case "deal" -> {
                if (phase != Phase.BET) return;
                startRound();
            }
            case "again" -> {
                if (phase != Phase.RESULT) return;
                draftBet = bet;
                startRound();
            }
            case "change" -> {
                if (phase != Phase.RESULT) return;
                draftBet = bet;
                phase = Phase.BET;
                refresh();
            }
            case "hit" -> { if (phase == Phase.PLAYING) hit(); }
            case "stand" -> { if (phase == Phase.PLAYING) stand(); }
            case "double" -> { if (phase == Phase.PLAYING) doubleDown(); }
            case "exit" -> finish();
            default -> {}
        }
    }

    // ── round ─────────────────────────────────────────────────────────────

    private void startRound() {
        double min = host.config().blackjack.minBet;
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
        player.clear();
        dealer.clear();
        dealerHidden = true;
        result = Result.NONE;
        payout = 0;

        player.add(deck.draw());
        dealer.add(deck.draw()); // 伏せ札
        player.add(deck.draw());
        dealer.add(deck.draw()); // 表向き

        boolean pbj = isBlackjack(player);
        boolean dbj = isBlackjack(dealer);
        if (pbj || dbj) {
            dealerHidden = false;
            result = (pbj && dbj) ? Result.PUSH : pbj ? Result.BLACKJACK : Result.LOSE;
            settle();
            return;
        }
        phase = Phase.PLAYING;
        host.message(String.format("§2§lBlackjack! §7あなた: §e%d  §7ディーラーの表: §e%s",
                value(player), dealer.get(1).shortName()));
        refresh();
    }

    private void hit() {
        player.add(deck.draw());
        int pv = value(player);
        if (pv > 21) {
            dealerHidden = false;
            result = Result.LOSE;
            settle();
        } else if (pv == 21) {
            stand();
        } else {
            refresh();
        }
    }

    private void stand() {
        dealerHidden = false;
        while (value(dealer) < 17) dealer.add(deck.draw());
        int pv = value(player);
        int dv = value(dealer);
        if (pv > 21) result = Result.LOSE;
        else if (dv > 21 || pv > dv) result = Result.WIN;
        else if (pv < dv) result = Result.LOSE;
        else result = Result.PUSH;
        settle();
    }

    private void doubleDown() {
        if (player.size() != 2) return;
        if (!canAfford(bet)) {
            host.message("§c残高が不足しているためダブルダウンできません。");
            return;
        }
        if (host.economyEnabled() && !host.withdraw(bet)) return;
        bet *= 2;
        player.add(deck.draw());
        if (value(player) > 21) {
            dealerHidden = false;
            result = Result.LOSE;
            settle();
        } else {
            stand();
        }
    }

    private void settle() {
        phase = Phase.RESULT;
        switch (result) {
            case BLACKJACK -> win(2.5);
            case WIN -> win(2.0);
            case PUSH -> {
                payout = bet;
                if (host.economyEnabled()) host.deposit(bet);
                host.message("§7§lPUSH! ベットが返却されました。");
                host.sound("minecraft:block.note_block.chime", 1f, 1f);
            }
            default -> {
                payout = 0;
                host.message(String.format("§c§lLose! §c%.0f %s を失いました。", bet, sym()));
                host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
            }
        }
        refresh();
    }

    private void win(double multiplier) {
        payout = bet * multiplier;
        payout(bet, multiplier);
        host.sound("minecraft:entity.player.levelup", 1f, 1f);
        host.effect("win");
    }

    // ── hand logic ────────────────────────────────────────────────────────

    static int cardValue(Card c) {
        return switch (c.rank()) {
            case 1 -> 11;
            case 11, 12, 13 -> 10;
            default -> c.rank();
        };
    }

    static int value(List<Card> hand) {
        int val = 0, aces = 0;
        for (Card c : hand) {
            if (c.rank() == 1) aces++;
            val += cardValue(c);
        }
        while (val > 21 && aces > 0) { val -= 10; aces--; }
        return val;
    }

    static boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && value(hand) == 21;
    }

    // ── state ─────────────────────────────────────────────────────────────

    @Override
    public JsonObject state() {
        JsonObject o = base(phase.name());
        var cfg = host.config().blackjack;
        o.addProperty("min", cfg.minBet);
        o.addProperty("max", cfg.maxBet);
        o.addProperty("bet", phase == Phase.BET ? draftBet : bet);
        o.addProperty("canDeal", draftBet >= cfg.minBet && canAfford(draftBet));
        o.addProperty("result", result.name());
        o.addProperty("payout", payout);

        JsonArray pc = new JsonArray();
        for (Card c : player) pc.add(c.toJson());
        o.add("player", pc);
        o.addProperty("playerValue", player.isEmpty() ? 0 : value(player));

        JsonArray dc = new JsonArray();
        for (int i = 0; i < dealer.size(); i++) {
            dc.add(dealerHidden && i == 0 ? Card.hiddenJson() : dealer.get(i).toJson());
        }
        o.add("dealer", dc);
        // 伏せ札があるときは表向きの札だけの合計は出さず "?" とする
        if (dealer.isEmpty()) o.addProperty("dealerValue", 0);
        else if (dealerHidden) o.add("dealerValue", com.google.gson.JsonNull.INSTANCE);
        else o.addProperty("dealerValue", value(dealer));

        o.addProperty("canDouble", phase == Phase.PLAYING && player.size() == 2 && canAfford(bet));
        o.addProperty("canAgain", phase == Phase.RESULT && canAfford(bet) && bet >= cfg.minBet);
        return o;
    }
}
