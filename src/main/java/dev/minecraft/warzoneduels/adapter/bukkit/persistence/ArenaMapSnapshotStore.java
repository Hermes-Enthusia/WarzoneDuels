package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapSnapshot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ArenaMapSnapshotStore {
    private static final int FORMAT_VERSION = 1;
    private static final String FILE_EXTENSION = ".wdmap.gz";

    private final WarzoneDuelsPlugin plugin;

    public ArenaMapSnapshotStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> saveAsync(ArenaMapSnapshot snapshot, String mapsDirectory, Executor executor) {
        return CompletableFuture.runAsync(() -> save(snapshot, mapsDirectory), executor);
    }

    public CompletableFuture<ArenaMapSnapshot> loadAsync(String mapId, String mapsDirectory, Executor executor) {
        return CompletableFuture.supplyAsync(() -> load(mapId, mapsDirectory), executor);
    }

    public boolean exists(String mapId, String mapsDirectory) {
        return resolve(mapId, mapsDirectory).isFile();
    }

    private void save(ArenaMapSnapshot snapshot, String mapsDirectory) {
        File file = resolve(snapshot.mapId(), mapsDirectory);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create map snapshot directory: " + parent.getAbsolutePath());
        }
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            out.writeInt(FORMAT_VERSION);
            out.writeUTF(snapshot.mapId());
            out.writeUTF(snapshot.worldName());
            out.writeInt(snapshot.footprintBlockCount());
            out.writeInt(snapshot.blockDataEntries().size());
            for (String entry : snapshot.blockDataEntries()) {
                out.writeUTF(entry);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save map snapshot " + snapshot.mapId() + ": " + ex.getMessage(), ex);
        }
    }

    private ArenaMapSnapshot load(String mapId, String mapsDirectory) {
        File file = resolve(mapId, mapsDirectory);
        if (!file.isFile()) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalStateException("Unsupported map snapshot format version " + version);
            }
            String loadedMapId = in.readUTF();
            String worldName = in.readUTF();
            int footprintBlockCount = in.readInt();
            int entryCount = in.readInt();
            List<String> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                entries.add(in.readUTF());
            }
            return new ArenaMapSnapshot(loadedMapId, worldName, footprintBlockCount, List.copyOf(entries));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load map snapshot " + mapId + ": " + ex.getMessage(), ex);
        }
    }

    private File resolve(String mapId, String mapsDirectory) {
        String safeMapId = mapId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return new File(new File(plugin.getDataFolder(), mapsDirectory), safeMapId + FILE_EXTENSION);
    }
}
