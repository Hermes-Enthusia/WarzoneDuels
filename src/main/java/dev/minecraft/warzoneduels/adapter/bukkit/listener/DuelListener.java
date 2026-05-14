package dev.minecraft.warzoneduels.adapter.bukkit.listener;

import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.domain.DuelRuntimeState;
import dev.minecraft.warzoneduels.util.SpearUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DuelListener implements Listener {
    private final DuelService duelService;
    private final Map<UUID, List<org.bukkit.block.Block>> vanillaEntityExplosionBlocks = new HashMap<>();
    private final Map<String, List<org.bukkit.block.Block>> vanillaBlockExplosionBlocks = new HashMap<>();

    public DuelListener(DuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (duelService.isDuelCountdownActive() && duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!duelService.isBlockPlaceAllowed(event.getBlockPlaced(), event.getItemInHand().getType(), event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (duelService.runtimeState() == DuelRuntimeState.ACTIVE && duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            Material placedType = event.getBlockPlaced().getType();
            if (!duelService.canUseExplosive(placedType, event.getPlayer())) {
                event.setCancelled(true);
                return;
            }
            duelService.trackPlacedBlock(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (duelService.isDuelCountdownActive() && duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!duelService.isBlockBreakAllowed(event.getBlock(), event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (duelService.shouldSuppressArenaBlockDrops(event.getBlock().getLocation())) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDropItems(BlockDropItemEvent event) {
        if (duelService.shouldSuppressArenaBlockDrops(event.getBlockState().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (duelService.shouldProtectArenaShellBlock(event.getBlockClicked().getLocation(), event.getPlayer())
            || duelService.shouldProtectArenaShellBlock(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (duelService.shouldProtectArenaShellBlock(event.getBlockClicked().getLocation(), event.getPlayer())
            || duelService.shouldProtectArenaShellBlock(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            return;
        }
        Item item = event.getItemDrop();
        duelService.allowArenaItemPickup(item.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!duelService.shouldSuppressArenaBlockDrops(event.getLocation())) {
            return;
        }
        if (event.getEntity().getThrower() != null) {
            duelService.allowArenaItemPickup(event.getEntity().getUniqueId());
            return;
        }
        if (duelService.isAllowedArenaItemEntity(event.getEntity().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            duelService.forgetArenaItemEntity(event.getItem().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemDespawn(ItemDespawnEvent event) {
        duelService.forgetArenaItemEntity(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isProtectedArenaShellInteraction(event)) {
            event.setCancelled(true);
            return;
        }
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE && isArenaShellExplosiveInteraction(event)) {
            event.setCancelled(true);
            return;
        }
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.getClickedBlock() != null
            && event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR
            && duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            if (!duelService.canUseExplosive(Material.RESPAWN_ANCHOR, event.getPlayer())) {
                event.setCancelled(true);
                duelService.sendBlockedCombatItemMessage(event.getPlayer());
                return;
            }
            return;
        }
        if (event.getItem() == null) {
            return;
        }
        if (duelService.isInActiveDuel(event.getPlayer().getUniqueId()) && !duelService.isCombatItemEnabled(event.getItem().getType(), event.getPlayer())) {
            event.setCancelled(true);
            duelService.sendBlockedCombatItemMessage(event.getPlayer());
            return;
        }
        if (duelService.isInActiveDuel(event.getPlayer().getUniqueId()) && !duelService.canUseCombatItem(event.getItem().getType(), event.getPlayer())) {
            return;
        }
        if (duelService.isInActiveDuel(event.getPlayer().getUniqueId()) && !duelService.canUseExplosive(event.getItem().getType(), event.getPlayer())) {
            event.setCancelled(true);
            duelService.sendBlockedCombatItemMessage(event.getPlayer());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE || duelService.arena() == null) {
            return;
        }
        Material material = materialForPlacedEntity(event.getEntity());
        if (material == null || !duelService.arena().contains(event.getEntity().getLocation())) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null || !duelService.canUseExplosive(material, player)) {
            event.setCancelled(true);
            if (player != null) {
                duelService.sendBlockedCombatItemMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE) {
            return;
        }
        if (duelService.isInActiveDuel(event.getPlayer().getUniqueId()) && !duelService.isCombatItemEnabled(event.getItem().getType(), event.getPlayer())) {
            event.setCancelled(true);
            duelService.sendBlockedCombatItemMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        if (!duelService.isInActiveDuel(player.getUniqueId())) {
            return;
        }
        Material launchedType = switch (event.getEntity().getType().name()) {
            case "ENDER_PEARL" -> Material.ENDER_PEARL;
            case "WIND_CHARGE" -> Material.WIND_CHARGE;
            default -> null;
        };
        if (launchedType != null) {
            if (!duelService.isCombatItemEnabled(launchedType, player)) {
                event.setCancelled(true);
                duelService.sendBlockedCombatItemMessage(player);
                return;
            }
            if (!duelService.canUseCombatItem(launchedType, player)) {
                return;
            }
            duelService.applyCombatCooldownDeferred(launchedType, player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void captureEntityExplode(EntityExplodeEvent event) {
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE || duelService.arena() == null) {
            return;
        }
        Entity entity = event.getEntity();
        if (!duelService.arena().contains(event.getLocation())) {
            return;
        }
        if (resolveExplosionMaterial(entity) != null) {
            vanillaEntityExplosionBlocks.put(entity.getUniqueId(), new ArrayList<>(event.blockList()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (duelService.arena() == null || !isArenaShellExplosion(event.getLocation(), event.blockList())) {
            return;
        }
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE) {
            event.blockList().clear();
            event.setCancelled(true);
            vanillaEntityExplosionBlocks.remove(event.getEntity().getUniqueId());
            return;
        }
        Entity entity = event.getEntity();
        if (!duelService.arena().contains(event.getLocation())) {
            return;
        }
        Material material = resolveExplosionMaterial(entity);
        if (material == null) {
            return;
        }
        duelService.clearExplosionSource(entity.getUniqueId());
        if (!duelService.isExplosiveMaterialAllowed(material)) {
            event.setCancelled(true);
            vanillaEntityExplosionBlocks.remove(entity.getUniqueId());
            return;
        }
        event.setCancelled(false);
        event.setYield(0F);
        List<org.bukkit.block.Block> vanillaBlocks = vanillaEntityExplosionBlocks.remove(entity.getUniqueId());
        List<org.bukkit.block.Block> sourceBlocks = vanillaBlocks == null ? new ArrayList<>(event.blockList()) : vanillaBlocks;
        event.blockList().clear();
        if (!duelService.shouldExplosionsDamageBlocks()) {
            return;
        }
        for (org.bukkit.block.Block block : sourceBlocks) {
            if (duelService.isArenaTerrainBlock(block.getLocation())) {
                event.blockList().add(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void captureBlockExplode(BlockExplodeEvent event) {
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE) {
            return;
        }
        if (duelService.arena() != null && duelService.arena().contains(event.getBlock().getLocation())) {
            vanillaBlockExplosionBlocks.put(blockExplosionKey(event), new ArrayList<>(event.blockList()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (duelService.arena() == null || !isArenaShellExplosion(event.getBlock().getLocation(), event.blockList())) {
            return;
        }
        if (duelService.runtimeState() != DuelRuntimeState.ACTIVE) {
            event.blockList().clear();
            event.setCancelled(true);
            vanillaBlockExplosionBlocks.remove(blockExplosionKey(event));
            return;
        }
        if (duelService.arena() != null && duelService.arena().contains(event.getBlock().getLocation())) {
            event.setCancelled(false);
            event.setYield(0F);
            List<org.bukkit.block.Block> vanillaBlocks = vanillaBlockExplosionBlocks.remove(blockExplosionKey(event));
            List<org.bukkit.block.Block> sourceBlocks = vanillaBlocks == null ? new ArrayList<>(event.blockList()) : vanillaBlocks;
            event.blockList().clear();
            if (duelService.shouldExplosionsDamageBlocks()) {
                for (org.bukkit.block.Block block : sourceBlocks) {
                    if (duelService.isArenaTerrainBlock(block.getLocation())) {
                        event.blockList().add(block);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) {
            Player attacker = resolveAttackingPlayer(event.getDamager());
            if (duelService.arena() == null || !duelService.arena().contains(crystal.getLocation())) {
                return;
            }
            if (attacker == null || !duelService.isInActiveDuel(attacker.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (!duelService.canUseExplosive(Material.END_CRYSTAL, attacker)) {
                event.setCancelled(true);
                duelService.sendBlockedCombatItemMessage(attacker);
            }
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttackingPlayer(event.getDamager());
        if (attacker != null && duelService.shouldBlockArenaShellPvp(victim, attacker)) {
            event.setCancelled(true);
            return;
        }
        if (duelService.isInsideArenaShell(victim.getLocation())
            && !duelService.isInActiveDuel(victim.getUniqueId())
            && (event.getCause() == DamageCause.ENTITY_EXPLOSION || event.getCause() == DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
            return;
        }
        if (!duelService.isInActiveDuel(victim.getUniqueId())) {
            return;
        }
        if (attacker != null) {
            if (SpearUtil.isSpear(attacker.getInventory().getItemInMainHand())
                && !duelService.isCombatItemEnabled(attacker.getInventory().getItemInMainHand().getType(), attacker)) {
                event.setCancelled(true);
                duelService.sendBlockedCombatItemMessage(attacker);
                return;
            }
            if (!duelService.isCombatItemEnabled(attacker.getInventory().getItemInMainHand().getType(), attacker)) {
                event.setCancelled(true);
                duelService.sendBlockedCombatItemMessage(attacker);
                return;
            }
            if (duelService.shouldCancelDamage(victim, attacker)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getCause() == DamageCause.ENTITY_EXPLOSION || event.getDamager() instanceof TNTPrimed || event.getDamager() instanceof EnderCrystal) {
            return;
        }
        if (!(event.getDamager() instanceof Player) && duelService.isInActiveDuel(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        boolean duelDeath = duelService.isInActiveDuel(event.getEntity().getUniqueId());
        boolean forcedDeath = duelService.consumePendingForcedDeath(event.getEntity().getUniqueId());
        if (!duelDeath && !forcedDeath) {
            return;
        }
        ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (duelDeath) {
            duelService.handleDeath(event.getEntity(), drops);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        duelService.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        duelService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        duelService.handleRespawn(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (duelService.shouldBlockArenaShellEntry(event.getPlayer(), event.getFrom(), to)) {
            event.setTo(event.getFrom());
            duelService.sendMessage(event.getPlayer(), "messages.arena-combat-entry-blocked");
            return;
        }
        if (duelService.arena() == null || !duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!duelService.canUseCombatItem(Material.ELYTRA, event.getPlayer()) && event.getPlayer().isGliding()) {
            event.getPlayer().setGliding(false);
            if (event.getPlayer().getVelocity().getY() > -0.35D) {
                event.getPlayer().setVelocity(event.getPlayer().getVelocity().setY(-0.35D));
            }
        }
        if (to == null) {
            return;
        }
        if (duelService.isDuelCountdownActive()) {
            Location from = event.getFrom();
            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                event.setTo(from);
            }
            return;
        }
        if (!duelService.isNearArenaTerrain(to, 2)) {
            duelService.handleArenaExitAttempt(event.getPlayer());
            return;
        }
        if (!duelService.arena().contains(to)) {
            duelService.handleArenaExitAttempt(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!duelService.isParticipantRestricted(event.getPlayer().getUniqueId())) {
            return;
        }
        if (!duelService.isAllowedCommandForParticipant(event.getMessage())) {
            event.setCancelled(true);
            duelService.sendMessage(event.getPlayer(), "messages.blocked-command");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!duelService.isInActiveDuel(event.getPlayer().getUniqueId())) {
            return;
        }
        if (duelService.isTeleportAllowed(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getCause() == TeleportCause.ENDER_PEARL && !duelService.isCombatItemEnabled(Material.ENDER_PEARL, event.getPlayer())) {
            event.setCancelled(true);
            duelService.sendMessage(event.getPlayer(), "messages.teleport-blocked");
            return;
        }
        if (event.getCause() == TeleportCause.CHORUS_FRUIT && !duelService.isCombatItemEnabled(Material.CHORUS_FRUIT, event.getPlayer())) {
            event.setCancelled(true);
            duelService.sendMessage(event.getPlayer(), "messages.teleport-blocked");
            return;
        }
        Location to = event.getTo();
        if (to != null && event.getCause() == TeleportCause.CHORUS_FRUIT && !duelService.isNearArenaTerrain(to, 2)) {
            Location fallback = duelService.chorusFallbackDestination(event.getPlayer());
            if (fallback != null) {
                event.setTo(fallback);
                return;
            }
            event.setCancelled(true);
            return;
        }
        if (to != null && !duelService.isAllowedDuelTeleportDestination(to)) {
            event.setCancelled(true);
            duelService.sendMessage(event.getPlayer(), "messages.teleport-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.isGliding() && duelService.isInActiveDuel(player.getUniqueId()) && !duelService.isCombatItemEnabled(Material.ELYTRA, player)) {
            event.setCancelled(true);
            duelService.sendBlockedCombatItemMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!duelService.isParticipantRestricted(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        duelService.handleAsyncChat(event.getPlayer());
    }

    private Player resolveAttackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private Material resolveExplosionMaterial(Entity entity) {
        Material tracked = duelService.explosionSourceMaterial(entity.getUniqueId());
        if (tracked != null) {
            return tracked;
        }
        return switch (entity.getType().name()) {
            case "END_CRYSTAL" -> Material.END_CRYSTAL;
            case "TNT_MINECART", "MINECART_TNT" -> Material.TNT_MINECART;
            case "TNT", "PRIMED_TNT" -> Material.TNT;
            default -> null;
        };
    }

    private Material materialForPlacedEntity(Entity entity) {
        return switch (entity.getType().name()) {
            case "END_CRYSTAL" -> Material.END_CRYSTAL;
            case "TNT_MINECART", "MINECART_TNT" -> Material.TNT_MINECART;
            default -> null;
        };
    }

    private String blockExplosionKey(BlockExplodeEvent event) {
        Location location = event.getBlock().getLocation();
        return location.getWorld().getName() + ':' + location.getBlockX() + ':' + location.getBlockY() + ':' + location.getBlockZ();
    }

    private boolean isArenaShellExplosion(Location source, List<org.bukkit.block.Block> blocks) {
        if (duelService.isInsideArenaShell(source)) {
            return true;
        }
        for (org.bukkit.block.Block block : blocks) {
            if (duelService.isInsideArenaShell(block.getLocation())) {
                return true;
            }
        }
        return false;
    }

    private boolean isArenaShellExplosiveInteraction(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null
            && event.getClickedBlock().getType() == Material.RESPAWN_ANCHOR
            && duelService.isInsideArenaShell(event.getClickedBlock().getLocation())) {
            return true;
        }
        if (event.getItem() == null) {
            return false;
        }
        Material type = event.getItem().getType();
        if (type != Material.END_CRYSTAL && type != Material.RESPAWN_ANCHOR && type != Material.TNT_MINECART) {
            return false;
        }
        Location location = event.getClickedBlock() == null ? event.getPlayer().getLocation() : event.getClickedBlock().getLocation();
        return duelService.isInsideArenaShell(location);
    }

    private boolean isProtectedArenaShellInteraction(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return false;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return false;
        }
        return duelService.shouldProtectArenaShellBlock(event.getClickedBlock().getLocation(), event.getPlayer());
    }
}
