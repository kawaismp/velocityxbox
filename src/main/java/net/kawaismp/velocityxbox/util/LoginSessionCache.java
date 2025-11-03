package net.kawaismp.velocityxbox.util;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

/**
 * Manages login sessions for players with automatic expiration
 */
public class LoginSessionCache {
    private static final long SESSION_EXPIRATION_MINUTES = 10;
    
    private final Logger logger;
    private final Map<UUID, SessionData> activeSessions;
    private final Map<UUID, Long> sessionExpirations;
    private final ScheduledExecutorService scheduler;
    
    public LoginSessionCache(Logger logger) {
        this.logger = logger;
        this.activeSessions = new ConcurrentHashMap<>();
        this.sessionExpirations = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        // Start cleanup task for expired sessions
        startCleanupTask();
    }
    
    /**
     * Create or update a login session
     */
    public void createSession(UUID originalUuid, String username, int protocolVersion, InetAddress ipAddress, String accountId, boolean onlineMode) {
        SessionData sessionData = new SessionData(
            username, 
            protocolVersion, 
            ipAddress, 
            accountId,
            onlineMode,
            System.currentTimeMillis()
        );
        
        activeSessions.put(originalUuid, sessionData);
        sessionExpirations.remove(originalUuid); // Remove expiration if it exists
        
        logger.debug("Created login session for {} (UUID: {}, onlineMode: {})", username, originalUuid, onlineMode);
    }
    
    /**
     * Mark session for expiration (called on disconnect)
     */
    public void markForExpiration(UUID originalUuid) {
        if (activeSessions.containsKey(originalUuid)) {
            long expirationTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(SESSION_EXPIRATION_MINUTES);
            sessionExpirations.put(originalUuid, expirationTime);
            
            logger.debug("Marked session for expiration: {} (expires in {} minutes)", originalUuid, SESSION_EXPIRATION_MINUTES);
        }
    }
    
    /**
     * Validate and retrieve session if still valid
     * Returns null if session doesn't exist, is expired, or validation fails
     */
    public SessionData validateSession(UUID originalUuid, int protocolVersion, InetAddress ipAddress, boolean onlineMode) {
        SessionData session = activeSessions.get(originalUuid);
        
        if (session == null) {
            return null;
        }
        
        // Check if session is marked for expiration and expired
        Long expirationTime = sessionExpirations.get(originalUuid);
        if (expirationTime != null && System.currentTimeMillis() > expirationTime) {
            removeSession(originalUuid);
            logger.debug("Session expired for UUID: {}", originalUuid);
            return null;
        }
        
        // Validate online mode
        if (session.isOnlineMode() != onlineMode) {
            logger.debug("Online mode mismatch for UUID: {} (expected: {}, got: {})", originalUuid, session.isOnlineMode(), onlineMode);
            return null;
        }
        
        // Validate protocol version
        if (session.getProtocolVersion() != protocolVersion) {
            logger.debug("Protocol version mismatch for UUID: {} (expected: {}, got: {})", originalUuid, session.getProtocolVersion(), protocolVersion);
            return null;
        }
        
        // Validate IP address
        if (!session.getIpAddress().equals(ipAddress)) {
            logger.debug("IP address mismatch for UUID: {} (expected: {}, got: {})", originalUuid, session.getIpAddress(), ipAddress);
            return null;
        }
        
        // Session is valid, remove expiration marker
        sessionExpirations.remove(originalUuid);
        
        return session;
    }
    
    /**
     * Remove a session completely
     */
    public void removeSession(UUID originalUuid) {
        activeSessions.remove(originalUuid);
        sessionExpirations.remove(originalUuid);
        logger.debug("Removed session for UUID: {}", originalUuid);
    }
    
    /**
     * Check if a session exists (regardless of expiration status)
     */
    public boolean hasSession(UUID originalUuid) {
        return activeSessions.containsKey(originalUuid);
    }
    
    /**
     * Get session data without validation
     */
    public SessionData getSession(UUID originalUuid) {
        return activeSessions.get(originalUuid);
    }
    
    /**
     * Start periodic cleanup task for expired sessions
     */
    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long currentTime = System.currentTimeMillis();
                int cleanedCount = 0;
                // Collect expired UUIDs first
                java.util.List<UUID> expiredUuids = new java.util.ArrayList<>();
                for (Map.Entry<UUID, Long> entry : sessionExpirations.entrySet()) {
                    if (currentTime > entry.getValue()) {
                        expiredUuids.add(entry.getKey());
                    }
                }
                // Remove expired sessions and increment counter only once per UUID
                for (UUID uuid : expiredUuids) {
                    boolean removedSession = activeSessions.remove(uuid) != null;
                    boolean removedExpiration = sessionExpirations.remove(uuid) != null;
                    if (removedSession || removedExpiration) {
                        cleanedCount++;
                    }
                }
                if (cleanedCount > 0) {
                    logger.debug("Cleaned up {} expired session(s)", cleanedCount);
                }
            } catch (Exception e) {
                logger.error("Error during session cleanup", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    /**
     * Shutdown the session cache
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        activeSessions.clear();
        sessionExpirations.clear();
        logger.info("LoginSessionCache shut down successfully");
    }
    
    /**
     * Get statistics about current sessions
     */
    public String getStatistics() {
        int total = activeSessions.size();
        int active = total - sessionExpirations.size();
        int expiring = sessionExpirations.size();
        
        return String.format("Sessions: %d total (%d active, %d expiring)", total, active, expiring);
    }
    
    /**
     * Data class to store session information
     */
    public static class SessionData {
        private final String username;
        private final int protocolVersion;
        private final InetAddress ipAddress;
        private final String accountId;
        private final boolean onlineMode;
        private final long createdAt;
        
        public SessionData(String username, int protocolVersion, InetAddress ipAddress, String accountId, boolean onlineMode, long createdAt) {
            this.username = username;
            this.protocolVersion = protocolVersion;
            this.ipAddress = ipAddress;
            this.accountId = accountId;
            this.onlineMode = onlineMode;
            this.createdAt = createdAt;
        }
        
        public String getUsername() {
            return username;
        }
        
        public int getProtocolVersion() {
            return protocolVersion;
        }
        
        public InetAddress getIpAddress() {
            return ipAddress;
        }
        
        public String getAccountId() {
            return accountId;
        }
        
        public boolean isOnlineMode() {
            return onlineMode;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
    }
}
