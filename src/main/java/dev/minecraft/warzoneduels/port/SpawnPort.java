package dev.minecraft.warzoneduels.port;

import org.bukkit.Location;

public interface SpawnPort {
    Location resolveSpawnFallback(Location configuredFallback);
}
