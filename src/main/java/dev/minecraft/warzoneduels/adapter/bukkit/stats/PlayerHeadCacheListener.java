package dev.minecraft.warzoneduels.adapter.bukkit.stats;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerHeadCacheListener implements Listener {
    private final PlayerHeadCache headCache;

    public PlayerHeadCacheListener(PlayerHeadCache headCache) {
        this.headCache = headCache;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        headCache.cacheFromOnline(event.getPlayer());
    }
}
