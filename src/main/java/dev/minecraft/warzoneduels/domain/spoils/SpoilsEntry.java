package dev.minecraft.warzoneduels.domain.spoils;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpoilsEntry {
    private final UUID entryUuid;
    private final UUID ownerUuid;
    private final String ownerDisplayName;
    private final UUID sourcePlayerUuid;
    private final String sourcePlayerDisplayName;
    private final long createdAtMillis;
    private final long expiresAtMillis;
    private final List<ItemStack> storedItems;

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
        this.entryUuid = entryId;
        this.ownerUuid = ownerId;
        this.ownerDisplayName = ownerName;
        this.sourcePlayerUuid = sourcePlayerId;
        this.sourcePlayerDisplayName = sourcePlayerName;
        this.createdAtMillis = createdAtEpochMs;
        this.expiresAtMillis = expiresAtEpochMs;
        this.storedItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                this.storedItems.add(item.clone());
            }
        }
    }

    public UUID entryId() {
        return entryUuid;
    }

    public UUID ownerId() {
        return ownerUuid;
    }

    public String ownerName() {
        return ownerDisplayName;
    }

    public UUID sourcePlayerId() {
        return sourcePlayerUuid;
    }

    public String sourcePlayerName() {
        return sourcePlayerDisplayName;
    }

    public long createdAtEpochMs() {
        return createdAtMillis;
    }

    public long expiresAtEpochMs() {
        return expiresAtMillis;
    }

    public List<ItemStack> items() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack item : storedItems) {
            copy.add(item == null ? null : item.clone());
        }
        return copy;
    }

    public int itemCount() {
        return storedItems.size();
    }

    public boolean isEmpty() {
        return storedItems.isEmpty();
    }

    public List<ItemStack> mutableItems() {
        return storedItems;
    }
}
