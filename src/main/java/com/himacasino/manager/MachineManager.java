package com.himacasino.manager;

import com.himacasino.HimaCasino;
import com.himacasino.games.horsewheel.HorseWheelTableDisplay;
import com.himacasino.games.roulette.RouletteTableDisplay;
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

    public record MachineData(MachineType type, int setting, double betAmount) {}
    public enum MachineType { SLOTS, ROULETTE, HORSEWHEEL }

    private final HimaCasino plugin;
    private final File        dataFile;
    private FileConfiguration data;

    private final Map<String, MachineData>          machines      = new HashMap<>();
    private final Set<String>                        busyMachines  = new HashSet<>();
    private final Map<String, RouletteTableDisplay>  tableDisplays = new HashMap<>();
    private final Map<String, HorseWheelTableDisplay> wheelDisplays = new HashMap<>();

    public MachineManager(HimaCasino plugin) {
        this.plugin   = plugin;
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
            String typeStr   = section.getString(key + ".type");
            int    setting   = section.getInt(key + ".setting", 1);
            double betAmount = section.getDouble(key + ".betAmount", 0);
            if (typeStr == null) continue;
            try {
                MachineType type = MachineType.valueOf(typeStr);
                machines.put(key, new MachineData(type, setting, betAmount));
                if (type == MachineType.ROULETTE) {
                    Location loc = keyToLocation(key);
                    if (loc != null) spawnTableDisplay(key, loc);
                } else if (type == MachineType.HORSEWHEEL) {
                    Location loc = keyToLocation(key);
                    if (loc != null) spawnWheelDisplay(key, loc.clone().add(0, 2.0, 0));
                }
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
        catch (IOException e) {
            plugin.getLogger().warning("machines.yml を保存できませんでした: " + e.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void addMachine(Location loc, MachineType type, int setting, double betAmount) {
        String key = locKey(loc);
        machines.put(key, new MachineData(type, setting, betAmount));
        save();
        if (type == MachineType.ROULETTE) {
            spawnTableDisplay(key, loc.clone().add(0.5, 0, 0.5));
        } else if (type == MachineType.HORSEWHEEL) {
            spawnWheelDisplay(key, loc.clone().add(0.5, 2.0, 0.5));
        }
    }

    public void addMachine(Location loc, MachineType type, int setting) {
        addMachine(loc, type, setting, 0);
    }

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
        RouletteTableDisplay td = tableDisplays.remove(key);
        if (td != null) td.despawn();
        HorseWheelTableDisplay wd = wheelDisplays.remove(key);
        if (wd != null) wd.despawn();
        return true;
    }

    // ── Type checks ────────────────────────────────────────────────────────

    public boolean isSlotMachine(Location loc) {
        MachineData md = machines.get(locKey(loc));
        return md != null && md.type() == MachineType.SLOTS;
    }

    public boolean isRouletteTable(Location loc) {
        MachineData md = machines.get(locKey(loc));
        return md != null && md.type() == MachineType.ROULETTE;
    }

    public boolean isHorseWheelTable(Location loc) {
        MachineData md = machines.get(locKey(loc));
        return md != null && md.type() == MachineType.HORSEWHEEL;
    }

    public MachineData getMachineData(Location loc) { return machines.get(locKey(loc)); }

    // ── Display accessors ──────────────────────────────────────────────────

    public RouletteTableDisplay getTableDisplay(Location loc) {
        RouletteTableDisplay td = tableDisplays.get(locKey(loc));
        if (td != null) return td;
        Location floor = new Location(loc.getWorld(),
                Math.floor(loc.getX()), loc.getBlockY(), Math.floor(loc.getZ()));
        return tableDisplays.get(locKey(floor));
    }

    public HorseWheelTableDisplay getWheelDisplay(Location loc) {
        HorseWheelTableDisplay wd = wheelDisplays.get(locKey(loc));
        if (wd != null) return wd;
        Location floor = new Location(loc.getWorld(),
                Math.floor(loc.getX()), loc.getBlockY(), Math.floor(loc.getZ()));
        return wheelDisplays.get(locKey(floor));
    }

    // ── Nearest display helpers ────────────────────────────────────────────

    public RouletteTableDisplay getNearestRouletteDisplay(Location loc, double maxDist) {
        RouletteTableDisplay nearest = null;
        double bestDistSq = maxDist * maxDist;
        for (RouletteTableDisplay td : tableDisplays.values()) {
            Location c = td.getCenter();
            if (!c.getWorld().equals(loc.getWorld())) continue;
            double d = c.distanceSquared(loc);
            if (d < bestDistSq) { bestDistSq = d; nearest = td; }
        }
        return nearest;
    }

    // ── Busy tracking ──────────────────────────────────────────────────────

    public boolean isBusy(Location loc)  { return busyMachines.contains(locKey(loc)); }
    public void occupy(Location loc)     { busyMachines.add(locKey(loc)); }
    public void release(Location loc)    { busyMachines.remove(locKey(loc)); }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public void cleanup() {
        tableDisplays.values().forEach(RouletteTableDisplay::despawn);
        tableDisplays.clear();
        wheelDisplays.values().forEach(HorseWheelTableDisplay::despawn);
        wheelDisplays.clear();
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private void spawnTableDisplay(String key, Location center) {
        RouletteTableDisplay old = tableDisplays.remove(key);
        if (old != null) old.despawn();
        if (center.getWorld() == null) return;
        tableDisplays.put(key, new RouletteTableDisplay(plugin, center));
    }

    private void spawnWheelDisplay(String key, Location center) {
        HorseWheelTableDisplay old = wheelDisplays.remove(key);
        if (old != null) old.despawn();
        if (center.getWorld() == null) return;
        wheelDisplays.put(key, new HorseWheelTableDisplay(plugin, center));
    }

    private static String locKey(Location loc) {
        World world = loc.getWorld();
        String w = (world != null) ? world.getName() : "null";
        return w + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private static Location keyToLocation(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        try {
            World world = org.bukkit.Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x + 0.5, y, z + 0.5);
        } catch (NumberFormatException e) { return null; }
    }
}
