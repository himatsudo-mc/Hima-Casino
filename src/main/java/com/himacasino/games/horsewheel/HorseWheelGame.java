package com.himacasino.games.horsewheel;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class HorseWheelGame extends GameBase {

    public static final String BET_TITLE = "§d§lHORSE WHEEL §6ベット";

    // 24-segment wheel: 0=WHITE(×2,8segs), 1=YELLOW(×3,6), 2=BLUE(×5,4), 3=GREEN(×8,3), 4=RED(×10,2), 5=GOLD(×20,1)
    static final int[] WHEEL_ORDER = {5,0,1,3,0,2,1,4,0,1,2,0,3,1,0,4,0,1,2,3,0,1,2,0};
    private static final double[]   MULTIPLIERS  = {2.0, 3.0, 5.0, 8.0, 10.0, 20.0};
    private static final String[]   HORSE_NAMES  = {"WHITE", "YELLOW", "BLUE", "GREEN", "RED", "GOLD"};
    private static final Material[] HORSE_MATS   = {
        Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE, Material.CYAN_CONCRETE,
        Material.LIME_CONCRETE,  Material.RED_CONCRETE,    Material.GOLD_BLOCK
    };
    private static final String[] HORSE_COLORS = {"§f", "§e", "§b", "§a", "§c", "§6"};

    // Bet-UI slot layout (27 slots, 3 rows)
    //   Row 0: bg W  Y  B  [TOTAL] G  R  GOLD bg
    //   Row 1: +10 +50 +100 +500 +1000 bg bg CLEAR bg
    //   Row 2: EXIT bg bg bg SPIN bg bg bg bg
    private static final int[] HORSE_SLOTS  = {1, 2, 3, 5, 6, 7};
    private static final int   S_BET_INFO   = 4;
    private static final int   B_CHIP_START = 9;
    private static final int   B_CLEAR      = 17;
    private static final int   S_EXIT       = 18;
    private static final int   S_SPIN       = 22;
    private static final double[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    // ── State ──────────────────────────────────────────────────────────────
    private final double[] bets          = new double[6];
    private int            selectedHorse = 0;
    private boolean        transitioning = false;

    // Spin animation state (fields so spinTick() method reference works cleanly)
    private int spinTickCount = 0;
    private int totalMoves    = 0;
    private int currentMove   = 0;
    private int spinTargetPos = 0;
    private int ballPos       = 0;
    private int resultType    = -1;
    private BukkitTask spinTask;

    // Inventories
    private Inventory betInv;

    // Display entities
    private final List<ItemDisplay> wheelSegments = new ArrayList<>();
    private ItemDisplay ballDisplay;
    private ItemDisplay centerDisplay;
    private Location    wheelCenter;

    public HorseWheelGame(HimaCasino plugin, Player player) {
        super(plugin, player, 0);
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

        // Horse selector buttons (row 0)
        for (int i = 0; i < 6; i++) {
            boolean sel = (i == selectedHorse);
            List<String> lore = new ArrayList<>();
            lore.add(String.format("§7Multiplier: §e×%.0f", MULTIPLIERS[i]));
            lore.add(String.format("§7Bet: §6§l%.0f %s", bets[i], plugin.getConfigLoader().getCurrencySymbol()));
            if (sel) lore.add("§a§l◀ Selected");
            betInv.setItem(HORSE_SLOTS[i], makeItem(
                    sel ? Material.GLOWSTONE : HORSE_MATS[i],
                    HORSE_COLORS[i] + "§l" + HORSE_NAMES[i],
                    lore));
        }

        // Total bet display
        double total = totalBet();
        betInv.setItem(S_BET_INFO, makeItem(Material.GOLD_BLOCK,
                String.format("§eTOTAL: §6§l%.0f %s", total, plugin.getConfigLoader().getCurrencySymbol()),
                List.of(
                    "§7Adding to: " + HORSE_COLORS[selectedHorse] + "§l" + HORSE_NAMES[selectedHorse],
                    String.format("§7Balance: §e%.0f",
                            plugin.getEconomyManager().isEnabled()
                            ? plugin.getEconomyManager().getBalance(player) : 0.0)
                )));

        // Chip buttons (row 1)
        Material[] chipMats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            betInv.setItem(B_CHIP_START + i, makeItem(chipMats[i],
                    "§a§l+" + (int) CHIP_VALUES[i],
                    List.of("§7→ " + HORSE_COLORS[selectedHorse] + HORSE_NAMES[selectedHorse])));
        }
        betInv.setItem(B_CLEAR, makeItem(Material.BARRIER, "§c§lCLEAR",
                List.of("§7Clear selected horse bet")));

        // Row 2
        betInv.setItem(S_EXIT, makeItem(Material.RED_CONCRETE, "§c§l✗ Exit", List.of("§7Leave game")));
        boolean canSpin = total > 0 && (!plugin.getEconomyManager().isEnabled()
                || plugin.getEconomyManager().getBalance(player) >= total);
        betInv.setItem(S_SPIN, canSpin
                ? makeItem(Material.LIME_CONCRETE, "§a§l▶ SPIN!",
                    List.of(String.format("§7Total bet: §e%.0f %s", total,
                            plugin.getConfigLoader().getCurrencySymbol())))
                : makeItem(Material.RED_CONCRETE, "§c§l✗ SPIN",
                    List.of(total <= 0 ? "§cPlace a bet first" : "§cInsufficient balance")));
    }

    private double totalBet() {
        double sum = 0;
        for (double b : bets) sum += b;
        return sum;
    }

    // ── Click handler ──────────────────────────────────────────────────────

    public void handleClick(int slot) {
        if (state == GameState.FINISHED) return;

        // Horse selection
        for (int i = 0; i < HORSE_SLOTS.length; i++) {
            if (slot == HORSE_SLOTS[i]) {
                selectedHorse = i;
                refreshBetUI();
                return;
            }
        }

        // Chip add
        if (slot >= B_CHIP_START && slot < B_CHIP_START + CHIP_VALUES.length) {
            double add = CHIP_VALUES[slot - B_CHIP_START];
            double max = plugin.getConfigLoader().getHighLowMaxBet();
            bets[selectedHorse] = Math.min(bets[selectedHorse] + add, max);
            refreshBetUI();
            return;
        }

        switch (slot) {
            case B_CLEAR -> { bets[selectedHorse] = 0; refreshBetUI(); }
            case S_EXIT  -> plugin.getServer().getScheduler().runTask(plugin, this::cleanup);
            case S_SPIN  -> {
                double total = totalBet();
                if (total > 0) startSpin(total);
            }
        }
    }

    // ── Spin ───────────────────────────────────────────────────────────────

    private void startSpin(double total) {
        betAmount = total;
        if (!chargeBet()) return;

        // Close bet UI (fires InventoryCloseEvent synchronously; transitioning guard suppresses cleanup)
        transitioning = true;
        player.closeInventory();
        transitioning = false;

        spawnWheel();

        // Determine result and compute ball path
        Random rng   = new Random();
        int startPos = rng.nextInt(24);
        spinTargetPos = rng.nextInt(24);
        int laps     = 3 + rng.nextInt(3);
        totalMoves   = laps * 24 + ((spinTargetPos - startPos + 24) % 24);
        if (totalMoves < 24) totalMoves += 24; // ensure at least 1 full lap
        currentMove   = 0;
        ballPos       = startPos;
        spinTickCount = 0;
        resultType    = WHEEL_ORDER[spinTargetPos];

        spinTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spinTick, 0L, 1L);
        player.sendMessage("§d§l══ HORSE WHEEL ══ §d§l▶ SPIN!");
    }

    private void spinTick() {
        if (currentMove >= totalMoves) {
            ballPos = spinTargetPos;
            updateBallDisplay();
            spinTask.cancel();
            spinTask = null;
            onSpinFinished();
            return;
        }

        spinTickCount++;
        double progress    = (double) currentMove / totalMoves;
        int ticksPerMove   = progress < 0.40 ? 1 : progress < 0.70 ? 2 : progress < 0.85 ? 4 : 7;

        if (spinTickCount % ticksPerMove == 0) {
            currentMove++;
            ballPos = (ballPos + 1) % 24;
            updateBallDisplay();
            float pitch = 0.6f + (1.0f - (float) Math.min(progress, 0.97)) * 1.4f;
            player.getWorld().playSound(wheelCenter, Sound.ENTITY_HORSE_GALLOP, 0.55f, pitch);
        }
    }

    private void onSpinFinished() {
        player.getWorld().playSound(wheelCenter, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.9f);
        player.getWorld().spawnParticle(Particle.CRIT, wheelCenter, 25, 0.4, 0.1, 0.4, 0.05);

        highlightWinner();

        // Calculate payout
        double winnings = bets[resultType] > 0 ? bets[resultType] * MULTIPLIERS[resultType] : 0;

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

        // Despawn wheel after 4 seconds, then reopen bet UI
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isFinished()) return;
            despawnWheel();
            Arrays.fill(bets, 0);
            openBetUI();
        }, 80L);
    }

    private void highlightWinner() {
        if (spinTargetPos < wheelSegments.size()) {
            ItemDisplay seg = wheelSegments.get(spinTargetPos);
            if (seg != null && seg.isValid()) {
                seg.setItemStack(new ItemStack(Material.GLOWSTONE));
            }
        }
        player.getWorld().playSound(wheelCenter, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1f, 1.2f);
    }

    // ── Display entities ───────────────────────────────────────────────────

    private void spawnWheel() {
        Location pLoc   = player.getLocation();
        double yawRad   = Math.toRadians(pLoc.getYaw());
        double dx       = -Math.sin(yawRad) * 3.5;
        double dz       =  Math.cos(yawRad) * 3.5;
        wheelCenter     = pLoc.clone().add(dx, 0.05, dz);
        World world     = wheelCenter.getWorld();
        double segRadius = 1.5;

        // 24 colored segment tiles arranged in a horizontal circle
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15.0);
            double sx    = wheelCenter.getX() + segRadius * Math.cos(angle);
            double sz    = wheelCenter.getZ() + segRadius * Math.sin(angle);
            Location loc = new Location(world, sx, wheelCenter.getY(), sz);
            final int horseType = WHEEL_ORDER[i];
            ItemDisplay seg = world.spawn(loc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(HORSE_MATS[horseType]));
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.45f, 0.08f, 0.45f),
                        new AxisAngle4f(0, 0, 1, 0)));
                d.setBillboardType(Display.Billboard.FIXED);
                d.setViewRange(32);
            });
            wheelSegments.add(seg);
        }

        // Center magenta disk
        centerDisplay = world.spawn(wheelCenter.clone(), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.55f, 0.09f, 0.55f),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setBillboardType(Display.Billboard.FIXED);
            d.setViewRange(32);
        });

        // Ball (quartz cube) at outer edge
        ballDisplay = spawnBall(ballPos, segRadius + 0.55);
    }

    private ItemDisplay spawnBall(int pos, double radius) {
        double angle  = Math.toRadians(pos * 15.0);
        double bx     = wheelCenter.getX() + radius * Math.cos(angle);
        double bz     = wheelCenter.getZ() + radius * Math.sin(angle);
        Location loc  = new Location(wheelCenter.getWorld(), bx, wheelCenter.getY() + 0.22, bz);
        return wheelCenter.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.QUARTZ_BLOCK));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.28f, 0.28f, 0.28f),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setBillboardType(Display.Billboard.FIXED);
            d.setViewRange(32);
        });
    }

    private void updateBallDisplay() {
        if (ballDisplay == null || wheelCenter == null) return;
        double radius = 1.5 + 0.55;
        double angle  = Math.toRadians(ballPos * 15.0);
        double bx     = wheelCenter.getX() + radius * Math.cos(angle);
        double bz     = wheelCenter.getZ() + radius * Math.sin(angle);
        ballDisplay.teleport(new Location(wheelCenter.getWorld(), bx, wheelCenter.getY() + 0.22, bz));
    }

    private void despawnWheel() {
        for (ItemDisplay d : wheelSegments) if (d != null && d.isValid()) d.remove();
        wheelSegments.clear();
        if (ballDisplay   != null && ballDisplay.isValid())   { ballDisplay.remove();   ballDisplay   = null; }
        if (centerDisplay != null && centerDisplay.isValid()) { centerDisplay.remove(); centerDisplay = null; }
        wheelCenter = null;
    }

    // ── GameBase overrides ─────────────────────────────────────────────────

    @Override public void onTick()              { /* managed by spinTask */ }
    @Override public void onWin(double m)       { }
    @Override public void onLoss()              { }

    @Override
    public void cleanup() {
        if (state == GameState.FINISHED) return;
        state = GameState.FINISHED;
        stopTickTask();
        if (spinTask != null) { spinTask.cancel(); spinTask = null; }
        despawnWheel();
        String open = player.getOpenInventory().getTitle();
        if (BET_TITLE.equals(open)) player.closeInventory();
        plugin.getGameManager().removeHorseWheelGame(player);
    }

    // ── Item helpers ───────────────────────────────────────────────────────

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack bg() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName("§0");
        item.setItemMeta(meta);
        return item;
    }
}
