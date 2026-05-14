package dev.minecraft.warzoneduels.adapter.bukkit.gui;

import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;

public final class DuelGuiListener implements Listener {
    private final DuelService duelService;
    private final Map<UUID, Boolean> awaitingWagerInput = new ConcurrentHashMap<>();

    public DuelGuiListener(DuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!awaitingWagerInput.containsKey(playerId)) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(duelService.plugin(), () -> applyCustomWager(event.getPlayer(), input));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        switch (holder.type()) {
            case REQUEST_PREVIEW -> {
                handleRequestPreview(event.getRawSlot(), player);
                return;
            }
            case ACTIVE_SETTINGS -> {
                if (event.getRawSlot() == 44) {
                    player.closeInventory();
                }
                return;
            }
            default -> {
            }
        }

        BuilderSession builder = duelService.getBuilder(player.getUniqueId());
        if (builder == null) {
            player.closeInventory();
            return;
        }

        DuelSettings settings = builder.settings();
        switch (holder.type()) {
            case MAP -> handleMap(event.getRawSlot(), player, settings);
            case RULES -> handleRules(event.getRawSlot(), player, settings);
            case PLACE_ONLY -> handlePlaceOnly(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case EXTRAS -> handleExtras(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case ITEM_RULES -> handleItemRules(event.getRawSlot(), player, settings, event.getClick(), holder.returnToConfirm());
            case WAGER -> handleWager(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case CONFIRM -> handleConfirm(event.getRawSlot(), player, settings);
            case REQUEST_PREVIEW, ACTIVE_SETTINGS -> {
            }
        }
    }

    private void handleMap(int slot, Player player, DuelSettings settings) {
        List<DuelMapOption> options = duelService.mapOptions();
        int[] mapSlots = {20, 22, 24};
        for (int i = 0; i < Math.min(options.size(), mapSlots.length); i++) {
            if (slot != mapSlots[i]) {
                continue;
            }
            DuelMapOption option = options.get(i);
            if (!option.available()) {
                return;
            }
            duelService.applyMapChoice(settings, option);
            player.openInventory(DuelGui.buildRulesGui(settings));
            return;
        }
        if (slot == 40) {
            duelService.clearBuilder(player.getUniqueId());
            player.closeInventory();
        }
    }

    private void handleRules(int slot, Player player, DuelSettings settings) {
        if (slot == 20) {
            settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.NONE);
            player.openInventory(DuelGui.buildItemRulesGui(settings));
            return;
        } else if (slot == 22) {
            settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_ONLY);
            player.openInventory(DuelGui.buildPlaceOnlyGui());
            return;
        } else if (slot == 24) {
            if (!settings.isMapSupportsBlockBreaking()) {
                return;
            }
            settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_BREAK);
            player.openInventory(DuelGui.buildExtrasGui(settings));
            return;
        } else if (slot == 36) {
            player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), settings));
            return;
        } else if (slot == 44) {
            duelService.clearBuilder(player.getUniqueId());
            player.closeInventory();
            return;
        } else {
            return;
        }
    }

    private void handlePlaceOnly(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        if (slot == 20) {
            settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.COBWEB_UTILS);
            settings.clearExplosiveRules();
            player.openInventory(DuelGui.buildItemRulesGui(settings, returnToConfirm));
        } else if (slot == 24) {
            settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.ALL_BLOCKS);
            if (duelService.shouldShowExplosivesMenu(settings)) {
                player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
            } else {
                player.openInventory(DuelGui.buildItemRulesGui(settings, returnToConfirm));
            }
        } else if (slot == 40) {
            player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildRulesGui(settings));
        }
    }

    private void handleExtras(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        if (slot == 20) {
            settings.setAllowCrystalsAnchors(!settings.isAllowCrystalsAnchors());
            player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
        } else if (slot == 22) {
            settings.setAllowExplosiveMinecarts(!settings.isAllowExplosiveMinecarts());
            player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
        } else if (slot == 24) {
            settings.setAllowOtherExplosives(!settings.isAllowOtherExplosives());
            player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
        } else if (slot == 36) {
            if (returnToConfirm) {
                player.openInventory(DuelGui.buildConfirmGui(settings));
            } else if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
                player.openInventory(DuelGui.buildRulesGui(settings));
            } else {
                player.openInventory(DuelGui.buildPlaceOnlyGui());
            }
        } else if (slot == 40) {
            player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildItemRulesGui(settings));
        }
    }

    private void handleItemRules(int slot, Player player, DuelSettings settings, ClickType click, boolean returnToConfirm) {
        if (slot == 20) {
            if (click.isRightClick()) {
                settings.setEnderPearlCooldownSeconds(nextCooldown(settings.getEnderPearlCooldownSeconds()));
            } else {
                settings.setAllowEnderPearls(!settings.isAllowEnderPearls());
            }
        } else if (slot == 22) {
            if (click.isRightClick()) {
                settings.setWindChargeCooldownSeconds(nextCooldown(settings.getWindChargeCooldownSeconds()));
            } else {
                settings.setAllowWindCharges(!settings.isAllowWindCharges());
            }
        } else if (slot == 24) {
            settings.setAllowMaces(!settings.isAllowMaces());
        } else if (slot == 29) {
            settings.setAllowChorusFruit(!settings.isAllowChorusFruit());
        } else if (slot == 31) {
            settings.setAllowSpears(!settings.isAllowSpears());
        } else if (slot == 33) {
            settings.setAllowElytras(!settings.isAllowElytras());
        } else if (slot == 45) {
            if (returnToConfirm) {
                player.openInventory(DuelGui.buildConfirmGui(settings));
            } else if (duelService.shouldShowExplosivesMenu(settings)) {
                player.openInventory(DuelGui.buildExtrasGui(settings));
            } else {
                player.openInventory(settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.NONE
                    ? DuelGui.buildRulesGui(settings)
                    : DuelGui.buildPlaceOnlyGui());
            }
            return;
        } else if (slot == 53) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        } else {
            return;
        }
        player.openInventory(DuelGui.buildItemRulesGui(settings, returnToConfirm));
    }

    private void handleRequestPreview(int slot, Player player) {
        if (slot == 31) {
            duelService.confirmAcceptRequest(player);
            player.closeInventory();
            return;
        }
        if (slot == 27) {
            duelService.denyRequest(player);
            player.closeInventory();
            return;
        }
        if (slot == 35) {
            player.closeInventory();
        }
    }

    private int nextCooldown(int current) {
        return switch (current) {
            case 0 -> 5;
            case 5 -> 10;
            case 10 -> 15;
            case 15 -> 30;
            default -> 0;
        };
    }

    private void handleWager(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        double wager = settings.getWager();
        if (slot == 9) {
            wager += 1;
        } else if (slot == 10) {
            wager += 10;
        } else if (slot == 11) {
            wager += 100;
        } else if (slot == 12) {
            wager += 1000;
        } else if (slot == 14) {
            wager -= 1;
        } else if (slot == 15) {
            wager -= 10;
        } else if (slot == 16) {
            wager -= 100;
        } else if (slot == 17) {
            wager -= 1000;
        } else if (slot == 53) {
            player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildConfirmGui(settings));
            return;
        } else if (slot == 45) {
            player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildItemRulesGui(settings));
            return;
        } else if (slot == 49) {
            wager = 0;
        } else if (slot == 31) {
            awaitingWagerInput.put(player.getUniqueId(), returnToConfirm);
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "[Duel] " + ChatColor.YELLOW + "Type a custom wager amount in chat. Type " + ChatColor.AQUA + "cancel" + ChatColor.YELLOW + " to back out.");
            return;
        } else {
            return;
        }
        settings.setWager(Math.max(0D, Math.min(duelService.maxWager(), wager)));
        player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
    }

    private void applyCustomWager(Player player, String input) {
        Boolean returnToConfirm = awaitingWagerInput.remove(player.getUniqueId());
        if (returnToConfirm == null) {
            return;
        }
        BuilderSession builder = duelService.getBuilder(player.getUniqueId());
        if (builder == null) {
            return;
        }
        DuelSettings settings = builder.settings();
        if (input.equalsIgnoreCase("cancel")) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        double wager;
        try {
            wager = Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "[Duel] " + ChatColor.GRAY + "Invalid amount. Use a number or type cancel.");
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        settings.setWager(Math.max(0D, Math.min(duelService.maxWager(), wager)));
        player.sendMessage(ChatColor.GOLD + "[Duel] " + ChatColor.GREEN + "Wager set to $" + DuelGui.formatAmount(settings.getWager()) + ".");
        player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
    }

    private void handleConfirm(int slot, Player player, DuelSettings settings) {
        if (slot == 19) {
            player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), settings));
        } else if (slot == 21) {
            player.openInventory(DuelGui.buildRulesGui(settings));
        } else if (slot == 23) {
            player.openInventory(DuelGui.buildItemRulesGui(settings, true));
        } else if (slot == 16) {
            if (duelService.shouldShowExplosivesMenu(settings)) {
                player.openInventory(DuelGui.buildExtrasGui(settings, true));
            }
        } else if (slot == 25) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), true));
        } else if (slot == 36) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager()));
        } else if (slot == 40) {
            duelService.sanitizeBuilderSettings(settings);
            duelService.sendRequest(player);
            player.closeInventory();
        } else if (slot == 44) {
            duelService.clearBuilder(player.getUniqueId());
            player.closeInventory();
        }
    }
}
