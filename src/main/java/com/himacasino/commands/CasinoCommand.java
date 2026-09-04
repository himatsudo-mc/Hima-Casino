package com.himacasino.commands;

import com.himacasino.HimaCasino;
import com.himacasino.games.blackjack.BlackjackGame;
import com.himacasino.games.highlow.HighLowGame;
import com.himacasino.games.poker.PokerGame;
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

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "roulette"   -> handleRoulette(player);
            case "highlow"    -> handleHighLow(player);
            case "blackjack"  -> handleBlackjack(player);
            case "poker"      -> handlePoker(player);
            case "setting"    -> handleSetting(player, args);
            case "setmachine" -> handleSetMachine(player, args);
            case "delmachine" -> handleDelMachine(player, args);
            case "help"       -> handleHelp(player, args);
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

    // ── /casino blackjack ─────────────────────────────────────────────────

    private void handleBlackjack(Player player) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }
        BlackjackGame game = new BlackjackGame(plugin, player);
        plugin.getGameManager().registerBlackjackGame(player, game);
        game.onStart();
    }

    // ── /casino poker ──────────────────────────────────────────────────────

    private void handlePoker(Player player) {
        if (plugin.getGameManager().hasActiveGame(player)) {
            player.sendMessage("§c現在進行中のゲームがあります。");
            return;
        }
        PokerGame game = new PokerGame(plugin, player);
        plugin.getGameManager().registerPokerGame(player, game);
        game.onStart();
    }

    // ── /casino help [game] ────────────────────────────────────────────────

    private void handleHelp(Player player, String[] args) {
        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "slots"     -> sendHelpSlots(player);
                case "roulette"  -> sendHelpRoulette(player);
                case "highlow"   -> sendHelpHighLow(player);
                case "horsewheel"-> sendHelpHorseWheel(player);
                case "blackjack" -> sendHelpBlackjack(player);
                case "poker"     -> sendHelpPoker(player);
                default          -> sendHelp(player);
            }
        } else {
            sendHelp(player);
        }
    }

    private void sendHelpSlots(Player player) {
        player.sendMessage("§6§l══════ スロット ══════");
        player.sendMessage("§7[slot]§f の看板を右クリックしてプレイ。");
        player.sendMessage("§7シンボル 1〜7 がランダムに揃い、組み合わせで配当が変わる。");
        player.sendMessage("§e配当: §f7-7-7=100x  3揃い=10x  2つの7=5x  7=2x  2揃い=1.5x");
        player.sendMessage("§7設定1〜6 で 7 の出現率が変化。(管理者: /casino setting <1-6>)");
        player.sendMessage("§6§l══════════════════════");
    }

    private void sendHelpRoulette(Player player) {
        player.sendMessage("§6§l══════ ルーレット ══════");
        player.sendMessage("§7/casino roulette §fまたはルーレット台を右クリック。");
        player.sendMessage("§7数字 0〜36 にベット。ホイールが回り、当たれば配当。");
        player.sendMessage("§e配当: §f単一数字=35:1  赤/黒=1:1  奇数/偶数=1:1");
        player.sendMessage("§7複数の数字に同時ベット可。");
        player.sendMessage("§6§l══════════════════════");
    }

    private void sendHelpHighLow(Player player) {
        player.sendMessage("§6§l══════ HIGH & LOW ══════");
        player.sendMessage("§7/casino highlow §fで開始。インベントリ UI でプレイ。");
        player.sendMessage("§7カードを引き、次のカードが HIGH か LOW かを予想。");
        player.sendMessage("§e配当: §f勝利=" + plugin.getConfigLoader().getHighLowWinMultiplier() + "x");
        player.sendMessage("§7連勝を続けて配当を積み重ねよう！");
        player.sendMessage("§6§l══════════════════════");
    }

    private void sendHelpHorseWheel(Player player) {
        player.sendMessage("§6§l══════ HORSE WHEEL ══════");
        player.sendMessage("§7設置されたホイールを右クリックしてプレイ。");
        player.sendMessage("§7馬の色にコインを賭け、縦回転ホイールが止まった位置で判定。");
        player.sendMessage("§e配当 (馬の色と倍率):");
        player.sendMessage("§f白=2x  §e黄=3x  §b水=5x  §a緑=8x  §c赤=10x  §6金=20x");
        player.sendMessage("§7（管理者: /casino setmachine horsewheel で設置）");
        player.sendMessage("§6§l══════════════════════");
    }

    private void sendHelpBlackjack(Player player) {
        player.sendMessage("§2§l══════ BLACKJACK ══════");
        player.sendMessage("§7/casino blackjack §fで開始。インベントリ UI でプレイ。");
        player.sendMessage("§7カードの合計を 21 に近づけ、ディーラーに勝つゲーム。");
        player.sendMessage("§e操作: §fHIT(1枚引く)  STAND(終了)  DOUBLE(倍賭け+1枚)");
        player.sendMessage("§e配当: §fブラックジャック=3:2  通常勝利=1:1  タイ=返金  負け=没収");
        player.sendMessage("§7ディーラーは 17 以上になるまでドロー。");
        player.sendMessage("§2§l══════════════════════");
    }

    private void sendHelpPoker(Player player) {
        player.sendMessage("§2§l══════ POKER (Sow) ══════");
        player.sendMessage("§7/casino poker §fで開始。インベントリ UI でプレイ。");
        player.sendMessage("§7ヘッズアップ・テキサスホールデム。ハウス相手に手役を競う。");
        player.sendMessage("§e操作: §fBET(賭ける)  CHECK/CALL(様子見/コール)  FOLD(降りる)");
        player.sendMessage("§7アンティを設定 → PRE-FLOP/FLOP/TURN/RIVER と進行し、最後にショーダウン。");
        player.sendMessage("§7先にベットできるのは常にプレイヤー。ディーラーはコール/フォールドのみ(リレイズなし)。");
        player.sendMessage("§2§l══════════════════════");
    }

    // ── /casino setting <1-6> ─────────────────────────────────────────────

    private void handleSetting(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        if (args.length < 2) { player.sendMessage("§e使い方: §f/casino setting <1-6>"); return; }
        try {
            int setting = Integer.parseInt(args[1]);
            if (setting < 1 || setting > 6) throw new NumberFormatException();
            plugin.getConfigLoader().setSlotsDefaultSetting(setting);
            player.sendMessage("§aスロットデフォルト設定を §e" + setting + " §aに変更しました。");
        } catch (NumberFormatException e) {
            player.sendMessage("§c1〜6 の整数を指定してください。");
        }
    }

    // ── /casino setmachine <roulette|horsewheel> ──────────────────────────

    private void handleSetMachine(Player player, String[] args) {
        if (!player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c権限がありません。");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§e使い方: §f/casino setmachine <roulette|horsewheel>");
            return;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null) {
            player.sendMessage("§c設置するブロックを見つめてください。(最大5ブロック先)");
            return;
        }

        MachineManager.MachineType type = switch (args[1].toLowerCase()) {
            case "roulette"   -> MachineManager.MachineType.ROULETTE;
            case "horsewheel" -> MachineManager.MachineType.HORSEWHEEL;
            default -> null;
        };
        if (type == null) {
            player.sendMessage("§c種類は §eroulette §cまたは §ehorsewheel §cを指定してください。");
            return;
        }

        plugin.getMachineManager().addMachine(target.getLocation(), type, 1);
        player.sendMessage(String.format("§a%s を §e%s §aとして登録しました。",
                target.getType().name(), type.name()));
        if (type == MachineManager.MachineType.HORSEWHEEL) {
            player.sendMessage("§7右クリックでHORSE WHEELが開きます。");
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

    // ── Help ───────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§6§l══════ HimaCasino ══════");
        player.sendMessage("§7スロット: §f[slot]§7 看板を右クリック");
        player.sendMessage("§e/casino roulette§7 – ルーレットUIを開く");
        player.sendMessage("§e/casino highlow§7 – HIGH & LOW を遊ぶ");
        player.sendMessage("§e/casino blackjack§7 – BLACKJACK を遊ぶ");
        player.sendMessage("§e/casino poker§7 – POKER (Sow) を遊ぶ");
        player.sendMessage("§7HORSE WHEEL: 設置されたホイールを右クリック");
        player.sendMessage("§e/casino help §f<slots|roulette|highlow|horsewheel|blackjack|poker>");
        if (player.hasPermission("himacasino.admin")) {
            player.sendMessage("§c§l[管理者]");
            player.sendMessage("§c/casino setting §f<1-6>§7 – スロット設定変更");
            player.sendMessage("§c/casino setmachine §froulette|horsewheel§7 – マシン設置");
            player.sendMessage("§c/casino delmachine§7 – マシン削除");
        }
        player.sendMessage("§6§l═══════════════════════");
    }
}
