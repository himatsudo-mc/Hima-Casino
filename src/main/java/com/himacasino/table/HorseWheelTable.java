package com.himacasino.table;

import com.himacasino.Casino;
import com.himacasino.game.HorseWheelGame;
import com.himacasino.server.Scheduler;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** ホースホイールの 3D 表示 (縦型 24 分割ホイール)。旧 HorseWheelTableDisplay。 */
public class HorseWheelTable implements HorseWheelGame.WheelView {

    private static final String[] HORSE_ITEMS = {
        "minecraft:white_concrete", "minecraft:yellow_concrete", "minecraft:cyan_concrete",
        "minecraft:lime_concrete", "minecraft:red_concrete", "minecraft:gold_block"
    };
    private static final String[] HORSE_COLORS = {"white", "yellow", "aqua", "green", "red", "gold"};

    private static final int SEGMENTS = 24;
    private static final float WHEEL_RADIUS = 1.4f;
    private static final float IDLE_OMEGA = 0.010f;
    private static final int SPIN_TICKS = 220;

    private final Casino casino;
    private final ServerWorld world;
    private final Vec3d center;
    private final Entity[] segments = new Entity[SEGMENTS];
    private final Entity[] labels = new Entity[SEGMENTS];
    private final Displays.Group group = new Displays.Group();

    private float currentAngle;
    private int prevTopSeg = -1;
    private boolean spinning;
    private int spinTick;
    private Scheduler.Handle animTask;

    public HorseWheelTable(Casino casino, ServerWorld world, Vec3d center) {
        this.casino = casino;
        this.world = world;
        this.center = center;
        spawnAll();
        startIdle();
    }

    public boolean isSpinning() { return spinning; }
    public Vec3d center() { return center; }
    public ServerWorld world() { return world; }

    private Vec3d segPos(double angle) {
        return new Vec3d(center.x + WHEEL_RADIUS * Math.cos(angle), center.y + WHEEL_RADIUS * Math.sin(angle), center.z);
    }

    private void spawnAll() {
        for (int i = 0; i < SEGMENTS; i++) {
            int horse = HorseWheelGame.WHEEL_ORDER[i];
            Vec3d p = segPos((2 * Math.PI / SEGMENTS) * i);
            segments[i] = group.add(Displays.item(world, p, HORSE_ITEMS[horse], Displays.Xform.uniform(0.42f), "fixed"));
            labels[i] = group.add(Displays.text(world, p.add(0, 0, -0.28),
                    (int) HorseWheelGame.MULTIPLIERS[horse] + "x", HORSE_COLORS[horse], true, 0.30f, "center"));
        }
        group.add(Displays.text(world, center.add(0, WHEEL_RADIUS + 0.70, -0.35), "▼", "red", true, 0.85f, "center"));
        group.add(Displays.text(world, center.add(0, WHEEL_RADIUS + 1.35, -0.35), "HORSE WHEEL", "light_purple", true, 0.90f, "center"));
    }

    private void startIdle() {
        spinning = false;
        if (animTask != null) animTask.cancel();
        animTask = casino.scheduler.repeat(0, 2, () -> {
            if (world.getClosestPlayer(center.x, center.y, center.z, 48, false) == null) return true;
            currentAngle = (currentAngle + IDLE_OMEGA * 2) % (float) (2 * Math.PI);
            applyAngle();
            tickSound(false);
            return true;
        });
    }

    @Override
    public void startSpin(int targetSeg, Runnable onFinished) {
        if (animTask != null) animTask.cancel();
        spinning = true;
        spinTick = 0;
        float start = currentAngle;
        float segBase = (float) ((2 * Math.PI / SEGMENTS) * targetSeg);
        float finalNorm = ((float) (Math.PI / 2) - segBase + (float) (4 * Math.PI)) % (float) (2 * Math.PI);
        float delta = (finalNorm - (start % (float) (2 * Math.PI)) + (float) (2 * Math.PI)) % (float) (2 * Math.PI);
        float total = (float) (5 * 2 * Math.PI) + delta;

        animTask = casino.scheduler.repeat(0, 1, () -> {
            spinTick++;
            float t = Math.min((float) spinTick / SPIN_TICKS, 1.0f);
            float inv = 1f - t;
            float eased = 1f - inv * inv * inv; // 三次イーズアウト
            currentAngle = (start + total * eased) % (float) (2 * Math.PI);
            applyAngle();
            tickSound(true);
            if (spinTick >= SPIN_TICKS) {
                spinning = false;
                onFinished.run();
                casino.scheduler.later(100, this::startIdle);
                return false;
            }
            return true;
        });
    }

    private void applyAngle() {
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3d p = segPos((2 * Math.PI / SEGMENTS) * i + currentAngle);
            Displays.moveTo(segments[i], p.x, p.y, p.z);
            Displays.moveTo(labels[i], p.x, p.y, p.z - 0.28);
        }
    }

    private void tickSound(boolean isSpinning) {
        float norm = (float) ((Math.PI / 2 - currentAngle) * SEGMENTS / (2 * Math.PI));
        int top = Math.floorMod(Math.round(norm), SEGMENTS);
        if (top != prevTopSeg) {
            prevTopSeg = top;
            float pitch = isSpinning ? 0.7f + (float) spinTick / SPIN_TICKS * 0.8f : 1.0f;
            var ev = Registries.SOUND_EVENT.get(Identifier.of("minecraft:block.note_block.hat"));
            if (ev != null) world.playSound(null, center.x, center.y, center.z, ev, SoundCategory.BLOCKS, 0.35f, pitch);
        }
    }

    public void despawn() {
        if (animTask != null) animTask.cancel();
        group.removeAll();
    }
}
