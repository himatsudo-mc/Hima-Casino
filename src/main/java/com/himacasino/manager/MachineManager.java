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
import java.util.Map;

/**
 * Manages placed casino machines (slot machines and roulette tables).
 * Data is persisted in machines.yml.
 */
public class MachineManager {

    public record MachineData(MachineType type, int setting) {}

    public enum MachineType { SLOTS, ROULETTE }

    private final HimaCasino plugin;
    private final File dataFile;
    private FileConfiguration data;

    // Runtime cache: location key → machine data
    private final Map<String, MachineData> machines = new HashMap<>();

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
            if (typeStr == null) continue;
            try {
                MachineType type = MachineType.valueOf(typeStr);
                machines.put(key, new MachineData(type, setting));
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("マシンデータ " + machines.size() + " 台を読み込みました。");
    }

    private void save() {
        for (Map.Entry<String, MachineData> entry : machines.entrySet()) {
            String key = entry.getKey();
            MachineData md = entry.getValue();
            data.set("machines." + key + ".type",    md.type().name());
            data.set("machines." + key + ".setting", md.setting());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("machines.yml を保存できませんでした: " + e.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void addMachine(Location loc, MachineType type, int setting) {
        machines.put(locKey(loc), new MachineData(type, setting));
        save();
    }

    public boolean removeMachine(Location loc) {
        String key = locKey(loc);
        if (!machines.containsKey(key)) return false;
        machines.remove(key);
        data.set("machines." + key, null);
        save();
        return true;
    }

    public boolean isSlotMachine(Location loc) {
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

    public boolean isMachine(Location loc) {
        return machines.containsKey(locKey(loc));
    }

    public int getMachineCount() { return machines.size(); }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String locKey(Location loc) {
        World world = loc.getWorld();
        String worldName = (world != null) ? world.getName() : "null";
        return worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
