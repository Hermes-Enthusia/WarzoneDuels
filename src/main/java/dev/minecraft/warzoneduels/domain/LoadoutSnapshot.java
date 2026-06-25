package dev.minecraft.warzoneduels.domain;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoadoutSnapshot {
    private final ItemStack[] storedContents;
    private final ItemStack[] storedArmor;
    private final ItemStack storedOffHand;
    private final double storedHealth;
    private final int storedFoodLevel;
    private final float storedSaturation;
    private final int storedTotalExperience;
    private final int storedLevel;
    private final float storedExpProgress;
    private final int storedFireTicks;
    private final List<PotionEffect> storedPotionEffects;

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
        this.storedContents = cloneArray(contents);
        this.storedArmor = cloneArray(armor);
        this.storedOffHand = offHand == null ? null : offHand.clone();
        this.storedHealth = health;
        this.storedFoodLevel = foodLevel;
        this.storedSaturation = saturation;
        this.storedTotalExperience = totalExperience;
        this.storedLevel = level;
        this.storedExpProgress = expProgress;
        this.storedFireTicks = fireTicks;
        this.storedPotionEffects = Collections.unmodifiableList(new ArrayList<>(potionEffects));
    }

    public ItemStack[] contents() {
        return cloneArray(storedContents);
    }

    public ItemStack[] armor() {
        return cloneArray(storedArmor);
    }

    public ItemStack offHand() {
        return storedOffHand == null ? null : storedOffHand.clone();
    }

    public double health() {
        return storedHealth;
    }

    public int foodLevel() {
        return storedFoodLevel;
    }

    public float saturation() {
        return storedSaturation;
    }

    public int totalExperience() {
        return storedTotalExperience;
    }

    public int level() {
        return storedLevel;
    }

    public float expProgress() {
        return storedExpProgress;
    }

    public int fireTicks() {
        return storedFireTicks;
    }

    public List<PotionEffect> potionEffects() {
        return storedPotionEffects;
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
