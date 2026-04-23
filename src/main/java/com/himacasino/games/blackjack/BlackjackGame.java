package com.himacasino.games.blackjack;

import com.himacasino.HimaCasino;
import com.himacasino.core.EconomyManager;
import com.himacasino.core.GameBase;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Blackjack — 27-slot inventory UI (3 rows).
 *
 * ── BET phase ───────────────────────────────────────────────────────────────
 *   Row 0: bg  bg  bg  bg  bg  bg  bg  bg  bg
 *   Row 1: bg  bg  bg  bg  [SET BET]  bg  bg  bg  bg
 *   Row 2: bg  bg  bg  bg  bg  bg  [DEAL]  bg  bg
 *
 * ── PLAYING phase ───────────────────────────────────────────────────────────
 *   Row 0: [D0][D1][D2][D3][D4]  bg  bg  bg [DEALER INFO]
 *   Row 1:  bg  bg  bg  bg [PLAYER TOTAL]  bg  bg  bg  bg
 *   Row 2: [P0][P1][P2][P3][P4]  bg [HIT][STAND][DOUBLE]
 *
 * ── RESULT phase ────────────────────────────────────────────────────────────
 *   Row 0: [D0][D1][D2][D3][D4]  bg  bg  bg [DEALER TOTAL]
 *   Row 1:  bg  bg  bg  bg [RESULT]  bg  bg  bg  bg
 *   Row 2: [P0][P1][P2][P3][P4]  bg [AGAIN][CHG BET][EXIT]
 */
public class BlackjackGame extends GameBase {

    public static final String TITLE     = "§2§lBLACK JACK";
    public static final String BET_TITLE = "§2BJ Bet Setting";

    // ── Shared slot constants ──────────────────────────────────────────────
    private static final int S_DEALER_START = 0;   // dealer cards: 0-4
    private static final int S_DEALER_INFO  = 8;
    private static final int S_CENTER       = 13;  // player total / result
    private static final int S_PLAYER_START = 18;  // player cards: 18-22
    // BET phase
    private static final int S_SET_BET = 13;
    private static final int S_PLAY    = 24;
    // PLAYING phase
    private static final int S_HIT        = 24;
    private static final int S_STAND      = 25;
    private static final int S_DOUBLE_DOWN = 26;
    // RESULT phase
    private static final int S_PLAY_AGAIN = 24;
    private static final int S_CHANGE_BET = 25;
    private static final int S_EXIT       = 26;
    // Bet-setting screen (BET_TITLE)
    private static final int B_CURRENT    = 4;
    private static final int B_CHIP_START = 9;
    private static final int B_CLEAR      = 17;
    private static final int B_CONFIRM    = 22;

    private static final double[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    private enum Phase  { BET, PLAYING, RESULT }
    private enum Result { NONE, WIN, BLACKJACK, PUSH, LOSE }

    // ── State ──────────────────────────────────────────────────────────────
    private Phase   phase         = Phase.BET;
    private Result  lastResult    = Result.NONE;
    private double  currentBet    = 0;
    private boolean transitioning = false;
    private boolean doubled       = false;

    private final List<Integer> playerHand = new ArrayList<>();
    private final List<Integer> dealerHand = new ArrayList<>();
    private boolean dealerHidden = true;

    private Inventory mainInv;
    private Inventory betInv;

    public BlackjackGame(HimaCasino plugin, Player player) {
        super(plugin, player, 0);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onStart() {
        state = GameState.RUNNING;
        phase = Phase.BET;
        buildMain();
        player.openInventory(mainInv); // safe: called from command, not click handler
    }

    public boolean isTransitioning() { return transitioning; }

    /** Called by listener when player ESC's the bet-setting screen. */
    public void returnToMain() {
        if (state == GameState.FINISHED) return;
        phase = Phase.BET;
        buildMain();
        openScheduled(mainInv);
    }

    // ── Inventory builders ─────────────────────────────────────────────────

    private void buildMain() {
        mainInv = plugin.getServer().createInventory(null, 27, TITLE);
        populateMain();
    }

    /** Updates items in the already-open mainInv without reopening (no flicker). */
    private void refreshMain() {
        if (mainInv == null) return;
        populateMain();
    }

    private void populateMain() {
        ItemStack bg = bg();
        for (int i = 0; i < 27; i++) mainInv.setItem(i, bg);
        switch (phase) {
            case BET     -> populateBet();
            case PLAYING -> populatePlaying();
            case RESULT  -> populateResult();
        }
    }

    private void populateBet() {
        double min    = plugin.getConfigLoader().getBlackjackMinBet();
        String betStr = currentBet > 0
                ? String.format("§eBet: §6§l%.0f %s", currentBet, sym())
                : "§7Bet: §enot set";

        mainInv.setItem(S_SET_BET, makeItem(Material.GOLD_INGOT, "§e§l⚙ Set Bet",
                List.of(betStr, "§7Click to open Bet Setting screen")));

        boolean canPlay = currentBet >= min
                && (!eco().isEnabled() || eco().getBalance(player) >= currentBet);
        mainInv.setItem(S_PLAY, canPlay
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ DEAL!", List.of(betStr))
                : makeItem(Material.RED_CONCRETE, "§c§l✗ DEAL",
                    List.of(currentBet < min
                            ? String.format("§cMin bet: §e%.0f", min)
                            : "§cInsufficient balance")));
    }

    private void populatePlaying() {
        int pv = handValue(playerHand);

        // Dealer row
        for (int i = 0; i < dealerHand.size() && i < 5; i++) {
            mainInv.setItem(S_DEALER_START + i,
                    (i == 0 && dealerHidden) ? makeHidden() : makeCard(dealerHand.get(i)));
        }
        mainInv.setItem(S_DEALER_INFO, makeItem(Material.RED_CONCRETE,
                "§cDealer: §7?",
                List.of("§7One card is hidden", "§8Face-up: §e" + cardName(dealerHand.get(1))
                        + " §7(" + cardValue(dealerHand.get(1)) + ")")));

        // Player row
        for (int i = 0; i < playerHand.size() && i < 5; i++) {
            mainInv.setItem(S_PLAYER_START + i, makeCard(playerHand.get(i)));
        }
        mainInv.setItem(S_CENTER, makeItem(Material.LIME_CONCRETE,
                "§aYou: §e§l" + pv,
                List.of(String.format("§7Bet: §e%.0f %s", betAmount, sym()),
                        pv >= 18 ? "§7Be careful!" : "§7Your turn")));

        // Actions
        mainInv.setItem(S_HIT, makeItem(Material.LIME_CONCRETE, "§a§lHIT",
                List.of("§7Draw one more card")));
        mainInv.setItem(S_STAND, makeItem(Material.YELLOW_CONCRETE, "§e§lSTAND",
                List.of("§7End your turn, let dealer draw")));

        boolean canDouble = playerHand.size() == 2
                && (!eco().isEnabled() || eco().getBalance(player) >= betAmount);
        mainInv.setItem(S_DOUBLE_DOWN, canDouble
                ? makeItem(Material.GOLD_INGOT, "§6§lDOUBLE DOWN",
                    List.of(String.format("§7Bet: §e%.0f → §6§l%.0f", betAmount, betAmount * 2),
                            "§7Draw 1 card, then stand"))
                : makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8§lDOUBLE DOWN",
                    List.of(playerHand.size() != 2 ? "§cOnly on first 2 cards"
                                                   : "§cInsufficient balance")));
    }

    private void populateResult() {
        int pv = handValue(playerHand);
        int dv = handValue(dealerHand);

        // Dealer row (all face-up)
        for (int i = 0; i < dealerHand.size() && i < 5; i++) {
            mainInv.setItem(S_DEALER_START + i, makeCard(dealerHand.get(i)));
        }
        mainInv.setItem(S_DEALER_INFO, makeItem(
                dv > 21 ? Material.BARRIER : Material.RED_CONCRETE,
                "§cDealer: §e" + dv,
                List.of(dv > 21 ? "§c§lBUST!" : "§7Final total")));

        // Player row
        for (int i = 0; i < playerHand.size() && i < 5; i++) {
            mainInv.setItem(S_PLAYER_START + i, makeCard(playerHand.get(i)));
        }

        // Result banner
        Material rMat; String rName; List<String> rLore = new ArrayList<>();
        switch (lastResult) {
            case BLACKJACK -> {
                rMat  = Material.NETHER_STAR; rName = "§6§l★ BLACKJACK! ★";
                rLore.add(String.format("§6+%.0f %s §7(3:2)", betAmount * 1.5, sym()));
            }
            case WIN -> {
                rMat  = Material.GOLD_INGOT;  rName = "§a§l★ WIN!";
                rLore.add(String.format("§a+%.0f %s", betAmount, sym()));
            }
            case PUSH -> {
                rMat  = Material.PAPER;       rName = "§7§lPUSH — Tie";
                rLore.add("§7Bet returned");
            }
            default -> {
                rMat  = Material.BARRIER;     rName = "§c§l✗ LOSE";
                rLore.add(String.format("§c-%.0f %s", betAmount, sym()));
            }
        }
        rLore.add(String.format("§8You %d  §8Dealer %d", pv, dv));
        mainInv.setItem(S_CENTER, makeItem(rMat, rName, rLore));

        // Action buttons
        mainInv.setItem(S_PLAY_AGAIN, makeItem(Material.LIME_CONCRETE, "§a§l▶ Play Again",
                List.of(String.format("§7Bet: §e%.0f %s", currentBet, sym()))));
        mainInv.setItem(S_CHANGE_BET, makeItem(Material.GOLD_INGOT, "§e§l⚙ Change Bet",
                List.of("§7Set a new bet amount")));
        mainInv.setItem(S_EXIT, makeItem(Material.RED_CONCRETE, "§c§l✗ Exit",
                List.of("§7Close the game")));
    }

    // ── Bet-setting screen ─────────────────────────────────────────────────

    public void openBetSetting() {
        betInv = plugin.getServer().createInventory(null, 27, BET_TITLE);
        refreshBetScreen();
        player.openInventory(betInv);
    }

    private void refreshBetScreen() {
        if (betInv == null) return;
        ItemStack bg = bg();
        for (int i = 0; i < 27; i++) betInv.setItem(i, bg);

        betInv.setItem(B_CURRENT, makeItem(Material.GOLD_BLOCK,
                String.format("§eCurrent Bet: §6§l%.0f %s", currentBet, sym()),
                List.of("§7Add chips below",
                        String.format("§7Max: §e%.0f", plugin.getConfigLoader().getBlackjackMaxBet()))));

        Material[] mats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            betInv.setItem(B_CHIP_START + i, makeItem(mats[i],
                    "§a§l+" + (int) CHIP_VALUES[i],
                    List.of("§7Click to add §e" + (int) CHIP_VALUES[i])));
        }
        betInv.setItem(B_CLEAR, makeItem(Material.BARRIER, "§c§lClear",
                List.of("§7Reset bet to 0")));

        double min = plugin.getConfigLoader().getBlackjackMinBet();
        boolean ok = currentBet >= min;
        betInv.setItem(B_CONFIRM, makeItem(
                ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                ok ? "§a§l✓ Confirm" : String.format("§c§l✗ Min %.0f required", min),
                List.of(String.format("§7Bet: §e%.0f %s", currentBet, sym()))));
    }

    // ── Click handlers ─────────────────────────────────────────────────────

    public void handleMainClick(int slot) {
        switch (phase) {
            case BET     -> handleBetPhaseClick(slot);
            case PLAYING -> handlePlayingPhaseClick(slot);
            case RESULT  -> handleResultPhaseClick(slot);
        }
    }

    private void handleBetPhaseClick(int slot) {
        if (slot == S_SET_BET) {
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openBetSetting();
                transitioning = false;
            });
        } else if (slot == S_PLAY) {
            double min = plugin.getConfigLoader().getBlackjackMinBet();
            if (currentBet < min) {
                player.sendMessage(String.format("§cMinimum bet is §e%.0f!", min));
                return;
            }
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                startRound();
                transitioning = false;
            });
        }
    }

    private void handlePlayingPhaseClick(int slot) {
        switch (slot) {
            case S_HIT         -> playerHit();
            case S_STAND       -> playerStand();
            case S_DOUBLE_DOWN -> {
                if (playerHand.size() == 2) playerDouble();
            }
        }
    }

    private void handleResultPhaseClick(int slot) {
        switch (slot) {
            case S_PLAY_AGAIN -> {
                transitioning = true;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    startRound();
                    transitioning = false;
                });
            }
            case S_CHANGE_BET -> {
                phase = Phase.BET;
                buildMain();
                openScheduled(mainInv);
            }
            case S_EXIT -> plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
        }
    }

    public void handleBetClick(int slot) {
        if (slot >= B_CHIP_START && slot < B_CHIP_START + CHIP_VALUES.length) {
            double add = CHIP_VALUES[slot - B_CHIP_START];
            currentBet = Math.min(currentBet + add, plugin.getConfigLoader().getBlackjackMaxBet());
            refreshBetScreen();
        } else if (slot == B_CLEAR) {
            currentBet = 0;
            refreshBetScreen();
        } else if (slot == B_CONFIRM) {
            if (currentBet >= plugin.getConfigLoader().getBlackjackMinBet()) {
                phase = Phase.BET;
                buildMain();
                openScheduled(mainInv);
            }
        }
    }

    // ── Round management ───────────────────────────────────────────────────

    private void startRound() {
        betAmount = currentBet;
        if (!chargeBet()) {
            currentBet = 0;
            buildMain();
            player.openInventory(mainInv); // safe: inside scheduled task
            return;
        }

        playerHand.clear();
        dealerHand.clear();
        dealerHidden = true;
        doubled      = false;
        lastResult   = Result.NONE;

        playerHand.add(drawCard());
        dealerHand.add(drawCard()); // face-down
        playerHand.add(drawCard());
        dealerHand.add(drawCard()); // face-up

        boolean playerBJ = isBlackjack(playerHand);
        boolean dealerBJ = isBlackjack(dealerHand);

        if (playerBJ || dealerBJ) {
            dealerHidden = false;
            lastResult = (playerBJ && dealerBJ) ? Result.PUSH
                       : playerBJ               ? Result.BLACKJACK
                                                : Result.LOSE;
            processPayout();
            phase = Phase.RESULT;
            buildMain();
            player.openInventory(mainInv); // safe: inside scheduled task
            return;
        }

        phase = Phase.PLAYING;
        buildMain();
        player.openInventory(mainInv); // safe: inside scheduled task
        player.sendMessage(String.format("§2§lBlackjack! §7Your hand: §e%d  §7Dealer shows: §e%s",
                handValue(playerHand), cardName(dealerHand.get(1))));
    }

    private void playerHit() {
        playerHand.add(drawCard());
        int pv = handValue(playerHand);
        if (pv > 21) {
            // Bust
            dealerHidden = false;
            lastResult = Result.LOSE;
            processPayout();
            phase = Phase.RESULT;
            buildMain();
            openScheduled(mainInv);
        } else {
            // Update cards in-place — no inventory reopen, no flicker
            refreshMain();
            if (pv == 21) {
                // Auto-stand on 21
                playerStand();
            }
        }
    }

    private void playerStand() {
        runDealerTurn();
    }

    private void playerDouble() {
        if (!eco().isEnabled() || eco().getBalance(player) >= betAmount) {
            eco().withdraw(player, betAmount);
            betAmount *= 2;
            doubled = true;
        } else {
            player.sendMessage("§cInsufficient balance to double down.");
            return;
        }
        playerHand.add(drawCard());
        if (handValue(playerHand) > 21) {
            dealerHidden = false;
            lastResult = Result.LOSE;
            processPayout();
            phase = Phase.RESULT;
            buildMain();
            openScheduled(mainInv);
        } else {
            runDealerTurn(); // auto-stand after double
        }
    }

    private void runDealerTurn() {
        dealerHidden = false;
        while (handValue(dealerHand) < 17) {
            dealerHand.add(drawCard());
        }
        evaluateResult();
    }

    private void evaluateResult() {
        int pv = handValue(playerHand);
        int dv = handValue(dealerHand);
        boolean playerBust = pv > 21;
        boolean dealerBust = dv > 21;

        if (playerBust) {
            lastResult = Result.LOSE;
        } else if (dealerBust || pv > dv) {
            lastResult = Result.WIN;
        } else if (pv < dv) {
            lastResult = Result.LOSE;
        } else {
            lastResult = Result.PUSH;
        }

        processPayout();
        phase = Phase.RESULT;
        buildMain();
        openScheduled(mainInv);
    }

    private void processPayout() {
        switch (lastResult) {
            case BLACKJACK -> onWin(2.5);
            case WIN       -> onWin(2.0);
            case PUSH      -> onDraw();
            case LOSE      -> onLoss();
        }
    }

    private void onDraw() {
        plugin.getEconomyManager().deposit(player, betAmount);
        player.sendMessage("§7§lPUSH! Bet returned.");
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
        player.sendMessage(String.format("§c§lLose! §cLost §e%.0f %s§c.", betAmount, sym()));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    @Override
    public void cleanup() {
        if (state == GameState.FINISHED) return;
        state = GameState.FINISHED;
        stopTickTask();
        String open = player.getOpenInventory().getTitle();
        if (TITLE.equals(open) || BET_TITLE.equals(open)) player.closeInventory();
        plugin.getGameManager().removeBlackjackGame(player);
    }

    // ── Scheduled inventory helper (next-tick, outside click-event) ────────

    private void openScheduled(Inventory inv) {
        transitioning = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.openInventory(inv);
            transitioning = false;
        });
    }

    // ── Card logic ─────────────────────────────────────────────────────────

    private int drawCard() { return new Random().nextInt(13) + 1; } // 1=A, 11=J, 12=Q, 13=K

    private int cardValue(int card) {
        if (card == 1) return 11;
        if (card >= 11) return 10;
        return card;
    }

    private int handValue(List<Integer> hand) {
        int val = 0, aces = 0;
        for (int c : hand) {
            if (c == 1) { aces++; val += 11; } else if (c >= 11) val += 10; else val += c;
        }
        while (val > 21 && aces > 0) { val -= 10; aces--; }
        return val;
    }

    private boolean isBlackjack(List<Integer> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    private String cardName(int v) {
        return switch (v) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K";
                             default -> String.valueOf(v); };
    }

    // ── Item factories ─────────────────────────────────────────────────────

    private ItemStack makeCard(int v) {
        ItemStack item = new ItemStack(cardMaterial(v));
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§f§l" + cardName(v));
        meta.setLore(List.of("§7Value: §e" + cardValue(v)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeHidden() {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§b§l?");
        meta.setLore(List.of("§7Face-down card"));
        item.setItemMeta(meta);
        return item;
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

    private Material cardMaterial(int v) {
        return switch (v) {
            case 1  -> Material.DIAMOND;          case 2  -> Material.LAPIS_LAZULI;
            case 3  -> Material.EMERALD;           case 4  -> Material.REDSTONE;
            case 5  -> Material.GOLD_NUGGET;       case 6  -> Material.IRON_NUGGET;
            case 7  -> Material.QUARTZ;            case 8  -> Material.PRISMARINE_CRYSTALS;
            case 9  -> Material.AMETHYST_SHARD;    case 10 -> Material.COPPER_INGOT;
            case 11 -> Material.IRON_INGOT;        case 12 -> Material.GOLD_INGOT;
            case 13 -> Material.NETHERITE_SCRAP;   default -> Material.PAPER;
        };
    }

    private String sym()     { return plugin.getConfigLoader().getCurrencySymbol(); }
    private EconomyManager eco() { return plugin.getEconomyManager(); }

    @Override public void onTick() { }
}
