package com.himacasino.command;

import com.himacasino.Casino;
import com.himacasino.machine.MachineManager;
import com.himacasino.server.Launcher;
import com.himacasino.server.SignWriter;
import com.himacasino.table.RouletteTable;
import com.himacasino.util.Legacy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** /casino (別名 /hc)。旧 CasinoCommand + CasinoTabCompleter を Brigadier に置き換えたもの。 */
public final class CasinoCommand {

    private static final List<String> HELP_TOPICS =
            List.of("slots", "roulette", "highlow", "horsewheel", "blackjack", "poker");

    private CasinoCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = build("casino");
        dispatcher.register(root);
        dispatcher.register(build("hc"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String name) {
        return CommandManager.literal(name)
                .executes(ctx -> help(ctx, null))
                .then(CommandManager.literal("help")
                        .executes(ctx -> help(ctx, null))
                        .then(CommandManager.argument("topic", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    HELP_TOPICS.forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .executes(ctx -> help(ctx, StringArgumentType.getString(ctx, "topic")))))
                .then(CommandManager.literal("roulette").executes(CasinoCommand::roulette))
                .then(CommandManager.literal("highlow").executes(ctx -> {
                    Launcher.highLow(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("blackjack").executes(ctx -> {
                    Launcher.blackjack(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("poker").executes(ctx -> {
                    Launcher.poker(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                // ── 管理者 ──
                .then(CommandManager.literal("setting").requires(admin())
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(1, 6))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    Casino.get().config.slots.defaultSetting = n;
                                    Casino.get().saveConfig();
                                    msg(ctx, "§aスロットデフォルト設定を §e" + n + " §aに変更しました。");
                                    return 1;
                                })))
                .then(CommandManager.literal("setmachine").requires(admin())
                        .then(CommandManager.literal("roulette").executes(ctx -> setMachine(ctx, MachineManager.MachineType.ROULETTE, 1, 0)))
                        .then(CommandManager.literal("horsewheel").executes(ctx -> setMachine(ctx, MachineManager.MachineType.HORSEWHEEL, 1, 0)))
                        .then(CommandManager.literal("slots")
                                .executes(ctx -> setSlots(ctx, Casino.get().config.slots.minBet, Casino.get().config.slots.defaultSetting))
                                .then(CommandManager.argument("bet", DoubleArgumentType.doubleArg(1))
                                        .executes(ctx -> setSlots(ctx, DoubleArgumentType.getDouble(ctx, "bet"), Casino.get().config.slots.defaultSetting))
                                        .then(CommandManager.argument("setting", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> setSlots(ctx, DoubleArgumentType.getDouble(ctx, "bet"),
                                                        IntegerArgumentType.getInteger(ctx, "setting"))))))
                )
                .then(CommandManager.literal("delmachine").requires(admin()).executes(CasinoCommand::delMachine))
                .then(CommandManager.literal("reload").requires(admin()).executes(ctx -> {
                    Casino.get().reloadConfig();
                    msg(ctx, "§a設定を再読み込みしました。");
                    return 1;
                }));
    }

    private static java.util.function.Predicate<ServerCommandSource> admin() {
        return src -> src.hasPermissionLevel(2);
    }

    private static void msg(CommandContext<ServerCommandSource> ctx, String legacy) {
        ctx.getSource().sendMessage(Legacy.toText(legacy));
    }

    // ── /casino roulette ──────────────────────────────────────────────────

    private static int roulette(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        RouletteTable t = Casino.get().machines.nearestRoulette(p.getServerWorld(), p.getPos(), 20);
        if (t == null) {
            msg(ctx, "§c近くにルーレットテーブルがありません。");
            return 0;
        }
        Launcher.roulette(p, t);
        return 1;
    }

    // ── 管理: マシン設置 / 削除 ───────────────────────────────────────────

    private static BlockPos target(ServerPlayerEntity p) {
        HitResult hit = p.raycast(5.0, 0f, false);
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) return b.getBlockPos();
        return null;
    }

    private static int setMachine(CommandContext<ServerCommandSource> ctx, MachineManager.MachineType type,
                                  int setting, double bet) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        BlockPos pos = target(p);
        if (pos == null) {
            msg(ctx, "§c設置するブロックを見つめてください。(最大5ブロック先)");
            return 0;
        }
        Casino.get().machines.addMachine(p.getServerWorld(), pos, type, setting, bet);
        msg(ctx, String.format("§a%s を §e%s §aとして登録しました。",
                p.getServerWorld().getBlockState(pos).getBlock().getName().getString(), type.name()));
        msg(ctx, type == MachineManager.MachineType.HORSEWHEEL
                ? "§7右クリックでHORSE WHEELが開きます。"
                : "§7プレイヤーが右クリックするとルーレット画面が開きます。");
        return 1;
    }

    private static int setSlots(CommandContext<ServerCommandSource> ctx, double bet, int setting) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        BlockPos pos = target(p);
        ServerWorld w = p.getServerWorld();
        if (pos == null || !(w.getBlockEntity(pos) instanceof SignBlockEntity)) {
            msg(ctx, "§cスロットにする看板を見つめてください。(最大5ブロック先)");
            return 0;
        }
        var cfg = Casino.get().config.slots;
        double clamped = Math.max(cfg.minBet, Math.min(cfg.maxBet, bet));
        Casino.get().machines.addMachine(w, pos, MachineManager.MachineType.SLOTS, setting, clamped);
        SignWriter.format(w, pos, clamped);
        msg(ctx, String.format("§aスロット看板を設置しました。§e設定 %d  ベット: %.0f %s", setting, clamped,
                Casino.get().config.currencySymbol));
        return 1;
    }

    private static int delMachine(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        BlockPos pos = target(p);
        if (pos == null) {
            msg(ctx, "§c削除するブロックを見つめてください。");
            return 0;
        }
        if (Casino.get().machines.removeMachine(p.getServerWorld(), pos)) {
            msg(ctx, "§aマシンを削除しました。");
            return 1;
        }
        msg(ctx, "§cそのブロックにはマシンが登録されていません。");
        return 0;
    }

    // ── help ──────────────────────────────────────────────────────────────

    private static int help(CommandContext<ServerCommandSource> ctx, String topic) {
        boolean admin = ctx.getSource().hasPermissionLevel(2);
        List<String> lines = topic == null ? general(admin) : switch (topic.toLowerCase()) {
            case "slots" -> List.of(
                    "§6§l══════ スロット ══════",
                    "§7スロット看板を右クリックしてプレイ。",
                    "§7シンボル 1〜7 がランダムに揃い、組み合わせで配当が変わる。",
                    "§e配当: §f7-7-7=100x  3揃い=10x  2つの7=5x  7=2x  2揃い=1.5x",
                    "§7設定1〜6 で 7 の出現率が変化。(管理者: /casino setting <1-6>)",
                    "§7(管理者) 看板を見つめて /casino setmachine slots [ベット額] [設定] で設置。",
                    "§6§l══════════════════════");
            case "roulette" -> List.of(
                    "§6§l══════ ルーレット ══════",
                    "§7/casino roulette §fまたはルーレット台を右クリック。",
                    "§7数字 0〜36 にベット。ホイールが回り、当たれば配当。",
                    "§e配当: §f単一数字=35:1  赤/黒=1:1  奇数/偶数=1:1  ダース=2:1",
                    "§7複数の数字に同時ベット可。",
                    "§6§l══════════════════════");
            case "highlow" -> List.of(
                    "§6§l══════ HIGH & LOW ══════",
                    "§7/casino highlow §fで開始。専用画面 (Chromium) でプレイ。",
                    "§7ディーラーのカードより大きい方を 2 枚の伏せ札から選ぶ。",
                    "§e配当: §f勝利=" + Casino.get().config.highlow.winMultiplier + "x",
                    "§6§l══════════════════════");
            case "horsewheel" -> List.of(
                    "§6§l══════ HORSE WHEEL ══════",
                    "§7設置されたホイールを右クリックしてプレイ。",
                    "§7馬の色にコインを賭け、縦回転ホイールが止まった位置で判定。",
                    "§e配当: §f白=2x  §e黄=3x  §b水=5x  §a緑=8x  §c赤=10x  §6金=20x",
                    "§7（管理者: /casino setmachine horsewheel で設置）",
                    "§6§l══════════════════════");
            case "blackjack" -> List.of(
                    "§2§l══════ BLACKJACK ══════",
                    "§7/casino blackjack §fで開始。専用画面 (Chromium) でプレイ。",
                    "§7カードの合計を 21 に近づけ、ディーラーに勝つゲーム。",
                    "§e操作: §fHIT(1枚引く)  STAND(終了)  DOUBLE(倍賭け+1枚)",
                    "§e配当: §fブラックジャック=3:2  通常勝利=1:1  タイ=返金  負け=没収",
                    "§7ディーラーは 17 以上になるまでドロー。",
                    "§2§l══════════════════════");
            case "poker" -> List.of(
                    "§2§l══════ POKER ══════",
                    "§7/casino poker §fで開始。専用画面 (Chromium) でプレイ。",
                    "§7ヘッズアップ・ファイブカードドロー。ハウス相手に手役を競う。",
                    "§7アンティ → 5枚配布 → 好きな枚数を選んで1回だけ交換 → ベッティング → ショーダウン",
                    "§e操作: §fカードをクリックで交換対象を選択 → DRAW で確定",
                    "§e操作: §fBET(賭ける)  CHECK/CALL(様子見/コール)  FOLD(降りる)",
                    "§2§l══════════════════════");
            default -> general(admin);
        };
        for (String l : lines) msg(ctx, l);
        return 1;
    }

    private static List<String> general(boolean admin) {
        var l = new java.util.ArrayList<String>(List.of(
                "§6§l══════ HimaCasino ══════",
                "§7スロット: スロット看板を右クリック",
                "§e/casino roulette§7 – ルーレット画面を開く",
                "§e/casino highlow§7 – HIGH & LOW を遊ぶ",
                "§e/casino blackjack§7 – BLACKJACK を遊ぶ",
                "§e/casino poker§7 – POKER を遊ぶ",
                "§7HORSE WHEEL: 設置されたホイールを右クリック",
                "§e/casino help §f<slots|roulette|highlow|horsewheel|blackjack|poker>"));
        if (admin) {
            l.add("§c§l[管理者]");
            l.add("§c/casino setting §f<1-6>§7 – スロット設定変更");
            l.add("§c/casino setmachine §froulette|horsewheel§7 – マシン設置");
            l.add("§c/casino setmachine slots §f[ベット額] [設定]§7 – 看板をスロットに");
            l.add("§c/casino delmachine§7 – マシン削除");
            l.add("§c/casino reload§7 – 設定再読み込み");
        }
        l.add("§6§l═══════════════════════");
        return l;
    }
}
