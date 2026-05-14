package dev.minecraft.warzoneduels.domain.analytics;

import dev.minecraft.warzoneduels.domain.DuelEndReason;

import java.util.UUID;

public record DuelRecord(
    String reference,
    long startedAtEpochMs,
    long endedAtEpochMs,
    long durationMillis,
    UUID playerOneId,
    String playerOneName,
    UUID playerTwoId,
    String playerTwoName,
    UUID winnerId,
    String winnerName,
    UUID loserId,
    String loserName,
    String mapId,
    String mapName,
    String ruleset,
    String itemRules,
    DuelEndReason endReason,
    boolean countedAsMatch,
    int spectatorCount,
    double wager
) {
}
