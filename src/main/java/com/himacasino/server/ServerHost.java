package com.himacasino.server;

import com.google.gson.JsonObject;
import com.himacasino.Casino;
import com.himacasino.config.CasinoConfig;
import com.himacasino.economy.EconomyService;
import com.himacasino.game.GameHost;
import com.himacasino.network.UiPayload;
import com.himacasino.util.Legacy;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.function.Supplier;

/** {@link GameHost} の Fabric 実装: 1 プレイヤー 1 ゲーム分。 */
public class ServerHost implements GameHost {

    private final Casino casino;
    private final ServerPlayerEntity player;
    private final Random random = new Random();
    private Supplier<Vec3d> soundOrigin;
    private Runnable endHook = () -> {};
    private boolean ended;

    public ServerHost(Casino casino, ServerPlayerEntity player) {
        this.casino = casino;
        this.player = player;
    }

    public ServerPlayerEntity player() { return player; }

    /** サウンド/パーティクルの発生位置 (ルーレット卓の位置など)。未指定ならプレイヤー位置。 */
    public ServerHost soundOrigin(Supplier<Vec3d> origin) {
        this.soundOrigin = origin;
        return this;
    }

    public ServerHost onEnd(Runnable hook) {
        this.endHook = hook;
        return this;
    }

    private Vec3d origin() {
        return soundOrigin != null ? soundOrigin.get() : player.getPos();
    }

    private EconomyService.Account account() {
        return casino.economy.account(player);
    }

    @Override public CasinoConfig config() { return casino.config; }

    @Override public Random random() { return random; }

    @Override public boolean economyEnabled() { return account() != null; }

    @Override
    public double balance() {
        var acc = account();
        return acc == null ? Double.MAX_VALUE : acc.balance();
    }

    @Override
    public boolean withdraw(double amount) {
        var acc = account();
        return acc == null || acc.withdraw(amount);
    }

    @Override
    public void deposit(double amount) {
        var acc = account();
        if (acc != null) acc.deposit(amount);
    }

    @Override
    public void message(String legacyText) {
        player.sendMessage(Legacy.toText(legacyText), false);
    }

    @Override
    public void sound(String id, float volume, float pitch) {
        SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of(id));
        if (ev == null) return;
        Vec3d o = origin();
        player.getServerWorld().playSound(null, o.x, o.y, o.z, ev, SoundCategory.BLOCKS, volume, pitch);
    }

    @Override
    public void effect(String name) {
        if (!"win".equals(name)) return;
        Vec3d o = origin();
        ServerWorld w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, o.x, o.y + 1.5, o.z, 30, 0.8, 0.5, 0.8, 0.0);
    }

    @Override
    public void later(int ticks, Runnable task) {
        casino.scheduler.later(ticks, () -> {
            if (!ended) task.run();
        });
    }

    @Override
    public void openUi(String game, JsonObject state) {
        send("open", game, state);
    }

    @Override
    public void sendState(String game, JsonObject state) {
        send("state", game, state);
    }

    @Override
    public void closeUi() {
        send("close", "", new JsonObject());
    }

    private void send(String kind, String game, JsonObject state) {
        if (!ServerPlayNetworking.canSend(player, UiPayload.ID)) return;
        ServerPlayNetworking.send(player, new UiPayload(kind, game, state.toString()));
    }

    @Override
    public void ended() {
        if (ended) return;
        ended = true;
        closeUi();
        endHook.run();
        casino.sessions.remove(player.getUuid(), this);
    }
}
