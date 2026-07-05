package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("PMD.DoNotUseThreads")
public final class SpoilsStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;
    private final ExecutorService writer;
    private final Object saveLock = new Object();

    public SpoilsStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spoils.yml");
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarzoneDuels-SpoilsStore");
            thread.setDaemon(true);
            return thread;
        });
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
                UUID entryId = safeUuid(key);
                UUID ownerId = safeUuid(section.getString("owner-id"));
                UUID sourcePlayerId = safeUuid(section.getString("source-player-id"));
                if (entryId == null || ownerId == null || sourcePlayerId == null) {
                    plugin.getLogger().warning("Skipping corrupt duel vault entry '" + key + "'.");
                    continue;
                }
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
            UUID playerId = safeUuid(raw);
            if (playerId != null) {
                clearOnJoin.add(playerId);
            }
        }
        return new State(entries, clearOnJoin);
    }

    public void save(Collection<SpoilsEntry> entries, Set<UUID> clearOnJoin) {
        saveSnapshot(snapshotEntries(entries), Set.copyOf(clearOnJoin));
    }

    public void saveAsync(Collection<SpoilsEntry> entries, Set<UUID> clearOnJoin) {
        List<SpoilsEntry> entrySnapshot = snapshotEntries(entries);
        Set<UUID> clearOnJoinSnapshot = Set.copyOf(clearOnJoin);
        try {
            writer.execute(() -> saveSnapshot(entrySnapshot, clearOnJoinSnapshot));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Duel vault save skipped because the writer is shutting down.");
        }
    }

    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private List<SpoilsEntry> snapshotEntries(Collection<SpoilsEntry> entries) {
        List<SpoilsEntry> snapshot = new ArrayList<>(entries.size());
        for (SpoilsEntry entry : entries) {
            snapshot.add(new SpoilsEntry(
                entry.entryId(),
                entry.ownerId(),
                entry.ownerName(),
                entry.sourcePlayerId(),
                entry.sourcePlayerName(),
                entry.createdAtEpochMs(),
                entry.expiresAtEpochMs(),
                entry.items()
            ));
        }
        return snapshot;
    }

    private void saveSnapshot(Collection<SpoilsEntry> entries, Set<UUID> clearOnJoin) {
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
        saveAtomic(yaml);
    }

    private void saveAtomic(YamlConfiguration yaml) {
        synchronized (saveLock) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Failed to create " + parent.getAbsolutePath());
                }
                File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
                yaml.save(temporary);
                try {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Failed to save spoils store: " + ex.getMessage());
            }
        }
    }

    private UUID safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record State(Map<UUID, SpoilsEntry> entries, Set<UUID> clearOnJoin) {
    }
}
