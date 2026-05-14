package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.UUID;

public final class SpoilsGuiListener implements Listener {
    private final SpoilsService spoilsService;

    public SpoilsGuiListener(SpoilsService spoilsService) {
        this.spoilsService = spoilsService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AbstractSpoilsHolder holder)) {
            return;
        }
        if (!holder.ownerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        event.setCancelled(true);

        if (holder instanceof SpoilsOverviewHolder overviewHolder) {
            handleOverviewClick(player, overviewHolder, event.getRawSlot());
            return;
        }
        if (holder instanceof SpoilsDetailHolder detailHolder) {
            handleDetailClick(player, detailHolder, event.getRawSlot());
        }
    }

    private void handleOverviewClick(Player player, SpoilsOverviewHolder holder, int slot) {
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (entries.isEmpty()) {
            player.closeInventory();
            spoilsService.sendNoSpoilsMessage(player);
            return;
        }
        int page = holder.page();
        if (slot >= 0 && slot < 45) {
            int index = (page * 45) + slot;
            if (index >= entries.size()) {
                return;
            }
            SpoilsEntry entry = entries.get(index);
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, 0));
            return;
        }
        if (slot == 45 && page > 0) {
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, page - 1));
            return;
        }
        if (slot == 53 && ((page + 1) * 45) < entries.size()) {
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, page + 1));
            return;
        }
        if (slot == 49) {
            player.closeInventory();
        }
    }

    private void handleDetailClick(Player player, SpoilsDetailHolder holder, int slot) {
        SpoilsEntry entry = spoilsService.getEntry(holder.entryId());
        if (entry == null || entry.isEmpty()) {
            List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
            if (entries.isEmpty()) {
                player.closeInventory();
                spoilsService.sendNoSpoilsMessage(player);
                return;
            }
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
            return;
        }

        int page = holder.page();
        if (slot >= 0 && slot < 45) {
            int index = (page * 45) + slot;
            if (spoilsService.claimSingleItem(player, holder.entryId(), index)) {
                reopenAfterClaim(player, holder.entryId(), page);
            }
            return;
        }
        if (slot == 45) {
            List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
            return;
        }
        if (slot == 46 && page > 0) {
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, page - 1));
            return;
        }
        if (slot == 47 && ((page + 1) * 45) < entry.items().size()) {
            player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), entry, spoilsService, page + 1));
            return;
        }
        if (slot == 50) {
            int claimed = spoilsService.claimAll(player, holder.entryId());
            if (claimed <= 0) {
                player.sendMessage(ChatColor.RED + "No items were claimed.");
            }
            reopenAfterClaim(player, holder.entryId(), page);
            return;
        }
        if (slot == 51) {
            if (spoilsService.deleteRemaining(player, holder.entryId())) {
                List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
                if (entries.isEmpty()) {
                    player.closeInventory();
                    spoilsService.sendNoSpoilsMessage(player);
                    return;
                }
                player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
            }
        }
    }

    private void reopenAfterClaim(Player player, UUID entryId, int page) {
        SpoilsEntry refreshed = spoilsService.getEntry(entryId);
        if (refreshed == null || refreshed.isEmpty()) {
            List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
            if (entries.isEmpty()) {
                player.closeInventory();
                spoilsService.sendNoSpoilsMessage(player);
                return;
            }
            player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
            return;
        }
        int newPage = Math.min(page, Math.max(0, (refreshed.items().size() - 1) / 45));
        player.openInventory(SpoilsGuiFactory.detail(player.getUniqueId(), refreshed, spoilsService, newPage));
    }
}
