package com.himacasino.games.blackjack;

import com.himacasino.HimaCasino;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class BlackjackListener implements Listener {

    private final HimaCasino plugin;

    public BlackjackListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        boolean isMain = holder instanceof BlackjackGame.MainHolder;
        boolean isBet  = holder instanceof BlackjackGame.BetHolder;
        if (!isMain && !isBet) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        BlackjackGame game = plugin.getGameManager().getBlackjackGame(player);
        if (game == null || game.isFinished()) return;

        if (isMain) game.handleMainClick(event.getSlot());
        else        game.handleBetClick(event.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        boolean isMain = holder instanceof BlackjackGame.MainHolder;
        boolean isBet  = holder instanceof BlackjackGame.BetHolder;
        if (!isMain && !isBet) return;

        BlackjackGame game = plugin.getGameManager().getBlackjackGame(player);
        if (game == null || game.isFinished()) return;
        if (game.isTransitioning()) return;

        if (isMain) {
            game.cleanup();
        } else {
            // Player ESC'd bet-setting screen — return to main
            game.returnToMain();
        }
    }
}
