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
 * Blackjack — 45-slot inventory UI (5 rows: dealer, gap, player, gap,
 * actions), sized to hug its content and match the reference table design.
 * The felt/wood table background is a single rounded-corner image baked into
 * the inventory title via a custom font glyph (see resource-pack
 * {@code assets/himacasino/font/default.json} and
 * {@code textures/font/blackjack_panel.png}); cards and action buttons are
 * real PAPER items with CustomModelData sitting on top of it. Dealer/player
 * totals and the bet/result are shown as plain colored text appended to the
 * same title, positioned right under the panel's wood title strip.
 *
 * ── Layout ──────────────────────────────────────────────────────────────────
 *   Row 0 (0-8):    [   DEALER CARDS, centered in 1-7   ]
 *   Row 1 (9-17):   (empty — breathing room between dealer/player rows)
 *   Row 2 (18-26):  [   PLAYER CARDS, centered in 19-25   ]
 *   Row 3 (27-35):  (empty — breathing room between player row/actions)
 *   Row 4 (36-44):    [ACTION 38]   [ACTION 40]   [ACTION 42]
 *
 * Dealer/player hands are rendered centered within their 7-slot range (see
 * {@link #placeHandCentered}) rather than left-packed, so a 2-card hand sits
 * in the middle of the row and naturally fills outward as more cards are
 * drawn. Action slots 38/40/42 are reused across phases (BET: Set Bet / — /
 * Deal, PLAYING: Hit / Stand / Double Down, RESULT: Play Again / Change Bet /
 * Exit).
 *
 * The gap rows above/below are not just cosmetic: cards/buttons render at
 * {@code display.gui.scale} slightly above 1.0 (1.15x / 1.1x), which is
 * already enough to overflow a bare 18px slot's height — a dealer row placed
 * flush against the title strip, or a player row flush against the button
 * row, visibly overlaps its neighbor. Keep scale increases and gap rows in
 * sync (see resource-pack card/button model JSON "display.gui.scale").
 *
 * Because the title carries the live Dealer/You/Bet text, every state change
 * needs a new title and therefore a full {@link #buildMain()} + reopen — Bukkit
 * cannot rename an already-open inventory in place, so there is a brief reopen
 * on every Hit (unlike a plain item-only refresh, this is unavoidable here).
 *
 * The GUI is identified via {@link MainHolder}/{@link BetHolder} rather than by
 * comparing title strings, since the title is now a Component carrying custom
 * font glyphs (not a stable, comparable legacy string).
 */
public class BlackjackGame extends GameBase {

    private static final String BET_TITLE_LABEL = "§2BJ Bet Setting";

    /** Marker holder identifying the main 45-slot table GUI (see {@link BlackjackListener}). */
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

    private static final int GUI_SIZE = 45;

    // ── Layout slots ───────────────────────────────────────────────────────
    // Rows 1 (9-17) and 3 (27-35) are left empty on purpose as breathing room
    // (see class javadoc — the enlarged card/button icons overflow a bare
    // 18px slot, so every occupied row needs a buffer on both sides).
    private static final int[] DEALER_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    private static final int[] PLAYER_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private static final int S_ACTION_LEFT   = 38; // Hit / Set Bet / Play Again
    private static final int S_ACTION_MIDDLE = 40; // Stand / — / Change Bet
    private static final int S_ACTION_RIGHT  = 42; // Double Down / Deal / Exit

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
        mainInv = plugin.getServer().createInventory(holder, GUI_SIZE, buildTitle(buildStatusText()));
        holder.inventory = mainInv;
        populateMain();
    }

    /** Live Dealer/You/Bet text shown right under the panel's title strip. */
    private String buildStatusText() {
        return switch (phase) {
            case BET -> String.format("§2§lBlackjack §7| §fBet: §e%.0f %s", currentBet, sym());
            case PLAYING -> {
                String dealerDisplay = dealerHidden ? "?" : String.valueOf(handValue(dealerHand));
                yield String.format("§2§lBlackjack §7| §fDealer: §e%s §7You: §e%d §7Bet: §e%.0f",
                        dealerDisplay, handValue(playerHand), betAmount);
            }
            case RESULT -> {
                String resultWord = switch (lastResult) {
                    case BLACKJACK -> "§6BLACKJACK!";
                    case WIN       -> "§aWIN!";
                    case PUSH      -> "§7PUSH";
                    default        -> "§cLOSE";
                };
                yield String.format("§2§lBlackjack §7| §fDealer: §e%d §7You: §e%d §7%s",
                        handValue(dealerHand), handValue(playerHand), resultWord);
            }
        };
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
        placeHandCentered(DEALER_SLOTS, dealerHand, true);
        placeHandCentered(PLAYER_SLOTS, playerHand, false);

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
        placeHandCentered(DEALER_SLOTS, dealerHand, true);
        placeHandCentered(PLAYER_SLOTS, playerHand, false);

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
        } else if (pv == 21) {
            playerStand(); // auto-stand on 21; this rebuilds+reopens itself
        } else {
            // The live "Dealer/You" totals live in the title, which Bukkit cannot
            // rename in place, so a Hit needs a full rebuild + reopen (not just a
            // same-inventory item refresh).
            buildMain();
            openScheduled(mainInv);
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

    /**
     * Renders {@code hand} centered within {@code slots} instead of left-packed, so a 2-card
     * hand sits in the middle of the row (matching the reference layout) and naturally spreads
     * to fill the row as more cards are drawn. When {@code dealer} is true, the first card is
     * drawn face-down while {@link #dealerHidden} is set.
     */
    private void placeHandCentered(int[] slots, List<Card> hand, boolean dealer) {
        int count = Math.min(hand.size(), slots.length);
        int start = (slots.length - count) / 2;
        for (int i = 0; i < count; i++) {
            boolean faceDown = dealer && i == 0 && dealerHidden;
            mainInv.setItem(slots[start + i], faceDown ? makeCardBack() : makeCard(hand.get(i)));
        }
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
