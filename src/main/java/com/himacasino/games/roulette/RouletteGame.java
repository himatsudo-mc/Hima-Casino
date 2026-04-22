package com.himacasino.games.roulette;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Roulette game with a realistic wheel visual.
 *
 * Visuals (Display Entities):
 *  ┌── Center spinning disk (1 ItemDisplay, rotates via setTransformation)
 *  ├── 37 pocket ItemDisplays arranged in a static ring (colored concrete)
 *  ├── 37 TextDisplays showing pocket numbers (static, same positions)
 *  └── 1 ball ItemDisplay (teleports every tick along a spiral path)
 */
public class RouletteGame extends GameBase {

    // European roulette wheel order (clockwise)
    static final int[] WHEEL_ORDER = {
        0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,
        24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26
    };
    private static final int POCKET_COUNT = WHEEL_ORDER.length; // 37

    static final int[] RED_NUMBERS = {
        1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    };

    // Pre-computed angle for each pocket index (radians, starting at angle 0)
    private static final double[] POCKET_ANGLES = new double[POCKET_COUNT];
    static {
        for (int i = 0; i < POCKET_COUNT; i++)
            POCKET_ANGLES[i] = (2 * Math.PI / POCKET_COUNT) * i;
    }

    // Visual dimensions
    private static final float POCKET_RADIUS    = 1.35f; // ring radius (blocks)
    private static final float CENTER_DISK_SCALE = 1.1f;  // half-size of center disk
    private static final float BALL_RADIUS_MAX   = 1.6f;
    private static final float BALL_RADIUS_MIN   = POCKET_RADIUS - 0.05f;
    private static final float POCKET_SCALE      = 0.28f; // size of each pocket block

    // Spin parameters
    private static final float WHEEL_OMEGA_0 = 0.18f;   // initial wheel angular velocity (rad/tick)
    private static final float BALL_OMEGA_0  = -0.40f;  // ball (opposite direction)

    private final UUID gameId;
    private final Location tableCenter;
    private final int spinTicks;

    // Bets
    final Map<Integer, Double> numberBets = new HashMap<>();
    final Map<String, Double>  colorBets  = new HashMap<>();
    double totalBet = 0;

    // Display entities
    private ItemDisplay centerDisk;
    private ItemDisplay ballDisplay;
    private final ItemDisplay[] pocketDisplays = new ItemDisplay[POCKET_COUNT];
    private final TextDisplay[]  numberDisplays  = new TextDisplay[POCKET_COUNT];

    // Animation state
    private int tick = 0;
    private float wheelAngle = 0f;
    private float ballAngle  = 0f;
    private int winningNumber = -1;

    public RouletteGame(HimaCasino plugin, Player player, Location tableCenter) {
        super(plugin, player, 0);
        this.gameId = UUID.randomUUID();
        this.tableCenter = tableCenter.clone();
        this.spinTicks = plugin.getConfigLoader().getRouletteSpinTicks();
    }

    // ── Betting ────────────────────────────────────────────────────────────

    public boolean placeBetOnNumber(int number, double amount) {
        if (number < 0 || number > 36) return false;
        if (!chargeAmount(amount)) return false;
        numberBets.merge(number, amount, Double::sum);
        totalBet += amount;
        player.sendMessage(String.format("§7Bet §e%d §7→ §6%.0f %s",
                number, amount, plugin.getConfigLoader().getCurrencySymbol()));
        return true;
    }

    public boolean placeBetOnColor(String color, double amount) {
        if (!color.equals("red") && !color.equals("black")) return false;
        if (!chargeAmount(amount)) return false;
        colorBets.merge(color, amount, Double::sum);
        totalBet += amount;
        String label = color.equals("red") ? "§cRED" : "§8BLACK";
        player.sendMessage(String.format("§7Bet %s §7→ §6%.0f %s",
                label, amount, plugin.getConfigLoader().getCurrencySymbol()));
        return true;
    }

    private boolean chargeAmount(double amount) {
        var eco = plugin.getEconomyManager();
        if (eco.isEnabled() && eco.getBalance(player) < amount) {
            player.sendMessage("§cInsufficient balance!");
            return false;
        }
        if (eco.isEnabled()) eco.withdraw(player, amount);
        return true;
    }

    public double getTotalBet() { return totalBet; }
    public boolean hasAnyBet()  { return totalBet > 0; }

    // ── Game lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onStart() {
        if (totalBet <= 0) {
            player.sendMessage("§cPlace a bet first!  §7(/casino roulette <number|red|black> <amount>)");
            return;
        }
        betAmount = totalBet;
        state = GameState.RUNNING;

        winningNumber = WHEEL_ORDER[new Random().nextInt(POCKET_COUNT)];
        spawnDisplays();

        player.sendMessage("§6§l╔══════════════════════╗");
        player.sendMessage("§6§l║    ROULETTE SPIN      ║");
        player.sendMessage("§6§l╚══════════════════════╝");
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 0.8f);

        tick = 0;
        startTickTask(1L);
    }

    // ── Visual spawn ───────────────────────────────────────────────────────

    private void spawnDisplays() {
        // 1. Center spinning disk (dark oak log tipped flat)
        ItemStack diskItem = new ItemStack(Material.DARK_OAK_LOG);
        centerDisk = tableCenter.getWorld().spawn(tableCenter.clone().add(0, 0.05, 0), ItemDisplay.class, d -> {
            d.setItemStack(diskItem);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // Lay flat by tilting 90° around X, then start spin angle 0
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(CENTER_DISK_SCALE, CENTER_DISK_SCALE, CENTER_DISK_SCALE),
                    new AxisAngle4f(0, 0, 0, 1)));
            d.setInterpolationDuration(2);
        });
        plugin.getDisplayManager().trackDisplay(gameId, centerDisk);

        // 2. Pocket ring (37 colored blocks + number labels)
        for (int i = 0; i < POCKET_COUNT; i++) {
            int number = WHEEL_ORDER[i];
            double angle = POCKET_ANGLES[i];
            double px = Math.cos(angle) * POCKET_RADIUS;
            double pz = Math.sin(angle) * POCKET_RADIUS;
            Location pLoc = tableCenter.clone().add(px, 0.06, pz);

            // Colored block
            Material mat = pocketMaterial(number);
            ItemStack pocketItem = new ItemStack(mat);
            final int idx = i;
            pocketDisplays[i] = tableCenter.getWorld().spawn(pLoc, ItemDisplay.class, d -> {
                d.setItemStack(pocketItem);
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                        new Vector3f(POCKET_SCALE, POCKET_SCALE, POCKET_SCALE),
                        new AxisAngle4f(0, 0, 0, 1)));
            });
            plugin.getDisplayManager().trackDisplay(gameId, pocketDisplays[i]);

            // Number label (above the block)
            String color = number == 0 ? "§a" : (isRed(number) ? "§c" : "§8");
            Location nLoc = tableCenter.clone().add(px, 0.22, pz);
            numberDisplays[i] = plugin.getDisplayManager().spawnTextDisplay(
                    nLoc, color + "§l" + number, 0.4f);
            numberDisplays[i].setBillboard(Display.Billboard.FIXED);
            plugin.getDisplayManager().trackDisplay(gameId, numberDisplays[i]);
        }

        // 3. Ball (starts at outer edge)
        ItemStack ballItem = new ItemStack(Material.SNOWBALL);
        ItemMeta bm = ballItem.getItemMeta();
        bm.setCustomModelData(21);
        ballItem.setItemMeta(bm);
        ballDisplay = tableCenter.getWorld().spawn(
                tableCenter.clone().add(BALL_RADIUS_MAX, 0.15, 0), ItemDisplay.class, d -> {
            d.setItemStack(ballItem);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(0,0,1,0),
                    new Vector3f(0.13f, 0.13f, 0.13f), new AxisAngle4f(0,0,1,0)));
        });
        plugin.getDisplayManager().trackDisplay(gameId, ballDisplay);
    }

    private static Material pocketMaterial(int number) {
        if (number == 0) return Material.LIME_CONCRETE;
        return isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }

    // ── Tick (animation) ───────────────────────────────────────────────────

    @Override
    public void onTick() {
        tick++;
        float progress = Math.min(1f, (float) tick / spinTicks);

        // Ease-out deceleration
        float wheelOmega = WHEEL_OMEGA_0 * (1f - progress) * (1f - progress);
        float ballOmega  = BALL_OMEGA_0  * (1f - progress * 0.8f);

        wheelAngle += wheelOmega;
        ballAngle  += ballOmega;

        // Spinning center disk (setTransformation → client-side interpolation)
        if (centerDisk != null && centerDisk.isValid()) {
            centerDisk.setInterpolationDelay(0);
            centerDisk.setInterpolationDuration(2);
            centerDisk.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(CENTER_DISK_SCALE, CENTER_DISK_SCALE, CENTER_DISK_SCALE),
                    new AxisAngle4f(wheelAngle, 0, 0, 1)));
        }

        // Ball spiral
        float radius = BALL_RADIUS_MAX - (BALL_RADIUS_MAX - BALL_RADIUS_MIN) * progress;
        float height = 0.15f - 0.10f * progress;
        double bx = Math.cos(ballAngle) * radius;
        double bz = Math.sin(ballAngle) * radius;
        if (ballDisplay != null && ballDisplay.isValid()) {
            ballDisplay.teleport(tableCenter.clone().add(bx, height, bz));
        }

        // Sounds
        int bounceInterval = Math.max(2, (int)(3 + progress * 12));
        if (tick % bounceInterval == 0 && progress < 0.95f) {
            tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_STONE_HIT, 0.25f, 1.8f - progress);
        }
        if (tick % 10 == 0 && progress < 0.9f) {
            tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.12f,
                    0.8f + (1f - progress) * 0.3f);
        }

        if (tick >= spinTicks) {
            stopTickTask();
            finalizeSpin();
        }
    }

    private void finalizeSpin() {
        // Snap ball to winning pocket
        int pocketIdx = getPocketIndex(winningNumber);
        double winAngle = POCKET_ANGLES[pocketIdx];
        double fx = Math.cos(winAngle) * (BALL_RADIUS_MIN - 0.02);
        double fz = Math.sin(winAngle) * (BALL_RADIUS_MIN - 0.02);
        if (ballDisplay != null && ballDisplay.isValid())
            ballDisplay.teleport(tableCenter.clone().add(fx, 0.08, fz));

        // Highlight winning pocket
        if (pocketDisplays[pocketIdx] != null && pocketDisplays[pocketIdx].isValid()) {
            pocketDisplays[pocketIdx].setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(POCKET_SCALE * 1.5f, POCKET_SCALE * 1.5f, POCKET_SCALE * 1.5f),
                    new AxisAngle4f(0, 0, 0, 1)));
        }

        // Sounds
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_STONE_PLACE, 1f, 1.2f);
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.9f);

        // Announce
        boolean red = isRed(winningNumber);
        String col = winningNumber == 0 ? "§a" : (red ? "§c" : "§8");
        String colLabel = winningNumber == 0 ? "§a(GREEN)" : (red ? "§c(RED)" : "§8(BLACK)");
        player.sendMessage(String.format("§6§lBall landed on: %s§l%d %s", col, winningNumber, colLabel));

        // Evaluate bets
        double winnings = 0;
        for (var e : numberBets.entrySet())
            if (e.getKey() == winningNumber) winnings += e.getValue() * 36; // 35:1 + stake
        for (var e : colorBets.entrySet()) {
            boolean betRed = e.getKey().equals("red");
            if (winningNumber != 0 && (betRed == red)) winnings += e.getValue() * 2; // 1:1 + stake
        }

        if (winnings > 0) onWin(winnings / betAmount);
        else onLoss();
    }

    private int getPocketIndex(int number) {
        for (int i = 0; i < WHEEL_ORDER.length; i++)
            if (WHEEL_ORDER[i] == number) return i;
        return 0;
    }

    public static boolean isRed(int number) {
        for (int r : RED_NUMBERS) if (r == number) return true;
        return false;
    }

    // ── Win / Loss ─────────────────────────────────────────────────────────

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        tableCenter.getWorld().playSound(tableCenter, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        tableCenter.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                tableCenter.clone().add(0, 1.5, 0), 50, 1.2, 0.5, 1.2, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 140L);
    }

    @Override
    public void onLoss() {
        state = GameState.LOSS;
        player.sendMessage(String.format("§cBetter luck next time! Lost §e%.0f %s§c.",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 120L);
    }

    @Override
    public void cleanup() {
        stopTickTask();
        plugin.getDisplayManager().removeGameDisplays(gameId);
        plugin.getGameManager().removeRouletteGame(player);
        state = GameState.FINISHED;
    }

    public UUID getGameId()      { return gameId; }
    public int getWinningNumber(){ return winningNumber; }
}
