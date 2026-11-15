package com.himacasino;

import com.himacasino.config.CasinoConfig;
import com.himacasino.economy.EconomyService;
import com.himacasino.machine.MachineManager;
import com.himacasino.server.Scheduler;
import com.himacasino.server.SessionManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/** mod 全体の共有状態 (旧プラグインの HimaCasino クラスのアクセサ群に相当)。 */
public final class Casino {

    private static Casino instance;

    public final Path configFile;
    public CasinoConfig config;
    public EconomyService economy;
    public final Scheduler scheduler = new Scheduler();
    public final SessionManager sessions = new SessionManager(this);
    public MachineManager machines;
    public MinecraftServer server;

    private Casino() {
        this.configFile = FabricLoader.getInstance().getConfigDir().resolve("hima-casino.json");
    }

    public static Casino get() {
        if (instance == null) instance = new Casino();
        return instance;
    }

    public void reloadConfig() {
        config = CasinoConfig.load(configFile);
        economy = new EconomyService(config);
    }

    public void saveConfig() {
        config.save(configFile);
    }
}
