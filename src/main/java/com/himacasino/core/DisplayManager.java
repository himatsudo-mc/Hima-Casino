package com.himacasino.core;

import com.himacasino.HimaCasino;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

public class DisplayManager {

    private final HimaCasino plugin;
    private final Map<UUID, List<Display>> tracked = new HashMap<>();

    public DisplayManager(HimaCasino plugin) {
        this.plugin = plugin;
    }

    public ItemDisplay spawnItemDisplay(Location location, ItemStack item, float scale) {
        World world = location.getWorld();
        return world.spawn(location, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setInterpolationDuration(2);
        });
    }

    public ItemDisplay spawnItemDisplayBillboard(Location location, ItemStack item, float scale) {
        World world = location.getWorld();
        return world.spawn(location, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            d.setInterpolationDuration(2);
        });
    }

    public TextDisplay spawnTextDisplay(Location location, String text, float scale) {
        World world = location.getWorld();
        return world.spawn(location, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setDefaultBackground(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
        });
    }

    public void trackDisplay(UUID gameId, Display display) {
        tracked.computeIfAbsent(gameId, k -> new ArrayList<>()).add(display);
    }

    public void removeGameDisplays(UUID gameId) {
        List<Display> list = tracked.remove(gameId);
        if (list != null) list.forEach(d -> { if (d.isValid()) d.remove(); });
    }

    /** Smooth rotation update for an ItemDisplay (spin around Y axis). */
    public void setRotationY(ItemDisplay display, float angle, int interpolationTicks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(interpolationTicks);
        Transformation prev = display.getTransformation();
        display.setTransformation(new Transformation(
                prev.getTranslation(),
                prev.getLeftRotation(),
                prev.getScale(),
                new AxisAngle4f(angle, 0, 1, 0)
        ));
    }

    public void cleanup() {
        tracked.values().forEach(list -> list.forEach(d -> { if (d.isValid()) d.remove(); }));
        tracked.clear();
    }
}
