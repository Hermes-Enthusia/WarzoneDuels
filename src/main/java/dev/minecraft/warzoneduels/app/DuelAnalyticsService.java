package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.DuelAnalyticsStore;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class DuelAnalyticsService {
    private final WarzoneDuelsPlugin plugin;
    private final DuelAnalyticsStore store;
    private final ExecutorService writer;
    private BukkitTask cleanupTask;

    public DuelAnalyticsService(WarzoneDuelsPlugin plugin, DuelAnalyticsStore store) {
        this.plugin = plugin;
        this.store = store;
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "WarzoneDuels-DuelAnalytics");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void enable() {
        store.enable();
        long retentionDays = Math.max(1L, plugin.getConfig().getLong("analytics.retention-days", 365L));
        long cleanupMinutes = Math.max(5L, plugin.getConfig().getLong("analytics.cleanup-interval-minutes", 30L));
        long cleanupTicks = cleanupMinutes * 60L * 20L;
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long cutoff = System.currentTimeMillis() - Duration.ofDays(retentionDays).toMillis();
            store.cleanupOlderThan(cutoff);
        }, cleanupTicks, cleanupTicks);
    }

    public void disable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        } finally {
            store.disable();
        }
    }

    public void recordDuel(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        if (duel == null) {
            return;
        }
        DuelRecord record = buildRecord(duel, winnerId, reason);
        try {
            writer.execute(() -> store.insert(record));
        } catch (RejectedExecutionException ex) {
            plugin.getLogger().warning("Skipped duel analytics record because the writer is shutting down.");
        }
    }

    public long countTotalDuels() {
        return store.countAll();
    }

    public long countDuelsSince(Duration duration) {
        return store.countSince(cutoff(duration));
    }

    public long countDuelsForPlayerSince(UUID playerId, Duration duration) {
        return store.countForPlayerSince(playerId, cutoff(duration));
    }

    public long countCancelledDuelsSince(Duration duration) {
        return store.countCancelledSince(cutoff(duration));
    }

    public List<DuelRecord> recentDuels(int limit) {
        return store.findRecent(limit);
    }

    public List<DuelRecord> recentDuelsForPlayer(UUID playerId, int limit) {
        return store.findRecentForPlayer(playerId, limit);
    }

    public List<Map.Entry<String, Long>> topMapsSince(Duration duration, int limit) {
        return store.topMapsSince(cutoff(duration), limit);
    }

    public List<Map.Entry<String, Long>> topRulesSince(Duration duration, int limit) {
        return store.topRulesSince(cutoff(duration), limit);
    }

    public List<Map.Entry<String, Long>> topEndReasonsSince(Duration duration, int limit) {
        return store.topEndReasonsSince(cutoff(duration), limit);
    }

    public String mostUsedMapSince(Duration duration) {
        List<Map.Entry<String, Long>> entries = topMapsSince(duration, 1);
        return entries.isEmpty() ? "None" : safeLabel(entries.get(0).getKey());
    }

    public String mostUsedRulesSince(Duration duration) {
        List<Map.Entry<String, Long>> entries = topRulesSince(duration, 1);
        return entries.isEmpty() ? "None" : safeLabel(entries.get(0).getKey());
    }

    public String mostPlayedMapForPlayer(UUID playerId) {
        return topLabelForPlayer(playerId, DuelRecord::mapName, 1, "None");
    }

    public String mostPlayedRulesForPlayer(UUID playerId) {
        return topLabelForPlayer(playerId, DuelRecord::ruleset, 1, "None");
    }

    public List<Map.Entry<String, Long>> recentOpponents(UUID playerId, int limit) {
        List<DuelRecord> records = recentDuelsForPlayer(playerId, 250);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DuelRecord record : records) {
            String opponent = opponentName(record, playerId);
            if (opponent == null || opponent.isBlank()) {
                continue;
            }
            counts.merge(opponent, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
            .limit(Math.max(1, limit))
            .toList();
    }

    private DuelRecord buildRecord(ActiveDuel duel, UUID winnerId, DuelEndReason reason) {
        MatchParticipant participantOne = duel.participantOne();
        MatchParticipant participantTwo = duel.participantTwo();
        DuelSettings settings = duel.settings();
        long endedAt = System.currentTimeMillis();
        long duration = Math.max(0L, endedAt - duel.startedAtEpochMs());

        UUID loserId = null;
        String winnerName = null;
        String loserName = null;
        boolean countedAsMatch = reason == DuelEndReason.KILL || reason == DuelEndReason.DRAW || reason == DuelEndReason.DISCONNECT_TIMEOUT;
        if (winnerId != null) {
            MatchParticipant winner = duel.participant(winnerId);
            MatchParticipant loser = duel.other(winnerId);
            if (winner != null) {
                winnerName = winner.name();
            }
            if (loser != null) {
                loserId = loser.playerId();
                loserName = loser.name();
            }
        }

        if (reason == DuelEndReason.DRAW) {
            countedAsMatch = true;
        }
        if (reason == DuelEndReason.SERVER_RESTART || reason == DuelEndReason.PLUGIN_DISABLE || reason == DuelEndReason.ADMIN_ABORT) {
            countedAsMatch = false;
        }

        String reference = Long.toString(duel.startedAtEpochMs(), 36).toUpperCase(Locale.ROOT);
        return new DuelRecord(
            reference,
            duel.startedAtEpochMs(),
            endedAt,
            duration,
            participantOne.playerId(),
            participantOne.name(),
            participantTwo.playerId(),
            participantTwo.name(),
            winnerId,
            winnerName,
            loserId,
            loserName,
            settings.getMapId(),
            settings.getMapDisplayName(),
            settings.formatBlockRules(),
            settings.formatExtendedItemRules(),
            reason,
            countedAsMatch,
            0,
            settings.getWager()
        );
    }

    private long cutoff(Duration duration) {
        return System.currentTimeMillis() - Math.max(1L, duration.toMillis());
    }

    private String opponentName(DuelRecord record, UUID playerId) {
        if (record.playerOneId().equals(playerId)) {
            return record.playerTwoName();
        }
        if (record.playerTwoId().equals(playerId)) {
            return record.playerOneName();
        }
        return null;
    }

    private String safeLabel(String value) {
        return value == null || value.isBlank() ? "None" : value;
    }

    private String topLabelForPlayer(UUID playerId, java.util.function.Function<DuelRecord, String> extractor, int limit, String fallback) {
        List<DuelRecord> records = recentDuelsForPlayer(playerId, 500);
        if (records.isEmpty()) {
            return fallback;
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DuelRecord record : records) {
            String key = extractor.apply(record);
            if (key == null || key.isBlank()) {
                continue;
            }
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
            .limit(limit)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(fallback);
    }
}
