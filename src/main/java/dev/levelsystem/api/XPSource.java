package dev.levelsystem.api;

/**
 * Represents the source of an XP gain event.
 * Used for analytics, pipeline filtering, and anti-exploit tracking.
 */
public enum XPSource {

    MOB_KILL("kill-mob"),
    BLOCK_BREAK("break-block"),
    ORE_MINE("mine-ore"),
    CRAFT("craft-item"),
    FISH("fish-catch"),
    BREED("breed-animal"),

    /** Directly awarded by another plugin / admin */
    PLUGIN("plugin"),

    /** Admin command */
    ADMIN("admin"),

    /** Custom / plugin-defined source */
    CUSTOM("custom"),

    /** Unknown / unspecified */
    UNKNOWN("unknown");

    private final String configKey;

    XPSource(String configKey) {
        this.configKey = configKey;
    }

    /** Config key used to look up the base XP value in config.yml */
    public String getConfigKey() {
        return configKey;
    }

    @Override
    public String toString() {
        return name().replace('_', ' ').toLowerCase();
    }
}
