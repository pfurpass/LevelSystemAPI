package dev.levelsystem.ui;

import dev.levelsystem.api.XPSource;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UIManager {

    private final FileConfiguration config;

    public UIManager(@NotNull FileConfiguration config) {
        this.config = config;
    }

    public void sendXPGain(@NotNull Player player, long amount, @NotNull XPSource source) {
        if (!config.getBoolean("ui.actionbar.enabled", true)) return;
        String format = config.getString("ui.actionbar.format", "&a+{amount} XP &7({source})");
        String msg = format.replace("{amount}", String.valueOf(amount)).replace("{source}", source.toString());
        sendRawActionbar(player, msg);
    }

    public void sendRawActionbar(@NotNull Player player, @NotNull String message) {
        // Actionbar deaktiviert – Spigot 1.21 unterstützt sendActionBar(String) nicht mehr
        // Stattdessen: kurze Chat-Nachricht (optional, auskommentierbar)
        // player.sendMessage(colorize(message));
    }

    public void sendLevelUpTitle(@NotNull Player player, int level) {
        if (!config.getBoolean("ui.levelup-title.enabled", true)) return;
        String title    = colorize(config.getString("ui.levelup-title.title", "&6&lLEVEL UP!"));
        String subtitle = colorize(config.getString("ui.levelup-title.subtitle", "&eYou reached level &6{level}&e!").replace("{level}", String.valueOf(level)));
        int fadeIn  = config.getInt("ui.levelup-title.fade-in", 10);
        int stay    = config.getInt("ui.levelup-title.stay", 70);
        int fadeOut = config.getInt("ui.levelup-title.fade-out", 20);
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    public void playLevelUpSound(@NotNull Player player) {
        if (!config.getBoolean("ui.levelup-sound.enabled", true)) return;
        String soundName = config.getString("ui.levelup-sound.sound", "ENTITY_PLAYER_LEVELUP");
        float volume = (float) config.getDouble("ui.levelup-sound.volume", 1.0);
        float pitch  = (float) config.getDouble("ui.levelup-sound.pitch", 1.0);
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), volume, pitch);
        } catch (IllegalArgumentException ignored) {}
    }

    public void updateBossBar(@NotNull Player player, int level, long xp, long xpRequired) {}
    public void removeBossBar(@NotNull Player player) {}

    private String colorize(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
