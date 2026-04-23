package com.himacasino.games.roulette;

import com.himacasino.HimaCasino;
import org.bukkit.Display;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent roulette table display entity group.
 *
 * Wheel sits at `center`. Betting grid extends in the +Z direction:
 *
 *    [wheel at z=0]
 *    ┌──────────────┐  z=2.0
 *    │     [0]      │
 *    │  3 │ 2 │ 1   │  row 1
 *    │  6 │ 5 │ 4   │  row 2
 *    │   ...        │
 *    │ 36 │35 │ 34  │  row 12
 *    │BLACK│  │ RED │  outside bets
 *    └──────────────┘  z=10.0
 */
public class RouletteTableDisplay {

    // ── Wheel geometry ─────────────────────────────────────────────────────
    private static final int     POCKET_COUNT   = RouletteGame.WHEEL_ORDER.length; // 37
    private static final double[] POCKET_ANGLES = new double[POCKET_COUNT];
    static {
        for (int i = 0; i < POCKET_COUNT; i++)
            POCKET_ANGLES[i] = (2 * Math.PI / POCKET_COUNT) * i;
    }

    private static final float POCKET_RADIUS     = 1.35f;
    private static final float CENTER_DISK_SCALE = 1.1f;
    private static final float POCKET_SCALE      = 0.28f;
    private static final float IDLE_OMEGA        = 0.018f;

    // ── Table geometry ─────────────────────────────────────────────────────
    private static final float TABLE_Z_START = 2.0f;
    private static final float CELL_X        = 0.60f;
    private static final float CELL_Z        = 0.56f;
    private static final float TABLE_HEIGHT  = 0.07f;

    private final HimaCasino plugin;
    private final UUID        tableId;
    private final Location    center;

    // ── Permanent entities ─────────────────────────────────────────────────
    private ItemDisplay          centerDisk;
    private final ItemDisplay[]  pocketDisplays = new ItemDisplay[POCKET_COUNT];

    // ── Bet coins (betKey → entity) ────────────────────────────────────────
    private final Map<String, ItemDisplay> betCoins = new HashMap<>();

    // ── State ──────────────────────────────────────────────────────────────
    private int     highlightedPocket = -1;
    private boolean gameActive        = false;
    private float   idleAngle         = 0f;
    private BukkitTask idleTask;

    public RouletteTableDisplay(HimaCasino plugin, Location center) {
        this.plugin  = plugin;
        this.tableId = UUID.randomUUID();
        this.center  = center.clone();
        spawnAll();
        startIdle();
    }

    // ── Spawn ──────────────────────────────────────────────────────────────

    private void spawnAll() {
        spawnWheel();
        spawnBettingTable();
    }

    private void spawnWheel() {
        World world = center.getWorld();

        track(flatItem(world, center.clone().add(0, 0.00, 0),
                Material.DARK_OAK_LOG, 3.3f, 3.3f, 0.07f, 0));

        int segs = 18;
        float rimR = 1.58f;
        for (int i = 0; i < segs; i++) {
            double a = (2 * Math.PI / segs) * i;
            double bx = Math.cos(a) * rimR, bz = Math.sin(a) * rimR;
            track(smallItem(world, center.clone().add(bx, 0.10, bz),
                    Material.WHITE_CONCRETE, 0.13f));
        }

        for (int i = 0; i < POCKET_COUNT; i++) {
            int    number = RouletteGame.WHEEL_ORDER[i];
            double angle  = POCKET_ANGLES[i];
            double px = Math.cos(angle) * POCKET_RADIUS;
            double pz = Math.sin(angle) * POCKET_RADIUS;
            Location pLoc = center.clone().add(px, 0.06, pz);

            Material mat = pocketMaterial(number);
            pocketDisplays[i] = world.spawn(pLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(mat));
                d.setBillboard(Display.Billboard.FIXED);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                        new Vector3f(POCKET_SCALE, POCKET_SCALE, POCKET_SCALE),
                        new AxisAngle4f(0, 0, 0, 1)));
                d.setPersistent(false);
            });
            plugin.getDisplayManager().trackDisplay(tableId, pocketDisplays[i]);

            String col = number == 0 ? "§a" : (RouletteGame.isRed(number) ? "§c" : "§7");
            Location nLoc = center.clone().add(px, 0.22, pz);
            TextDisplay lbl = plugin.getDisplayManager().spawnTextDisplay(
                    nLoc, col + "§l" + number, 0.38f);
            lbl.setBillboard(Display.Billboard.FIXED);
            lbl.setPersistent(false);
            plugin.getDisplayManager().trackDisplay(tableId, lbl);
        }

        centerDisk = world.spawn(center.clone().add(0, 0.05, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DARK_OAK_LOG));
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(CENTER_DISK_SCALE, CENTER_DISK_SCALE, CENTER_DISK_SCALE),
                    new AxisAngle4f(0, 0, 0, 1)));
            d.setInterpolationDuration(3);
            d.setPersistent(false);
        });
        plugin.getDisplayManager().trackDisplay(tableId, centerDisk);

        track(world.spawn(center.clone().add(0, 0.14, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.DARK_OAK_LOG));
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.22f, 0.28f, 0.22f),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setPersistent(false);
        }));

        TextDisplay title = plugin.getDisplayManager().spawnTextDisplay(
                center.clone().add(0, 1.0, 0), "§6§lROULETTE", 1.4f);
        title.setPersistent(false);
        plugin.getDisplayManager().trackDisplay(tableId, title);
    }

    private void spawnBettingTable() {
        World world = center.getWorld();

        float tableLen    = 13 * CELL_Z + 1.0f;
        float tableCenterZ = TABLE_Z_START + tableLen / 2f - 0.2f;

        track(flatItem(world, center.clone().add(0, TABLE_HEIGHT - 0.03f, tableCenterZ),
                Material.DARK_OAK_PLANKS,
                3 * CELL_X + 0.55f, tableLen + 0.35f, 0.04f, 0));

        track(flatItem(world, center.clone().add(0, TABLE_HEIGHT - 0.01f, tableCenterZ),
                Material.GREEN_CONCRETE,
                3 * CELL_X + 0.15f, tableLen + 0.0f, 0.04f, 0));

        spawnGridLabel(world, 0, getBetPos(0));
        for (int n = 1; n <= 36; n++) spawnGridLabel(world, n, getBetPos(n));

        float outsideZ = TABLE_Z_START + 13 * CELL_Z + 0.28f;
        textLabel(world, center.clone().add( CELL_X, TABLE_HEIGHT + 0.01, outsideZ), "§c§lRED",   0.85f);
        textLabel(world, center.clone().add(     0f, TABLE_HEIGHT + 0.01, outsideZ), "§7§lEVEN", 0.70f);
        textLabel(world, center.clone().add(-CELL_X, TABLE_HEIGHT + 0.01, outsideZ), "§8§lBLACK", 0.85f);
    }

    private void spawnGridLabel(World world, int number, float[] pos) {
        String col = number == 0 ? "§a" : (RouletteGame.isRed(number) ? "§c" : "§8");
        textLabel(world, center.clone().add(pos[0], TABLE_HEIGHT + 0.01, pos[1]),
                col + "§l" + number, 0.72f);
    }

    private void textLabel(World world, Location loc, String text, float scale) {
        TextDisplay td = plugin.getDisplayManager().spawnTextDisplay(loc, text, scale);
        td.setPersistent(false);
        plugin.getDisplayManager().trackDisplay(tableId, td);
    }

    private ItemDisplay flatItem(World world, Location loc,
                                  Material mat, float sx, float sz, float sy, float yaw) {
        return world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(mat));
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(sx, sz, sy),
                    new AxisAngle4f(yaw, 0, 1, 0)));
            d.setPersistent(false);
        });
    }

    private ItemDisplay smallItem(World world, Location loc, Material mat, float scale) {
        return world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(mat));
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)));
            d.setPersistent(false);
        });
    }

    private void track(ItemDisplay d) {
        plugin.getDisplayManager().trackDisplay(tableId, d);
    }

    // ── Idle animation ─────────────────────────────────────────────────────

    private void startIdle() {
        idleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (gameActive) return;
            idleAngle += IDLE_OMEGA;
            if (centerDisk != null && centerDisk.isValid()) {
                centerDisk.setInterpolationDelay(0);
                centerDisk.setInterpolationDuration(3);
                centerDisk.setTransformation(new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                        new Vector3f(CENTER_DISK_SCALE, CENTER_DISK_SCALE, CENTER_DISK_SCALE),
                        new AxisAngle4f(idleAngle, 0, 0, 1)));
            }
        }, 0L, 3L);
    }

    // ── Bet coin management ────────────────────────────────────────────────

    public void updateNumberCoin(int number, double totalAmount) {
        String key = "n_" + number;
        removeCoin(key);
        float[] pos = getBetPos(number);
        spawnCoin(key, pos[0], pos[1], totalAmount, Material.GOLD_NUGGET, "§e");
    }

    public void updateColorCoin(String color, double totalAmount) {
        removeCoin(color);
        float[] pos = getColorBetPos(color);
        if (pos == null) return;
        Material mat = color.equals("red") ? Material.RED_DYE : Material.INK_SAC;
        String   col = color.equals("red") ? "§c" : "§8";
        spawnCoin(color, pos[0], pos[1], totalAmount, mat, col);
    }

    private void spawnCoin(String key, float relX, float relZ, double amount,
                            Material mat, String colorCode) {
        Location loc = center.clone().add(relX, TABLE_HEIGHT + 0.12, relZ);
        ItemStack stack = new ItemStack(mat);
        ItemMeta  meta  = stack.getItemMeta();
        meta.setDisplayName(colorCode + String.format("%.0f", amount));
        stack.setItemMeta(meta);
        ItemDisplay d = center.getWorld().spawn(loc, ItemDisplay.class, disp -> {
            disp.setItemStack(stack);
            disp.setBillboard(Display.Billboard.VERTICAL);
            disp.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.30f, 0.30f, 0.30f),
                    new AxisAngle4f(0, 0, 1, 0)));
            disp.setPersistent(false);
        });
        plugin.getDisplayManager().trackDisplay(tableId, d);
        betCoins.put(key, d);
    }

    private void removeCoin(String key) {
        ItemDisplay old = betCoins.remove(key);
        if (old != null && old.isValid()) old.remove();
    }

    public void clearBetCoins() {
        for (ItemDisplay d : betCoins.values())
            if (d != null && d.isValid()) d.remove();
        betCoins.clear();
    }

    // ── Pocket highlight ───────────────────────────────────────────────────

    public void highlightPocket(int pocketIdx) {
        resetHighlight();
        highlightedPocket = pocketIdx;
        ItemDisplay pd = pocketDisplays[pocketIdx];
        if (pd != null && pd.isValid()) {
            pd.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(POCKET_SCALE * 1.5f, POCKET_SCALE * 1.5f, POCKET_SCALE * 1.5f),
                    new AxisAngle4f(0, 0, 0, 1)));
        }
    }

    public void resetHighlight() {
        if (highlightedPocket < 0 || highlightedPocket >= POCKET_COUNT) return;
        ItemDisplay pd = pocketDisplays[highlightedPocket];
        if (pd != null && pd.isValid()) {
            pd.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float)(Math.PI / 2), 1, 0, 0),
                    new Vector3f(POCKET_SCALE, POCKET_SCALE, POCKET_SCALE),
                    new AxisAngle4f(0, 0, 0, 1)));
        }
        highlightedPocket = -1;
    }

    // ── Grid coordinate helpers ────────────────────────────────────────────

    private static float[] getBetPos(int number) {
        if (number == 0) return new float[]{0f, TABLE_Z_START};
        int row = (number - 1) / 3;
        int mod = number % 3;
        float x = (mod == 1) ? CELL_X : (mod == 2) ? 0f : -CELL_X;
        float z = TABLE_Z_START + CELL_Z + row * CELL_Z;
        return new float[]{x, z};
    }

    private static float[] getColorBetPos(String key) {
        float z = TABLE_Z_START + 13 * CELL_Z + 0.28f;
        return switch (key) {
            case "red"   -> new float[]{ CELL_X, z};
            case "black" -> new float[]{-CELL_X, z};
            default      -> null;
        };
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    public void despawn() {
        if (idleTask != null) { idleTask.cancel(); idleTask = null; }
        clearBetCoins();
        plugin.getDisplayManager().removeGameDisplays(tableId);
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public UUID        getTableId()    { return tableId; }
    public Location    getCenter()     { return center.clone(); }
    public ItemDisplay getCenterDisk() { return centerDisk; }
    public boolean     isGameActive()  { return gameActive; }
    public void        setGameActive(boolean active) { this.gameActive = active; }

    private static Material pocketMaterial(int number) {
        if (number == 0) return Material.LIME_CONCRETE;
        return RouletteGame.isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
    }
}
