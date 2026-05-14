package dev.minecraft.warzoneduels.domain.terrain;

public record ArenaMapOperationStatus(
    boolean busy,
    String type,
    String mapId,
    long processedBlocks,
    long totalBlocks
) {
    public static ArenaMapOperationStatus idle(String currentMapId) {
        return new ArenaMapOperationStatus(false, "idle", currentMapId, 0, 0);
    }
}
