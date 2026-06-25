package dev.minecraft.warzoneduels.adapter.bukkit.reset;

import dev.minecraft.warzoneduels.BlockKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ArenaSnapshot {
    private final Map<BlockKey, SavedBlockState> savedBlocks;

    public ArenaSnapshot(Map<BlockKey, SavedBlockState> blocks) {
        this.savedBlocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }

    public Map<BlockKey, SavedBlockState> blocks() {
        return savedBlocks;
    }

    public boolean isEmpty() {
        return savedBlocks.isEmpty();
    }
}
