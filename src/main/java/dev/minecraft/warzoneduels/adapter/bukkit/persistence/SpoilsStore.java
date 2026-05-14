package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpoilsStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;

    public SpoilsStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spoils.yml");
    }

    public State load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<UUID, SpoilsEntry> entries = new HashMap<>();
        ConfigurationSection entriesSection = yaml.getConfigurationSection("entries");
        if (entriesSection != null) {
            for (String key : entriesSection.getKeys(false)) {
                ConfigurationSection section = entriesSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                UUID entryId = UUID.fromString(key);
                UUID ownerId = UUID.fromString(section.getString("owner-id"));
                UUID sourcePlayerId = UUID.fromString(section.getString("source-player-id"));
                String ownerName = section.getString("owner-name", "Unknown");
                String sourcePlayerName = section.getString("source-player-name", "Unknown");
                long createdAt = section.getLong("created-at");
                long expiresAt = section.getLong("expires-at");
                List<ItemStack> items = new ArrayList<>();
                for (Object object : section.getList("items", List.of())) {
                    if (object instanceof ItemStack item) {
                        items.add(item);
                    }
                }
                entries.put(entryId, new SpoilsEntry(entryId, ownerId, ownerName, sourcePlayerId, sourcePlayerName, createdAt, expiresAt, items));
            }
        }

        Set<UUID> clearOnJoin = new HashSet<>();
        for (String raw : yaml.getStringList("pending-clear-on-join")) {
            clearOnJoin.add(UUID.fromString(raw));
        }
        return new State(entries, clearOnJoin);
    }

    public void save(Collection<SpoilsEntry> entries, Set<UUID> clearOnJoin) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection entriesSection = yaml.createSection("entries");
        for (SpoilsEntry entry : entries) {
            ConfigurationSection section = entriesSection.createSection(entry.entryId().toString());
            section.set("owner-id", entry.ownerId().toString());
            section.set("owner-name", entry.ownerName());
            section.set("source-player-id", entry.sourcePlayerId().toString());
            section.set("source-player-name", entry.sourcePlayerName());
            section.set("created-at", entry.createdAtEpochMs());
            section.set("expires-at", entry.expiresAtEpochMs());
            section.set("items", entry.items());
        }
        yaml.set("pending-clear-on-join", clearOnJoin.stream().map(UUID::toString).toList());
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save spoils store: " + ex.getMessage());
        }
    }

    public record State(Map<UUID, SpoilsEntry> entries, Set<UUID> clearOnJoin) {
    }
}
