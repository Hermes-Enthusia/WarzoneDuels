package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.ArenaFootprintStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.ArenaMapSnapshotStore;
import dev.minecraft.warzoneduels.domain.terrain.ArenaFootprint;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapOperationStatus;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapSnapshot;
import dev.minecraft.warzoneduels.domain.terrain.FootprintBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings("PMD.DoNotUseThreads")
public final class ArenaTerrainService {
    private static final String OPERATION_RESTORE = "restore";
    private static final List<String> SUPPORT_SENSITIVE_SUFFIXES = List.of(
        "_BUTTON",
        "_TORCH",
        "_WALL_TORCH",
        "_SIGN",
        "_HANGING_SIGN",
        "_WALL_SIGN",
        "_RAIL",
        "_BANNER",
        "_WALL_BANNER",
        "_SAPLING",
        "_FLOWER",
        "_TULIP",
        "_MUSHROOM",
        "_CORAL",
        "_FAN",
        "_PRESSURE_PLATE",
        "_CARPET",
        "_POT",
        "_HEAD",
        "_SKULL",
        "_VINE",
        "_CANDLE",
        "_BUSH"
    );
    private static final Set<Material> SUPPORT_SENSITIVE_MATERIALS = Set.of(
        Material.CACTUS,
        Material.DEAD_BUSH,
        Material.SUGAR_CANE,
        Material.TALL_GRASS,
        Material.SHORT_GRASS,
        Material.FERN,
        Material.LARGE_FERN,
        Material.SUNFLOWER,
        Material.LILAC,
        Material.ROSE_BUSH,
        Material.PEONY,
        Material.LADDER,
        Material.LEVER,
        Material.TRIPWIRE_HOOK,
        Material.REDSTONE_WIRE,
        Material.REPEATER,
        Material.COMPARATOR
    );

    private final WarzoneDuelsPlugin plugin;
    private final ArenaMapService arenaMapService;
    private final ArenaFootprintStore footprintStore;
    private final ArenaMapSnapshotStore snapshotStore;
    private final Object operationLock = new Object();
    private final File dirtyMarkerFile;

    private ExecutorService ioExecutor;
    private ArenaFootprint arenaFootprint;
    private String footprintFile = "arena-footprint.yml";
    private String mapsDirectory = "maps";
    private int captureBlocksPerTick = 900;
    private int restoreBlocksPerTick = 2400;
    private boolean restoreDefaultOnStartup = true;
    private boolean disabled;
    private TerrainOperation operation;
    private ArenaMapOperationStatus operationStatus = ArenaMapOperationStatus.idle("flat_arena");
    private final Map<String, ArenaMapSnapshot> snapshotCache = new ConcurrentHashMap<>();

    public ArenaTerrainService(
        WarzoneDuelsPlugin plugin,
        ArenaMapService arenaMapService,
        ArenaFootprintStore footprintStore,
        ArenaMapSnapshotStore snapshotStore
    ) {
        this.plugin = plugin;
        this.arenaMapService = arenaMapService;
        this.footprintStore = footprintStore;
        this.snapshotStore = snapshotStore;
        this.dirtyMarkerFile = new File(plugin.getDataFolder(), "arena-terrain-dirty.marker");
    }

    public void enable() {
        disabled = false;
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ioExecutor = Executors.newSingleThreadExecutor(new TerrainThreadFactory());
        }
    }

    public void disable() {
        disabled = true;
        synchronized (operationLock) {
            if (operation != null && operation.task != null) {
                if (OPERATION_RESTORE.equals(operation.type)) {
                    markDirty("Terrain restore was interrupted while disabling.");
                }
                operation.task.cancel();
            }
            operation = null;
            operationStatus = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
            snapshotCache.clear();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
    }

    public void reload(FileConfiguration config) {
        footprintFile = config.getString("terrain.footprint-file", "arena-footprint.yml");
        mapsDirectory = config.getString("terrain.snapshot-directory", "maps");
        captureBlocksPerTick = Math.max(50, config.getInt("terrain.capture-blocks-per-tick", 900));
        restoreBlocksPerTick = Math.max(50, config.getInt("terrain.restore-blocks-per-tick", 2400));
        restoreDefaultOnStartup = config.getBoolean("terrain.restore-default-on-startup", true);

        if (isBusy()) {
            return;
        }
        footprintStore.ensureBundledDefault(footprintFile);
        arenaFootprint = footprintStore.load(footprintFile);
        operationStatus = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
        synchronized (operationLock) {
            snapshotCache.clear();
        }
        if (arenaFootprint == null || arenaFootprint.isEmpty()) {
            plugin.getLogger().warning("Arena terrain footprint could not be loaded from " + footprintFile + ".");
            return;
        }
        preloadSnapshots();
    }

    public boolean isBusy() {
        synchronized (operationLock) {
            return operation != null;
        }
    }

    public boolean isReady() {
        return arenaFootprint != null && !arenaFootprint.isEmpty();
    }

    public ArenaFootprint footprint() {
        return arenaFootprint;
    }

    public boolean containsFootprintBlock(Location location) {
        if (location == null || location.getWorld() == null || arenaFootprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaFootprint.worldName())) {
            return false;
        }
        return arenaFootprint.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean isNearFootprint(Location location, int radius) {
        if (location == null || location.getWorld() == null || arenaFootprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaFootprint.worldName())) {
            return false;
        }

        int safeRadius = Math.max(0, radius);
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        if (centerX < arenaFootprint.minX() - safeRadius || centerX > arenaFootprint.maxX() + safeRadius
            || centerY < arenaFootprint.minY() - safeRadius || centerY > arenaFootprint.maxY() + safeRadius
            || centerZ < arenaFootprint.minZ() - safeRadius || centerZ > arenaFootprint.maxZ() + safeRadius) {
            return false;
        }

        for (int x = centerX - safeRadius; x <= centerX + safeRadius; x++) {
            for (int y = centerY - safeRadius; y <= centerY + safeRadius; y++) {
                for (int z = centerZ - safeRadius; z <= centerZ + safeRadius; z++) {
                    if (arenaFootprint.contains(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isOnOrInsideFootprintBlock(Location location) {
        if (location == null || location.getWorld() == null || arenaFootprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaFootprint.worldName())) {
            return false;
        }

        int x = location.getBlockX();
        int feetY = location.getBlockY();
        int z = location.getBlockZ();
        return arenaFootprint.contains(x, feetY, z)
            || arenaFootprint.contains(x, feetY - 1, z);
    }

    public boolean isWithinFootprintColumn(Location location, int verticalRadius) {
        if (location == null || location.getWorld() == null || arenaFootprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(arenaFootprint.worldName())) {
            return false;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        if (x < arenaFootprint.minX() || x > arenaFootprint.maxX() || z < arenaFootprint.minZ() || z > arenaFootprint.maxZ()) {
            return false;
        }

        int radius = Math.max(0, verticalRadius);
        int minY = Math.max(arenaFootprint.minY(), location.getBlockY() - radius);
        int maxY = Math.min(arenaFootprint.maxY(), location.getBlockY() + radius);
        for (int y = minY; y <= maxY; y++) {
            if (arenaFootprint.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public Location findPlayableLocation(Location preferred) {
        if (arenaFootprint == null || arenaFootprint.isEmpty()) {
            return null;
        }
        World world = resolveFootprintWorld();
        if (world == null) {
            return null;
        }
        List<FootprintBlock> blocks = arenaFootprint.orderedBlocks();
        if (blocks.isEmpty()) {
            return null;
        }

        int start = ThreadLocalRandom.current().nextInt(blocks.size());
        for (int offset = 0; offset < blocks.size(); offset++) {
            FootprintBlock block = blocks.get((start + offset) % blocks.size());
            Location location = playableLocationAt(world, block, preferred);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    public ArenaMapOperationStatus status() {
        synchronized (operationLock) {
            return operationStatus;
        }
    }

    public boolean shouldRestoreDefaultOnStartup() {
        return restoreDefaultOnStartup;
    }

    public boolean hasSnapshot(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return false;
        }
        synchronized (operationLock) {
            if (snapshotCache.containsKey(mapId.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return snapshotStore.exists(mapId, mapsDirectory);
    }

    public void captureSnapshot(String mapId, Runnable success, Consumer<String> failure) {
        if (!beginOperation("capture", mapId, failure)) {
            return;
        }
        World world = resolveFootprintWorld();
        if (world == null) {
            failActiveOperation("Arena footprint world is not loaded.", failure);
            return;
        }

        List<FootprintBlock> blocks = arenaFootprint.orderedBlocks();
        List<String> blockData = new ArrayList<>(blocks.size());
        AtomicInteger index = new AtomicInteger();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (disabled) {
                failActiveOperation("Terrain capture was interrupted because the plugin is disabling.", failure);
                return;
            }
            int processedThisTick = 0;
            while (processedThisTick < captureBlocksPerTick && index.get() < blocks.size()) {
                FootprintBlock footprintBlock = blocks.get(index.getAndIncrement());
                blockData.add(world.getBlockAt(footprintBlock.x(), footprintBlock.y(), footprintBlock.z()).getBlockData().getAsString());
                processedThisTick++;
            }
            updateProgress(index.get(), blocks.size());
            if (index.get() >= blocks.size()) {
                TerrainOperation finished = finishMainThreadPhase();
                if (finished != null && finished.task != null) {
                    finished.task.cancel();
                }
                saveSnapshotAsync(new ArenaMapSnapshot(mapId, world.getName(), blocks.size(), List.copyOf(blockData)), success, failure);
            }
        }, 1L, 1L);
        attachTask(task);
    }

    public void loadSnapshot(String mapId, Runnable success, Consumer<String> failure) {
        if (!beginOperation(OPERATION_RESTORE, mapId, failure)) {
            return;
        }
        ArenaMapSnapshot cachedSnapshot = cachedSnapshot(mapId);
        if (cachedSnapshot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (disabled || !isCurrentOperation(OPERATION_RESTORE, mapId)) {
                    return;
                }
                if (!validateSnapshot(cachedSnapshot, failure)) {
                    return;
                }
                applySnapshot(cachedSnapshot, success, failure);
            });
            return;
        }
        if (!snapshotStore.exists(mapId, mapsDirectory)) {
            failActiveOperation("No saved terrain snapshot exists for map '" + mapId + "'.", failure);
            return;
        }
        CompletableFuture<ArenaMapSnapshot> future = snapshotStore.loadAsync(mapId, mapsDirectory, ioExecutor);
        future.whenComplete((snapshot, throwable) -> runOnMainIfEnabled(() -> {
            if (disabled || !isCurrentOperation(OPERATION_RESTORE, mapId)) {
                return;
            }
            if (throwable != null) {
                failActiveOperation("Failed to load terrain snapshot '" + mapId + "': " + rootMessage(throwable), failure);
                return;
            }
            if (!validateSnapshot(snapshot, failure)) {
                return;
            }
            cacheSnapshot(snapshot);
            applySnapshot(snapshot, success, failure);
        }));
    }

    public void ensureDefaultSnapshotLoaded(Consumer<String> infoReceiver) {
        boolean dirty = isDirty();
        if ((!restoreDefaultOnStartup && !dirty) || disabled || isBusy()) {
            return;
        }
        String defaultMapId = arenaMapService.defaultMapId();
        if (!hasSnapshot(defaultMapId)) {
            if (infoReceiver != null) {
                infoReceiver.accept("Default arena snapshot '" + defaultMapId + "' does not exist yet.");
            }
            return;
        }
        loadSnapshot(defaultMapId, () -> {
            clearDirty();
            if (infoReceiver != null) {
                infoReceiver.accept("Default arena terrain restored.");
            }
        }, message -> {
            if (infoReceiver != null) {
                infoReceiver.accept(message);
            }
        });
    }

    private void applySnapshot(ArenaMapSnapshot snapshot, Runnable success, Consumer<String> failure) {
        World world = resolveFootprintWorld();
        if (world == null) {
            failActiveOperation("Arena footprint world is not loaded.", failure);
            return;
        }

        List<FootprintBlock> blocks = arenaFootprint.orderedBlocks();
        List<String> entries = snapshot.blockDataEntries();
        List<Integer> orderedIndices = buildRestoreOrder(entries);
        AtomicInteger index = new AtomicInteger();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (disabled) {
                failActiveOperation("Terrain restore was interrupted because the plugin is disabling.", failure);
                return;
            }
            int processedThisTick = 0;
            while (processedThisTick < restoreBlocksPerTick && index.get() < orderedIndices.size()) {
                int current = orderedIndices.get(index.getAndIncrement());
                FootprintBlock footprintBlock = blocks.get(current);
                BlockData blockData;
                try {
                    blockData = Bukkit.createBlockData(entries.get(current));
                } catch (IllegalArgumentException ex) {
                    failActiveOperation("Invalid block data inside terrain snapshot '" + snapshot.mapId() + "': " + ex.getMessage(), failure);
                    return;
                }
                world.getBlockAt(footprintBlock.x(), footprintBlock.y(), footprintBlock.z()).setBlockData(blockData, false);
                processedThisTick++;
            }
            updateProgress(index.get(), orderedIndices.size());
            if (index.get() >= orderedIndices.size()) {
                arenaMapService.markCurrentArenaMap(snapshot.mapId());
                TerrainOperation finished = clearOperation();
                if (finished != null && finished.task != null) {
                    finished.task.cancel();
                }
                if (success != null) {
                    success.run();
                }
            }
        }, 1L, 1L);
        attachTask(task);
    }

    private void saveSnapshotAsync(ArenaMapSnapshot snapshot, Runnable success, Consumer<String> failure) {
        snapshotStore.saveAsync(snapshot, mapsDirectory, ioExecutor).whenComplete((ignored, throwable) -> runOnMainIfEnabled(() -> {
            if (disabled || !isCurrentOperation("capture", snapshot.mapId())) {
                return;
            }
            if (throwable != null) {
                failActiveOperation("Failed to save terrain snapshot '" + snapshot.mapId() + "': " + rootMessage(throwable), failure);
                return;
            }
            cacheSnapshot(snapshot);
            clearOperation();
            if (success != null) {
                success.run();
            }
        }));
    }

    private boolean validateSnapshot(ArenaMapSnapshot snapshot, Consumer<String> failure) {
        if (snapshot == null) {
            failActiveOperation("Terrain snapshot could not be loaded.", failure);
            return false;
        }
        if (arenaFootprint == null) {
            failActiveOperation("Arena footprint is not loaded.", failure);
            return false;
        }
        if (!snapshot.worldName().equalsIgnoreCase(arenaFootprint.worldName())) {
            failActiveOperation("Terrain snapshot world does not match the configured arena world.", failure);
            return false;
        }
        int footprintSize = arenaFootprint.orderedBlocks().size();
        if (snapshot.footprintBlockCount() != footprintSize || snapshot.blockDataEntries().size() != footprintSize) {
            failActiveOperation("Terrain snapshot footprint does not match the configured arena footprint.", failure);
            return false;
        }
        return true;
    }

    private void preloadSnapshots() {
        List<String> mapIds = arenaMapService.options().stream()
            .map(option -> option.id().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        for (String mapId : mapIds) {
            if (!snapshotStore.exists(mapId, mapsDirectory)) {
                continue;
            }
            snapshotStore.loadAsync(mapId, mapsDirectory, ioExecutor).whenComplete((snapshot, throwable) -> {
                if (disabled || throwable != null || snapshot == null) {
                    return;
                }
                cacheSnapshot(snapshot);
            });
        }
    }

    private void cacheSnapshot(ArenaMapSnapshot snapshot) {
        if (snapshot == null || snapshot.mapId() == null) {
            return;
        }
        synchronized (operationLock) {
            snapshotCache.put(snapshot.mapId().toLowerCase(Locale.ROOT), snapshot);
        }
    }

    private ArenaMapSnapshot cachedSnapshot(String mapId) {
        synchronized (operationLock) {
            return snapshotCache.get(mapId.toLowerCase(Locale.ROOT));
        }
    }

    private World resolveFootprintWorld() {
        if (arenaFootprint == null || arenaFootprint.worldName() == null || arenaFootprint.worldName().isBlank()) {
            return null;
        }
        return Bukkit.getWorld(arenaFootprint.worldName());
    }

    private Location playableLocationAt(World world, FootprintBlock block, Location preferred) {
        Material floor = world.getBlockAt(block.x(), block.y(), block.z()).getType();
        if (floor.isAir()) {
            return null;
        }
        Material feet = world.getBlockAt(block.x(), block.y() + 1, block.z()).getType();
        Material head = world.getBlockAt(block.x(), block.y() + 2, block.z()).getType();
        if (!feet.isAir() || !head.isAir()) {
            return null;
        }
        float yaw = preferred == null ? 0.0F : preferred.getYaw();
        float pitch = preferred == null ? 0.0F : preferred.getPitch();
        return new Location(world, block.x() + 0.5D, block.y() + 1.0D, block.z() + 0.5D, yaw, pitch);
    }

    private boolean beginOperation(String type, String mapId, Consumer<String> failure) {
        if (disabled) {
            if (failure != null) {
                failure.accept("Terrain service is unavailable while the plugin is disabling.");
            }
            return false;
        }
        if (!isReady()) {
            if (failure != null) {
                failure.accept("Arena terrain footprint is not loaded.");
            }
            return false;
        }
        synchronized (operationLock) {
            if (operation != null) {
                if (failure != null) {
                    failure.accept("The arena terrain is already busy with another operation.");
                }
                return false;
            }
            operation = new TerrainOperation(type, mapId);
            operationStatus = new ArenaMapOperationStatus(true, type, mapId, 0L, arenaFootprint.orderedBlocks().size());
            return true;
        }
    }

    private void attachTask(BukkitTask task) {
        synchronized (operationLock) {
            if (operation != null) {
                operation.task = task;
            } else {
                task.cancel();
            }
        }
    }

    private void updateProgress(long processedBlocks, long totalBlocks) {
        synchronized (operationLock) {
            if (operation != null) {
                operationStatus = new ArenaMapOperationStatus(true, operation.type, operation.mapId, processedBlocks, totalBlocks);
            }
        }
    }

    private TerrainOperation finishMainThreadPhase() {
        synchronized (operationLock) {
            return operation;
        }
    }

    private boolean isCurrentOperation(String type, String mapId) {
        synchronized (operationLock) {
            return operation != null
                && operation.type.equals(type)
                && operation.mapId.equalsIgnoreCase(mapId);
        }
    }

    private void failActiveOperation(String message, Consumer<String> failure) {
        TerrainOperation finished = clearOperation();
        if (finished != null && finished.task != null) {
            if (OPERATION_RESTORE.equals(finished.type)) {
                markDirty(message);
            }
            finished.task.cancel();
        }
        plugin.getLogger().warning(message);
        if (failure != null) {
            failure.accept(message);
        }
    }

    private TerrainOperation clearOperation() {
        synchronized (operationLock) {
            TerrainOperation previous = operation;
            operation = null;
            operationStatus = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
            return previous;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    private void runOnMainIfEnabled(Runnable runnable) {
        if (disabled || !plugin.isEnabled()) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
        } catch (IllegalPluginAccessException ignored) {
            // The plugin disabled after the completion callback checked isEnabled.
        }
    }

    private boolean isDirty() {
        return dirtyMarkerFile.isFile();
    }

    private void markDirty(String reason) {
        try {
            File parent = dirtyMarkerFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Failed to create " + parent.getAbsolutePath());
            }
            if (!dirtyMarkerFile.exists() && !dirtyMarkerFile.createNewFile()) {
                plugin.getLogger().warning("Arena terrain dirty marker could not be created.");
            }
            plugin.getLogger().warning(reason + " Arena terrain marked dirty; default terrain will be restored on next safe startup.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to mark arena terrain dirty: " + ex.getMessage());
        }
    }

    private void clearDirty() {
        if (dirtyMarkerFile.exists() && !dirtyMarkerFile.delete()) {
            plugin.getLogger().warning("Failed to clear arena terrain dirty marker.");
        }
    }

    private List<Integer> buildRestoreOrder(List<String> entries) {
        List<Integer> stable = new ArrayList<>();
        List<Integer> dependent = new ArrayList<>();
        List<Integer> gravity = new ArrayList<>();

        List<FootprintBlock> blocks = arenaFootprint.orderedBlocks();
        for (int i = 0; i < entries.size(); i++) {
            String rawData = entries.get(i);
            Material material = parseMaterial(rawData);
            if (material == null || material.isAir()) {
                stable.add(i);
                continue;
            }
            if (material.hasGravity()) {
                gravity.add(i);
                continue;
            }
            if (isSupportSensitive(material)) {
                dependent.add(i);
                continue;
            }
            stable.add(i);
        }

        stable.sort((left, right) -> Integer.compare(blocks.get(left).y(), blocks.get(right).y()));
        dependent.sort((left, right) -> Integer.compare(blocks.get(left).y(), blocks.get(right).y()));
        gravity.sort((left, right) -> Integer.compare(blocks.get(left).y(), blocks.get(right).y()));

        List<Integer> ordered = new ArrayList<>(entries.size());
        ordered.addAll(stable);
        ordered.addAll(dependent);
        ordered.addAll(gravity);
        return ordered;
    }

    private Material parseMaterial(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return Material.AIR;
        }
        int bracketIndex = rawData.indexOf('[');
        String materialName = bracketIndex >= 0 ? rawData.substring(0, bracketIndex) : rawData;
        return Material.matchMaterial(materialName);
    }

    private boolean isSupportSensitive(Material material) {
        String name = material.name();
        return SUPPORT_SENSITIVE_MATERIALS.contains(material)
            || SUPPORT_SENSITIVE_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private static final class TerrainOperation {
        private final String type;
        private final String mapId;
        private BukkitTask task;

        private TerrainOperation(String type, String mapId) {
            this.type = type;
            this.mapId = mapId.toLowerCase(Locale.ROOT);
        }
    }

    private static final class TerrainThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "WarzoneDuels-Terrain-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
