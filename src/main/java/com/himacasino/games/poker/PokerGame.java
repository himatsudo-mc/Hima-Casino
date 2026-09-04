package com.himacasino.games.poker;

import com.himacasino.HimaCasino;
import com.himacasino.core.EconomyManager;
import com.himacasino.core.GameBase;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Poker (Sow) — heads-up Texas Hold'em against the house, played on a 54-slot inventory UI
 * that mirrors Blackjack's layout conventions (border glass, centered card rows, a 3-button
 * action row, a separate chip-based bet-setting sub-screen).
 *
 * ── Layout ──────────────────────────────────────────────────────────────────
 *   Row 0 (0-8):    border glass, pot/ante status at slot 4
 *   Row 1 (9-17):   community cards, centered in 11-15
 *   Row 2 (18-26):  status message at slot 22; dealer's (face-down until showdown) hole
 *                   cards at 19-20 as flavor, border glass elsewhere
 *   Row 3 (27-35):  player's hole cards at 30-31
 *   Row 4 (36-44):  border glass (divider)
 *   Row 5 (45-53):  action buttons: BET=47 / CHECK·CALL=49 / FOLD=51
 *
 * ── Betting model ───────────────────────────────────────────────────────────
 * The player sets an ante (bet-setting sub-screen, same chip UI as Blackjack) which both
 * sides put into the pot to start a hand. On each street (pre-flop/flop/turn/river) the
 * player acts first: BET opens the same chip UI to size a wager (the dealer then calls or
 * folds — never re-raises, keeping the interaction bounded to one bet per street), or
 * CHECK, after which the dealer may check back (advancing the street) or bet (the player
 * must then CALL or FOLD — the BET button is unavailable while a dealer bet is outstanding,
 * i.e. no re-raising). Reaching showdown at the river compares both hands with
 * {@link HandEvaluator}. The dealer's decisions come from {@link DealerAI}, a hand-strength
 * heuristic rather than a full solver — sufficient for a casino minigame opponent.
 *
 * Dealer bets are capped at the player's balance so the player can always afford to call —
 * this keeps the game free of all-in / side-pot bookkeeping, a deliberate scope limit.
 */
public class PokerGame extends GameBase {

    /** Marker holder identifying the main 54-slot table GUI (see {@link PokerListener}). */
    public static final class MainHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Marker holder identifying the 27-slot ante/bet-setting GUI. */
    public static final class BetHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final String MAIN_TITLE      = "§2§lPoker (Sow)";
    private static final String BET_TITLE_ANTE  = "§2Ante Setting";
    private static final String BET_TITLE_RAISE = "§2Bet Setting";

    private static final int GUI_SIZE = 54;

    // ── Layout slots ───────────────────────────────────────────────────────
    private static final int   S_POT             = 4;
    private static final int[] ROW0_BORDER       = {0, 1, 2, 3, 5, 6, 7, 8};
    private static final int[] COMMUNITY_SLOTS   = {11, 12, 13, 14, 15};
    private static final int[] ROW1_BORDER       = {9, 10, 16, 17};
    private static final int   S_MESSAGE         = 22;
    private static final int[] DEALER_HOLE_SLOTS = {19, 20};
    private static final int[] ROW2_BORDER       = {18, 21, 23, 24, 25, 26};
    private static final int[] HOLE_SLOTS        = {30, 31};
    private static final int[] ROW3_BORDER       = {27, 28, 29, 32, 33, 34, 35};
    private static final int[] ROW4_BORDER       = {36, 37, 38, 39, 40, 41, 42, 43, 44};
    private static final int   S_ACTION_LEFT     = 47; // BET / Set Ante / Play Again
    private static final int   S_ACTION_MIDDLE   = 49; // CHECK-CALL / — / Change Ante
    private static final int   S_ACTION_RIGHT    = 51; // FOLD / Deal / Exit
    private static final int[] ROW5_BORDER       = {45, 46, 48, 50, 52, 53};

    // Bet-setting screen (separate 27-slot inventory, identified by BetHolder)
    private static final int B_CURRENT    = 4;
    private static final int B_CHIP_START = 9;
    private static final int B_CLEAR      = 17;
    private static final int B_CONFIRM    = 22;

    private static final double[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    private enum Phase  { ANTE, PREFLOP, FLOP, TURN, RIVER, RESULT }
    private enum Result { NONE, WIN, LOSE, PUSH, DEALER_FOLDED, PLAYER_FOLDED }
    private enum BetMode { ANTE, RAISE }

    // ── State ──────────────────────────────────────────────────────────────
    private Phase   phase         = Phase.ANTE;
    private Result  lastResult    = Result.NONE;
    private double  currentBet    = 0; // draft ante (BetMode.ANTE) or draft bet size (BetMode.RAISE)
    private boolean transitioning = false;
    private BetMode betSettingMode = BetMode.ANTE;

    private final Deck deck = new Deck();
    private final List<Card> playerHole = new ArrayList<>();
    private final List<Card> dealerHole = new ArrayList<>();
    private final List<Card> board      = new ArrayList<>();

    private double  pot = 0;
    private boolean awaitingPlayerCallDecision = false;
    private double  dealerBetAmount = 0;
    private boolean revealDealerHand = false;
    private HandEvaluator.HandRank playerFinalRank;
    private HandEvaluator.HandRank dealerFinalRank;

    private Inventory mainInv;
    private Inventory betInv;

    public PokerGame(HimaCasino plugin, Player player) {
        super(plugin, player, 0);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onStart() {
        state = GameState.RUNNING;
        phase = Phase.ANTE;
        buildMain();
        player.openInventory(mainInv); // safe: called from command, not click handler
    }

    public boolean isTransitioning() { return transitioning; }

    /** Called by listener when player ESC's the bet-setting screen. */
    public void returnToMain() {
        if (state == GameState.FINISHED) return;
        populateMain();
        openScheduled(mainInv);
    }

    // ── Inventory builders ─────────────────────────────────────────────────

    private void buildMain() {
        MainHolder holder = new MainHolder();
        mainInv = plugin.getServer().createInventory(holder, GUI_SIZE, MAIN_TITLE);
        holder.inventory = mainInv;
        populateMain();
    }

    private void populateMain() {
        for (int i = 0; i < GUI_SIZE; i++) mainInv.setItem(i, null);
        switch (phase) {
            case ANTE                              -> populateAnte();
            case PREFLOP, FLOP, TURN, RIVER         -> populateStreet();
            case RESULT                             -> populateResult();
        }
    }

    private void populateAnte() {
        fillBorder(ROW0_BORDER);
        mainInv.setItem(S_POT, potStatusItem());
        fillBorder(ROW1_BORDER);
        for (int s : COMMUNITY_SLOTS) mainInv.setItem(s, bg());
        fillBorder(ROW2_BORDER);
        mainInv.setItem(DEALER_HOLE_SLOTS[0], bg());
        mainInv.setItem(DEALER_HOLE_SLOTS[1], bg());
        mainInv.setItem(S_MESSAGE, makeItem(Material.PAPER, MAIN_TITLE,
                List.of("§7アンティを設定して §aDEAL §7を押してください。")));
        fillBorder(ROW3_BORDER);
        mainInv.setItem(HOLE_SLOTS[0], bg());
        mainInv.setItem(HOLE_SLOTS[1], bg());
        fillBorder(ROW4_BORDER);
        fillBorder(ROW5_BORDER);

        mainInv.setItem(S_ACTION_LEFT, makeItem(Material.GOLD_INGOT, "§e§l⚙ Set Ante",
                List.of(anteStatusLore(), "§7Click to open Ante Setting screen")));
        mainInv.setItem(S_ACTION_MIDDLE, bg());

        double min = minBet();
        boolean canDeal = currentBet >= min && (!eco().isEnabled() || eco().getBalance(player) >= currentBet);
        mainInv.setItem(S_ACTION_RIGHT, canDeal
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ DEAL!", List.of(anteStatusLore()))
                : makeItem(Material.RED_CONCRETE, "§c§l✗ DEAL",
                    List.of(currentBet < min
                            ? String.format("§cMin ante: §e%.0f", min)
                            : "§cInsufficient balance")));
    }

    private void populateStreet() {
        fillBorder(ROW0_BORDER);
        mainInv.setItem(S_POT, potStatusItem());
        fillBorder(ROW1_BORDER);
        renderCommunity();
        fillBorder(ROW2_BORDER);
        renderDealerBacks();
        mainInv.setItem(S_MESSAGE, makeItem(Material.PAPER, streetTitle(), streetLore()));
        fillBorder(ROW3_BORDER);
        renderHoleCards();
        fillBorder(ROW4_BORDER);
        fillBorder(ROW5_BORDER);

        if (awaitingPlayerCallDecision) {
            mainInv.setItem(S_ACTION_LEFT, makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8(Raise unavailable)",
                    List.of("§7ディーラーのベットにコールかフォールドで応答")));
            mainInv.setItem(S_ACTION_MIDDLE, makeItem(Material.LIME_CONCRETE, "§a§l✓ CALL",
                    List.of(String.format("§7%.0f %s をコール", dealerBetAmount, sym()))));
            mainInv.setItem(S_ACTION_RIGHT, makeItem(Material.RED_CONCRETE, "§c§l✗ FOLD",
                    List.of("§7降りる")));
        } else {
            boolean canBet = availableBetMax() >= minBet();
            mainInv.setItem(S_ACTION_LEFT, canBet
                    ? makeItem(Material.GOLD_INGOT, "§e§l⚙ BET", List.of("§7ベット額を設定して賭ける"))
                    : makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8BET", List.of("§7残高不足")));
            mainInv.setItem(S_ACTION_MIDDLE, makeItem(Material.YELLOW_CONCRETE, "§e§l✓ CHECK",
                    List.of("§7このストリートを様子見")));
            mainInv.setItem(S_ACTION_RIGHT, makeItem(Material.RED_CONCRETE, "§c§l✗ FOLD",
                    List.of("§7降りる")));
        }
    }

    private void populateResult() {
        fillBorder(ROW0_BORDER);
        mainInv.setItem(S_POT, potStatusItem());
        fillBorder(ROW1_BORDER);
        renderCommunity();
        fillBorder(ROW2_BORDER);
        if (revealDealerHand) {
            mainInv.setItem(DEALER_HOLE_SLOTS[0], dealerHole.get(0).toItemStack());
            mainInv.setItem(DEALER_HOLE_SLOTS[1], dealerHole.get(1).toItemStack());
        } else {
            renderDealerBacks();
        }
        mainInv.setItem(S_MESSAGE, resultMessageItem());
        fillBorder(ROW3_BORDER);
        renderHoleCards();
        fillBorder(ROW4_BORDER);
        fillBorder(ROW5_BORDER);

        mainInv.setItem(S_ACTION_LEFT, makeItem(Material.LIME_CONCRETE, "§a§l▶ Play Again",
                List.of(String.format("§7Ante: §e%.0f %s", betAmount, sym()))));
        mainInv.setItem(S_ACTION_MIDDLE, makeItem(Material.GOLD_INGOT, "§e§l⚙ Change Ante",
                List.of("§7新しいアンティを設定")));
        mainInv.setItem(S_ACTION_RIGHT, makeItem(Material.RED_CONCRETE, "§c§l✗ Exit",
                List.of("§7ゲームを終了")));
    }

    // ── Bet-setting screen (shared by ante-setting and mid-round raises) ───

    public void openAnteSetting() {
        betSettingMode = BetMode.ANTE;
        BetHolder holder = new BetHolder();
        betInv = plugin.getServer().createInventory(holder, 27, BET_TITLE_ANTE);
        holder.inventory = betInv;
        refreshBetScreen();
        player.openInventory(betInv);
    }

    public void openRaiseSetting() {
        betSettingMode = BetMode.RAISE;
        currentBet = 0;
        BetHolder holder = new BetHolder();
        betInv = plugin.getServer().createInventory(holder, 27, BET_TITLE_RAISE);
        holder.inventory = betInv;
        refreshBetScreen();
        player.openInventory(betInv);
    }

    private double betScreenMax() {
        return betSettingMode == BetMode.ANTE ? maxBet() : Math.min(maxBet(), availableBetMax());
    }

    private void refreshBetScreen() {
        if (betInv == null) return;
        ItemStack bg = bg();
        for (int i = 0; i < 27; i++) betInv.setItem(i, bg);

        String label = betSettingMode == BetMode.ANTE ? "Ante" : "Bet";
        double max = betScreenMax();
        betInv.setItem(B_CURRENT, makeItem(Material.GOLD_BLOCK,
                String.format("§eCurrent %s: §6§l%.0f %s", label, currentBet, sym()),
                List.of("§7Add chips below", String.format("§7Max: §e%.0f", max))));

        Material[] mats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            betInv.setItem(B_CHIP_START + i, makeItem(mats[i],
                    "§a§l+" + (int) CHIP_VALUES[i],
                    List.of("§7Click to add §e" + (int) CHIP_VALUES[i])));
        }
        betInv.setItem(B_CLEAR, makeItem(Material.BARRIER, "§c§lClear", List.of("§7Reset to 0")));

        double min = minBet();
        boolean ok = currentBet >= min && currentBet <= max;
        betInv.setItem(B_CONFIRM, makeItem(
                ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                ok ? "§a§l✓ Confirm" : String.format("§c§l✗ Min %.0f required", min),
                List.of(String.format("§7%s: §e%.0f %s", label, currentBet, sym()))));
    }

    // ── Click handlers ─────────────────────────────────────────────────────

    public void handleMainClick(int slot) {
        switch (phase) {
            case ANTE                              -> handleAntePhaseClick(slot);
            case PREFLOP, FLOP, TURN, RIVER         -> handleStreetClick(slot);
            case RESULT                             -> handleResultClick(slot);
        }
    }

    private void handleAntePhaseClick(int slot) {
        if (slot == S_ACTION_LEFT) {
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openAnteSetting();
                transitioning = false;
            });
        } else if (slot == S_ACTION_RIGHT) {
            double min = minBet();
            if (currentBet < min) {
                player.sendMessage(String.format("§cMinimum ante is §e%.0f!", min));
                return;
            }
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                startRound();
                transitioning = false;
            });
        }
    }

    private void handleStreetClick(int slot) {
        if (slot == S_ACTION_LEFT) {
            if (awaitingPlayerCallDecision) return;
            if (availableBetMax() < minBet()) {
                player.sendMessage(String.format("§c最低ベット額 §e%.0f §cを賭けるための残高がありません。", minBet()));
                return;
            }
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openRaiseSetting();
                transitioning = false;
            });
        } else if (slot == S_ACTION_MIDDLE) {
            if (awaitingPlayerCallDecision) playerCalls();
            else                            playerChecks();
        } else if (slot == S_ACTION_RIGHT) {
            settlePlayerFold();
        }
    }

    private void handleResultClick(int slot) {
        switch (slot) {
            case S_ACTION_LEFT -> {
                double min = minBet();
                if (betAmount < min || (eco().isEnabled() && eco().getBalance(player) < betAmount)) {
                    player.sendMessage("§c残高が不足しているため同じアンティで再戦できません。");
                    return;
                }
                transitioning = true;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    currentBet = betAmount;
                    startRound();
                    transitioning = false;
                });
            }
            case S_ACTION_MIDDLE -> {
                phase = Phase.ANTE;
                populateMain();
            }
            case S_ACTION_RIGHT -> plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
        }
    }

    public void handleBetClick(int slot) {
        double max = betScreenMax();
        if (slot >= B_CHIP_START && slot < B_CHIP_START + CHIP_VALUES.length) {
            double add = CHIP_VALUES[slot - B_CHIP_START];
            currentBet = Math.min(currentBet + add, max);
            refreshBetScreen();
        } else if (slot == B_CLEAR) {
            currentBet = 0;
            refreshBetScreen();
        } else if (slot == B_CONFIRM) {
            double min = minBet();
            if (currentBet < min || currentBet > max) return;

            if (betSettingMode == BetMode.ANTE) {
                transitioning = true;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    populateMain();
                    player.openInventory(mainInv);
                    transitioning = false;
                });
            } else {
                double raiseAmount = currentBet;
                transitioning = true;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    playerBets(raiseAmount);
                    player.openInventory(mainInv);
                    transitioning = false;
                });
            }
        }
    }

    // ── Round management ───────────────────────────────────────────────────

    private void startRound() {
        betAmount = currentBet; // ante
        if (!chargeBet()) {
            currentBet = 0;
            populateMain();
            player.openInventory(mainInv); // safe: inside scheduled task
            return;
        }

        deck.reshuffle();
        playerHole.clear();
        dealerHole.clear();
        board.clear();
        pot = betAmount * 2; // player's ante + the house's matching ante
        lastResult = Result.NONE;
        revealDealerHand = false;
        awaitingPlayerCallDecision = false;
        dealerBetAmount = 0;

        playerHole.add(deck.draw());
        playerHole.add(deck.draw());
        dealerHole.add(deck.draw());
        dealerHole.add(deck.draw());

        phase = Phase.PREFLOP;
        populateMain();
        player.openInventory(mainInv); // safe: inside scheduled task
        player.sendMessage(String.format("§2§lPoker! §7アンティ: §e%.0f §7ポット: §e%.0f %s",
                betAmount, pot, sym()));
    }

    /** Player checks; the dealer then checks back (advancing the street) or bets. */
    private void playerChecks() {
        player.sendMessage("§7チェックしました。");
        double maxDealerBet = Math.min(maxBet(), availableBetMax());
        double strength = DealerAI.strength(dealerHole, board);
        double betAmt = maxDealerBet >= minBet() ? DealerAI.decideBet(strength, pot, maxDealerBet) : 0;
        if (betAmt <= 0) {
            player.sendMessage("§7ディーラーもチェックしました。");
            advanceStreet();
        } else {
            dealerBetAmount = betAmt;
            pot += betAmt;
            awaitingPlayerCallDecision = true;
            player.sendMessage(String.format("§eディーラーが §6%.0f %s §eをベットしました！コールまたはフォールドしてください。",
                    betAmt, sym()));
            populateMain();
        }
    }

    /** Player calls the dealer's outstanding bet. */
    private void playerCalls() {
        double amt = dealerBetAmount;
        if (eco().isEnabled() && eco().getBalance(player) < amt) {
            player.sendMessage("§c残高が不足しているためコールできません。");
            return;
        }
        eco().withdraw(player, amt);
        awaitingPlayerCallDecision = false;
        dealerBetAmount = 0;
        player.sendMessage(String.format("§7%.0f %s §7でコールしました。", amt, sym()));
        advanceStreet();
    }

    /** Player opens with a bet; the dealer immediately calls (never re-raises) or folds. */
    private void playerBets(double amount) {
        eco().withdraw(player, amount);
        double potBefore = pot;
        pot += amount;
        player.sendMessage(String.format("§a%.0f %s §aをベットしました。", amount, sym()));

        double strength = DealerAI.strength(dealerHole, board);
        if (DealerAI.shouldCall(strength, amount, potBefore)) {
            pot += amount; // dealer matches (house money, no real transaction)
            player.sendMessage("§eディーラーがコールしました。");
            advanceStreet();
        } else {
            player.sendMessage("§eディーラーがフォールドしました！");
            settleDealerFold();
        }
    }

    private void advanceStreet() {
        switch (phase) {
            case PREFLOP -> {
                board.add(deck.draw());
                board.add(deck.draw());
                board.add(deck.draw());
                phase = Phase.FLOP;
            }
            case FLOP -> { board.add(deck.draw()); phase = Phase.TURN; }
            case TURN -> { board.add(deck.draw()); phase = Phase.RIVER; }
            case RIVER -> { settleShowdown(); return; }
            default -> { return; }
        }
        awaitingPlayerCallDecision = false;
        dealerBetAmount = 0;
        populateMain();
    }

    private void settleShowdown() {
        revealDealerHand = true;
        List<Card> playerAll = new ArrayList<>(playerHole);
        playerAll.addAll(board);
        List<Card> dealerAll = new ArrayList<>(dealerHole);
        dealerAll.addAll(board);
        playerFinalRank = HandEvaluator.evaluate(playerAll);
        dealerFinalRank = HandEvaluator.evaluate(dealerAll);

        phase = Phase.RESULT;
        int cmp = playerFinalRank.compareTo(dealerFinalRank);
        if (cmp > 0)      { lastResult = Result.WIN;  onWin(pot / betAmount); }
        else if (cmp < 0) { lastResult = Result.LOSE;  onLoss(); }
        else              { lastResult = Result.PUSH;  onPush(); }
        populateMain();
    }

    private void settleDealerFold() {
        revealDealerHand = false;
        lastResult = Result.DEALER_FOLDED;
        phase = Phase.RESULT;
        onWin(pot / betAmount);
        populateMain();
    }

    private void settlePlayerFold() {
        revealDealerHand = false;
        lastResult = Result.PLAYER_FOLDED;
        phase = Phase.RESULT;
        onLoss();
        populateMain();
    }

    private void onPush() {
        double refund = pot / 2.0;
        eco().deposit(player, refund);
        player.sendMessage(String.format("§7§lPUSH! §7ポットの半分 §e%.0f %s §7が返還されました。", refund, sym()));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
    }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 25, 0.8, 0.5, 0.8, 0);
        state = GameState.RUNNING;
    }

    @Override
    public void onLoss() {
        state = GameState.RUNNING;
        player.sendMessage(String.format("§c§lLose! §cポット §e%.0f %s §cを失いました。", pot, sym()));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    @Override
    public void cleanup() {
        if (state == GameState.FINISHED) return;
        state = GameState.FINISHED;
        stopTickTask();
        InventoryHolder open = player.getOpenInventory().getTopInventory().getHolder();
        if (open instanceof MainHolder || open instanceof BetHolder) player.closeInventory();
        plugin.getGameManager().removePokerGame(player);
    }

    // ── Scheduled inventory helper (next-tick, outside click-event) ────────

    private void openScheduled(Inventory inv) {
        transitioning = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.openInventory(inv);
            transitioning = false;
        });
    }

    // ── Rendering helpers ───────────────────────────────────────────────────

    private void renderCommunity() {
        for (int i = 0; i < COMMUNITY_SLOTS.length; i++) {
            mainInv.setItem(COMMUNITY_SLOTS[i], i < board.size() ? board.get(i).toItemStack() : bg());
        }
    }

    private void renderHoleCards() {
        if (playerHole.size() >= 2) {
            mainInv.setItem(HOLE_SLOTS[0], playerHole.get(0).toItemStack());
            mainInv.setItem(HOLE_SLOTS[1], playerHole.get(1).toItemStack());
        } else {
            mainInv.setItem(HOLE_SLOTS[0], bg());
            mainInv.setItem(HOLE_SLOTS[1], bg());
        }
    }

    private void renderDealerBacks() {
        mainInv.setItem(DEALER_HOLE_SLOTS[0], makeCardBack());
        mainInv.setItem(DEALER_HOLE_SLOTS[1], makeCardBack());
    }

    private String streetTitle() {
        return switch (phase) {
            case PREFLOP -> "§2§lPRE-FLOP";
            case FLOP    -> "§2§lFLOP";
            case TURN    -> "§2§lTURN";
            case RIVER   -> "§2§lRIVER";
            default      -> MAIN_TITLE;
        };
    }

    private List<String> streetLore() {
        return awaitingPlayerCallDecision
                ? List.of("§eYour Turn §7— ディーラーのベットに応答してください")
                : List.of("§eYour Turn §7— BET / CHECK / FOLD");
    }

    private ItemStack potStatusItem() {
        if (phase == Phase.ANTE) {
            return makeItem(Material.GOLD_BLOCK, "§ePot", List.of(anteStatusLore()));
        }
        return makeItem(Material.GOLD_BLOCK, String.format("§ePot: §6§l%.0f %s", pot, sym()),
                List.of(String.format("§7Ante: §e%.0f %s", betAmount, sym())));
    }

    private String anteStatusLore() {
        return currentBet > 0
                ? String.format("§eAnte: §6§l%.0f %s", currentBet, sym())
                : "§7Ante: §enot set";
    }

    private ItemStack resultMessageItem() {
        String title = switch (lastResult) {
            case WIN            -> "§a§lWIN!";
            case LOSE           -> "§c§lLOSE";
            case PUSH           -> "§7§lPUSH";
            case DEALER_FOLDED  -> "§6§lDEALER FOLDED";
            case PLAYER_FOLDED  -> "§c§lYOU FOLDED";
            case NONE           -> "§7Result";
        };
        List<String> lore = new ArrayList<>();
        if (revealDealerHand) {
            lore.add("§7You: §f" + playerFinalRank.displayName());
            lore.add("§7Dealer: §f" + dealerFinalRank.displayName());
        }
        lore.add(String.format("§7Pot: §e%.0f %s", pot, sym()));

        Material mat = switch (lastResult) {
            case WIN, DEALER_FOLDED -> Material.GOLD_INGOT;
            case PUSH               -> Material.PAPER;
            default                 -> Material.BARRIER;
        };
        return makeItem(mat, title, lore);
    }

    // ── Item factories ─────────────────────────────────────────────────────

    private ItemStack makeCardBack() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        meta.setCustomModelData(Card.BACK_CUSTOM_MODEL_DATA);
        meta.setDisplayName("§b§l?");
        meta.setLore(List.of("§7Dealer's card (face-down)"));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(int[] slots) {
        ItemStack bgItem = bg();
        for (int s : slots) mainInv.setItem(s, bgItem);
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bg() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta = item.getItemMeta();
        meta.setDisplayName("§0");
        item.setItemMeta(meta);
        return item;
    }

    // ── Config / economy helpers ────────────────────────────────────────────

    private double availableBetMax() {
        return eco().isEnabled() ? eco().getBalance(player) : maxBet();
    }

    private double minBet() { return plugin.getConfigLoader().getPokerMinBet(); }
    private double maxBet() { return plugin.getConfigLoader().getPokerMaxBet(); }
    private String sym()    { return plugin.getConfigLoader().getCurrencySymbol(); }
    private EconomyManager eco() { return plugin.getEconomyManager(); }

    @Override public void onTick() { }
}
