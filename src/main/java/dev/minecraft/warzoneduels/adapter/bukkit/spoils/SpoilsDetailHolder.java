package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import java.util.UUID;

public final class SpoilsDetailHolder extends AbstractSpoilsHolder {
    private final UUID entryId;
    private final int page;

    public SpoilsDetailHolder(UUID ownerId, UUID entryId, int page) {
        super(ownerId);
        this.entryId = entryId;
        this.page = page;
    }

    public UUID entryId() {
        return entryId;
    }

    public int page() {
        return page;
    }
}
