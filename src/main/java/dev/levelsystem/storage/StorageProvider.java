package dev.levelsystem.storage;

import dev.levelsystem.api.LevelPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Abstraction over all persistence backends.
 * Implementations: {@link SQLiteStorage}, {@link MySQLStorage}.
 */
public interface StorageProvider {

    /** Initialize the storage backend (create tables, open connections, …). */
    void init();

    /** Gracefully shut down and close all connections. */
    void shutdown();

    /**
     * Load a player's data from the backing store.
     *
     * @return populated {@link LevelPlayer} or null if not found
     */
    @Nullable LevelPlayer load(UUID uuid);

    /**
     * Persist a player's current state.
     * Called asynchronously by the API.
     */
    void save(LevelPlayer player);

    /**
     * Delete all data for a player (GDPR / admin use).
     */
    void delete(UUID uuid);

    /**
     * Whether this backend is reachable / healthy.
     */
    default boolean isHealthy() { return true; }
}
