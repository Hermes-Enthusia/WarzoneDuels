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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LoadoutArchiveStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;

    public LoadoutArchiveStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loadout-archives.yml");
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
        YamlConfiguration yaml = load();
        String base = "players." + player.getUniqueId();
        yaml.set(base + ".name", player.getName());
        yaml.set(base + ".captured-at", System.currentTimeMillis());
        writeSnapshot(yaml.createSection(base + ".snapshot"), snapshot);
        save(yaml);
    }

    public LoadoutSnapshot loadLatestPreDuel(UUID playerId) {
        YamlConfiguration yaml = load();
        ConfigurationSection section = yaml.getConfigurationSection("players." + playerId + ".snapshot");
        if (section == null) {
            return null;
        }
        return readSnapshot(section);
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

    @SuppressWarnings("unchecked")
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

    private YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void save(YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save loadout archive file: " + ex.getMessage());
        }
    }
}
