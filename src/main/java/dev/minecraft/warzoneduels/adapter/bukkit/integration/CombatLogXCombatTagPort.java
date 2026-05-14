package dev.minecraft.warzoneduels.adapter.bukkit.integration;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.event.PlayerPreTagEvent;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import com.github.sirblobman.combatlogx.api.object.UntagReason;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class CombatLogXCombatTagPort implements CombatTagPort, Listener {
    private final WarzoneDuelsPlugin plugin;
    private final DuelService duelService;

    private ICombatManager combatManager;
    private boolean registered;

    public CombatLogXCombatTagPort(WarzoneDuelsPlugin plugin, DuelService duelService) {
        this.plugin = plugin;
        this.duelService = duelService;
    }

    @Override
    public void enable() {
        Plugin other = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (!(other instanceof ICombatLogX combatLogX) || !other.isEnabled()) {
            return;
        }
        combatManager = combatLogX.getCombatManager();
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        plugin.getLogger().info("Hooked WarzoneDuels into CombatLogX combat tagging.");
    }

    @Override
    public void disable() {
        combatManager = null;
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @Override
    public boolean isInCombat(Player player) {
        return combatManager != null && player != null && player.isOnline() && combatManager.isInCombat(player);
    }

    @Override
    public void clearCombatState(Player player) {
        if (combatManager == null || player == null || !player.isOnline()) {
            return;
        }
        if (combatManager.isInCombat(player)) {
            combatManager.untag(player, UntagReason.EXPIRE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPreTag(PlayerPreTagEvent event) {
        if (duelService.isParticipantRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
