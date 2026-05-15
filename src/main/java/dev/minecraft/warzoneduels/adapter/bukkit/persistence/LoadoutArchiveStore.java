package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.LoadoutSnapshot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public final class LoadoutArchiveStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;
    private final Map<UUID, ArchivedLoadout> archives = new ConcurrentHashMap<>();
    private final ExecutorService writer;
    private final Object saveLock = new Object();
    private volatile boolean loaded;

    public LoadoutArchiveStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loadout-archives.yml");
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarzoneDuels-LoadoutArchiveStore");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void enable() {
        archives.clear();
        archives.putAll(loadAll());
        loaded = true;
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
        saveSnapshot(snapshotArchives());
    }

    public LoadoutSnapshot capture(Player player) {
        return new LoadoutSnapshot(
            player.getInventory().getContents(),
            player.getInventory().getArmorContents(),
            player.getInventory().getItemInOffHand(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getTotalExperience(),
            player.getLevel(),
            player.getExp(),
            player.getFireTicks(),
            new ArrayList<>(player.getActivePotionEffects())
        );
    }

    public void saveLatestPreDuel(Player player, LoadoutSnapshot snapshot) {
        ensureLoaded();
        archives.put(player.getUniqueId(), new ArchivedLoadout(player.getUniqueId(), player.getName(), System.currentTimeMillis(), copySnapshot(snapshot)));
        saveAsync();
    }

    public LoadoutSnapshot loadLatestPreDuel(UUID playerId) {
        ensureLoaded();
        ArchivedLoadout archived = archives.get(playerId);
        return archived == null ? null : copySnapshot(archived.snapshot());
    }

    public void apply(Player player, LoadoutSnapshot snapshot) {
        player.getInventory().setContents(snapshot.contents());
        player.getInventory().setArmorContents(snapshot.armor());
        player.getInventory().setItemInOffHand(snapshot.offHand());
        player.setHealth(Math.max(1.0D, Math.min(player.getMaxHealth(), snapshot.health())));
        player.setFoodLevel(snapshot.foodLevel());
        player.setSaturation(snapshot.saturation());
        player.setTotalExperience(snapshot.totalExperience());
        player.setLevel(snapshot.level());
        player.setExp(snapshot.expProgress());
        player.setFireTicks(snapshot.fireTicks());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : snapshot.potionEffects()) {
            player.addPotionEffect(effect, true);
        }
        player.updateInventory();
    }

    private void saveAsync() {
        List<ArchivedLoadout> snapshot = snapshotArchives();
        try {
            writer.execute(() -> saveSnapshot(snapshot));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Loadout archive save skipped because the writer is shutting down.");
        }
    }

    private List<ArchivedLoadout> snapshotArchives() {
        return archives.values().stream()
            .map(archive -> new ArchivedLoadout(archive.playerId(), archive.name(), archive.capturedAtEpochMs(), copySnapshot(archive.snapshot())))
            .toList();
    }

    private Map<UUID, ArchivedLoadout> loadAll() {
        Map<UUID, ArchivedLoadout> loadedArchives = new ConcurrentHashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return loadedArchives;
        }
        for (String key : players.getKeys(false)) {
            UUID playerId = safeUuid(key);
            ConfigurationSection section = players.getConfigurationSection(key);
            if (playerId == null || section == null) {
                continue;
            }
            ConfigurationSection snapshotSection = section.getConfigurationSection("snapshot");
            if (snapshotSection == null) {
                continue;
            }
            loadedArchives.put(playerId, new ArchivedLoadout(
                playerId,
                section.getString("name", "Unknown"),
                section.getLong("captured-at"),
                readSnapshot(snapshotSection)
            ));
        }
        return loadedArchives;
    }

    private void saveSnapshot(List<ArchivedLoadout> snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("players");
        for (ArchivedLoadout archive : snapshot) {
            String base = archive.playerId().toString();
            players.set(base + ".name", archive.name());
            players.set(base + ".captured-at", archive.capturedAtEpochMs());
            writeSnapshot(players.createSection(base + ".snapshot"), archive.snapshot());
        }
        saveAtomic(yaml);
    }

    private void writeSnapshot(ConfigurationSection section, LoadoutSnapshot snapshot) {
        section.set("contents", snapshot.contents());
        section.set("armor", snapshot.armor());
        section.set("offhand", snapshot.offHand());
        section.set("health", snapshot.health());
        section.set("food", snapshot.foodLevel());
        section.set("saturation", snapshot.saturation());
        section.set("total-exp", snapshot.totalExperience());
        section.set("level", snapshot.level());
        section.set("exp-progress", snapshot.expProgress());
        section.set("fire-ticks", snapshot.fireTicks());
        section.set("effects", snapshot.potionEffects());
    }

    private LoadoutSnapshot readSnapshot(ConfigurationSection section) {
        List<PotionEffect> effects = new ArrayList<>();
        Object rawEffects = section.get("effects");
        if (rawEffects instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof PotionEffect effect) {
                    effects.add(effect);
                }
            }
        }
        return new LoadoutSnapshot(
            castItemStackArray(section.get("contents")),
            castItemStackArray(section.get("armor")),
            section.getItemStack("offhand"),
            section.getDouble("health"),
            section.getInt("food"),
            (float) section.getDouble("saturation"),
            section.getInt("total-exp"),
            section.getInt("level"),
            (float) section.getDouble("exp-progress"),
            section.getInt("fire-ticks"),
            effects
        );
    }

    private ItemStack[] castItemStackArray(Object raw) {
        if (raw instanceof ItemStack[] array) {
            return array;
        }
        if (raw instanceof List<?> list) {
            ItemStack[] stacks = new ItemStack[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object entry = list.get(i);
                stacks[i] = entry instanceof ItemStack stack ? stack : null;
            }
            return stacks;
        }
        return new ItemStack[0];
    }

    private LoadoutSnapshot copySnapshot(LoadoutSnapshot snapshot) {
        return new LoadoutSnapshot(
            snapshot.contents(),
            snapshot.armor(),
            snapshot.offHand(),
            snapshot.health(),
            snapshot.foodLevel(),
            snapshot.saturation(),
            snapshot.totalExperience(),
            snapshot.level(),
            snapshot.expProgress(),
            snapshot.fireTicks(),
            snapshot.potionEffects()
        );
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
                plugin.getLogger().warning("Failed to save loadout archive file: " + ex.getMessage());
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

    private void ensureLoaded() {
        if (!loaded) {
            enable();
        }
    }

    private record ArchivedLoadout(UUID playerId, String name, long capturedAtEpochMs, LoadoutSnapshot snapshot) {
    }
}
