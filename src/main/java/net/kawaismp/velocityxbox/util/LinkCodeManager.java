package net.kawaismp.velocityxbox.util;

import org.slf4j.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LinkCodeManager {
    private final Logger logger;
    private final Map<String, LinkEntry> codeToEntry;
    private final Map<String, String> usernameToCode;
    private final ScheduledExecutorService cleanupExecutor;
    private final int expirationMinutes;
    private final SecureRandom random;

    public LinkCodeManager(Logger logger, int expirationMinutes) {
        this.logger = logger;
        this.codeToEntry = new ConcurrentHashMap<>();
        this.usernameToCode = new ConcurrentHashMap<>();
        this.expirationMinutes = expirationMinutes;
        this.random = new SecureRandom();

        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "LinkCode-Cleanup");
            thread.setDaemon(true);
            return thread;
        });

        // Run cleanup every minute
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredCodes, 1, 1, TimeUnit.MINUTES);

        logger.info("LinkCodeManager initialized with {} minute expiration", expirationMinutes);
    }

    /**
     * Generate a random 6-digit verification code
     */
    private String generateCode() {
        // Generate 6-digit code (100000-999999)
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    /**
     * Create a new verification code for a player
     * Returns the generated code
     */
    public String createCode(String username) {
        // Remove old code if exists
        String oldCode = usernameToCode.get(username);
        if (oldCode != null) {
            codeToEntry.remove(oldCode);
        }

        // Generate unique code
        String code;
        do {
            code = generateCode();
        } while (codeToEntry.containsKey(code));

        // Store the code
        Instant expiration = Instant.now().plusSeconds(expirationMinutes * 60L);
        LinkEntry entry = new LinkEntry(username, expiration);
        codeToEntry.put(code, entry);
        usernameToCode.put(username, code);

        logger.debug("Generated link code {} for player {} (expires at {})", code, username, expiration);
        return code;
    }

    /**
     * Verify and consume a code
     * Returns the username if valid, empty if invalid/expired
     */
    public Optional<String> verifyAndConsume(String code) {
        LinkEntry entry = codeToEntry.get(code);

        if (entry == null) {
            logger.debug("Code {} not found", code);
            return Optional.empty();
        }

        if (Instant.now().isAfter(entry.expiration)) {
            logger.debug("Code {} has expired", code);
            // Clean up expired code
            codeToEntry.remove(code);
            usernameToCode.remove(entry.username);
            return Optional.empty();
        }

        // Valid code - remove it (one-time use)
        codeToEntry.remove(code);
        usernameToCode.remove(entry.username);

        logger.info("Verified link code {} for player {}", code, entry.username);
        return Optional.of(entry.username);
    }

    /**
     * Get the active code for a username (if exists and not expired)
     */
    public Optional<String> getActiveCode(String username) {
        String code = usernameToCode.get(username);
        if (code == null) {
            return Optional.empty();
        }

        LinkEntry entry = codeToEntry.get(code);
        if (entry == null || Instant.now().isAfter(entry.expiration)) {
            // Clean up if expired
            codeToEntry.remove(code);
            usernameToCode.remove(username);
            return Optional.empty();
        }

        return Optional.of(code);
    }

    /**
     * Remove a code for a specific username
     */
    public void removeCode(String username) {
        String code = usernameToCode.remove(username);
        if (code != null) {
            codeToEntry.remove(code);
            logger.debug("Removed link code for player {}", username);
        }
    }

    /**
     * Clean up expired codes
     */
    private void cleanupExpiredCodes() {
        Instant now = Instant.now();
        int removedCount = 0;

        for (Map.Entry<String, LinkEntry> entry : codeToEntry.entrySet()) {
            if (now.isAfter(entry.getValue().expiration)) {
                String code = entry.getKey();
                String username = entry.getValue().username;
                codeToEntry.remove(code);
                usernameToCode.remove(username);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            logger.debug("Cleaned up {} expired link codes", removedCount);
        }
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        logger.info("Shutting down LinkCodeManager...");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        codeToEntry.clear();
        usernameToCode.clear();
        logger.info("LinkCodeManager shut down successfully");
    }

    /**
     * Inner class to store link entry data
     */
    private record LinkEntry(String username, Instant expiration) {
    }
}

