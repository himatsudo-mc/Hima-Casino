package com.himacasino.games.slots;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class SlotsGame extends GameBase {

    private static final int REELS = 3;
    private static final int SYMBOLS = 7;

    // Reel stop tick offsets (each reel stops 20 ticks after the previous)
    private static final int BASE_SPIN_TICKS = 60;
    private static final int REEL_DELAY = 20;

    private final int setting;
    private final Location origin;
    private final UUID gameId;

    private final TextDisplay[] reelDisplays = new TextDisplay[REELS];
    private final int[] results = new int[REELS];
    private final boolean[] stopped = new boolean[REELS];
    private int tick = 0;

    // Symbol colors for visual feedback
    private static final String[] SYMBOL_COLORS = {
        "§f", "§b", "§a", "§e", "§6", "§c", "§d"
    };

    // Custom model data IDs for ItemDisplay (resource pack)
    public static final int BASE_CUSTOM_MODEL = 1; // 1–7 = slot numbers

    public SlotsGame(HimaCasino plugin, Player player, double betAmount, int setting, Location origin) {
        super(plugin, player, betAmount);
        this.setting = setting;
        this.origin = origin.clone();
        this.gameId = UUID.randomUUID();
    }

    @Override
    public void onStart() {
        if (!chargeBet()) return;
        state = GameState.RUNNING;

        spawnReelDisplays();

        player.sendMessage("§6§l╔══════════════════╗");
        player.sendMessage(String.format("§6§l║  §eスロット §7(設定%d)  §6§l║", setting));
        player.sendMessage("§6§l╚══════════════════╝");
        player.sendMessage(String.format("§7賭け金: §e%.0f %s",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));

        origin.getWorld().playSound(origin, Sound.BLOCK_LEVER_CLICK, 1f, 1f);
        startTickTask(1L);
    }

    private void spawnReelDisplays() {
        for (int i = 0; i < REELS; i++) {
            Location loc = origin.clone().add((i - 1) * 1.2, 2.2, 0);
            TextDisplay td = plugin.getDisplayManager().spawnTextDisplay(loc, "§f?", 2.0f);
            reelDisplays[i] = td;
            plugin.getDisplayManager().trackDisplay(gameId, td);

            // Reel frame using ItemDisplay (paper with custom model)
            ItemStack frame = createSlotFrameItem(i);
            Location frameLoc = origin.clone().add((i - 1) * 1.2, 2.2, -0.05);
            var frameDisplay = plugin.getDisplayManager().spawnItemDisplay(frameLoc, frame, 1.0f);
            plugin.getDisplayManager().trackDisplay(gameId, frameDisplay);
        }
    }

    private ItemStack createSlotFrameItem(int reelIndex) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(10 + reelIndex); // resource pack: slot frame models
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void onTick() {
        tick++;

        for (int i = 0; i < REELS; i++) {
            int stopAt = BASE_SPIN_TICKS + i * REEL_DELAY;

            if (!stopped[i]) {
                if (tick < stopAt) {
                    // Spinning: cycle through numbers with offset per reel
                    int displayNum = ((tick + i * 3) % SYMBOLS) + 1;
                    updateReelDisplay(i, SYMBOL_COLORS[displayNum - 1] + "§l" + displayNum, false);

                    // Tick sound
                    if (tick % 3 == 0) {
                        origin.getWorld().playSound(origin, Sound.BLOCK_NOTE_BLOCK_HAT,
                                0.4f, 1.5f - (float) i * 0.1f);
                    }
                } else {
                    // Stop this reel
                    results[i] = rollSymbol();
                    stopped[i] = true;
                    updateReelDisplay(i, SYMBOL_COLORS[results[i] - 1] + "§l" + results[i], true);
                    origin.getWorld().playSound(origin, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f + i * 0.1f);

                    // Reveal flash particle
                    origin.getWorld().spawnParticle(Particle.CRIT,
                            reelDisplays[i].getLocation().add(0, 0.3, 0),
                            8, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        // All reels stopped
        if (stopped[0] && stopped[1] && stopped[2]) {
            stopTickTask();
            // Brief pause before evaluation
            plugin.getServer().getScheduler().runTaskLater(plugin, this::evaluateResult, 15L);
        }
    }

    private void updateReelDisplay(int index, String text, boolean stopped) {
        TextDisplay td = reelDisplays[index];
        if (td == null || !td.isValid()) return;
        td.setText(text);
        if (stopped) {
            // Scale up slightly on stop for visual pop
            td.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(2.3f, 2.3f, 2.3f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            // Return to normal size after 5 ticks
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (td.isValid()) {
                    td.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(2.0f, 2.0f, 2.0f),
                            new AxisAngle4f(0, 0, 1, 0)
                    ));
                }
            }, 5L);
        }
    }

    private int rollSymbol() {
        List<Integer> weights = plugin.getConfigLoader().getSlotsWeights(setting);
        if (weights == null || weights.size() < SYMBOLS) {
            // Default uniform weights
            return new Random().nextInt(SYMBOLS) + 1;
        }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        int roll = new Random().nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < weights.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) return i + 1;
        }
        return SYMBOLS;
    }

    private void evaluateResult() {
        player.sendMessage(String.format("§6結果: %s§l%d  %s§l%d  %s§l%d",
                SYMBOL_COLORS[results[0] - 1], results[0],
                SYMBOL_COLORS[results[1] - 1], results[1],
                SYMBOL_COLORS[results[2] - 1], results[2]));

        Map<String, Double> payouts = plugin.getConfigLoader().getSlotsPayouts();
        double multiplier = calculateMultiplier(payouts);

        if (multiplier > 0) {
            onWin(multiplier);
        } else {
            onLoss();
        }
    }

    private double calculateMultiplier(Map<String, Double> payouts) {
        int a = results[0], b = results[1], c = results[2];

        // 7-7-7 jackpot
        if (a == 7 && b == 7 && c == 7) {
            player.sendMessage("§6§l★★★  JACKPOT! 7-7-7  ★★★");
            return payouts.getOrDefault("7-7-7", 100.0);
        }
        // Three of a kind
        if (a == b && b == c) {
            player.sendMessage("§a§l3つ揃い！");
            return payouts.getOrDefault("3-of-a-kind", 10.0);
        }
        // Two 7s
        if (countOf(7) == 2) {
            player.sendMessage("§a7が2つ！");
            return payouts.getOrDefault("two-7s", 5.0);
        }
        // One 7
        if (countOf(7) == 1) {
            player.sendMessage("§a7が1つ！");
            return payouts.getOrDefault("one-7", 2.0);
        }
        // Two of a kind
        if (a == b || b == c || a == c) {
            player.sendMessage("§a2つ揃い！");
            return payouts.getOrDefault("two-of-a-kind", 1.5);
        }
        return 0;
    }

    private int countOf(int sym) {
        int n = 0;
        for (int r : results) if (r == sym) n++;
        return n;
    }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        origin.getWorld().playSound(origin, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        origin.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                origin.clone().add(0, 2.5, 0), 40, 0.8, 0.5, 0.8, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 100L);
    }

    @Override
    public void onLoss() {
        state = GameState.LOSS;
        player.sendMessage(String.format("§c残念！ §7賭け金 §e%.0f %s §7を失いました。",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));
        origin.getWorld().playSound(origin, Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 80L);
    }

    @Override
    public void cleanup() {
        stopTickTask();
        plugin.getDisplayManager().removeGameDisplays(gameId);
        plugin.getGameManager().removeSlotsGame(player);
        state = GameState.FINISHED;
    }

    public UUID getGameId() { return gameId; }
    public int getSetting() { return setting; }
}
