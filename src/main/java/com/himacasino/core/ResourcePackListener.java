package com.himacasino.core;

import com.himacasino.HimaCasino;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Sends the HimaCasino resource pack (blackjack card graphics, GUI icons) to players
 * on join, when {@code resource-pack.enabled} is set in config.yml.
 */
public class ResourcePackListener implements Listener {

    private final HimaCasino plugin;

    public ResourcePackListener(HimaCasino plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ConfigLoader cfg = plugin.getConfigLoader();
        if (!cfg.isResourcePackEnabled()) return;

        String url = cfg.getResourcePackUrl();
        if (url == null || url.isBlank()) {
            plugin.getLogger().warning("resource-pack.enabled は true ですが、resource-pack.url が未設定です。");
            return;
        }

        Player player = event.getPlayer();
        byte[] hash = parseSha1(cfg.getResourcePackSha1());
        String prompt = ChatColor.translateAlternateColorCodes('&', cfg.getResourcePackPromptMessage());
        boolean force = cfg.isResourcePackForced();

        // hash is @Nullable on this Bukkit API overload; passing new byte[0] instead of null
        // here throws IllegalArgumentException ("hash should be 20 bytes long but was 0") on
        // recent Paper builds when resource-pack.sha1 isn't configured.
        player.setResourcePack(url, hash, prompt, force);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (!plugin.getConfigLoader().isResourcePackEnabled()) return;

        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        plugin.getLogger().info(String.format(
                "[ResourcePack] %s: %s", event.getPlayer().getName(), status));

        switch (status) {
            case FAILED_DOWNLOAD -> plugin.getLogger().warning(
                    "リソースパックのダウンロードに失敗しました。resource-pack.url がZIPファイルへの直接リンクか確認してください: "
                            + event.getPlayer().getName());
            case INVALID_URL -> plugin.getLogger().warning(
                    "resource-pack.url が無効です。設定を確認してください: " + event.getPlayer().getName());
            case DECLINED -> plugin.getLogger().warning(
                    "プレイヤーがリソースパックを拒否しました: " + event.getPlayer().getName());
            default -> { /* ACCEPTED / DOWNLOADED / SUCCESSFULLY_LOADED はログのみ */ }
        }
    }

    private byte[] parseSha1(String hex) {
        if (hex == null || hex.isBlank()) return null;
        String clean = hex.trim();
        if (clean.length() != 40) return null;
        byte[] bytes = new byte[20];
        try {
            for (int i = 0; i < 20; i++) {
                bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("resource-pack.sha1 の形式が不正です。無視します: " + hex);
            return null;
        }
        return bytes;
    }
}
