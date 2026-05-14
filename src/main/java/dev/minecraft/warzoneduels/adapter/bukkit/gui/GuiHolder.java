package dev.minecraft.warzoneduels.adapter.bukkit.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {
    private final GuiType type;
    private final boolean returnToConfirm;

    public GuiHolder(GuiType type) {
        this(type, false);
    }

    public GuiHolder(GuiType type, boolean returnToConfirm) {
        this.type = type;
        this.returnToConfirm = returnToConfirm;
    }

    public GuiType type() {
        return type;
    }

    public boolean returnToConfirm() {
        return returnToConfirm;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
