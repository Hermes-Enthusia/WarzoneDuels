package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public final class MatchParticipant {
    private final UUID playerId;
    private final String name;
    private boolean drawRequested;
    private Long disconnectDeadlineEpochMs;

    public MatchParticipant(UUID playerId, String name) {
        this.playerId = playerId;
        this.name = name;
    }

    public UUID playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public boolean drawRequested() {
        return drawRequested;
    }

    public void setDrawRequested(boolean drawRequested) {
        this.drawRequested = drawRequested;
    }

    public Long disconnectDeadlineEpochMs() {
        return disconnectDeadlineEpochMs;
    }

    public void setDisconnectDeadlineEpochMs(Long disconnectDeadlineEpochMs) {
        this.disconnectDeadlineEpochMs = disconnectDeadlineEpochMs;
    }
}
