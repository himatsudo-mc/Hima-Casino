package com.himacasino.machine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.himacasino.Casino;
import com.himacasino.table.HorseWheelTable;
import com.himacasino.table.RouletteTable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 設置済みマシン (スロット看板 / ルーレット卓 / ホースホイール) の登録・永続化・3D 表示の管理。 */
public class MachineManager {

    public enum MachineType { SLOTS, ROULETTE, HORSEWHEEL }

    public static class MachineData {
        public MachineType type;
        public int setting = 1;
        public double betAmount = 0;

        public MachineData() {}

        public MachineData(MachineType type, int setting, double betAmount) {
            this.type = type;
            this.setting = setting;
            this.betAmount = betAmount;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, MachineData>>() {}.getType();

    private final Casino casino;
    private final Path file;
    private final MinecraftServer server;
    private final Map<String, MachineData> machines = new LinkedHashMap<>();
    private final Set<String> busy = new HashSet<>();
    private final Map<String, RouletteTable> roulettes = new HashMap<>();
    private final Map<String, HorseWheelTable> wheels = new HashMap<>();

    public MachineManager(Casino casino, MinecraftServer server) {
        this.casino = casino;
        this.server = server;
        this.file = FabricLoader.getInstance().getConfigDir().resolve("hima-casino-machines.json");
        load();
    }

    // ── keys ──────────────────────────────────────────────────────────────

    public static String key(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "," + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static BlockPos posOf(String key) {
        String[] p = key.split(",");
        return new BlockPos(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    private static String dimOf(String key) {
        return key.substring(0, key.indexOf(','));
    }

    private ServerWorld worldOf(String key) {
        String dim = dimOf(key);
        for (ServerWorld w : server.getWorlds()) {
            if (w.getRegistryKey().getValue().toString().equals(dim)) return w;
        }
        return null;
    }

    // ── persistence ───────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, MachineData> loaded = GSON.fromJson(r, FILE_TYPE);
            if (loaded != null) {
                loaded.forEach((k, v) -> { if (v != null && v.type != null) machines.put(k, v); });
            }
        } catch (Exception e) {
            System.err.println("[HimaCasino] マシンデータを読み込めませんでした: " + e.getMessage());
        }
        System.out.println("[HimaCasino] マシンデータ " + machines.size() + " 台を読み込みました。");
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(machines, FILE_TYPE, w);
            }
        } catch (IOException e) {
            System.err.println("[HimaCasino] マシンデータを保存できませんでした: " + e.getMessage());
        }
    }

    // ── public API ────────────────────────────────────────────────────────

    public void addMachine(ServerWorld world, BlockPos pos, MachineType type, int setting, double betAmount) {
        String k = key(world, pos);
        despawn(k);
        machines.put(k, new MachineData(type, setting, betAmount));
        save();
        spawn(world, k, machines.get(k));
    }

    public boolean removeMachine(ServerWorld world, BlockPos pos) {
        String k = key(world, pos);
        if (machines.remove(k) == null) return false;
        busy.remove(k);
        despawn(k);
        save();
        return true;
    }

    public MachineData get(ServerWorld world, BlockPos pos) {
        return machines.get(key(world, pos));
    }

    public boolean is(ServerWorld world, BlockPos pos, MachineType type) {
        MachineData d = get(world, pos);
        return d != null && d.type == type;
    }

    public RouletteTable rouletteAt(ServerWorld world, BlockPos pos) {
        return roulettes.get(key(world, pos));
    }

    public HorseWheelTable wheelAt(ServerWorld world, BlockPos pos) {
        return wheels.get(key(world, pos));
    }

    public RouletteTable nearestRoulette(ServerWorld world, Vec3d from, double maxDist) {
        RouletteTable best = null;
        double bestSq = maxDist * maxDist;
        for (RouletteTable t : roulettes.values()) {
            if (t.world() != world) continue;
            double d = t.center().squaredDistanceTo(from);
            if (d < bestSq) { bestSq = d; best = t; }
        }
        return best;
    }

    public boolean isBusy(ServerWorld world, BlockPos pos) { return busy.contains(key(world, pos)); }

    public void occupy(ServerWorld world, BlockPos pos) { busy.add(key(world, pos)); }

    public void release(ServerWorld world, BlockPos pos) { busy.remove(key(world, pos)); }

    // ── 3D display lifecycle ──────────────────────────────────────────────

    /** チャンクが読み込まれたとき、そこにある卓の表示を出す */
    public void onChunkLoad(ServerWorld world, ChunkPos chunk) {
        for (var e : machines.entrySet()) {
            MachineData d = e.getValue();
            if (d.type == MachineType.SLOTS) continue;
            BlockPos p = posOf(e.getKey());
            if (!new ChunkPos(p).equals(chunk)) continue;
            if (worldOf(e.getKey()) != world) continue;
            String k = e.getKey();
            casino.scheduler.later(2, () -> {
                if (!roulettes.containsKey(k) && !wheels.containsKey(k)) spawn(world, k, d);
            });
        }
    }

    public void onChunkUnload(ServerWorld world, ChunkPos chunk) {
        for (String k : new java.util.ArrayList<>(machines.keySet())) {
            BlockPos p = posOf(k);
            if (new ChunkPos(p).equals(chunk) && worldOf(k) == world) despawn(k);
        }
    }

    private void spawn(ServerWorld world, String k, MachineData d) {
        BlockPos p = posOf(k);
        if (d.type == MachineType.ROULETTE) {
            roulettes.put(k, new RouletteTable(casino, world, new Vec3d(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5)));
        } else if (d.type == MachineType.HORSEWHEEL) {
            wheels.put(k, new HorseWheelTable(casino, world, new Vec3d(p.getX() + 0.5, p.getY() + 2.0, p.getZ() + 0.5)));
        }
    }

    private void despawn(String k) {
        RouletteTable r = roulettes.remove(k);
        if (r != null) r.despawn();
        HorseWheelTable w = wheels.remove(k);
        if (w != null) w.despawn();
    }

    public void cleanup() {
        roulettes.values().forEach(RouletteTable::despawn);
        roulettes.clear();
        wheels.values().forEach(HorseWheelTable::despawn);
        wheels.clear();
    }
}
