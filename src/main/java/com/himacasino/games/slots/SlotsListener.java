package com.himacasino.games.slots;

import com.himacasino.HimaCasino;
import com.himacasino.manager.MachineManager;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SlotsListener implements Listener {

    private final HimaCasino plugin;

    public SlotsListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        MachineManager mm = plugin.getMachineManager();
        if (!mm.isSlotMachine(block.getLocation())) return;

        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        MachineManager.MachineData data = mm.getMachineData(block.getLocation());
        int setting = (data != null) ? data.setting() : plugin.getConfigLoader().getSlotsDefaultSetting();

        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。終了してからお試しください。");
            return;
        }

        // Open bet selection UI
        openBetUI(player, block, setting);
    }

    private void openBetUI(Player player, Block block, int setting) {
        // Show bet options via chat (simple implementation)
        player.sendMessage("§6§l═══ スロットマシン ═══");
        player.sendMessage(String.format("§7設定: §e%d  §7最小ベット: §e%.0f  §7最大ベット: §e%.0f",
                setting,
                plugin.getConfigLoader().getSlotsMinBet(),
                plugin.getConfigLoader().getSlotsMaxBet()));
        player.sendMessage("§e/casino slots <ベット額> §7でプレイ開始！");
        player.sendMessage(String.format("§7例: §f/casino slots %.0f",
                plugin.getConfigLoader().getSlotsMinBet()));
    }
}
