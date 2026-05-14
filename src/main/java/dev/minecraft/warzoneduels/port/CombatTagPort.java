package dev.minecraft.warzoneduels.port;

import org.bukkit.entity.Player;

public interface CombatTagPort {
    default void enable() {
    }

    default void disable() {
    }

    default boolean isInCombat(Player player) {
        return false;
    }

    default void clearCombatState(Player player) {
    }
}
