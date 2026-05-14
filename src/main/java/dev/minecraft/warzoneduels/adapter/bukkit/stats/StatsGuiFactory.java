package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StatsGuiFactory {
    private static final int[] LEADERBOARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private StatsGuiFactory() {
    }

    public static Inventory buildProfile(PlayerDuelStats stats, PlayerHeadCache headCache) {
        Inventory inv = Bukkit.createInventory(new StatsGuiHolder(StatsGuiType.PROFILE, stats.playerId(), 0), 45, ChatColor.DARK_AQUA + "Duel Stats");
        fillBackground(inv);

        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Death Duel Profile",
            ChatColor.GRAY + "A clean look at this player's duel record."
        ));

        ItemStack head = headCache.createHead(stats.playerId(), ChatColor.GOLD + stats.lastKnownName());
        ItemMeta headMeta = head.getItemMeta();
        if (headMeta != null) {
            headMeta.setLore(List.of(
                ChatColor.GRAY + "Wins: " + ChatColor.GREEN + stats.wins(),
                ChatColor.GRAY + "Losses: " + ChatColor.RED + stats.losses(),
                ChatColor.GRAY + "Draws: " + ChatColor.AQUA + stats.draws()
            ));
            head.setItemMeta(headMeta);
        }
        inv.setItem(13, head);

        inv.setItem(20, statItem(Material.IRON_SWORD, "Matches Played", String.valueOf(stats.matchesPlayed()), "Total completed duels recorded."));
        inv.setItem(21, statItem(Material.EMERALD, "Wins", String.valueOf(stats.wins()), "Victories earned in the arena."));
        inv.setItem(22, statItem(Material.REDSTONE, "Losses", String.valueOf(stats.losses()), "Defeats taken in the arena."));
        inv.setItem(23, statItem(Material.ENDER_EYE, "Win/Loss Ratio", formatRatio(stats), "Wins divided by losses."));
        inv.setItem(24, statItem(Material.GOLDEN_HELMET, "Draws", String.valueOf(stats.draws()), "Mutual duel cancellations."));

        inv.setItem(29, statItem(Material.BLAZE_POWDER, "Current Streak", String.valueOf(stats.currentWinStreak()), "Active consecutive wins."));
        inv.setItem(31, statItem(Material.NETHER_STAR, "Best Streak", String.valueOf(stats.bestWinStreak()), "Highest win streak reached."));
        inv.setItem(33, statItem(Material.SKELETON_SKULL, "Disconnect Forfeits", String.valueOf(stats.disconnectForfeitLosses()), "Losses from failing to return in time."));

        inv.setItem(40, item(Material.PLAYER_HEAD, ChatColor.GOLD + "Wins Leaderboard",
            ChatColor.GRAY + "View the top duel winners.",
            "",
            ChatColor.YELLOW + "Click to open"
        ));
        inv.setItem(44, item(Material.BARRIER, ChatColor.RED + "Close"));
        return inv;
    }

    public static Inventory buildLeaderboard(List<PlayerDuelStats> entries, PlayerHeadCache headCache, int page, java.util.UUID returnProfileId) {
        Inventory inv = Bukkit.createInventory(new StatsGuiHolder(StatsGuiType.LEADERBOARD, returnProfileId, page), 45, ChatColor.DARK_AQUA + "Wins Leaderboard");
        fillBackground(inv);
        inv.setItem(4, item(Material.TOTEM_OF_UNDYING, ChatColor.GOLD + "Top Duel Winners",
            ChatColor.GRAY + "Ordered by total wins.",
            ChatColor.GRAY + "Click a player head to inspect their profile."
        ));

        for (int i = 0; i < Math.min(entries.size(), LEADERBOARD_SLOTS.length); i++) {
            PlayerDuelStats stats = entries.get(i);
            int rank = (page * LEADERBOARD_SLOTS.length) + i + 1;
            ItemStack head = headCache.createHead(stats.playerId(), colorRank(rank) + "#" + rank + " " + ChatColor.GOLD + stats.lastKnownName());
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setLore(List.of(
                    ChatColor.GRAY + "Wins: " + ChatColor.GREEN + stats.wins(),
                    ChatColor.GRAY + "Losses: " + ChatColor.RED + stats.losses(),
                    ChatColor.GRAY + "Win/Loss: " + ChatColor.AQUA + formatRatio(stats),
                    ChatColor.GRAY + "Best Streak: " + ChatColor.GOLD + stats.bestWinStreak(),
                    "",
                    ChatColor.YELLOW + "Click to view profile"
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(LEADERBOARD_SLOTS[i], head);
        }

        inv.setItem(36, item(Material.ARROW, ChatColor.AQUA + "Back"));
        if (page > 0) {
            inv.setItem(38, item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Previous Page"));
        }
        if (entries.size() == LEADERBOARD_SLOTS.length) {
            inv.setItem(42, item(Material.SPECTRAL_ARROW, ChatColor.YELLOW + "Next Page"));
        }
        inv.setItem(44, item(Material.BARRIER, ChatColor.RED + "Close"));
        return inv;
    }

    private static ItemStack statItem(Material material, String label, String value, String description) {
        return item(material, ChatColor.GOLD + label,
            ChatColor.WHITE + value,
            "",
            ChatColor.GRAY + description
        );
    }

    private static String formatRatio(PlayerDuelStats stats) {
        if (stats.losses() <= 0) {
            return stats.wins() > 0 ? "Perfect" : "0.00";
        }
        return String.format(java.util.Locale.US, "%.2f", (double) stats.wins() / (double) stats.losses());
    }

    private static String colorRank(int rank) {
        return switch (rank) {
            case 1 -> ChatColor.GOLD.toString();
            case 2 -> ChatColor.WHITE.toString();
            case 3 -> ChatColor.YELLOW.toString();
            default -> ChatColor.GRAY.toString();
        };
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            Collections.addAll(loreList, lore);
            meta.setLore(loreList);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void fillBackground(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }
}
