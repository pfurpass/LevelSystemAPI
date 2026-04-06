package dev.levelsystem.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * MySQL / MariaDB storage backend with connection pooling via HikariCP.
 */
public class MySQLStorage extends AbstractSQLStorage {

    private final FileConfiguration config;

    public MySQLStorage(Logger log, FileConfiguration config) {
        super(log);
        this.config = config;
    }

    @Override
    protected void configureDataSource() {
        String host     = config.getString("storage.mysql.host", "localhost");
        int    port     = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "levelsystem");
        String user     = config.getString("storage.mysql.username", "root");
        String pass     = config.getString("storage.mysql.password", "");
        boolean ssl     = config.getBoolean("storage.mysql.ssl", false);
        int poolSize    = config.getInt("storage.mysql.pool-size", 10);
        long timeout    = config.getLong("storage.mysql.connection-timeout", 30000L);

        String url = String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database, ssl);

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setConnectionTimeout(timeout);
        cfg.setPoolName("LevelSystem-MySQL");

        // Recommended MySQL tuning
        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(cfg);
        log.info("[LevelSystem] Using MySQL: " + host + ":" + port + "/" + database);
    }

    @Override
    protected String upsertPlayerSQL() {
        return "INSERT INTO ls_players (uuid, name, level, xp, updated) VALUES (?, ?, ?, ?, NOW()) " +
               "ON DUPLICATE KEY UPDATE name=VALUES(name), level=VALUES(level), xp=VALUES(xp), updated=NOW()";
    }

    @Override
    protected String upsertSkillSQL() {
        return "INSERT INTO ls_skill_data (uuid, skill_id, level, xp) VALUES (?, ?, ?, ?) " +
               "ON DUPLICATE KEY UPDATE level=VALUES(level), xp=VALUES(xp)";
    }
}
