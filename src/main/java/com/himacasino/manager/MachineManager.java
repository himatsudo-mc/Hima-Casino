package com.himacasino.manager;

import com.himacasino.HimaCasino;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MachineManager {

    /** betAmount = per-machine fixed wager (slots). 0 = not applicable (roulette). */
    public record MachineData(MachineType type, int setting, double betAmount) {}

    public enum MachineType { SLOTS, ROULETTE }

    private final HimaCasino plugin;
    private final File dataFile;
    private FileConfiguration data;

    private final Map<String, MachineData> machines = new HashMap<>();
    private final Set<String> busyMachines = new HashSet<>();

    public MachineManager(HimaCasino plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "machines.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("machines.yml を作成できませんでした: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection section = data.getConfigurationSection("machines");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String typeStr  = section.getString(key + ".type");
            int setting     = section.getInt(key + ".setting", 1);
            double betAmount = section.getDouble(key + ".betAmount", 0);
            if (typeStr == null) continue;
            try {
                MachineType type = MachineType.valueOf(typeStr);
                machines.put(key, new MachineData(type, setting, betAmount));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("マシンデータ " + machines.size() + " 台を読み込みました。");
    }

    private void save() {
        for (Map.Entry<String, MachineData> entry : machines.entrySet()) {
            String key = entry.getKey();
            MachineData md = entry.getValue();
            data.set("machines." + key + ".type",      md.type().name());
            data.set("machines." + key + ".setting",   md.setting());
            data.set("machines." + key + ".betAmount", md.betAmount());
        }
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("machines.yml を保存できませんでした: " + e.getMessage()); }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void addMachine(Location loc, MachineType type, int setting, double betAmount) {
        machines.put(locKey(loc), new MachineData(type, setting, betAmount));
        save();
    }

    /** Convenience for roulette tables (no fixed bet). */
    public void addMachine(Location loc, MachineType type, int setting) {
        addMachine(loc, type, setting, 0);
    }

    /** Convenience for slot signs. */
    public void addSlotSign(Location loc, int setting, double betAmount) {
        addMachine(loc, MachineType.SLOTS, setting, betAmount);
    }

    public boolean removeMachine(Location loc) {
        String key = locKey(loc);
        if (!machines.containsKey(key)) return false;
        machines.remove(key);
        busyMachines.remove(key);
        data.set("machines." + key, null);
        save();
        return true;
    }

    public boolean isSlotMachine(Location loc)  {
        MachineData md = machines.get(locKey(loc));
        return md != null && md.type() == MachineType.SLOTS;
    }

    public boolean isRouletteTable(Location loc) {
        MachineData md = machines.get(locKey(loc));
        return md != null && md.type() == MachineType.ROULETTE;
    }

    public MachineData getMachineData(Location loc) {
        return machines.get(locKey(loc));
    }

    public boolean isMachine(Location loc) { return machines.containsKey(locKey(loc)); }

    // ── Busy tracking ──────────────────────────────────────────────────────

    public boolean isBusy(Location loc)    { return busyMachines.contains(locKey(loc)); }
    public void occupy(Location loc)       { busyMachines.add(locKey(loc)); }
    public void release(Location loc)      { busyMachines.remove(locKey(loc)); }

    public int getMachineCount() { return machines.size(); }

    private static String locKey(Location loc) {
        World world = loc.getWorld();
        String w = (world != null) ? world.getName() : "null";
        return w + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
