package com.himacasino.games.horsewheel;

import com.himacasino.HimaCasino;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class HorseWheelListener implements Listener {

    private final HimaCasino plugin;

    public HorseWheelListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!plugin.getMachineManager().isHorseWheelTable(block.getLocation())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }

        HorseWheelTableDisplay tableDisplay =
                plugin.getMachineManager().getWheelDisplay(block.getLocation());
        if (tableDisplay == null) return;

        if (tableDisplay.isSpinning() || plugin.getMachineManager().isBusy(block.getLocation())) {
            player.sendMessage("§cホイールは今使用中です。しばらくお待ちください。");
            return;
        }

        plugin.getMachineManager().occupy(block.getLocation());
        HorseWheelGame game = new HorseWheelGame(plugin, player, tableDisplay, block.getLocation());
        plugin.getGameManager().registerHorseWheelGame(player, game);
        game.onStart();
        game.openBetUI();
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
        if (game.isTransitioning()) return;

        // Player closed bet UI without spinning → cleanup
        plugin.getServer().getScheduler().runTask(plugin, game::cleanup);
    }
}
