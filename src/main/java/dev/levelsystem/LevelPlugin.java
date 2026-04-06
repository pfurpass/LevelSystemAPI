package dev.levelsystem;

import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.rest.RestAPIServer;
import dev.levelsystem.commands.LevelCommand;
import dev.levelsystem.config.LevelConfig;
import dev.levelsystem.hooks.PlaceholderHook;
import dev.levelsystem.hooks.VaultHook;
import dev.levelsystem.listeners.PlayerListener;
import dev.levelsystem.storage.MySQLStorage;
import dev.levelsystem.storage.RedisCache;
import dev.levelsystem.storage.SQLiteStorage;
import dev.levelsystem.storage.StorageProvider;
import dev.levelsystem.ui.UIManager;
import dev.levelsystem.util.AntiExploit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for LevelSystemAPI.
 *
 * <p>Startup order:
 * <ol>
 *   <li>Load config</li>
 *   <li>Init storage (SQLite / MySQL → optional Redis wrapper)</li>
 *   <li>Build {@link LevelAPI}</li>
 *   <li>Register skills from config</li>
 *   <li>Register permission multipliers from config</li>
 *   <li>Hook Vault + PlaceholderAPI</li>
 *   <li>Register commands + listeners</li>
 * </ol>
 */
public final class LevelPlugin extends JavaPlugin {

    private LevelAPI api;
    private LevelConfig levelConfig;
    private VaultHook vaultHook;
    private RestAPIServer restAPI;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // 1. Config
        levelConfig = new LevelConfig(this);
        levelConfig.load();

        // 2. Storage
        StorageProvider storage = buildStorage();
        storage.init();

        // 3. Core systems
        UIManager ui        = new UIManager(getConfig());
        AntiExploit exploit = new AntiExploit(getConfig());

        // 4. API
        api = new LevelAPI(this);
        api.setMaxLevel(levelConfig.getMaxLevel());
        api.setLevelFormula(levelConfig.getFormula());
        api.init(storage, ui, exploit);

        // 5. Register skills
        levelConfig.getSkills().forEach(api::registerSkill);

        // 6. Permission multipliers from config
        loadPermissionMultipliers();

        // 7. Hooks
        hookVault();
        hookPlaceholderAPI();

        // 8. Commands + listeners
        LevelCommand cmd = new LevelCommand(api);
        getCommand("level").setExecutor(cmd);
        getCommand("level").setTabCompleter(cmd);
        getCommand("xp").setExecutor(cmd);
        getCommand("xp").setTabCompleter(cmd);
        getCommand("skill").setExecutor(cmd);
        getCommand("skill").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(
                new PlayerListener(api, ui, getConfig()), this);

        // 9. REST API (optional)
        if (getConfig().getBoolean("rest-api.enabled", false)) {
            String host   = getConfig().getString("rest-api.host", "0.0.0.0");
            int port      = getConfig().getInt("rest-api.port", 8080);
            String secret = getConfig().getString("rest-api.secret-key", "");
            restAPI = new RestAPIServer(api, getLogger(), secret);
            try {
                restAPI.start(host, port);
            } catch (Exception e) {
                getLogger().severe("[LevelSystem] REST API failed to start: " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("LevelSystemAPI enabled in " + elapsed + "ms. " +
                "Storage: " + getConfig().getString("storage.type", "sqlite").toUpperCase() +
                " | Skills: " + api.getSkills().size());
    }

    @Override
    public void onDisable() {
        if (restAPI != null) restAPI.stop();
        if (api != null) api.shutdown();
        getLogger().info("LevelSystemAPI disabled – all player data saved.");
    }

    // ── Storage factory ───────────────────────────────────────────────────

    private StorageProvider buildStorage() {
        String type = getConfig().getString("storage.type", "sqlite").toLowerCase();

        StorageProvider base = switch (type) {
            case "mysql", "mariadb" -> new MySQLStorage(getLogger(), getConfig());
            default                  -> new SQLiteStorage(getLogger(), getDataFolder(), getConfig());
        };

        // Optionally wrap with Redis
        if (getConfig().getBoolean("storage.redis.enabled", false)) {
            RedisCache redis = new RedisCache(base, getLogger(), getConfig());
            redis.connectRedis(getConfig());
            return redis;
        }

        return base;
    }

    // ── Hook helpers ──────────────────────────────────────────────────────

    private void hookVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return;
        vaultHook = new VaultHook(api);
        if (vaultHook.hookEconomy()) {
            getLogger().info("[LevelSystem] Vault economy hooked.");
        } else {
            // Register self as provider if no other economy plugin is present
            vaultHook.registerAsEconomyProvider(this);
            getLogger().info("[LevelSystem] Vault: registered XP as economy provider.");
        }
    }

    private void hookPlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        new PlaceholderHook(api).register();
        getLogger().info("[LevelSystem] PlaceholderAPI hooked.");
    }

    private void loadPermissionMultipliers() {
        var section = getConfig().getConfigurationSection("multipliers");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (key.equals("default")) continue;
            double mult = section.getDouble(key, 1.0);
            api.registerMultiplier("levelsystem.xp.multiplier." + key, mult);
            getLogger().info("[LevelSystem] Multiplier: " + key + " → " + mult + "x");
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public LevelAPI getAPI() { return api; }

    public VaultHook getVaultHook() { return vaultHook; }
}
