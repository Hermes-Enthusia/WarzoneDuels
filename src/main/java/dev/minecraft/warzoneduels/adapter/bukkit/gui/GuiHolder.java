package dev.minecraft.warzoneduels.adapter.bukkit.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {
    private final GuiType holderType;
    private final boolean shouldReturnToConfirm;

    public GuiHolder(GuiType type) {
        this(type, false);
    }

    public GuiHolder(GuiType type, boolean returnToConfirm) {
        this.holderType = type;
        this.shouldReturnToConfirm = returnToConfirm;
    }

    public GuiType type() {
        return holderType;
    }

    public boolean returnToConfirm() {
        return shouldReturnToConfirm;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
