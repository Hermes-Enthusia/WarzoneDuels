package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerStatsStore {
    private final WarzoneDuelsPlugin plugin;
    private final File file;

    public PlayerStatsStore(WarzoneDuelsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public Map<UUID, PlayerDuelStats> load() {
        Map<UUID, PlayerDuelStats> stats = new HashMap<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return stats;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection playerSection = section.getConfigurationSection(key);
            if (playerSection == null) {
                continue;
            }
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            String lastKnownName = playerSection.getString("last-known-name", "Unknown");
            PlayerDuelStats playerStats = new PlayerDuelStats(playerId, lastKnownName);
            playerStats.setMatchesPlayed(playerSection.getInt("matches-played", 0));
            playerStats.setWins(playerSection.getInt("wins", 0));
            playerStats.setLosses(playerSection.getInt("losses", 0));
            playerStats.setDraws(playerSection.getInt("draws", 0));
            playerStats.setDisconnectForfeitLosses(playerSection.getInt("disconnect-forfeit-losses", 0));
            playerStats.setCurrentWinStreak(playerSection.getInt("current-win-streak", 0));
            playerStats.setBestWinStreak(playerSection.getInt("best-win-streak", 0));
            stats.put(playerId, playerStats);
        }
        return stats;
    }

    public void save(Map<UUID, PlayerDuelStats> stats) {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("players");
        for (PlayerDuelStats playerStats : stats.values()) {
            ConfigurationSection playerSection = section.createSection(playerStats.playerId().toString());
            playerSection.set("last-known-name", playerStats.lastKnownName());
            playerSection.set("matches-played", playerStats.matchesPlayed());
            playerSection.set("wins", playerStats.wins());
            playerSection.set("losses", playerStats.losses());
            playerSection.set("draws", playerStats.draws());
            playerSection.set("disconnect-forfeit-losses", playerStats.disconnectForfeitLosses());
            playerSection.set("current-win-streak", playerStats.currentWinStreak());
            playerSection.set("best-win-streak", playerStats.bestWinStreak());
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save duel stats: " + ex.getMessage());
        }
    }
}
