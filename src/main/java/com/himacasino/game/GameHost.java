package com.himacasino.game;

import com.google.gson.JsonObject;
import com.himacasino.config.CasinoConfig;

import java.util.Random;

/**
 * ゲームロジックから見たプレイヤー/サーバーの窓口。
 * ロジックを Minecraft 非依存に保ち、テストでは偽物に差し替えられるようにします。
 */
public interface GameHost {

    CasinoConfig config();

    Random random();

    // ── 経済 ──────────────────────────────────────────────────────────────
    /** false のとき残高チェックなしの無料モード */
    boolean economyEnabled();

    double balance();

    boolean withdraw(double amount);

    void deposit(double amount);

    // ── 演出 ──────────────────────────────────────────────────────────────
    /** チャット通知 (§ カラーコード可) */
    void message(String legacyText);

    /** minecraft:block.note_block.bass のようなサウンド ID (プレイヤー位置で再生) */
    void sound(String id, float volume, float pitch);

    /** "win" / "lose" などのパーティクル演出 */
    void effect(String name);

    /** n tick 後に実行 (ゲーム終了後は呼ばれても無害であること) */
    void later(int ticks, Runnable task);

    // ── クライアント UI (Chromium) ────────────────────────────────────────
    void openUi(String game, JsonObject state);

    void sendState(String game, JsonObject state);

    void closeUi();

    /** ゲームが終了したことの通知 (セッション破棄・マシン解放など) */
    void ended();
}
