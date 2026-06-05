package dev.minecraft.warzoneduels.app;

import dev.minecraft.warzoneduels.BlockKey;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.gui.DuelGui;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.LoadoutArchiveStore;
import dev.minecraft.warzoneduels.adapter.bukkit.persistence.RuntimeStateStore;
import dev.minecraft.warzoneduels.adapter.bukkit.reset.ArenaResetService;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.ArenaDefinition;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelRequest;
import dev.minecraft.warzoneduels.domain.DuelRuntimeState;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.LoadoutSnapshot;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;
import dev.minecraft.warzoneduels.domain.terrain.ArenaMapOperationStatus;
import dev.minecraft.warzoneduels.port.EconomyPort;
import dev.minecraft.warzoneduels.port.SpawnPort;
import dev.minecraft.warzoneduels.port.CombatTagPort;
import dev.minecraft.warzoneduels.util.SpearUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DuelService {
    private static final Set<String> BUILT_IN_ALLOWED_DUEL_COMMANDS = Set.of(
        "draw",
        "surrender",
        "duel draw",
        "duel surrender",
        "duel cancel",
        "duel info",
        "duel settings"
    );

    private final WarzoneDuelsPlugin plugin;
    private final EconomyPort economyPort;
    private final SpawnPort spawnPort;
    private final RuntimeStateStore runtimeStateStore;
    private final LoadoutArchiveStore loadoutArchiveStore;
    private final ArenaResetService arenaResetService;
    private final SpoilsService spoilsService;
    private final StatsService statsService;
    private final DuelAnalyticsService duelAnalyticsService;
    private final ArenaMapService arenaMapService;
    private final ArenaTerrainService arenaTerrainService;
    private CombatTagPort combatTagPort;

    private final Map<UUID, BuilderSession> builders = new HashMap<>();
    private final Set<UUID> oneTimeTeleportAllowance = new HashSet<>();
    private final Set<UUID> allowedArenaItemEntityIds = new HashSet<>();
    private final Map<BlockKey, Long> allowedArenaItemSpawnLocations = new ConcurrentHashMap<>();
    private final Set<UUID> respawnToSpawn = new HashSet<>();
    private final Set<UUID> recoveryTeleportIds = new HashSet<>();
    private final Set<UUID> activeParticipantIndex = ConcurrentHashMap.newKeySet();
    private final Map<UUID, LoadoutSnapshot> disconnectSnapshots = new HashMap<>();
    private final Set<UUID> pendingForcedDeathIds = new HashSet<>();
    private final Map<UUID, Long> blockedItemMessageCooldowns = new HashMap<>();
    private final Map<UUID, Long> arenaExitMessageCooldowns = new HashMap<>();
    private final Map<UUID, Material> trackedExplosionSources = new HashMap<>();

    private ArenaDefinition arena;
    private DuelRequest pendingRequest;
    private volatile ActiveDuel activeDuel;
    private ActiveDuel preparingDuel;
    private QueuedDuelStart queuedDuelStart;

    private String prefix;
    private int requestExpireSeconds;
    private int disconnectGraceSeconds;
    private double maxWager;
    private boolean allowSameIp;
    private boolean allowWaterDrain;
    private boolean clearPlacedBlocksWhenNoBreak;
    private int startCountdownSeconds;
    private String matchmakingWorld;
    private int matchmakingMinX;
    private int matchmakingMaxX;
    private int matchmakingMinY;
    private int matchmakingMaxY;
    private int matchmakingMinZ;
    private int matchmakingMaxZ;
    private boolean blockCombatEntry = true;
    private List<String> allowedDuelCommands = List.of();
    private List<DuelMapOption> mapOptions = List.of();

    private BukkitTask requestExpiryTask;
    private BukkitTask disconnectMonitorTask;
    private BukkitTask countdownTask;
    private BukkitTask queuedStartTask;
    private BukkitTask containmentTask;
    private boolean duelCountdownActive;

    public DuelService(
        WarzoneDuelsPlugin plugin,
        EconomyPort economyPort,
        SpawnPort spawnPort,
        RuntimeStateStore runtimeStateStore,
        LoadoutArchiveStore loadoutArchiveStore,
        ArenaResetService arenaResetService,
        SpoilsService spoilsService,
        StatsService statsService,
        DuelAnalyticsService duelAnalyticsService,
        ArenaMapService arenaMapService,
        ArenaTerrainService arenaTerrainService,
        CombatTagPort combatTagPort
    ) {
        this.plugin = plugin;
        this.economyPort = economyPort;
        this.spawnPort = spawnPort;
        this.runtimeStateStore = runtimeStateStore;
        this.loadoutArchiveStore = loadoutArchiveStore;
        this.arenaResetService = arenaResetService;
        this.spoilsService = spoilsService;
        this.statsService = statsService;
        this.duelAnalyticsService = duelAnalyticsService;
        this.arenaMapService = arenaMapService;
        this.arenaTerrainService = arenaTerrainService;
        this.combatTagPort = combatTagPort;
    }

    public void setCombatTagPort(CombatTagPort combatTagPort) {
        this.combatTagPort = combatTagPort;
    }

    public void enable() {
        loadoutArchiveStore.enable();
        reloadConfig();
        recoveryTeleportIds.clear();
        recoveryTeleportIds.addAll(runtimeStateStore.loadRecoveryTeleportIds());
        recoverActiveDuelIfNeeded();
    }

    public void disable(boolean serverStopping) {
        cancelRequestExpiryTask();
        cancelDisconnectMonitorTask();
        cancelQueuedStartTask();
        cancelCountdownTask();
        cancelContainmentTask();

        if (preparingDuel != null) {
            refundWagerIfHeld(preparingDuel);
            preparingDuel = null;
        }

        if (serverStopping) {
            handleServerStoppingDisable();
            runtimeStateStore.clearReloadResumeMarker();
            runtimeStateStore.clearRuntime();
            builders.clear();
            pendingRequest = null;
            activeDuel = null;
            queuedDuelStart = null;
            rebuildParticipantIndex();
            loadoutArchiveStore.shutdown();
            return;
        }

        if (activeDuel != null) {
            runtimeStateStore.saveActiveDuelSync(activeDuel);
            runtimeStateStore.markReloadResume();
        } else {
            runtimeStateStore.clearRuntime();
            runtimeStateStore.clearReloadResumeMarker();
        }
        loadoutArchiveStore.shutdown();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();
        prefix = color(config.getString("messages.prefix", "&6[Duel]&r "));
        requestExpireSeconds = Math.max(5, config.getInt("settings.request-expire-seconds", 120));
        disconnectGraceSeconds = Math.max(5, config.getInt("settings.disconnect-grace-seconds", 30));
        maxWager = Math.max(0D, config.getDouble("settings.max-wager", 100000D));
        allowSameIp = config.getBoolean("settings.allow-same-ip-duels", false);
        blockCombatEntry = config.getBoolean("settings.block-combat-entry", true);
        allowWaterDrain = config.getBoolean("settings.allow-water-drain", true);
        clearPlacedBlocksWhenNoBreak = config.getBoolean("settings.clear-placed-blocks-when-no-break", true);
        startCountdownSeconds = Math.max(0, config.getInt("settings.start-countdown-seconds", 5));
        matchmakingWorld = config.getString("matchmaking-spawn.world", config.getString("arena.world", "world"));
        matchmakingMinX = Math.min(config.getInt("matchmaking-spawn.corner1.x", -218), config.getInt("matchmaking-spawn.corner2.x", 219));
        matchmakingMaxX = Math.max(config.getInt("matchmaking-spawn.corner1.x", -218), config.getInt("matchmaking-spawn.corner2.x", 219));
        matchmakingMinY = Math.min(config.getInt("matchmaking-spawn.min-y", -64), config.getInt("matchmaking-spawn.max-y", 320));
        matchmakingMaxY = Math.max(config.getInt("matchmaking-spawn.min-y", -64), config.getInt("matchmaking-spawn.max-y", 320));
        matchmakingMinZ = Math.min(config.getInt("matchmaking-spawn.corner1.z", -404), config.getInt("matchmaking-spawn.corner2.z", 188));
        matchmakingMaxZ = Math.max(config.getInt("matchmaking-spawn.corner1.z", -404), config.getInt("matchmaking-spawn.corner2.z", 188));
        allowedDuelCommands = config.getStringList("settings.allowed-duel-commands").stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        arenaMapService.reload(config);
        arenaTerrainService.reload(config);
        arenaMapService.promoteSavedMaps(arenaTerrainService::hasSnapshot);
        mapOptions = arenaMapService.options();
        arena = loadArena(config);
    }

    public DuelRuntimeState runtimeState() {
        if (activeDuel != null) {
            return DuelRuntimeState.ACTIVE;
        }
        if (pendingRequest != null) {
            return DuelRuntimeState.REQUEST_PENDING;
        }
        return DuelRuntimeState.IDLE;
    }

    public ArenaDefinition arena() {
        return arena;
    }

    public boolean isArenaReady() {
        return arena != null && arena.isReady();
    }

    public BuilderSession getBuilder(UUID playerId) {
        return builders.get(playerId);
    }

    public void clearBuilder(UUID playerId) {
        builders.remove(playerId);
    }

    public double maxWager() {
        return maxWager;
    }

    public WarzoneDuelsPlugin plugin() {
        return plugin;
    }

    public List<DuelMapOption> mapOptions() {
        return mapOptions;
    }

    public boolean hasActiveDuel() {
        return activeDuel != null;
    }

    public DuelRequest pendingRequest() {
        return pendingRequest;
    }

    public boolean isInActiveDuel(UUID playerId) {
        return activeDuel != null && activeDuel.contains(playerId);
    }

    public boolean isParticipantRestricted(UUID playerId) {
        return activeParticipantIndex.contains(playerId);
    }

    public boolean isTeleportAllowed(UUID playerId) {
        return oneTimeTeleportAllowance.remove(playerId);
    }

    public void allowTeleportOnce(UUID playerId) {
        oneTimeTeleportAllowance.add(playerId);
    }

    public void startBuilder(Player sender, Player target) {
        requirePrimaryThread();
        if (!isArenaReady()) {
            sendMessage(sender, "messages.no-arena");
            return;
        }
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessage(sender, "messages.duel-already-running");
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sendMessage(sender, "messages.self-duel");
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(sender)) {
            sendMessage(sender, "messages.player-in-combat");
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(target)) {
            sendMessage(sender, "messages.target-in-combat", "{player}", target.getName());
            return;
        }
        if (!isInsideMatchmakingSpawn(sender.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
            sendMessage(sender, "messages.must-be-at-spawn");
            return;
        }
        if (isParticipantRestricted(sender.getUniqueId()) || isParticipantRestricted(target.getUniqueId())) {
            sendMessage(sender, "messages.target-busy");
            return;
        }
        builders.put(sender.getUniqueId(), new BuilderSession(target.getUniqueId(), new DuelSettings()));
    }

    public void sendRequest(Player requester) {
        requirePrimaryThread();
        BuilderSession builder = builders.get(requester.getUniqueId());
        if (builder == null) {
            sendMessage(requester, "messages.no-builder");
            return;
        }
        if (pendingRequest != null || activeDuel != null || queuedDuelStart != null) {
            sendMessage(requester, "messages.duel-already-running");
            return;
        }
        Player target = Bukkit.getPlayer(builder.targetId());
        if (target == null || !target.isOnline()) {
            builders.remove(requester.getUniqueId());
            sendMessage(requester, "messages.target-offline");
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(requester)) {
            sendMessage(requester, "messages.player-in-combat");
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(target)) {
            sendMessage(requester, "messages.target-in-combat", "{player}", target.getName());
            return;
        }
        if (!isInsideMatchmakingSpawn(requester.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
            sendMessage(requester, "messages.must-be-at-spawn");
            return;
        }
        if (!allowSameIp && sameIp(requester, target)) {
            sendMessage(requester, "messages.same-ip-blocked");
            return;
        }
        DuelSettings settings = builder.settings().copy();
        if (settings.getWager() > maxWager) {
            settings.setWager(maxWager);
        }
        if (settings.getWager() > 0D && !economyPort.isEnabled()) {
            sendMessage(requester, "messages.wager-disabled");
            return;
        }
        if (settings.getWager() > 0D) {
            if (!economyPort.has(requester, settings.getWager())) {
                sendMessage(requester, "messages.cannot-afford", "{player}", requester.getName());
                return;
            }
            if (!economyPort.has(target, settings.getWager())) {
                sendMessage(requester, "messages.cannot-afford", "{player}", target.getName());
                return;
            }
        }
        pendingRequest = new DuelRequest(
            requester.getUniqueId(),
            target.getUniqueId(),
            requester.getName(),
            target.getName(),
            settings,
            System.currentTimeMillis()
        );
        builders.remove(requester.getUniqueId());
        sendMessage(requester, "messages.request-sent", "{player}", target.getName());
        sendRequestDetails(target, pendingRequest);
        scheduleRequestExpiry();
    }

    public void acceptRequest(Player target) {
        requirePrimaryThread();
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, "messages.no-pending-request");
            return;
        }
        openPendingRequestReview(target);
    }

    public void confirmAcceptRequest(Player target) {
        requirePrimaryThread();
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, "messages.no-pending-request");
            return;
        }
        Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
        if (requester == null || !requester.isOnline()) {
            clearPendingRequest();
            sendMessage(target, "messages.target-offline");
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(requester)) {
            clearPendingRequest();
            sendMessage(requester, "messages.player-in-combat");
            sendMessage(target, "messages.target-in-combat", "{player}", requester.getName());
            return;
        }
        if (combatTagPort != null && combatTagPort.isInCombat(target)) {
            clearPendingRequest();
            sendMessage(target, "messages.player-in-combat");
            if (requester != null) {
                sendMessage(requester, "messages.target-in-combat", "{player}", target.getName());
            }
            return;
        }
        if (!isInsideMatchmakingSpawn(requester.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
            clearPendingRequest();
            sendMessage(requester, "messages.must-be-at-spawn");
            sendMessage(target, "messages.must-be-at-spawn");
            return;
        }
        DuelSettings settings = pendingRequest.settings();
        if (settings.getWager() > 0D) {
            if (!economyPort.isEnabled()) {
                clearPendingRequest();
                sendMessage(target, "messages.wager-disabled");
                return;
            }
            if (!economyPort.has(requester, settings.getWager())) {
                clearPendingRequest();
                sendMessage(target, "messages.cannot-afford", "{player}", requester.getName());
                return;
            }
            if (!economyPort.has(target, settings.getWager())) {
                clearPendingRequest();
                sendMessage(target, "messages.cannot-afford", "{player}", target.getName());
                return;
            }
        }
        clearPendingRequest();
        if (arenaTerrainService.isBusy()) {
            queueDuelStart(requester, target, settings);
            return;
        }
        startDuel(requester, target, settings);
    }

    public void openPendingRequestReview(Player target) {
        requirePrimaryThread();
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, "messages.no-pending-request");
            return;
        }
        target.openInventory(DuelGui.buildRequestPreviewGui(pendingRequest.requesterName(), pendingRequest.settings()));
    }

    public void denyRequest(Player target) {
        requirePrimaryThread();
        if (pendingRequest == null || !pendingRequest.targetId().equals(target.getUniqueId())) {
            sendMessage(target, "messages.no-pending-request");
            return;
        }
        Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
        clearPendingRequest();
        if (requester != null) {
            sendMessage(requester, "messages.request-denied");
        }
    }

    public void requestDraw(Player player) {
        requirePrimaryThread();
        if (queuedDuelStart != null && queuedDuelStart.involves(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
            Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
            clearQueuedDuelStart();
            if (requester != null) {
                sendMessageRaw(requester, prefix + ChatColor.YELLOW + "The queued duel was canceled.");
            }
            if (target != null && !target.getUniqueId().equals(player.getUniqueId())) {
                sendMessageRaw(target, prefix + ChatColor.YELLOW + "The queued duel was canceled.");
            }
            return;
        }
        if (activeDuel == null) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        participant.setDrawRequested(true);
        sendToParticipants("messages.draw-requested", "{player}", player.getName());
        if (activeDuel.participantOne().drawRequested() && activeDuel.participantTwo().drawRequested()) {
            concludeDuel(null, DuelEndReason.DRAW, true);
            return;
        }
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void showSettings(Player player) {
        if (activeDuel == null) {
            sendMessage(player, "messages.settings-none");
            return;
        }
        player.openInventory(DuelGui.buildActiveSettingsGui(activeDuel.settings()));
    }

    public void reloadFromCommand(CommandSender sender) {
        requirePrimaryThread();
        if (arenaTerrainService.isBusy()) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Arena terrain is busy. Wait for the current map operation to finish first.");
            return;
        }
        reloadConfig();
        if (plugin.spoilsService() != null) {
            plugin.spoilsService().reloadConfig();
        }
        sendMessageRaw(sender, prefix + ChatColor.GREEN + "Config reloaded.");
    }

    public void saveMapSnapshot(CommandSender sender, String rawMapId) {
        requirePrimaryThread();
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "You cannot save arena maps while a duel is active or pending.");
            return;
        }
        DuelMapOption option = arenaMapService.find(rawMapId);
        if (option == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Unknown map id. Use one of: " + mapOptions.stream().map(DuelMapOption::id).toList());
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Capturing arena terrain for " + option.displayName() + "...");
        arenaTerrainService.captureSnapshot(option.id(),
            () -> {
                arenaMapService.markCurrentArenaMap(option.id());
                sendMessageRaw(sender, prefix + ChatColor.GREEN + "Saved terrain snapshot for " + option.displayName() + ".");
            },
            message -> sendMessageRaw(sender, prefix + ChatColor.RED + message)
        );
    }

    public void loadMapSnapshot(CommandSender sender, String rawMapId) {
        requirePrimaryThread();
        if (activeDuel != null || pendingRequest != null || queuedDuelStart != null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "You cannot load arena maps while a duel is active or pending.");
            return;
        }
        DuelMapOption option = arenaMapService.find(rawMapId);
        if (option == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "Unknown map id. Use one of: " + mapOptions.stream().map(DuelMapOption::id).toList());
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Loading arena terrain for " + option.displayName() + "...");
        arenaTerrainService.loadSnapshot(option.id(),
            () -> sendMessageRaw(sender, prefix + ChatColor.GREEN + "Arena terrain restored to " + option.displayName() + "."),
            message -> sendMessageRaw(sender, prefix + ChatColor.RED + message)
        );
    }

    public void showMapStatus(CommandSender sender) {
        ArenaMapOperationStatus status = arenaTerrainService.status();
        if (!status.busy()) {
            sendMessageRaw(sender, prefix + ChatColor.YELLOW + "Arena terrain idle. Current map: " + ChatColor.WHITE + arenaMapService.currentArenaMapId());
            return;
        }
        sendMessageRaw(
            sender,
            prefix + ChatColor.YELLOW + "Arena terrain " + status.type() + " " + ChatColor.WHITE + status.mapId()
                + ChatColor.YELLOW + " (" + status.processedBlocks() + "/" + status.totalBlocks() + ")."
        );
    }

    public void restoreLatestLoadout(CommandSender sender, Player target) {
        requirePrimaryThread();
        LoadoutSnapshot snapshot = loadoutArchiveStore.loadLatestPreDuel(target.getUniqueId());
        if (snapshot == null) {
            sendMessageRaw(sender, prefix + ChatColor.RED + "No archived pre-duel loadout exists for " + target.getName() + ".");
            return;
        }
        loadoutArchiveStore.apply(target, snapshot);
        sendMessageRaw(sender, prefix + ChatColor.GREEN + "Restored archived pre-duel loadout for " + target.getName() + ".");
    }

    public void handleQuit(Player player) {
        requirePrimaryThread();
        builders.remove(player.getUniqueId());
        if (queuedDuelStart != null && queuedDuelStart.involves(player.getUniqueId())) {
            Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
            Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
            if (requester != null) {
                sendMessage(requester, "messages.target-offline");
            }
            if (target != null) {
                sendMessage(target, "messages.target-offline");
            }
            clearQueuedDuelStart();
        }
        if (pendingRequest != null) {
            if (pendingRequest.requesterId().equals(player.getUniqueId()) || pendingRequest.targetId().equals(player.getUniqueId())) {
                clearPendingRequest();
            }
        }
        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        disconnectSnapshots.put(player.getUniqueId(), loadoutArchiveStore.capture(player));
        participant.setDisconnectDeadlineEpochMs(System.currentTimeMillis() + (disconnectGraceSeconds * 1000L));
        sendToParticipants("messages.disconnect-grace", "{player}", participant.name(), "{seconds}", String.valueOf(disconnectGraceSeconds));
        startDisconnectMonitor();
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void handleJoin(Player player) {
        requirePrimaryThread();
        if (spoilsService.prepareForcedDeathIfPending(player)) {
            pendingForcedDeathIds.add(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.setHealth(0.0D);
                }
            });
        }
        if (recoveryTeleportIds.contains(player.getUniqueId())) {
            teleportToExit(player);
            recoveryTeleportIds.remove(player.getUniqueId());
            runtimeStateStore.clearRecoveryTeleportId(player.getUniqueId());
        }
        if (shouldBlockArenaFootprintEntry(player, player.getLocation())) {
            handleUnauthorizedArenaEntry(player);
        }
        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        participant.setDisconnectDeadlineEpochMs(null);
        disconnectSnapshots.remove(player.getUniqueId());
        clearExternalCombatState(player);
        teleportToAssignedSpawn(player);
        sendMessage(player, "messages.rejoined-duel");
        if (!hasDisconnectingParticipant()) {
            cancelDisconnectMonitorTask();
        } else {
            startDisconnectMonitor();
        }
        runtimeStateStore.queueActiveDuelSave(activeDuel);
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        requirePrimaryThread();
        UUID playerId = event.getPlayer().getUniqueId();
        if (respawnToSpawn.remove(playerId) || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
    }

    public void handleDeath(Player player, List<ItemStack> drops) {
        requirePrimaryThread();
        if (activeDuel == null) {
            return;
        }
        MatchParticipant dead = activeDuel.participant(player.getUniqueId());
        if (dead == null) {
            return;
        }
        MatchParticipant winner = activeDuel.other(dead.playerId());
        Player winnerPlayer = winner == null ? null : Bukkit.getPlayer(winner.playerId());
        if (winner != null) {
            spoilsService.createSpoils(winner.playerId(), winner.name(), dead.playerId(), dead.name(), drops);
        }
        respawnToSpawn.add(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
    }

    public void handleAsyncChat(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> sendMessage(player, "messages.chat-blocked"));
    }

    public void handleArenaExitAttempt(Player player) {
        requirePrimaryThread();
        teleportToAssignedSpawn(player);
        sendArenaExitBlockedMessage(player);
    }

    public void handleUnauthorizedArenaEntry(Player player) {
        requirePrimaryThread();
        teleportToExit(player);
        String message = plugin.getConfig().getString("messages.arena-entry-blocked", "");
        if (message == null || message.isBlank()) {
            sendMessageRaw(player, ChatColor.RED + "Only active duel participants may enter the fighting area.");
            return;
        }
        sendMessage(player, "messages.arena-entry-blocked");
    }

    public boolean isAllowedCommandForParticipant(String rawCommand) {
        String normalized = rawCommand.toLowerCase(Locale.ROOT).replaceFirst("^/", "").trim();
        if (BUILT_IN_ALLOWED_DUEL_COMMANDS.contains(normalized)) {
            return true;
        }
        for (String allowed : allowedDuelCommands) {
            if (normalized.equals(allowed) || normalized.startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlockBreakAllowed(Block block, Player player) {
        if (arena != null && arena.contains(block.getLocation()) && activeDuel == null) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        boolean participant = isInActiveDuel(player.getUniqueId());
        if (!arena.contains(block.getLocation())) {
            return !participant;
        }
        if (!participant) {
            return false;
        }
        DuelSettings settings = activeDuel.settings();
        BlockKey blockKey = BlockKey.fromLocation(block.getLocation());
        if (activeDuel.placedBlocks().contains(blockKey)) {
            return true;
        }
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return settings.isMapSupportsBlockBreaking() && isArenaTerrainBlock(block.getLocation());
        }
        return false;
    }

    public boolean isBlockPlaceAllowed(Block block, Material itemType, Player player) {
        if (arena != null && arena.contains(block.getLocation()) && activeDuel == null) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        boolean participant = isInActiveDuel(player.getUniqueId());
        if (!arena.contains(block.getLocation())) {
            return !participant;
        }
        if (!participant) {
            return false;
        }
        if (!arenaTerrainService.containsFootprintBlock(block.getLocation())) {
            return false;
        }
        DuelSettings settings = activeDuel.settings();
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.NONE) {
            return false;
        }
        if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return true;
        }
        if (settings.getPlaceOnlyMode() == DuelSettings.PlaceOnlyMode.ALL_BLOCKS) {
            return true;
        }
        return isCobwebUtility(itemType);
    }

    public void trackPlacedBlock(Block block) {
        requirePrimaryThread();
        if (activeDuel != null) {
            activeDuel.placedBlocks().add(BlockKey.fromLocation(block.getLocation()));
        }
    }

    public boolean canUseExplosive(Material material, Player actor) {
        if (!isRestrictedExplosiveMaterial(material)) {
            return true;
        }
        if (activeDuel == null) {
            return true;
        }
        if (!isInActiveDuel(actor.getUniqueId())) {
            return false;
        }
        DuelSettings settings = activeDuel.settings();
        if (material == Material.END_CRYSTAL || material == Material.RESPAWN_ANCHOR) {
            if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
                return settings.isAllowCrystalsAnchors();
            }
            if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_ONLY) {
                return settings.isAllowCrystalsAnchors();
            }
            return false;
        }
        if (settings.getPlaceBreakMode() != DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return false;
        }
        if (material == Material.TNT_MINECART) {
            return settings.isAllowExplosiveMinecarts();
        }
        if (material == Material.TNT) {
            return settings.isAllowOtherExplosives();
        }
        return true;
    }

    public boolean isExplosiveMaterialAllowed(Material material) {
        if (!isRestrictedExplosiveMaterial(material)) {
            return true;
        }
        if (activeDuel == null) {
            return true;
        }
        DuelSettings settings = activeDuel.settings();
        if (material == Material.END_CRYSTAL || material == Material.RESPAWN_ANCHOR) {
            if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
                return settings.isAllowCrystalsAnchors();
            }
            if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_ONLY) {
                return settings.isAllowCrystalsAnchors();
            }
            return false;
        }
        if (settings.getPlaceBreakMode() != DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            return false;
        }
        if (material == Material.TNT_MINECART) {
            return settings.isAllowExplosiveMinecarts();
        }
        if (material == Material.TNT) {
            return settings.isAllowOtherExplosives();
        }
        return true;
    }

    public boolean shouldExplosionsDamageBlocks() {
        if (activeDuel == null) {
            return true;
        }
        return activeDuel.settings().getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK
            && activeDuel.settings().isMapSupportsBlockBreaking();
    }

    public boolean isArenaTerrainBlock(Location location) {
        return arenaTerrainService.containsFootprintBlock(location);
    }

    public boolean isInsideArenaShell(Location location) {
        return arena != null && arena.contains(location);
    }

    public boolean shouldBlockArenaLiquidFlow(Location from, Location to) {
        if (arena == null || (from == null && to == null)) {
            return false;
        }
        boolean fromInside = from != null && arena.contains(from);
        boolean toInside = to != null && arena.contains(to);
        if (!fromInside && !toInside) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        if (!fromInside || !toInside) {
            return true;
        }
        return !arenaTerrainService.isNearFootprint(to, 3);
    }

    public boolean shouldBlockArenaEnvironmentalBlockChange(Location location) {
        return arena != null && location != null && arena.contains(location);
    }

    public boolean shouldProtectArenaShellBlock(Location location, Player player) {
        if (arena == null || location == null || !arena.contains(location)) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        if (player == null || !isInActiveDuel(player.getUniqueId())) {
            return true;
        }
        return !arenaTerrainService.containsFootprintBlock(location);
    }

    public boolean shouldBlockArenaShellEntry(Player player, Location from, Location to) {
        if (!blockCombatEntry || player == null || combatTagPort == null || arena == null || to == null) {
            return false;
        }
        if (isInActiveDuel(player.getUniqueId())) {
            return false;
        }
        if (!arena.contains(to) || (from != null && arena.contains(from))) {
            return false;
        }
        return combatTagPort.isInCombat(player);
    }

    public boolean shouldBlockArenaFootprintEntry(Player player, Location to) {
        if (player == null || arena == null || to == null) {
            return false;
        }
        if (isInActiveDuel(player.getUniqueId())) {
            return false;
        }
        return arenaTerrainService.isNearFootprint(to, 1);
    }

    public boolean isNearArenaTerrain(Location location, int radius) {
        return arenaTerrainService.isNearFootprint(location, radius);
    }

    public boolean isAllowedDuelTeleportDestination(Location location) {
        return arena != null
            && location != null
            && arena.contains(location)
            && arenaTerrainService.isWithinFootprintColumn(location, 6);
    }

    public Location chorusFallbackDestination(Player player) {
        Location preferred = player == null ? null : player.getLocation();
        Location fallback = arenaTerrainService.findPlayableLocation(preferred);
        if (fallback != null) {
            return fallback;
        }
        return player == null ? null : spawnFor(player.getUniqueId());
    }

    public boolean shouldSuppressArenaBlockDrops(Location location) {
        return activeDuel != null
            && arena != null
            && arena.contains(location)
            && !activeDuel.placedBlocks().contains(BlockKey.fromLocation(location));
    }

    public boolean shouldBlockArenaShellPvp(Player victim, Player attacker) {
        if (victim == null || attacker == null || arena == null || !arena.contains(victim.getLocation())) {
            return false;
        }
        if (activeDuel == null) {
            return true;
        }
        return !isInActiveDuel(victim.getUniqueId()) || !isInActiveDuel(attacker.getUniqueId());
    }

    public void allowArenaItemPickup(UUID itemEntityId) {
        if (itemEntityId != null) {
            allowedArenaItemEntityIds.add(itemEntityId);
        }
    }

    public void allowArenaItemSpawnAt(Location location) {
        if (location != null && activeDuel != null && arena != null && arena.contains(location)) {
            allowedArenaItemSpawnLocations.put(BlockKey.fromLocation(location), System.currentTimeMillis() + 2000L);
        }
    }

    public boolean consumeAllowedArenaItemSpawn(Location location) {
        if (location == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        allowedArenaItemSpawnLocations.entrySet().removeIf(entry -> entry.getValue() < now);
        BlockKey key = BlockKey.fromLocation(location);
        Long exact = allowedArenaItemSpawnLocations.remove(key);
        if (exact != null && exact >= now) {
            return true;
        }
        for (int x = location.getBlockX() - 1; x <= location.getBlockX() + 1; x++) {
            for (int y = location.getBlockY() - 1; y <= location.getBlockY() + 1; y++) {
                for (int z = location.getBlockZ() - 1; z <= location.getBlockZ() + 1; z++) {
                    Location nearby = new Location(location.getWorld(), x, y, z);
                    Long expiresAt = allowedArenaItemSpawnLocations.remove(BlockKey.fromLocation(nearby));
                    if (expiresAt != null && expiresAt >= now) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isAllowedArenaItemEntity(UUID itemEntityId) {
        return itemEntityId != null && allowedArenaItemEntityIds.contains(itemEntityId);
    }

    public void forgetArenaItemEntity(UUID itemEntityId) {
        if (itemEntityId != null) {
            allowedArenaItemEntityIds.remove(itemEntityId);
        }
    }

    public void trackExplosionSource(UUID entityId, Material sourceMaterial) {
        if (entityId != null && sourceMaterial != null) {
            trackedExplosionSources.put(entityId, sourceMaterial);
        }
    }

    public Material explosionSourceMaterial(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        return trackedExplosionSources.get(entityId);
    }

    public void clearExplosionSource(UUID entityId) {
        if (entityId != null) {
            trackedExplosionSources.remove(entityId);
        }
    }

    public boolean shouldCancelDamage(Player victim, Player attacker) {
        if (activeDuel == null) {
            return false;
        }
        if (duelCountdownActive && isInActiveDuel(victim.getUniqueId())) {
            return true;
        }
        boolean victimParticipant = isInActiveDuel(victim.getUniqueId());
        if (!victimParticipant) {
            return false;
        }
        if (attacker == null) {
            return false;
        }
        return !isInActiveDuel(attacker.getUniqueId());
    }

    public boolean isDuelCountdownActive() {
        return duelCountdownActive;
    }

    public boolean canUseCombatItem(Material material, Player actor) {
        if (activeDuel == null) {
            return true;
        }
        if (!isInActiveDuel(actor.getUniqueId())) {
            return true;
        }
        if (duelCountdownActive) {
            return false;
        }
        DuelSettings settings = activeDuel.settings();
        if (SpearUtil.isSpear(material)) {
            return settings.isAllowSpears();
        }
        return switch (material) {
            case ENDER_PEARL -> settings.isAllowEnderPearls() && !actor.hasCooldown(Material.ENDER_PEARL);
            case WIND_CHARGE -> settings.isAllowWindCharges() && !actor.hasCooldown(Material.WIND_CHARGE);
            case CHORUS_FRUIT -> settings.isAllowChorusFruit();
            case MACE -> settings.isAllowMaces();
            case ELYTRA -> settings.isAllowElytras();
            case BRUSH -> false;
            default -> true;
        };
    }

    public boolean isCombatItemEnabled(Material material, Player actor) {
        if (activeDuel == null || !isInActiveDuel(actor.getUniqueId())) {
            return true;
        }
        DuelSettings settings = activeDuel.settings();
        if (SpearUtil.isSpear(material)) {
            return settings.isAllowSpears();
        }
        return switch (material) {
            case ENDER_PEARL -> settings.isAllowEnderPearls();
            case WIND_CHARGE -> settings.isAllowWindCharges();
            case CHORUS_FRUIT -> settings.isAllowChorusFruit();
            case MACE -> settings.isAllowMaces();
            case ELYTRA -> settings.isAllowElytras();
            default -> true;
        };
    }

    public int combatCooldownSeconds(Material material, Player actor) {
        if (activeDuel == null || !isInActiveDuel(actor.getUniqueId())) {
            return 0;
        }
        DuelSettings settings = activeDuel.settings();
        if (material == Material.ENDER_PEARL) {
            return settings.getEnderPearlCooldownSeconds();
        }
        if (material == Material.WIND_CHARGE) {
            return settings.getWindChargeCooldownSeconds();
        }
        return 0;
    }

    public void applyCombatCooldown(Material material, Player actor) {
        int seconds = combatCooldownSeconds(material, actor);
        if (seconds > 0) {
            actor.setCooldown(material, seconds * 20);
        }
    }

    public void applyCombatCooldownDeferred(Material material, Player actor) {
        Bukkit.getScheduler().runTask(plugin, () -> applyCombatCooldown(material, actor));
    }

    public Location spawnFor(UUID playerId) {
        if (activeDuel == null || arena == null) {
            return null;
        }
        if (activeDuel.participantOne().playerId().equals(playerId)) {
            return arena.spawn1();
        }
        if (activeDuel.participantTwo().playerId().equals(playerId)) {
            return arena.spawn2();
        }
        return null;
    }

    public void updateArenaLocation(String key, Location location) {
        requirePrimaryThread();
        FileConfiguration config = plugin.getConfig();
        config.set("arena.world", location.getWorld() == null ? "world" : location.getWorld().getName());
        String value = location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
        switch (key) {
            case "setpos1" -> config.set("arena.pos1", value);
            case "setpos2" -> config.set("arena.pos2", value);
            case "setspawn1" -> config.set("arena.spawn1", value);
            case "setspawn2" -> config.set("arena.spawn2", value);
            case "setspectator" -> config.set("arena.spectator", value);
            case "setexit" -> config.set("arena.exit", value);
            default -> {
                return;
            }
        }
        plugin.saveConfig();
        reloadConfig();
    }

    public Collection<String> commandSuggestions() {
        return List.of(
            "accept", "deny", "review", "draw", "surrender", "cancel", "vault", "stats", "info", "settings",
            "reload", "restoreloadout", "mapsave", "mapload", "mapstatus",
            "setpos1", "setpos2", "setspawn1", "setspawn2", "setspectator", "setexit"
        );
    }

    public PlayerDuelStats stats(UUID playerId, String playerName) {
        return statsService.stats(playerId, playerName);
    }

    public void applyMapChoice(DuelSettings settings, DuelMapOption option) {
        arenaMapService.applySelection(settings, option);
    }

    public void sanitizeBuilderSettings(DuelSettings settings) {
        arenaMapService.sanitizeSettings(settings);
    }

    public boolean shouldShowExplosivesMenu(DuelSettings settings) {
        return settings.shouldShowExplosivesConfiguration();
    }

    private void recoverActiveDuelIfNeeded() {
        RuntimeStateStore.PersistedRuntime persistedRuntime = runtimeStateStore.loadActiveDuel();
        runtimeStateStore.clearReloadResumeMarker();
        if (persistedRuntime.activeDuel() == null) {
            runtimeStateStore.clearRuntime();
            return;
        }
        if (!persistedRuntime.resumeAllowed()) {
            refundPersistedWagerIfHeld(persistedRuntime.activeDuel());
            Set<UUID> playerIds = Set.of(
                persistedRuntime.activeDuel().participantOne().playerId(),
                persistedRuntime.activeDuel().participantTwo().playerId()
            );
            recoveryTeleportIds.addAll(playerIds);
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            runtimeStateStore.clearRuntime();
            plugin.getLogger().warning("Found stale duel runtime data without a reload marker. Match was canceled for safety.");
            return;
        }
        activeDuel = persistedRuntime.activeDuel();
        arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
        rebuildParticipantIndex();
        for (UUID playerId : List.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                clearExternalCombatState(player);
                teleportToAssignedSpawn(player);
                sendMessage(player, "messages.duel-resumed");
            }
        }
        startDisconnectMonitor();
        startContainmentMonitor();
    }

    private void handleServerStoppingDisable() {
        clearQueuedDuelStart();
        if (activeDuel == null) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        Set<UUID> participantIds = Set.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId());
        recoveryTeleportIds.addAll(participantIds);
        runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
        refundWagerIfHeld();
        duelAnalyticsService.recordDuel(finishedDuel, null, DuelEndReason.SERVER_RESTART);
        for (UUID playerId : participantIds) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                teleportToExit(player);
            }
        }
        activeDuel = null;
        rebuildParticipantIndex();
        cleanupVolatileArenaState();
    }

    private void startDuel(Player requester, Player target, DuelSettings settings) {
        if (arenaTerrainService.isBusy()) {
            queueDuelStart(requester, target, settings);
            return;
        }
        if (!arenaTerrainService.isReady()) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "Arena terrain footprint is not loaded.");
            sendMessageRaw(target, prefix + ChatColor.RED + "Arena terrain footprint is not loaded.");
            return;
        }
        DuelSettings preparedSettings = settings.copy();
        arenaMapService.sanitizeSettings(preparedSettings);
        DuelMapOption selectedMap = arenaMapService.resolve(preparedSettings.getMapId());
        if (!arenaTerrainService.hasSnapshot(selectedMap.id())) {
            sendMessageRaw(requester, prefix + ChatColor.RED + "The selected arena map is not saved yet: " + selectedMap.displayName() + ".");
            sendMessageRaw(target, prefix + ChatColor.RED + "The selected arena map is not saved yet: " + selectedMap.displayName() + ".");
            return;
        }

        MatchParticipant one = new MatchParticipant(requester.getUniqueId(), requester.getName());
        MatchParticipant two = new MatchParticipant(target.getUniqueId(), target.getName());
        ActiveDuel stagedDuel = new ActiveDuel(one, two, preparedSettings, System.currentTimeMillis());
        loadoutArchiveStore.saveLatestPreDuel(requester, loadoutArchiveStore.capture(requester));
        loadoutArchiveStore.saveLatestPreDuel(target, loadoutArchiveStore.capture(target));

        if (preparedSettings.getWager() > 0D && !holdWager(stagedDuel, requester, target, preparedSettings.getWager())) {
            sendMessage(requester, "messages.cannot-afford", "{player}", target.getName());
            sendMessage(target, "messages.cannot-afford", "{player}", requester.getName());
            return;
        }

        preparingDuel = stagedDuel;
        sendMessageRaw(requester, ChatColor.YELLOW + "Preparing arena terrain...");
        sendMessageRaw(target, ChatColor.YELLOW + "Preparing arena terrain...");
        arenaTerrainService.loadSnapshot(selectedMap.id(), () -> {
            if (preparingDuel != stagedDuel) {
                refundWagerIfHeld(stagedDuel);
                return;
            }
            preparingDuel = null;
            activeDuel = stagedDuel;
            queuedDuelStart = null;
            arenaMapService.prepareArenaForMatch(arena, activeDuel.settings());
            clearExternalCombatState(requester);
            clearExternalCombatState(target);
            prepareCombatant(requester);
            prepareCombatant(target);
            teleportToAssignedSpawn(requester);
            teleportToAssignedSpawn(target);
            rebuildParticipantIndex();
            startContainmentMonitor();
            sendMessageRaw(requester, ChatColor.RED + "Disconnecting gives you " + disconnectGraceSeconds + " seconds to rejoin before you lose.");
            sendMessageRaw(target, ChatColor.RED + "Disconnecting gives you " + disconnectGraceSeconds + " seconds to rejoin before you lose.");
            String wagerText = preparedSettings.getWager() > 0D ? " for $" + formatAmount(preparedSettings.getWager()) : "";
            broadcast("messages.duel-start", "{p1}", requester.getName(), "{p2}", target.getName(), "{wager}", wagerText);
            runtimeStateStore.queueActiveDuelSave(activeDuel, 1L);
            startCountdown(requester, target);
        }, message -> {
            if (preparingDuel == stagedDuel) {
                preparingDuel = null;
            }
            refundWagerIfHeld(stagedDuel);
            sendMessageRaw(requester, ChatColor.RED + message);
            sendMessageRaw(target, ChatColor.RED + message);
            arenaTerrainService.ensureDefaultSnapshotLoaded(logMessage -> plugin.getLogger().warning(logMessage));
        });
    }

    private void concludeDuel(Player winner, DuelEndReason reason, boolean broadcastOutcome) {
        requirePrimaryThread();
        if (activeDuel == null) {
            return;
        }
        ActiveDuel finishedDuel = activeDuel;
        UUID winnerId = winner == null ? null : winner.getUniqueId();
        cancelCountdownTask();
        cancelDisconnectMonitorTask();
        cancelContainmentTask();

        if (winner != null) {
            payoutWager(winner);
            if (broadcastOutcome) {
                String wagerText = finishedDuel.settings().getWager() > 0D ? " for $" + formatAmount(finishedDuel.settings().getWager()) : "";
                broadcast("messages.duel-end", "{winner}", winner.getName(), "{wager}", wagerText);
            }
        } else {
            refundWagerIfHeld();
            if (broadcastOutcome) {
                broadcast("messages.duel-draw");
            }
        }

        UUID participantOne = finishedDuel.participantOne().playerId();
        UUID participantTwo = finishedDuel.participantTwo().playerId();

        if (winner != null) {
            healAfterDuel(winner);
            teleportToExit(winner);
        }
        Player first = Bukkit.getPlayer(participantOne);
        Player second = Bukkit.getPlayer(participantTwo);
        if (first != null && first.isOnline() && !respawnToSpawn.contains(participantOne)) {
            teleportToExit(first);
        }
        if (second != null && second.isOnline() && !respawnToSpawn.contains(participantTwo)) {
            teleportToExit(second);
        }

        activeDuel = null;
        runtimeStateStore.clearRuntime();
        rebuildParticipantIndex();
        duelAnalyticsService.recordDuel(finishedDuel, winnerId, reason);
        statsService.recordMatchResult(finishedDuel, winnerId, reason);
        cleanupArenaAfterMatch(finishedDuel, true);
    }

    private void cleanupArenaAfterMatch(ActiveDuel duel, boolean restoreDefaultTerrain) {
        if (duel == null || arena == null) {
            return;
        }
        if (duel.arenaSnapshot() != null) {
            arenaResetService.restore(arena, duel.arenaSnapshot());
        }
        if (clearPlacedBlocksWhenNoBreak || !duel.placedBlocks().isEmpty()) {
            arenaResetService.clearTrackedPlacedBlocks(arena, duel.placedBlocks());
        }
        if (allowWaterDrain) {
            arenaResetService.clearFluids(arena, arenaTerrainService.footprint());
        }
        arenaResetService.clearNonPlayerEntities(arena);
        if (restoreDefaultTerrain) {
            String defaultMapId = arenaMapService.defaultMapId();
            if (arenaTerrainService.hasSnapshot(defaultMapId)) {
                arenaTerrainService.loadSnapshot(defaultMapId, () -> {
                }, message -> plugin.getLogger().warning(message));
            } else {
                arenaMapService.restoreDefaultArena(arena);
                plugin.getLogger().warning("Default arena terrain snapshot '" + defaultMapId + "' does not exist yet.");
            }
        }
        disconnectSnapshots.clear();
        cleanupVolatileArenaState();
    }

    private void cleanupVolatileArenaState() {
        allowedArenaItemEntityIds.clear();
        allowedArenaItemSpawnLocations.clear();
        trackedExplosionSources.clear();
        blockedItemMessageCooldowns.clear();
        arenaExitMessageCooldowns.clear();
        pendingForcedDeathIds.clear();
        duelCountdownActive = false;
    }

    private void prepareCombatant(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.setFreezeTicks(0);
        for (var effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFallDistance(0F);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void healAfterDuel(Player player) {
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        player.setFireTicks(0);
        player.setArrowsInBody(0);
        player.setFreezeTicks(0);
    }

    private void scheduleRequestExpiry() {
        cancelRequestExpiryTask();
        requestExpiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequest == null) {
                return;
            }
            Player requester = Bukkit.getPlayer(pendingRequest.requesterId());
            String targetName = pendingRequest.targetName();
            clearPendingRequest();
            if (requester != null) {
                sendMessage(requester, "messages.request-expired", "{player}", targetName);
            }
        }, requestExpireSeconds * 20L);
    }

    private void startDisconnectMonitor() {
        if (activeDuel == null) {
            return;
        }
        if (!hasDisconnectingParticipant()) {
            cancelDisconnectMonitorTask();
            return;
        }
        if (disconnectMonitorTask != null) {
            return;
        }
        disconnectMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelDisconnectMonitorTask();
                return;
            }
            MatchParticipant expired = findExpiredDisconnectParticipant();
            if (expired == null) {
                if (!hasDisconnectingParticipant()) {
                    cancelDisconnectMonitorTask();
                }
                return;
            }
            MatchParticipant winner = activeDuel.other(expired.playerId());
            Player winnerPlayer = winner == null ? null : Bukkit.getPlayer(winner.playerId());
            recoveryTeleportIds.add(expired.playerId());
            runtimeStateStore.saveRecoveryTeleportIds(recoveryTeleportIds);
            LoadoutSnapshot snapshot = disconnectSnapshots.get(expired.playerId());
            if (winner != null && snapshot != null) {
                spoilsService.createSpoilsFromSnapshot(winner.playerId(), winner.name(), expired.playerId(), expired.name(), snapshot);
            }
            spoilsService.markForcedDeathOnJoin(expired.playerId());
            if (winnerPlayer != null) {
                sendToParticipants("messages.disconnect-loss", "{player}", expired.name());
            }
            concludeDuel(winnerPlayer, DuelEndReason.DISCONNECT_TIMEOUT, true);
        }, 20L, 20L);
    }

    private MatchParticipant findExpiredDisconnectParticipant() {
        if (activeDuel == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (MatchParticipant participant : List.of(activeDuel.participantOne(), activeDuel.participantTwo())) {
            Long deadline = participant.disconnectDeadlineEpochMs();
            if (deadline != null && now >= deadline) {
                return participant;
            }
        }
        return null;
    }

    private boolean hasDisconnectingParticipant() {
        if (activeDuel == null) {
            return false;
        }
        return activeDuel.participantOne().disconnectDeadlineEpochMs() != null
            || activeDuel.participantTwo().disconnectDeadlineEpochMs() != null;
    }

    private void cancelRequestExpiryTask() {
        if (requestExpiryTask != null) {
            requestExpiryTask.cancel();
            requestExpiryTask = null;
        }
    }

    private void cancelDisconnectMonitorTask() {
        if (disconnectMonitorTask != null) {
            disconnectMonitorTask.cancel();
            disconnectMonitorTask = null;
        }
    }

    private void cancelCountdownTask() {
        duelCountdownActive = false;
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void startContainmentMonitor() {
        if (activeDuel == null || containmentTask != null) {
            return;
        }
        containmentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelContainmentTask();
                return;
            }
            enforceParticipantContainment(activeDuel.participantOne().playerId());
            enforceParticipantContainment(activeDuel.participantTwo().playerId());
        }, 5L, 5L);
    }

    private void enforceParticipantContainment(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (!isParticipantInsideAllowedArena(player.getLocation())) {
            handleArenaExitAttempt(player);
        }
    }

    private boolean isParticipantInsideAllowedArena(Location location) {
        return arena != null
            && location != null
            && arena.contains(location)
            && arenaTerrainService.isWithinFootprintColumn(location, 6);
    }

    private void cancelContainmentTask() {
        if (containmentTask != null) {
            containmentTask.cancel();
            containmentTask = null;
        }
    }

    private void clearPendingRequest() {
        pendingRequest = null;
        cancelRequestExpiryTask();
    }

    private boolean holdWager(ActiveDuel duel, Player one, Player two, double amount) {
        if (!economyPort.withdraw(one, amount)) {
            return false;
        }
        if (!economyPort.withdraw(two, amount)) {
            economyPort.deposit(one, amount);
            return false;
        }
        duel.setWagerHeld(true);
        duel.setWagerPot(amount * 2D);
        return true;
    }

    private void payoutWager(Player winner) {
        if (activeDuel == null || !activeDuel.isWagerHeld()) {
            return;
        }
        if (activeDuel.getWagerPot() > 0D) {
            economyPort.deposit(winner, activeDuel.getWagerPot());
        }
    }

    private void refundWagerIfHeld() {
        if (activeDuel == null || !activeDuel.isWagerHeld()) {
            return;
        }
        double each = activeDuel.settings().getWager();
        economyPort.deposit(activeDuel.participantOne().playerId(), each);
        economyPort.deposit(activeDuel.participantTwo().playerId(), each);
    }

    private void refundPersistedWagerIfHeld(ActiveDuel duel) {
        if (duel == null || !duel.isWagerHeld()) {
            return;
        }
        double each = duel.settings().getWager();
        economyPort.deposit(duel.participantOne().playerId(), each);
        economyPort.deposit(duel.participantTwo().playerId(), each);
    }

    private void refundWagerIfHeld(ActiveDuel duel) {
        refundPersistedWagerIfHeld(duel);
    }

    private void rebuildParticipantIndex() {
        activeParticipantIndex.clear();
        if (activeDuel != null) {
            activeParticipantIndex.add(activeDuel.participantOne().playerId());
            activeParticipantIndex.add(activeDuel.participantTwo().playerId());
        }
    }

    private ArenaDefinition loadArena(FileConfiguration config) {
        String worldName = config.getString("arena.world", "world");
        Location pos1 = parseLocation(worldName, config.getString("arena.pos1", "0,64,0"));
        Location pos2 = parseLocation(worldName, config.getString("arena.pos2", "10,70,10"));
        Location spawn1 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn1", "2,65,2")), 180.0F);
        Location spawn2 = exactSpawn(parseLocation(worldName, config.getString("arena.spawn2", "8,65,8")), 0.0F);
        Location spectator = parseLocation(worldName, config.getString("arena.spectator", "5,75,5"));
        Location exit = parseLocation(worldName, config.getString("arena.exit", "0,80,0"));
        return new ArenaDefinition(worldName, pos1, pos2, spawn1, spawn2, spectator, exit);
    }

    private Location exactSpawn(Location base, float yaw) {
        if (base == null || base.getWorld() == null) {
            return base;
        }
        return new Location(base.getWorld(), base.getX(), base.getY(), base.getZ(), yaw, 0.0F);
    }

    private Location parseLocation(String worldName, String raw) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return new Location(null, 0, 0, 0);
        }
        String[] parts = raw.split(",");
        if (parts.length < 3) {
            return new Location(world, 0, 0, 0);
        }
        try {
            return new Location(
                world,
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim())
            );
        } catch (NumberFormatException ignored) {
            return new Location(world, 0, 0, 0);
        }
    }

    private void sendRequestDetails(Player target, DuelRequest request) {
        sendMessage(target, "messages.request-received", "{player}", request.requesterName());
        target.sendMessage(
            Component.text("[Review Request] ", NamedTextColor.GOLD)
                .clickEvent(ClickEvent.runCommand("/duel review"))
                .append(Component.text("[Open Accept Menu] ", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/duel accept")))
                .append(Component.text("[Deny]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/duel deny")))
        );
        sendMessageRaw(target, ChatColor.YELLOW + "Use /duel accept to review the settings and accept or deny the request.");
    }

    public void sendMessage(Player player, String path, String... replacements) {
        if (player == null) {
            return;
        }
        String message = plugin.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sendMessageRaw(player, color(message));
    }

    public void sendMessageRaw(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public void sendMessageRaw(Player player, String message) {
        player.sendMessage(prefix + message);
    }

    public void sendBlockedCombatItemMessage(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastSent = blockedItemMessageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < 750L) {
            return;
        }
        blockedItemMessageCooldowns.put(player.getUniqueId(), now);
        sendMessage(player, "messages.combat-item-blocked");
    }

    private void sendArenaExitBlockedMessage(Player player) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastSent = arenaExitMessageCooldowns.get(player.getUniqueId());
        if (lastSent != null && now - lastSent < 1500L) {
            return;
        }
        arenaExitMessageCooldowns.put(player.getUniqueId(), now);
        sendMessage(player, "messages.arena-exit-blocked");
    }

    private void sendToParticipants(String path, String... replacements) {
        if (activeDuel == null) {
            return;
        }
        Player one = Bukkit.getPlayer(activeDuel.participantOne().playerId());
        Player two = Bukkit.getPlayer(activeDuel.participantTwo().playerId());
        if (one != null) {
            sendMessage(one, path, replacements);
        }
        if (two != null) {
            sendMessage(two, path, replacements);
        }
    }

    private void broadcast(String path, String... replacements) {
        String message = plugin.getConfig().getString(path, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        Bukkit.broadcastMessage(prefix + color(message));
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public void teleportSafe(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        allowTeleportOnce(player.getUniqueId());
        player.teleport(location);
    }

    private void teleportToAssignedSpawn(Player player) {
        Location spawn = spawnFor(player.getUniqueId());
        if (spawn != null) {
            teleportSafe(player, spawn);
        }
    }

    private void teleportToExit(Player player) {
        teleportSafe(player, exitLocation());
    }

    private Location exitLocation() {
        return spawnPort.resolveSpawnFallback(arena == null ? null : arena.exit());
    }

    private boolean sameIp(Player a, Player b) {
        if (a.getAddress() == null || b.getAddress() == null) {
            return false;
        }
        InetAddress aAddress = a.getAddress().getAddress();
        InetAddress bAddress = b.getAddress().getAddress();
        if (aAddress == null || bAddress == null) {
            return false;
        }
        return aAddress.getHostAddress().equalsIgnoreCase(bAddress.getHostAddress());
    }

    private boolean isCobwebUtility(Material material) {
        return material == Material.COBWEB
            || material == Material.WATER_BUCKET
            || material.name().endsWith("_BUTTON")
            || material.name().endsWith("_PRESSURE_PLATE");
    }

    private boolean isRestrictedExplosiveMaterial(Material material) {
        return material == Material.END_CRYSTAL
            || material == Material.RESPAWN_ANCHOR
            || material == Material.TNT_MINECART
            || material == Material.TNT;
    }

    private void sendTerrainBusyMessage(CommandSender sender) {
        ArenaMapOperationStatus status = arenaTerrainService.status();
        if (status.busy()) {
            sendMessageRaw(
                sender,
                prefix + ChatColor.RED + "Arena terrain is busy with " + status.type() + " " + status.mapId()
                    + " (" + status.processedBlocks() + "/" + status.totalBlocks() + ")."
            );
            return;
        }
        sendMessageRaw(sender, prefix + ChatColor.RED + "Arena terrain is busy.");
    }

    private void queueDuelStart(Player requester, Player target, DuelSettings settings) {
        queuedDuelStart = new QueuedDuelStart(
            requester.getUniqueId(),
            target.getUniqueId(),
            requester.getName(),
            target.getName(),
            settings.copy()
        );
        sendMessageRaw(requester, prefix + ChatColor.YELLOW + "Arena is busy. Your duel is queued and will start when the arena is ready.");
        sendMessageRaw(target, prefix + ChatColor.YELLOW + "Arena is busy. Your duel is queued and will start when the arena is ready.");
        ensureQueuedStartTask();
    }

    private void ensureQueuedStartTask() {
        if (queuedStartTask != null) {
            return;
        }
        queuedStartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queuedDuelStart == null) {
                cancelQueuedStartTask();
                return;
            }
            if (activeDuel != null || pendingRequest != null || arenaTerrainService.isBusy()) {
                return;
            }
            Player requester = Bukkit.getPlayer(queuedDuelStart.requesterId());
            Player target = Bukkit.getPlayer(queuedDuelStart.targetId());
            if (requester == null || !requester.isOnline() || target == null || !target.isOnline()) {
                if (requester != null) {
                    sendMessage(requester, "messages.target-offline");
                }
                if (target != null) {
                    sendMessage(target, "messages.target-offline");
                }
                clearQueuedDuelStart();
                return;
            }
            if (!isInsideMatchmakingSpawn(requester.getLocation()) || !isInsideMatchmakingSpawn(target.getLocation())) {
                sendMessage(requester, "messages.must-be-at-spawn");
                sendMessage(target, "messages.must-be-at-spawn");
                clearQueuedDuelStart();
                return;
            }
            if (combatTagPort != null && (combatTagPort.isInCombat(requester) || combatTagPort.isInCombat(target))) {
                if (combatTagPort.isInCombat(requester)) {
                    sendMessage(requester, "messages.player-in-combat");
                    sendMessage(target, "messages.target-in-combat", "{player}", requester.getName());
                } else {
                    sendMessage(target, "messages.player-in-combat");
                    sendMessage(requester, "messages.target-in-combat", "{player}", target.getName());
                }
                clearQueuedDuelStart();
                return;
            }
            DuelSettings settings = queuedDuelStart.settings().copy();
            clearQueuedDuelStart();
            startDuel(requester, target, settings);
        }, 20L, 20L);
    }

    private void clearQueuedDuelStart() {
        queuedDuelStart = null;
        cancelQueuedStartTask();
    }

    private void cancelQueuedStartTask() {
        if (queuedStartTask != null) {
            queuedStartTask.cancel();
            queuedStartTask = null;
        }
    }

    private boolean isInsideMatchmakingSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(matchmakingWorld)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= matchmakingMinX && x <= matchmakingMaxX
            && y >= matchmakingMinY && y <= matchmakingMaxY
            && z >= matchmakingMinZ && z <= matchmakingMaxZ;
    }

    private void clearExternalCombatState(Player player) {
        if (combatTagPort != null) {
            combatTagPort.clearCombatState(player);
        }
    }

    private String formatAmount(double amount) {
        if (amount % 1D == 0D) {
            return String.valueOf((long) amount);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private void startCountdown(Player requester, Player target) {
        cancelCountdownTask();
        if (startCountdownSeconds <= 0) {
            duelCountdownActive = false;
            return;
        }
        duelCountdownActive = true;
        applyCountdownLock(requester);
        applyCountdownLock(target);
        final int[] secondsLeft = {startCountdownSeconds};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeDuel == null) {
                cancelCountdownTask();
                return;
            }
            if (secondsLeft[0] <= 0) {
                duelCountdownActive = false;
                clearCountdownLock(requester);
                clearCountdownLock(target);
                playCountdownSound(requester, true);
                playCountdownSound(target, true);
                showCountdownTitle(requester, "&aFight!", "&7You are released.");
                showCountdownTitle(target, "&aFight!", "&7You are released.");
                sendToParticipants("messages.duel-released");
                cancelCountdownTask();
                return;
            }
            playCountdownSound(requester, false);
            playCountdownSound(target, false);
            showCountdownTitle(requester, "&6" + secondsLeft[0], "&7Prepare to fight");
            showCountdownTitle(target, "&6" + secondsLeft[0], "&7Prepare to fight");
            sendToParticipants("messages.duel-countdown", "{seconds}", String.valueOf(secondsLeft[0]));
            secondsLeft[0]--;
        }, 0L, 20L);
    }

    private void applyCountdownLock(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (startCountdownSeconds + 2) * 20, 10, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (startCountdownSeconds + 2) * 20, 250, false, false, false));
    }

    private void clearCountdownLock(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    private void showCountdownTitle(Player player, String title, String subtitle) {
        player.sendTitle(color(title), color(subtitle), 0, 15, 5);
    }

    private void playCountdownSound(Player player, boolean release) {
        player.playSound(
            player.getLocation(),
            release ? Sound.ENTITY_PLAYER_LEVELUP : Sound.BLOCK_NOTE_BLOCK_PLING,
            1.0F,
            release ? 1.0F : 1.5F
        );
    }

    private void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("DuelService state mutations must run on the primary thread.");
        }
    }

    private record QueuedDuelStart(
        UUID requesterId,
        UUID targetId,
        String requesterName,
        String targetName,
        DuelSettings settings
    ) {
        private boolean involves(UUID playerId) {
            return requesterId.equals(playerId) || targetId.equals(playerId);
        }
    }

    public boolean consumePendingForcedDeath(UUID playerId) {
        return pendingForcedDeathIds.remove(playerId);
    }
}
