package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.adapter.bukkit.persistence.PlayerStatsStore;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsService {
    private final PlayerStatsStore store;
    private final Map<UUID, PlayerDuelStats> statsByPlayerId = new ConcurrentHashMap<>();

    public StatsService(PlayerStatsStore store) {
        this.store = store;
    }

    public void enable() {
        statsByPlayerId.clear();
        statsByPlayerId.putAll(store.load());
    }

    public void disable() {
        store.shutdown();
        store.save(statsByPlayerId);
    }

    public void recordMatchResult(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        if (duel == null) {
            return;
        }
        MatchParticipant participantOne = duel.participantOne();
        MatchParticipant participantTwo = duel.participantTwo();
        if (reason == DuelEndReason.DRAW) {
            stats(participantOne.playerId(), participantOne.name()).recordDraw();
            stats(participantTwo.playerId(), participantTwo.name()).recordDraw();
            save();
            return;
        }
        if (winnerId == null) {
            return;
        }

        MatchParticipant winner = duel.participant(winnerId);
        MatchParticipant loser = duel.other(winnerId);
        if (winner == null || loser == null) {
            return;
        }
        stats(winner.playerId(), winner.name()).recordWin();
        stats(loser.playerId(), loser.name()).recordLoss(reason == DuelEndReason.DISCONNECT_TIMEOUT);
        save();
    }

    public PlayerDuelStats findByNameOrOffline(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String needle = name.toLowerCase(Locale.ROOT);
        for (PlayerDuelStats stats : statsByPlayerId.values()) {
            if (stats.lastKnownName() != null && stats.lastKnownName().equalsIgnoreCase(needle)) {
                return stats;
            }
        }
        for (PlayerDuelStats stats : statsByPlayerId.values()) {
            if (stats.lastKnownName() != null && stats.lastKnownName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                return stats;
            }
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        if (offlinePlayer != null && offlinePlayer.getUniqueId() != null) {
            return stats(offlinePlayer.getUniqueId(), offlinePlayer.getName() == null ? name : offlinePlayer.getName());
        }
        return null;
    }

    public PlayerDuelStats findExistingByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String needle = name.toLowerCase(Locale.ROOT);
        for (PlayerDuelStats stats : statsByPlayerId.values()) {
            if (stats.lastKnownName() != null && stats.lastKnownName().equalsIgnoreCase(needle)) {
                return stats;
            }
        }
        for (PlayerDuelStats stats : statsByPlayerId.values()) {
            if (stats.lastKnownName() != null && stats.lastKnownName().toLowerCase(Locale.ROOT).startsWith(needle)) {
                return stats;
            }
        }
        return null;
    }

    public PlayerDuelStats stats(UUID playerId, String lastKnownName) {
        PlayerDuelStats playerStats = statsByPlayerId.computeIfAbsent(playerId, id -> new PlayerDuelStats(id, lastKnownName));
        if (lastKnownName != null && !lastKnownName.isBlank()) {
            playerStats.setLastKnownName(lastKnownName);
        }
        return playerStats;
    }

    public PlayerDuelStats findById(UUID playerId) {
        return statsByPlayerId.get(playerId);
    }

    public Collection<PlayerDuelStats> all() {
        return statsByPlayerId.values();
    }

    public List<PlayerDuelStats> topByWins(int page, int pageSize) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, pageSize);
        return statsByPlayerId.values().stream()
            .filter(stats -> stats.matchesPlayed() > 0)
            .sorted(Comparator
                .comparingInt(PlayerDuelStats::wins).reversed()
                .thenComparing(Comparator.comparingInt(PlayerDuelStats::bestWinStreak).reversed())
                .thenComparing(PlayerDuelStats::lastKnownName, String.CASE_INSENSITIVE_ORDER))
            .skip((long) safePage * safePageSize)
            .limit(safePageSize)
            .toList();
    }

    private void save() {
        store.saveAsync(statsByPlayerId);
    }
}
