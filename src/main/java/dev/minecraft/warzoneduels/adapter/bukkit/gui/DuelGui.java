package dev.minecraft.warzoneduels.adapter.bukkit.gui;

import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelSettings;
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
import java.util.Locale;

public final class DuelGui {
    private static final int[] MAP_SLOTS = {20, 22, 24};

    private DuelGui() {
    }

    public static Inventory buildMapGui(List<DuelMapOption> options, DuelSettings settings) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.MAP), 45, ChatColor.DARK_AQUA + "Duel: Choose Map");
        fillBackground(inv);
        inv.setItem(4, item(
            Material.FILLED_MAP,
            ChatColor.GOLD + "Choose the Arena Map",
            ChatColor.GRAY + "Pick the terrain first.",
            ChatColor.GRAY + "The next rules will adapt to this map."
        ));
        for (int i = 0; i < Math.min(options.size(), MAP_SLOTS.length); i++) {
            DuelMapOption option = options.get(i);
            inv.setItem(MAP_SLOTS[i], mapItem(option));
        }
        inv.setItem(40, item(Material.BARRIER, ChatColor.RED + "Cancel"));
        return inv;
    }

    public static Inventory buildRulesGui(DuelSettings settings) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.RULES), 45, ChatColor.DARK_AQUA + "Duel: Arena Rules");
        fillBackground(inv);
        inv.setItem(4, item(
            Material.FILLED_MAP,
            ChatColor.GOLD + settings.getMapDisplayName(),
            ChatColor.GRAY + settings.getMapDescription(),
            "",
            ChatColor.DARK_GRAY + (settings.isMapSupportsBlockBreaking()
                ? "This map supports full terrain formats."
                : settings.isMapSupportsPlaceOnly()
                    ? "This map supports protected-terrain formats."
                    : "This map only supports non-building formats.")
        ));
        inv.setItem(20, item(
            Material.IRON_BARS,
            ChatColor.RED + "No Building or Breaking",
            ChatColor.GRAY + "No blocks may be placed or broken",
            ChatColor.GRAY + "during this duel."
        ));
        inv.setItem(22, item(
            Material.WATER_BUCKET,
            ChatColor.YELLOW + "Limited Placement",
            ChatColor.GRAY + "Allows the placement of some or all items.",
            ChatColor.GRAY + "Blocks placed during the duel can be broken.",
            ChatColor.GRAY + "Original map blocks stay protected."
        ));
        inv.setItem(24, item(
            settings.isMapSupportsBlockBreaking() ? Material.IRON_PICKAXE : Material.BARRIER,
            settings.isMapSupportsBlockBreaking() ? ChatColor.GREEN + "Place & Break" : ChatColor.RED + "Place & Break",
            settings.isMapSupportsBlockBreaking()
                ? ChatColor.GRAY + "Allows full placement and terrain breaking."
                : ChatColor.GRAY + "This map does not support terrain breaking.",
            settings.isMapSupportsBlockBreaking()
                ? ChatColor.GRAY + "Best suited to deeper terrain-based fights."
                : ChatColor.DARK_RED + "Choose a terrain map to use this format."
        ));
        inv.setItem(36, item(Material.ARROW, ChatColor.AQUA + "Back to Maps"));
        inv.setItem(44, item(Material.BARRIER, ChatColor.RED + "Cancel"));
        return inv;
    }

    public static Inventory buildPlaceOnlyGui() {
        return buildPlaceOnlyGui(false);
    }

    public static Inventory buildPlaceOnlyGui(boolean returnToConfirm) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.PLACE_ONLY, returnToConfirm), 45, ChatColor.DARK_AQUA + "Duel: Limited Placement");
        fillBackground(inv);
        inv.setItem(4, item(
            Material.WATER_BUCKET,
            ChatColor.GOLD + "Limited Placement Style",
            ChatColor.GRAY + "Choose what players may place",
            ChatColor.GRAY + "while the terrain stays protected."
        ));
        inv.setItem(20, item(
            Material.COBWEB,
            ChatColor.AQUA + "Utilities Only",
            ChatColor.GRAY + "Allows cobwebs, water, buttons,",
            ChatColor.GRAY + "and pressure plates only.",
            "",
            ChatColor.DARK_GRAY + "Explosives are not available in this format."
        ));
        inv.setItem(24, item(
            Material.BRICKS,
            ChatColor.YELLOW + "All Placeable Blocks",
            ChatColor.GRAY + "Allows standard block placement.",
            ChatColor.GRAY + "Blocks placed during the duel can be broken.",
            ChatColor.GRAY + "Original map blocks stay protected."
        ));
        inv.setItem(40, item(Material.ARROW, ChatColor.AQUA + "Back"));
        return inv;
    }

    public static Inventory buildExtrasGui(DuelSettings settings) {
        return buildExtrasGui(settings, false);
    }

    public static Inventory buildExtrasGui(DuelSettings settings, boolean returnToConfirm) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.EXTRAS, returnToConfirm), 45, ChatColor.DARK_AQUA + "Duel: Explosives");
        fillBackground(inv);
        inv.setItem(4, item(
            Material.TNT,
            ChatColor.GOLD + "Explosive Rules",
            ChatColor.GRAY + "Choose which explosive tools are legal",
            ChatColor.GRAY + "for this duel format."
        ));
        inv.setItem(20, toggleItem(Material.END_CRYSTAL, "Crystals & Anchors", settings.isAllowCrystalsAnchors()));
        inv.setItem(22, toggleItem(Material.TNT_MINECART, "Explosive Minecarts", settings.isAllowExplosiveMinecarts()));
        inv.setItem(24, toggleItem(Material.TNT, "Other Explosive Blocks", settings.isAllowOtherExplosives()));
        inv.setItem(36, item(Material.ARROW, ChatColor.AQUA + (returnToConfirm ? "Back to Review" : "Back")));
        inv.setItem(40, item(Material.IRON_SWORD, ChatColor.GOLD + (returnToConfirm ? "Save and Return" : "Continue to Combat Items")));
        return inv;
    }

    public static Inventory buildItemRulesGui(DuelSettings settings) {
        return buildItemRulesGui(settings, false);
    }

    public static Inventory buildItemRulesGui(DuelSettings settings, boolean returnToConfirm) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.ITEM_RULES, returnToConfirm), 54, ChatColor.DARK_AQUA + "Duel: Combat Items");
        fillBackground(inv);

        inv.setItem(4, item(Material.BOOK, ChatColor.GOLD + "Current Combat Rules", combatSummaryLore(settings)));
        inv.setItem(8, item(
            Material.HOPPER,
            ChatColor.AQUA + "How Editing Works",
            ChatColor.YELLOW + "Left-click: " + ChatColor.WHITE + "enable or disable an item",
            ChatColor.YELLOW + "Right-click: " + ChatColor.WHITE + "edit pearl or wind cooldown"
        ));

        inv.setItem(20, combatToggleItem(
            Material.ENDER_PEARL,
            "Ender Pearls",
            settings.isAllowEnderPearls(),
            List.of(
                ChatColor.GRAY + "Controls pearl movement inside the arena.",
                ChatColor.GRAY + "Blocked pearls cannot be thrown at all."
            ),
            settings.getEnderPearlCooldownSeconds(),
            true
        ));
        inv.setItem(22, combatToggleItem(
            Material.WIND_CHARGE,
            "Wind Charges",
            settings.isAllowWindCharges(),
            List.of(
                ChatColor.GRAY + "Controls wind charge movement and utility.",
                ChatColor.GRAY + "Blocked charges cannot be used at all."
            ),
            settings.getWindChargeCooldownSeconds(),
            true
        ));
        inv.setItem(24, combatToggleItem(
            Material.MACE,
            "Maces",
            settings.isAllowMaces(),
            List.of(
                ChatColor.GRAY + "Controls mace use during the duel.",
                ChatColor.GRAY + "Disable this for non-mace formats."
            ),
            0,
            false
        ));
        inv.setItem(29, combatToggleItem(
            Material.CHORUS_FRUIT,
            "Chorus Fruit",
            settings.isAllowChorusFruit(),
            List.of(
                ChatColor.GRAY + "Controls chorus teleports and escapes.",
                ChatColor.GRAY + "Blocked chorus fruit cannot be eaten."
            ),
            0,
            false
        ));
        inv.setItem(31, combatToggleItem(
            Material.DIAMOND_SPEAR,
            "Spears",
            settings.isAllowSpears(),
            List.of(
                ChatColor.GRAY + "Controls vanilla spear melee combat.",
                ChatColor.GRAY + "Disable this for sword-focused formats."
            ),
            0,
            false
        ));
        inv.setItem(33, combatToggleItem(
            Material.ELYTRA,
            "Elytras",
            settings.isAllowElytras(),
            List.of(
                ChatColor.GRAY + "Controls glide-based movement in the arena.",
                ChatColor.GRAY + "Disable this for grounded formats."
            ),
            0,
            false
        ));

        inv.setItem(45, item(Material.ARROW, ChatColor.AQUA + (returnToConfirm ? "Back to Review" : "Back"), ChatColor.GRAY + "Return to the previous setup step."));
        inv.setItem(53, item(Material.GOLD_INGOT, ChatColor.GOLD + (returnToConfirm ? "Save and Return" : "Continue to Wager"), ChatColor.GRAY + (returnToConfirm ? "Return to the duel review screen." : "Lock in these combat rules and set the wager.")));
        return inv;
    }

    public static Inventory buildWagerGui(DuelSettings settings, double maxWager) {
        return buildWagerGui(settings, maxWager, false);
    }

    public static Inventory buildWagerGui(DuelSettings settings, double maxWager, boolean returnToConfirm) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.WAGER, returnToConfirm), 54, ChatColor.DARK_AQUA + "Duel: Wager");
        fillBackground(inv);
        inv.setItem(4, item(
            Material.EMERALD,
            ChatColor.GOLD + "Set the Wager",
            ChatColor.GRAY + "Use the buttons for quick adjustments.",
            ChatColor.GRAY + "Use the book for a custom amount."
        ));
        inv.setItem(9, item(
            Material.LIME_GLAZED_TERRACOTTA,
            ChatColor.GREEN + "Raise by $1",
            ChatColor.GRAY + "Fine adjustment."
        ));
        inv.setItem(10, item(
            Material.LIME_CONCRETE,
            ChatColor.GREEN + "Raise by $10",
            ChatColor.GRAY + "Small reduction."
        ));
        inv.setItem(11, item(
            Material.LIME_CONCRETE_POWDER,
            ChatColor.GREEN + "Raise by $100",
            ChatColor.GRAY + "Major reduction."
        ));
        inv.setItem(12, item(
            Material.LIME_STAINED_GLASS,
            ChatColor.GREEN + "Raise by $1000",
            ChatColor.GRAY + "Largest reduction."
        ));
        inv.setItem(13, item(
            Material.GOLD_INGOT,
            ChatColor.GOLD + "Current Wager",
            "",
            ChatColor.YELLOW + "$" + formatAmount(settings.getWager()),
            "",
            ChatColor.DARK_GRAY + "Maximum: $" + formatAmount(maxWager),
            ChatColor.GRAY + "This is the amount each player risks."
        ));
        inv.setItem(14, item(
            Material.RED_CONCRETE,
            ChatColor.RED + "Lower by $1",
            ChatColor.GRAY + "Fine adjustment."
        ));
        inv.setItem(15, item(
            Material.RED_TERRACOTTA,
            ChatColor.RED + "Lower by $10",
            ChatColor.GRAY + "Small reduction."
        ));
        inv.setItem(16, item(
            Material.REDSTONE_BLOCK,
            ChatColor.RED + "Lower by $100",
            ChatColor.GRAY + "Major reduction."
        ));
        inv.setItem(17, item(
            Material.NETHER_BRICKS,
            ChatColor.RED + "Lower by $1000",
            ChatColor.GRAY + "Largest reduction."
        ));
        inv.setItem(31, item(
            Material.BOOK,
            ChatColor.AQUA + "Custom Amount",
            "",
            ChatColor.GRAY + "Type a number in chat to set",
            ChatColor.GRAY + "the wager directly.",
            "",
            ChatColor.YELLOW + "Click: " + ChatColor.WHITE + "enter custom amount"
        ));
        inv.setItem(45, item(Material.ARROW, ChatColor.GRAY + (returnToConfirm ? "Back to Review" : "Back")));
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Clear Wager", ChatColor.GRAY + "Reset the wager to $0."));
        inv.setItem(53, item(Material.LIME_CONCRETE, ChatColor.GREEN + (returnToConfirm ? "Save and Return" : "Continue"), ChatColor.GRAY + (returnToConfirm ? "Return to the duel review screen." : "Finish the setup review.")));
        return inv;
    }

    public static Inventory buildConfirmGui(DuelSettings settings) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.CONFIRM), 45, ChatColor.GOLD + "Duel: Review");
        fillBackground(inv);
        inv.setItem(4, item(Material.BOOK, ChatColor.GOLD + "Review the Duel",
            ChatColor.GRAY + "Click any section below to edit it.",
            ChatColor.GRAY + "Each editor returns here when saved."
        ));
        inv.setItem(19, item(Material.FILLED_MAP, ChatColor.YELLOW + "Map",
            value("Map", settings.getMapDisplayName()),
            "",
            action("Click to edit")
        ));
        inv.setItem(21, item(Material.BOOK, ChatColor.YELLOW + "Block Rules",
            value("Place", placeRuleState(settings)),
            value("Break", breakRuleState(settings)),
            "",
            action("Click to edit")
        ));
        inv.setItem(23, item(Material.IRON_SWORD, ChatColor.YELLOW + "Combat Items",
            value("Pearls", cooldownRuleState(settings.isAllowEnderPearls(), settings.getEnderPearlCooldownSeconds())),
            value("Wind Charges", cooldownRuleState(settings.isAllowWindCharges(), settings.getWindChargeCooldownSeconds())),
            value("Elytras", ruleState(settings.isAllowElytras())),
            value("Maces", ruleState(settings.isAllowMaces())),
            value("Spears", ruleState(settings.isAllowSpears())),
            value("Chorus Fruit", ruleState(settings.isAllowChorusFruit())),
            "",
            action("Click to edit")
        ));
        inv.setItem(16, settings.shouldShowExplosivesConfiguration()
            ? item(Material.TNT, ChatColor.YELLOW + "Explosives",
                value("Crystals / Anchors", boolState(settings.isAllowCrystalsAnchors())),
                value("TNT Minecarts", boolState(settings.isAllowExplosiveMinecarts())),
                value("TNT / Other", boolState(settings.isAllowOtherExplosives())),
                "",
                action("Click to edit"))
            : item(Material.GRAY_DYE, ChatColor.YELLOW + "Explosives",
                value("Explosives", settings.formatExplosives()),
                "",
                ChatColor.DARK_RED + "No explosive configuration in this format."));
        String wager = settings.getWager() > 0 ? "$" + formatAmount(settings.getWager() * 2D) : "None";
        inv.setItem(25, item(Material.GOLD_INGOT, ChatColor.YELLOW + "Wager",
            value("Total Wager", wager),
            "",
            action("Click to edit")
        ));
        inv.setItem(36, item(Material.ARROW, ChatColor.GRAY + "Back"));
        inv.setItem(40, item(Material.LIME_WOOL, ChatColor.GREEN + "Send Duel Request"));
        inv.setItem(44, item(Material.BARRIER, ChatColor.RED + "Cancel"));
        return inv;
    }

    public static Inventory buildRequestPreviewGui(String requesterName, DuelSettings settings) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.REQUEST_PREVIEW), 36, ChatColor.GOLD + "Duel Request");
        inv.setItem(4, item(Material.PAPER, ChatColor.GOLD + "Challenger",
            value("Player", requesterName)
        ));
        inv.setItem(10, item(Material.FILLED_MAP, ChatColor.YELLOW + "Map",
            value("Map", settings.getMapDisplayName())
        ));
        inv.setItem(12, item(Material.BOOK, ChatColor.YELLOW + "Block Rules",
            value("Place", placeRuleState(settings)),
            value("Break", breakRuleState(settings))
        ));
        inv.setItem(14, item(Material.IRON_SWORD, ChatColor.YELLOW + "Combat Items",
            value("Pearls", cooldownRuleState(settings.isAllowEnderPearls(), settings.getEnderPearlCooldownSeconds())),
            value("Wind Charges", cooldownRuleState(settings.isAllowWindCharges(), settings.getWindChargeCooldownSeconds())),
            value("Elytras", ruleState(settings.isAllowElytras())),
            value("Maces", ruleState(settings.isAllowMaces())),
            value("Spears", ruleState(settings.isAllowSpears())),
            value("Chorus Fruit", ruleState(settings.isAllowChorusFruit()))
        ));
        inv.setItem(16, settings.shouldShowExplosivesConfiguration()
            ? item(Material.TNT, ChatColor.YELLOW + "Explosives",
                value("Crystals / Anchors", boolState(settings.isAllowCrystalsAnchors())),
                value("TNT Minecarts", boolState(settings.isAllowExplosiveMinecarts())),
                value("TNT / Other", boolState(settings.isAllowOtherExplosives())))
            : item(Material.GRAY_DYE, ChatColor.YELLOW + "Explosives",
                value("Explosives", settings.formatExplosives())));
        String wager = settings.getWager() > 0 ? "$" + formatAmount(settings.getWager() * 2D) : "None";
        inv.setItem(22, item(Material.GOLD_INGOT, ChatColor.YELLOW + "Wager",
            value("Total Wager", wager)
        ));
        inv.setItem(27, item(Material.RED_WOOL, ChatColor.RED + "Deny"));
        inv.setItem(31, item(Material.LIME_WOOL, ChatColor.GREEN + "Accept"));
        inv.setItem(35, item(Material.BARRIER, ChatColor.GRAY + "Close"));
        return inv;
    }

    public static Inventory buildActiveSettingsGui(DuelSettings settings) {
        Inventory inv = Bukkit.createInventory(new GuiHolder(GuiType.ACTIVE_SETTINGS), 45, ChatColor.GOLD + "Active Duel Settings");
        fillBackground(inv);
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.GOLD + "Current Duel Settings",
            ChatColor.GRAY + "A read-only view of the active duel."
        ));
        inv.setItem(19, item(Material.FILLED_MAP, ChatColor.YELLOW + "Map",
            value("Map", settings.getMapDisplayName())
        ));
        inv.setItem(21, item(Material.BOOK, ChatColor.YELLOW + "Block Rules",
            value("Place", placeRuleState(settings)),
            value("Break", breakRuleState(settings))
        ));
        inv.setItem(23, item(Material.IRON_SWORD, ChatColor.YELLOW + "Combat Items",
            value("Pearls", cooldownRuleState(settings.isAllowEnderPearls(), settings.getEnderPearlCooldownSeconds())),
            value("Wind Charges", cooldownRuleState(settings.isAllowWindCharges(), settings.getWindChargeCooldownSeconds())),
            value("Elytras", ruleState(settings.isAllowElytras())),
            value("Maces", ruleState(settings.isAllowMaces())),
            value("Spears", ruleState(settings.isAllowSpears())),
            value("Chorus Fruit", ruleState(settings.isAllowChorusFruit()))
        ));
        inv.setItem(25, settings.shouldShowExplosivesConfiguration()
            ? item(Material.TNT, ChatColor.YELLOW + "Explosives",
                value("Crystals / Anchors", boolState(settings.isAllowCrystalsAnchors())),
                value("TNT Minecarts", boolState(settings.isAllowExplosiveMinecarts())),
                value("TNT / Other", boolState(settings.isAllowOtherExplosives())))
            : item(Material.GRAY_DYE, ChatColor.YELLOW + "Explosives",
                value("Explosives", settings.formatExplosives())));
        String wager = settings.getWager() > 0 ? "$" + formatAmount(settings.getWager() * 2D) : "None";
        inv.setItem(31, item(Material.GOLD_INGOT, ChatColor.YELLOW + "Wager",
            value("Total Wager", wager)
        ));
        inv.setItem(44, item(Material.BARRIER, ChatColor.RED + "Close"));
        return inv;
    }

    private static ItemStack toggleItem(Material material, String label, boolean enabled) {
        return toggleItem(material, label, enabled, 0, false);
    }

    private static ItemStack toggleItem(Material material, String label, boolean enabled, int cooldownSeconds, boolean supportsCooldown) {
        String name = (enabled ? ChatColor.GREEN : ChatColor.RED) + label;
        String status = enabled ? "Enabled" : "Disabled";
        if (supportsCooldown) {
            return item(
                material,
                name,
                ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + status : ChatColor.RED + status),
                ChatColor.GRAY + "Cooldown: " + ChatColor.AQUA + cooldownSeconds + "s",
                "",
                ChatColor.YELLOW + "Click: " + ChatColor.WHITE + "toggle this rule"
            );
        }
        return item(
            material,
            name,
            ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + status : ChatColor.RED + status),
            "",
            ChatColor.YELLOW + "Click: " + ChatColor.WHITE + "toggle this rule"
        );
    }

    private static ItemStack combatToggleItem(
        Material material,
        String label,
        boolean enabled,
        List<String> description,
        int cooldownSeconds,
        boolean supportsCooldown
    ) {
        List<String> lore = new ArrayList<>(description);
        lore.add("");
        lore.add(ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Blocked"));
        if (supportsCooldown) {
            lore.add(ChatColor.GRAY + "Cooldown: " + ChatColor.AQUA + cooldownSeconds + "s");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-click: " + ChatColor.WHITE + "toggle access");
            lore.add(ChatColor.YELLOW + "Right-click: " + ChatColor.WHITE + "change cooldown");
        } else {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left-click: " + ChatColor.WHITE + "toggle access");
        }
        return item(material, (enabled ? ChatColor.GREEN : ChatColor.RED) + label, lore.toArray(String[]::new));
    }

    private static ItemStack mapItem(DuelMapOption option) {
        String nameColor = option.available() ? ChatColor.GOLD.toString() : ChatColor.RED.toString();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + option.description());
        lore.add("");
        lore.add(ChatColor.GRAY + (option.supportsBlockBreaking()
            ? "Terrain map: full place/break supported."
            : option.supportsPlaceOnly()
                ? "Protected map: placement formats supported."
                : "Restricted map: placement formats unavailable."));
        lore.add("");
        if (option.available()) {
            lore.add(ChatColor.YELLOW + "Click to select this arena.");
        } else {
            lore.add(ChatColor.RED + "Not available yet.");
        }
        if (option.hasSchematic()) {
            lore.add(ChatColor.DARK_GRAY + "Schematic: " + option.schematicFile());
        }
        if (!option.availabilityNote().isBlank()) {
            lore.add(ChatColor.DARK_GRAY + option.availabilityNote());
        }
        return item(option.icon(), nameColor + option.displayName(), lore.toArray(String[]::new));
    }

    private static String[] combatSummaryLore(DuelSettings settings) {
        return new String[] {
            ChatColor.DARK_GRAY + "Movement",
            "",
            ChatColor.GRAY + "Ender Pearls: " + ruleState(settings.isAllowEnderPearls(), settings.getEnderPearlCooldownSeconds()),
            ChatColor.GRAY + "Wind Charges: " + ruleState(settings.isAllowWindCharges(), settings.getWindChargeCooldownSeconds()),
            "",
            ChatColor.DARK_GRAY + "Weapons",
            "",
            ChatColor.GRAY + "Maces: " + ruleState(settings.isAllowMaces()),
            ChatColor.GRAY + "Chorus Fruit: " + ruleState(settings.isAllowChorusFruit()),
            ChatColor.GRAY + "Spears: " + ruleState(settings.isAllowSpears()),
            ChatColor.GRAY + "Elytras: " + ruleState(settings.isAllowElytras())
        };
    }

    private static String ruleState(boolean enabled) {
        return enabled ? ChatColor.GREEN + "Allowed" : ChatColor.RED + "Blocked";
    }

    private static String ruleState(boolean enabled, int cooldownSeconds) {
        if (!enabled) {
            return ChatColor.RED + "Blocked";
        }
        if (cooldownSeconds <= 0) {
            return ChatColor.GREEN + "Allowed";
        }
        return ChatColor.GREEN + "Allowed" + ChatColor.DARK_GRAY + " (" + cooldownSeconds + "s)";
    }

    private static String boolState(boolean enabled) {
        return enabled ? ChatColor.GREEN + "Enabled" : ChatColor.RED + "Blocked";
    }

    private static String section(String text) {
        return ChatColor.DARK_GRAY + text;
    }

    private static String value(String label, String value) {
        return ChatColor.GRAY + label + ": " + ChatColor.WHITE + value;
    }

    private static String note(String text) {
        return ChatColor.GRAY + text;
    }

    private static String action(String text) {
        return ChatColor.YELLOW + text;
    }

    private static String placeRuleState(DuelSettings settings) {
        return switch (settings.getPlaceBreakMode()) {
            case NONE -> ChatColor.RED + "Not Allowed";
            case PLACE_ONLY -> settings.getPlaceOnlyMode() == DuelSettings.PlaceOnlyMode.COBWEB_UTILS
                ? ChatColor.YELLOW + "Limited - Utilities"
                : ChatColor.YELLOW + "Limited";
            case PLACE_BREAK -> ChatColor.GREEN + "Allowed";
        };
    }

    private static String breakRuleState(DuelSettings settings) {
        return switch (settings.getPlaceBreakMode()) {
            case NONE -> ChatColor.RED + "Not Allowed";
            case PLACE_ONLY -> settings.getPlaceOnlyMode() == DuelSettings.PlaceOnlyMode.COBWEB_UTILS
                ? ChatColor.YELLOW + "Limited - Placed Utilities"
                : ChatColor.YELLOW + "Limited - Placed Blocks";
            case PLACE_BREAK -> ChatColor.GREEN + "Allowed";
        };
    }

    private static String cooldownRuleState(boolean enabled, int cooldownSeconds) {
        if (!enabled) {
            return ChatColor.RED + "Blocked";
        }
        if (cooldownSeconds > 0) {
            return ChatColor.AQUA + String.valueOf(cooldownSeconds) + ChatColor.GRAY + " second cooldown";
        }
        return ChatColor.GREEN + "Allowed";
    }

    private static String blockRuleSummary(DuelSettings settings) {
        return switch (settings.getPlaceBreakMode()) {
            case NONE -> "No building or terrain changes.";
            case PLACE_ONLY -> settings.getPlaceOnlyMode() == DuelSettings.PlaceOnlyMode.COBWEB_UTILS
                ? "Only utility placement is allowed. Original terrain stays protected."
                : "Placed blocks can be broken. Original terrain stays protected.";
            case PLACE_BREAK -> "Full placement and terrain breaking are allowed.";
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
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    public static String formatAmount(double amount) {
        if (amount % 1D == 0D) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}
