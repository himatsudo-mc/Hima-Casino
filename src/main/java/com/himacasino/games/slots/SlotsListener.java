package com.himacasino.games.slots;

import com.himacasino.HimaCasino;
import com.himacasino.manager.MachineManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles sign-based slot machines.
 *
 * Placement format (player writes):
 *   Line 1: [slot]
 *   Line 2: <bet amount>  (optional, default = config min-bet)
 *   Line 3: <setting 1-6> (optional, default = config default-setting)
 *   Line 4: (ignored)
 *
 * After placement the sign is auto-formatted:
 *   Line 1: [SLOT]           (gold, bold)
 *   Line 2: ? | ? | ?        (gray)
 *   Line 3: Bet: <amount>    (yellow)
 *   Line 4: Click to Start!  (green)
 */
public class SlotsListener implements Listener {

    private final HimaCasino plugin;

    public SlotsListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    // ── Sign placement ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
        String raw = event.getLine(0);
        if (raw == null || !raw.trim().equalsIgnoreCase("[slot]")) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§cNo permission to place slot signs.");
            return;
        }

        // Bet amount from line 1
        double bet = plugin.getConfigLoader().getSlotsMinBet();
        String line1 = event.getLine(1);
        if (line1 != null && !line1.isBlank()) {
            try {
                double parsed = Double.parseDouble(line1.trim());
                bet = Math.max(plugin.getConfigLoader().getSlotsMinBet(),
                        Math.min(plugin.getConfigLoader().getSlotsMaxBet(), parsed));
            } catch (NumberFormatException ignored) {}
        }

        // Setting from line 2
        int setting = plugin.getConfigLoader().getSlotsDefaultSetting();
        String line2 = event.getLine(2);
        if (line2 != null && !line2.isBlank()) {
            try {
                int s = Integer.parseInt(line2.trim());
                if (s >= 1 && s <= 6) setting = s;
            } catch (NumberFormatException ignored) {}
        }

        // Format the sign
        event.line(0, Component.text("[SLOT]", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        event.line(1, Component.text("? | ? | ?", NamedTextColor.GRAY));
        event.line(2, Component.text(String.format("Bet: %.0f", bet), NamedTextColor.YELLOW));
        event.line(3, Component.text("Click to Start!", NamedTextColor.GREEN));

        plugin.getMachineManager().addSlotSign(event.getBlock().getLocation(), setting, bet);
        player.sendMessage(String.format("§aSlot sign placed. §eSetting %d  Bet: %.0f %s",
                setting, bet, plugin.getConfigLoader().getCurrencySymbol()));
    }

    // ── Right-click to play ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign)) return;
        if (!plugin.getMachineManager().isSlotMachine(block.getLocation())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        MachineManager.MachineData data = plugin.getMachineManager().getMachineData(block.getLocation());
        if (data == null) return;

        if (plugin.getMachineManager().isBusy(block.getLocation())) {
            player.sendMessage("§cThis slot machine is currently in use!");
            return;
        }
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§cYou already have an active game!");
            return;
        }

        double bet = data.betAmount();
        if (plugin.getEconomyManager().isEnabled()
                && plugin.getEconomyManager().getBalance(player) < bet) {
            player.sendMessage(String.format("§cInsufficient balance! Required: §e%.0f %s",
                    bet, plugin.getConfigLoader().getCurrencySymbol()));
            return;
        }

        plugin.getMachineManager().occupy(block.getLocation());
        SlotsGame game = new SlotsGame(plugin, player, bet, data.setting(), block);
        plugin.getGameManager().registerSlotsGame(player, game);
        game.onStart();
    }

    // ── Protect sign from being broken during a game ───────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.getMachineManager().isSlotMachine(block.getLocation())) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("himacasino.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cNo permission to remove slot signs.");
            return;
        }
        if (plugin.getMachineManager().isBusy(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§cThis slot machine is currently in use!");
            return;
        }

        plugin.getMachineManager().removeMachine(block.getLocation());
        player.sendMessage("§aSlot sign removed.");
    }
}
