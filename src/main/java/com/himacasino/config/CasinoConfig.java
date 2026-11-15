package com.himacasino.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mod 設定 (config/hima-casino.json)。旧プラグインの config.yml に相当します。
 * フィールドを増やしても、欠けた項目はデフォルト値で補われます。
 */
public class CasinoConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String currencySymbol = "コイン";
    public Economy economy = new Economy();
    public Slots slots = new Slots();
    public Roulette roulette = new Roulette();
    public HighLow highlow = new HighLow();
    public Simple blackjack = new Simple();
    public Simple poker = new Simple();

    public static class Economy {
        /** auto = Common Economy API があれば使う / free = 残高を扱わない / cea = 必須 */
        public String mode = "auto";
        /** 使用する通貨 ID (例: "mymod:coins")。空なら最初のアカウント。 */
        public String currencyId = "";
    }

    public static class Simple {
        public double minBet = 10.0;
        public double maxBet = 10000.0;
    }

    public static class Slots extends Simple {
        public int defaultSetting = 1;
        /** 設定番号 → シンボル 1..7 の出現ウェイト */
        public Map<String, List<Integer>> settings = defaultSettings();
        /** 配当倍率 */
        public Map<String, Double> payouts = defaultPayouts();

        private static Map<String, List<Integer>> defaultSettings() {
            Map<String, List<Integer>> m = new LinkedHashMap<>();
            m.put("1", List.of(30, 25, 20, 15, 5, 3, 2));
            m.put("2", List.of(28, 23, 19, 14, 6, 5, 5));
            m.put("3", List.of(25, 22, 18, 14, 7, 7, 7));
            m.put("4", List.of(23, 20, 17, 13, 9, 9, 9));
            m.put("5", List.of(20, 18, 16, 12, 12, 11, 11));
            m.put("6", List.of(18, 16, 14, 12, 13, 13, 14));
            return m;
        }

        private static Map<String, Double> defaultPayouts() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("7-7-7", 100.0);
            m.put("3-of-a-kind", 10.0);
            m.put("two-7s", 5.0);
            m.put("one-7", 2.0);
            m.put("two-of-a-kind", 1.5);
            return m;
        }
    }

    public static class Roulette extends Simple {
        /** ホイールが止まるまでのチック数 (20 = 1 秒) */
        public int spinTicks = 200;
    }

    public static class HighLow extends Simple {
        public double winMultiplier = 1.9;
    }

    // ── accessors with the same semantics as the old ConfigLoader ─────────

    public List<Integer> slotsWeights(int setting) {
        return slots.settings.get(String.valueOf(setting));
    }

    public double slotsPayout(String key, double fallback) {
        return slots.payouts.getOrDefault(key, fallback);
    }

    // ── persistence ───────────────────────────────────────────────────────

    public static CasinoConfig load(Path file) {
        CasinoConfig cfg = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, CasinoConfig.class);
            } catch (Exception e) {
                System.err.println("[HimaCasino] 設定を読み込めませんでした。デフォルトを使用します: " + e.getMessage());
            }
        }
        if (cfg == null) cfg = new CasinoConfig();
        cfg.save(file); // 欠けた項目を補った形で書き戻す
        return cfg;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("[HimaCasino] 設定を保存できませんでした: " + e.getMessage());
        }
    }
}
