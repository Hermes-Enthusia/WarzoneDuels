package dev.minecraft.warzoneduels.adapter.bukkit.spoils;

import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpoilsGuiFactory {
    private static final int CONTENT_SLOTS = 45;

    private SpoilsGuiFactory() {
    }

    public static Inventory overview(UUID ownerId, List<SpoilsEntry> entries, SpoilsService spoilsService, int page) {
        Inventory inventory = Bukkit.createInventory(new SpoilsOverviewHolder(ownerId, entries.stream().map(SpoilsEntry::entryId).toList(), page), 54, ChatColor.GOLD + "Duel Vault");
        int start = page * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < entries.size(); i++) {
            SpoilsEntry entry = entries.get(start + i);
            inventory.setItem(i, entryItem(entry, spoilsService));
        }
        if (page > 0) {
            inventory.setItem(45, button(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        if (start + CONTENT_SLOTS < entries.size()) {
            inventory.setItem(53, button(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }
        inventory.setItem(49, button(Material.BARRIER, ChatColor.RED + "Close"));
        return inventory;
    }

    public static Inventory detail(UUID ownerId, SpoilsEntry entry, SpoilsService spoilsService, int page) {
        Inventory inventory = Bukkit.createInventory(new SpoilsDetailHolder(ownerId, entry.entryId(), page), 54, ChatColor.GOLD + "Vault: " + entry.sourcePlayerName());
        List<ItemStack> items = entry.items();
        int start = page * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < items.size(); i++) {
            inventory.setItem(i, items.get(start + i));
        }
        inventory.setItem(45, button(Material.ARROW, ChatColor.YELLOW + "Back"));
        if (page > 0) {
            inventory.setItem(46, button(Material.PAPER, ChatColor.YELLOW + "Previous Items"));
        }
        if (start + CONTENT_SLOTS < items.size()) {
            inventory.setItem(47, button(Material.PAPER, ChatColor.YELLOW + "Next Items"));
        }
        inventory.setItem(50, button(Material.HOPPER, ChatColor.GREEN + "Claim All"));
        inventory.setItem(51, button(Material.LAVA_BUCKET, ChatColor.RED + "Delete Remaining"));
        inventory.setItem(53, info(entry, spoilsService));
        return inventory;
    }

    private static ItemStack entryItem(SpoilsEntry entry, SpoilsService spoilsService) {
        ItemStack stack = new ItemStack(spoilsService.displayMaterial(entry));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Vault from " + entry.sourcePlayerName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Items: " + entry.itemCount());
            lore.add(ChatColor.GRAY + "Expires in: " + spoilsService.formatRemainingTime(entry));
            lore.add(ChatColor.YELLOW + "Click to open.");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack info(SpoilsEntry entry, SpoilsService spoilsService) {
        ItemStack stack = new ItemStack(Material.CLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Vault Info");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Winner: " + entry.ownerName());
            lore.add(ChatColor.GRAY + "Source: " + entry.sourcePlayerName());
            lore.add(ChatColor.GRAY + "Expires in: " + spoilsService.formatRemainingTime(entry));
            lore.add(ChatColor.GRAY + "Click items to claim individually.");
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack button(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
