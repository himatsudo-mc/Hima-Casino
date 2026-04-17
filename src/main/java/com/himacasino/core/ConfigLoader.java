package com.himacasino.core;

import com.himacasino.HimaCasino;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigLoader {

    private final HimaCasino plugin;

    public ConfigLoader(HimaCasino plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ── Slots ──────────────────────────────────────────────────────────────

    public int getSlotsDefaultSetting() {
        return cfg().getInt("slots.default-setting", 1);
    }

    public void setSlotsDefaultSetting(int setting) {
        cfg().set("slots.default-setting", setting);
        plugin.saveConfig();
    }

    public List<Integer> getSlotsWeights(int setting) {
        return cfg().getIntegerList("slots.settings." + setting + ".weights");
    }

    public Map<String, Double> getSlotsPayouts() {
        Map<String, Double> map = new HashMap<>();
        var section = cfg().getConfigurationSection("slots.payouts");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                map.put(key, section.getDouble(key));
            }
        }
        return map;
    }

    public double getSlotsMinBet() { return cfg().getDouble("slots.min-bet", 10.0); }
    public double getSlotsMaxBet() { return cfg().getDouble("slots.max-bet", 10000.0); }

    // ── Roulette ───────────────────────────────────────────────────────────

    public double getRouletteMinBet() { return cfg().getDouble("roulette.min-bet", 10.0); }
    public double getRouletteMaxBet() { return cfg().getDouble("roulette.max-bet", 10000.0); }
    public int getRouletteSpinTicks() { return cfg().getInt("roulette.spin-ticks", 200); }

    // ── High & Low ─────────────────────────────────────────────────────────

    public double getHighLowMinBet() { return cfg().getDouble("highlow.min-bet", 10.0); }
    public double getHighLowMaxBet() { return cfg().getDouble("highlow.max-bet", 10000.0); }
    public double getHighLowWinMultiplier() { return cfg().getDouble("highlow.win-multiplier", 1.9); }

    // ── General ────────────────────────────────────────────────────────────

    public String getCurrencySymbol() { return cfg().getString("currency-symbol", "コイン"); }
}
