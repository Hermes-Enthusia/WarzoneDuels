package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import java.util.List;
import java.util.UUID;

public final class SpoilsOverviewHolder extends AbstractSpoilsHolder {
    private final List<UUID> entryIds;
    private final int page;

    public SpoilsOverviewHolder(UUID ownerId, List<UUID> entryIds, int page) {
        super(ownerId);
        this.entryIds = entryIds;
        this.page = page;
    }

    public List<UUID> entryIds() {
        return entryIds;
    }

    public int page() {
        return page;
    }
}
