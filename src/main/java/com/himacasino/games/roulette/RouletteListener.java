package com.himacasino.games.roulette;

import com.himacasino.HimaCasino;
import com.himacasino.manager.MachineManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RouletteListener implements Listener {

    private final HimaCasino plugin;
    private final Map<UUID, RouletteBetUI> openUIs = new HashMap<>();

    public RouletteListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    // ── World interaction: right-click roulette table block ────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        MachineManager mm = plugin.getMachineManager();
        if (!mm.isRouletteTable(block.getLocation())) return;

        event.setCancelled(true);
        openBetUI(player, block.getLocation().add(0.5, 0, 0.5));
    }

    public void openBetUI(Player player) {
        openBetUI(player, player.getLocation());
    }

    public void openBetUI(Player player, Location tableCenter) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }
        RouletteBetUI ui = new RouletteBetUI(plugin, player, tableCenter);
        ui.open();
        openUIs.put(player.getUniqueId(), ui);
    }

    // ── Inventory interaction ──────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!RouletteBetUI.TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        RouletteBetUI ui = openUIs.get(player.getUniqueId());
        if (ui == null) return;

        ui.handleClick(event.getSlot());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!RouletteBetUI.TITLE.equals(event.getView().getTitle())) return;

        RouletteBetUI ui = openUIs.remove(player.getUniqueId());
        if (ui == null) return;

        // If the game hasn't started yet (no active running game), clean up the pending game
        RouletteGame game = ui.getGame();
        if (game != null && game.getState() == com.himacasino.core.GameBase.GameState.IDLE) {
            // Refund any bets placed
            double refund = game.getTotalBet();
            if (refund > 0 && plugin.getEconomyManager().isEnabled()) {
                plugin.getEconomyManager().deposit(player, refund);
                player.sendMessage(String.format("§7ルーレットUIを閉じました。§e%.0f %s §7を返金します。",
                        refund, plugin.getConfigLoader().getCurrencySymbol()));
            }
            plugin.getGameManager().removeRouletteGame(player);
        }
    }

    public void registerUI(Player player, RouletteBetUI ui) {
        openUIs.put(player.getUniqueId(), ui);
    }
}
