package net.kawaismp.velocityxbox.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LastServerCache {
    private static final Logger LOGGER = Logger.getLogger(LastServerCache.class.getName());
    private static final long EXPIRY_MILLIS = 60 * 60 * 1000; // 1 hour
    private static final long DEBOUNCE_DELAY_MS = 1000;

    private final File cacheFile;
    private final Gson gson = new Gson();
    private final Map<UUID, Entry> cache = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "LastServerCache-Saver");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<ScheduledFuture<?>> scheduledSave = new AtomicReference<>();
    private final Map<UUID, Entry> pendingChanges = new HashMap<>();
    private final Map<UUID, Boolean> pendingRemovals = new HashMap<>();
    private volatile boolean isShutdown = false;
    private long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private static class Entry {
        String server;
        long timestamp;

        Entry(String server, long timestamp) {
            this.server = server;
            this.timestamp = timestamp;
        }
    }

    public LastServerCache(Path dataDirectory) {
        this.cacheFile = new File(dataDirectory.toFile(), "last_server_cache.json");
        load();
        schedulePeriodicCleanup();
    }

    public synchronized void put(UUID uuid, String server) {
        if (isShutdown) {
            LOGGER.warning("Attempted to put entry after shutdown");
            return;
        }
        pendingChanges.put(uuid, new Entry(server, System.currentTimeMillis()));
        pendingRemovals.remove(uuid);
        scheduleSave();
    }

    public synchronized String getIfValid(UUID uuid) {
        periodicCleanupIfNeeded();
        Entry entry = cache.get(uuid);
        if (entry != null && System.currentTimeMillis() - entry.timestamp < EXPIRY_MILLIS) {
            return entry.server;
        }
        return null;
    }

    public synchronized void remove(UUID uuid) {
        if (isShutdown) {
            LOGGER.warning("Attempted to remove entry after shutdown");
            return;
        }
        pendingRemovals.put(uuid, true);
        pendingChanges.remove(uuid);
        scheduleSave();
    }

    private synchronized void applyPendingChanges() {
        // Apply removals first
        for (UUID uuid : pendingRemovals.keySet()) {
            cache.remove(uuid);
        }
        pendingRemovals.clear();

        // Apply puts
        for (Map.Entry<UUID, Entry> e : pendingChanges.entrySet()) {
            cache.put(e.getKey(), e.getValue());
        }
        pendingChanges.clear();
    }

    private void scheduleSave() {
        if (isShutdown) {
            return;
        }

        // Cancel existing scheduled save and schedule a new one (reset debounce timer)
        ScheduledFuture<?> oldFuture = scheduledSave.get();
        if (oldFuture != null && !oldFuture.isDone()) {
            oldFuture.cancel(false);
        }

        try {
            ScheduledFuture<?> newFuture = scheduler.schedule(() -> {
                try {
                    synchronized (LastServerCache.this) {
                        if (!isShutdown) {
                            applyPendingChanges();
                            save();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error during scheduled save", e);
                }
            }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);

            scheduledSave.set(newFuture);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to schedule save", e);
            // Fallback: try to save immediately
            synchronized (this) {
                applyPendingChanges();
                save();
            }
        }
    }

    public void shutdown() {
        synchronized (this) {
            if (isShutdown) {
                return;
            }
            isShutdown = true;

            // Cancel scheduled save and flush immediately
            ScheduledFuture<?> future = scheduledSave.get();
            if (future != null && !future.isDone()) {
                future.cancel(false);
            }

            // Apply any pending changes
            applyPendingChanges();
            save();
        }

        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void load() {
        if (!cacheFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(cacheFile)) {
            Type type = new TypeToken<Map<String, Entry>>(){}.getType();
            Map<String, Entry> raw = gson.fromJson(reader, type);

            if (raw != null) {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, Entry> e : raw.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(e.getKey());
                        Entry entry = e.getValue();
                        // Only load non-expired entries
                        if (entry != null && now - entry.timestamp < EXPIRY_MILLIS) {
                            cache.put(uuid, entry);
                        }
                    } catch (IllegalArgumentException ex) {
                        LOGGER.warning("Invalid UUID in cache file: " + e.getKey());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load cache file", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error loading cache", e);
        }
    }

    private void save() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            Map<String, Entry> raw = new HashMap<>();
            long now = System.currentTimeMillis();

            // Only save non-expired entries
            for (Map.Entry<UUID, Entry> e : cache.entrySet()) {
                if (now - e.getValue().timestamp < EXPIRY_MILLIS) {
                    raw.put(e.getKey().toString(), e.getValue());
                }
            }

            gson.toJson(raw, writer);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save cache file", e);
        }
    }

    private synchronized void cleanUp() {
        Iterator<Map.Entry<UUID, Entry>> it = cache.entrySet().iterator();
        long now = System.currentTimeMillis();
        boolean changed = false;

        while (it.hasNext()) {
            Map.Entry<UUID, Entry> e = it.next();
            if (now - e.getValue().timestamp >= EXPIRY_MILLIS) {
                it.remove();
                changed = true;
            }
        }

        // Save immediately if entries were removed during cleanup
        if (changed) {
            save();
        }

        lastCleanupTime = now;
    }

    private void periodicCleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime >= CLEANUP_INTERVAL_MS) {
            cleanUp();
        }
    }

    private void schedulePeriodicCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                synchronized (LastServerCache.this) {
                    if (!isShutdown) {
                        cleanUp();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during periodic cleanup", e);
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }
}