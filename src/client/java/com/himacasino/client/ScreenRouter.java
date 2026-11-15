package com.himacasino.client;

import com.himacasino.network.UiPayload;
import net.minecraft.client.MinecraftClient;

/**
 * Chromium 画面の開閉。MCEF のクラスを参照するので、MCEF が導入されているときだけ呼ぶこと
 * ({@link HimaCasinoClient} が確認してから呼び出す)。
 */
final class ScreenRouter {

    private ScreenRouter() {}

    static void handle(MinecraftClient client, UiPayload p) {
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
        if (!ChromiumBridge.available()) {
            HimaCasinoClient.reject(client, "§cChromium (MCEF) の準備ができていません。初回ダウンロードが完了するまでお待ちください。");
            return;
        }
        client.setScreen(new CasinoScreen(p.game(), p.json()));
    }
}
