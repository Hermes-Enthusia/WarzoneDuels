package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public record DuelRequest(
    UUID requesterId,
    UUID targetId,
    String requesterName,
    String targetName,
    DuelSettings settings,
    long createdAtEpochMs
) {
}
