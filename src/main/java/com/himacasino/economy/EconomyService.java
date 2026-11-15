package com.himacasino.economy;

import com.himacasino.config.CasinoConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 経済連携。Common Economy API (任意) があればそれを使い、無ければ「残高を扱わない無料モード」。
 * Vault の代わりです。
 */
public class EconomyService {

    /** プレイヤー 1 人分の口座 */
    public interface Account {
        double balance();
        boolean withdraw(double amount);
        void deposit(double amount);
    }

    private final CasinoConfig config;
    private final boolean ceaLoaded;

    public EconomyService(CasinoConfig config) {
        this.config = config;
        this.ceaLoaded = FabricLoader.getInstance().isModLoaded("common-economy-api");
    }

    public String describe() {
        return switch (config.economy.mode) {
            case "free" -> "無料モード (残高を扱いません)";
            default -> ceaLoaded ? "Common Economy API と連携します" : "Common Economy API が無いため無料モードで動作します";
        };
    }

    /** null のときは無料モード */
    public Account account(ServerPlayerEntity player) {
        if ("free".equals(config.economy.mode) || !ceaLoaded) return null;
        try {
            return CeaBackend.account(player, config.economy.currencyId);
        } catch (Throwable t) {
            return null;
        }
    }
}
