package com.himacasino.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ヘッズアップ ファイブカードドロー vs ハウス (1 回の交換 + 1 回のベットラウンド)。
 * Phase: ANTE → DRAW → BETTING → RESULT
 */
public class PokerGame extends CasinoGame {

    public enum Phase { ANTE, DRAW, BETTING, RESULT }
    public enum Result { NONE, WIN, LOSE, PUSH, DEALER_FOLDED, PLAYER_FOLDED }

    private Phase phase = Phase.ANTE;
    private Result result = Result.NONE;

    private double draftAnte = 0;
    private double raiseDraft = 0;
    private double ante = 0;

    private final Deck deck;
    private final DealerAI ai;
    private final List<Card> player = new ArrayList<>();
    private final List<Card> dealer = new ArrayList<>();
    private final Set<Integer> exchange = new LinkedHashSet<>();

    private double pot = 0;
    private double contribution = 0; // プレイヤーが実際に出した額 (ポットにはハウス分も入っている)
    private boolean awaitingCall = false;
    private double dealerBet = 0;
    private boolean revealDealer = false;
    private HandEvaluator.HandRank playerRank;
    private HandEvaluator.HandRank dealerRank;
    private double payout = 0;

    public PokerGame(GameHost host) {
        super(host);
        this.deck = new Deck(host.random());
        this.ai = new DealerAI(host.random());
    }

    @Override public String id() { return "poker"; }

    public Phase phase() { return phase; }
    public Result result() { return result; }
    public double payout() { return payout; }

    // ── actions ───────────────────────────────────────────────────────────

    @Override
    public void handle(String action, JsonObject args) {
        if (isFinished()) return;
        switch (action) {
            case "chip" -> chip(num(args, "v", 0));
            case "clear" -> {
                if (phase == Phase.ANTE) draftAnte = 0;
                else if (phase == Phase.BETTING && !awaitingCall) raiseDraft = 0;
                else return;
                refresh();
            }
            case "deal" -> { if (phase == Phase.ANTE) startRound(); }
            case "toggle" -> {
                if (phase != Phase.DRAW) return;
                int i = intArg(args, "i", -1);
                if (i < 0 || i >= player.size()) return;
                if (!exchange.remove(i)) exchange.add(i);
                refresh();
            }
            case "draw" -> { if (phase == Phase.DRAW) confirmDraw(); }
            case "check" -> { if (phase == Phase.BETTING && !awaitingCall) playerChecks(); }
            case "bet" -> { if (phase == Phase.BETTING && !awaitingCall) playerBets(raiseDraft); }
            case "call" -> { if (phase == Phase.BETTING && awaitingCall) playerCalls(); }
            case "fold" -> { if (phase == Phase.BETTING) settlePlayerFold(); }
            case "again" -> {
                if (phase != Phase.RESULT) return;
                if (ante < minBet() || !canAfford(ante)) {
                    host.message("§c残高が不足しているため同じアンティで再戦できません。");
                    return;
                }
                draftAnte = ante;
                startRound();
            }
            case "change" -> {
                if (phase != Phase.RESULT) return;
                draftAnte = ante;
                phase = Phase.ANTE;
                refresh();
            }
            case "exit" -> finish();
            default -> {}
        }
    }

    private void chip(double v) {
        if (!isChip(v)) return;
        if (phase == Phase.ANTE) {
            draftAnte = Math.min(draftAnte + v, maxBet());
        } else if (phase == Phase.BETTING && !awaitingCall) {
            raiseDraft = Math.min(raiseDraft + v, raiseMax());
        } else {
            return;
        }
        refresh();
    }

    // ── round ─────────────────────────────────────────────────────────────

    private void startRound() {
        if (draftAnte < minBet()) {
            host.message(String.format("§c最低アンティは §e%.0f §cです。", minBet()));
            return;
        }
        ante = draftAnte;
        if (!charge(ante)) {
            draftAnte = 0;
            phase = Phase.ANTE;
            refresh();
            return;
        }
        deck.reshuffle();
        player.clear();
        dealer.clear();
        exchange.clear();
        pot = ante * 2; // プレイヤーのアンティ + ハウスの同額
        contribution = ante;
        result = Result.NONE;
        revealDealer = false;
        awaitingCall = false;
        dealerBet = 0;
        raiseDraft = 0;
        payout = 0;
        playerRank = null;
        dealerRank = null;

        for (int i = 0; i < 5; i++) player.add(deck.draw());
        for (int i = 0; i < 5; i++) dealer.add(deck.draw());

        phase = Phase.DRAW;
        host.message(String.format("§2§lPoker! §7アンティ: §e%.0f §7ポット: §e%.0f %s", ante, pot, sym()));
        refresh();
    }

    private void confirmDraw() {
        int exchanged = exchange.size();
        for (int idx : exchange) player.set(idx, deck.draw());
        exchange.clear();

        for (int idx : DealerAI.discardIndices(dealer)) dealer.set(idx, deck.draw());

        host.message(exchanged > 0
                ? String.format("§7%d 枚交換しました。", exchanged)
                : "§7スタンドパット(交換なし)しました。");
        phase = Phase.BETTING;
        refresh();
    }

    private void playerChecks() {
        host.message("§7チェックしました。");
        double maxDealerBet = Math.min(maxBet(), availableBetMax());
        double strength = DealerAI.strength(dealer);
        double amt = maxDealerBet >= minBet() ? ai.decideBet(strength, pot, maxDealerBet) : 0;
        if (amt <= 0) {
            host.message("§7ディーラーもチェックしました。");
            settleShowdown();
        } else {
            dealerBet = amt;
            pot += amt;
            awaitingCall = true;
            host.message(String.format("§eディーラーが §6%.0f %s §eをベットしました！コールまたはフォールドしてください。", amt, sym()));
            refresh();
        }
    }

    private void playerCalls() {
        double amt = dealerBet;
        if (!canAfford(amt)) {
            host.message("§c残高が不足しているためコールできません。");
            return;
        }
        if (host.economyEnabled()) host.withdraw(amt);
        contribution += amt;
        awaitingCall = false;
        dealerBet = 0;
        host.message(String.format("§7%.0f %s §7でコールしました。", amt, sym()));
        settleShowdown();
    }

    private void playerBets(double amount) {
        if (amount < minBet() || amount > raiseMax()) {
            host.message(String.format("§cベット額は §e%.0f§c〜§e%.0f §cで指定してください。", minBet(), raiseMax()));
            return;
        }
        if (!canAfford(amount)) {
            host.message("§c残高が不足しているためベットできません。");
            return;
        }
        if (host.economyEnabled()) host.withdraw(amount);
        contribution += amount;
        double potBefore = pot;
        pot += amount;
        raiseDraft = 0;
        host.message(String.format("§a%.0f %s §aをベットしました。", amount, sym()));

        if (ai.shouldCall(DealerAI.strength(dealer), amount, potBefore)) {
            pot += amount; // ハウスが同額コール (実際の入出金はなし)
            host.message("§eディーラーがコールしました。");
            settleShowdown();
        } else {
            host.message("§eディーラーがフォールドしました！");
            settleDealerFold();
        }
    }

    private void settleShowdown() {
        revealDealer = true;
        playerRank = HandEvaluator.evaluate(player);
        dealerRank = HandEvaluator.evaluate(dealer);
        phase = Phase.RESULT;
        int cmp = playerRank.compareTo(dealerRank);
        if (cmp > 0) { result = Result.WIN; win(); }
        else if (cmp < 0) { result = Result.LOSE; lose(); }
        else { result = Result.PUSH; push(); }
        host.message(String.format("§7あなた: §f%s §7| ディーラー: §f%s", playerRank.displayName(), dealerRank.displayName()));
        refresh();
    }

    private void settleDealerFold() {
        revealDealer = false;
        result = Result.DEALER_FOLDED;
        phase = Phase.RESULT;
        win();
        refresh();
    }

    private void settlePlayerFold() {
        revealDealer = false;
        result = Result.PLAYER_FOLDED;
        phase = Phase.RESULT;
        awaitingCall = false;
        lose();
        refresh();
    }

    private void win() {
        double mult = pot / ante;
        payout = pot;
        payout(ante, mult);
        host.sound("minecraft:entity.player.levelup", 1f, 1f);
        host.effect("win");
    }

    private void lose() {
        payout = 0;
        host.message(String.format("§c§lLose! §e%.0f %s §cを失いました。", contribution, sym()));
        host.sound("minecraft:block.note_block.bass", 1f, 0.5f);
    }

    private void push() {
        payout = contribution;
        if (host.economyEnabled()) host.deposit(contribution);
        host.message(String.format("§7§lPUSH! §7賭け金 §e%.0f %s §7が返還されました。", contribution, sym()));
        host.sound("minecraft:block.note_block.chime", 1f, 1f);
    }

    // ── limits ────────────────────────────────────────────────────────────

    private double minBet() { return host.config().poker.minBet; }
    private double maxBet() { return host.config().poker.maxBet; }

    private double availableBetMax() {
        return host.economyEnabled() ? host.balance() : maxBet();
    }

    private double raiseMax() {
        return Math.min(maxBet(), availableBetMax());
    }

    // ── state ─────────────────────────────────────────────────────────────

    @Override
    public JsonObject state() {
        JsonObject o = base(phase.name());
        o.addProperty("min", minBet());
        o.addProperty("max", maxBet());
        o.addProperty("ante", phase == Phase.ANTE ? draftAnte : ante);
        o.addProperty("canDeal", draftAnte >= minBet() && canAfford(draftAnte));
        o.addProperty("pot", pot);
        o.addProperty("raise", raiseDraft);
        o.addProperty("raiseMax", raiseMax());
        o.addProperty("canRaise", raiseMax() >= minBet());
        o.addProperty("awaitingCall", awaitingCall);
        o.addProperty("dealerBet", dealerBet);
        o.addProperty("canCall", awaitingCall && canAfford(dealerBet));
        o.addProperty("result", result.name());
        o.addProperty("payout", payout);
        o.addProperty("canAgain", phase == Phase.RESULT && ante >= minBet() && canAfford(ante));

        JsonArray pc = new JsonArray();
        for (Card c : player) pc.add(c.toJson());
        o.add("player", pc);
        JsonArray dc = new JsonArray();
        for (Card c : dealer) dc.add(revealDealer ? c.toJson() : Card.hiddenJson());
        o.add("dealer", dc);
        JsonArray ex = new JsonArray();
        for (int i : exchange) ex.add(i);
        o.add("exchange", ex);

        if (phase == Phase.BETTING || phase == Phase.RESULT) {
            HandEvaluator.HandRank pr = playerRank != null ? playerRank
                    : (player.size() == 5 ? HandEvaluator.evaluate(player) : null);
            if (pr != null) o.addProperty("playerHand", pr.displayName());
        }
        if (revealDealer && dealerRank != null) o.addProperty("dealerHand", dealerRank.displayName());
        return o;
    }
}
