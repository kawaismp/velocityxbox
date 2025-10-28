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

public class LastServerCache {
    private static final long EXPIRY_MILLIS = 60 * 60 * 1000; // 1 hour
    private final File cacheFile;
    private final Gson gson = new Gson();
    private final Map<UUID, Entry> cache = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledSave = null;
    private static final long DEBOUNCE_DELAY_MS = 1000;

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
    }

    public synchronized void put(UUID uuid, String server) {
        cache.put(uuid, new Entry(server, System.currentTimeMillis()));
        debounceSave();
    }

    public synchronized String getIfValid(UUID uuid) {
        cleanUp();
        Entry entry = cache.get(uuid);
        if (entry != null && System.currentTimeMillis() - entry.timestamp < EXPIRY_MILLIS) {
            return entry.server;
        }
        return null;
    }

    public synchronized void remove(UUID uuid) {
        cache.remove(uuid);
        debounceSave();
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
        if (changed) debounceSave();
    }

    private void debounceSave() {
        synchronized (this) {
            if (scheduledSave != null && !scheduledSave.isDone()) {
                return;
            }
            scheduledSave = scheduler.schedule(this::save, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    // Optionally, call this on plugin shutdown to flush pending saves
    public void shutdown() {
        synchronized (this) {
            if (scheduledSave != null && !scheduledSave.isDone()) {
                scheduledSave.cancel(false);
                save();
            }
            scheduler.shutdown();
        }
    }

    private void load() {
        if (!cacheFile.exists()) return;
        try (FileReader reader = new FileReader(cacheFile)) {
            Type type = new TypeToken<Map<String, Entry>>(){}.getType();
            Map<String, Entry> raw = gson.fromJson(reader, type);
            if (raw != null) {
                for (Map.Entry<String, Entry> e : raw.entrySet()) {
                    cache.put(UUID.fromString(e.getKey()), e.getValue());
                }
            }
        } catch (IOException ignored) {}
    }

    private void save() {
        try (FileWriter writer = new FileWriter(cacheFile)) {
            Map<String, Entry> raw = new HashMap<>();
            for (Map.Entry<UUID, Entry> e : cache.entrySet()) {
                raw.put(e.getKey().toString(), e.getValue());
            }
            gson.toJson(raw, writer);
        } catch (IOException ignored) {}
    }
}
