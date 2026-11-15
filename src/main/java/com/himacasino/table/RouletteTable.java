package com.himacasino.table;

import com.himacasino.Casino;
import com.himacasino.game.RouletteGame;
import com.himacasino.server.Scheduler;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * ルーレット卓の 3D 表示 (ホイール + ベッティングテーブル)。旧 RouletteTableDisplay + RouletteGame の
 * 回転アニメーション部分。プレイ画面そのものは Chromium GUI が担当します。
 */
public class RouletteTable implements RouletteGame.TableView {

    private static final int POCKET_COUNT = RouletteGame.WHEEL_ORDER.length; // 37
    private static final float POCKET_RADIUS = 1.35f;
    private static final float CENTER_DISK_SCALE = 1.1f;
    private static final float POCKET_SCALE = 0.28f;
    private static final float IDLE_OMEGA = 0.018f;

    private static final float TABLE_Z_START = 2.0f;
    private static final float CELL_X = 0.60f;
    private static final float CELL_Z = 0.56f;
    private static final float TABLE_HEIGHT = 0.07f;

    private static final float BALL_RADIUS_MAX = 1.6f;
    private static final float BALL_RADIUS_MIN = 1.30f;
    private static final float WHEEL_OMEGA_0 = 0.18f;
    private static final float BALL_OMEGA_0 = -0.40f;

    private final Casino casino;
    private final ServerWorld world;
    private final Vec3d center;
    private final Displays.Group group = new Displays.Group();
    private final Displays.Group coins = new Displays.Group();
    private final Displays.Group ball = new Displays.Group();
    private final Map<String, Displays.Group> coinByKey = new HashMap<>();
    private final Entity[] pockets = new Entity[POCKET_COUNT];
    private Entity centerDisk;
    private Entity ballEntity;

    private boolean active;
    private boolean spinning;
    private float idleAngle;
    private int highlighted = -1;
    private Scheduler.Handle idleTask;
    private Scheduler.Handle spinTask;

    public RouletteTable(Casino casino, ServerWorld world, Vec3d center) {
        this.casino = casino;
        this.world = world;
        this.center = center;
        spawnAll();
        idleTask = casino.scheduler.repeat(0, 3, this::idle);
    }

    public Vec3d center() { return center; }
    public ServerWorld world() { return world; }
    public boolean isActive() { return active; }

    private static double angle(int i) { return (2 * Math.PI / POCKET_COUNT) * i; }

    // ── spawn ─────────────────────────────────────────────────────────────

    private Vec3d at(double dx, double dy, double dz) {
        return center.add(dx, dy, dz);
    }

    private void spawnAll() {
        // ホイール台座
        group.add(Displays.item(world, at(0, 0.00, 0), "minecraft:dark_oak_log",
                Displays.Xform.flat(3.3f, 3.3f, 0.07f, 0, 0, 1, 0), "fixed"));

        // リムの白いマーカー
        int segs = 18;
        float rimR = 1.58f;
        for (int i = 0; i < segs; i++) {
            double a = (2 * Math.PI / segs) * i;
            group.add(Displays.item(world, at(Math.cos(a) * rimR, 0.10, Math.sin(a) * rimR),
                    "minecraft:white_concrete", Displays.Xform.uniform(0.13f), "fixed"));
        }

        // ポケット + 数字ラベル
        for (int i = 0; i < POCKET_COUNT; i++) {
            int number = RouletteGame.WHEEL_ORDER[i];
            double px = Math.cos(angle(i)) * POCKET_RADIUS;
            double pz = Math.sin(angle(i)) * POCKET_RADIUS;
            pockets[i] = group.add(Displays.item(world, at(px, 0.06, pz), pocketItem(number),
                    pocketXform(POCKET_SCALE), "fixed"));
            group.add(Displays.text(world, at(px, 0.22, pz), String.valueOf(number), numberColor(number, true),
                    true, 0.38f, "fixed"));
        }

        // 回転する中央ディスク
        centerDisk = group.add(Displays.item(world, at(0, 0.05, 0), "minecraft:dark_oak_log",
                diskXform(0), "fixed"));
        // 中央のコーン
        group.add(Displays.item(world, at(0, 0.14, 0), "minecraft:dark_oak_log",
                Displays.Xform.of(0.22f, 0.28f, 0.22f), "fixed"));
        // タイトル
        group.add(Displays.text(world, at(0, 1.0, 0), "ROULETTE", "gold", true, 1.4f, "vertical"));

        spawnBettingTable();
    }

    private void spawnBettingTable() {
        float tableLen = 13 * CELL_Z + 1.0f;
        float tableCenterZ = TABLE_Z_START + tableLen / 2f - 0.2f;

        group.add(Displays.item(world, at(0, TABLE_HEIGHT - 0.03f, tableCenterZ), "minecraft:dark_oak_planks",
                Displays.Xform.flat(3 * CELL_X + 0.55f, tableLen + 0.35f, 0.04f, 0, 0, 1, 0), "fixed"));
        group.add(Displays.item(world, at(0, TABLE_HEIGHT - 0.01f, tableCenterZ), "minecraft:green_concrete",
                Displays.Xform.flat(3 * CELL_X + 0.15f, tableLen, 0.04f, 0, 0, 1, 0), "fixed"));

        for (int n = 0; n <= 36; n++) {
            float[] p = betPos(n);
            group.add(Displays.text(world, at(p[0], TABLE_HEIGHT + 0.01, p[1]), String.valueOf(n),
                    numberColor(n, false), true, 0.72f, "vertical"));
        }
        float outsideZ = TABLE_Z_START + 13 * CELL_Z + 0.28f;
        group.add(Displays.text(world, at(CELL_X, TABLE_HEIGHT + 0.01, outsideZ), "RED", "red", true, 0.85f, "vertical"));
        group.add(Displays.text(world, at(0, TABLE_HEIGHT + 0.01, outsideZ), "EVEN", "gray", true, 0.70f, "vertical"));
        group.add(Displays.text(world, at(-CELL_X, TABLE_HEIGHT + 0.01, outsideZ), "BLACK", "dark_gray", true, 0.85f, "vertical"));
    }

    private static float[] betPos(int number) {
        if (number == 0) return new float[]{0f, TABLE_Z_START};
        int row = (number - 1) / 3;
        int mod = number % 3;
        float x = (mod == 1) ? CELL_X : (mod == 2) ? 0f : -CELL_X;
        return new float[]{x, TABLE_Z_START + CELL_Z + row * CELL_Z};
    }

    private static float[] colorBetPos(String key) {
        float z = TABLE_Z_START + 13 * CELL_Z + 0.28f;
        return switch (key) {
            case "red" -> new float[]{CELL_X, z};
            case "black" -> new float[]{-CELL_X, z};
            default -> null;
        };
    }

    private static String pocketItem(int n) {
        if (n == 0) return "minecraft:lime_concrete";
        return RouletteGame.isRed(n) ? "minecraft:red_concrete" : "minecraft:black_concrete";
    }

    private static String numberColor(int n, boolean onWheel) {
        if (n == 0) return "green";
        if (RouletteGame.isRed(n)) return "red";
        return onWheel ? "gray" : "dark_gray";
    }

    private static Displays.Xform pocketXform(float s) {
        return Displays.Xform.flat(s, s, s, 0, 0, 0, 1);
    }

    private static Displays.Xform diskXform(float angle) {
        return Displays.Xform.flat(CENTER_DISK_SCALE, CENTER_DISK_SCALE, CENTER_DISK_SCALE, angle, 0, 0, 1);
    }

    // ── idle rotation ─────────────────────────────────────────────────────

    private boolean idle() {
        if (spinning || active) return true;
        if (world.getClosestPlayer(center.x, center.y, center.z, 48, false) == null) return true;
        idleAngle += IDLE_OMEGA * 3;
        Displays.setTransform(centerDisk, diskXform(idleAngle), 3);
        return true;
    }

    // ── TableView ─────────────────────────────────────────────────────────

    @Override public void setActive(boolean a) { this.active = a; }

    @Override
    public void setNumberCoin(int number, double total) {
        setCoin("n_" + number, betPos(number), total, "minecraft:gold_nugget", "yellow");
    }

    @Override
    public void setColorCoin(String color, double total) {
        float[] p = colorBetPos(color);
        if (p == null) return;
        boolean red = color.equals("red");
        setCoin(color, p, total, red ? "minecraft:red_dye" : "minecraft:ink_sac", red ? "red" : "dark_gray");
    }

    private void setCoin(String key, float[] pos, double amount, String item, String color) {
        Displays.Group old = coinByKey.remove(key);
        if (old != null) old.removeAll();
        Displays.Group g = new Displays.Group();
        Vec3d base = at(pos[0], TABLE_HEIGHT + 0.12, pos[1]);
        g.add(Displays.item(world, base, item, Displays.Xform.uniform(0.30f), "vertical"));
        g.add(Displays.text(world, base.add(0, 0.28, 0), String.format("%.0f", amount), color, true, 0.5f, "center"));
        coinByKey.put(key, g);
    }

    @Override
    public void clearCoins() {
        coinByKey.values().forEach(Displays.Group::removeAll);
        coinByKey.clear();
    }

    @Override
    public void startSpin(int winningNumber, int spinTicks, Runnable onLanded) {
        spinning = true;
        ballEntity = ball.add(Displays.item(world, at(BALL_RADIUS_MAX, 0.15, 0), "minecraft:snowball",
                Displays.Xform.uniform(0.13f), "center"));

        final int[] tick = {0};
        final float[] wheelAngle = {idleAngle};
        final float[] ballAngle = {0f};
        spinTask = casino.scheduler.repeat(0, 1, () -> {
            tick[0]++;
            float progress = Math.min(1f, (float) tick[0] / spinTicks);
            wheelAngle[0] += WHEEL_OMEGA_0 * (1f - progress) * (1f - progress);
            ballAngle[0] += BALL_OMEGA_0 * (1f - progress * 0.8f);

            Displays.setTransform(centerDisk, diskXform(wheelAngle[0]), 2);

            float radius = BALL_RADIUS_MAX - (BALL_RADIUS_MAX - BALL_RADIUS_MIN) * progress;
            float height = 0.15f - 0.10f * progress;
            Displays.moveTo(ballEntity, center.x + Math.cos(ballAngle[0]) * radius,
                    center.y + height, center.z + Math.sin(ballAngle[0]) * radius);

            int bounce = Math.max(2, (int) (3 + progress * 12));
            if (tick[0] % bounce == 0 && progress < 0.95f) sound("minecraft:block.stone.hit", 0.25f, 1.8f - progress);
            if (tick[0] % 10 == 0 && progress < 0.9f) {
                sound("minecraft:block.wooden_button.click_on", 0.12f, 0.8f + (1f - progress) * 0.3f);
            }

            if (tick[0] >= spinTicks) {
                land(winningNumber);
                onLanded.run();
                return false;
            }
            return true;
        });
    }

    private void land(int winningNumber) {
        int idx = 0;
        for (int i = 0; i < POCKET_COUNT; i++) if (RouletteGame.WHEEL_ORDER[i] == winningNumber) idx = i;
        double a = angle(idx);
        Displays.moveTo(ballEntity, center.x + Math.cos(a) * (BALL_RADIUS_MIN - 0.02), center.y + 0.08,
                center.z + Math.sin(a) * (BALL_RADIUS_MIN - 0.02));
    }

    @Override
    public void showResult(int winningNumber) {
        int idx = 0;
        for (int i = 0; i < POCKET_COUNT; i++) if (RouletteGame.WHEEL_ORDER[i] == winningNumber) idx = i;
        resetHighlight();
        highlighted = idx;
        Displays.setTransform(pockets[idx], pocketXform(POCKET_SCALE * 1.5f), 0);
    }

    private void resetHighlight() {
        if (highlighted >= 0) {
            Displays.setTransform(pockets[highlighted], pocketXform(POCKET_SCALE), 0);
            highlighted = -1;
        }
    }

    @Override
    public void reset() {
        if (spinTask != null) { spinTask.cancel(); spinTask = null; }
        spinning = false;
        ball.removeAll();
        ballEntity = null;
        resetHighlight();
    }

    private void sound(String id, float vol, float pitch) {
        var ev = Registries.SOUND_EVENT.get(Identifier.of(id));
        if (ev != null) world.playSound(null, center.x, center.y, center.z, ev, SoundCategory.BLOCKS, vol, pitch);
    }

    public void despawn() {
        if (idleTask != null) idleTask.cancel();
        if (spinTask != null) spinTask.cancel();
        clearCoins();
        ball.removeAll();
        group.removeAll();
    }
}
