package dev.minecraft.warzoneduels.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public final class SpearUtil {
    private static final Set<Material> SPEARS = EnumSet.of(
        Material.WOODEN_SPEAR,
        Material.STONE_SPEAR,
        Material.COPPER_SPEAR,
        Material.IRON_SPEAR,
        Material.GOLDEN_SPEAR,
        Material.DIAMOND_SPEAR,
        Material.NETHERITE_SPEAR
    );

    private SpearUtil() {
    }

    public static boolean isSpear(ItemStack item) {
        return item != null && SPEARS.contains(item.getType());
    }

    public static boolean isSpear(Material material) {
        return material != null && SPEARS.contains(material);
    }
}
