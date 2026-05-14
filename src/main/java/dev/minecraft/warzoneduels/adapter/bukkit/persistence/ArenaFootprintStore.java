package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.terrain.ArenaFootprint;
import dev.minecraft.warzoneduels.domain.terrain.FootprintBlock;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ArenaFootprintStore {
    private final WarzoneDuelsPlugin plugin;

    public ArenaFootprintStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public void ensureBundledDefault(String relativePath) {
        File file = resolve(relativePath);
        if (!file.exists()) {
            plugin.saveResource("arena-footprint.yml", false);
        }
    }

    public ArenaFootprint load(String relativePath) {
        File file = resolve(relativePath);
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String worldName = yaml.getString("world", "").trim();
        if (worldName.isBlank()) {
            return null;
        }
        List<String> blocks = yaml.getStringList("absolute_blocks");
        if (blocks.isEmpty()) {
            return null;
        }
        int minX = yaml.getInt("bounds.min.x");
        int minY = yaml.getInt("bounds.min.y");
        int minZ = yaml.getInt("bounds.min.z");
        int maxX = yaml.getInt("bounds.max.x");
        int maxY = yaml.getInt("bounds.max.y");
        int maxZ = yaml.getInt("bounds.max.z");

        List<FootprintBlock> ordered = new ArrayList<>(blocks.size());
        Set<Long> packed = new HashSet<>(blocks.size() * 2);
        for (String raw : blocks) {
            String[] parts = raw.split(",");
            if (parts.length != 3) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                int z = Integer.parseInt(parts[2].trim());
                FootprintBlock block = new FootprintBlock(x, y, z);
                ordered.add(block);
                packed.add(block.packedKey());
            } catch (NumberFormatException ignored) {
            }
        }
        if (ordered.isEmpty()) {
            return null;
        }
        return new ArenaFootprint(worldName.toLowerCase(Locale.ROOT), minX, minY, minZ, maxX, maxY, maxZ, List.copyOf(ordered), Set.copyOf(packed));
    }

    private File resolve(String relativePath) {
        String normalized = relativePath == null || relativePath.isBlank() ? "arena-footprint.yml" : relativePath;
        return new File(plugin.getDataFolder(), normalized.replace("/", File.separator).replace("\\", File.separator));
    }
}
