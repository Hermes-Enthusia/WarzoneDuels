package dev.minecraft.warzoneduels.adapter.bukkit.reset;

import dev.minecraft.warzoneduels.BlockKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ArenaSnapshot {
    private final Map<BlockKey, SavedBlockState> blocks;

    public ArenaSnapshot(Map<BlockKey, SavedBlockState> blocks) {
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
    }

    public Map<BlockKey, SavedBlockState> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
