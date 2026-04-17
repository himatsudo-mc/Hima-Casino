package com.himacasino.games.roulette;

import com.himacasino.HimaCasino;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-row (54-slot) chest inventory for roulette betting.
 *
 * Layout:
 *   Row 0 (0-8):   [0] [chip10] [chip50] [chip100] [chip500] [chip1k] [ ] [ ] [SPIN]
 *   Row 1 (9-17):  numbers  1– 9
 *   Row 2 (18-26): numbers 10–18
 *   Row 3 (27-35): numbers 19–27
 *   Row 4 (36-44): numbers 28–36
 *   Row 5 (45-53): [RED] [BLACK] [EVEN] [ODD] [LOW 1-18] [HIGH 19-36] [1st12] [2nd12] [3rd12]
 */
public class RouletteBetUI {

    public static final String TITLE = "§4ルーレット §7- ベット";

    // Slot indices for special items
    public static final int SLOT_ZERO = 0;
    public static final int SLOT_CHIP_10   = 1;
    public static final int SLOT_CHIP_50   = 2;
    public static final int SLOT_CHIP_100  = 3;
    public static final int SLOT_CHIP_500  = 4;
    public static final int SLOT_CHIP_1000 = 5;
    public static final int SLOT_SPIN      = 8;

    public static final int SLOT_RED    = 45;
    public static final int SLOT_BLACK  = 46;
    public static final int SLOT_EVEN   = 47;
    public static final int SLOT_ODD    = 48;
    public static final int SLOT_LOW    = 49;
    public static final int SLOT_HIGH   = 50;
    public static final int SLOT_1ST12  = 51;
    public static final int SLOT_2ND12  = 52;
    public static final int SLOT_3RD12  = 53;

    public static final double[] CHIP_VALUES = {10, 50, 100, 500, 1000};

    private final HimaCasino plugin;
    private final Player player;
    private final Location tableCenter;
    private RouletteGame game;
    private Inventory inventory;
    private double selectedChip = 10;

    public RouletteBetUI(HimaCasino plugin, Player player, Location tableCenter) {
        this.plugin = plugin;
        this.player = player;
        this.tableCenter = tableCenter;
    }

    public void open() {
        game = new RouletteGame(plugin, player, tableCenter);
        plugin.getGameManager().registerRouletteGame(player, game);

        inventory = plugin.getServer().createInventory(null, 54, TITLE);
        buildLayout();
        player.openInventory(inventory);
    }

    private void buildLayout() {
        // Background
        ItemStack bg = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§0", null);
        for (int i = 0; i < 54; i++) inventory.setItem(i, bg);

        // Zero pocket (green)
        inventory.setItem(SLOT_ZERO, makeNumber(0));

        // Chip buttons
        double[] chipVals = CHIP_VALUES;
        int[] chipSlots = {SLOT_CHIP_10, SLOT_CHIP_50, SLOT_CHIP_100, SLOT_CHIP_500, SLOT_CHIP_1000};
        Material[] chipMats = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.NETHERITE_INGOT};
        for (int i = 0; i < chipSlots.length; i++) {
            boolean selected = (chipVals[i] == selectedChip);
            String name = (selected ? "§a§l" : "§e") + "チップ " + formatChip((int) chipVals[i]);
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックで選択");
            if (selected) lore.add("§a§l▶ 選択中");
            inventory.setItem(chipSlots[i], makeItem(chipMats[i], name, lore));
        }

        // Spin button
        inventory.setItem(SLOT_SPIN, makeItem(Material.LIME_CONCRETE,
                "§a§l▶▶ スピン！",
                List.of("§7ベット後にクリックして回転")));

        // Numbers 1–36
        for (int n = 1; n <= 36; n++) {
            inventory.setItem(numberToSlot(n), makeNumber(n));
        }

        // Color / type bets
        inventory.setItem(SLOT_RED,   makeItem(Material.RED_WOOL,   "§c§lREDにベット",   List.of("§71:1配当", "§7現在のチップ: §e" + formatChip((int) selectedChip))));
        inventory.setItem(SLOT_BLACK, makeItem(Material.BLACK_WOOL,  "§8§lBLACKにベット", List.of("§71:1配当", "§7現在のチップ: §e" + formatChip((int) selectedChip))));
        inventory.setItem(SLOT_EVEN,  makeItem(Material.LIGHT_BLUE_WOOL, "§b§lEVEN (偶数)", List.of("§71:1配当")));
        inventory.setItem(SLOT_ODD,   makeItem(Material.YELLOW_WOOL, "§e§lODD (奇数)",   List.of("§71:1配当")));
        inventory.setItem(SLOT_LOW,   makeItem(Material.GREEN_WOOL,  "§a§lLOW (1–18)",  List.of("§71:1配当")));
        inventory.setItem(SLOT_HIGH,  makeItem(Material.ORANGE_WOOL, "§6§lHIGH (19–36)",List.of("§71:1配当")));
        inventory.setItem(SLOT_1ST12, makeItem(Material.PINK_WOOL,   "§d§l1st 12 (1–12)",  List.of("§72:1配当")));
        inventory.setItem(SLOT_2ND12, makeItem(Material.PURPLE_WOOL, "§5§l2nd 12 (13–24)", List.of("§72:1配当")));
        inventory.setItem(SLOT_3RD12, makeItem(Material.CYAN_WOOL,   "§3§l3rd 12 (25–36)", List.of("§72:1配当")));
    }

    private ItemStack makeNumber(int number) {
        Material mat;
        String colorCode;
        if (number == 0) {
            mat = Material.GREEN_STAINED_GLASS_PANE;
            colorCode = "§a";
        } else if (RouletteGame.isRed(number)) {
            mat = Material.RED_STAINED_GLASS_PANE;
            colorCode = "§c";
        } else {
            mat = Material.BLACK_STAINED_GLASS_PANE;
            colorCode = "§8";
        }
        return makeItem(mat, colorCode + "§l" + number,
                List.of("§735:1配当", "§7チップ: §e" + formatChip((int) selectedChip)));
    }

    /** Maps number 1–36 to inventory slot. */
    public static int numberToSlot(int n) {
        // Row 1: 1–9  → slots 9–17
        // Row 2: 10–18 → slots 18–26
        // Row 3: 19–27 → slots 27–35
        // Row 4: 28–36 → slots 36–44
        return 9 + (n - 1);
    }

    /** Returns number (1–36) from slot, or -1 if not a number slot. */
    public static int slotToNumber(int slot) {
        if (slot >= 9 && slot <= 44) {
            int n = slot - 9 + 1;
            if (n >= 1 && n <= 36) return n;
        }
        return -1;
    }

    public void handleClick(int slot) {
        // Chip selection
        double[] chipVals = CHIP_VALUES;
        int[] chipSlots = {SLOT_CHIP_10, SLOT_CHIP_50, SLOT_CHIP_100, SLOT_CHIP_500, SLOT_CHIP_1000};
        for (int i = 0; i < chipSlots.length; i++) {
            if (slot == chipSlots[i]) {
                selectedChip = chipVals[i];
                buildLayout(); // refresh to show selection
                return;
            }
        }

        // Spin
        if (slot == SLOT_SPIN) {
            if (!game.hasAnyBet()) {
                player.sendMessage("§cまずベットしてください！");
                return;
            }
            player.closeInventory();
            game.onStart();
            return;
        }

        // Zero
        if (slot == SLOT_ZERO) {
            game.placeBetOnNumber(0, selectedChip);
            refreshBetInfo();
            return;
        }

        // Number
        int number = slotToNumber(slot);
        if (number != -1) {
            game.placeBetOnNumber(number, selectedChip);
            refreshBetInfo();
            return;
        }

        // Color/type bets
        switch (slot) {
            case SLOT_RED   -> game.placeBetOnColor("red",   selectedChip);
            case SLOT_BLACK -> game.placeBetOnColor("black", selectedChip);
            case SLOT_EVEN  -> placeEvenOddBet(true);
            case SLOT_ODD   -> placeEvenOddBet(false);
            case SLOT_LOW   -> placeHighLowBet(false);
            case SLOT_HIGH  -> placeHighLowBet(true);
            case SLOT_1ST12 -> placeDozens(1);
            case SLOT_2ND12 -> placeDozens(2);
            case SLOT_3RD12 -> placeDozens(3);
        }
        refreshBetInfo();
    }

    private void placeEvenOddBet(boolean even) {
        for (int n = 1; n <= 36; n++) {
            if ((n % 2 == 0) == even) game.placeBetOnNumber(n, selectedChip / 18.0);
        }
        player.sendMessage(String.format("§7%s に §6%.0f %s §7をベット",
                even ? "§bEVEN" : "§eODD", selectedChip,
                plugin.getConfigLoader().getCurrencySymbol()));
    }

    private void placeHighLowBet(boolean high) {
        int start = high ? 19 : 1;
        int end   = high ? 36 : 18;
        for (int n = start; n <= end; n++) game.placeBetOnNumber(n, selectedChip / 18.0);
        player.sendMessage(String.format("§7%s に §6%.0f %s §7をベット",
                high ? "§6HIGH" : "§aLOW", selectedChip,
                plugin.getConfigLoader().getCurrencySymbol()));
    }

    private void placeDozens(int dozen) {
        int start = (dozen - 1) * 12 + 1;
        for (int n = start; n < start + 12; n++) game.placeBetOnNumber(n, selectedChip / 12.0);
        player.sendMessage(String.format("§7%s に §6%.0f %s §7をベット",
                dozen + "st/nd/rd 12", selectedChip,
                plugin.getConfigLoader().getCurrencySymbol()));
    }

    private void refreshBetInfo() {
        // Update spin button with total bet
        inventory.setItem(SLOT_SPIN, makeItem(Material.LIME_CONCRETE,
                "§a§l▶▶ スピン！",
                List.of("§7合計ベット: §e" + formatChip((int) game.getTotalBet()) + " " +
                        plugin.getConfigLoader().getCurrencySymbol(),
                        "§aクリックして回転！")));
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatChip(int value) {
        if (value >= 1000) return (value / 1000) + "k";
        return String.valueOf(value);
    }

    public Inventory getInventory() { return inventory; }
    public RouletteGame getGame() { return game; }
}
