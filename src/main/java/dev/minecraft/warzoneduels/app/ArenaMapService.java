package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class ArenaMapService {
    private static final DuelMapOption FALLBACK_MAP = new DuelMapOption(
        "flat_arena",
        "Flat Arena",
        "Classic flat colosseum floor.",
        Material.SMOOTH_STONE,
        true,
        true,
        true,
        false,
        true,
        "",
        false,
        "Default map. Safe for standard flat fights."
    );

    private List<DuelMapOption> configuredOptions = List.of(FALLBACK_MAP);
    private Map<String, DuelMapOption> optionsById = Map.of(FALLBACK_MAP.id(), FALLBACK_MAP);
    private DuelMapOption defaultMap = FALLBACK_MAP;
    private String selectedArenaMapId = FALLBACK_MAP.id();

    public void reload(FileConfiguration config) {
        List<DuelMapOption> loaded = new ArrayList<>();
        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection != null) {
            for (String key : mapsSection.getKeys(false)) {
                ConfigurationSection section = mapsSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                Material icon = Material.matchMaterial(section.getString("icon", "MAP"));
                if (icon == null) {
                    icon = Material.MAP;
                }
                loaded.add(new DuelMapOption(
                    key,
                    section.getString("display-name", key),
                    section.getString("description", ""),
                    icon,
                    section.getBoolean("available", false),
                    section.getBoolean("default", false),
                    section.getBoolean("supports-place-only", true),
                    section.getBoolean("supports-block-breaking", false),
                    section.getBoolean("supports-protected-explosives", true),
                    section.getString("schematic-file", ""),
                    section.getBoolean("paste-air", false),
                    section.getString("availability-note", "")
                ));
            }
        }

        if (loaded.isEmpty()) {
            loaded.add(FALLBACK_MAP);
        }

        Map<String, DuelMapOption> indexed = new LinkedHashMap<>();
        DuelMapOption discoveredDefault = null;
        for (DuelMapOption option : loaded) {
            indexed.put(option.id().toLowerCase(Locale.ROOT), option);
            if (option.defaultMap() && discoveredDefault == null) {
                discoveredDefault = option;
            }
        }
        if (discoveredDefault == null) {
            discoveredDefault = indexed.getOrDefault(FALLBACK_MAP.id(), loaded.get(0));
        }

        configuredOptions = List.copyOf(loaded);
        optionsById = Map.copyOf(indexed);
        defaultMap = discoveredDefault;
        if (!optionsById.containsKey(selectedArenaMapId.toLowerCase(Locale.ROOT))) {
            selectedArenaMapId = defaultMap.id();
        }
    }

    public void promoteSavedMaps(Predicate<String> snapshotExists) {
        if (snapshotExists == null || configuredOptions.isEmpty()) {
            return;
        }
        List<DuelMapOption> adjusted = new ArrayList<>(configuredOptions.size());
        for (DuelMapOption option : configuredOptions) {
            boolean unlocked = option.available() || option.defaultMap() || snapshotExists.test(option.id());
            adjusted.add(new DuelMapOption(
                option.id(),
                option.displayName(),
                option.description(),
                option.icon(),
                unlocked,
                option.defaultMap(),
                option.supportsPlaceOnly(),
                option.supportsBlockBreaking(),
                option.supportsProtectedExplosives(),
                option.schematicFile(),
                option.pasteAir(),
                option.availabilityNote()
            ));
        }
        Map<String, DuelMapOption> indexed = new LinkedHashMap<>();
        DuelMapOption discoveredDefault = null;
        for (DuelMapOption option : adjusted) {
            indexed.put(option.id().toLowerCase(Locale.ROOT), option);
            if (option.defaultMap() && discoveredDefault == null) {
                discoveredDefault = option;
            }
        }
        configuredOptions = List.copyOf(adjusted);
        optionsById = Map.copyOf(indexed);
        if (discoveredDefault != null) {
            defaultMap = discoveredDefault;
        }
    }

    public List<DuelMapOption> options() {
        return configuredOptions;
    }

    public DuelMapOption resolve(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return defaultMap;
        }
        return optionsById.getOrDefault(mapId.toLowerCase(Locale.ROOT), defaultMap);
    }

    public DuelMapOption find(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return null;
        }
        DuelMapOption direct = optionsById.get(mapId.toLowerCase(Locale.ROOT));
        if (direct != null) {
            return direct;
        }
        String normalized = mapId.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
        for (DuelMapOption option : configuredOptions) {
            String optionId = option.id().toLowerCase(Locale.ROOT).replace("_", "");
            String display = option.displayName().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "").replace("_", "");
            if (normalized.equals(optionId) || normalized.equals(display)) {
                return option;
            }
        }
        return null;
    }

    public void applySelection(DuelSettings settings, DuelMapOption selected) {
        applyMapMetadata(settings, selected == null ? defaultMap : selected);
        sanitizeSettings(settings);
    }

    public void sanitizeSettings(DuelSettings settings) {
        DuelMapOption option = resolve(settings.getMapId());
        applyMapMetadata(settings, option);
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK && !settings.isMapSupportsBlockBreaking()) {
            settings.setPlaceBreakMode(settings.isMapSupportsPlaceOnly()
                ? DuelSettings.PlaceBreakMode.PLACE_ONLY
                : DuelSettings.PlaceBreakMode.NONE);
        }
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_ONLY && !settings.isMapSupportsPlaceOnly()) {
            settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.NONE);
        }
        if (!settings.shouldShowExplosivesConfiguration()) {
            settings.clearExplosiveRules();
        }
    }

    public void prepareArenaForMatch(ArenaDefinition arena, DuelSettings settings) {
        selectedArenaMapId = resolve(settings.getMapId()).id();
    }

    public void restoreDefaultArena(ArenaDefinition arena) {
        selectedArenaMapId = defaultMap.id();
    }

    public void markCurrentArenaMap(String mapId) {
        selectedArenaMapId = resolve(mapId).id();
    }

    public String currentArenaMapId() {
        return selectedArenaMapId;
    }

    public String defaultMapId() {
        return defaultMap.id();
    }

    private void applyMapMetadata(DuelSettings settings, DuelMapOption option) {
        settings.setMapId(option.id());
        settings.setMapDisplayName(option.displayName());
        settings.setMapDescription(option.description());
        settings.setMapSupportsPlaceOnly(option.supportsPlaceOnly());
        settings.setMapSupportsBlockBreaking(option.supportsBlockBreaking());
        settings.setMapSupportsProtectedExplosives(option.supportsProtectedExplosives());
        settings.setMapSchematicFile(option.schematicFile());
        settings.setMapPasteAir(option.pasteAir());
    }
}
