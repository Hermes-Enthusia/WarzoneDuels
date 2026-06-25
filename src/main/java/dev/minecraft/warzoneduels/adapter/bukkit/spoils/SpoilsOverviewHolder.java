package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import java.util.List;
import java.util.UUID;

public final class SpoilsOverviewHolder extends AbstractSpoilsHolder {
    private final List<UUID> entryUuids;
    private final int pageNumber;

    public SpoilsOverviewHolder(UUID ownerId, List<UUID> entryIds, int page) {
        super(ownerId);
        this.entryUuids = entryIds;
        this.pageNumber = page;
    }

    public List<UUID> entryIds() {
        return entryUuids;
    }

    public int page() {
        return pageNumber;
    }
}
