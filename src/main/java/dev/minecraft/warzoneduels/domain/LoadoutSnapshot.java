package dev.minecraft.warzoneduels.domain;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoadoutSnapshot {
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offHand;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final int totalExperience;
    private final int level;
    private final float expProgress;
    private final int fireTicks;
    private final List<PotionEffect> potionEffects;

    public LoadoutSnapshot(
        ItemStack[] contents,
        ItemStack[] armor,
        ItemStack offHand,
        double health,
        int foodLevel,
        float saturation,
        int totalExperience,
        int level,
        float expProgress,
        int fireTicks,
        List<PotionEffect> potionEffects
    ) {
        this.contents = cloneArray(contents);
        this.armor = cloneArray(armor);
        this.offHand = offHand == null ? null : offHand.clone();
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.totalExperience = totalExperience;
        this.level = level;
        this.expProgress = expProgress;
        this.fireTicks = fireTicks;
        this.potionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects));
    }

    public ItemStack[] contents() {
        return cloneArray(contents);
    }

    public ItemStack[] armor() {
        return cloneArray(armor);
    }

    public ItemStack offHand() {
        return offHand == null ? null : offHand.clone();
    }

    public double health() {
        return health;
    }

    public int foodLevel() {
        return foodLevel;
    }

    public float saturation() {
        return saturation;
    }

    public int totalExperience() {
        return totalExperience;
    }

    public int level() {
        return level;
    }

    public float expProgress() {
        return expProgress;
    }

    public int fireTicks() {
        return fireTicks;
    }

    public List<PotionEffect> potionEffects() {
        return potionEffects;
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
