package dev.minecraft.warzoneduels.domain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class ArenaDefinition {
    private final String worldName;
    private final Location pos1;
    private final Location pos2;
    private final Location spawn1;
    private final Location spawn2;
    private final Location spectator;
    private final Location exit;

    public ArenaDefinition(
        String worldName,
        Location pos1,
        Location pos2,
        Location spawn1,
        Location spawn2,
        Location spectator,
        Location exit
    ) {
        this.worldName = worldName;
        this.pos1 = pos1.clone();
        this.pos2 = pos2.clone();
        this.spawn1 = spawn1.clone();
        this.spawn2 = spawn2.clone();
        this.spectator = spectator.clone();
        this.exit = exit.clone();
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public Location pos1() {
        return pos1.clone();
    }

    public Location pos2() {
        return pos2.clone();
    }

    public Location spawn1() {
        return spawn1.clone();
    }

    public Location spawn2() {
        return spawn2.clone();
    }

    public Location spectator() {
        return spectator.clone();
    }

    public Location exit() {
        return exit.clone();
    }

    public boolean isReady() {
        return world() != null;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
