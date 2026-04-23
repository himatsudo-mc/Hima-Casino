package com.himacasino.core;

import com.himacasino.games.highlow.HighLowGame;
import com.himacasino.games.horsewheel.HorseWheelGame;
import com.himacasino.games.roulette.RouletteGame;
import com.himacasino.games.slots.SlotsGame;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {

    private final Map<UUID, SlotsGame>      slots      = new HashMap<>();
    private final Map<UUID, RouletteGame>   roulette   = new HashMap<>();
    private final Map<UUID, HighLowGame>    highlow    = new HashMap<>();
    private final Map<UUID, HorseWheelGame> horsewheel = new HashMap<>();

    public boolean hasActiveGame(Player player) {
        UUID id = player.getUniqueId();
        return isActive(slots.get(id)) || isActive(roulette.get(id))
                || isActive(highlow.get(id)) || isActive(horsewheel.get(id));
    }

    private boolean isActive(GameBase g) {
        return g != null && !g.isFinished();
    }

    // ── Slots ──────────────────────────────────────────────────────────────

    public void registerSlotsGame(Player player, SlotsGame game) {
        slots.put(player.getUniqueId(), game);
    }

    public void removeSlotsGame(Player player) {
        slots.remove(player.getUniqueId());
    }

    // ── Roulette ───────────────────────────────────────────────────────────

    public RouletteGame getRouletteGame(Player player) {
        return roulette.get(player.getUniqueId());
    }

    public void registerRouletteGame(Player player, RouletteGame game) {
        roulette.put(player.getUniqueId(), game);
    }

    public void removeRouletteGame(Player player) {
        roulette.remove(player.getUniqueId());
    }

    // ── High & Low ─────────────────────────────────────────────────────────

    public HighLowGame getHighLowGame(Player player) {
        return highlow.get(player.getUniqueId());
    }

    public void registerHighLowGame(Player player, HighLowGame game) {
        highlow.put(player.getUniqueId(), game);
    }

    public void removeHighLowGame(Player player) {
        highlow.remove(player.getUniqueId());
    }

    // ── Horse Wheel ────────────────────────────────────────────────────────

    public HorseWheelGame getHorseWheelGame(Player player) {
        return horsewheel.get(player.getUniqueId());
    }

    public void registerHorseWheelGame(Player player, HorseWheelGame game) {
        horsewheel.put(player.getUniqueId(), game);
    }

    public void removeHorseWheelGame(Player player) {
        horsewheel.remove(player.getUniqueId());
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public void cleanupAll() {
        slots.values().forEach(g -> { if (!g.isFinished()) g.cleanup(); });
        roulette.values().forEach(g -> { if (!g.isFinished()) g.cleanup(); });
        highlow.values().forEach(g -> { if (!g.isFinished()) g.cleanup(); });
        horsewheel.values().forEach(g -> { if (!g.isFinished()) g.cleanup(); });
        slots.clear();
        roulette.clear();
        highlow.clear();
        horsewheel.clear();
    }
}
