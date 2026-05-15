# WarzoneDuels Staging Testing Checklist

This checklist is for staging validation before the plugin is moved to the live server.

Target environment:
- Leaf / Paper 1.21.11
- Java 21
- WarzoneDuels built from this repository
- Test with Vault/economy, CombatLogX, WorldGuard, MaceGuard, EnthusiaTeleport, and Plan both present and absent where noted

Before testing:
- Back up the server.
- Confirm the deployed jar is the freshly built `target/WarzoneDuels-1.0-SNAPSHOT.jar`.
- Confirm `plugins/WarzoneDuels/config.yml` has the correct arena cuboid, spawn locations, terrain footprint, map snapshots, and economy settings.
- Confirm the arena default snapshot exists and the three expected maps exist in the WarzoneDuels data folder.
- Confirm WorldGuard/MaceGuard/CombatX configs match the current staging plan.
- Use two real player accounts for duel tests where possible.
- Keep console open and watch for warnings/errors during every section.

## 1. Startup And Basic Load

- Start the server with WarzoneDuels installed.
- Confirm WarzoneDuels enables without stack traces.
- Confirm missing optional dependencies do not hard-fail the plugin if intentionally removed for a test pass.
- Confirm the default arena terrain is restored when no active duel is present.
- Run `/duel mapstatus` as an admin.
- Expected: status is idle, current map is sensible, no terrain operation stuck active.
- Confirm no `arena-terrain-dirty.marker` remains after a successful default restore.

## 2. Commands And Permissions

- As a normal player, run `/duel reload`.
- Expected: permission denied.
- As an admin, run `/duel reload`.
- Expected: config reloads unless terrain is busy.
- As a normal player, run `/duel mapload forest`.
- Expected: permission denied.
- As an admin, run `/duel mapload forest`, `/duel mapload desert`, and the default map id.
- Expected: maps load successfully and `/duel mapstatus` returns to idle after each load.
- Run `/vault`, `/duel vault`, `/stats`, `/stats <player>`, `/duel stats <player>`.
- Expected: GUIs open or clear no-data messages appear without console errors.

## 3. Duel Request Flow

- Put both test players inside the configured matchmaking spawn cuboid.
- Run `/duel <player>`.
- Choose a map, block rules, combat item rules, explosives, and wager.
- Send the duel request.
- Expected: requester sees sent message; target receives a clean request message.
- On target, run `/duel accept`.
- Expected: request review GUI opens before accepting.
- Click deny.
- Expected: requester receives deny message and no duel starts.
- Repeat request and accept through the GUI.
- Expected: arena prepares, players teleport to arena spawns, countdown starts, and players are released after countdown.
- Try self-duel.
- Expected: blocked.
- Try accepting an expired request.
- Expected: blocked with no pending request message.
- Try sending a request while a pending request exists.
- Expected: blocked.
- Try sending a request while the arena is busy loading.
- Expected: duel queues or is blocked according to current behavior, with no duplicate active duel.

## 4. CombatLogX Entry Blocking

- Put a player in CombatLogX combat.
- Try sending a duel request.
- Expected: blocked.
- Try accepting a duel request while in CombatLogX combat.
- Expected: blocked and request cleared safely.
- Try walking into the arena shell while CombatLogX-tagged and not in a duel.
- Expected: entry blocked by WarzoneDuels/WorldGuard setup.
- Start a duel and confirm both participants have their external combat state cleared.

## 5. Wagers And Economy Safety

- Test a duel with no wager.
- Expected: starts normally, no economy transactions.
- Test a duel with a valid wager.
- Expected: both players are charged when the duel actually starts; winner receives the full pot.
- Test with requester lacking funds.
- Expected: request/send/start is blocked before withdrawal.
- Test with target lacking funds.
- Expected: accept/start is blocked before withdrawal.
- Start a wagered duel and end by draw/cancel.
- Expected: both players are refunded.
- Start a wagered duel, then reload WarzoneDuels during arena preparation.
- Expected: wager is refunded, no active duel is left behind, no duplicate queued duel.
- Start a wagered duel, then full-stop the server during active duel.
- Expected: no winner is paid; both players are refunded.

## 6. Countdown And Lockdown

- Accept a duel and observe the 5-second countdown.
- Expected: title/action feedback appears and sounds play.
- During countdown, try moving, jumping, placing blocks, breaking blocks, throwing pearls, throwing wind charges, using chorus, buckets, and attacking.
- Expected: movement and combat actions are blocked until release.
- After release, confirm normal allowed combat behavior resumes.

## 7. Command And Chat Lockdown

- During an active duel, try normal chat.
- Expected: chat is blocked.
- Try `/spawn`, `/home`, `/tpa`, `/warp`, and other server commands.
- Expected: blocked.
- Try `/draw`, `/surrender`, `/duel cancel`, `/duel draw`, `/duel surrender`, `/duel info`, and `/duel settings`.
- Expected: allowed commands work or open the intended GUI.
- Confirm no command allows teleport escape.

## 8. Teleport And Escape Prevention

- Try walking out of the fighting footprint.
- Expected: participant is teleported back to their assigned arena spawn.
- Try pearls with pearls disabled.
- Expected: launch/use blocked with chat message.
- Try pearls with pearls enabled and no cooldown.
- Expected: pearl works only inside allowed arena destination.
- Try pearls with cooldown enabled.
- Expected: vanilla cooldown overlay appears and blocks repeated use until cooldown ends.
- Try a pearl stasis chamber outside the arena during duel.
- Expected: teleport out is blocked.
- Try chorus fruit with chorus disabled.
- Expected: blocked with chat message.
- Try chorus fruit with chorus enabled.
- Expected: vanilla chorus behavior happens only if the target remains inside/near the arena footprint; escape attempts are redirected or blocked safely.
- Try wind charges with wind charges disabled.
- Expected: blocked with chat message.
- Try wind charges with cooldown enabled.
- Expected: wind charges launch and vanilla cooldown overlay appears.

## 9. Combat Item Rules

- Test mace disabled.
- Expected: mace damage/use is blocked in duel.
- Test mace enabled.
- Expected: mace works normally.
- Test spear disabled using the 1.21.11 spear items, not tridents.
- Expected: spear melee damage is blocked.
- Test spear enabled.
- Expected: spear melee damage works normally.
- Test elytra disabled.
- Expected: gliding is prevented immediately and repeated space spam does not allow meaningful glide.
- Test elytra enabled.
- Expected: WarzoneDuels does not block elytra; verify CombatX does not override unexpectedly.
- Test brush on suspicious sand/gravel in the arena.
- Expected: brush use is blocked in duel.

## 10. Block Place And Break Rules

- No-build/no-break mode.
- Expected: participants cannot place or break arena blocks.
- Limited placement utilities mode.
- Expected: only configured utility placement works; original map blocks cannot be broken.
- All placement mode.
- Expected: allowed placement works; placed blocks can be broken; original map blocks cannot be broken unless full place/break mode allows it.
- Place/break mode on terrain maps.
- Expected: original terrain blocks inside the scanned footprint can be broken only when map/rules allow.
- Try placing blocks inside the arena cuboid but outside the scanned footprint.
- Expected: blocked.
- Try breaking blocks inside the arena cuboid but outside the scanned footprint.
- Expected: blocked.
- Try trapdoors, doors, buttons, pressure plates, anvils, containers, and interactable blocks in the arena shell.
- Expected: shell/protected area interaction is blocked unless intentionally allowed by duel rules.

## 11. Drops, Spoils, And Item Safety

- Kill a player in an active duel.
- Expected: loser drops do not fall on the ground; winner receives a duel vault entry.
- Open `/vault`.
- Claim one item.
- Expected: one item transfers to inventory, vault updates, no duplication.
- Claim all with enough inventory space.
- Expected: all items transfer and vault entry is removed.
- Claim all with a nearly full inventory.
- Expected: only items that fit are claimed; remaining items stay in vault.
- Delete remaining vault items.
- Expected: vault entry is removed permanently.
- Break original map blocks during allowed modes.
- Expected: original map blocks do not drop items.
- Break player-placed blocks or TNT minecarts placed during the duel.
- Expected: player-placed items behave naturally unless another rule intentionally blocks drops.
- Drop normal inventory items during duel.
- Expected: dropped items remain natural and can be picked up normally.
- Check console and inventories for duplication or item deletion after death, claim, reload, and restart tests.

## 12. Disconnect And Combat Logging

- During an active duel, have one player disconnect and reconnect before the grace timeout.
- Expected: player is returned to their arena spawn, duel continues, no inventory loss.
- During an active duel, have one player disconnect and stay offline past timeout.
- Expected: offline player forfeits, winner receives spoils from the captured disconnect snapshot, wager pays to winner.
- Have the loser reconnect after timeout.
- Expected: pending forced death triggers, inventory is cleared, death is counted, and no duplicate drops occur.
- Disconnect both players during a duel.
- Expected: no duplicate conclusions, no duplicate payouts, no stuck active duel.

## 13. Reload Behavior

- Reload WarzoneDuels with no duel active.
- Expected: clean reload, no duplicate tasks, no stuck runtime files.
- Reload with a pending request.
- Expected: pending request is safely cleared or expires; no active duel starts unexpectedly.
- Reload during arena preparation after a wagered accept.
- Expected: wager refunded, no active duel is stranded.
- Reload during active duel.
- Expected: active duel resumes, players are returned to arena spawns, commands/chat/item rules still apply.
- Reload during default terrain restore.
- Expected: reload is blocked while terrain is busy or dirty marker recovery handles it on next startup.

## 14. Full Server Restart Behavior

- Start an active no-wager duel and stop the server.
- Start the server again.
- Expected: duel is canceled, both players are sent to spawn/recovery location, no winner is recorded.
- Start an active wagered duel and stop the server.
- Start the server again.
- Expected: duel is canceled and wager is refunded.
- Stop the server during terrain restore.
- Start the server again.
- Expected: dirty marker causes default terrain restore on startup, then dirty marker clears.
- Confirm runtime files do not leave stale active duel state after restart.

## 15. Arena Terrain And Reset

- Load each saved map manually with `/duel mapload <map>`.
- Expected: no TPS drop, map finishes, support-sensitive blocks are intact, gravity blocks do not fall incorrectly.
- Start and finish a duel on each map.
- Expected: after match cleanup, default terrain restores.
- During a duel, place blocks, water, TNT, crystals, anchors, and minecarts according to rules.
- End the duel.
- Expected: placed blocks/liquids/entities are removed or reset according to rules.
- Verify no dropped items, minecarts, crystals, TNT entities, arrows, or other non-player entities remain in the arena.
- Verify no arena shell blocks were modified.

## 16. Explosions And External Protection Plugins

- With WorldGuard/MaceGuard/CombatX enabled, test end crystals in allowed duel rules.
- Expected: crystals place, explode, damage players, and damage only allowed arena footprint blocks.
- Test crystals disabled.
- Expected: place/use/damage is blocked with message.
- Test respawn anchors in allowed duel rules.
- Expected: anchors explode with normal damage/knockback where vanilla allows, and block damage is constrained to arena footprint.
- Test anchors disabled.
- Expected: use is blocked with message.
- Test TNT.
- Expected: normal behavior when allowed; block damage constrained to footprint.
- Test TNT minecarts.
- Expected: normal explosion behavior when allowed; block damage constrained to footprint.
- Test explosions outside the arena and near the arena shell.
- Expected: outside/non-duel explosions do not damage protected arena blocks.
- Check console for CombatX, WorldGuard, MaceGuard, or WarzoneDuels errors.

## 17. Liquids, Fire, And Environmental Changes

- Place water during an allowed duel mode.
- Expected: water flows inside the allowed arena area but does not escape/protectively modify the shell.
- Place lava if rules allow or if another plugin allows it.
- Expected: flow is bounded; arena shell is not damaged.
- Try fire spread, lava ignition, lightning ignition, block burning, grass/vine/spread, and block fading inside the arena.
- Expected: environmental spread/destruction is blocked where WarzoneDuels protects the arena.
- End the duel.
- Expected: leftover liquids are cleared from the footprint during cleanup.

## 18. Plan Integration

- Start server with Plan installed and enabled in config.
- Expected: WarzoneDuels data extension/page registers without errors.
- Open the Plan WarzoneDuels page.
- Expected: total duels, recent duels, player stats, maps, and end reasons load.
- Search a player with duel history.
- Expected: player-specific values and recent duel history load.
- Start server without Plan installed.
- Expected: WarzoneDuels logs that Plan is unavailable or silently skips; plugin still enables.
- Disable Plan integration in config and reload.
- Expected: WarzoneDuels does not register Plan integration.

## 19. Stats And Analytics

- Complete a normal kill duel.
- Expected: winner wins +1, loser losses +1, analytics record has end reason kill.
- Complete a draw/cancel duel.
- Expected: draw stats update according to current intended behavior.
- Complete a disconnect timeout duel.
- Expected: winner gets win, loser gets loss and disconnect-forfeit loss.
- Open `/stats` and leaderboard GUI.
- Expected: players with no duel stats do not appear above players with wins.
- Restart server and recheck stats.
- Expected: stats persist.

## 20. Loadout Archive And Admin Restore

- Start a duel after changing both players' inventory.
- Expected: pre-duel loadout archives are saved.
- Run `/duel restoreloadout <player>` as admin after a test duel.
- Expected: target receives archived pre-duel loadout.
- Try restore for a player with no archive.
- Expected: clear no-archive message.
- Restart server and try restore again.
- Expected: archived loadout persists.

## 21. Performance And TPS

- Watch TPS/MSPT during map load, duel start, active duel, explosions, and cleanup.
- Expected: no watchdog stalls and no long main-thread freezes.
- Stress test repeated block placement in allowed modes.
- Expected: no YAML save freeze and no severe TPS impact.
- Stress test repeated water placement and flow.
- Expected: no runaway entity/block cleanup or flow outside arena.
- Stress test explosions with crystals/TNT/minecarts.
- Expected: block filtering does not create major TPS spikes.
- End several duels in a row.
- Expected: no accumulating tasks, no increasing cleanup time, no persistent non-player entities.

## 22. Final Staging Acceptance

Pass criteria:
- No console stack traces from WarzoneDuels during normal test cases.
- No watchdog warnings caused by WarzoneDuels.
- No item duplication.
- No unintended item loss except intentional spoils expiration/deletion.
- No stuck active duel after reload/restart.
- No stuck players in arena or combat lockdown after duel end.
- Arena returns to default terrain when idle.
- Wagers refund/pay exactly once.
- Spoils, stats, analytics, and loadout archives persist across restart.
- Optional integrations fail safely when missing.

If any test fails:
- Save console logs.
- Record exact command/action sequence.
- Record selected map, block rules, combat item rules, explosives rules, wager, and involved plugins.
- Do not move to live until the failure is reproduced or explained.
