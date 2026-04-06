package dev.levelsystem.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a named skill that has its own independent level/XP progression.
 * <p>
 * Skills can use a different {@link LevelFormula} and max-level than the global system.
 */
public final class Skill {

    private final String id;
    private final String displayName;
    private LevelFormula formula;
    private int maxLevel;

    public Skill(@NotNull String id, @NotNull String displayName, @NotNull LevelFormula formula, int maxLevel) {
        this.id = id.toLowerCase();
        this.displayName = displayName;
        this.formula = formula;
        this.maxLevel = maxLevel;
    }

    /** Convenience constructor with defaults */
    public Skill(@NotNull String id) {
        this(id, capitalize(id), LevelFormula.QUADRATIC, 50);
    }

    // ── Getters ──────────────────────────────────────────────────────────

    /** Unique lowercase identifier, e.g. {@code "mining"} */
    public @NotNull String getId() { return id; }

    /** Human-readable name shown in UI */
    public @NotNull String getDisplayName() { return displayName; }

    public @NotNull LevelFormula getFormula() { return formula; }

    public int getMaxLevel() { return maxLevel; }

    // ── Setters ──────────────────────────────────────────────────────────

    public void setFormula(@NotNull LevelFormula formula) {
        this.formula = Objects.requireNonNull(formula);
    }

    public void setMaxLevel(int maxLevel) {
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel must be >= 1");
        this.maxLevel = maxLevel;
    }

    // ── Util ─────────────────────────────────────────────────────────────

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Skill other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return "Skill{" + id + "}"; }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
