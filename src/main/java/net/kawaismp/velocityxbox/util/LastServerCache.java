package net.kawaismp.velocityxbox.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe cache for tracking player's last connected server with automatic expiry and persistence.
 */
public class LastServerCache {
    private static final Logger LOG = Logger.getLogger(LastServerCache.class.getName());
    private static final long EXPIRY_MS = TimeUnit.MINUTES.toMillis(45);
    private static final long SAVE_DELAY_MS = 1000;

    private final Path cacheFile;
    private final Path tempFile;
    private final Gson gson;
    private final ConcurrentHashMap<UUID, Entry> cache;
    private final ScheduledExecutorService executor;
    private final AtomicReference<ScheduledFuture<?>> pendingSave;
    private final AtomicBoolean dirty;
    private final AtomicBoolean shutdown;

    private static class Entry {
        final String server;
        final long timestamp;

        Entry(String server, long timestamp) {
            this.server = server;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp >= EXPIRY_MS;
        }
    }

    public LastServerCache(Path dataDirectory) {
        this.cacheFile = dataDirectory.resolve("last_server_cache.json");
        this.tempFile = dataDirectory.resolve("last_server_cache.json.tmp");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.cache = new ConcurrentHashMap<>();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LastServerCache");
            t.setDaemon(true);
            return t;
        });
        this.pendingSave = new AtomicReference<>();
        this.dirty = new AtomicBoolean(false);
        this.shutdown = new AtomicBoolean(false);

        load();
        scheduleCleanup();
    }

    /**
     * Records the server a player connected to.
     */
    public void put(UUID uuid, String server) {
        if (shutdown.get()) return;

        cache.put(uuid, new Entry(server, System.currentTimeMillis()));
        scheduleSave();
    }

    /**
     * Gets the last server for a player if not expired.
     * @return server name or null if expired/not found
     */
    public String get(UUID uuid) {
        Entry entry = cache.get(uuid);
        return (entry != null && !entry.isExpired()) ? entry.server : null;
    }

    /**
     * Removes a player's cached server.
     */
    public void remove(UUID uuid) {
        if (shutdown.get()) return;

        if (cache.remove(uuid) != null) {
            scheduleSave();
        }
    }

    /**
     * Flushes pending changes and shuts down the cache.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) return;

        // Cancel pending save and flush immediately
        ScheduledFuture<?> future = pendingSave.getAndSet(null);
        if (future != null) future.cancel(false);

        if (dirty.getAndSet(false)) {
            save();
        }

        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void scheduleSave() {
        dirty.set(true);

        // Replace any pending save with a new one (debouncing)
        ScheduledFuture<?> oldFuture = pendingSave.getAndSet(
                executor.schedule(this::saveIfDirty, SAVE_DELAY_MS, TimeUnit.MILLISECONDS)
        );

        if (oldFuture != null) {
            oldFuture.cancel(false);
        }
    }

    private void saveIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            save();
        }
    }

    private void save() {
        if (shutdown.get()) return;

        try {
            // Build map with only valid entries
            Map<String, Entry> data = new ConcurrentHashMap<>();
            cache.forEach((uuid, entry) -> {
                if (!entry.isExpired()) {
                    data.put(uuid.toString(), entry);
                }
            });

            // Write to temp file first (atomic write)
            String json = gson.toJson(data);
            Files.writeString(tempFile, json);
            Files.move(tempFile, cacheFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to save cache", e);
        }
    }

    private void load() {
        if (!Files.exists(cacheFile)) return;

        try {
            String json = Files.readString(cacheFile);
            Type type = new TypeToken<Map<String, Entry>>(){}.getType();
            Map<String, Entry> data = gson.fromJson(json, type);

            if (data != null) {
                data.forEach((uuidStr, entry) -> {
                    try {
                        if (entry != null && !entry.isExpired()) {
                            cache.put(UUID.fromString(uuidStr), entry);
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.warning("Invalid UUID in cache: " + uuidStr);
                    }
                });
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load cache", e);
        }
    }

    private void cleanup() {
        if (shutdown.get()) return;

        cache.entrySet().removeIf(e -> e.getValue().isExpired());

        // Save after cleanup to persist the removal
        if (dirty.compareAndSet(false, true)) {
            save();
            dirty.set(false);
        }
    }

    private void scheduleCleanup() {
        executor.scheduleAtFixedRate(() -> {
            try {
                cleanup();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Cleanup failed", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
}