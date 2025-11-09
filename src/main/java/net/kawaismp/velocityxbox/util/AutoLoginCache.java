package net.kawaismp.velocityxbox.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kawaismp.velocityxbox.database.model.Account;
import net.kawaismp.velocityxbox.event.PlayerLoginSuccessEvent;

/**
 * Caches auto-login data between GameProfileRequestEvent and ServerPostConnectEvent
 * This allows us to perform auto-login checks early but complete the login later
 * when we have access to the Player object for sending messages
 */
public class AutoLoginCache {
    private final Map<UUID, AutoLoginData> pendingAutoLogins;
    
    public AutoLoginCache() {
        this.pendingAutoLogins = new ConcurrentHashMap<>();
    }
    
    /**
     * Store auto-login data for a player
     * @param internalUuid The internal UUID that will be assigned to the player
     * @param account The account data
     * @param loginMethod The login method used
     * @param originalUuid The original UUID before login (for session caching)
     */
    public void store(UUID internalUuid, Account account, PlayerLoginSuccessEvent.LoginMethod loginMethod, UUID originalUuid) {
        AutoLoginData data = new AutoLoginData(account, loginMethod, originalUuid);
        pendingAutoLogins.put(internalUuid, data);
    }
    
    /**
     * Retrieve and remove auto-login data for a player
     * @param internalUuid The player's internal UUID
     * @return The auto-login data, or null if not found
     */
    public AutoLoginData retrieve(UUID internalUuid) {
        return pendingAutoLogins.remove(internalUuid);
    }
    
    /**
     * Check if a player has pending auto-login data
     * @param internalUuid The player's internal UUID
     * @return true if auto-login data exists
     */
    public boolean hasPendingAutoLogin(UUID internalUuid) {
        return pendingAutoLogins.containsKey(internalUuid);
    }
    
    /**
     * Remove auto-login data without retrieving it
     * @param internalUuid The player's internal UUID
     */
    public void remove(UUID internalUuid) {
        pendingAutoLogins.remove(internalUuid);
    }
    
    /**
     * Clear all pending auto-logins
     */
    public void clear() {
        pendingAutoLogins.clear();
    }
    
    /**
     * Data class to store auto-login information
     */
    public static class AutoLoginData {
        private final Account account;
        private final PlayerLoginSuccessEvent.LoginMethod loginMethod;
        private final UUID originalUuid;
        
        public AutoLoginData(Account account, PlayerLoginSuccessEvent.LoginMethod loginMethod, UUID originalUuid) {
            this.account = account;
            this.loginMethod = loginMethod;
            this.originalUuid = originalUuid;
        }
        
        public Account getAccount() {
            return account;
        }
        
        public PlayerLoginSuccessEvent.LoginMethod getLoginMethod() {
            return loginMethod;
        }
        
        public UUID getOriginalUuid() {
            return originalUuid;
        }
    }
}
