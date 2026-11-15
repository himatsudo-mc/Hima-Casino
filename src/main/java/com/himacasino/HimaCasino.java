package com.himacasino;

import com.himacasino.command.CasinoCommand;
import com.himacasino.machine.MachineManager;
import com.himacasino.network.ActionPayload;
import com.himacasino.network.UiPayload;
import com.himacasino.server.Launcher;
import com.himacasino.table.Displays;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class HimaCasino implements ModInitializer {

    public static final String MOD_ID = "hima-casino";

    @Override
    public void onInitialize() {
        Casino casino = Casino.get();
        casino.reloadConfig();

        // ネットワーク
        PayloadTypeRegistry.playS2C().register(UiPayload.ID, UiPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                casino.sessions.onAction(context.player(), payload.json()));

        // コマンド
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> CasinoCommand.register(dispatcher));

        // ライフサイクル
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            casino.server = server;
            casino.reloadConfig();
            casino.machines = new MachineManager(casino, server);
            System.out.println("[HimaCasino] " + casino.economy.describe());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            casino.sessions.shutdownAll();
            if (casino.machines != null) casino.machines.cleanup();
            casino.scheduler.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> casino.scheduler.tick());

        // 3D 卓の表示はチャンクの読み込みに合わせて出し入れする
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (casino.machines != null) casino.machines.onChunkLoad(world, chunk.getPos());
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            if (casino.machines != null) casino.machines.onChunkUnload(world, chunk.getPos());
        });
        // クラッシュ等でディスクに残ったこの mod の Display を掃除する
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(Displays.TAG) && !Displays.isTracked(entity.getUuid())) {
                casino.scheduler.later(1, entity::discard);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> casino.sessions.onDisconnect(handler.player));

        // ブロック右クリック: スロット看板 / ルーレット卓 / ホースホイール
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            MachineManager mm = casino.machines;
            if (mm == null) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            MachineManager.MachineData data = mm.get(sw, pos);
            if (data == null) return ActionResult.PASS;

            switch (data.type) {
                case SLOTS -> Launcher.slots(sp, sw, pos, data);
                case ROULETTE -> {
                    var table = mm.rouletteAt(sw, pos);
                    if (table != null) Launcher.roulette(sp, table);
                }
                case HORSEWHEEL -> {
                    var wheel = mm.wheelAt(sw, pos);
                    if (wheel != null) Launcher.horseWheel(sp, sw, pos, wheel);
                }
            }
            return ActionResult.SUCCESS;
        });

        // マシンの破壊は管理者のみ (使用中は不可)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw) || casino.machines == null) return true;
            MachineManager.MachineData data = casino.machines.get(sw, pos);
            if (data == null) return true;
            if (!player.hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("§cマシンを壊す権限がありません。"), true);
                return false;
            }
            if (casino.machines.isBusy(sw, pos)) {
                player.sendMessage(Text.literal("§cこのマシンは使用中です！"), true);
                return false;
            }
            casino.machines.removeMachine(sw, pos);
            player.sendMessage(Text.literal("§aマシンを削除しました。"), false);
            return true;
        });

        System.out.println("[HimaCasino] v" + net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer(MOD_ID).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?")
                + " を初期化しました。");
    }
}
