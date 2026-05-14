package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public final class StatsGuiListener implements Listener {
    private static final int[] LEADERBOARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final StatsService statsService;
    private final PlayerHeadCache headCache;

    public StatsGuiListener(StatsService statsService, PlayerHeadCache headCache) {
        this.statsService = statsService;
        this.headCache = headCache;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof StatsGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        switch (holder.type()) {
            case PROFILE -> handleProfile(player, holder, event.getRawSlot());
            case LEADERBOARD -> handleLeaderboard(player, holder, event.getRawSlot());
        }
    }

    private void handleProfile(Player player, StatsGuiHolder holder, int slot) {
        if (slot == 40) {
            player.openInventory(StatsGuiFactory.buildLeaderboard(statsService.topByWins(0, 21), headCache, 0, holder.targetPlayerId()));
            return;
        }
        if (slot == 44) {
            player.closeInventory();
        }
    }

    private void handleLeaderboard(Player player, StatsGuiHolder holder, int slot) {
        int page = holder.page();
        if (slot == 36) {
            PlayerDuelStats profileStats = holder.targetPlayerId() == null
                ? statsService.stats(player.getUniqueId(), player.getName())
                : statsService.findById(holder.targetPlayerId());
            if (profileStats == null) {
                profileStats = statsService.stats(player.getUniqueId(), player.getName());
            }
            player.openInventory(StatsGuiFactory.buildProfile(profileStats, headCache));
            return;
        }
        if (slot == 38 && page > 0) {
            player.openInventory(StatsGuiFactory.buildLeaderboard(statsService.topByWins(page - 1, 21), headCache, page - 1, holder.targetPlayerId()));
            return;
        }
        if (slot == 42) {
            List<PlayerDuelStats> next = statsService.topByWins(page + 1, 21);
            if (!next.isEmpty()) {
                player.openInventory(StatsGuiFactory.buildLeaderboard(next, headCache, page + 1, holder.targetPlayerId()));
            }
            return;
        }
        if (slot == 44) {
            player.closeInventory();
            return;
        }
        for (int i = 0; i < LEADERBOARD_SLOTS.length; i++) {
            if (slot != LEADERBOARD_SLOTS[i]) {
                continue;
            }
            List<PlayerDuelStats> entries = statsService.topByWins(page, 21);
            if (i >= entries.size()) {
                return;
            }
            player.openInventory(StatsGuiFactory.buildProfile(entries.get(i), headCache));
            return;
        }
    }
}
