package dev.minecraft.warzoneduels;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class BlockKey {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;

    public BlockKey(UUID worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockKey fromLocation(Location location) {
        World world = location.getWorld();
        UUID worldId = world == null ? new UUID(0L, 0L) : world.getUID();
        return new BlockKey(worldId, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public UUID getWorldId() {
        return worldId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockKey blockKey = (BlockKey) obj;
        return x == blockKey.x && y == blockKey.y && z == blockKey.z && Objects.equals(worldId, blockKey.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
