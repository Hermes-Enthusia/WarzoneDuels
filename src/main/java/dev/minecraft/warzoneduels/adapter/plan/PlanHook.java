package dev.minecraft.warzoneduels.adapter.plan;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelAnalyticsService;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.StatsService;
import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.delivery.web.ResolverService;
import com.djrapitops.plan.extension.ExtensionService;

public final class PlanHook {
    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;
    private final StatsService statsService;
    private final DuelAnalyticsService analyticsService;

    public PlanHook(
        WarzoneDuelsPlugin plugin,
        DuelService duelService,
        StatsService statsService,
        DuelAnalyticsService analyticsService
    ) {
        this.plugin = plugin;
        this.duelService = duelService;
        this.statsService = statsService;
        this.analyticsService = analyticsService;
    }

    public void hookIntoPlan() {
        if (!areCapabilitiesAvailable()) {
            return;
        }
        registerDataExtension();
        registerPageExtension();
        listenForPlanReloads();
    }

    private boolean areCapabilitiesAvailable() {
        CapabilityService capabilities = CapabilityService.getInstance();
        return capabilities.hasCapability("DATA_EXTENSION_VALUES")
            && capabilities.hasCapability("PAGE_EXTENSION_RESOLVERS");
    }

    private void registerDataExtension() {
        try {
            ExtensionService.getInstance().register(new WarzoneDuelsDataExtension(statsService, analyticsService, duelService));
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Plan is enabled but not ready for the duel data extension: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Plan rejected the duel data extension: " + ex.getMessage());
        }
    }

    private void registerPageExtension() {
        try {
            ResolverService service = ResolverService.getInstance();
            if (service.getResolver("/warzone-duels").isEmpty()) {
                service.registerResolver("WarzoneDuels", "/warzone-duels", new WarzoneDuelsPlanResolver(plugin, statsService, analyticsService, duelService));
            }
        } catch (IllegalStateException ex) {
            plugin.getLogger().warning("Plan is enabled but not ready for the duel dashboard page: " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Plan rejected the duel dashboard page: " + ex.getMessage());
        }
    }

    private void listenForPlanReloads() {
        CapabilityService.getInstance().registerEnableListener(isEnabled -> {
            if (isEnabled) {
                registerDataExtension();
                registerPageExtension();
            }
        });
    }
}
