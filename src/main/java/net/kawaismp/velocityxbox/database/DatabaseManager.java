package net.kawaismp.velocityxbox.database;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import com.velocitypowered.api.util.UuidUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kawaismp.velocityxbox.config.ConfigManager;
import net.kawaismp.velocityxbox.database.model.Account;
import org.slf4j.Logger;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DatabaseManager {
    private static final String QUERY_GET_ACCOUNT = "SELECT id, username, password_hash, xbox_user_id, discord_id FROM accounts WHERE LOWER(username) = LOWER(?)";
    private static final String QUERY_GET_ACCOUNT_BY_ID = "SELECT id, username, password_hash, xbox_user_id, discord_id FROM accounts WHERE id = ?";
    private static final String QUERY_GET_ACCOUNT_BY_XUID = "SELECT id, username, discord_id FROM accounts WHERE xbox_user_id = ?";
    private static final String QUERY_GET_ACCOUNT_BY_DISCORD_ID = "SELECT id, username, password_hash, xbox_user_id, discord_id FROM accounts WHERE discord_id = ?";
    private static final String QUERY_COUNT_ACCOUNTS_BY_DISCORD_ID = "SELECT COUNT(*) FROM accounts WHERE discord_id = ?";
    private static final String QUERY_UPDATE_XBOX_LINK = "UPDATE accounts SET xbox_user_id = ? WHERE id = ?";
    private static final String QUERY_UNLINK_XBOX = "UPDATE accounts SET xbox_user_id = NULL WHERE id = ?";
    private static final String QUERY_UPDATE_DISCORD_LINK = "UPDATE accounts SET discord_id = ? WHERE id = ?";
    private static final String QUERY_CREATE_ACCOUNT = "INSERT INTO accounts (id, username, password_hash, discord_id) VALUES (?, ?, ?, ?) RETURNING id, username, password_hash, xbox_user_id, discord_id";

    private final Logger logger;
    private final ExecutorService executor;
    private HikariDataSource dataSource;

    public DatabaseManager(VelocityXbox plugin, ConfigManager config) {
        this.logger = plugin.getLogger();
        this.executor = Executors.newFixedThreadPool(
                4,
                r -> {
                    Thread thread = new Thread(r, "VelocityXbox-DB-Worker");
                    thread.setDaemon(true);
                    return thread;
                }
        );

        initializeDataSource(config);
        testConnection();
    }

    private void initializeDataSource(ConfigManager config) {
        try {
            logger.info("Initializing database connection pool...");

            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
            hikariConfig.addDataSourceProperty("serverName", config.getDatabaseHost());
            hikariConfig.addDataSourceProperty("portNumber", config.getDatabasePort());
            hikariConfig.addDataSourceProperty("databaseName", config.getDatabaseName());
            hikariConfig.addDataSourceProperty("applicationName", "VelocityXbox");
            hikariConfig.addDataSourceProperty("tcpKeepAlive", true);

            hikariConfig.setUsername(config.getDatabaseUser());
            hikariConfig.setPassword(config.getDatabasePassword());

            // Connection pool settings
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setConnectionTimeout(30000);
            hikariConfig.setIdleTimeout(600000);
            hikariConfig.setMaxLifetime(1800000);
            hikariConfig.setPoolName("VelocityXbox-HikariPool");

            // Performance settings
            hikariConfig.addDataSourceProperty("prepareThreshold", 3);
            hikariConfig.addDataSourceProperty("preparedStatementCacheQueries", 256);
            hikariConfig.addDataSourceProperty("preparedStatementCacheSizeMiB", 5);

            this.dataSource = new HikariDataSource(hikariConfig);

            logger.info("Database connection pool initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize database connection pool", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void testConnection() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                logger.info("Database connection test successful");
            } else {
                throw new SQLException("Connection validation failed");
            }
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            throw new RuntimeException("Database connection test failed", e);
        }
    }

    /**
     * Get account by username (async)
     */
    public CompletableFuture<Optional<Account>> getAccountByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_GET_ACCOUNT)) {

                stmt.setString(1, username);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(
                                rs.getString("id"),
                                rs.getString("username"),
                                rs.getString("password_hash"),
                                rs.getString("xbox_user_id"),
                                rs.getString("discord_id")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching account by username: {}", username, e);
                throw new RuntimeException("Database query failed", e);
            }
            return Optional.empty();
        }, executor);
    }

    /**
     * Get account by account ID (async)
     */
    public CompletableFuture<Optional<Account>> getAccountById(String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_GET_ACCOUNT_BY_ID)) {

                stmt.setObject(1, UUID.fromString(accountId));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(
                                rs.getString("id"),
                                rs.getString("username"),
                                rs.getString("password_hash"),
                                rs.getString("xbox_user_id"),
                                rs.getString("discord_id")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching account by ID: {}", accountId, e);
                throw new RuntimeException("Database query failed", e);
            }
            return Optional.empty();
        }, executor);
    }

    /**
     * Get account by Xbox User ID (async)
     */
    public CompletableFuture<Optional<Account>> getAccountByXuid(String xuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_GET_ACCOUNT_BY_XUID)) {

                stmt.setLong(1, Long.parseLong(xuid));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(
                                rs.getString("id"),
                                rs.getString("username"),
                                null,
                                xuid,
                                rs.getString("discord_id")
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching account by XUID: {}", xuid, e);
                throw new RuntimeException("Database query failed", e);
            }
            return Optional.empty();
        }, executor);
    }

    /**
     * Check if account has linked Xbox account (async)
     */
    public CompletableFuture<Boolean> hasLinkedXbox(String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_GET_ACCOUNT_BY_ID)) {

                stmt.setObject(1, UUID.fromString(accountId));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String xboxUserId = rs.getString("xbox_user_id");
                        return xboxUserId != null && !xboxUserId.isEmpty();
                    }
                }
            } catch (SQLException e) {
                logger.error("Error checking Xbox link status for account: {}", accountId, e);
                throw new RuntimeException("Database query failed", e);
            }
            return false;
        }, executor);
    }

    /**
     * Link Xbox account (async)
     */
    public CompletableFuture<Boolean> linkXboxAccount(String accountId, String xuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_XBOX_LINK)) {

                stmt.setLong(1, Long.parseLong(xuid));
                stmt.setObject(2, UUID.fromString(accountId));

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("Error linking Xbox account {} to {}", xuid, accountId, e);
                throw new RuntimeException("Database update failed", e);
            }
        }, executor);
    }

    /**
     * Unlink Xbox account (async)
     */
    public CompletableFuture<Boolean> unlinkXboxAccount(String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_UNLINK_XBOX)) {

                stmt.setObject(1, UUID.fromString(accountId));

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("Error unlinking Xbox account for: {}", accountId, e);
                throw new RuntimeException("Database update failed", e);
            }
        }, executor);
    }

    /**
     * Create a new account with raw password (async)
     * This method hashes the password and generates a new UUID
     */
    public CompletableFuture<Account> createAccount(String username, String rawPassword) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate new UUID for account
                UUID accountId = UuidUtils.generateOfflinePlayerUuid(username);

                // Hash the password using Argon2
                String passwordHash = hashPassword(rawPassword);

                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(QUERY_CREATE_ACCOUNT)) {

                    stmt.setObject(1, accountId);
                    stmt.setString(2, username);
                    stmt.setString(3, passwordHash);
                    // Discord ID is null for in-game registrations
                    stmt.setObject(4, null, Types.BIGINT);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            return new Account(
                                    rs.getString("id"),
                                    rs.getString("username"),
                                    rs.getString("password_hash"),
                                    rs.getString("xbox_user_id"),
                                    rs.getString("discord_id")
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Error creating account for username: {}", username, e);
                throw new RuntimeException("Database insert failed", e);
            }
            return null;
        }, executor);
    }

    /**
     * Hash a password using Argon2
     */
    private String hashPassword(String rawPassword) {
        // Use Argon2id with secure parameters
        Argon2Function argon2 = Argon2Function.getInstance(
                65536,  // memory cost
                2,      // iterations
                2,      // parallelism
                32,     // hash length
                Argon2.ID  // Argon2id variant
        );
        return Password.hash(rawPassword).with(argon2).getResult();
    }

    /**
     * Verify password using Argon2
     */
    public boolean verifyPassword(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }

        try {
            Argon2Function argon2 = Argon2Function.getInstanceFromHash(passwordHash);
            return Password.check(rawPassword, passwordHash).with(argon2);
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }

    /**
     * Get account by Discord ID (async)
     */
    public CompletableFuture<Optional<Account>> getAccountByDiscordId(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_GET_ACCOUNT_BY_DISCORD_ID)) {

                stmt.setLong(1, Long.parseLong(discordId));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(
                                rs.getString("id"),
                                rs.getString("username"),
                                rs.getString("password_hash"),
                                rs.getString("xbox_user_id"),
                                discordId
                        ));
                    }
                }
            } catch (SQLException e) {
                logger.error("Error fetching account by Discord ID: {}", discordId, e);
                throw new RuntimeException("Database query failed", e);
            }
            return Optional.empty();
        }, executor);
    }

    /**
     * Count accounts linked to a Discord ID (async)
     */
    public CompletableFuture<Integer> countAccountsByDiscordId(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_COUNT_ACCOUNTS_BY_DISCORD_ID)) {

                stmt.setLong(1, Long.parseLong(discordId));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                logger.error("Error counting accounts by Discord ID: {}", discordId, e);
                throw new RuntimeException("Database query failed", e);
            }
            return 0;
        }, executor);
    }

    /**
     * Link Discord account (async)
     */
    public CompletableFuture<Boolean> linkDiscordAccount(String accountId, String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(QUERY_UPDATE_DISCORD_LINK)) {

                stmt.setLong(1, Long.parseLong(discordId));
                stmt.setObject(2, UUID.fromString(accountId));

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                logger.error("Error linking Discord account {} to {}", discordId, accountId, e);
                throw new RuntimeException("Database update failed", e);
            }
        }, executor);
    }

    /**
     * Close database connection pool and executor
     */
    public void close() {
        logger.info("Closing database connections...");

        // Shutdown executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close data source
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed successfully");
        }
    }

    /**
     * Get a direct connection (for special cases - use sparingly)
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
