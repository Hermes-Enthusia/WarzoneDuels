package dev.minecraft.warzoneduels.adapter.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import dev.minecraft.warzoneduels.app.DuelAnalyticsService;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;

import java.time.Duration;
import java.util.UUID;

@PluginInfo(
    name = "WarzoneDuels",
    iconName = "swords",
    iconFamily = Family.SOLID,
    color = Color.RED
)
public final class WarzoneDuelsDataExtension implements DataExtension {
    private final StatsService statsService;
    private final DuelAnalyticsService analyticsService;
    private final DuelService duelService;

    public WarzoneDuelsDataExtension(StatsService statsService, DuelAnalyticsService analyticsService, DuelService duelService) {
        this.statsService = statsService;
        this.analyticsService = analyticsService;
        this.duelService = duelService;
    }

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[] {
            CallEvents.PLAYER_JOIN,
            CallEvents.PLAYER_LEAVE
        };
    }

    @NumberProvider(text = "Total Duels", description = "All duel records stored by WarzoneDuels.", iconName = "swords", iconColor = Color.RED, priority = 100, showInPlayerTable = false)
    public long serverTotalDuels() {
        return analyticsService.countTotalDuels();
    }

    @NumberProvider(text = "Duels 24h", description = "Duels recorded in the last 24 hours.", iconName = "clock", iconColor = Color.AMBER, priority = 90, showInPlayerTable = false)
    public long serverDuels24h() {
        return analyticsService.countDuelsSince(Duration.ofHours(24));
    }

    @NumberProvider(text = "Duels 7d", description = "Duels recorded in the last 7 days.", iconName = "calendar-week", iconColor = Color.YELLOW, priority = 80, showInPlayerTable = false)
    public long serverDuels7d() {
        return analyticsService.countDuelsSince(Duration.ofDays(7));
    }

    @NumberProvider(text = "Duels 30d", description = "Duels recorded in the last 30 days.", iconName = "calendar-days", iconColor = Color.LIGHT_BLUE, priority = 70, showInPlayerTable = false)
    public long serverDuels30d() {
        return analyticsService.countDuelsSince(Duration.ofDays(30));
    }

    @NumberProvider(text = "Active Duels", description = "Whether a duel is active right now.", iconName = "circle", iconColor = Color.GREEN, priority = 60, showInPlayerTable = false)
    public long serverActiveDuels() {
        return duelService.hasActiveDuel() ? 1L : 0L;
    }

    @NumberProvider(text = "Cancelled 7d", description = "Cancelled or failed duels recorded in the last 7 days.", iconName = "triangle-exclamation", iconColor = Color.GREY, priority = 50, showInPlayerTable = false)
    public long serverCancelledDuels7d() {
        return analyticsService.countCancelledDuelsSince(Duration.ofDays(7));
    }

    @NumberProvider(text = "Player Duels", description = "All duel records for this player.", iconName = "user-ninja", iconColor = Color.RED, priority = 100, showInPlayerTable = true)
    public long playerTotalDuels(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.matchesPlayed();
    }

    @NumberProvider(text = "Wins", description = "Lifetime duel wins.", iconName = "trophy", iconColor = Color.GREEN, priority = 90, showInPlayerTable = true)
    public long playerWins(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.wins();
    }

    @NumberProvider(text = "Losses", description = "Lifetime duel losses.", iconName = "skull", iconColor = Color.RED, priority = 80, showInPlayerTable = true)
    public long playerLosses(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.losses();
    }

    @NumberProvider(text = "Draws", description = "Lifetime duel draws or mutual cancellations.", iconName = "handshake", iconColor = Color.LIGHT_BLUE, priority = 70, showInPlayerTable = true)
    public long playerDraws(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.draws();
    }

    @NumberProvider(text = "Current Streak", description = "Current active win streak.", iconName = "fire", iconColor = Color.AMBER, priority = 60, showInPlayerTable = true)
    public long playerCurrentStreak(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.currentWinStreak();
    }

    @NumberProvider(text = "Best Streak", description = "Highest win streak reached.", iconName = "star", iconColor = Color.AMBER, priority = 50, showInPlayerTable = true)
    public long playerBestStreak(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.bestWinStreak();
    }

    @NumberProvider(text = "Duels 24h", description = "Duels recorded for this player in the last 24 hours.", iconName = "clock", iconColor = Color.AMBER, priority = 40, showInPlayerTable = true)
    public long playerDuels24h(UUID playerUUID) {
        return analyticsService.countDuelsForPlayerSince(playerUUID, Duration.ofHours(24));
    }

    @NumberProvider(text = "Duels 7d", description = "Duels recorded for this player in the last 7 days.", iconName = "calendar-week", iconColor = Color.YELLOW, priority = 30, showInPlayerTable = true)
    public long playerDuels7d(UUID playerUUID) {
        return analyticsService.countDuelsForPlayerSince(playerUUID, Duration.ofDays(7));
    }

    @NumberProvider(text = "Duels 30d", description = "Duels recorded for this player in the last 30 days.", iconName = "calendar-days", iconColor = Color.LIGHT_BLUE, priority = 20, showInPlayerTable = true)
    public long playerDuels30d(UUID playerUUID) {
        return analyticsService.countDuelsForPlayerSince(playerUUID, Duration.ofDays(30));
    }

    @NumberProvider(text = "Disconnect Losses", description = "Losses caused by disconnect timeout.", iconName = "plug-circle-xmark", iconColor = Color.GREY, priority = 10, showInPlayerTable = true)
    public long playerDisconnectLosses(UUID playerUUID) {
        PlayerDuelStats stats = statsService.findById(playerUUID);
        return stats == null ? 0L : stats.disconnectForfeitLosses();
    }
}
