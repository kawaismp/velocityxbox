package net.kawaismp.velocityxbox.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks original player connection data before profile randomization
 * Uses a two-stage approach:
 * 1. Temporarily stores by random username during GameProfileRequestEvent
 * 2. Transfers to internal UUID mapping when player actually connects
 */
public class PlayerConnectionData {
    // Temporary storage: randomUsername -> ConnectionInfo (before player connects)
    private final Map<String, ConnectionInfo> pendingConnections;
    
    // Permanent storage: internalUuid -> ConnectionInfo (after player connects)
    private final Map<UUID, ConnectionInfo> internalToOriginal;
    private final Map<UUID, UUID> originalToInternal;
    
    public PlayerConnectionData() {
        this.pendingConnections = new ConcurrentHashMap<>();
        this.internalToOriginal = new ConcurrentHashMap<>();
        this.originalToInternal = new ConcurrentHashMap<>();
    }
    
    /**
     * Store pending connection info during GameProfileRequestEvent
     * @param randomUsername The randomized username that will be used temporarily
     * @param originalUuid The original UUID from GameProfileRequestEvent
     * @param originalUsername The original username before randomization
     * @param protocolVersion The player's protocol version
     */
    public void storePending(String randomUsername, UUID originalUuid, String originalUsername, int protocolVersion) {
        TimestampedConnectionInfo info = new TimestampedConnectionInfo(originalUuid, originalUsername, protocolVersion, System.currentTimeMillis());
        pendingConnections.put(randomUsername, info);
    }
    
    /**
     * Transfer pending connection to permanent storage using actual internal UUID
     * Call this in ServerPostConnectEvent when you have the actual Player object
     * @param internalUuid The player's actual internal UUID
     * @param currentUsername The player's current username (randomized)
     * @return true if transfer was successful, false if no pending connection found
     */
    public boolean transferFromPending(UUID internalUuid, String currentUsername) {
        ConnectionInfo info = pendingConnections.remove(currentUsername);
        if (info != null) {
            internalToOriginal.put(internalUuid, info);
            originalToInternal.put(info.originalUuid, internalUuid);
            return true;
        }
        return false;
    }
    
    /**
     * Store connection info directly (for cases where you already have internal UUID)
     * @param internalUuid The player's internal UUID
     * @param originalUuid The original UUID
     * @param originalUsername The original username
     * @param protocolVersion The player's protocol version
     */
    public void store(UUID internalUuid, UUID originalUuid, String originalUsername, int protocolVersion) {
        ConnectionInfo info = new ConnectionInfo(originalUuid, originalUsername, protocolVersion);
        internalToOriginal.put(internalUuid, info);
        originalToInternal.put(originalUuid, internalUuid);
    }
    
    /**
     * Get original UUID from internal UUID
     */
    public UUID getOriginalUuid(UUID internalUuid) {
        ConnectionInfo info = internalToOriginal.get(internalUuid);
        return info != null ? info.originalUuid : null;
    }
    
    /**
     * Get internal UUID from original UUID
     */
    public UUID getInternalUuid(UUID originalUuid) {
        return originalToInternal.get(originalUuid);
    }
    
    /**
     * Get original username from internal UUID
     */
    public String getOriginalUsername(UUID internalUuid) {
        ConnectionInfo info = internalToOriginal.get(internalUuid);
        return info != null ? info.originalUsername : null;
    }
    
    /**
     * Get protocol version from internal UUID
     */
    public Integer getProtocolVersion(UUID internalUuid) {
        ConnectionInfo info = internalToOriginal.get(internalUuid);
        return info != null ? info.protocolVersion : null;
    }
    
    /**
     * Get full connection info
     */
    public ConnectionInfo getConnectionInfo(UUID internalUuid) {
        return internalToOriginal.get(internalUuid);
    }
    
    /**
     * Remove connection data (called on disconnect)
     */
    public void remove(UUID internalUuid) {
        ConnectionInfo info = internalToOriginal.remove(internalUuid);
        if (info != null) {
            originalToInternal.remove(info.originalUuid);
        }
    }
    
    /**
     * Check if internal UUID is tracked
     */
    public boolean hasInternalUuid(UUID internalUuid) {
        return internalToOriginal.containsKey(internalUuid);
    }
    
    /**
     * Check if original UUID is tracked
     */
    public boolean hasOriginalUuid(UUID originalUuid) {
        return originalToInternal.containsKey(originalUuid);
    }
    
    /**
     * Clear all data
     */
    public void clear() {
        pendingConnections.clear();
        internalToOriginal.clear();
        originalToInternal.clear();
    }
    
    /**
     * Clean up stale pending connections (optional, for safety)
     * Can be called periodically to remove connections that never completed
     */
    /**
     * Clean up stale pending connections older than the given threshold (in milliseconds).
     * @param staleThresholdMillis Entries older than this threshold will be removed.
     * @return number of entries actually removed
     */
    public int cleanupStalePending(long staleThresholdMillis) {
        long now = System.currentTimeMillis();
        int removed = 0;
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, ConnectionInfo> entry : pendingConnections.entrySet()) {
            if (entry.getValue() instanceof TimestampedConnectionInfo info) {
                if (now - info.getTimestamp() > staleThresholdMillis) {
                    keysToRemove.add(entry.getKey());
                }
            }
        }
        for (String key : keysToRemove) {
            pendingConnections.remove(key);
            removed++;
        }
        return removed;
    }
    
    /**
     * Data class to store connection information
     */
    public static class ConnectionInfo {
        private final UUID originalUuid;
        private final String originalUsername;
        private final int protocolVersion;
        
        public ConnectionInfo(UUID originalUuid, String originalUsername, int protocolVersion) {
            this.originalUuid = originalUuid;
            this.originalUsername = originalUsername;
            this.protocolVersion = protocolVersion;
        }
        
        public UUID getOriginalUuid() {
            return originalUuid;
        }
        
        public String getOriginalUsername() {
            return originalUsername;
        }
        
        public int getProtocolVersion() {
            return protocolVersion;
        }
    }

    /**
     * Extension of ConnectionInfo that includes a timestamp for stale cleanup.
     */
    public static class TimestampedConnectionInfo extends ConnectionInfo {
        private final long timestamp;

        public TimestampedConnectionInfo(UUID originalUuid, String originalUsername, int protocolVersion, long timestamp) {
            super(originalUuid, originalUsername, protocolVersion);
            this.timestamp = timestamp;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
