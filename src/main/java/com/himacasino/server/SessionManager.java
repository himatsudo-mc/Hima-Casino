package com.himacasino.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.himacasino.Casino;
import com.himacasino.game.CasinoGame;
import com.himacasino.network.UiPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** プレイヤーごとの進行中ゲーム (1 人 1 ゲーム)。旧 GameManager に相当。 */
public class SessionManager {

    private record Session(CasinoGame game, ServerHost host) {}

    private final Casino casino;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public SessionManager(Casino casino) {
        this.casino = casino;
    }

    public boolean hasActive(ServerPlayerEntity player) {
        Session s = sessions.get(player.getUuid());
        return s != null && !s.game.isFinished();
    }

    /**
     * ゲームを開始する。needsUi のゲームはクライアント側 mod (Chromium GUI) が無いと開始できない。
     * @return 開始できたゲーム、できなければ null (理由はプレイヤーへ通知済み)
     */
    public <T extends CasinoGame> T start(ServerPlayerEntity player, boolean needsUi,
                                          Function<ServerHost, T> factory, ServerHost host) {
        if (hasActive(player)) {
            host.message("§c現在進行中のゲームがあります。");
            return null;
        }
        if (needsUi && !ServerPlayNetworking.canSend(player, UiPayload.ID)) {
            host.message("§cこのゲームの画面には HimaCasino クライアント mod と MCEF (Chromium) が必要です。");
            host.message("§7ランチャーの mod 一覧に HimaCasino と MCEF が含まれているか確認してください。");
            return null;
        }
        T game = factory.apply(host);
        sessions.put(player.getUuid(), new Session(game, host));
        return game;
    }

    /** クライアントからのアクション ({"a":"..."}) を処理する */
    public void onAction(ServerPlayerEntity player, String json) {
        Session s = sessions.get(player.getUuid());
        if (s == null || s.game.isFinished()) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String action = obj.has("a") ? obj.get("a").getAsString() : "";
            if ("__closed".equals(action)) {
                s.game.onClientClosed();
            } else {
                s.game.handle(action, obj);
            }
        } catch (RuntimeException e) {
            // 不正な入力は無視 (ゲームを壊さない)
        }
    }

    void remove(UUID id, ServerHost host) {
        Session s = sessions.get(id);
        if (s != null && s.host == host) sessions.remove(id);
    }

    public void onDisconnect(ServerPlayerEntity player) {
        Session s = sessions.get(player.getUuid());
        if (s != null) s.game.shutdown();
        sessions.remove(player.getUuid());
    }

    public void shutdownAll() {
        for (Session s : new ArrayList<>(sessions.values())) s.game.shutdown();
        sessions.clear();
    }
}
