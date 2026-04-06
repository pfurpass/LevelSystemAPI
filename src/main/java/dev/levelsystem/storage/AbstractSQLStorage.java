package dev.levelsystem.storage;

import com.zaxxer.hikari.HikariDataSource;
import dev.levelsystem.api.LevelPlayer;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared SQL logic for SQLite and MySQL backends.
 * Sub-classes only need to configure the {@link HikariDataSource}.
 */
public abstract class AbstractSQLStorage implements StorageProvider {

    protected final Logger log;
    protected HikariDataSource dataSource;

    protected AbstractSQLStorage(Logger log) {
        this.log = log;
    }

    // ── Table DDL ────────────────────────────────────────────────────────

    protected static final String CREATE_PLAYERS =
            "CREATE TABLE IF NOT EXISTS ls_players (" +
            "  uuid      VARCHAR(36)  NOT NULL PRIMARY KEY," +
            "  name      VARCHAR(64)  NOT NULL," +
            "  level     INT          NOT NULL DEFAULT 0," +
            "  xp        BIGINT       NOT NULL DEFAULT 0," +
            "  updated   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP" +
            ")";

    protected static final String CREATE_SKILLS =
            "CREATE TABLE IF NOT EXISTS ls_skill_data (" +
            "  uuid      VARCHAR(36)  NOT NULL," +
            "  skill_id  VARCHAR(64)  NOT NULL," +
            "  level     INT          NOT NULL DEFAULT 0," +
            "  xp        BIGINT       NOT NULL DEFAULT 0," +
            "  PRIMARY KEY (uuid, skill_id)," +
            "  FOREIGN KEY (uuid) REFERENCES ls_players(uuid) ON DELETE CASCADE" +
            ")";

    @Override
    public void init() {
        configureDataSource();
        try (Connection con = dataSource.getConnection();
             Statement st = con.createStatement()) {
            st.execute(CREATE_PLAYERS);
            st.execute(CREATE_SKILLS);
            log.info("[LevelSystem] Database tables ready.");
        } catch (SQLException e) {
            log.log(Level.SEVERE, "[LevelSystem] Failed to initialize database tables", e);
        }
    }

    /** Sub-classes must set up {@link #dataSource} here. */
    protected abstract void configureDataSource();

    // ── CRUD ─────────────────────────────────────────────────────────────

    @Override
    public @Nullable LevelPlayer load(UUID uuid) {
        String sql = "SELECT name, level, xp FROM ls_players WHERE uuid = ?";
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                LevelPlayer lp = new LevelPlayer(uuid, rs.getString("name"), rs.getInt("level"), rs.getLong("xp"));
                loadSkillData(con, lp);
                return lp;
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "[LevelSystem] Failed to load player " + uuid, e);
            return null;
        }
    }

    private void loadSkillData(Connection con, LevelPlayer lp) throws SQLException {
        String sql = "SELECT skill_id, level, xp FROM ls_skill_data WHERE uuid = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, lp.getUUID().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lp.loadSkillData(rs.getString("skill_id"), rs.getInt("level"), rs.getLong("xp"));
                }
            }
        }
    }

    @Override
    public void save(LevelPlayer lp) {
        // Upsert player row
        String upsert = upsertPlayerSQL();
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement(upsert)) {
            ps.setString(1, lp.getUUID().toString());
            ps.setString(2, lp.getName());
            ps.setInt(3, lp.getLevel());
            ps.setLong(4, lp.getXP());
            ps.executeUpdate();

            // Upsert each skill
            saveSkillData(con, lp);
        } catch (SQLException e) {
            log.log(Level.WARNING, "[LevelSystem] Failed to save player " + lp.getUUID(), e);
        }
    }

    private void saveSkillData(Connection con, LevelPlayer lp) throws SQLException {
        String sql = upsertSkillSQL();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (var entry : lp.getAllSkillData().entrySet()) {
                ps.setString(1, lp.getUUID().toString());
                ps.setString(2, entry.getKey());
                ps.setInt(3, entry.getValue().level);
                ps.setLong(4, entry.getValue().xp);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void delete(UUID uuid) {
        try (Connection con = dataSource.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM ls_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "[LevelSystem] Failed to delete player " + uuid, e);
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    @Override
    public boolean isHealthy() {
        try (Connection con = dataSource.getConnection()) {
            return con.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Dialect hooks ────────────────────────────────────────────────────

    /** SQL for upsert of player row – override per dialect */
    protected abstract String upsertPlayerSQL();

    /** SQL for upsert of skill row – override per dialect */
    protected abstract String upsertSkillSQL();
}
