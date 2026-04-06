package dev.levelsystem.api;

/**
 * Functional interface defining how much total XP is required to reach a given level.
 * <p>
 * Default implementation: {@code 100 * level²}
 * <p>
 * Example usage:
 * <pre>{@code
 *   api.setLevelFormula(level -> 100 * level * level);
 * }</pre>
 */
@FunctionalInterface
public interface LevelFormula {

    /**
     * Calculate the XP required to reach {@code level} from level {@code level - 1}.
     *
     * @param level the target level (≥ 1)
     * @return XP required for this level step
     */
    long xpForLevel(int level);

    /**
     * Calculate cumulative XP needed to reach {@code level} from level 0.
     *
     * @param level target level
     * @return total cumulative XP
     */
    default long totalXpForLevel(int level) {
        long total = 0;
        for (int i = 1; i <= level; i++) {
            total += xpForLevel(i);
        }
        return total;
    }

    // ── Built-in formulas ────────────────────────────────────────────────

    /** Quadratic: 100 × level² — default */
    LevelFormula QUADRATIC = level -> 100L * level * level;

    /** Linear: 100 × level */
    LevelFormula LINEAR = level -> 100L * level;

    /** Exponential-ish: 50 × level^1.5 */
    LevelFormula MODERATE = level -> (long) (50 * Math.pow(level, 1.5));

    /** Classic Minecraft-style cubic curve */
    LevelFormula CLASSIC = level -> {
        if (level <= 16) return 2L * level + 7;
        if (level <= 31) return 5L * level - 38;
        return 9L * level - 158;
    };
}
