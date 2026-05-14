package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public abstract class AbstractSpoilsHolder implements InventoryHolder {
    private final UUID ownerId;

    protected AbstractSpoilsHolder(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
