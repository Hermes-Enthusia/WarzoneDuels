package dev.minecraft.warzoneduels.domain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class ArenaDefinition {
    private final String arenaWorldName;
    private final Location firstCorner;
    private final Location secondCorner;
    private final Location firstSpawn;
    private final Location secondSpawn;
    private final Location spectatorLocation;
    private final Location exitLocation;

    public ArenaDefinition(
        String worldName,
        Location pos1,
        Location pos2,
        Location spawn1,
        Location spawn2,
        Location spectator,
        Location exit
    ) {
        this.arenaWorldName = worldName;
        this.firstCorner = pos1.clone();
        this.secondCorner = pos2.clone();
        this.firstSpawn = spawn1.clone();
        this.secondSpawn = spawn2.clone();
        this.spectatorLocation = spectator.clone();
        this.exitLocation = exit.clone();
    }

    public String worldName() {
        return arenaWorldName;
    }

    public World world() {
        return Bukkit.getWorld(arenaWorldName);
    }

    public Location pos1() {
        return firstCorner.clone();
    }

    public Location pos2() {
        return secondCorner.clone();
    }

    public Location spawn1() {
        return firstSpawn.clone();
    }

    public Location spawn2() {
        return secondSpawn.clone();
    }

    public Location spectator() {
        return spectatorLocation.clone();
    }

    public Location exit() {
        return exitLocation.clone();
    }

    public boolean isReady() {
        return world() != null;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaWorldName)) {
            return false;
        }
        int minX = Math.min(firstCorner.getBlockX(), secondCorner.getBlockX());
        int maxX = Math.max(firstCorner.getBlockX(), secondCorner.getBlockX());
        int minY = Math.min(firstCorner.getBlockY(), secondCorner.getBlockY());
        int maxY = Math.max(firstCorner.getBlockY(), secondCorner.getBlockY());
        int minZ = Math.min(firstCorner.getBlockZ(), secondCorner.getBlockZ());
        int maxZ = Math.max(firstCorner.getBlockZ(), secondCorner.getBlockZ());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
