package dev.minecraft.warzoneduels.domain.stats;

import java.util.UUID;

public final class PlayerDuelStats {
    private final UUID playerId;
    private String lastKnownName;
    private int matchesPlayed;
    private int wins;
    private int losses;
    private int draws;
    private int disconnectForfeitLosses;
    private int currentWinStreak;
    private int bestWinStreak;

    public PlayerDuelStats(UUID playerId, String lastKnownName) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName;
    }

    public UUID playerId() {
        return playerId;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public int matchesPlayed() {
        return matchesPlayed;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayed = Math.max(0, matchesPlayed);
    }

    public int wins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = Math.max(0, wins);
    }

    public int losses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = Math.max(0, losses);
    }

    public int draws() {
        return draws;
    }

    public void setDraws(int draws) {
        this.draws = Math.max(0, draws);
    }

    public int disconnectForfeitLosses() {
        return disconnectForfeitLosses;
    }

    public void setDisconnectForfeitLosses(int disconnectForfeitLosses) {
        this.disconnectForfeitLosses = Math.max(0, disconnectForfeitLosses);
    }

    public int currentWinStreak() {
        return currentWinStreak;
    }

    public void setCurrentWinStreak(int currentWinStreak) {
        this.currentWinStreak = Math.max(0, currentWinStreak);
    }

    public int bestWinStreak() {
        return bestWinStreak;
    }

    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreak = Math.max(0, bestWinStreak);
    }

    public void recordWin() {
        matchesPlayed++;
        wins++;
        currentWinStreak++;
        if (currentWinStreak > bestWinStreak) {
            bestWinStreak = currentWinStreak;
        }
    }

    public void recordLoss(boolean disconnectForfeit) {
        matchesPlayed++;
        losses++;
        currentWinStreak = 0;
        if (disconnectForfeit) {
            disconnectForfeitLosses++;
        }
    }

    public void recordDraw() {
        matchesPlayed++;
        draws++;
        currentWinStreak = 0;
    }
}
