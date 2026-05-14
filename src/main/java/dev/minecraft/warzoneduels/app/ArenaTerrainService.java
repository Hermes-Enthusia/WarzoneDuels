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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class ArenaTerrainService {
    private final WarzoneDuelsPlugin plugin;
    private final ArenaMapService arenaMapService;
    private final ArenaFootprintStore footprintStore;
    private final ArenaMapSnapshotStore snapshotStore;
    private final Object operationLock = new Object();

    private ExecutorService ioExecutor;
    private ArenaFootprint footprint;
    private String footprintFile = "arena-footprint.yml";
    private String mapsDirectory = "maps";
    private int captureBlocksPerTick = 900;
    private int restoreBlocksPerTick = 2400;
    private boolean restoreDefaultOnStartup = true;
    private boolean disabled;
    private TerrainOperation operation;
    private ArenaMapOperationStatus status = ArenaMapOperationStatus.idle("flat_arena");
    private final Map<String, ArenaMapSnapshot> snapshotCache = new HashMap<>();

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
                operation.task.cancel();
            }
            operation = null;
            status = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
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
        footprint = footprintStore.load(footprintFile);
        status = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
        synchronized (operationLock) {
            snapshotCache.clear();
        }
        if (footprint == null || footprint.isEmpty()) {
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
        return footprint != null && !footprint.isEmpty();
    }

    public ArenaFootprint footprint() {
        return footprint;
    }

    public boolean containsFootprintBlock(Location location) {
        if (location == null || location.getWorld() == null || footprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(footprint.worldName())) {
            return false;
        }
        return footprint.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean isNearFootprint(Location location, int radius) {
        if (location == null || location.getWorld() == null || footprint == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(footprint.worldName())) {
            return false;
        }

        int safeRadius = Math.max(0, radius);
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        if (centerX < footprint.minX() - safeRadius || centerX > footprint.maxX() + safeRadius
            || centerY < footprint.minY() - safeRadius || centerY > footprint.maxY() + safeRadius
            || centerZ < footprint.minZ() - safeRadius || centerZ > footprint.maxZ() + safeRadius) {
            return false;
        }

        for (int x = centerX - safeRadius; x <= centerX + safeRadius; x++) {
            for (int y = centerY - safeRadius; y <= centerY + safeRadius; y++) {
                for (int z = centerZ - safeRadius; z <= centerZ + safeRadius; z++) {
                    if (footprint.contains(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Location findPlayableLocation(Location preferred) {
        if (footprint == null || footprint.isEmpty()) {
            return null;
        }
        World world = resolveFootprintWorld();
        if (world == null) {
            return null;
        }
        List<FootprintBlock> blocks = footprint.orderedBlocks();
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
            return status;
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

        List<FootprintBlock> blocks = footprint.orderedBlocks();
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
        if (!beginOperation("restore", mapId, failure)) {
            return;
        }
        ArenaMapSnapshot cachedSnapshot = cachedSnapshot(mapId);
        if (cachedSnapshot != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (disabled || !isCurrentOperation("restore", mapId)) {
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
        future.whenComplete((snapshot, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (disabled || !isCurrentOperation("restore", mapId)) {
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
        if (!restoreDefaultOnStartup || disabled || isBusy()) {
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

        List<FootprintBlock> blocks = footprint.orderedBlocks();
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
        snapshotStore.saveAsync(snapshot, mapsDirectory, ioExecutor).whenComplete((ignored, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
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
        if (footprint == null) {
            failActiveOperation("Arena footprint is not loaded.", failure);
            return false;
        }
        if (!snapshot.worldName().equalsIgnoreCase(footprint.worldName())) {
            failActiveOperation("Terrain snapshot world does not match the configured arena world.", failure);
            return false;
        }
        int footprintSize = footprint.orderedBlocks().size();
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
        if (footprint == null || footprint.worldName() == null || footprint.worldName().isBlank()) {
            return null;
        }
        return Bukkit.getWorld(footprint.worldName());
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
            status = new ArenaMapOperationStatus(true, type, mapId, 0L, footprint.orderedBlocks().size());
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
                status = new ArenaMapOperationStatus(true, operation.type, operation.mapId, processedBlocks, totalBlocks);
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
            status = ArenaMapOperationStatus.idle(arenaMapService.currentArenaMapId());
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

    private List<Integer> buildRestoreOrder(List<String> entries) {
        List<Integer> stable = new ArrayList<>();
        List<Integer> dependent = new ArrayList<>();
        List<Integer> gravity = new ArrayList<>();

        List<FootprintBlock> blocks = footprint.orderedBlocks();
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
        return name.endsWith("_BUTTON")
            || name.endsWith("_TORCH")
            || name.endsWith("_WALL_TORCH")
            || name.endsWith("_SIGN")
            || name.endsWith("_HANGING_SIGN")
            || name.endsWith("_WALL_SIGN")
            || name.endsWith("_RAIL")
            || name.endsWith("_BANNER")
            || name.endsWith("_WALL_BANNER")
            || name.endsWith("_SAPLING")
            || name.endsWith("_FLOWER")
            || name.endsWith("_TULIP")
            || name.endsWith("_MUSHROOM")
            || name.endsWith("_CORAL")
            || name.endsWith("_FAN")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_CARPET")
            || name.endsWith("_POT")
            || name.endsWith("_HEAD")
            || name.endsWith("_SKULL")
            || name.endsWith("_VINE")
            || name.endsWith("_CANDLE")
            || name.endsWith("_BUSH")
            || material == Material.CACTUS
            || material == Material.DEAD_BUSH
            || material == Material.SUGAR_CANE
            || material == Material.TALL_GRASS
            || material == Material.SHORT_GRASS
            || material == Material.FERN
            || material == Material.LARGE_FERN
            || material == Material.SUNFLOWER
            || material == Material.LILAC
            || material == Material.ROSE_BUSH
            || material == Material.PEONY
            || material == Material.LADDER
            || material == Material.LEVER
            || material == Material.TRIPWIRE_HOOK
            || material == Material.REDSTONE_WIRE
            || material == Material.REPEATER
            || material == Material.COMPARATOR;
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
