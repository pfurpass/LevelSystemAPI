package dev.levelsystem.hooks;

import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.api.LevelPlayer;
import dev.levelsystem.api.Skill;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion providing the following placeholders:
 *
 * <pre>
 *   %levelsystem_level%                   → global level
 *   %levelsystem_xp%                      → current global XP
 *   %levelsystem_xp_required%             → XP needed for next level
 *   %levelsystem_xp_to_next%              → XP remaining until next level
 *   %levelsystem_progress%                → progress % (0–100)
 *   %levelsystem_max_level%               → configured max level
 *   %levelsystem_multiplier%              → effective XP multiplier
 *   %levelsystem_skill_<id>_level%        → skill level
 *   %levelsystem_skill_<id>_xp%           → skill XP
 *   %levelsystem_skill_<id>_xp_required%  → skill XP needed for next level
 * </pre>
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final LevelAPI api;

    public PlaceholderHook(LevelAPI api) {
        this.api = api;
    }

    @Override
    public @NotNull String getIdentifier() { return "levelsystem"; }

    @Override
    public @NotNull String getAuthor() { return "LevelSystem"; }

    @Override
    public @NotNull String getVersion() { return "1.0.0"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        LevelPlayer lp = api.getPlayer(player);

        // Global placeholders
        return switch (params) {
            case "level"        -> String.valueOf(lp.getLevel());
            case "xp"           -> String.valueOf(lp.getXP());
            case "xp_required"  -> String.valueOf(api.getLevelFormula().xpForLevel(lp.getLevel() + 1));
            case "xp_to_next"   -> {
                long toNext = lp.xpToNextLevel(api.getLevelFormula(), api.getMaxLevel());
                yield toNext < 0 ? "MAX" : String.valueOf(toNext);
            }
            case "progress"     -> {
                double pct = lp.progressToNextLevel(api.getLevelFormula(), api.getMaxLevel()) * 100;
                yield String.format("%.1f", pct);
            }
            case "max_level"    -> String.valueOf(api.getMaxLevel());
            case "multiplier"   -> String.format("%.1fx", api.getMultiplier(player));
            default             -> resolveSkillPlaceholder(lp, params);
        };
    }

    private @Nullable String resolveSkillPlaceholder(LevelPlayer lp, String params) {
        // Format: skill_<id>_<type>
        if (!params.startsWith("skill_")) return null;
        String[] parts = params.split("_", 3);
        if (parts.length < 3) return null;

        String skillId = parts[1];
        String type    = parts[2];
        Skill skill    = api.getSkill(skillId);
        if (skill == null) return "?";

        return switch (type) {
            case "level"       -> String.valueOf(lp.getLevel(skill));
            case "xp"          -> String.valueOf(lp.getXP(skill));
            case "xp_required" -> String.valueOf(skill.getFormula().xpForLevel(lp.getLevel(skill) + 1));
            default            -> null;
        };
    }
}
