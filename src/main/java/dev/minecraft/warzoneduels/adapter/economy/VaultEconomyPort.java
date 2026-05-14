package dev.minecraft.warzoneduels.adapter.economy;

import dev.minecraft.warzoneduels.port.EconomyPort;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class VaultEconomyPort implements EconomyPort {
    private final Economy economy;
    private final boolean wagersEnabled;

    public VaultEconomyPort(Economy economy, boolean wagersEnabled) {
        this.economy = economy;
        this.wagersEnabled = wagersEnabled;
    }

    @Override
    public boolean isEnabled() {
        return wagersEnabled && economy != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        return isEnabled() && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!isEnabled()) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public void deposit(Player player, double amount) {
        if (!isEnabled()) {
            return;
        }
        economy.depositPlayer(player, amount);
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        if (!isEnabled()) {
            return;
        }
        economy.depositPlayer(org.bukkit.Bukkit.getOfflinePlayer(playerId), amount);
    }
}
