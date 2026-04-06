package dev.levelsystem.listeners;

import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.api.LevelPlayer;
import dev.levelsystem.api.XPSource;
import dev.levelsystem.ui.UIManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;

/**
 * Core event listener.
 *
 * <p>Handles:
 * <ul>
 *   <li>Player join  → load cache, update boss bar</li>
 *   <li>Player quit  → save + unload from cache</li>
 *   <li>Auto XP sources (mob kill, block break) as configured</li>
 * </ul>
 *
 * <p>Third-party plugins can disable the built-in XP sources and fire their
 * own {@link dev.levelsystem.events.XPGainEvent} manually, or call
 * {@link LevelAPI#addXP(Player, long, XPSource)} directly.
 */
public class PlayerListener implements Listener {

    private final LevelAPI api;
    private final UIManager ui;
    private final FileConfiguration config;

    public PlayerListener(LevelAPI api, UIManager ui, FileConfiguration config) {
        this.api = api;
        this.ui = ui;
        this.config = config;
    }

    // ── Session ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Load async, then push boss bar update on main thread once ready
        api.getPlayerAsync(player.getUniqueId()).thenAccept(lp -> {
            if (!player.isOnline()) return;
            player.getServer().getScheduler().runTask(
                    player.getServer().getPluginManager().getPlugin("LevelSystemAPI"),
                    () -> refreshBossBar(player, lp)
            );
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        api.unloadPlayer(event.getPlayer().getUniqueId());
        ui.removeBossBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        api.unloadPlayer(event.getPlayer().getUniqueId());
        ui.removeBossBar(event.getPlayer());
    }

    // ── Auto XP Sources ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) return;
        Player killer = (Player) event.getEntity().getKiller();
        long xp = config.getLong("xp.kill-mob", 10L);
        if (xp > 0) api.addXP(killer, xp, XPSource.MOB_KILL);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String type = event.getBlock().getType().name();

        // Ore detection: any block whose name contains "ORE"
        if (type.contains("ORE")) {
            long xp = config.getLong("xp.mine-ore", 8L);
            if (xp > 0) api.addXP(player, xp, XPSource.ORE_MINE);
        } else {
            long xp = config.getLong("xp.break-block", 2L);
            if (xp > 0) api.addXP(player, xp, XPSource.BLOCK_BREAK);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        long xp = config.getLong("xp.fish-catch", 15L);
        if (xp > 0) api.addXP(event.getPlayer(), xp, XPSource.FISH);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void refreshBossBar(Player player, LevelPlayer lp) {
        long xpRequired = api.getLevelFormula().xpForLevel(lp.getLevel() + 1);
        ui.updateBossBar(player, lp.getLevel(), lp.getXP(), xpRequired);
    }
}
