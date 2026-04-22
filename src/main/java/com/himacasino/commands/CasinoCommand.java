package com.himacasino.commands;

import com.himacasino.HimaCasino;
import com.himacasino.games.highlow.HighLowGame;
import com.himacasino.games.roulette.RouletteBetUI;
import com.himacasino.games.roulette.RouletteGame;
import com.himacasino.games.slots.SlotsGame;
import com.himacasino.manager.MachineManager;
import org.bukkit.Location;
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
            case "slots"    -> handleSlots(player, args);
            case "roulette" -> handleRoulette(player, args);
            case "highlow"  -> handleHighLow(player, args);
            case "setting"  -> handleSetting(player, args);
            case "setmachine" -> handleSetMachine(player, args);
            case "delmachine" -> handleDelMachine(player, args);
            default         -> sendHelp(player);
        }
        return true;
    }

    // ── /casino slots <bet> ────────────────────────────────────────────────

    private void handleSlots(Player player, String[] args) {
        double bet = parseBet(player, args, 1);
        if (bet < 0) return;

        double min = plugin.getConfigLoader().getSlotsMinBet();
        double max = plugin.getConfigLoader().getSlotsMaxBet();
        if (bet < min || bet > max) {
            player.sendMessage(String.format("§cベット額は §e%.0f §c〜 §e%.0f %s §cにしてください。",
                    min, max, plugin.getConfigLoader().getCurrencySymbol()));
            return;
        }
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }

        int setting = plugin.getConfigLoader().getSlotsDefaultSetting();
        SlotsGame game = new SlotsGame(plugin, player, bet, setting, null);
        plugin.getGameManager().registerSlotsGame(player, game);
        game.onStart();
    }

    // ── /casino roulette [<number|red|black> <bet>|spin|open] ─────────────

    private void handleRoulette(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("open")) {
            // Open the betting GUI
            if (plugin.getGameManager().hasActiveGame(player)) {
                player.sendMessage("§c現在進行中のゲームがあります。");
                return;
            }
            plugin.getRouletteListener().openBetUI(player);
            return;
        }

        if (args[1].equalsIgnoreCase("spin")) {
            RouletteGame game = plugin.getGameManager().getRouletteGame(player);
            if (game == null || game.isFinished()) {
                player.sendMessage("§cルーレットゲームが見つかりません。まずベットしてください。");
                return;
            }
            if (game.getState() != com.himacasino.core.GameBase.GameState.IDLE) {
                player.sendMessage("§cすでにゲームが進行中です。");
                return;
            }
            game.onStart();
            return;
        }

        // /casino roulette <target> <amount>
        double bet = parseBet(player, args, 2);
        if (bet < 0) return;

        double min = plugin.getConfigLoader().getRouletteMinBet();
        double max = plugin.getConfigLoader().getRouletteMaxBet();
        if (bet < min || bet > max) {
            player.sendMessage(String.format("§cベット額は §e%.0f §c〜 §e%.0f %s §cにしてください。",
                    min, max, plugin.getConfigLoader().getCurrencySymbol()));
            return;
        }

        // Get or create a pending game
        RouletteGame game = plugin.getGameManager().getRouletteGame(player);
        if (game == null || game.isFinished()) {
            game = new RouletteGame(plugin, player, player.getLocation());
            plugin.getGameManager().registerRouletteGame(player, game);
        }
        if (game.getState() == com.himacasino.core.GameBase.GameState.RUNNING) {
            player.sendMessage("§cルーレットはすでに回転中です。");
            return;
        }

        String target = args[1].toLowerCase();
        switch (target) {
            case "red"   -> game.placeBetOnColor("red",   bet);
            case "black" -> game.placeBetOnColor("black", bet);
            default -> {
                try {
                    int number = Integer.parseInt(target);
                    if (!game.placeBetOnNumber(number, bet)) {
                        player.sendMessage("§c無効な数字です (0〜36)。");
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c無効なベット対象です。数字 (0-36)、red、black を指定してください。");
                }
            }
        }
    }

    // ── /casino highlow ────────────────────────────────────────────────────

    private void handleHighLow(Player player, String[] args) {
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

    // ── /casino setmachine <slots|roulette> [setting] ─────────────────────

    private void handleSetMachine(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/casino setmachine <slots|roulette> [setting(1-6)]");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§c設置するブロックを見つめてください。(最大5ブロック先)");
            return;
        }

        MachineManager.MachineType type;
        try {
            type = MachineManager.MachineType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c種類は §eslots §cまたは §eroulette §cを指定してください。");
            return;
        }

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
        if (type == MachineManager.MachineType.SLOTS) {
            player.sendMessage("§7プレイヤーが右クリックするとスロットマシンが起動します。");
        } else {
            player.sendMessage("§7プレイヤーが右クリックするとルーレットUIが開きます。");
        }
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

    // ── Helpers ────────────────────────────────────────────────────────────

    private double parseBet(Player player, String[] args, int index) {
        if (args.length <= index) {
            player.sendMessage("§cベット額を指定してください。");
            return -1;
        }
        try {
            double bet = Double.parseDouble(args[index]);
            if (bet <= 0) throw new NumberFormatException();
            return bet;
        } catch (NumberFormatException e) {
            player.sendMessage("§c有効なベット額を入力してください。");
            return -1;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l══════ HimaCasino ══════");
        player.sendMessage("§e/casino slots §f<ベット額>§7 – スロットを遊ぶ");
        player.sendMessage("§e/casino roulette §f[open]§7 – ルーレットUIを開く");
        player.sendMessage("§e/casino roulette §f<数字|red|black> <額>§7 – ルーレットベット");
        player.sendMessage("§e/casino roulette spin§7 – ルーレットを回す");
        player.sendMessage("§e/casino highlow§7 – HIGH & LOW を遊ぶ (ベット額はUI内で設定)");
        if (player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c§l[管理者]");
            player.sendMessage("§c/casino setting §f<1-6>§7 – スロット設定変更");
            player.sendMessage("§c/casino setmachine §f<slots|roulette> [setting]§7 – マシン設置");
            player.sendMessage("§c/casino delmachine§7 – マシン削除");
        }
        player.sendMessage("§6§l═══════════════════════");
    }
}
