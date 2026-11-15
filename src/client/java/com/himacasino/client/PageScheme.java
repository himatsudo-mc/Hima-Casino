package com.himacasino.client;

import com.cinemamod.mcef.MCEF;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * himacasino://app/<ページ> で、この mod 内の HTML/CSS/JS を Chromium に配信する。
 *
 * MCEF 標準の mod:// は MCEF 自身のクラスローダーから探すため、他の mod のファイルを返せない。
 * そこで独自スキームを登録し、HimaCasino 自身のクラスローダーから読み込む。
 */
final class PageScheme {

    static final String SCHEME = "himacasino";
    static final String HOST = "app";
    private static final String ROOT = "/assets/himacasino/html/";

    private static boolean registered;

    private PageScheme() {}

    static String url(String page) {
        return SCHEME + "://" + HOST + "/" + page;
    }

    static synchronized void register() {
        if (registered) return;
        MCEF.getApp().getHandle().registerSchemeHandlerFactory(SCHEME, HOST, new Factory());
        registered = true;
    }

    private static final class Factory implements CefSchemeHandlerFactory {
        @Override
        public CefResourceHandler create(CefBrowser browser, CefFrame frame, String scheme, CefRequest request) {
            return new Handler();
        }
    }

    private static final class Handler implements CefResourceHandler {
        private InputStream in;
        private String mime = "text/html";
        private boolean found;

        @Override
        public boolean processRequest(CefRequest request, CefCallback callback) {
            String url = request.getURL();
            String prefix = SCHEME + "://" + HOST + "/";
            String path = url.startsWith(prefix) ? url.substring(prefix.length()) : "";
            int cut = path.indexOf('?');
            if (cut >= 0) path = path.substring(0, cut);
            cut = path.indexOf('#');
            if (cut >= 0) path = path.substring(0, cut);

            // ディレクトリ脱出を禁止 (ページ名は英数字・ハイフン・ドットのみ)
            if (path.isEmpty() || !path.matches("[A-Za-z0-9_\\-]+\\.(html|css|js)")) {
                callback.cancel();
                return false;
            }
            in = PageScheme.class.getResourceAsStream(ROOT + path);
            if (in == null) {
                callback.cancel();
                return false;
            }
            found = true;
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            mime = switch (ext) {
                case "css" -> "text/css";
                case "js" -> "text/javascript";
                default -> "text/html";
            };
            callback.Continue();
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirectUrl) {
            response.setMimeType(mime);
            response.setStatus(found ? 200 : 404);
            response.setHeaderByName("Cache-Control", "no-cache", true);
            length.set(-1);
        }

        @Override
        public boolean readResponse(byte[] out, int bytesToRead, IntRef bytesRead, CefCallback callback) {
            if (in == null) {
                bytesRead.set(0);
                return false;
            }
            try {
                int n = in.read(out, 0, bytesToRead);
                if (n <= 0) {
                    bytesRead.set(0);
                    close();
                    return false;
                }
                bytesRead.set(n);
                return true;
            } catch (IOException e) {
                bytesRead.set(0);
                close();
                return false;
            }
        }

        @Override
        public void cancel() {
            close();
        }

        private void close() {
            try {
                if (in != null) in.close();
            } catch (IOException ignored) {
            }
            in = null;
        }
    }
}
