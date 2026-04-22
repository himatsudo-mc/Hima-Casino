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
        String title = event.getView().getTitle();
        boolean isMain = HighLowGame.TITLE.equals(title);
        boolean isBet  = HighLowGame.BET_TITLE.equals(title);
        if (!isMain && !isBet) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        HighLowGame game = plugin.getGameManager().getHighLowGame(player);
        if (game == null || game.isFinished()) return;

        if (isMain) game.handleMainClick(event.getSlot());
        else        game.handleBetClick(event.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!HighLowGame.TITLE.equals(title) && !HighLowGame.BET_TITLE.equals(title)) return;

        HighLowGame game = plugin.getGameManager().getHighLowGame(player);
        if (game == null || game.isFinished()) return;

        // Only clean up when main screen is closed; closing bet screen leaves game alive
        if (HighLowGame.TITLE.equals(title)) {
            game.cleanup();
        }
    }
}
