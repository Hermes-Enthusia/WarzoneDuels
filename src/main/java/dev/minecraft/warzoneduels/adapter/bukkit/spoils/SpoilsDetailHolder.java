package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import java.util.UUID;

public final class SpoilsDetailHolder extends AbstractSpoilsHolder {
    private final UUID entryUuid;
    private final int pageNumber;

    public SpoilsDetailHolder(UUID ownerId, UUID entryId, int page) {
        super(ownerId);
        this.entryUuid = entryId;
        this.pageNumber = page;
    }

    public UUID entryId() {
        return entryUuid;
    }

    public int page() {
        return pageNumber;
    }
}
