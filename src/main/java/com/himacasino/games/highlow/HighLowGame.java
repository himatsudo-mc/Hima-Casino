package com.himacasino.games.highlow;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * HIGH & LOW card game.
 *
 * ── Inventory layouts (3 rows = 27 slots) ──────────────────────────────────
 *
 * BET_SETTING screen  (title = BET_TITLE)
 *   Row 0:  bg  bg  bg  bg  [CURRENT BET]  bg  bg  bg  bg
 *   Row 1: +10 +50 +100 +500 +1k  bg  bg  CLEAR  bg
 *   Row 2:  bg  bg  bg  bg  [CONFIRM]  bg  bg  bg  bg
 *
 * MAIN screen  (title = TITLE)
 *   BET phase — middle column (col 4 = slots 4,13,22):
 *     Row 1: [SET BET ⚙]  at slot 13
 *     Row 2: [▶ PLAY!]    at slot 22
 *
 *   PLAYING phase:
 *     Row 0: [DEALER]  at slot 4
 *     Row 1: [CARD L] [VS] [CARD R]  at slots 10,13,16
 *     Row 2: [BET INFO]  at slot 22
 *
 *   RESULT phase:
 *     Row 0: [DEALER]  at slot 4
 *     Row 1: [CHOSEN] [RESULT] [OTHER]  at slots 10,13,16
 *     Row 2: [PLAY AGAIN] [CHANGE BET] [EXIT]  at slots 20,24,26
 */
public class HighLowGame extends GameBase {

    public static final String TITLE     = "§6HIGH & LOW";
    public static final String BET_TITLE = "§6Bet Setting";

    private static final int CARD_MIN = 1, CARD_MAX = 13;

    // ── Slot indices (main screen) ─────────────────────────────────────────
    private static final int S_DEALER     = 4;
    private static final int S_CARD_L     = 10;
    private static final int S_VS         = 13;
    private static final int S_CARD_R     = 16;
    private static final int S_BET_INFO   = 22;
    // BET phase – both in middle column
    private static final int S_SET_BET    = 13;
    private static final int S_PLAY       = 22;
    // RESULT phase
    private static final int S_RESULT     = 13;
    private static final int S_PLAY_AGAIN = 20;
    private static final int S_CHANGE_BET = 24;
    private static final int S_EXIT       = 26;

    // ── Slot indices (bet-setting screen) ─────────────────────────────────
    private static final int B_CURRENT    = 4;
    private static final int B_CHIP_START = 9;
    private static final int B_CLEAR      = 17;
    private static final int B_CONFIRM    = 22;

    private static final double[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    private enum Phase { BET, PLAYING, RESULT }

    // ── State ──────────────────────────────────────────────────────────────
    private double  currentBet    = 0;
    private Phase   phase         = Phase.BET;
    private boolean transitioning = false; // true while scheduling an inventory open
    private boolean resultPending = false; // true during 20-tick payout delay

    private int   dealerCard;
    private int[] hiddenCards = new int[2];
    private int   chosenIndex = -1;

    private Inventory mainInv;
    private Inventory betInv;

    public HighLowGame(HimaCasino plugin, Player player) {
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

    // ── Public API for listener ────────────────────────────────────────────

    public boolean isTransitioning() { return transitioning; }

    /** Called by listener when player ESC's from the bet-setting screen. */
    public void returnToMain() {
        if (state == GameState.FINISHED) return;
        phase = Phase.BET;
        buildMain();
        openScheduled(mainInv);
    }

    // ── Inventory builders ─────────────────────────────────────────────────

    private void buildMain() {
        mainInv = plugin.getServer().createInventory(null, 27, TITLE);
        ItemStack bg = bg();
        for (int i = 0; i < 27; i++) mainInv.setItem(i, bg);
        switch (phase) {
            case BET     -> buildMain_Bet();
            case PLAYING -> buildMain_Playing();
            case RESULT  -> buildMain_Result();
        }
    }

    private void buildMain_Bet() {
        double min    = plugin.getConfigLoader().getHighLowMinBet();
        String betStr = currentBet > 0
                ? String.format("§eBet: §6§l%.0f %s", currentBet, plugin.getConfigLoader().getCurrencySymbol())
                : "§7Bet: §enot set";

        mainInv.setItem(S_SET_BET, makeItem(Material.GOLD_INGOT,
                "§e§l⚙ Set Bet",
                List.of(betStr, "§7Click to open Bet Setting screen")));

        boolean canPlay = currentBet >= min
                && (!plugin.getEconomyManager().isEnabled()
                    || plugin.getEconomyManager().getBalance(player) >= currentBet);

        mainInv.setItem(S_PLAY, canPlay
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ PLAY!", List.of(betStr))
                : makeItem(Material.RED_CONCRETE,  "§c§l✗ PLAY",
                    List.of(currentBet < min ? String.format("§cMin bet: %.0f", min) : "§cInsufficient balance", betStr)));
    }

    private void buildMain_Playing() {
        mainInv.setItem(S_DEALER,   makeDealerCard());
        mainInv.setItem(S_CARD_L,   makeHiddenCard());
        mainInv.setItem(S_VS,       makeItem(Material.IRON_BARS, "§7§l─ VS ─", null));
        mainInv.setItem(S_CARD_R,   makeHiddenCard());
        mainInv.setItem(S_BET_INFO, makeItem(Material.PAPER,
                String.format("§7Bet: §e%.0f %s", betAmount, plugin.getConfigLoader().getCurrencySymbol()),
                List.of("§7Choose a card — higher than dealer wins!")));
    }

    private void buildMain_Result() {
        mainInv.setItem(S_DEALER, makeDealerCard());
        int chosen = hiddenCards[chosenIndex];
        mainInv.setItem(S_CARD_L, makeRevealedCard(chosen, true));
        mainInv.setItem(S_CARD_R, makeRevealedCard(hiddenCards[1 - chosenIndex], false));

        boolean win  = chosen > dealerCard;
        boolean draw = chosen == dealerCard;
        List<String> rlore = new ArrayList<>();
        if (win)       rlore.add(String.format("§a+%.0f %s",
                betAmount * plugin.getConfigLoader().getHighLowWinMultiplier(),
                plugin.getConfigLoader().getCurrencySymbol()));
        else if (draw) rlore.add("§7Bet returned");
        else           rlore.add(String.format("§c-%.0f %s", betAmount,
                plugin.getConfigLoader().getCurrencySymbol()));
        if (resultPending) rlore.add("§8...");

        mainInv.setItem(S_RESULT, makeItem(
                win ? Material.GOLD_INGOT : (draw ? Material.PAPER : Material.BARRIER),
                win ? "§a§l★ WIN!"       : (draw ? "§7§lDRAW"     : "§c§l✗ LOSE"),
                rlore));

        if (!resultPending) {
            mainInv.setItem(S_PLAY_AGAIN, makeItem(Material.LIME_CONCRETE, "§a§l▶ Play Again",
                    List.of(String.format("§7Bet: §e%.0f %s", betAmount,
                            plugin.getConfigLoader().getCurrencySymbol()))));
            mainInv.setItem(S_CHANGE_BET, makeItem(Material.GOLD_INGOT,
                    "§e§l⚙ Change Bet", List.of("§7Set a new bet amount")));
            mainInv.setItem(S_EXIT, makeItem(Material.RED_CONCRETE,
                    "§c§l✗ Exit", List.of("§7Close the game")));
        }
    }

    // ── Bet setting screen ─────────────────────────────────────────────────

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
                String.format("§eCurrent Bet: §6§l%.0f %s",
                        currentBet, plugin.getConfigLoader().getCurrencySymbol()),
                List.of("§7Add chips below",
                        String.format("§7Max: §e%.0f", plugin.getConfigLoader().getHighLowMaxBet()))));

        Material[] mats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            betInv.setItem(B_CHIP_START + i, makeItem(mats[i],
                    "§a§l+" + (int) CHIP_VALUES[i],
                    List.of("§7Click to add §e" + (int) CHIP_VALUES[i] + " §7to bet")));
        }
        betInv.setItem(B_CLEAR, makeItem(Material.BARRIER, "§c§lClear",
                List.of("§7Reset bet to 0")));

        double min = plugin.getConfigLoader().getHighLowMinBet();
        boolean ok = currentBet >= min;
        betInv.setItem(B_CONFIRM, makeItem(
                ok ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                ok ? "§a§l✓ Confirm" : String.format("§c§l✗ Min %.0f required", min),
                List.of(String.format("§7Bet: §e%.0f %s", currentBet,
                        plugin.getConfigLoader().getCurrencySymbol()))));
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
            // Schedule to avoid opening inventory inside InventoryClickEvent
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openBetSetting(); // player.openInventory fires close event; transitioning=true guards it
                transitioning = false;
            });
        } else if (slot == S_PLAY) {
            double min = plugin.getConfigLoader().getHighLowMinBet();
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
        if (slot != S_CARD_L && slot != S_CARD_R) return;
        chosenIndex = (slot == S_CARD_L) ? 0 : 1;
        revealCards();
    }

    private void handleResultPhaseClick(int slot) {
        if (resultPending) return; // wait for payout to process
        if (slot == S_PLAY_AGAIN) {
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                playAgain();
                transitioning = false;
            });
        } else if (slot == S_CHANGE_BET) {
            phase = Phase.BET;
            buildMain();
            openScheduled(mainInv);
        } else if (slot == S_EXIT) {
            plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
        }
    }

    public void handleBetClick(int slot) {
        if (slot >= B_CHIP_START && slot < B_CHIP_START + CHIP_VALUES.length) {
            double add = CHIP_VALUES[slot - B_CHIP_START];
            currentBet = Math.min(currentBet + add, plugin.getConfigLoader().getHighLowMaxBet());
            refreshBetScreen();
        } else if (slot == B_CLEAR) {
            currentBet = 0;
            refreshBetScreen();
        } else if (slot == B_CONFIRM) {
            if (currentBet >= plugin.getConfigLoader().getHighLowMinBet()) {
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
            player.openInventory(mainInv);
            return;
        }
        Random rng     = new Random();
        dealerCard     = rng.nextInt(CARD_MAX) + CARD_MIN;
        hiddenCards[0] = rng.nextInt(CARD_MAX) + CARD_MIN;
        hiddenCards[1] = rng.nextInt(CARD_MAX) + CARD_MIN;
        chosenIndex    = -1;
        phase = Phase.PLAYING;
        buildMain();
        player.openInventory(mainInv); // inside scheduled task: close event fires, transitioning=true guards it
        player.sendMessage(String.format("§6Dealer: §f§l%s  §7(Bet: §e%.0f§7)",
                cardName(dealerCard), betAmount));
    }

    private void revealCards() {
        if (chosenIndex < 0) return;
        int chosen = hiddenCards[chosenIndex];

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.8f);
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0.1);
        player.sendMessage(String.format("§eYour card: §f§l%s", cardName(chosen)));

        phase = Phase.RESULT;
        resultPending = true;
        buildMain();            // builds result screen (no action buttons yet)
        openScheduled(mainInv); // shows result screen next tick

        // Process payout after brief delay for drama
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isFinished()) return;
            resultPending = false;
            if (chosen > dealerCard)      onWin(plugin.getConfigLoader().getHighLowWinMultiplier());
            else if (chosen < dealerCard) onLoss();
            else                          onDraw();
            // Refresh open inventory in-place to show action buttons
            if (!isFinished() && mainInv != null) buildMain_Result();
        }, 20L);
    }

    private void playAgain() {
        double balance = plugin.getEconomyManager().isEnabled()
                ? plugin.getEconomyManager().getBalance(player) : Double.MAX_VALUE;
        if (balance < betAmount) {
            player.sendMessage(String.format("§cInsufficient balance for §e%.0f %s§c!",
                    betAmount, plugin.getConfigLoader().getCurrencySymbol()));
            return;
        }
        currentBet = betAmount;
        startRound();
    }

    private void onDraw() {
        plugin.getEconomyManager().deposit(player, betAmount);
        player.sendMessage("§7§lDRAW! Bet returned.");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
    }

    @Override public void onTick() { /* event-driven */ }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 25, 0.8, 0.5, 0.8, 0);
        state = GameState.RUNNING; // allow replay
    }

    @Override
    public void onLoss() {
        state = GameState.RUNNING;
        player.sendMessage(String.format("§c§lDealer wins! Lost §e%.0f %s§c.",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    @Override
    public void cleanup() {
        if (state == GameState.FINISHED) return; // idempotent guard
        state = GameState.FINISHED;
        stopTickTask();
        String open = player.getOpenInventory().getTitle();
        if (TITLE.equals(open) || BET_TITLE.equals(open)) player.closeInventory();
        plugin.getGameManager().removeHighLowGame(player);
    }

    // ── Schedule inventory open (next tick, outside click-event context) ───

    private void openScheduled(Inventory inv) {
        transitioning = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.openInventory(inv); // close event fires HERE with transitioning=true
            transitioning = false;     // reset AFTER openInventory returns
        });
    }

    // ── Item factories ─────────────────────────────────────────────────────

    private ItemStack makeDealerCard() {
        ItemStack item = new ItemStack(cardMaterial(dealerCard));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§lDealer: §f§l" + cardName(dealerCard));
        meta.setLore(List.of("§7Value: §e" + dealerCard));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeHiddenCard() {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l?");
        meta.setLore(List.of("§7Click to select", "§7Higher than dealer = WIN"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeRevealedCard(int value, boolean chosen) {
        ItemStack item = new ItemStack(cardMaterial(value));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((chosen ? "§a§l" : "§7") + cardName(value));
        List<String> lore = new ArrayList<>();
        lore.add("§7Value: §e" + value);
        if (chosen) lore.add("§a§l◀ Your pick");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bg() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§0");
        item.setItemMeta(meta);
        return item;
    }

    private String cardName(int v) {
        return switch (v) {
            case 1  -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> String.valueOf(v);
        };
    }

    private Material cardMaterial(int v) {
        return switch (v) {
            case 1  -> Material.DIAMOND;
            case 2  -> Material.LAPIS_LAZULI;
            case 3  -> Material.EMERALD;
            case 4  -> Material.REDSTONE;
            case 5  -> Material.GOLD_NUGGET;
            case 6  -> Material.IRON_NUGGET;
            case 7  -> Material.QUARTZ;
            case 8  -> Material.PRISMARINE_CRYSTALS;
            case 9  -> Material.AMETHYST_SHARD;
            case 10 -> Material.COPPER_INGOT;
            case 11 -> Material.IRON_INGOT;
            case 12 -> Material.GOLD_INGOT;
            case 13 -> Material.NETHERITE_SCRAP;
            default -> Material.PAPER;
        };
    }
}
