package com.himacasino.table;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * item_display / text_display エンティティを NBT 経由で生成・更新するヘルパー。
 * 旧プラグインの Bukkit Display API 呼び出しに相当します。
 */
public final class Displays {

    /** この mod が出したエンティティの目印 (クラッシュ後の取り残し掃除に使う) */
    public static final String TAG = "himacasino_display";

    private static final Set<UUID> TRACKED = new HashSet<>();

    private Displays() {}

    public static boolean isTracked(UUID id) { return TRACKED.contains(id); }

    /** 変換 (translation / left rotation / scale / right rotation) */
    public record Xform(Vector3f translation, Quaternionf left, Vector3f scale, Quaternionf right) {

        public static Xform of(float sx, float sy, float sz) {
            return new Xform(new Vector3f(), new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
        }

        public static Xform uniform(float s) { return of(s, s, s); }

        /** 地面と平行に寝かせた板 (X 軸まわり 90°)。右回転 (yaw) でその場回転。 */
        public static Xform flat(float sx, float sz, float sy, float rightAngle, float rx, float ry, float rz) {
            return new Xform(new Vector3f(),
                    new Quaternionf(new AxisAngle4f((float) (Math.PI / 2), 1, 0, 0)),
                    new Vector3f(sx, sz, sy),
                    new Quaternionf(new AxisAngle4f(rightAngle, rx, ry, rz)));
        }

        NbtCompound toNbt() {
            NbtCompound c = new NbtCompound();
            c.put("translation", floats(translation.x, translation.y, translation.z));
            c.put("left_rotation", floats(left.x, left.y, left.z, left.w));
            c.put("scale", floats(scale.x, scale.y, scale.z));
            c.put("right_rotation", floats(right.x, right.y, right.z, right.w));
            return c;
        }
    }

    // ── spawn ─────────────────────────────────────────────────────────────

    public static Entity item(ServerWorld w, Vec3d pos, String itemId, Xform x, String billboard) {
        NbtCompound n = new NbtCompound();
        n.putString("id", "minecraft:item_display");
        NbtCompound item = new NbtCompound();
        item.putString("id", itemId);
        item.putInt("count", 1);
        n.put("item", item);
        n.putString("item_display", "fixed");
        n.putString("billboard", billboard);
        n.put("transformation", x.toNbt());
        n.putInt("teleport_duration", 2);
        n.putFloat("view_range", 2.0f);
        return spawn(w, pos, n);
    }

    public static Entity text(ServerWorld w, Vec3d pos, String text, String color, boolean bold,
                              float scale, String billboard) {
        NbtCompound n = new NbtCompound();
        n.putString("id", "minecraft:text_display");
        n.putString("text", component(text, color, bold));
        n.putString("billboard", billboard);
        n.putInt("background", 0);
        n.putBoolean("shadow", true);
        n.putString("alignment", "center");
        n.put("transformation", Xform.uniform(scale).toNbt());
        n.putFloat("view_range", 2.0f);
        return spawn(w, pos, n);
    }

    private static Entity spawn(ServerWorld w, Vec3d pos, NbtCompound nbt) {
        NbtList p = new NbtList();
        p.add(NbtDouble.of(pos.x));
        p.add(NbtDouble.of(pos.y));
        p.add(NbtDouble.of(pos.z));
        nbt.put("Pos", p);
        NbtList tags = new NbtList();
        tags.add(NbtString.of(TAG));
        nbt.put("Tags", tags);
        Entity e = EntityType.loadEntityWithPassengers(nbt, w, SpawnReason.COMMAND, ent -> ent);
        if (e == null) return null;
        TRACKED.add(e.getUuid());
        w.spawnNewEntityAndPassengers(e);
        return e;
    }

    // ── update ────────────────────────────────────────────────────────────

    /** 変換を差し替える。interpolationTicks > 0 ならクライアント側で補間される。 */
    public static void setTransform(Entity e, Xform x, int interpolationTicks) {
        if (e == null || e.isRemoved()) return;
        NbtCompound nbt = e.writeNbt(new NbtCompound());
        nbt.put("transformation", x.toNbt());
        nbt.putInt("start_interpolation", 0);
        nbt.putInt("interpolation_duration", interpolationTicks);
        e.readNbt(nbt);
    }

    public static void setText(Entity e, String text, String color, boolean bold) {
        if (e == null || e.isRemoved()) return;
        NbtCompound nbt = e.writeNbt(new NbtCompound());
        nbt.putString("text", component(text, color, bold));
        e.readNbt(nbt);
    }

    public static void moveTo(Entity e, double x, double y, double z) {
        if (e == null || e.isRemoved()) return;
        e.setPosition(x, y, z);
    }

    public static void remove(Entity e) {
        if (e == null) return;
        TRACKED.remove(e.getUuid());
        if (!e.isRemoved()) e.discard();
    }

    // ── group ─────────────────────────────────────────────────────────────

    /** まとめて消せるエンティティの集合 */
    public static final class Group {
        private final List<Entity> entities = new ArrayList<>();

        public <T extends Entity> T add(T e) {
            if (e != null) entities.add(e);
            return e;
        }

        public void removeAll() {
            for (Entity e : entities) remove(e);
            entities.clear();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static String component(String text, String color, boolean bold) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        if (color != null) o.addProperty("color", color);
        if (bold) o.addProperty("bold", true);
        return o.toString();
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }
}
