package com.himacasino.games.blackjack;

import com.himacasino.HimaCasino;
import com.himacasino.core.EconomyManager;
import com.himacasino.core.GameBase;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Blackjack — 54-slot inventory UI (6 rows). The felt/wood table background is a
 * single rounded-corner image baked into the inventory title via a custom font
 * glyph (see resource-pack {@code assets/himacasino/font/default.json} and
 * {@code textures/font/blackjack_panel.png}); cards and action buttons are real
 * PAPER items with CustomModelData sitting on top of it.
 *
 * ── Layout (all phases share the same 54-slot frame) ──────────────────────────
 *   Row 0 (0-8):    (panel background: wood title strip)
 *   Row 1 (9-17):   [DEALER BADGE]  [   DEALER CARDS 10-16   ]
 *   Row 2 (18-26):                  [STATUS/RESULT, slot 22]
 *   Row 3 (27-35):  [   PLAYER CARDS 28-34   ]  [PLAYER BADGE]
 *   Row 4 (36-44):                  [PLAYER INFO, slot 40]
 *   Row 5 (45-53):  [ACTION 47][ACTION 48][ACTION 49]  (panel: wood trim)
 *
 * Action slots 47/48/49 are reused across phases (BET: Set Bet / — / Deal,
 * PLAYING: Hit / Stand / Double Down, RESULT: Play Again / Change Bet / Exit).
 *
 * The GUI is identified via {@link MainHolder}/{@link BetHolder} rather than by
 * comparing title strings, since the title is now a Component carrying custom
 * font glyphs (not a stable, comparable legacy string).
 */
public class BlackjackGame extends GameBase {

    private static final String TITLE_LABEL     = "§2§lBLACK JACK";
    private static final String BET_TITLE_LABEL = "§2BJ Bet Setting";

    /** Marker holder identifying the main 54-slot table GUI (see {@link BlackjackListener}). */
    public static final class MainHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Marker holder identifying the 27-slot bet-setting GUI. */
    public static final class BetHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    // ── Custom-font panel background (resource-pack: font/default.json) ────
    // Codepoints must match resource-pack/assets/himacasino/font/default.json.
    private static final Key PANEL_FONT = Key.key("himacasino", "default");
    private static final String GLYPH_SPACE_LEFT8 = "\uF801"; // -8px: title margin (x=8) -> x=0
    private static final String GLYPH_SPACE_BACK  = "\uF802"; // -(176-8)px: panel right edge -> x=8
    private static final String GLYPH_PANEL       = "\uF803"; // 176x222 felt/wood table image

    private static Component buildTitle(String legacyLabel) {
        Component panel = Component.text(GLYPH_SPACE_LEFT8 + GLYPH_PANEL + GLYPH_SPACE_BACK).font(PANEL_FONT);
        return panel.append(LegacyComponentSerializer.legacySection().deserialize(legacyLabel));
    }

    private static final int GUI_SIZE = 54;

    // ── Layout slots ───────────────────────────────────────────────────────
    private static final int[] DEALER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] PLAYER_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int S_DEALER_BADGE = 9;
    private static final int S_PLAYER_BADGE = 40;
    private static final int S_STATUS       = 22;

    private static final int S_ACTION_LEFT   = 47; // Hit / Set Bet / Play Again
    private static final int S_ACTION_MIDDLE = 48; // Stand / — / Change Bet
    private static final int S_ACTION_RIGHT  = 49; // Double Down / Deal / Exit

    // CustomModelData for action button icons (PAPER-based, see resource pack).
    private static final int CMD_ACTION_HIT    = 100;
    private static final int CMD_ACTION_STAND  = 101;
    private static final int CMD_ACTION_DOUBLE = 102;

    // Bet-setting screen (separate 27-slot inventory, identified by BetHolder)
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

    private final Deck deck = new Deck();
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();
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
        MainHolder holder = new MainHolder();
        mainInv = plugin.getServer().createInventory(holder, GUI_SIZE, buildTitle(TITLE_LABEL));
        holder.inventory = mainInv;
        populateMain();
    }

    /** Updates items in the already-open mainInv without reopening (no flicker). */
    private void refreshMain() {
        if (mainInv == null) return;
        populateMain();
    }

    private void populateMain() {
        // Background (felt + wood frame) is the custom-font panel glyph baked into
        // the title; clear all slots so only the panel shows through where unused.
        for (int i = 0; i < GUI_SIZE; i++) mainInv.setItem(i, null);

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

        mainInv.setItem(S_STATUS, makeItem(Material.PAPER, "§2§lBLACK JACK",
                List.of(betStr, "§7Set your bet, then Deal")));

        mainInv.setItem(S_ACTION_LEFT, makeItem(Material.GOLD_INGOT, "§e§l⚙ Set Bet",
                List.of(betStr, "§7Click to open Bet Setting screen")));

        boolean canPlay = currentBet >= min
                && (!eco().isEnabled() || eco().getBalance(player) >= currentBet);
        mainInv.setItem(S_ACTION_RIGHT, canPlay
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ DEAL!", List.of(betStr))
                : makeItem(Material.RED_CONCRETE, "§c§l✗ DEAL",
                    List.of(currentBet < min
                            ? String.format("§cMin bet: §e%.0f", min)
                            : "§cInsufficient balance")));
    }

    private void populatePlaying() {
        int pv = handValue(playerHand);

        for (int i = 0; i < dealerHand.size() && i < DEALER_SLOTS.length; i++) {
            mainInv.setItem(DEALER_SLOTS[i],
                    (i == 0 && dealerHidden) ? makeCardBack() : makeCard(dealerHand.get(i)));
        }
        mainInv.setItem(S_DEALER_BADGE, makeItem(Material.RED_CONCRETE,
                "§cDealer: §7?",
                List.of("§7One card is hidden", "§8Face-up: §e" + dealerHand.get(1).shortName()
                        + " §7(" + cardValue(dealerHand.get(1)) + ")")));

        for (int i = 0; i < playerHand.size() && i < PLAYER_SLOTS.length; i++) {
            mainInv.setItem(PLAYER_SLOTS[i], makeCard(playerHand.get(i)));
        }
        mainInv.setItem(S_PLAYER_BADGE, makeItem(Material.LIME_CONCRETE,
                "§aYou: §e§l" + pv,
                List.of(String.format("§7Bet: §e%.0f %s", betAmount, sym()),
                        pv >= 18 ? "§7Be careful!" : "§7Your turn")));

        mainInv.setItem(S_STATUS, makeItem(Material.PAPER, "§f§lYour Turn",
                List.of("§7Choose an action below")));

        mainInv.setItem(S_ACTION_LEFT, makeActionButton(CMD_ACTION_HIT, "§a§lHIT",
                List.of("§7Draw one more card")));
        mainInv.setItem(S_ACTION_MIDDLE, makeActionButton(CMD_ACTION_STAND, "§e§lSTAND",
                List.of("§7End your turn, let dealer draw")));

        boolean canDouble = playerHand.size() == 2
                && (!eco().isEnabled() || eco().getBalance(player) >= betAmount);
        mainInv.setItem(S_ACTION_RIGHT, canDouble
                ? makeActionButton(CMD_ACTION_DOUBLE, "§6§lDOUBLE DOWN",
                    List.of(String.format("§7Bet: §e%.0f → §6§l%.0f", betAmount, betAmount * 2),
                            "§7Draw 1 card, then stand"))
                : makeItem(Material.GRAY_STAINED_GLASS_PANE, "§8§lDOUBLE DOWN",
                    List.of(playerHand.size() != 2 ? "§cOnly on first 2 cards"
                                                   : "§cInsufficient balance")));
    }

    private void populateResult() {
        int pv = handValue(playerHand);
        int dv = handValue(dealerHand);

        for (int i = 0; i < dealerHand.size() && i < DEALER_SLOTS.length; i++) {
            mainInv.setItem(DEALER_SLOTS[i], makeCard(dealerHand.get(i)));
        }
        mainInv.setItem(S_DEALER_BADGE, makeItem(
                dv > 21 ? Material.BARRIER : Material.RED_CONCRETE,
                "§cDealer: §e" + dv,
                List.of(dv > 21 ? "§c§lBUST!" : "§7Final total")));

        for (int i = 0; i < playerHand.size() && i < PLAYER_SLOTS.length; i++) {
            mainInv.setItem(PLAYER_SLOTS[i], makeCard(playerHand.get(i)));
        }
        mainInv.setItem(S_PLAYER_BADGE, makeItem(Material.LIME_CONCRETE,
                "§aYou: §e§l" + pv, List.of("§7Final total")));

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
        mainInv.setItem(S_STATUS, makeItem(rMat, rName, rLore));

        mainInv.setItem(S_ACTION_LEFT, makeItem(Material.LIME_CONCRETE, "§a§l▶ Play Again",
                List.of(String.format("§7Bet: §e%.0f %s", currentBet, sym()))));
        mainInv.setItem(S_ACTION_MIDDLE, makeItem(Material.GOLD_INGOT, "§e§l⚙ Change Bet",
                List.of("§7Set a new bet amount")));
        mainInv.setItem(S_ACTION_RIGHT, makeItem(Material.RED_CONCRETE, "§c§l✗ Exit",
                List.of("§7Close the game")));
    }

    // ── Bet-setting screen ─────────────────────────────────────────────────

    public void openBetSetting() {
        BetHolder holder = new BetHolder();
        betInv = plugin.getServer().createInventory(holder, 27,
                LegacyComponentSerializer.legacySection().deserialize(BET_TITLE_LABEL));
        holder.inventory = betInv;
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
        if (slot == S_ACTION_LEFT) {
            transitioning = true;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                openBetSetting();
                transitioning = false;
            });
        } else if (slot == S_ACTION_RIGHT) {
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
            case S_ACTION_LEFT   -> playerHit();
            case S_ACTION_MIDDLE -> playerStand();
            case S_ACTION_RIGHT  -> {
                if (playerHand.size() == 2) playerDouble();
            }
        }
    }

    private void handleResultPhaseClick(int slot) {
        switch (slot) {
            case S_ACTION_LEFT -> {
                transitioning = true;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    startRound();
                    transitioning = false;
                });
            }
            case S_ACTION_MIDDLE -> {
                phase = Phase.BET;
                buildMain();
                openScheduled(mainInv);
            }
            case S_ACTION_RIGHT -> plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
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
        lastResult   = Result.NONE;

        playerHand.add(deck.draw());
        dealerHand.add(deck.draw()); // face-down
        playerHand.add(deck.draw());
        dealerHand.add(deck.draw()); // face-up

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
                handValue(playerHand), dealerHand.get(1).shortName()));
    }

    private void playerHit() {
        playerHand.add(deck.draw());
        int pv = handValue(playerHand);
        if (pv > 21) {
            dealerHidden = false;
            lastResult = Result.LOSE;
            processPayout();
            phase = Phase.RESULT;
            buildMain();
            openScheduled(mainInv);
        } else {
            refreshMain();
            if (pv == 21) {
                playerStand(); // auto-stand on 21
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
        } else {
            player.sendMessage("§cInsufficient balance to double down.");
            return;
        }
        playerHand.add(deck.draw());
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
            dealerHand.add(deck.draw());
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
        InventoryHolder open = player.getOpenInventory().getTopInventory().getHolder();
        if (open instanceof MainHolder || open instanceof BetHolder) player.closeInventory();
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

    // ── Card / hand logic ───────────────────────────────────────────────────

    private int cardValue(Card card) { return card.rank().baseValue(); }

    private int handValue(List<Card> hand) {
        int val = 0, aces = 0;
        for (Card c : hand) {
            if (c.rank() == Card.Rank.ACE) aces++;
            val += cardValue(c);
        }
        while (val > 21 && aces > 0) { val -= 10; aces--; }
        return val;
    }

    private boolean isBlackjack(List<Card> hand) {
        return hand.size() == 2 && handValue(hand) == 21;
    }

    // ── Item factories ─────────────────────────────────────────────────────

    private ItemStack makeCard(Card card) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        meta.setCustomModelData(card.customModelData());
        meta.setDisplayName("§f§l" + card.shortName());
        meta.setLore(List.of("§7" + card.suit().displayName(), "§7Value: §e" + cardValue(card)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeCardBack() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        meta.setCustomModelData(Card.BACK_CUSTOM_MODEL_DATA);
        meta.setDisplayName("§b§l?");
        meta.setLore(List.of("§7Face-down card"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeActionButton(int customModelData, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta  = item.getItemMeta();
        meta.setCustomModelData(customModelData);
        meta.setDisplayName(name);
        meta.setLore(lore);
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

    private String sym()     { return plugin.getConfigLoader().getCurrencySymbol(); }
    private EconomyManager eco() { return plugin.getEconomyManager(); }

    @Override public void onTick() { }
}
