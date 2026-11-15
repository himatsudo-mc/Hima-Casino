package com.himacasino.client;

import com.himacasino.network.ActionPayload;
import com.himacasino.network.UiPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** クライアント側エントリーポイント: サーバーの指示で Chromium GUI を開閉する。 */
public class HimaCasinoClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(UiPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> handle(client, payload));
        });
    }

    private static void handle(MinecraftClient client, UiPayload p) {
        switch (p.kind()) {
            case "open" -> open(client, p);
            case "state" -> {
                if (client.currentScreen instanceof CasinoScreen s && s.game().equals(p.game())) s.pushState(p.json());
            }
            case "close" -> {
                if (client.currentScreen instanceof CasinoScreen s) s.closeFromServer();
            }
            default -> {}
        }
    }

    private static void open(MinecraftClient client, UiPayload p) {
        if (client.currentScreen instanceof CasinoScreen s && s.game().equals(p.game())) {
            s.pushState(p.json());
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("mcef") || !ChromiumBridge.available()) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cChromium (MCEF) の準備ができていません。MCEF が導入され、初回ダウンロードが完了しているか確認してください。"), false);
            }
            // サーバー側のゲームを終了させる (ベットは返金される)
            if (ClientPlayNetworking.canSend(ActionPayload.ID)) {
                ClientPlayNetworking.send(new ActionPayload("{\"a\":\"__closed\"}"));
            }
            return;
        }
        client.setScreen(new CasinoScreen(p.game(), p.json()));
    }
}
