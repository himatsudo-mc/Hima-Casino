package com.himacasino.economy;

import eu.pb4.common.economy.api.CommonEconomy;
import eu.pb4.common.economy.api.EconomyAccount;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;

/** Common Economy API 呼び出し専用 (mod が無い環境ではロードされない)。 */
final class CeaBackend {

    private CeaBackend() {}

    static EconomyService.Account account(ServerPlayerEntity player, String currencyId) {
        Collection<EconomyAccount> accounts = CommonEconomy.getAccounts(player);
        EconomyAccount chosen = null;
        for (EconomyAccount a : accounts) {
            if (currencyId == null || currencyId.isBlank() || a.currency().id().toString().equals(currencyId)) {
                chosen = a;
                break;
            }
        }
        if (chosen == null) return null;
        final EconomyAccount acc = chosen;
        return new EconomyService.Account() {
            @Override public double balance() { return acc.balance(); }

            @Override public boolean withdraw(double amount) {
                return acc.decreaseBalance(Math.round(amount)).isSuccessful();
            }

            @Override public void deposit(double amount) {
                acc.increaseBalance(Math.round(amount));
            }
        };
    }
}
