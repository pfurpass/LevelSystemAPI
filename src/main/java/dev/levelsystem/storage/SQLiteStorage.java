package dev.levelsystem.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * SQLite storage backend – zero-config default.
 */
public class SQLiteStorage extends AbstractSQLStorage {

    private final File dataFolder;
    private final String fileName;

    public SQLiteStorage(Logger log, File dataFolder, FileConfiguration config) {
        super(log);
        this.dataFolder = dataFolder;
        this.fileName = config.getString("storage.sqlite.file", "levelsystem.db");
    }

    @Override
    protected void configureDataSource() {
        HikariConfig cfg = new HikariConfig();
        String path = new File(dataFolder, fileName).getAbsolutePath();
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + path);
        cfg.setMaximumPoolSize(1);         // SQLite is single-writer
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setPoolName("LevelSystem-SQLite");
        dataSource = new HikariDataSource(cfg);
        log.info("[LevelSystem] Using SQLite: " + path);
    }

    @Override
    protected String upsertPlayerSQL() {
        // SQLite INSERT OR REPLACE
        return "INSERT OR REPLACE INTO ls_players (uuid, name, level, xp, updated) " +
               "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
    }

    @Override
    protected String upsertSkillSQL() {
        return "INSERT OR REPLACE INTO ls_skill_data (uuid, skill_id, level, xp) VALUES (?, ?, ?, ?)";
    }
}
