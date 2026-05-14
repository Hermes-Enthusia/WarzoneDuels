package dev.minecraft.warzoneduels.domain;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaSnapshot;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveDuel {
    private final MatchParticipant participantOne;
    private final MatchParticipant participantTwo;
    private final DuelSettings settings;
    private final long startedAtEpochMs;
    private final Set<BlockKey> placedBlocks = new HashSet<>();
    private ArenaSnapshot arenaSnapshot;
    private boolean wagerHeld;
    private double wagerPot;

    public ActiveDuel(MatchParticipant participantOne, MatchParticipant participantTwo, DuelSettings settings, long startedAtEpochMs) {
        this.participantOne = participantOne;
        this.participantTwo = participantTwo;
        this.settings = settings;
        this.startedAtEpochMs = startedAtEpochMs;
    }

    public MatchParticipant participantOne() {
        return participantOne;
    }

    public MatchParticipant participantTwo() {
        return participantTwo;
    }

    public DuelSettings settings() {
        return settings;
    }

    public long startedAtEpochMs() {
        return startedAtEpochMs;
    }

    public Set<BlockKey> placedBlocks() {
        return placedBlocks;
    }

    public ArenaSnapshot arenaSnapshot() {
        return arenaSnapshot;
    }

    public void setArenaSnapshot(ArenaSnapshot arenaSnapshot) {
        this.arenaSnapshot = arenaSnapshot;
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
        return participantOne.playerId().equals(playerId) || participantTwo.playerId().equals(playerId);
    }

    public MatchParticipant participant(UUID playerId) {
        if (participantOne.playerId().equals(playerId)) {
            return participantOne;
        }
        if (participantTwo.playerId().equals(playerId)) {
            return participantTwo;
        }
        return null;
    }

    public MatchParticipant other(UUID playerId) {
        if (participantOne.playerId().equals(playerId)) {
            return participantTwo;
        }
        if (participantTwo.playerId().equals(playerId)) {
            return participantOne;
        }
        return null;
    }
}
