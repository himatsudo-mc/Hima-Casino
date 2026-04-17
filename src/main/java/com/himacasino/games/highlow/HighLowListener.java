package com.himacasino.games.highlow;

import com.himacasino.HimaCasino;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class HighLowListener implements Listener {

    private final HimaCasino plugin;

    public HighLowListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!HighLowGame.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        HighLowGame game = plugin.getGameManager().getHighLowGame(player);
        if (game == null || game.isFinished()) return;

        game.onCardChosen(event.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!HighLowGame.TITLE.equals(event.getView().getTitle())) return;

        HighLowGame game = plugin.getGameManager().getHighLowGame(player);
        if (game == null || game.isFinished()) return;

        // If player closed mid-game, treat as loss
        if (game.isWaitingForChoice()) {
            game.onLoss();
        }
    }
}
