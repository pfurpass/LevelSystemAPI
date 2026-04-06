package dev.levelsystem.hooks;

import dev.levelsystem.api.LevelAPI;
import dev.levelsystem.api.LevelPlayer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Optional Vault integration.
 *
 * <p>Two modes (configurable):
 * <ol>
 *   <li><b>Consumer</b>: uses Vault Economy to pay players on level-up.</li>
 *   <li><b>Provider</b>: exposes XP as a Vault Economy currency so other
 *       plugins can read/modify XP through the Vault interface.</li>
 * </ol>
 */
public class VaultHook {

    private Economy economy;
    private final LevelAPI api;

    public VaultHook(LevelAPI api) {
        this.api = api;
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    /**
     * Attempt to hook into an existing Vault Economy provider.
     *
     * @return true if a provider was found
     */
    public boolean hookEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return true;
    }

    /**
     * Register LevelSystemAPI as a Vault Economy provider so other plugins
     * can treat XP as currency.
     *
     * @param plugin the main plugin instance
     */
    public void registerAsEconomyProvider(JavaPlugin plugin) {
        Bukkit.getServicesManager().register(Economy.class, new XPEconomyProvider(api),
                plugin, ServicePriority.Normal);
    }

    public boolean isHooked() { return economy != null; }

    // ── Economy helpers ───────────────────────────────────────────────────

    /**
     * Pay a player a fixed amount of currency on level-up.
     *
     * @param player online/offline player
     * @param amount currency amount
     */
    public boolean payOnLevelUp(OfflinePlayer player, double amount) {
        if (!isHooked()) return false;
        EconomyResponse resp = economy.depositPlayer(player, amount);
        return resp.transactionSuccess();
    }

    public Economy getEconomy() { return economy; }

    // ── XP as Economy provider ────────────────────────────────────────────

    /**
     * Implements {@link Economy} using XP as the currency balance.
     * Other plugins calling {@code Economy#getBalance(player)} will get the player's XP.
     */
    public static final class XPEconomyProvider implements Economy {

        private final LevelAPI api;

        public XPEconomyProvider(LevelAPI api) { this.api = api; }

        @Override public boolean isEnabled() { return true; }
        @Override public String getName() { return "LevelSystem-XP"; }
        @Override public boolean hasBankSupport() { return false; }
        @Override public int fractionalDigits() { return 0; }
        @Override public String format(double amount) { return (long) amount + " XP"; }
        @Override public String currencyNamePlural() { return "XP"; }
        @Override public String currencyNameSingular() { return "XP"; }

        @Override
        public boolean hasAccount(OfflinePlayer player) { return true; }
        @Override
        public boolean hasAccount(OfflinePlayer player, String world) { return true; }
        @Override
        public boolean hasAccount(String playerName) { return true; }
        @Override
        public boolean hasAccount(String playerName, String world) { return true; }

        @Override
        public double getBalance(OfflinePlayer player) {
            return api.getPlayer(player.getUniqueId()).getXP();
        }
        @Override
        public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
        @Override
        public double getBalance(String playerName) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerName);
            return api.getPlayer(op.getUniqueId()).getXP();
        }
        @Override
        public double getBalance(String playerName, String world) { return getBalance(playerName); }

        @Override
        public boolean has(OfflinePlayer player, double amount) { return getBalance(player) >= amount; }
        @Override
        public boolean has(OfflinePlayer player, String world, double amount) { return has(player, amount); }
        @Override
        public boolean has(String playerName, double amount) { return getBalance(playerName) >= amount; }
        @Override
        public boolean has(String playerName, String world, double amount) { return has(playerName, amount); }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            LevelPlayer lp = api.getPlayer(player.getUniqueId());
            long current = lp.getXP();
            if (current < (long) amount)
                return new EconomyResponse(0, current, EconomyResponse.ResponseType.FAILURE, "Not enough XP");
            lp.setXP(current - (long) amount);
            lp.markDirty();
            return new EconomyResponse(amount, lp.getXP(), EconomyResponse.ResponseType.SUCCESS, null);
        }
        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return withdrawPlayer(player, amount); }
        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
        }
        @Override
        public EconomyResponse withdrawPlayer(String playerName, String world, double amount) { return withdrawPlayer(playerName, amount); }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            LevelPlayer lp = api.getPlayer(player.getUniqueId());
            lp.setXP(lp.getXP() + (long) amount);
            lp.markDirty();
            return new EconomyResponse(amount, lp.getXP(), EconomyResponse.ResponseType.SUCCESS, null);
        }
        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return depositPlayer(player, amount); }
        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
        }
        @Override
        public EconomyResponse depositPlayer(String playerName, String world, double amount) { return depositPlayer(playerName, amount); }

        // Bank operations – not supported
        @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notSupported(); }
        @Override public EconomyResponse createBank(String name, String player) { return notSupported(); }
        @Override public EconomyResponse deleteBank(String name) { return notSupported(); }
        @Override public EconomyResponse bankBalance(String name) { return notSupported(); }
        @Override public EconomyResponse bankHas(String name, double amount) { return notSupported(); }
        @Override public EconomyResponse bankWithdraw(String name, double amount) { return notSupported(); }
        @Override public EconomyResponse bankDeposit(String name, double amount) { return notSupported(); }
        @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notSupported(); }
        @Override public EconomyResponse isBankOwner(String name, String playerName) { return notSupported(); }
        @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notSupported(); }
        @Override public EconomyResponse isBankMember(String name, String playerName) { return notSupported(); }
        @Override public List<String> getBanks() { return List.of(); }
        @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
        @Override public boolean createPlayerAccount(OfflinePlayer player, String world) { return true; }
        @Override public boolean createPlayerAccount(String playerName) { return true; }
        @Override public boolean createPlayerAccount(String playerName, String world) { return true; }

        private EconomyResponse notSupported() {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks not supported");
        }
    }
}
