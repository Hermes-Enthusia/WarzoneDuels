package dev.minecraft.warzoneduels.adapter.bukkit.reset;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.terrain.ArenaFootprint;
import dev.minecraft.warzoneduels.domain.terrain.FootprintBlock;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ArenaResetService {
    public ArenaSnapshot capture(ArenaDefinition arena) {
        World world = arena.world();
        if (world == null) {
            return new ArenaSnapshot(Map.of());
        }
        Map<BlockKey, SavedBlockState> snapshot = new LinkedHashMap<>();
        forEachBlock(arena, (block) -> snapshot.put(BlockKey.fromLocation(block.getLocation()), SavedBlockState.capture(block)));
        return new ArenaSnapshot(snapshot);
    }

    public void restore(ArenaDefinition arena, ArenaSnapshot snapshot) {
        World world = arena.world();
        if (world == null || snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockKey, SavedBlockState> entry : snapshot.blocks().entrySet()) {
            BlockKey key = entry.getKey();
            if (!world.getUID().equals(key.getWorldId())) {
                continue;
            }
            Block block = world.getBlockAt(key.getX(), key.getY(), key.getZ());
            entry.getValue().apply(block);
        }
    }

    public void clearTrackedPlacedBlocks(ArenaDefinition arena, Set<BlockKey> placedBlocks) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        for (BlockKey key : placedBlocks) {
            if (!world.getUID().equals(key.getWorldId())) {
                continue;
            }
            Block block = world.getBlockAt(key.getX(), key.getY(), key.getZ());
            if (block.getType() != Material.AIR) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public void clearFluids(ArenaDefinition arena) {
        forEachBlock(arena, (block) -> {
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                block.setType(Material.AIR, false);
            }
        });
    }

    public void clearFluids(ArenaDefinition arena, ArenaFootprint footprint) {
        World world = arena.world();
        if (world == null || footprint == null || footprint.isEmpty()) {
            return;
        }
        for (FootprintBlock footprintBlock : footprint.orderedBlocks()) {
            Block block = world.getBlockAt(footprintBlock.x(), footprintBlock.y(), footprintBlock.z());
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                block.setType(Material.AIR, false);
            }
        }
    }

    public void clearNonPlayerEntities(ArenaDefinition arena) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        Location pos1 = arena.pos1();
        Location pos2 = arena.pos2();
        int minChunkX = Math.floorDiv(Math.min(pos1.getBlockX(), pos2.getBlockX()), 16);
        int maxChunkX = Math.floorDiv(Math.max(pos1.getBlockX(), pos2.getBlockX()), 16);
        int minChunkZ = Math.floorDiv(Math.min(pos1.getBlockZ(), pos2.getBlockZ()), 16);
        int maxChunkZ = Math.floorDiv(Math.max(pos1.getBlockZ(), pos2.getBlockZ()), 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Player) && arena.contains(entity.getLocation())) {
                        entity.remove();
                    }
                }
            }
        }
    }

    private void forEachBlock(ArenaDefinition arena, BlockConsumer consumer) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        Location pos1 = arena.pos1();
        Location pos2 = arena.pos2();
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    consumer.accept(world.getBlockAt(x, y, z));
                }
            }
        }
    }

    @FunctionalInterface
    private interface BlockConsumer {
        void accept(Block block);
    }
}
