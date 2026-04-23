package com.himacasino.games.horsewheel;

import com.himacasino.HimaCasino;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Permanent vertical Horse Wheel display entity group.
 *
 * The wheel is in the XY plane (segments orbit the center in the vertical plane).
 * Players approach from the -Z side to view it face-on.
 *
 * Layout:
 *   center = machine-block position + (0.5, 2.0, 0.5)
 *   24 colored concrete segments orbit at radius 1.4
 *   Multiplier labels (TextDisplay, Billboard.CENTER) overlay each segment
 *   "▼" pointer (TextDisplay) sits above the rim — the segment under the pointer wins
 */
public class HorseWheelTableDisplay {

    // ── Shared constants (used by HorseWheelGame) ──────────────────────────
    static final int[]      WHEEL_ORDER  = {5,0,1,3,0,2,1,4,0,1,2,0,3,1,0,4,0,1,2,3,0,1,2,0};
    static final double[]   MULTIPLIERS  = {2.0, 3.0, 5.0, 8.0, 10.0, 20.0};
    static final String[]   HORSE_NAMES  = {"WHITE", "YELLOW", "BLUE", "GREEN", "RED", "GOLD"};
    static final Material[] HORSE_MATS   = {
        Material.WHITE_CONCRETE, Material.YELLOW_CONCRETE, Material.CYAN_CONCRETE,
        Material.LIME_CONCRETE,  Material.RED_CONCRETE,    Material.GOLD_BLOCK
    };
    static final String[]   HORSE_COLORS = {"§f", "§e", "§b", "§a", "§c", "§6"};

    private static final float WHEEL_RADIUS = 1.4f;
    private static final float IDLE_OMEGA   = 0.010f; // ~0.57°/tick, ~31 s per rev
    private static final int   SPIN_TICKS   = 220;    // ~11 s spin duration

    private final HimaCasino plugin;
    private final Location    center;

    private final List<ItemDisplay> segDisplays = new ArrayList<>();
    private final List<TextDisplay> segLabels   = new ArrayList<>();
    private TextDisplay pointerText;

    private float      currentAngle  = 0f;
    private int        prevTopSeg    = -1;
    private BukkitTask animTask;
    private boolean    spinning      = false;

    // Spin state
    private float    spinStartAngle = 0f;
    private float    spinTotalAngle = 0f;
    private int      spinTick       = 0;
    private int      winningSegIdx  = -1;
    private Runnable onSpinFinished;

    public HorseWheelTableDisplay(HimaCasino plugin, Location center) {
        this.plugin = plugin;
        this.center = center.clone();
        spawnAll();
        startIdleLoop();
    }

    // ── Spawn ───────────────────────────────────────────────────────────────

    private void spawnAll() {
        World world = center.getWorld();
        if (world == null) return;

        // 24 segment blocks arranged in vertical circle
        for (int i = 0; i < 24; i++) {
            final int horseType = WHEEL_ORDER[i];
            double baseAngle = (2 * Math.PI / 24) * i;
            Location segLoc  = segLoc(baseAngle);

            ItemDisplay seg = world.spawn(segLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(HORSE_MATS[horseType]));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.42f, 0.42f, 0.42f),
                        new AxisAngle4f(0, 0, 1, 0)));
                d.setPersistent(false);
                d.setViewRange(64);
            });
            segDisplays.add(seg);

            // Multiplier label — always faces the player (Billboard.CENTER)
            Location lblLoc = segLoc.clone().add(0, 0, -0.28);
            TextDisplay lbl = world.spawn(lblLoc, TextDisplay.class, d -> {
                d.setText(HORSE_COLORS[horseType] + "§l" + (int) MULTIPLIERS[horseType] + "x");
                d.setBillboard(Display.Billboard.CENTER);
                d.setDefaultBackground(false);
                d.setShadowed(true);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.30f, 0.30f, 0.30f),
                        new AxisAngle4f(0, 0, 1, 0)));
                d.setPersistent(false);
                d.setViewRange(64);
            });
            segLabels.add(lbl);
        }

        // "▼" pointer above the wheel, facing -Z (toward players who approach from that side)
        Location ptrLoc = center.clone().add(0, WHEEL_RADIUS + 0.70, -0.35);
        pointerText = world.spawn(ptrLoc, TextDisplay.class, d -> {
            d.setText("§c§l▼");
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.85f, 0.85f, 0.85f),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setPersistent(false);
            d.setViewRange(64);
        });

        // Title label
        Location titleLoc = center.clone().add(0, WHEEL_RADIUS + 1.35, -0.35);
        TextDisplay title = world.spawn(titleLoc, TextDisplay.class, d -> {
            d.setText("§d§lHORSE WHEEL");
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setShadowed(true);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.90f, 0.90f, 0.90f),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setPersistent(false);
            d.setViewRange(64);
        });
        segLabels.add(title); // tracked for cleanup
    }

    // ── Animation loops ────────────────────────────────────────────────────

    private void startIdleLoop() {
        if (animTask != null && !animTask.isCancelled()) return;
        spinning = false;
        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            currentAngle = (currentAngle + IDLE_OMEGA) % (float) (2 * Math.PI);
            applyAngle();
            tickSound(false);
        }, 0L, 1L);
    }

    /**
     * Starts the spin animation, ending with {@code targetSeg} at the top (pointer position).
     *
     * @param targetSeg  index (0-23) into {@link #WHEEL_ORDER} that should land at the pointer
     * @param onFinished called on the main thread when the spin completes
     */
    public void startSpin(int targetSeg, Runnable onFinished) {
        if (animTask != null && !animTask.isCancelled()) animTask.cancel();
        spinning       = true;
        winningSegIdx  = targetSeg;
        onSpinFinished = onFinished;
        spinStartAngle = currentAngle;
        spinTick       = 0;

        // Segment i is at angle (2π/24)*i + finalAngle when at the pointer (π/2 = top)
        // → finalAngle = π/2 − (2π/24)*targetSeg
        float segBase    = (float) ((2 * Math.PI / 24) * targetSeg);
        float finalNorm  = ((float) (Math.PI / 2) - segBase + (float) (4 * Math.PI))
                           % (float) (2 * Math.PI);
        float delta      = (finalNorm - (spinStartAngle % (float) (2 * Math.PI))
                           + (float) (2 * Math.PI)) % (float) (2 * Math.PI);
        // 5 full laps + delta to reach the stop position
        spinTotalAngle   = (float) (5 * 2 * Math.PI) + delta;

        animTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spinTick, 0L, 1L);
    }

    private void spinTick() {
        spinTick++;
        float t      = (float) spinTick / SPIN_TICKS;
        float eased  = easeOut(Math.min(t, 1.0f));
        currentAngle = (spinStartAngle + spinTotalAngle * eased) % (float) (2 * Math.PI);
        applyAngle();
        tickSound(true);

        if (spinTick >= SPIN_TICKS) {
            animTask.cancel();
            animTask = null;
            spinning = false;
            if (onSpinFinished != null) { onSpinFinished.run(); onSpinFinished = null; }
            plugin.getServer().getScheduler().runTaskLater(plugin, this::startIdleLoop, 100L);
        }
    }

    private static float easeOut(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv; // cubic ease-out: fast then slows dramatically
    }

    // ── Position update ────────────────────────────────────────────────────

    private void applyAngle() {
        for (int i = 0; i < segDisplays.size(); i++) {
            double angle  = (2 * Math.PI / 24) * i + currentAngle;
            Location sLoc = segLoc(angle);
            Location lLoc = sLoc.clone().add(0, 0, -0.28);
            if (segDisplays.get(i).isValid()) segDisplays.get(i).teleport(sLoc);
            if (i < segLabels.size() - 1 && segLabels.get(i).isValid()) {
                segLabels.get(i).teleport(lLoc);
            }
        }
    }

    private void tickSound(boolean isSpinning) {
        int top = topSegment();
        if (top != prevTopSeg) {
            prevTopSeg = top;
            float pitch = isSpinning
                    ? 0.7f + (float) spinTick / SPIN_TICKS * 0.8f
                    : 1.0f;
            center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, pitch);
        }
    }

    /** Returns which segment index is closest to the top (pointer position = angle π/2). */
    private int topSegment() {
        float norm = (float) ((Math.PI / 2 - currentAngle) * 24 / (2 * Math.PI));
        return Math.floorMod(Math.round(norm), 24);
    }

    // ── Geometry ───────────────────────────────────────────────────────────

    /** Location for a segment at the given angle (wheel in the XY plane, fixed Z). */
    private Location segLoc(double angle) {
        double x = center.getX() + WHEEL_RADIUS * Math.cos(angle);
        double y = center.getY() + WHEEL_RADIUS * Math.sin(angle);
        return new Location(center.getWorld(), x, y, center.getZ());
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public Location getCenter()         { return center.clone(); }
    public boolean  isSpinning()        { return spinning; }
    public int      getWinningSegIdx()  { return winningSegIdx; }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public void despawn() {
        if (animTask != null && !animTask.isCancelled()) animTask.cancel();
        segDisplays.forEach(d -> { if (d.isValid()) d.remove(); });
        segLabels.forEach(d ->   { if (d.isValid()) d.remove(); });
        if (pointerText != null && pointerText.isValid()) pointerText.remove();
        segDisplays.clear();
        segLabels.clear();
    }
}
