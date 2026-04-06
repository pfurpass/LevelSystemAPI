package dev.levelsystem.storage;

import dev.levelsystem.api.LevelPlayer;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional Redis layer for:
 * <ul>
 *   <li>Fast read cache (player data stored as hash)</li>
 *   <li>Cross-server pub/sub invalidation</li>
 * </ul>
 *
 * Wraps an existing {@link StorageProvider} and falls back to it on cache miss.
 */
public class RedisCache implements StorageProvider {

    private static final String CHANNEL = "levelsystem:invalidate";

    private final StorageProvider delegate;
    private final Logger log;
    private final String prefix;

    private JedisPool pool;
    private Thread subscriberThread;

    public RedisCache(StorageProvider delegate, Logger log, FileConfiguration config) {
        this.delegate = delegate;
        this.log = log;
        this.prefix = config.getString("storage.redis.key-prefix", "levelsystem:");
    }

    @Override
    public void init() {
        delegate.init();
        // Redis connection will be initialized by LevelPlugin after config check
    }

    public void connectRedis(FileConfiguration config) {
        String host = config.getString("storage.redis.host", "localhost");
        int port    = config.getInt("storage.redis.port", 6379);
        String pass = config.getString("storage.redis.password", "");

        JedisPoolConfig poolCfg = new JedisPoolConfig();
        poolCfg.setMaxTotal(10);

        if (pass == null || pass.isEmpty()) {
            pool = new JedisPool(poolCfg, host, port, 2000);
        } else {
            pool = new JedisPool(poolCfg, host, port, 2000, pass);
        }

        // Start pub/sub listener for cross-server invalidation
        subscriberThread = new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        // Another server saved a player – invalidate our local cache key
                        // (The API's in-memory map handles this separately; here we just
                        //  remove the Redis hash so the next read fetches fresh from DB)
                        try (Jedis inner = pool.getResource()) {
                            inner.del(prefix + "player:" + message);
                        }
                    }
                }, CHANNEL);
            } catch (Exception e) {
                log.log(Level.WARNING, "[LevelSystem] Redis subscriber error", e);
            }
        }, "LevelSystem-Redis-Sub");
        subscriberThread.setDaemon(true);
        subscriberThread.start();

        log.info("[LevelSystem] Redis connected: " + host + ":" + port);
    }

    @Override
    public LevelPlayer load(UUID uuid) {
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                String key = prefix + "player:" + uuid;
                String level = jedis.hget(key, "level");
                if (level != null) {
                    String name = jedis.hget(key, "name");
                    long xp = Long.parseLong(jedis.hget(key, "xp"));
                    return new LevelPlayer(uuid, name, Integer.parseInt(level), xp);
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "[LevelSystem] Redis read error for " + uuid, e);
            }
        }
        // Cache miss → delegate to SQL
        LevelPlayer lp = delegate.load(uuid);
        if (lp != null && pool != null) cachePlayer(lp);
        return lp;
    }

    @Override
    public void save(LevelPlayer lp) {
        delegate.save(lp);
        if (pool != null) {
            cachePlayer(lp);
            // Publish invalidation to other servers
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(CHANNEL, lp.getUUID().toString());
            } catch (Exception e) {
                log.log(Level.WARNING, "[LevelSystem] Redis publish error", e);
            }
        }
    }

    @Override
    public void delete(UUID uuid) {
        delegate.delete(uuid);
        if (pool != null) {
            try (Jedis jedis = pool.getResource()) {
                jedis.del(prefix + "player:" + uuid);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void shutdown() {
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null) pool.close();
        delegate.shutdown();
    }

    @Override
    public boolean isHealthy() {
        if (pool == null) return delegate.isHealthy();
        try (Jedis jedis = pool.getResource()) {
            return "PONG".equals(jedis.ping()) && delegate.isHealthy();
        } catch (Exception e) {
            return false;
        }
    }

    // ── Private ───────────────────────────────────────────────────────────

    private void cachePlayer(LevelPlayer lp) {
        try (Jedis jedis = pool.getResource()) {
            String key = prefix + "player:" + lp.getUUID();
            jedis.hset(key, "name", lp.getName());
            jedis.hset(key, "level", String.valueOf(lp.getLevel()));
            jedis.hset(key, "xp", String.valueOf(lp.getXP()));
            jedis.expire(key, 300); // 5-minute TTL
        } catch (Exception e) {
            log.log(Level.WARNING, "[LevelSystem] Redis write error", e);
        }
    }
}
