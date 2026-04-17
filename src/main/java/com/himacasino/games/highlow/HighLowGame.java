package com.himacasino.games.highlow;

import com.himacasino.HimaCasino;
import com.himacasino.core.GameBase;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * HIGH & LOW card game.
 *
 * Layout (3-row / 27-slot inventory):
 *   Row 0 (0-8):   [bg] [bg] [bg] [bg] [DEALER CARD] [bg] [bg] [bg] [bg]
 *   Row 1 (9-17):  [bg] [CARD A] [bg] [bg] [vs] [bg] [bg] [CARD B] [bg]
 *   Row 2 (18-26): [bg] [bg] [bg] [bg] [RESULT] [bg] [bg] [bg] [bg]
 */
public class HighLowGame extends GameBase {

    public static final String TITLE = "§6HIGH & LOW";

    private static final int CARD_MIN = 1;
    private static final int CARD_MAX = 13;

    // Slot indices
    private static final int SLOT_DEALER = 4;
    private static final int SLOT_CARD_A = 10;
    private static final int SLOT_CARD_B = 16;
    private static final int SLOT_VS     = 13;
    private static final int SLOT_RESULT = 22;

    private int dealerCard;
    private final int[] hiddenCards = new int[2];
    private Inventory inventory;
    private boolean waitingForChoice = false;

    public HighLowGame(HimaCasino plugin, Player player, double betAmount) {
        super(plugin, player, betAmount);
    }

    @Override
    public void onStart() {
        if (!chargeBet()) return;
        state = GameState.RUNNING;

        Random rng = new Random();
        dealerCard = rng.nextInt(CARD_MAX) + CARD_MIN;
        hiddenCards[0] = rng.nextInt(CARD_MAX) + CARD_MIN;
        hiddenCards[1] = rng.nextInt(CARD_MAX) + CARD_MIN;

        buildInventory();
        player.openInventory(inventory);
        waitingForChoice = true;

        player.sendMessage("§6§l╔════════════════════╗");
        player.sendMessage("§6§l║    HIGH & LOW       ║");
        player.sendMessage("§6§l╚════════════════════╝");
        player.sendMessage(String.format("§eディーラーカード: §f§l%s", cardName(dealerCard)));
        player.sendMessage("§7左か右のカードを選んでください！");
    }

    private void buildInventory() {
        inventory = plugin.getServer().createInventory(null, 27, TITLE);

        // Background
        ItemStack bg = makeItem(Material.GRAY_STAINED_GLASS_PANE, "§0", null);
        for (int i = 0; i < 27; i++) inventory.setItem(i, bg);

        // Dealer card (face up)
        inventory.setItem(SLOT_DEALER, makeDealerItem());

        // Two hidden cards (face down)
        inventory.setItem(SLOT_CARD_A, makeHiddenCard("A"));
        inventory.setItem(SLOT_CARD_B, makeHiddenCard("B"));

        // VS label
        inventory.setItem(SLOT_VS, makeItem(Material.BARRIER, "§7§l－ VS －", null));

        // Instruction
        inventory.setItem(SLOT_RESULT, makeItem(Material.BOOK,
                "§e§lどちらが高い？",
                List.of("§7左右どちらかのカードをクリック",
                        String.format("§7賭け金: §e%.0f %s",
                                betAmount, plugin.getConfigLoader().getCurrencySymbol()))));
    }

    private ItemStack makeDealerItem() {
        ItemStack item = new ItemStack(cardMaterial(dealerCard));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§lディーラー: §f§l" + cardName(dealerCard));
        meta.setLore(List.of("§7値: §e" + dealerCard));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeHiddenCard(String label) {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l? カード " + label);
        meta.setLore(List.of("§7クリックして選択！", "§7これが §e" + cardName(dealerCard) + " §7より高い？"));
        item.setItemMeta(meta);
        return item;
    }

    public void onCardChosen(int slot) {
        if (!waitingForChoice || state != GameState.RUNNING) return;

        int chosenIndex;
        if (slot == SLOT_CARD_A) chosenIndex = 0;
        else if (slot == SLOT_CARD_B) chosenIndex = 1;
        else return;

        waitingForChoice = false;
        int chosen = hiddenCards[chosenIndex];
        int other  = hiddenCards[1 - chosenIndex];

        // Reveal both cards with delay
        revealCard(slot, chosen, true);
        int otherSlot = (chosenIndex == 0) ? SLOT_CARD_B : SLOT_CARD_A;
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                revealCard(otherSlot, other, false), 8L);

        // Sound & particles on selection
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.8f);
        player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0, 1.5, 0), 10, 0.4, 0.4, 0.4, 0.1);

        player.sendMessage(String.format("§eあなたのカード: §f§l%s (値: %d)", cardName(chosen), chosen));

        // Evaluate after reveal animation
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (chosen > dealerCard) {
                showResultOverlay(true);
                onWin(plugin.getConfigLoader().getHighLowWinMultiplier());
            } else if (chosen < dealerCard) {
                showResultOverlay(false);
                onLoss();
            } else {
                showResultOverlay(null);
                onDraw();
            }
        }, 20L);
    }

    private void revealCard(int slot, int value, boolean chosen) {
        if (inventory == null) return;
        ItemStack item = new ItemStack(cardMaterial(value));
        ItemMeta meta = item.getItemMeta();
        String prefix = chosen ? "§a§l" : "§7";
        meta.setDisplayName(prefix + cardName(value));
        List<String> lore = new ArrayList<>();
        lore.add("§7値: §e" + value);
        if (chosen) lore.add("§a§l◀ あなたの選択");
        meta.setLore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void showResultOverlay(Boolean win) {
        if (inventory == null) return;
        ItemStack result;
        if (Boolean.TRUE.equals(win)) {
            result = makeItem(Material.GOLD_INGOT, "§a§l★ 勝利！★",
                    List.of(String.format("§e+%.0f %s", betAmount * plugin.getConfigLoader().getHighLowWinMultiplier(),
                            plugin.getConfigLoader().getCurrencySymbol())));
        } else if (Boolean.FALSE.equals(win)) {
            result = makeItem(Material.BARRIER, "§c§l✗ 敗北...",
                    List.of(String.format("§c-%.0f %s", betAmount, plugin.getConfigLoader().getCurrencySymbol())));
        } else {
            result = makeItem(Material.PAPER, "§7§l引き分け",
                    List.of("§7賭け金を返還"));
        }
        inventory.setItem(SLOT_RESULT, result);
    }

    private void onDraw() {
        state = GameState.FINISHED;
        plugin.getEconomyManager().deposit(player, betAmount);
        player.sendMessage("§7§l引き分け！ 賭け金を返還します。");
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 80L);
    }

    @Override
    public void onTick() {
        // Event-driven; no tick loop needed
    }

    @Override
    public void onWin(double multiplier) {
        state = GameState.WIN;
        payWinnings(multiplier);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 25, 0.8, 0.5, 0.8, 0);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 80L);
    }

    @Override
    public void onLoss() {
        state = GameState.LOSS;
        player.sendMessage(String.format("§c§l敗北... §7賭け金 §e%.0f %s §7を失いました。",
                betAmount, plugin.getConfigLoader().getCurrencySymbol()));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        plugin.getServer().getScheduler().runTaskLater(plugin, this::cleanup, 80L);
    }

    @Override
    public void cleanup() {
        stopTickTask();
        if (player.getOpenInventory().getTitle().equals(TITLE)) {
            player.closeInventory();
        }
        plugin.getGameManager().removeHighLowGame(player);
        state = GameState.FINISHED;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String cardName(int v) {
        return switch (v) {
            case 1  -> "A (エース)";
            case 11 -> "J (ジャック)";
            case 12 -> "Q (クイーン)";
            case 13 -> "K (キング)";
            default -> String.valueOf(v);
        };
    }

    private Material cardMaterial(int v) {
        return switch (v) {
            case 1  -> Material.DIAMOND;
            case 2  -> Material.LAPIS_LAZULI;
            case 3  -> Material.EMERALD;
            case 4  -> Material.REDSTONE;
            case 5  -> Material.GOLD_NUGGET;
            case 6  -> Material.IRON_NUGGET;
            case 7  -> Material.QUARTZ;
            case 8  -> Material.PRISMARINE_CRYSTALS;
            case 9  -> Material.AMETHYST_SHARD;
            case 10 -> Material.COPPER_INGOT;
            case 11 -> Material.IRON_INGOT;
            case 12 -> Material.GOLD_INGOT;
            case 13 -> Material.NETHERITE_SCRAP;
            default -> Material.PAPER;
        };
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public Inventory getInventory() { return inventory; }
    public boolean isWaitingForChoice() { return waitingForChoice; }
}
