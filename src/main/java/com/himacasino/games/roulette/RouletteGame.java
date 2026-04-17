package com.himacasino.games.roulette;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class RouletteGame extends GameBase {

    // European roulette wheel order (clockwise from 0)
    private static final int[] WHEEL_ORDER = {
        0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,
        24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26
    };
    private static final int POCKET_COUNT = WHEEL_ORDER.length;

    private static final int[] RED_NUMBERS = {
        1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    };

    // Angle (radians) of each pocket on the wheel
    private static final double[] POCKET_ANGLES = new double[POCKET_COUNT];
    static {
        for (int i = 0; i < POCKET_COUNT; i++) {
            POCKET_ANGLES[i] = (2 * Math.PI / POCKET_COUNT) * i;
        }
    }

    private final UUID gameId;
    private final Location tableCenter;
    private final int spinTicks;

    // Active bets: number bets (0-36) and color bets ("red"/"black")
    final Map<Integer, Double> numberBets = new HashMap<>();
    final Map<String, Double> colorBets = new HashMap<>();
    double totalBet = 0;

    // Display entities
    private ItemDisplay wheelDisplay;
    private ItemDisplay ballDisplay;
    private TextDisplay resultDisplay;

    // Animation state
    private int tick = 0;
    private float wheelAngle = 0f;
    private float ballAngle = 0f;
    private int winningNumber = -1;

    // Initial angular velocities (rad/tick)
    private static final float WHEEL_OMEGA_0 = 0.18f;  // wheel
    private static final float BALL_OMEGA_0  = -0.35f; // ball (opposite direction)
    private static final float WHEEL_RADIUS  = 1.8f;
    private static final float BALL_RADIUS_MAX = 1.6f;
    private static final float BALL_RADIUS_MIN = 0.4f;

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
        player.sendMessage(String.format("§7数字 §e%d §7に §6%.0f %s §7をベット",
                number, amount, plugin.getConfigLoader().getCurrencySymbol()));
        return true;
    }

    public boolean placeBetOnColor(String color, double amount) {
        if (!color.equals("red") && !color.equals("black")) return false;
        if (!chargeAmount(amount)) return false;
        colorBets.merge(color, amount, Double::sum);
        totalBet += amount;
        String label = color.equals("red") ? "§c赤" : "§8黒";
        player.sendMessage(String.format("§7%s §7に §6%.0f %s §7をベット",
                label, amount, plugin.getConfigLoader().getCurrencySymbol()));
        return true;
    }

    private boolean chargeAmount(double amount) {
        var eco = plugin.getEconomyManager();
        if (eco.isEnabled() && eco.getBalance(player) < amount) {
            player.sendMessage("§c残高が不足しています。");
            return false;
        }
        if (eco.isEnabled()) eco.withdraw(player, amount);
        return true;
    }

    public double getTotalBet() { return totalBet; }

    // ── Game lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onStart() {
        if (totalBet <= 0) {
            player.sendMessage("§cまずベットしてください。 §7(/casino roulette <数字|red|black> <金額>)");
            return;
        }
        betAmount = totalBet;
        state = GameState.RUNNING;

        // Pre-determine winning number
        winningNumber = WHEEL_ORDER[new Random().nextInt(POCKET_COUNT)];

        spawnDisplays();

        player.sendMessage("§6§l╔══════════════════╗");
        player.sendMessage("§6§l║  ルーレット スピン  §6§l║");
        player.sendMessage("§6§l╚══════════════════╝");
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f, 0.8f);

        tick = 0;
        startTickTask(1L);
    }

    private void spawnDisplays() {
        // Wheel – flat disk (FIXED billboard, tipped 90° around X to lie horizontal)
        ItemStack wheelItem = new ItemStack(Material.PAPER);
        ItemMeta wm = wheelItem.getItemMeta();
        wm.setCustomModelData(20); // resource pack: roulette_wheel model
        wheelItem.setItemMeta(wm);

        wheelDisplay = tableCenter.getWorld().spawn(tableCenter.clone().add(0, 0.1, 0), ItemDisplay.class, d -> {
            d.setItemStack(wheelItem);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // Lay flat: left rotation = 90° around X
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(WHEEL_RADIUS, WHEEL_RADIUS, WHEEL_RADIUS),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setInterpolationDuration(2);
        });
        plugin.getDisplayManager().trackDisplay(gameId, wheelDisplay);

        // Ball – small sphere orbiting the wheel
        ItemStack ballItem = new ItemStack(Material.PAPER);
        ItemMeta bm = ballItem.getItemMeta();
        bm.setCustomModelData(21); // resource pack: roulette_ball model
        ballItem.setItemMeta(bm);

        Location ballStartLoc = tableCenter.clone().add(BALL_RADIUS_MAX, 0.15, 0);
        ballDisplay = tableCenter.getWorld().spawn(ballStartLoc, ItemDisplay.class, d -> {
            d.setItemStack(ballItem);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.15f, 0.15f, 0.15f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
        });
        plugin.getDisplayManager().trackDisplay(gameId, ballDisplay);

        // Result text display (hidden until end)
        resultDisplay = plugin.getDisplayManager().spawnTextDisplay(
                tableCenter.clone().add(0, 1.5, 0), "", 1.5f);
        plugin.getDisplayManager().trackDisplay(gameId, resultDisplay);
    }

    @Override
    public void onTick() {
        tick++;
        float progress = (float) tick / spinTicks;
        if (progress > 1f) progress = 1f;

        // Ease-out deceleration: speed = omega_0 * (1 - progress)^2
        float wheelOmega = WHEEL_OMEGA_0 * (1f - progress) * (1f - progress);
        float ballOmega  = BALL_OMEGA_0  * (1f - progress * 0.8f);

        wheelAngle += wheelOmega;
        ballAngle  += ballOmega;

        // Update wheel rotation (right rotation = spin around Z in flat-down space)
        if (wheelDisplay != null && wheelDisplay.isValid()) {
            wheelDisplay.setInterpolationDelay(0);
            wheelDisplay.setInterpolationDuration(2);
            wheelDisplay.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(WHEEL_RADIUS, WHEEL_RADIUS, WHEEL_RADIUS),
                    new AxisAngle4f(wheelAngle, 0, 0, 1)
            ));
        }

        // Ball spiral: radius decreases as progress increases
        float ballRadius = BALL_RADIUS_MAX - (BALL_RADIUS_MAX - BALL_RADIUS_MIN) * progress;
        float ballHeight = 0.15f - 0.1f * progress; // gradually drops into pocket

        double bx = Math.cos(ballAngle) * ballRadius;
        double bz = Math.sin(ballAngle) * ballRadius;
        if (ballDisplay != null && ballDisplay.isValid()) {
            ballDisplay.teleport(tableCenter.clone().add(bx, ballHeight, bz));
        }

        // Ball bounce sound (frequency decreases as ball slows)
        int bounceInterval = Math.max(2, (int)(3 + progress * 12));
        if (tick % bounceInterval == 0 && progress < 0.95f) {
            float pitch = 1.8f - progress;
            tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_STONE_HIT,
                    0.25f, pitch);
        }
        // Wheel whir sound
        if (tick % 8 == 0 && progress < 0.9f) {
            tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON,
                    0.15f, 0.9f + (1f - progress) * 0.3f);
        }

        if (tick >= spinTicks) {
            stopTickTask();
            finalizeSpin();
        }
    }

    private void finalizeSpin() {
        // Snap ball to correct pocket position on the wheel
        int pocketIndex = getPocketIndex(winningNumber);
        double finalAngle = POCKET_ANGLES[pocketIndex];
        double fx = Math.cos(finalAngle) * (BALL_RADIUS_MIN + 0.05);
        double fz = Math.sin(finalAngle) * (BALL_RADIUS_MIN + 0.05);
        if (ballDisplay != null && ballDisplay.isValid()) {
            ballDisplay.teleport(tableCenter.clone().add(fx, 0.05, fz));
        }

        // Play landing sounds
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_STONE_PLACE, 1f, 1.2f);
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.9f);

        // Show result
        boolean red = isRed(winningNumber);
        String color = (winningNumber == 0) ? "§a" : (red ? "§c" : "§8");
        String colorLabel = (winningNumber == 0) ? "§a(グリーン)" : (red ? "§c(レッド)" : "§8(ブラック)");
        if (resultDisplay != null && resultDisplay.isValid()) {
            resultDisplay.setText(String.format("§l%s%d\n§7%s", color, winningNumber, colorLabel));
        }

        player.sendMessage(String.format("§6§lボールが入ったポケット: %s§l%d %s",
                color, winningNumber, colorLabel));

        // Evaluate bets
        double winnings = 0;
        for (var e : numberBets.entrySet()) {
            if (e.getKey() == winningNumber) winnings += e.getValue() * 36; // 35:1 + stake
        }
        for (var e : colorBets.entrySet()) {
            boolean betRed = e.getKey().equals("red");
            if (winningNumber != 0 && (betRed == red)) {
                winnings += e.getValue() * 2; // 1:1 + stake
            }
        }

        if (winnings > 0) {
            double mult = winnings / betAmount;
            onWin(mult);
        } else {
            onLoss();
        }
    }

    private int getPocketIndex(int number) {
        for (int i = 0; i < WHEEL_ORDER.length; i++) {
            if (WHEEL_ORDER[i] == number) return i;
        }
        return 0;
    }

    public static boolean isRed(int number) {
        for (int r : RED_NUMBERS) if (r == number) return true;
        return false;
    }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        tableCenter.getWorld().playSound(tableCenter, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        tableCenter.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                tableCenter.clone().add(0, 1.5, 0), 50, 1.2, 0.5, 1.2, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 120L);
    }

    @Override
    public void onLoss() {
        state = GameState.LOSS;
        player.sendMessage(String.format("§c残念！ §7合計 §e%.0f %s §7を失いました。",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));
        tableCenter.getWorld().playSound(tableCenter, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 100L);
    }

    @Override
    public void cleanup() {
        stopTickTask();
        plugin.getDisplayManager().removeGameDisplays(gameId);
        plugin.getGameManager().removeRouletteGame(player);
        state = GameState.FINISHED;
    }

    public UUID getGameId() { return gameId; }
    public int getWinningNumber() { return winningNumber; }
    public boolean hasAnyBet() { return totalBet > 0; }
}
