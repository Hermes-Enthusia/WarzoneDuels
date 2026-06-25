package dev.minecraft.warzoneduels.domain.stats;

import java.util.UUID;

public final class PlayerDuelStats {
    private final UUID playerUuid;
    private String storedLastKnownName;
    private int matchesPlayedCount;
    private int winCount;
    private int lossCount;
    private int drawCount;
    private int disconnectForfeitLossCount;
    private int currentWinStreakCount;
    private int bestWinStreakCount;

    public PlayerDuelStats(UUID playerId, String lastKnownName) {
        this.playerUuid = playerId;
        this.storedLastKnownName = lastKnownName;
    }

    public UUID playerId() {
        return playerUuid;
    }

    public String lastKnownName() {
        return storedLastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.storedLastKnownName = lastKnownName;
    }

    public int matchesPlayed() {
        return matchesPlayedCount;
    }

    public void setMatchesPlayed(int matchesPlayed) {
        this.matchesPlayedCount = Math.max(0, matchesPlayed);
    }

    public int wins() {
        return winCount;
    }

    public void setWins(int wins) {
        this.winCount = Math.max(0, wins);
    }

    public int losses() {
        return lossCount;
    }

    public void setLosses(int losses) {
        this.lossCount = Math.max(0, losses);
    }

    public int draws() {
        return drawCount;
    }

    public void setDraws(int draws) {
        this.drawCount = Math.max(0, draws);
    }

    public int disconnectForfeitLosses() {
        return disconnectForfeitLossCount;
    }

    public void setDisconnectForfeitLosses(int disconnectForfeitLosses) {
        this.disconnectForfeitLossCount = Math.max(0, disconnectForfeitLosses);
    }

    public int currentWinStreak() {
        return currentWinStreakCount;
    }

    public void setCurrentWinStreak(int currentWinStreak) {
        this.currentWinStreakCount = Math.max(0, currentWinStreak);
    }

    public int bestWinStreak() {
        return bestWinStreakCount;
    }

    public void setBestWinStreak(int bestWinStreak) {
        this.bestWinStreakCount = Math.max(0, bestWinStreak);
    }

    public void recordWin() {
        matchesPlayedCount++;
        winCount++;
        currentWinStreakCount++;
        if (currentWinStreakCount > bestWinStreakCount) {
            bestWinStreakCount = currentWinStreakCount;
        }
    }

    public void recordLoss(boolean disconnectForfeit) {
        matchesPlayedCount++;
        lossCount++;
        currentWinStreakCount = 0;
        if (disconnectForfeit) {
            disconnectForfeitLossCount++;
        }
    }

    public void recordDraw() {
        matchesPlayedCount++;
        drawCount++;
        currentWinStreakCount = 0;
    }
}
