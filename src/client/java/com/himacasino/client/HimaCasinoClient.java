package com.himacasino.client;

import com.himacasino.network.ActionPayload;
import com.himacasino.network.UiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * クライアント側エントリーポイント: サーバーの指示で Chromium GUI を開閉する。
 * このクラスは MCEF のクラスを直接参照しない (MCEF 未導入でもクラッシュしないように)。
 */
public class HimaCasinoClient implements ClientModInitializer {

    private static final boolean MCEF_PRESENT = FabricLoader.getInstance().isModLoaded("mcef");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(UiPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                if (MCEF_PRESENT) {
                    ScreenRouter.handle(client, payload);
                } else if ("open".equals(payload.kind())) {
                    reject(client, "§cこのゲームの画面には MCEF (Chromium) が必要です。ランチャーの mod 一覧を確認してください。");
                }
            });
        });
    }

    /** 画面を開けないことを伝え、サーバー側のゲームを終了させる (ベットは返金される) */
    static void reject(MinecraftClient client, String legacyMessage) {
        if (client.player != null) client.player.sendMessage(Text.literal(legacyMessage), false);
        if (ClientPlayNetworking.canSend(ActionPayload.ID)) {
            ClientPlayNetworking.send(new ActionPayload("{\"a\":\"__closed\"}"));
        }
    }
}
