package com.himacasino.games.horsewheel;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Horse Wheel game session — manages the betting UI for one player
 * while delegating all visual spin animation to {@link HorseWheelTableDisplay}.
 *
 * Bet UI layout (27 slots, 3 rows):
 *   Row 0:  bg  W  Y  B  [TOTAL]  G  R  GOLD  bg
 *   Row 1: +10 +50 +100 +500 +1000  bg  bg  CLEAR  bg
 *   Row 2: EXIT  bg  bg  bg  SPIN  bg  bg  bg  bg
 */
public class HorseWheelGame extends GameBase {

    public static final String BET_TITLE = "§d§lHORSE WHEEL §6ベット";

    private static final int[]      WHEEL_ORDER  = HorseWheelTableDisplay.WHEEL_ORDER;
    private static final double[]   MULTIPLIERS  = HorseWheelTableDisplay.MULTIPLIERS;
    private static final String[]   HORSE_NAMES  = HorseWheelTableDisplay.HORSE_NAMES;
    private static final Material[] HORSE_MATS   = HorseWheelTableDisplay.HORSE_MATS;
    private static final String[]   HORSE_COLORS = HorseWheelTableDisplay.HORSE_COLORS;

    private static final int[]    HORSE_SLOTS  = {1, 2, 3, 5, 6, 7};
    private static final int      S_BET_INFO   = 4;
    private static final int      B_CHIP_START = 9;
    private static final int      B_CLEAR      = 17;
    private static final int      S_EXIT       = 18;
    private static final int      S_SPIN       = 22;
    private static final double[] CHIP_VALUES  = {10, 50, 100, 500, 1000};

    private final HorseWheelTableDisplay tableDisplay;
    private final Location               machineLoc;

    private final double[] bets          = new double[6];
    private int            selectedHorse = 0;
    private boolean        transitioning = false;

    private Inventory betInv;

    public HorseWheelGame(HimaCasino plugin, Player player,
                          HorseWheelTableDisplay tableDisplay, Location machineLoc) {
        super(plugin, player, 0);
        this.tableDisplay = tableDisplay;
        this.machineLoc   = machineLoc;
    }

    @Override
    public void onStart() {
        state = GameState.RUNNING;
    }

    public boolean isTransitioning() { return transitioning; }

    // ── Bet UI ─────────────────────────────────────────────────────────────

    public void openBetUI() {
        betInv = plugin.getServer().createInventory(null, 27, BET_TITLE);
        refreshBetUI();
        player.openInventory(betInv);
    }

    private void refreshBetUI() {
        if (betInv == null) return;
        ItemStack bg = bg();
        for (int i = 0; i < 27; i++) betInv.setItem(i, bg);

        for (int i = 0; i < 6; i++) {
            boolean sel = (i == selectedHorse);
            List<String> lore = new ArrayList<>();
            lore.add(String.format("§7Multiplier: §e×%.0f", MULTIPLIERS[i]));
            lore.add(String.format("§7Bet: §6§l%.0f %s", bets[i],
                    plugin.getConfigLoader().getCurrencySymbol()));
            if (sel) lore.add("§a§l◀ Selected");
            betInv.setItem(HORSE_SLOTS[i], makeItem(
                    sel ? Material.GLOWSTONE : HORSE_MATS[i],
                    HORSE_COLORS[i] + "§l" + HORSE_NAMES[i],
                    lore));
        }

        double total = totalBet();
        betInv.setItem(S_BET_INFO, makeItem(Material.GOLD_BLOCK,
                String.format("§eTOTAL: §6§l%.0f %s", total,
                        plugin.getConfigLoader().getCurrencySymbol()),
                List.of(
                    "§7Adding to: " + HORSE_COLORS[selectedHorse] + "§l" + HORSE_NAMES[selectedHorse],
                    String.format("§7Balance: §e%.0f",
                            plugin.getEconomyManager().isEnabled()
                            ? plugin.getEconomyManager().getBalance(player) : 0.0)
                )));

        Material[] chipMats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            betInv.setItem(B_CHIP_START + i, makeItem(chipMats[i],
                    "§a§l+" + (int) CHIP_VALUES[i],
                    List.of("§7→ " + HORSE_COLORS[selectedHorse] + HORSE_NAMES[selectedHorse])));
        }
        betInv.setItem(B_CLEAR, makeItem(Material.BARRIER, "§c§lCLEAR",
                List.of("§7Clear selected horse bet")));
        betInv.setItem(S_EXIT, makeItem(Material.RED_CONCRETE, "§c§l✗ Exit",
                List.of("§7Leave game")));

        boolean canSpin = total > 0 && (!plugin.getEconomyManager().isEnabled()
                || plugin.getEconomyManager().getBalance(player) >= total);
        betInv.setItem(S_SPIN, canSpin
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ SPIN!",
                    List.of(String.format("§7Total: §e%.0f %s", total,
                            plugin.getConfigLoader().getCurrencySymbol())))
                : makeItem(Material.RED_CONCRETE, "§c§l✗ SPIN",
                    List.of(total <= 0 ? "§cPlace a bet first" : "§cInsufficient balance")));
    }

    private double totalBet() {
        double s = 0;
        for (double b : bets) s += b;
        return s;
    }

    // ── Click handler ──────────────────────────────────────────────────────

    public void handleClick(int slot) {
        if (state == GameState.FINISHED) return;

        for (int i = 0; i < HORSE_SLOTS.length; i++) {
            if (slot == HORSE_SLOTS[i]) { selectedHorse = i; refreshBetUI(); return; }
        }

        if (slot >= B_CHIP_START && slot < B_CHIP_START + CHIP_VALUES.length) {
            double max = plugin.getConfigLoader().getHighLowMaxBet();
            bets[selectedHorse] = Math.min(
                    bets[selectedHorse] + CHIP_VALUES[slot - B_CHIP_START], max);
            refreshBetUI();
            return;
        }

        switch (slot) {
            case B_CLEAR -> { bets[selectedHorse] = 0; refreshBetUI(); }
            case S_EXIT  -> plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
            case S_SPIN  -> { double t = totalBet(); if (t > 0) startSpin(t); }
        }
    }

    // ── Spin ───────────────────────────────────────────────────────────────

    private void startSpin(double total) {
        betAmount = total;
        if (!chargeBet()) return;

        // Close bet UI (fires InventoryCloseEvent synchronously inside click handler;
        // transitioning=true prevents the close handler from calling cleanup)
        transitioning = true;
        player.closeInventory();
        transitioning = false;

        final int targetSeg  = new Random().nextInt(24);
        final int resultType = WHEEL_ORDER[targetSeg];
        // Snapshot bet on winning horse before bets[] is reset after spin
        final double winnerBet = bets[resultType];

        player.sendMessage("§d§l══ HORSE WHEEL ══ §d§l▶ SPIN!");

        tableDisplay.startSpin(targetSeg, () -> {
            if (isFinished()) return;

            double winnings = winnerBet > 0 ? winnerBet * MULTIPLIERS[resultType] : 0;

            player.sendMessage("§d§l══ HORSE WHEEL RESULT ══");
            player.sendMessage("§7Result: " + HORSE_COLORS[resultType] + "§l" + HORSE_NAMES[resultType]
                    + String.format(" §7(×%.0f)", MULTIPLIERS[resultType]));

            if (winnings > 0) {
                plugin.getEconomyManager().deposit(player, winnings);
                player.sendMessage(String.format("§a§l★ WIN! §a+%.0f %s",
                        winnings, plugin.getConfigLoader().getCurrencySymbol()));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        player.getLocation().add(0, 1, 0), 30, 0.8, 0.5, 0.8, 0);
            } else {
                player.sendMessage(String.format("§c§l✗ LOSE! §c-%.0f %s",
                        betAmount, plugin.getConfigLoader().getCurrencySymbol()));
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            }
            player.sendMessage("§d§l═════════════════════════");

            // Reopen bet UI after wheel stops (4 s)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (isFinished()) return;
                Arrays.fill(bets, 0);
                plugin.getMachineManager().release(machineLoc);
                openBetUI();
            }, 80L);
        });
    }

    // ── GameBase overrides ─────────────────────────────────────────────────

    @Override public void onTick()        { }
    @Override public void onWin(double m) { }
    @Override public void onLoss()        { }

    @Override
    public void cleanup() {
        if (state == GameState.FINISHED) return;
        state = GameState.FINISHED;
        stopTickTask();
        String open = player.getOpenInventory().getTitle();
        if (BET_TITLE.equals(open)) player.closeInventory();
        plugin.getMachineManager().release(machineLoc);
        plugin.getGameManager().removeHorseWheelGame(player);
    }

    // ── Item helpers ───────────────────────────────────────────────────────

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
}
