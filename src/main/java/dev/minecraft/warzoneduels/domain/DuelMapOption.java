package dev.minecraft.warzoneduels.domain;

import org.bukkit.Material;

public record DuelMapOption(
    String id,
    String displayName,
    String description,
    Material icon,
    boolean available,
    boolean defaultMap,
    boolean supportsPlaceOnly,
    boolean supportsBlockBreaking,
    boolean supportsProtectedExplosives,
    String schematicFile,
    boolean pasteAir,
    String availabilityNote
) {
    public boolean hasSchematic() {
        return schematicFile != null && !schematicFile.isBlank();
    }
}
