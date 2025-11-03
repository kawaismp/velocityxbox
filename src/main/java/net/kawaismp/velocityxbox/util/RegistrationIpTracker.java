package net.kawaismp.velocityxbox.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * Tracks IP addresses used for registration to prevent abuse
 */
public class RegistrationIpTracker {
    private static final int MAX_REGISTRATIONS_PER_WEEK = 1;
    private static final String IP_TRACKER_FILE = "registration_ips.json";
    
    private final Logger logger;
    private final File dataFile;
    private final Gson gson;
    private final Map<String, List<RegistrationRecord>> ipRegistrations;
    
    public RegistrationIpTracker(Logger logger, Path dataDirectory) {
        this.logger = logger;
        this.dataFile = new File(dataDirectory.toFile(), IP_TRACKER_FILE);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.ipRegistrations = new ConcurrentHashMap<>();
        
        loadFromFile();
    }
    
    /**
     * Check if an IP address can register a new account
     * @param ipAddress The IP address to check
     * @return true if the IP can register, false if limit exceeded
     */
    public boolean canRegister(InetAddress ipAddress) {
        String ip = ipAddress.getHostAddress();
        
        // Clean up old records first
        cleanupExpiredRecords(ip);
        
        List<RegistrationRecord> records = ipRegistrations.getOrDefault(ip, new ArrayList<>());
        
        // Count registrations in the last 7 days
        long weekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        long recentRegistrations = records.stream()
                .filter(record -> record.getTimestamp() >= weekAgo)
                .count();
        
        boolean canRegister = recentRegistrations < MAX_REGISTRATIONS_PER_WEEK;
        
        if (!canRegister) {
            logger.debug("IP {} has reached registration limit ({}/{})", ip, recentRegistrations, MAX_REGISTRATIONS_PER_WEEK);
        }
        
        return canRegister;
    }
    
    /**
     * Record a successful registration for an IP address
     * @param ipAddress The IP address that registered
     * @param username The username that was registered
     */
    public void recordRegistration(InetAddress ipAddress, String username) {
        String ip = ipAddress.getHostAddress();
        
        List<RegistrationRecord> records = ipRegistrations.computeIfAbsent(ip, k -> new ArrayList<>());
        records.add(new RegistrationRecord(username, System.currentTimeMillis()));
        
        logger.info("Recorded registration for IP {}: {}", ip, username);
        
        // Save to file
        saveToFile();
    }
    
    /**
     * Remove expired records (older than 7 days) for a specific IP
     */
    private void cleanupExpiredRecords(String ip) {
        List<RegistrationRecord> records = ipRegistrations.get(ip);
        if (records == null || records.isEmpty()) {
            return;
        }
        
        long weekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        List<RegistrationRecord> validRecords = records.stream()
                .filter(record -> record.getTimestamp() >= weekAgo)
                .collect(Collectors.toList());
        
        if (validRecords.isEmpty()) {
            ipRegistrations.remove(ip);
        } else if (validRecords.size() < records.size()) {
            ipRegistrations.put(ip, validRecords);
        }
    }
    
    /**
     * Perform global cleanup of all expired records
     */
    public void performGlobalCleanup() {
        long weekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        int removedIps = 0;
        int removedRecords = 0;
        
        List<String> ipsToRemove = new ArrayList<>();
        
        for (Map.Entry<String, List<RegistrationRecord>> entry : ipRegistrations.entrySet()) {
            List<RegistrationRecord> validRecords = entry.getValue().stream()
                    .filter(record -> record.getTimestamp() >= weekAgo)
                    .collect(Collectors.toList());
            
            int originalSize = entry.getValue().size();
            removedRecords += (originalSize - validRecords.size());
            
            if (validRecords.isEmpty()) {
                ipsToRemove.add(entry.getKey());
            } else if (validRecords.size() < originalSize) {
                ipRegistrations.put(entry.getKey(), validRecords);
            }
        }
        
        for (String ip : ipsToRemove) {
            ipRegistrations.remove(ip);
            removedIps++;
        }
        
        if (removedIps > 0 || removedRecords > 0) {
            logger.info("Cleaned up {} expired registration records from {} IPs", removedRecords, removedIps);
            saveToFile();
        }
    }
    
    /**
     * Load registration data from file
     */
    private void loadFromFile() {
        if (!dataFile.exists()) {
            logger.info("No existing IP registration data found, starting fresh");
            return;
        }
        
        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, List<RegistrationRecord>>>(){}.getType();
            Map<String, List<RegistrationRecord>> loaded = gson.fromJson(reader, type);
            
            if (loaded != null) {
                ipRegistrations.putAll(loaded);
                logger.info("Loaded registration data for {} IP addresses", ipRegistrations.size());
            }
        } catch (IOException e) {
            logger.error("Failed to load IP registration data", e);
        }
    }
    
    /**
     * Save registration data to file
     */
    private void saveToFile() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(ipRegistrations, writer);
        } catch (IOException e) {
            logger.error("Failed to save IP registration data", e);
        }
    }
    
    /**
     * Get statistics about tracked IPs
     */
    public String getStatistics() {
        int totalIps = ipRegistrations.size();
        int totalRecords = ipRegistrations.values().stream()
                .mapToInt(List::size)
                .sum();
        
        return String.format("Tracking %d IPs with %d total registration records", totalIps, totalRecords);
    }
    
    /**
     * Shutdown and save data
     */
    public void shutdown() {
        performGlobalCleanup();
        saveToFile();
        logger.info("RegistrationIpTracker shut down successfully");
    }
    
    /**
     * Data class for registration record
     */
    public static class RegistrationRecord {
        private final String username;
        private final long timestamp;
        
        public RegistrationRecord(String username, long timestamp) {
            this.username = username;
            this.timestamp = timestamp;
        }
        
        public String getUsername() {
            return username;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}
