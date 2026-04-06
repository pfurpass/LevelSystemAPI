package dev.levelsystem.events;

import dev.levelsystem.api.Skill;
import dev.levelsystem.api.XPSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player is about to gain XP.
 * The amount can be modified or the event cancelled entirely.
 *
 * <pre>{@code
 * @EventHandler
 * public void onXPGain(XPGainEvent event) {
 *     // Double XP for VIPs
 *     if (event.getPlayer().hasPermission("server.vip")) {
 *         event.setAmount(event.getAmount() * 2);
 *     }
 *
 *     // Deny XP from a specific source
 *     if (event.getSource() == XPSource.BLOCK_BREAK) {
 *         event.setCancelled(true);
 *     }
 * }
 * }</pre>
 */
public class XPGainEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final XPSource source;
    private final double appliedMultiplier;
    private final @Nullable Skill skill;

    private long amount;
    private boolean cancelled = false;

    /** Global XP gain constructor */
    public XPGainEvent(@NotNull Player player, long amount, @NotNull XPSource source, double appliedMultiplier) {
        this(player, amount, source, appliedMultiplier, null);
    }

    /** Skill XP gain constructor */
    public XPGainEvent(@NotNull Player player, long amount, @NotNull XPSource source,
                       double appliedMultiplier, @Nullable Skill skill) {
        this.player = player;
        this.amount = amount;
        this.source = source;
        this.appliedMultiplier = appliedMultiplier;
        this.skill = skill;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public @NotNull Player getPlayer() { return player; }

    /** Current XP amount (after multipliers, before this event modifies it). */
    public long getAmount() { return amount; }

    /** Override the XP amount to grant. */
    public void setAmount(long amount) { this.amount = Math.max(0, amount); }

    public @NotNull XPSource getSource() { return source; }

    /** Multiplier that was already applied to produce {@code amount}. */
    public double getAppliedMultiplier() { return appliedMultiplier; }

    /** Non-null if this XP gain is for a specific skill. */
    public @Nullable Skill getSkill() { return skill; }

    public boolean isSkillXP() { return skill != null; }

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
