package dev.minecraft.warzoneduels.adapter.bukkit.reset;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

public record SavedBlockState(String materialKey, String blockData) {
    public static SavedBlockState capture(Block block) {
        return new SavedBlockState(block.getType().name(), block.getBlockData().getAsString());
    }

    public void apply(Block block) {
        Material material = Material.matchMaterial(materialKey);
        if (material == null) {
            material = Material.AIR;
        }
        block.setType(material, false);
        if (!blockData.isBlank()) {
            try {
                block.setBlockData(Bukkit.createBlockData(blockData), false);
            } catch (IllegalArgumentException ignored) {
                // Fallback to plain material if block data is invalid on this server version.
            }
        }
    }
}
