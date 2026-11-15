package com.himacasino.server;

import com.himacasino.Casino;
import com.himacasino.game.BlackjackGame;
import com.himacasino.game.HighLowGame;
import com.himacasino.game.HorseWheelGame;
import com.himacasino.game.PokerGame;
import com.himacasino.game.RouletteGame;
import com.himacasino.game.SlotsGame;
import com.himacasino.machine.MachineManager;
import com.himacasino.table.HorseWheelTable;
import com.himacasino.table.RouletteTable;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** ゲームの開始処理 (コマンド / ブロック右クリックの共通入口)。旧各 Listener・Command のゲーム生成部分。 */
public final class Launcher {

    private Launcher() {}

    private static ServerHost host(ServerPlayerEntity p) {
        return new ServerHost(Casino.get(), p);
    }

    public static void blackjack(ServerPlayerEntity p) {
        ServerHost h = host(p);
        BlackjackGame g = Casino.get().sessions.start(p, true, BlackjackGame::new, h);
        if (g != null) g.open();
    }

    public static void highLow(ServerPlayerEntity p) {
        ServerHost h = host(p);
        HighLowGame g = Casino.get().sessions.start(p, true, HighLowGame::new, h);
        if (g != null) g.open();
    }

    public static void poker(ServerPlayerEntity p) {
        ServerHost h = host(p);
        PokerGame g = Casino.get().sessions.start(p, true, PokerGame::new, h);
        if (g != null) g.open();
    }

    public static void roulette(ServerPlayerEntity p, RouletteTable table) {
        ServerHost h = host(p).soundOrigin(table::center);
        if (table.isActive()) {
            h.message("§cこのテーブルでは現在別のゲームが進行中です。");
            return;
        }
        RouletteGame g = Casino.get().sessions.start(p, true, host -> new RouletteGame(host, table), h);
        if (g != null) g.open();
    }

    public static void horseWheel(ServerPlayerEntity p, ServerWorld world, BlockPos pos, HorseWheelTable wheel) {
        MachineManager mm = Casino.get().machines;
        ServerHost h = host(p).soundOrigin(wheel::center).onEnd(() -> mm.release(world, pos));
        if (wheel.isSpinning() || mm.isBusy(world, pos)) {
            h.message("§cホイールは今使用中です。しばらくお待ちください。");
            return;
        }
        HorseWheelGame g = Casino.get().sessions.start(p, true, host -> new HorseWheelGame(host, wheel), h);
        if (g != null) {
            mm.occupy(world, pos);
            g.open();
        }
    }

    public static void slots(ServerPlayerEntity p, ServerWorld world, BlockPos pos, MachineManager.MachineData data) {
        MachineManager mm = Casino.get().machines;
        ServerHost h = host(p).soundOrigin(() -> net.minecraft.util.math.Vec3d.ofCenter(pos))
                .onEnd(() -> mm.release(world, pos));
        if (mm.isBusy(world, pos)) {
            h.message("§cこのスロットは使用中です！");
            return;
        }
        double bet = data.betAmount;
        if (h.economyEnabled() && h.balance() < bet) {
            h.message(String.format("§c残高が不足しています！ 必要額: §e%.0f %s", bet, Casino.get().config.currencySymbol));
            return;
        }
        SlotsGame g = Casino.get().sessions.start(p, false,
                host -> new SlotsGame(host, bet, data.setting, new SignWriter(world, pos)), h);
        if (g != null) {
            mm.occupy(world, pos);
            g.open(); // = start()
        }
    }
}
