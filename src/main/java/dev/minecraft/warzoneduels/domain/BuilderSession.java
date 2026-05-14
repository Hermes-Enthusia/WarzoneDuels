package dev.minecraft.warzoneduels.domain;

import java.util.UUID;

public record BuilderSession(UUID targetId, DuelSettings settings) {
}
