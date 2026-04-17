package com.himacasino.core;

import com.himacasino.HimaCasino;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public abstract class GameBase {

    protected final HimaCasino plugin;
    protected final Player player;
    protected double betAmount;
    protected GameState state;
    protected BukkitTask tickTask;

    public enum GameState {
        IDLE, RUNNING, WIN, LOSS, FINISHED
    }

    protected GameBase(HimaCasino plugin, Player player, double betAmount) {
        this.plugin = plugin;
        this.player = player;
        this.betAmount = betAmount;
        this.state = GameState.IDLE;
    }

    public abstract void onStart();
    public abstract void onTick();
    public abstract void onWin(double multiplier);
    public abstract void onLoss();
    public abstract void cleanup();

    protected boolean chargeBet() {
        EconomyManager eco = plugin.getEconomyManager();
        if (!eco.isEnabled()) return true;
        if (eco.getBalance(player) < betAmount) {
            player.sendMessage("§c残高が不足しています。");
            return false;
        }
        eco.withdraw(player, betAmount);
        return true;
    }

    protected void payWinnings(double multiplier) {
        EconomyManager eco = plugin.getEconomyManager();
        double winnings = betAmount * multiplier;
        if (eco.isEnabled()) eco.deposit(player, winnings);
        player.sendMessage(String.format("§a§l+%.0f %s 獲得！ (x%.1f)",
                winnings, plugin.getConfigLoader().getCurrencySymbol(), multiplier));
    }

    protected void startTickTask(long intervalTicks) {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::onTick, 0L, intervalTicks);
    }

    protected void stopTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public boolean isFinished() {
        return state == GameState.FINISHED;
    }

    public Player getPlayer() { return player; }
    public GameState getState() { return state; }
    public double getBetAmount() { return betAmount; }
}
