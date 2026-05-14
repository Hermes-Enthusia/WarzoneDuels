package dev.minecraft.warzoneduels.domain.terrain;

import java.util.List;
import java.util.Set;

public record ArenaFootprint(
    String worldName,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ,
    List<FootprintBlock> orderedBlocks,
    Set<Long> packedBlockKeys
) {
    public boolean isEmpty() {
        return orderedBlocks == null || orderedBlocks.isEmpty();
    }

    public boolean contains(int x, int y, int z) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            return false;
        }
        long packed = (((long) (x & 0x3FFFFFF)) << 38)
            | (((long) (z & 0x3FFFFFF)) << 12)
            | (y & 0xFFF);
        return packedBlockKeys.contains(packed);
    }

    public boolean withinBounds(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }
}
