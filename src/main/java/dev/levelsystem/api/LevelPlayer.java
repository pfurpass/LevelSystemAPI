package dev.levelsystem.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a player's full progression state.
 * <p>
 * This object is cached in memory – modifications here are flushed
 * to the database asynchronously via {@link dev.levelsystem.storage.StorageProvider}.
 * <p>
 * Always interact with this object on the main thread unless you know what you're doing.
 */
public class LevelPlayer {

    private final UUID uuid;
    private final String name;

    // Global level / XP
    private volatile int level;
    private volatile long xp;

    // Skill levels / XP: skillId → data
    private final ConcurrentHashMap<String, SkillData> skillData = new ConcurrentHashMap<>();

    // Dirty flag for async save
    private volatile boolean dirty = false;

    // ── Constructor ──────────────────────────────────────────────────────

    public LevelPlayer(@NotNull UUID uuid, @NotNull String name, int level, long xp) {
        this.uuid = uuid;
        this.name = name;
        this.level = level;
        this.xp = xp;
    }

    // ── Global Level / XP ────────────────────────────────────────────────

    /** Get the player's current global level. */
    public int getLevel() { return level; }

    /** Get the player's current global XP. */
    public long getXP() { return xp; }

    /** Set the global level directly (does not adjust XP). */
    public void setLevel(int level) {
        this.level = Math.max(0, level);
        markDirty();
    }

    /** Set the global XP directly (does not trigger level calculations). */
    public void setXP(long xp) {
        this.xp = Math.max(0, xp);
        markDirty();
    }

    /**
     * Internal: apply level-up result from {@link LevelAPI}.
     * Called only from within the API to ensure events fire correctly.
     */
    void applyGlobal(int newLevel, long newXP) {
        this.level = newLevel;
        this.xp = newXP;
        markDirty();
    }

    // ── Skill Level / XP ─────────────────────────────────────────────────

    /** Get the level for a specific skill (0 if not started). */
    public int getLevel(@NotNull Skill skill) {
        return getSkillData(skill).level;
    }

    /** Get the XP for a specific skill. */
    public long getXP(@NotNull Skill skill) {
        return getSkillData(skill).xp;
    }

    /** Internal: apply skill level-up result. */
    public void applySkill(@NotNull Skill skill, int newLevel, long newXP) {
        SkillData data = getSkillData(skill);
        data.level = newLevel;
        data.xp = newXP;
        markDirty();
    }

    /** Initialize skill data (called by storage layer on load). */
    public void loadSkillData(@NotNull String skillId, int level, long xp) {
        skillData.put(skillId, new SkillData(level, xp));
    }

    /** Unmodifiable view of all skill data. */
    public @NotNull Map<String, SkillData> getAllSkillData() {
        return Collections.unmodifiableMap(skillData);
    }

    private @NotNull SkillData getSkillData(@NotNull Skill skill) {
        return skillData.computeIfAbsent(skill.getId(), id -> new SkillData(0, 0));
    }

    // ── XP Progress ──────────────────────────────────────────────────────

    /**
     * Calculate XP needed to advance from current level to the next.
     * Returns -1 if at max level.
     */
    public long xpToNextLevel(@NotNull LevelFormula formula, int maxLevel) {
        if (maxLevel > 0 && level >= maxLevel) return -1L;
        return formula.xpForLevel(level + 1) - xp;
    }

    /** Progress percentage (0.0 – 1.0) towards the next level. */
    public double progressToNextLevel(@NotNull LevelFormula formula, int maxLevel) {
        if (maxLevel > 0 && level >= maxLevel) return 1.0;
        long needed = formula.xpForLevel(level + 1);
        if (needed <= 0) return 1.0;
        return Math.min(1.0, (double) xp / needed);
    }

    // ── Misc ─────────────────────────────────────────────────────────────

    public @NotNull UUID getUUID() { return uuid; }

    public @NotNull String getName() { return name; }

    /** Returns the online {@link Player} or null if offline. */
    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public boolean isDirty() { return dirty; }

    public void markDirty() { dirty = true; }

    public void markClean() { dirty = false; }

    @Override
    public String toString() {
        return "LevelPlayer{uuid=" + uuid + ", name=" + name + ", level=" + level + ", xp=" + xp + "}";
    }

    // ── Inner: SkillData ─────────────────────────────────────────────────

    public static class SkillData {
        public volatile int level;
        public volatile long xp;

        public SkillData(int level, long xp) {
            this.level = level;
            this.xp = xp;
        }
    }
}
