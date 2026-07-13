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

    // ── Right-click roulette table block ──────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player     player = event.getPlayer();
        MachineManager mm = plugin.getMachineManager();
        if (!mm.isRouletteTable(block.getLocation())) return;

        event.setCancelled(true);

        RouletteTableDisplay td = mm.getTableDisplay(block.getLocation());
        Location tableCenter = (td != null) ? td.getCenter()
                : block.getLocation().clone().add(0.5, 0, 0.5);

        openBetUI(player, tableCenter);
    }

    /** Opens the bet UI at the nearest registered roulette table (command-triggered). */
    public void openBetUI(Player player) {
        RouletteTableDisplay td = plugin.getMachineManager()
                .getNearestRouletteDisplay(player.getLocation(), 20);
        if (td == null) {
            player.sendMessage("§c近くにルーレットテーブルがありません。");
            return;
        }
        openBetUI(player, td.getCenter());
    }

    public void openBetUI(Player player, Location tableCenter) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }
        RouletteTableDisplay td = plugin.getMachineManager().getTableDisplay(tableCenter);
        if (td != null && td.isGameActive()) {
            player.sendMessage("§cこのテーブルでは現在別のゲームが進行中です。");
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

        RouletteGame game = ui.getGame();
        if (game != null && game.getState() == com.himacasino.core.GameBase.GameState.IDLE) {
            double refund = game.getTotalBet();
            if (refund > 0 && plugin.getEconomyManager().isEnabled()) {
                plugin.getEconomyManager().deposit(player, refund);
                player.sendMessage(String.format("§7ルーレットUIを閉じました。§e%.0f %s §7を返金します。",
                        refund, plugin.getConfigLoader().getCurrencySymbol()));
            }
            RouletteTableDisplay td = plugin.getMachineManager().getTableDisplay(ui.getTableCenter());
            if (td != null) td.clearBetCoins();
            plugin.getGameManager().removeRouletteGame(player);
        }
    }
}
