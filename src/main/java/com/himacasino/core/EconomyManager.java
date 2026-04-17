package com.himacasino.core;

import com.himacasino.HimaCasino;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final HimaCasino plugin;
    private Economy economy;
    private boolean enabled = false;

    public EconomyManager(HimaCasino plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        enabled = true;
        return true;
    }

    public boolean isEnabled() { return enabled; }

    public double getBalance(Player player) {
        if (!enabled) return Double.MAX_VALUE;
        return economy.getBalance(player);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!enabled) return true;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
