package dev.levelsystem.api;

import dev.levelsystem.LevelPlugin;
import dev.levelsystem.events.LevelUpEvent;
import dev.levelsystem.events.XPGainEvent;
import dev.levelsystem.storage.StorageProvider;
import dev.levelsystem.ui.UIManager;
import dev.levelsystem.util.AntiExploit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.logging.Logger;

/**
 * Main entry point for the LevelSystemAPI.
 *
 * <h3>Minimal setup:</h3>
 * <pre>{@code
 *   LevelAPI api = LevelAPI.get();
 *   api.addXP(player, 25);
 * }</pre>
 *
 * <h3>With source tracking:</h3>
 * <pre>{@code
 *   api.addXP(player, 100, XPSource.MOB_KILL);
 * }</pre>
 */
public class LevelAPI {

    private static LevelAPI instance;

    private final LevelPlugin plugin;
    private final Logger log;

    // ── Player Cache ─────────────────────────────────────────────────────
    private final ConcurrentHashMap<UUID, LevelPlayer> cache = new ConcurrentHashMap<>();

    // ── Systems ──────────────────────────────────────────────────────────
    private StorageProvider storage;
    private UIManager uiManager;
    private AntiExploit antiExploit;

    // ── Configuration ─────────────────────────────────────────────────────
    private LevelFormula globalFormula = LevelFormula.QUADRATIC;
    private int maxLevel = 100;

    // ── Skills ───────────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, Skill> skills = new ConcurrentHashMap<>();

    // ── Reward Callbacks ─────────────────────────────────────────────────
    private final List<Consumer<LevelUpEvent>> rewardHandlers = Collections.synchronizedList(new ArrayList<>());

    // ── XP Multipliers ───────────────────────────────────────────────────
    /** Permission → multiplier map, evaluated in insertion order */
    private final LinkedHashMap<String, Double> permissionMultipliers = new LinkedHashMap<>();

    // ── Constructor ──────────────────────────────────────────────────────

    public LevelAPI(@NotNull LevelPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        instance = this;
    }

    // ── Singleton ────────────────────────────────────────────────────────

    /**
     * Get the active {@link LevelAPI} instance.
     *
     * @throws IllegalStateException if LevelSystemAPI is not loaded
     */
    public static @NotNull LevelAPI get() {
        if (instance == null) throw new IllegalStateException("LevelSystemAPI is not loaded!");
        return instance;
    }

    // ── Init / Shutdown ──────────────────────────────────────────────────

    public void init(@NotNull StorageProvider storage, @NotNull UIManager uiManager, @NotNull AntiExploit antiExploit) {
        this.storage = storage;
        this.uiManager = uiManager;
        this.antiExploit = antiExploit;

        // Start periodic dirty-flush task (every 30 s)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirty, 600L, 600L);
    }

    public void shutdown() {
        flushDirty();
        cache.clear();
        instance = null;
    }

    // ── Player Cache ─────────────────────────────────────────────────────

    /**
     * Get a {@link LevelPlayer} from cache or database.
     * This is a synchronous call – prefer {@link #getPlayerAsync(UUID)} off the main thread.
     */
    public @NotNull LevelPlayer getPlayer(@NotNull UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> {
            LevelPlayer loaded = storage.load(id);
            if (loaded == null) {
                String name = Optional.ofNullable(Bukkit.getOfflinePlayer(id).getName()).orElse("Unknown");
                loaded = new LevelPlayer(id, name, 0, 0);
            }
            return loaded;
        });
    }

    /** Convenience overload accepting a live {@link Player}. */
    public @NotNull LevelPlayer getPlayer(@NotNull Player player) {
        return getPlayer(player.getUniqueId());
    }

    /** Async load (creates entry in cache when done). */
    public @NotNull CompletableFuture<LevelPlayer> getPlayerAsync(@NotNull UUID uuid) {
        if (cache.containsKey(uuid)) return CompletableFuture.completedFuture(cache.get(uuid));
        return CompletableFuture.supplyAsync(() -> getPlayer(uuid));
    }

    /** Remove player from cache (called on quit after saving). */
    public void unloadPlayer(@NotNull UUID uuid) {
        LevelPlayer lp = cache.remove(uuid);
        if (lp != null && lp.isDirty()) {
            CompletableFuture.runAsync(() -> storage.save(lp));
        }
    }

    // ── XP Management ────────────────────────────────────────────────────

    /**
     * Add XP to a player using the global formula.
     *
     * @param player online player
     * @param amount raw XP amount (before multipliers)
     */
    public void addXP(@NotNull Player player, long amount) {
        addXP(player, amount, XPSource.UNKNOWN);
    }

    /**
     * Add XP to a player with source tracking and multiplier support.
     *
     * @param player online player
     * @param amount raw XP amount (before multipliers)
     * @param source origin of the XP gain
     */
    public void addXP(@NotNull Player player, long amount, @NotNull XPSource source) {
        // Anti-exploit check
        if (!antiExploit.checkXP(player, amount, source)) return;

        // Apply permission multipliers
        double multiplier = getMultiplier(player);
        long finalAmount = Math.round(amount * multiplier);

        // Fire XPGainEvent (cancellable, modifiable)
        XPGainEvent event = new XPGainEvent(player, finalAmount, source, multiplier);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        finalAmount = event.getAmount();

        LevelPlayer lp = getPlayer(player);
        long newXP = lp.getXP() + finalAmount;

        // Check for level-up(s)
        int newLevel = lp.getLevel();
        while (true) {
            if (maxLevel > 0 && newLevel >= maxLevel) break;
            long needed = globalFormula.xpForLevel(newLevel + 1);
            if (newXP < needed) break;
            newXP -= needed;
            newLevel++;
        }

        boolean leveledUp = newLevel > lp.getLevel();
        int oldLevel = lp.getLevel();

        lp.applyGlobal(newLevel, newXP);

        // UI feedback
        uiManager.sendXPGain(player, finalAmount, source);

        // Level-up handling
        if (leveledUp) {
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                fireLevelUp(player, lp, lvl);
            }
        }

        // Async save
        scheduleAsyncSave(lp);

        if (plugin.getConfig().getBoolean("debug")) {
            log.info("[DEBUG] " + player.getName() + " gained " + finalAmount + " XP (src=" + source + ") → level=" + lp.getLevel() + " xp=" + lp.getXP());
        }
    }

    /**
     * Add XP to a specific skill.
     *
     * @param player online player
     * @param skill  target skill
     * @param amount raw XP amount
     */
    public void addXP(@NotNull Player player, @NotNull Skill skill, long amount) {
        addXP(player, skill, amount, XPSource.UNKNOWN);
    }

    public void addXP(@NotNull Player player, @NotNull Skill skill, long amount, @NotNull XPSource source) {
        if (!antiExploit.checkXP(player, amount, source)) return;

        double multiplier = getMultiplier(player);
        long finalAmount = Math.round(amount * multiplier);

        XPGainEvent event = new XPGainEvent(player, finalAmount, source, multiplier, skill);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        finalAmount = event.getAmount();

        LevelPlayer lp = getPlayer(player);
        long newXP = lp.getXP(skill) + finalAmount;
        int newLevel = lp.getLevel(skill);

        while (true) {
            if (newLevel >= skill.getMaxLevel()) break;
            long needed = skill.getFormula().xpForLevel(newLevel + 1);
            if (newXP < needed) break;
            newXP -= needed;
            newLevel++;
        }

        boolean leveledUp = newLevel > lp.getLevel(skill);
        int oldLevel = lp.getLevel(skill);

        lp.applySkill(skill, newLevel, newXP);

        if (leveledUp) {
            for (int lvl = oldLevel + 1; lvl <= newLevel; lvl++) {
                fireLevelUp(player, lp, lvl, skill);
            }
        }

        scheduleAsyncSave(lp);
    }

    /** Remove XP from a player (will not go below 0 XP or negative level). */
    public void removeXP(@NotNull Player player, long amount) {
        LevelPlayer lp = getPlayer(player);
        long newXP = Math.max(0, lp.getXP() - amount);
        lp.applyGlobal(lp.getLevel(), newXP);
        scheduleAsyncSave(lp);
    }

    /** Directly set a player's global level. */
    public void setLevel(@NotNull Player player, int level) {
        LevelPlayer lp = getPlayer(player);
        lp.applyGlobal(Math.max(0, Math.min(level, maxLevel)), 0);
        scheduleAsyncSave(lp);
    }

    // ── Formula / Config ─────────────────────────────────────────────────

    public void setLevelFormula(@NotNull LevelFormula formula) {
        this.globalFormula = formula;
    }

    public @NotNull LevelFormula getLevelFormula() { return globalFormula; }

    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

    public int getMaxLevel() { return maxLevel; }

    // ── Multipliers ──────────────────────────────────────────────────────

    /** Register a permission-based multiplier. */
    public void registerMultiplier(@NotNull String permission, double multiplier) {
        permissionMultipliers.put(permission, multiplier);
    }

    /** Calculate the effective XP multiplier for a player (highest matching wins). */
    public double getMultiplier(@NotNull Player player) {
        double best = plugin.getConfig().getDouble("multipliers.default", 1.0);
        for (Map.Entry<String, Double> entry : permissionMultipliers.entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                best = Math.max(best, entry.getValue());
            }
        }
        return best;
    }

    // ── Rewards ──────────────────────────────────────────────────────────

    /**
     * Register a reward handler that fires on every {@link LevelUpEvent}.
     *
     * <pre>{@code
     *   api.registerReward(event -> {
     *       if (event.getNewLevel() == 10) giveItem(event.getPlayer());
     *   });
     * }</pre>
     */
    public void registerReward(@NotNull Consumer<LevelUpEvent> handler) {
        rewardHandlers.add(handler);
    }

    /**
     * Register a reward that fires only at a specific level.
     */
    public void registerReward(int level, @NotNull Consumer<LevelUpEvent> handler) {
        rewardHandlers.add(event -> {
            if (event.getNewLevel() == level) handler.accept(event);
        });
    }

    // ── Skills ───────────────────────────────────────────────────────────

    /** Register a new skill. */
    public void registerSkill(@NotNull Skill skill) {
        skills.put(skill.getId(), skill);
    }

    /** Get a skill by id (case-insensitive). */
    public @Nullable Skill getSkill(@NotNull String id) {
        return skills.get(id.toLowerCase());
    }

    /** Get all registered skills. */
    public @NotNull Collection<Skill> getSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    // ── Storage ──────────────────────────────────────────────────────────

    public @NotNull StorageProvider getStorage() { return storage; }

    // ── UI ───────────────────────────────────────────────────────────────

    public @NotNull UIManager getUIManager() { return uiManager; }

    public void sendActionbar(@NotNull Player player, @NotNull String message) {
        uiManager.sendRawActionbar(player, message);
    }

    public void sendLevelUpTitle(@NotNull Player player) {
        LevelPlayer lp = getPlayer(player);
        uiManager.sendLevelUpTitle(player, lp.getLevel());
    }

    // ── Internal ─────────────────────────────────────────────────────────

    private void fireLevelUp(@NotNull Player player, @NotNull LevelPlayer lp, int newLevel) {
        fireLevelUp(player, lp, newLevel, null);
    }

    private void fireLevelUp(@NotNull Player player, @NotNull LevelPlayer lp, int newLevel, @Nullable Skill skill) {
        LevelUpEvent event = (skill == null)
                ? new LevelUpEvent(player, lp, newLevel - 1, newLevel)
                : new LevelUpEvent(player, lp, newLevel - 1, newLevel, skill);

        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            if (skill == null) {
                uiManager.sendLevelUpTitle(player, newLevel);
                uiManager.playLevelUpSound(player);
            }
            rewardHandlers.forEach(h -> h.accept(event));
        }
    }

    private void scheduleAsyncSave(@NotNull LevelPlayer lp) {
        lp.markDirty();
        CompletableFuture.runAsync(() -> {
            storage.save(lp);
            lp.markClean();
        });
    }

    private void flushDirty() {
        cache.values().stream()
                .filter(LevelPlayer::isDirty)
                .forEach(lp -> {
                    storage.save(lp);
                    lp.markClean();
                });
    }
}
