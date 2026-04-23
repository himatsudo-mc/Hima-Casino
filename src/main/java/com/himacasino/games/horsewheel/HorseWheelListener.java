package com.himacasino.games.horsewheel;

import com.himacasino.HimaCasino;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class HorseWheelListener implements Listener {

    private final HimaCasino plugin;

    public HorseWheelListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!HorseWheelGame.BET_TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        HorseWheelGame game = plugin.getGameManager().getHorseWheelGame(player);
        if (game == null || game.isFinished()) return;

        game.handleClick(event.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!HorseWheelGame.BET_TITLE.equals(event.getView().getTitle())) return;

        HorseWheelGame game = plugin.getGameManager().getHorseWheelGame(player);
        if (game == null || game.isFinished()) return;
        if (game.isTransitioning()) return; // closing due to spin start — suppress cleanup

        // Player closed the bet UI without spinning → cleanup and despawn
        plugin.getServer().getScheduler().runTask(plugin, game::cleanup);
    }
}
