package net.kawaismp.velocityxbox.config;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.route.Route;

public class ConfigManager {
    private static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
    private static final String DEFAULT_AUTH_SERVER = "auth";
    private static final String DEFAULT_HUB_SERVER = "hub";
    private static final String DEFAULT_DB_HOST = "localhost";
    private static final int DEFAULT_DB_PORT = 5432;
    private static final String DEFAULT_DB_NAME = "postgres";
    private static final String DEFAULT_DB_USER = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "password";

    private final YamlDocument config;

    public ConfigManager(YamlDocument config) {
        this.config = config;
        validateConfiguration();
    }

    private void validateConfiguration() {
        if (getAuthServer().isEmpty()) {
            throw new IllegalStateException("Auth server name cannot be empty");
        }
        if (getHubServer().isEmpty()) {
            throw new IllegalStateException("Hub server name cannot be empty");
        }
        if (getDatabaseHost().isEmpty()) {
            throw new IllegalStateException("Database host cannot be empty");
        }
        if (getDatabaseName().isEmpty()) {
            throw new IllegalStateException("Database name cannot be empty");
        }
        if (getDatabaseUser().isEmpty()) {
            throw new IllegalStateException("Database user cannot be empty");
        }
    }

    public int getMaxLoginAttempts() {
        return config.getInt(Route.from("max-login-attempts"), DEFAULT_MAX_LOGIN_ATTEMPTS);
    }

    public String getAuthServer() {
        return config.getString(Route.from("auth-server"), DEFAULT_AUTH_SERVER);
    }

    public String getHubServer() {
        return config.getString(Route.from("hub-server"), DEFAULT_HUB_SERVER);
    }

    public String getDatabaseHost() {
        return config.getString(Route.from("database", "host"), DEFAULT_DB_HOST);
    }

    public int getDatabasePort() {
        return config.getInt(Route.from("database", "port"), DEFAULT_DB_PORT);
    }

    public String getDatabaseName() {
        return config.getString(Route.from("database", "name"), DEFAULT_DB_NAME);
    }

    public String getDatabaseUser() {
        return config.getString(Route.from("database", "user"), DEFAULT_DB_USER);
    }

    public String getDatabasePassword() {
        return config.getString(Route.from("database", "password"), DEFAULT_DB_PASSWORD);
    }
}