package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public abstract class AbstractSpoilsHolder implements InventoryHolder {
    private final UUID ownerUuid;

    protected AbstractSpoilsHolder(UUID ownerId) {
        this.ownerUuid = ownerId;
    }

    public UUID ownerId() {
        return ownerUuid;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
