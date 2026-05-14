package dev.minecraft.warzoneduels.domain.spoils;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpoilsEntry {
    private final UUID entryId;
    private final UUID ownerId;
    private final String ownerName;
    private final UUID sourcePlayerId;
    private final String sourcePlayerName;
    private final long createdAtEpochMs;
    private final long expiresAtEpochMs;
    private final List<ItemStack> items;

    public SpoilsEntry(
        UUID entryId,
        UUID ownerId,
        String ownerName,
        UUID sourcePlayerId,
        String sourcePlayerName,
        long createdAtEpochMs,
        long expiresAtEpochMs,
        List<ItemStack> items
    ) {
        this.entryId = entryId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.sourcePlayerId = sourcePlayerId;
        this.sourcePlayerName = sourcePlayerName;
        this.createdAtEpochMs = createdAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.items = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                this.items.add(item.clone());
            }
        }
    }

    public UUID entryId() {
        return entryId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public UUID sourcePlayerId() {
        return sourcePlayerId;
    }

    public String sourcePlayerName() {
        return sourcePlayerName;
    }

    public long createdAtEpochMs() {
        return createdAtEpochMs;
    }

    public long expiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public List<ItemStack> items() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : items) {
            copy.add(item == null ? null : item.clone());
        }
        return copy;
    }

    public int itemCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<ItemStack> mutableItems() {
        return items;
    }
}
