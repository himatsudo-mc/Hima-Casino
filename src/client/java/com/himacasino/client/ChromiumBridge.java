package com.himacasino.client;

import com.cinemamod.mcef.MCEF;
import net.minecraft.client.MinecraftClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.util.function.Consumer;

/**
 * Chromium ⇄ Java の橋渡し (JS の cefQuery を受ける CefMessageRouter)。
 * JS 側: window.cefQuery({request: '{"a":"hit"}'}) → {@link #listener} に JSON 文字列が届く。
 */
final class ChromiumBridge {

    private static boolean installed;
    /** 現在表示中の画面が受け取る (CEF スレッドから呼ばれるので、実処理はメインスレッドに移すこと) */
    static volatile Consumer<String> listener;

    private ChromiumBridge() {}

    /** MCEF が初期化済みであること (CefClient にルーターを 1 度だけ登録する) */
    static synchronized void install() {
        if (installed) return;
        CefMessageRouter router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig());
        router.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                                   boolean persistent, CefQueryCallback callback) {
                Consumer<String> l = listener;
                if (l != null) l.accept(request);
                callback.success("");
                return true;
            }
        }, true);
        MCEF.getClient().getHandle().addMessageRouter(router);
        installed = true;
    }

    static boolean available() {
        return MCEF.isInitialized();
    }

    static void runOnMain(Runnable r) {
        MinecraftClient.getInstance().execute(r);
    }
}
