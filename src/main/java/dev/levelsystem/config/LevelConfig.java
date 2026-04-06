package dev.levelsystem.config;

import dev.levelsystem.LevelPlugin;
import dev.levelsystem.api.LevelFormula;
import dev.levelsystem.api.Skill;
import dev.levelsystem.util.FormulaParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed wrapper around {@code config.yml} that parses all settings once
 * on startup and provides strongly-typed accessors.
 */
public class LevelConfig {

    private final LevelPlugin plugin;

    private int maxLevel;
    private LevelFormula formula;
    private final List<Skill> skills = new ArrayList<>();

    public LevelConfig(LevelPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        var cfg = plugin.getConfig();

        // Max level
        maxLevel = cfg.getInt("max-level", 100);

        // Level formula
        String formulaExpr = cfg.getString("level-formula", "100 * level^2");
        try {
            formula = FormulaParser.parse(formulaExpr);
            // Smoke test
            formula.xpForLevel(5);
        } catch (Exception e) {
            plugin.getLogger().warning("[LevelSystem] Invalid level-formula '" + formulaExpr +
                    "' – falling back to QUADRATIC. Error: " + e.getMessage());
            formula = LevelFormula.QUADRATIC;
        }

        // Skills
        skills.clear();
        if (cfg.getBoolean("skills.enabled", true)) {
            List<String> skillIds = cfg.getStringList("skills.list");
            for (String id : skillIds) {
                int skillMax = cfg.getInt("skills.max-level." + id, maxLevel);
                Skill skill = new Skill(id);
                skill.setMaxLevel(skillMax);
                skill.setFormula(formula); // use same formula by default
                skills.add(skill);
            }
        }

        plugin.getLogger().info("[LevelSystem] Config loaded – max-level=" + maxLevel +
                ", skills=" + skills.size() + ", formula=" + formulaExpr);
    }

    public int getMaxLevel() { return maxLevel; }

    public LevelFormula getFormula() { return formula; }

    public List<Skill> getSkills() { return skills; }
}
