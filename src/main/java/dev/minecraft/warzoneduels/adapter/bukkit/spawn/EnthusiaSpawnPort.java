package dev.minecraft.warzoneduels.adapter.bukkit.spawn;

import dev.minecraft.warzoneduels.port.SpawnPort;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EnthusiaSpawnPort implements SpawnPort {
    private static final String TELEPORT_PLUGIN = "EnthusiaTeleport";

    private final Logger logger;

    public EnthusiaSpawnPort(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Location resolveSpawnFallback(Location configuredFallback) {
        Location fromPlugin = resolveFromEnthusiaTeleport();
        if (fromPlugin != null) {
            return fromPlugin;
        }
        if (configuredFallback != null) {
            return configuredFallback.clone();
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private Location resolveFromEnthusiaTeleport() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(TELEPORT_PLUGIN);
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Class<?> pluginClass = Class.forName("org.enthusia.teleport.EnthusiaTeleportPlugin");
            Method getInstance = pluginClass.getMethod("getInstance");
            Object pluginInstance = getInstance.invoke(null);
            if (pluginInstance == null) {
                return null;
            }
            Method getSpawnManager = pluginClass.getMethod("getSpawnManager");
            Object spawnManager = getSpawnManager.invoke(pluginInstance);
            if (spawnManager == null) {
                return null;
            }
            Method getSpawnLocation = spawnManager.getClass().getMethod("getSpawnLocation");
            Object location = getSpawnLocation.invoke(spawnManager);
            if (location instanceof Location resolved) {
                return resolved.clone();
            }
        } catch (ReflectiveOperationException ex) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.warning("Failed to resolve spawn from EnthusiaTeleport, falling back to config: " + ex.getMessage());
            }
        }
        return null;
    }
}
