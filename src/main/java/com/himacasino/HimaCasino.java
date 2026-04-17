package com.himacasino;

import com.himacasino.commands.CasinoCommand;
import com.himacasino.commands.CasinoTabCompleter;
import com.himacasino.core.ConfigLoader;
import com.himacasino.core.DisplayManager;
import com.himacasino.core.EconomyManager;
import com.himacasino.core.GameManager;
import com.himacasino.games.highlow.HighLowListener;
import com.himacasino.games.roulette.RouletteListener;
import com.himacasino.games.slots.SlotsListener;
import com.himacasino.manager.MachineManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class HimaCasino extends JavaPlugin {

    private static HimaCasino instance;

    private ConfigLoader configLoader;
    private EconomyManager economyManager;
    private DisplayManager displayManager;
    private GameManager gameManager;
    private MachineManager machineManager;
    private RouletteListener rouletteListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        // Core systems
        configLoader   = new ConfigLoader(this);
        economyManager = new EconomyManager(this);
        displayManager = new DisplayManager(this);
        gameManager    = new GameManager();
        machineManager = new MachineManager(this);

        // Economy (Vault soft-depend)
        if (economyManager.setup()) {
            getLogger().info("Vault と連携しました。経済機能が有効です。");
        } else {
            getLogger().warning("Vault が見つかりませんでした。経済機能は無効です。");
        }

        // Commands
        CasinoCommand executor = new CasinoCommand(this);
        CasinoTabCompleter completer = new CasinoTabCompleter();
        PluginCommand cmd = getCommand("casino");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(completer);
        }

        // Listeners
        rouletteListener = new RouletteListener(this);
        getServer().getPluginManager().registerEvents(new SlotsListener(this),    this);
        getServer().getPluginManager().registerEvents(rouletteListener,            this);
        getServer().getPluginManager().registerEvents(new HighLowListener(this),   this);

        getLogger().info("HimaCasino v" + getDescription().getVersion() + " が有効になりました！");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.cleanupAll();
        if (displayManager != null) displayManager.cleanup();
        getLogger().info("HimaCasino が無効になりました。");
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public static HimaCasino getInstance() { return instance; }

    public ConfigLoader getConfigLoader()     { return configLoader; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public GameManager getGameManager()       { return gameManager; }
    public MachineManager getMachineManager() { return machineManager; }
    public RouletteListener getRouletteListener() { return rouletteListener; }
}
