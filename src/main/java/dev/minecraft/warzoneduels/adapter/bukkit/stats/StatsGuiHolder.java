package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class StatsGuiHolder implements InventoryHolder {
    private final StatsGuiType holderType;
    private final UUID targetPlayerUuid;
    private final int pageNumber;

    public StatsGuiHolder(StatsGuiType type, UUID targetPlayerId, int page) {
        this.holderType = type;
        this.targetPlayerUuid = targetPlayerId;
        this.pageNumber = page;
    }

    public StatsGuiType type() {
        return holderType;
    }

    public UUID targetPlayerId() {
        return targetPlayerUuid;
    }

    public int page() {
        return pageNumber;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
