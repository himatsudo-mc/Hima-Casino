package com.himacasino.commands;

import com.himacasino.HimaCasino;
import com.himacasino.games.highlow.HighLowGame;
import com.himacasino.manager.MachineManager;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CasinoCommand implements CommandExecutor {

    private final HimaCasino plugin;

    public CasinoCommand(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cプレイヤーのみ使用できます。");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "roulette"   -> handleRoulette(player);
            case "highlow"    -> handleHighLow(player);
            case "setting"    -> handleSetting(player, args);
            case "setmachine" -> handleSetMachine(player, args);
            case "delmachine" -> handleDelMachine(player, args);
            default           -> sendHelp(player);
        }
        return true;
    }

    // ── /casino roulette ───────────────────────────────────────────────────

    private void handleRoulette(Player player) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }
        plugin.getRouletteListener().openBetUI(player);
    }

    // ── /casino highlow ────────────────────────────────────────────────────

    private void handleHighLow(Player player) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }

        HighLowGame game = new HighLowGame(plugin, player);
        plugin.getGameManager().registerHighLowGame(player, game);
        game.onStart();
    }

    // ── /casino setting <1-6> ─────────────────────────────────────────────

    private void handleSetting(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/casino setting <1-6>");
            return;
        }
        try {
            int setting = Integer.parseInt(args[1]);
            if (setting < 1 || setting > 6) throw new NumberFormatException();
            plugin.getConfigLoader().setSlotsDefaultSetting(setting);
            player.sendMessage("§aスロットデフォルト設定を §e" + setting + " §aに変更しました。");
        } catch (NumberFormatException e) {
            player.sendMessage("§c1〜6 の整数を指定してください。");
        }
    }

    // ── /casino setmachine roulette [setting] ─────────────────────────────

    private void handleSetMachine(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/casino setmachine roulette");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§c設置するブロックを見つめてください。(最大5ブロック先)");
            return;
        }

        if (!args[1].equalsIgnoreCase("roulette")) {
            player.sendMessage("§c種類は §eroulette §cを指定してください。");
            return;
        }
        MachineManager.MachineType type = MachineManager.MachineType.ROULETTE;

        int setting = 1;
        if (args.length >= 3) {
            try {
                setting = Integer.parseInt(args[2]);
                if (setting < 1 || setting > 6) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage("§cスロット設定は 1〜6 を指定してください。");
                return;
            }
        }

        plugin.getMachineManager().addMachine(target.getLocation(), type, setting);
        player.sendMessage(String.format("§a%s を §e%s §aとして登録しました。",
                target.getType().name(), type.name()));
        player.sendMessage("§7プレイヤーが右クリックするとルーレットUIが開きます。");
    }

    // ── /casino delmachine ────────────────────────────────────────────────

    private void handleDelMachine(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§c削除するブロックを見つめてください。");
            return;
        }

        if (plugin.getMachineManager().removeMachine(target.getLocation())) {
            player.sendMessage("§aマシンを削除しました。");
        } else {
            player.sendMessage("§cそのブロックにはマシンが登録されていません。");
        }
    }

    // ── Help ───────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§6§l══════ HimaCasino ══════");
        player.sendMessage("§7スロット: §f[slot]§7 看板を右クリック");
        player.sendMessage("§e/casino roulette§7 – ルーレットUIを開く");
        player.sendMessage("§e/casino highlow§7 – HIGH & LOW を遊ぶ");
        if (player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c§l[管理者]");
            player.sendMessage("§c/casino setting §f<1-6>§7 – スロット設定変更");
            player.sendMessage("§c/casino setmachine §froulette§7 – ルーレットマシン設置");
            player.sendMessage("§c/casino delmachine§7 – マシン削除");
        }
        player.sendMessage("§6§l═══════════════════════");
    }
}
