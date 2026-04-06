package dev.levelsystem.commands;

import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.api.LevelPlayer;
import dev.levelsystem.api.Skill;
import dev.levelsystem.api.XPSource;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles:
 * <pre>
 *   /level              → show own level
 *   /level info [player]
 *   /level set <player> <level>
 *   /level reset <player>
 *   /level top
 *
 *   /xp                 → show own XP
 *   /xp add <player> <amount> [source]
 *   /xp remove <player> <amount>
 *   /xp set <player> <amount>
 *
 *   /skill              → list skills
 *   /skill info <skill> [player]
 *   /skill set <skill> <player> <level>
 * </pre>
 */
public class LevelCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§6Level§8] §r";
    private static final String NO_PERM = PREFIX + "§cNo permission.";
    private static final String PLAYER_ONLY = PREFIX + "§cThis command requires a player.";

    private final LevelAPI api;

    public LevelCommand(LevelAPI api) { this.api = api; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "level", "lvl" -> handleLevel(sender, args);
            case "xp",   "exp"  -> handleXP(sender, args);
            case "skill"         -> handleSkill(sender, args);
            default -> false;
        };
    }

    // ── /level ────────────────────────────────────────────────────────────

    private boolean handleLevel(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();

        return switch (sub) {
            case "info" -> {
                Player target = resolveTarget(sender, args, 1);
                if (target == null) yield false;
                LevelPlayer lp = api.getPlayer(target.getUniqueId());
                long toNext = lp.xpToNextLevel(api.getLevelFormula(), api.getMaxLevel());
                sender.sendMessage(PREFIX + "§e" + target.getName() +
                        " §7– Level §6" + lp.getLevel() +
                        " §7| XP §a" + lp.getXP() +
                        " §7/ §a" + (toNext < 0 ? "MAX" : api.getLevelFormula().xpForLevel(lp.getLevel() + 1)));
                yield true;
            }
            case "set" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 3) { sender.sendMessage(PREFIX + "§cUsage: /level set <player> <level>"); yield true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                int level;
                try { level = Integer.parseInt(args[2]); } catch (NumberFormatException e) { sender.sendMessage(PREFIX + "§cInvalid number."); yield true; }
                api.setLevel(target, level);
                sender.sendMessage(PREFIX + "§aSet §e" + target.getName() + "§a's level to §6" + level + "§a.");
                yield true;
            }
            case "reset" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 2) { sender.sendMessage(PREFIX + "§cUsage: /level reset <player>"); yield true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                api.setLevel(target, 0);
                sender.sendMessage(PREFIX + "§aReset §e" + target.getName() + "§a's level.");
                yield true;
            }
            default -> {
                sender.sendMessage(PREFIX + "§7/level §6info §7[player]");
                sender.sendMessage(PREFIX + "§7/level §6set §7<player> <level>");
                sender.sendMessage(PREFIX + "§7/level §6reset §7<player>");
                yield true;
            }
        };
    }

    // ── /xp ──────────────────────────────────────────────────────────────

    private boolean handleXP(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show own XP
            if (!(sender instanceof Player player)) { sender.sendMessage(PLAYER_ONLY); return true; }
            LevelPlayer lp = api.getPlayer(player);
            sender.sendMessage(PREFIX + "§7Your XP: §a" + lp.getXP());
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "add" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 3) { sender.sendMessage(PREFIX + "§cUsage: /xp add <player> <amount> [source]"); yield true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                long amount;
                try { amount = Long.parseLong(args[2]); } catch (NumberFormatException e) { sender.sendMessage(PREFIX + "§cInvalid number."); yield true; }
                XPSource source = args.length >= 4 ? parseSource(args[3]) : XPSource.ADMIN;
                api.addXP(target, amount, source);
                sender.sendMessage(PREFIX + "§aAdded §e" + amount + " XP §ato §e" + target.getName() + "§a.");
                yield true;
            }
            case "remove" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 3) { sender.sendMessage(PREFIX + "§cUsage: /xp remove <player> <amount>"); yield true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                long amount;
                try { amount = Long.parseLong(args[2]); } catch (NumberFormatException e) { sender.sendMessage(PREFIX + "§cInvalid number."); yield true; }
                api.removeXP(target, amount);
                sender.sendMessage(PREFIX + "§aRemoved §e" + amount + " XP §afrom §e" + target.getName() + "§a.");
                yield true;
            }
            case "set" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 3) { sender.sendMessage(PREFIX + "§cUsage: /xp set <player> <amount>"); yield true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                long amount;
                try { amount = Long.parseLong(args[2]); } catch (NumberFormatException e) { sender.sendMessage(PREFIX + "§cInvalid number."); yield true; }
                LevelPlayer lp = api.getPlayer(target);
                lp.setXP(amount);
                lp.markDirty();
                sender.sendMessage(PREFIX + "§aSet §e" + target.getName() + "§a's XP to §e" + amount + "§a.");
                yield true;
            }
            default -> {
                sender.sendMessage(PREFIX + "§7/xp §6add §7<player> <amount>");
                sender.sendMessage(PREFIX + "§7/xp §6remove §7<player> <amount>");
                sender.sendMessage(PREFIX + "§7/xp §6set §7<player> <amount>");
                yield true;
            }
        };
    }

    // ── /skill ────────────────────────────────────────────────────────────

    private boolean handleSkill(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // List skills
            String skills = api.getSkills().stream()
                    .map(Skill::getDisplayName)
                    .collect(Collectors.joining("§7, §a"));
            sender.sendMessage(PREFIX + "§7Skills: §a" + (skills.isEmpty() ? "none" : skills));
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(PREFIX + "§cUsage: /skill info <skill> [player]"); yield true; }
                Skill skill = api.getSkill(args[1]);
                if (skill == null) { sender.sendMessage(PREFIX + "§cUnknown skill: " + args[1]); yield true; }
                Player target = resolveTarget(sender, args, 2);
                if (target == null) yield false;
                LevelPlayer lp = api.getPlayer(target.getUniqueId());
                sender.sendMessage(PREFIX + "§e" + target.getName() + " §8» §6" + skill.getDisplayName() +
                        " §7Lvl §6" + lp.getLevel(skill) + " §7| XP §a" + lp.getXP(skill));
                yield true;
            }
            case "set" -> {
                if (!sender.hasPermission("levelsystem.admin")) { sender.sendMessage(NO_PERM); yield true; }
                if (args.length < 4) { sender.sendMessage(PREFIX + "§cUsage: /skill set <skill> <player> <level>"); yield true; }
                Skill skill = api.getSkill(args[1]);
                if (skill == null) { sender.sendMessage(PREFIX + "§cUnknown skill: " + args[1]); yield true; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { sender.sendMessage(PREFIX + "§cPlayer not found."); yield true; }
                int level;
                try { level = Integer.parseInt(args[3]); } catch (NumberFormatException e) { sender.sendMessage(PREFIX + "§cInvalid number."); yield true; }
                LevelPlayer lp = api.getPlayer(target);
                lp.applySkill(skill, level, 0);
                lp.markDirty();
                sender.sendMessage(PREFIX + "§aSet §e" + target.getName() + "§a's §6" + skill.getDisplayName() + " §ato level §6" + level + "§a.");
                yield true;
            }
            default -> {
                sender.sendMessage(PREFIX + "§7/skill §6info §7<skill> [player]");
                sender.sendMessage(PREFIX + "§7/skill §6set §7<skill> <player> <level>");
                yield true;
            }
        };
    }

    // ── Tab Completion ────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command cmd,
                                                @NotNull String alias,
                                                @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> online = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();

        switch (cmd.getName().toLowerCase()) {
            case "level", "lvl" -> {
                if (args.length == 1) completions.addAll(List.of("info", "set", "reset"));
                else if (args.length == 2 && !args[0].equalsIgnoreCase("info")) completions.addAll(online);
            }
            case "xp", "exp" -> {
                if (args.length == 1) completions.addAll(List.of("add", "remove", "set"));
                else if (args.length == 2) completions.addAll(online);
                else if (args.length == 4 && args[0].equalsIgnoreCase("add"))
                    Arrays.stream(XPSource.values()).map(XPSource::name).forEach(completions::add);
            }
            case "skill" -> {
                if (args.length == 1) completions.addAll(List.of("info", "set"));
                else if (args.length == 2) api.getSkills().stream().map(Skill::getId).forEach(completions::add);
                else if (args.length == 3) completions.addAll(online);
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(input)).collect(Collectors.toList());
    }

    // ── Util ─────────────────────────────────────────────────────────────

    private @Nullable Player resolveTarget(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player t = Bukkit.getPlayerExact(args[index]);
            if (t == null) sender.sendMessage(PREFIX + "§cPlayer not found.");
            return t;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(PLAYER_ONLY); return null; }
        return p;
    }

    private XPSource parseSource(String s) {
        try { return XPSource.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return XPSource.CUSTOM; }
    }
}
