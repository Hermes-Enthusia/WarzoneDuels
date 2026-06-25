package dev.minecraft.warzoneduels.domain;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaSnapshot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveDuel {
    private final MatchParticipant firstParticipant;
    private final MatchParticipant secondParticipant;
    private final DuelSettings duelSettings;
    private final long startedAtMillis;
    private final Set<BlockKey> placedBlockKeys = new HashSet<>();
    private ArenaSnapshot currentArenaSnapshot;
    private boolean wagerHeld;
    private double wagerPot;

    public ActiveDuel(MatchParticipant participantOne, MatchParticipant participantTwo, DuelSettings settings, long startedAtEpochMs) {
        this.firstParticipant = participantOne;
        this.secondParticipant = participantTwo;
        this.duelSettings = settings;
        this.startedAtMillis = startedAtEpochMs;
    }

    public MatchParticipant participantOne() {
        return firstParticipant;
    }

    public MatchParticipant participantTwo() {
        return secondParticipant;
    }

    public DuelSettings settings() {
        return duelSettings;
    }

    public long startedAtEpochMs() {
        return startedAtMillis;
    }

    public Set<BlockKey> placedBlocks() {
        return placedBlockKeys;
    }

    public ArenaSnapshot arenaSnapshot() {
        return currentArenaSnapshot;
    }

    public void setArenaSnapshot(ArenaSnapshot arenaSnapshot) {
        this.currentArenaSnapshot = arenaSnapshot;
    }

    public boolean isWagerHeld() {
        return wagerHeld;
    }

    public void setWagerHeld(boolean wagerHeld) {
        this.wagerHeld = wagerHeld;
    }

    public double getWagerPot() {
        return wagerPot;
    }

    public void setWagerPot(double wagerPot) {
        this.wagerPot = wagerPot;
    }

    public boolean contains(UUID playerId) {
        return firstParticipant.playerId().equals(playerId) || secondParticipant.playerId().equals(playerId);
    }

    public MatchParticipant participant(UUID playerId) {
        if (firstParticipant.playerId().equals(playerId)) {
            return firstParticipant;
        }
        if (secondParticipant.playerId().equals(playerId)) {
            return secondParticipant;
        }
        return null;
    }

    public MatchParticipant other(UUID playerId) {
        if (firstParticipant.playerId().equals(playerId)) {
            return secondParticipant;
        }
        if (secondParticipant.playerId().equals(playerId)) {
            return firstParticipant;
        }
        return null;
    }
}
