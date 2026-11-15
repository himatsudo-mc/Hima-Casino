package com.himacasino.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.himacasino.config.CasinoConfig;
import com.himacasino.game.BlackjackGame;
import com.himacasino.game.CasinoGame;
import com.himacasino.game.GameHost;
import com.himacasino.game.HighLowGame;
import com.himacasino.game.HorseWheelGame;
import com.himacasino.game.PokerGame;
import com.himacasino.game.RouletteGame;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 開発用サーバー: Chromium GUI (HTML) を、実際の Java ゲームロジックにブラウザから直接つなぐ。
 * Minecraft もクライアント mod も不要で、Playwright などによる画面確認に使う。
 *
 *   ./gradlew devServer           → http://localhost:8765/blackjack.html
 *   GET  /api/start?game=poker&balance=5000   ゲームを新規開始
 *   POST /api/action                           {"a": "hit"} を送信 → 最新状態を返す
 *   GET  /api/state                            最新状態
 */
public final class DevServer {

    private static final Object LOCK = new Object();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor();
    private static final Path WEB = Path.of("src/client/resources/assets/himacasino/html");

    private static DevHost host;
    private static CasinoGame game;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/api/start", ex -> {
            Map<String, String> q = query(ex);
            synchronized (LOCK) {
                start(q.getOrDefault("game", "blackjack"),
                        Double.parseDouble(q.getOrDefault("balance", "5000")),
                        !"1".equals(q.get("free")));
                json(ex, current());
            }
        });
        server.createContext("/api/state", ex -> {
            synchronized (LOCK) { json(ex, current()); }
        });
        server.createContext("/api/action", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (LOCK) {
                if (game != null) {
                    JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                    String a = o.has("a") ? o.get("a").getAsString() : "";
                    if ("__closed".equals(a)) game.onClientClosed();
                    else if (!"__ready".equals(a)) game.handle(a, o);
                }
                json(ex, current());
            }
        });
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            if (p.equals("/")) p = "/blackjack.html";
            Path f = WEB.resolve(p.substring(1)).normalize();
            if (!f.startsWith(WEB) || !Files.isRegularFile(f)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] data = Files.readAllBytes(f);
            String type = p.endsWith(".css") ? "text/css" : p.endsWith(".js") ? "text/javascript" : "text/html";
            ex.getResponseHeaders().add("Content-Type", type + "; charset=utf-8");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        });
        server.start();
        System.out.println("DevServer: http://127.0.0.1:" + port + "/  (例: /blackjack.html, /api/start?game=blackjack)");
    }

    private static void start(String name, double balance, boolean economy) {
        host = new DevHost(balance, economy);
        DevHost h = host;
        game = switch (name) {
            case "highlow" -> new HighLowGame(h);
            case "poker" -> new PokerGame(h);
            case "roulette" -> new RouletteGame(h, new RouletteStub(h));
            case "horsewheel" -> new HorseWheelGame(h, (seg, done) -> h.later(60, done));
            default -> new BlackjackGame(h);
        };
        game.open();
    }

    private static String current() {
        JsonObject s = game == null ? new JsonObject() : game.state();
        if (host != null && host.uiClosed) s.addProperty("_ui", "closed");
        return s.toString();
    }

    private static void json(HttpExchange ex, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new java.util.HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) for (String kv : q.split("&")) {
            String[] p = kv.split("=", 2);
            if (p.length == 2) m.put(p[0], p[1]);
        }
        return m;
    }

    /** 1 秒後にボールが止まるだけのルーレット卓 */
    private static final class RouletteStub implements RouletteGame.TableView {
        private final DevHost h;
        RouletteStub(DevHost h) { this.h = h; }
        public void setNumberCoin(int n, double t) {}
        public void setColorCoin(String c, double t) {}
        public void clearCoins() {}
        public void setActive(boolean a) {}
        public void startSpin(int n, int ticks, Runnable landed) { h.later(40, landed); }
        public void showResult(int n) {}
        public void reset() {}
    }

    private static final class DevHost implements GameHost {
        private final CasinoConfig cfg = new CasinoConfig();
        private final Random rng = new Random();
        private final boolean economy;
        private double balance;
        volatile boolean uiClosed;

        DevHost(double balance, boolean economy) {
            this.balance = balance;
            this.economy = economy;
        }

        @Override public CasinoConfig config() { return cfg; }
        @Override public Random random() { return rng; }
        @Override public boolean economyEnabled() { return economy; }
        @Override public double balance() { return balance; }
        @Override public boolean withdraw(double a) { if (balance < a) return false; balance -= a; return true; }
        @Override public void deposit(double a) { balance += a; }
        @Override public void message(String t) { System.out.println("[chat] " + t.replaceAll("§.", "")); }
        @Override public void sound(String id, float v, float p) {}
        @Override public void effect(String n) {}
        @Override public void later(int ticks, Runnable r) {
            TIMER.schedule(() -> { synchronized (LOCK) { r.run(); } }, ticks * 50L, TimeUnit.MILLISECONDS);
        }
        @Override public void openUi(String g, JsonObject s) { uiClosed = false; }
        @Override public void sendState(String g, JsonObject s) {}
        @Override public void closeUi() { uiClosed = true; }
        @Override public void ended() { uiClosed = true; }
    }
}
