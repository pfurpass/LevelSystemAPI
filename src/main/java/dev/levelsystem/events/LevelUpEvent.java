package dev.levelsystem.events;

import dev.levelsystem.api.LevelPlayer;
import dev.levelsystem.api.Skill;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player levels up – either globally or in a specific skill.
 *
 * <pre>{@code
 * @EventHandler
 * public void onLevelUp(LevelUpEvent event) {
 *     Player p = event.getPlayer();
 *     int newLevel = event.getNewLevel();
 *
 *     if (event.isSkillLevelUp()) {
 *         Skill skill = event.getSkill();
 *         // skill-specific handling
 *     }
 * }
 * }</pre>
 */
public class LevelUpEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final LevelPlayer levelPlayer;
    private final int oldLevel;
    private final int newLevel;
    private final @Nullable Skill skill;
    private boolean cancelled = false;

    /** Global level-up constructor */
    public LevelUpEvent(@NotNull Player player,
                        @NotNull LevelPlayer levelPlayer,
                        int oldLevel,
                        int newLevel) {
        this(player, levelPlayer, oldLevel, newLevel, null);
    }

    /** Skill level-up constructor */
    public LevelUpEvent(@NotNull Player player,
                        @NotNull LevelPlayer levelPlayer,
                        int oldLevel,
                        int newLevel,
                        @Nullable Skill skill) {
        this.player = player;
        this.levelPlayer = levelPlayer;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.skill = skill;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public @NotNull Player getPlayer() { return player; }

    public @NotNull LevelPlayer getLevelPlayer() { return levelPlayer; }

    public int getOldLevel() { return oldLevel; }

    public int getNewLevel() { return newLevel; }

    /** The skill that leveled up, or null for a global level-up. */
    public @Nullable Skill getSkill() { return skill; }

    /** True if this is a skill-specific level-up event. */
    public boolean isSkillLevelUp() { return skill != null; }

    // ── Cancellable ──────────────────────────────────────────────────────

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    // ── Bukkit boilerplate ───────────────────────────────────────────────

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
