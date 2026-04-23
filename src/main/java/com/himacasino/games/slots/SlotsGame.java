package com.himacasino.games.slots;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.util.*;

public class SlotsGame extends GameBase {

    private static final int REELS          = 3;
    private static final int SYMBOLS        = 7;
    private static final int BASE_SPIN_TICKS = 60;
    private static final int REEL_DELAY     = 20;

    private static final String[] SYM_COLORS = {
        "§f", "§b", "§a", "§e", "§6", "§c", "§d"
    };

    private final int   setting;
    /** Null when started via command (no physical sign). */
    private final Block signBlock;

    private final int[]     results = new int[REELS];
    private final boolean[] stopped = new boolean[REELS];
    private int tick = 0;

    public SlotsGame(HimaCasino plugin, Player player, double betAmount, int setting, Block signBlock) {
        super(plugin, player, betAmount);
        this.setting   = setting;
        this.signBlock = signBlock;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void onStart() {
        if (!chargeBet()) {
            if (signBlock != null) plugin.getMachineManager().release(signBlock.getLocation());
            plugin.getGameManager().removeSlotsGame(player);
            return;
        }
        state = GameState.RUNNING;
        updateSign("§e? §7| §e? §7| §e?", "§cPlaying...");

        player.sendMessage("§6§l╔══════════════════╗");
        player.sendMessage(String.format("§6§l║  §eSLOTS §7(setting %d)  §6§l║", setting));
        player.sendMessage("§6§l╚══════════════════╝");
        player.sendMessage(String.format("§7Bet: §e%.0f %s", betAmount,
                plugin.getConfigLoader().getCurrencySymbol()));

        signLocation().getWorld().playSound(signLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
        startTickTask(1L);
    }

    @Override
    public void onTick() {
        tick++;
        for (int i = 0; i < REELS; i++) {
            int stopAt = BASE_SPIN_TICKS + i * REEL_DELAY;
            if (!stopped[i]) {
                if (tick < stopAt) {
                    if (tick % 3 == 0)
                        signLocation().getWorld().playSound(signLocation(),
                                Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.5f - i * 0.1f);
                } else {
                    results[i] = rollSymbol();
                    stopped[i] = true;
                    signLocation().getWorld().playSound(signLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.0f + i * 0.1f);
                    signLocation().getWorld().spawnParticle(Particle.CRIT,
                            signLocation().clone().add((i - 1) * 1.2, 1.5, 0),
                            8, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        // Update sign reel line
        if (tick % 2 == 0) {
            String r0 = stopped[0] ? SYM_COLORS[results[0] - 1] + results[0] : "§e?";
            String r1 = stopped[1] ? SYM_COLORS[results[1] - 1] + results[1] : "§e?";
            String r2 = stopped[2] ? SYM_COLORS[results[2] - 1] + results[2] : "§e?";
            updateSignLine1(r0 + " §7| " + r1 + " §7| " + r2);
        }

        if (stopped[0] && stopped[1] && stopped[2]) {
            stopTickTask();
            plugin.getServer().getScheduler().runTaskLater(plugin, this::evaluateResult, 15L);
        }
    }

    private void evaluateResult() {
        player.sendMessage(String.format("§6Result: %s§l%d §7| %s§l%d §7| %s§l%d",
                SYM_COLORS[results[0] - 1], results[0],
                SYM_COLORS[results[1] - 1], results[1],
                SYM_COLORS[results[2] - 1], results[2]));

        Map<String, Double> pay  = plugin.getConfigLoader().getSlotsPayouts();
        double              mult = calcMultiplier(pay);
        if (mult > 0) onWin(mult); else onLoss();
    }

    private double calcMultiplier(Map<String, Double> pay) {
        int a = results[0], b = results[1], c = results[2];
        if (a == 7 && b == 7 && c == 7) {
            player.sendMessage("§6§l★ JACKPOT! 7-7-7 ★");
            return pay.getOrDefault("7-7-7", 100.0);
        }
        if (a == b && b == c) { player.sendMessage("§a§l3 of a kind!"); return pay.getOrDefault("3-of-a-kind", 10.0); }
        if (countOf(7) == 2)  { player.sendMessage("§aTwo 7s!");        return pay.getOrDefault("two-7s", 5.0); }
        if (countOf(7) == 1)  { player.sendMessage("§aOne 7!");         return pay.getOrDefault("one-7", 2.0); }
        if (a == b || b == c || a == c) { player.sendMessage("§a2 of a kind!"); return pay.getOrDefault("two-of-a-kind", 1.5); }
        return 0;
    }

    private int countOf(int sym) {
        int n = 0;
        for (int r : results) if (r == sym) n++;
        return n;
    }

    private int rollSymbol() {
        List<Integer> w = plugin.getConfigLoader().getSlotsWeights(setting);
        if (w == null || w.size() < SYMBOLS) return new Random().nextInt(SYMBOLS) + 1;
        int total = w.stream().mapToInt(Integer::intValue).sum();
        int roll  = new Random().nextInt(total);
        int cum   = 0;
        for (int i = 0; i < w.size(); i++) { cum += w.get(i); if (roll < cum) return i + 1; }
        return SYMBOLS;
    }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        String sym = reelStr();
        updateSign(sym, "§aYou Win! +" + fmt(betAmount * multiplier));
        signLocation().getWorld().playSound(signLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        signLocation().getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                signLocation().clone().add(0, 2.5, 0), 40, 0.8, 0.5, 0.8, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 120L);
    }

    @Override
    public void onLoss() {
        state = GameState.LOSS;
        player.sendMessage(String.format("§cNo luck! Lost §e%.0f %s§c.", betAmount,
                plugin.getConfigLoader().getCurrencySymbol()));
        updateSign(reelStr(), "§cTry Again!");
        signLocation().getWorld().playSound(signLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 100L);
    }

    @Override
    public void cleanup() {
        stopTickTask();
        String sym = (results[0] > 0) ? reelStr() : "§7? §7| §7? §7| §7?";
        updateSign(sym, "§aClick to Start!");
        if (signBlock != null) plugin.getMachineManager().release(signBlock.getLocation());
        plugin.getGameManager().removeSlotsGame(player);
        state = GameState.FINISHED;
    }

    // ── Sign helpers ───────────────────────────────────────────────────────

    private void updateSign(String reelLine, String statusLine) {
        if (signBlock == null) return;
        if (!(signBlock.getState() instanceof Sign sign)) return;
        var side = sign.getSide(Side.FRONT);
        side.line(1, leg(reelLine));
        side.line(3, leg(statusLine));
        sign.update();
    }

    private void updateSignLine1(String reelLine) {
        if (signBlock == null) return;
        if (!(signBlock.getState() instanceof Sign sign)) return;
        sign.getSide(Side.FRONT).line(1, leg(reelLine));
        sign.update();
    }

    private String reelStr() {
        return String.format("%s%d §7| %s%d §7| %s%d",
                SYM_COLORS[results[0] - 1], results[0],
                SYM_COLORS[results[1] - 1], results[1],
                SYM_COLORS[results[2] - 1], results[2]);
    }

    private static Component leg(String s) {
        return LegacyComponentSerializer.legacySection().deserialize(s);
    }

    private Location signLocation() {
        return signBlock != null ? signBlock.getLocation() : player.getLocation();
    }

    private static String fmt(double v) {
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("%.1fk", v / 1_000);
        return String.format("%.0f", v);
    }
}
