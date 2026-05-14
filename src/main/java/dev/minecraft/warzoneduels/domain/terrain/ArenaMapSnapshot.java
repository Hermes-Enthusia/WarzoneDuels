package dev.minecraft.warzoneduels.domain.terrain;

import java.util.List;

public record ArenaMapSnapshot(
    String mapId,
    String worldName,
    int footprintBlockCount,
    List<String> blockDataEntries
) {
}
