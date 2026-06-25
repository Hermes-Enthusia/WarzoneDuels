package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public final class MatchParticipant {
    private final UUID playerUuid;
    private final String playerName;
    private boolean hasRequestedDraw;
    private Long disconnectDeadlineMillis;

    public MatchParticipant(UUID playerId, String name) {
        this.playerUuid = playerId;
        this.playerName = name;
    }

    public UUID playerId() {
        return playerUuid;
    }

    public String name() {
        return playerName;
    }

    public boolean drawRequested() {
        return hasRequestedDraw;
    }

    public void setDrawRequested(boolean drawRequested) {
        this.hasRequestedDraw = drawRequested;
    }

    public Long disconnectDeadlineEpochMs() {
        return disconnectDeadlineMillis;
    }

    public void setDisconnectDeadlineEpochMs(Long disconnectDeadlineEpochMs) {
        this.disconnectDeadlineMillis = disconnectDeadlineEpochMs;
    }
}
