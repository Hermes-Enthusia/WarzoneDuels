package dev.minecraft.warzoneduels.port;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface EconomyPort {
    boolean isEnabled();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    void deposit(Player player, double amount);

    void deposit(UUID playerId, double amount);
}
