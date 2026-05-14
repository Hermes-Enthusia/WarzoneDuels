package dev.minecraft.warzoneduels.adapter.bukkit.command;

import dev.minecraft.warzoneduels.adapter.bukkit.spoils.SpoilsGuiFactory;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.SpoilsService;
import dev.minecraft.warzoneduels.domain.spoils.SpoilsEntry;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DuelCommand implements CommandExecutor, TabCompleter {
    private final DuelService duelService;
    private final SpoilsService spoilsService;

    public DuelCommand(DuelService duelService, SpoilsService spoilsService) {
        this.duelService = duelService;
        this.spoilsService = spoilsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if ("surrender".equalsIgnoreCase(command.getName()) || "draw".equalsIgnoreCase(command.getName())) {
            duelService.requestDraw(player);
            return true;
        }
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept" -> duelService.acceptRequest(player);
            case "deny" -> duelService.denyRequest(player);
            case "draw", "surrender", "cancel" -> duelService.requestDraw(player);
            case "review" -> openPendingRequestReview(player);
            case "vault" -> openSpoils(player);
            case "stats" -> player.performCommand(args.length >= 2 ? "stats " + args[1] : "stats");
            case "info", "settings" -> duelService.showSettings(player);
            case "reload" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                duelService.reloadFromCommand(player);
            }
            case "restoreloadout" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel restoreloadout <player>");
                    return true;
                }
                Player target = player.getServer().getPlayer(args[1]);
                if (target == null) {
                    duelService.sendMessage(player, "messages.target-offline");
                    return true;
                }
                duelService.restoreLatestLoadout(player, target);
            }
            case "mapsave" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel mapsave <mapId>");
                    return true;
                }
                duelService.saveMapSnapshot(player, args[1]);
            }
            case "mapload" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /duel mapload <mapId>");
                    return true;
                }
                duelService.loadMapSnapshot(player, args[1]);
            }
            case "mapstatus" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                duelService.showMapStatus(player);
            }
            case "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setexit" -> {
                if (!player.hasPermission("warzoneduels.admin")) {
                    duelService.sendMessage(player, "messages.no-permission");
                    return true;
                }
                Location location = player.getLocation();
                if (args.length == 4) {
                    try {
                        location = new Location(
                            player.getWorld(),
                            Double.parseDouble(args[1]),
                            Double.parseDouble(args[2]),
                            Double.parseDouble(args[3])
                        );
                    } catch (NumberFormatException ex) {
                        player.sendMessage(ChatColor.RED + "Invalid coordinates.");
                        return true;
                    }
                }
                duelService.updateArenaLocation(sub, location);
                player.sendMessage(ChatColor.GREEN + "Updated " + sub + " to " + formatLocation(location));
            }
            default -> {
                if (args.length != 1) {
                    sendUsage(player);
                    return true;
                }
                Player target = player.getServer().getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    duelService.sendMessage(player, "messages.target-offline");
                    return true;
                }
                duelService.startBuilder(player, target);
                if (duelService.getBuilder(player.getUniqueId()) != null) {
                    player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), duelService.getBuilder(player.getUniqueId()).settings()));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            for (String option : duelService.commandSuggestions()) {
                if (!sender.hasPermission("warzoneduels.admin") && (option.startsWith("set") || option.equals("reload") || option.equals("restoreloadout") || option.startsWith("map"))) {
                    continue;
                }
                if (typed.isEmpty() || option.startsWith(typed)) {
                    result.add(option);
                }
            }
            sender.getServer().getOnlinePlayers().forEach(player -> {
                String name = player.getName();
                if (typed.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    result.add(name);
                }
            });
        } else if (args.length == 2 && "restoreloadout".equalsIgnoreCase(args[0]) && sender.hasPermission("warzoneduels.admin")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            sender.getServer().getOnlinePlayers().forEach(player -> {
                String name = player.getName();
                if (typed.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    result.add(name);
                }
            });
        } else if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            sender.getServer().getOnlinePlayers().forEach(player -> {
                String name = player.getName();
                if (typed.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    result.add(name);
                }
            });
        } else if (args.length == 2
            && ("mapsave".equalsIgnoreCase(args[0]) || "mapload".equalsIgnoreCase(args[0]))
            && sender.hasPermission("warzoneduels.admin")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            for (String option : List.of("flat_arena", "forest", "desert")) {
                if (typed.isEmpty() || option.startsWith(typed)) {
                    result.add(option);
                }
            }
        }
        return result;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /duel <player|accept|deny|review|draw|surrender|cancel|vault|stats|info|settings>");
        if (player.hasPermission("warzoneduels.admin")) {
            player.sendMessage(ChatColor.GRAY + "Admin: /duel <mapsave|mapload|mapstatus|reload|restoreloadout|setpos1|setpos2|setspawn1|setspawn2|setspectator|setexit>");
        }
    }

    private String formatLocation(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void openSpoils(Player player) {
        List<SpoilsEntry> entries = spoilsService.getEntriesFor(player.getUniqueId());
        if (entries.isEmpty()) {
            spoilsService.sendNoSpoilsMessage(player);
            return;
        }
        player.openInventory(SpoilsGuiFactory.overview(player.getUniqueId(), entries, spoilsService, 0));
    }

    private void openPendingRequestReview(Player player) {
        duelService.openPendingRequestReview(player);
    }
}
