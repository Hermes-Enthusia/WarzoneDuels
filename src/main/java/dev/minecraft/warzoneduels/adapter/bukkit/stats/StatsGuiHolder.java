package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class StatsGuiHolder implements InventoryHolder {
    private final StatsGuiType type;
    private final UUID targetPlayerId;
    private final int page;

    public StatsGuiHolder(StatsGuiType type, UUID targetPlayerId, int page) {
        this.type = type;
        this.targetPlayerId = targetPlayerId;
        this.page = page;
    }

    public StatsGuiType type() {
        return type;
    }

    public UUID targetPlayerId() {
        return targetPlayerId;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
