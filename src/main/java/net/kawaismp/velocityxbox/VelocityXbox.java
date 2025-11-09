package net.kawaismp.velocityxbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.UuidUtils;

import dev.dejvokep.boostedyaml.YamlDocument;
import net.kawaismp.velocityxbox.command.LinkCommand;
import net.kawaismp.velocityxbox.command.LoginCommand;
import net.kawaismp.velocityxbox.command.RegisterCommand;
import net.kawaismp.velocityxbox.command.UnlinkCommand;
import net.kawaismp.velocityxbox.config.ConfigManager;
import net.kawaismp.velocityxbox.database.DatabaseManager;
import net.kawaismp.velocityxbox.event.PlayerLoginSuccessEvent;
import net.kawaismp.velocityxbox.manager.LinkApiServer;
import net.kawaismp.velocityxbox.manager.PlayerLoginManager;
import net.kawaismp.velocityxbox.util.AutoLoginCache;
import net.kawaismp.velocityxbox.util.LastServerCache;
import net.kawaismp.velocityxbox.util.LinkCodeManager;
import net.kawaismp.velocityxbox.util.LoginSessionCache;
import net.kawaismp.velocityxbox.util.MessageProvider;
import net.kawaismp.velocityxbox.util.PlayerConnectionData;
import net.kawaismp.velocityxbox.util.RegistrationIpTracker;

@Plugin(
        id = "velocityxbox",
        name = "VelocityXbox",
        version = "1.2.0",
        url = "brokiem.is-a.dev",
        authors = {"brokiem"},
        dependencies = {
                @Dependency(id = "geyser"),
                @Dependency(id = "floodgate")
        }
)
public class VelocityXbox implements EventRegistrar {
    private final Logger logger;
    private final ProxyServer proxy;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerLoginManager loginManager;
    private MessageProvider messageProvider;
    private LastServerCache lastServerCache;
    private LoginSessionCache sessionCache;
    private PlayerConnectionData connectionData;
    private RegistrationIpTracker registrationIpTracker;
    private LinkCodeManager linkCodeManager;
    private LinkApiServer linkApiServer;
    private AutoLoginCache autoLoginCache;

    private volatile boolean geyserInitialized = false;

    @Inject
    public VelocityXbox(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(final ProxyInitializeEvent event) {
        try {
            // Initialize configuration
            initializeConfig();

            // Initialize managers
            this.messageProvider = new MessageProvider();
            this.databaseManager = new DatabaseManager(this, configManager);
            this.loginManager = new PlayerLoginManager(this);
            this.lastServerCache = new LastServerCache(dataDirectory);
            this.sessionCache = new LoginSessionCache(logger);
            this.connectionData = new PlayerConnectionData();
            this.registrationIpTracker = new RegistrationIpTracker(logger, dataDirectory);
            this.linkCodeManager = new LinkCodeManager(logger, configManager.getLinkCodeExpiration());
            this.linkApiServer = new LinkApiServer(this);
            this.autoLoginCache = new AutoLoginCache();

            // Register with Geyser
            GeyserApi.api().eventBus().register(this, this);

            // Start Link API server
            linkApiServer.start();

            // Register commands
            registerCommands();

            // Start periodic tasks
            startPeriodicTasks();

            logger.info("VelocityXbox plugin initialized successfully!");
            logger.info("Auth server: {}", configManager.getAuthServer());
            logger.info("Main server: {}", configManager.getHubServer());
        } catch (Exception e) {
            logger.error("Failed to initialize VelocityXbox plugin", e);
            throw new RuntimeException("Plugin initialization failed", e);
        }
    }

    private void initializeConfig() {
        try {
            YamlDocument config = YamlDocument.create(
                    new File(dataDirectory.toFile(), "config.yml"),
                    Objects.requireNonNull(getClass().getResourceAsStream("/config.yml"))
            );
            config.save();
            this.configManager = new ConfigManager(config);
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            throw new RuntimeException("Configuration initialization failed", e);
        }
    }

    private void registerCommands() {
        CommandManager commandManager = proxy.getCommandManager();

        // Login command
        CommandMeta loginMeta = commandManager.metaBuilder("login")
                .aliases("l")
                .plugin(this)
                .build();
        BrigadierCommand loginCommand = LoginCommand.createBrigadierCommand(this);
        commandManager.register(loginMeta, loginCommand);

        // Unlink command
        CommandMeta unlinkMeta = commandManager.metaBuilder("unlink")
                .aliases("unlinkxbox")
                .plugin(this)
                .build();
        BrigadierCommand unlinkCommand = UnlinkCommand.createBrigadierCommand(this);
        commandManager.register(unlinkMeta, unlinkCommand);

        // Register command
        CommandMeta registerMeta = commandManager.metaBuilder("register")
                .aliases("signup")
                .plugin(this)
                .build();
        BrigadierCommand registerCommand = RegisterCommand.createBrigadierCommand(this);
        commandManager.register(registerMeta, registerCommand);

        // Link command
        CommandMeta linkMeta = commandManager.metaBuilder("link")
                .aliases("discordlink")
                .plugin(this)
                .build();
        BrigadierCommand linkCommand = LinkCommand.createBrigadierCommand(this);
        commandManager.register(linkMeta, linkCommand);

        logger.info("Commands registered successfully");
    }

    private void startPeriodicTasks() {
        // Login reminder task
        proxy.getScheduler()
                .buildTask(this, () -> proxy.getAllPlayers().forEach(player -> {
                    UUID playerId = player.getInternalUniqueId();
                    if (!loginManager.isLoggingIn(playerId) && !loginManager.isLogged(playerId)) {
                        loginManager.showLoginTitle(player);
                    }
                }))
                .repeat(4, TimeUnit.SECONDS)
                .schedule();

        // Registration IP tracker cleanup task (runs every hour)
        proxy.getScheduler()
                .buildTask(this, () -> {
                    if (registrationIpTracker != null) {
                        registrationIpTracker.performGlobalCleanup();
                    }
                })
                .repeat(1, TimeUnit.HOURS)
                .schedule();
    }

    @Subscribe
    public void onGeyserPostInitializeEvent(GeyserPostInitializeEvent event) {
        geyserInitialized = true;
        logger.info("Geyser initialized successfully");
    }

    @Subscribe
    public void onProxyShutdown(final ProxyShutdownEvent event) {
        logger.info("Shutting down VelocityXbox plugin...");

        if (linkApiServer != null) {
            linkApiServer.stop();
        }

        if (linkCodeManager != null) {
            linkCodeManager.shutdown();
        }

        if (loginManager != null) {
            loginManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        if (lastServerCache != null) {
            lastServerCache.shutdown();
        }

        if (sessionCache != null) {
            sessionCache.shutdown();
        }

        if (connectionData != null) {
            connectionData.clear();
        }

        if (registrationIpTracker != null) {
            registrationIpTracker.shutdown();
        }

        if (autoLoginCache != null) {
            autoLoginCache.clear();
        }

        logger.info("VelocityXbox plugin shut down successfully");
    }

    @Subscribe
    public void onGameProfileRequest(final GameProfileRequestEvent event) {
        String baseUsername = event.getUsername();
        UUID preLoginUuid = event.getGameProfile().getId(); // UUID before any authentication
        int protocolVersion = event.getConnection().getProtocolVersion().getProtocol();
        
        // Generate original offline UUID for this username (for session validation)
        UUID originalUuid = UuidUtils.generateOfflinePlayerUuid(baseUsername);

        // First, try session-based auto-login
        LoginSessionCache.SessionData sessionData = sessionCache.validateSession(
                originalUuid,
                protocolVersion,
                event.getConnection().getRemoteAddress().getAddress(),
                event.isOnlineMode()
        );

        if (sessionData != null) {
            // Valid session found - perform auto-login
            UUID accountUuid = UUID.fromString(sessionData.getAccountId());
            String accountUsername = sessionData.getUsername();
            
            logger.info("Auto-login via session for {} (account: {})", baseUsername, accountUsername);
            
            // Set the game profile to the logged-in account
            GameProfile loggedInProfile = new GameProfile(
                    accountUuid,
                    accountUsername,
                    event.getGameProfile().getProperties()
            );
            event.setGameProfile(loggedInProfile);
            
            // Fetch full account data and cache for completion in ServerPostConnectEvent
            databaseManager.getAccountById(sessionData.getAccountId())
                    .thenAccept(accountOpt -> {
                        if (accountOpt.isPresent()) {
                            autoLoginCache.store(
                                    accountUuid, 
                                    accountOpt.get(), 
                                    PlayerLoginSuccessEvent.LoginMethod.SESSION_CACHE,
                                    originalUuid
                            );
                        } else {
                            logger.warn("Session referenced non-existent account ID: {}", sessionData.getAccountId());
                        }
                    })
                    .exceptionally(ex -> {
                        logger.error("Error fetching account data for session auto-login", ex);
                        return null;
                    });
            
            return; // Skip randomization
        }

        // Second, try Bedrock XUID-based auto-login
        if (geyserInitialized && GeyserApi.api().isBedrockPlayer(preLoginUuid)) {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(preLoginUuid);
            if (connection != null) {
                String xuid = connection.xuid();
                logger.info("Attempting Bedrock auto-login for {} with XUID {}", baseUsername, xuid);
                
                // Synchronously fetch account by XUID (this blocks the event, but it's quick)
                try {
                    java.util.Optional<net.kawaismp.velocityxbox.database.model.Account> accountOpt = 
                            databaseManager.getAccountByXuid(xuid).get(5, java.util.concurrent.TimeUnit.SECONDS);
                    
                    if (accountOpt.isPresent()) {
                        net.kawaismp.velocityxbox.database.model.Account account = accountOpt.get();
                        UUID accountUuid = UUID.fromString(account.id());
                        String accountUsername = account.username();
                        
                        logger.info("Found linked Bedrock account for XUID {}: {}", xuid, accountUsername);
                        
                        // Set the game profile to the logged-in account
                        GameProfile loggedInProfile = new GameProfile(
                                accountUuid,
                                accountUsername,
                                event.getGameProfile().getProperties()
                        );
                        event.setGameProfile(loggedInProfile);
                        
                        // Cache for completion in ServerPostConnectEvent
                        autoLoginCache.store(
                                accountUuid, 
                                account, 
                                PlayerLoginSuccessEvent.LoginMethod.XBOX_AUTO_LOGIN,
                                originalUuid
                        );
                        
                        return; // Skip randomization
                    } else {
                        logger.debug("No linked account found for XUID {}", xuid);
                    }
                } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException | InterruptedException e) {
                    logger.error("Error during Bedrock auto-login for XUID {}", xuid, e);
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    // Continue to randomization on error
                }
            }
        }

        // No auto-login - proceed with randomization
        String randomUsername = loginManager.generateRandomUsername();
        UUID finalUuid = originalUuid;
        int suffix = 1;

        // Check if UUID already exists and append number if needed
        while (proxy.getPlayer(finalUuid).isPresent()) {
            String suffixedUsername = baseUsername + suffix;
            finalUuid = UuidUtils.generateOfflinePlayerUuid(suffixedUsername);
            suffix++;
        }

        // Create the randomized profile
        GameProfile randomizedProfile = new GameProfile(
                finalUuid,
                randomUsername,
                event.getGameProfile().getProperties()
        );

        event.setGameProfile(randomizedProfile);

        // Store pending connection data using the randomized username as temporary key
        // We'll transfer this to the actual internal UUID when the player connects
        connectionData.storePending(randomUsername, originalUuid, baseUsername, protocolVersion);

        logger.debug("Stored pending connection data for random username: {} (original: {})", randomUsername, baseUsername);
    }

    @Subscribe
    public void onPlayerChooseInitialServer(final PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();
        
        // Check if this player has auto-login data (was auto-logged in during GameProfileRequestEvent)
        if (autoLoginCache.hasPendingAutoLogin(playerId)) {
            // Player was auto-logged in, redirect them to hub or last server instead of auth server
            UUID accountUuid = playerId; // For auto-logged players, playerId is the account UUID
            
            // Try to get last server from cache
            String lastServer = lastServerCache.get(accountUuid);
            
            // Determine target server
            String targetServerName;
            if (lastServer != null && !lastServer.isEmpty()) {
                // Check if last server is not a special server (hub or auth)
                String hubServer = configManager.getHubServer();
                String authServer = configManager.getAuthServer();
                boolean isSpecialServer = lastServer.equalsIgnoreCase(hubServer) || lastServer.equalsIgnoreCase(authServer);
                
                if (!isSpecialServer) {
                    // Use last server if it's not a special server
                    targetServerName = lastServer;
                    logger.info("Redirecting auto-logged player {} to last server: {}", player.getUsername(), lastServer);
                } else {
                    // Last server was special, go to hub instead
                    targetServerName = hubServer;
                    lastServerCache.remove(accountUuid);
                    logger.info("Redirecting auto-logged player {} to hub (last server was special)", player.getUsername());
                }
            } else {
                // No last server, go to hub
                targetServerName = configManager.getHubServer();
                logger.info("Redirecting auto-logged player {} to hub (no last server)", player.getUsername());
            }
            
            // Set the initial server
            proxy.getServer(targetServerName).ifPresentOrElse(
                    server -> {
                        event.setInitialServer(server);
                        logger.debug("Set initial server for {} to {}", player.getUsername(), targetServerName);
                    },
                    () -> {
                        logger.warn("Target server '{}' not found for auto-logged player {}, using default", 
                                targetServerName, player.getUsername());
                        // Fall back to hub if target server doesn't exist
                        proxy.getServer(configManager.getHubServer()).ifPresent(event::setInitialServer);
                    }
            );
        }
        // If player is not auto-logged in, they will go to the default initial server (auth server)
    }

    @Subscribe
    public void onCommandExecute(final CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        UUID uuid = player.getInternalUniqueId();
        boolean isLoggedIn = loginManager.isLogged(uuid);
        boolean isInAuthServer = player.getCurrentServer()
                .map(server -> configManager.getAuthServer().equalsIgnoreCase(server.getServerInfo().getName()))
                .orElse(false);

        // Cancel reminder task when player inputs any command
        if (!isLoggedIn) {
            loginManager.cancelReminderTask(uuid);
        }

        String command = event.getCommand().split(" ")[0];

        if ((isInAuthServer || !isLoggedIn) &&
                !command.equalsIgnoreCase("login") &&
                !command.equalsIgnoreCase("l") &&
                !command.equalsIgnoreCase("register") &&
                !command.equalsIgnoreCase("signup")) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(messageProvider.getCommandDenied());
        } else if (isLoggedIn && (command.equalsIgnoreCase("login") || command.equalsIgnoreCase("register"))) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(messageProvider.getAlreadyLoggedIn());
        }
    }

    @Subscribe
    public void onPlayerChat(final PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getInternalUniqueId();
        boolean isInAuthServer = player.getCurrentServer()
                .map(server -> configManager.getAuthServer().equalsIgnoreCase(server.getServerInfo().getName()))
                .orElse(false);

        if (isInAuthServer || !loginManager.isLogged(uuid)) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe
    public void onPreConnect(final ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();

        if (!loginManager.isLogged(playerId)) {
            return;
        }

        if (configManager.getAuthServer().equalsIgnoreCase(event.getOriginalServer().getServerInfo().getName())) {
            // Reset login status when returning to auth server
            loginManager.logout(player);

            String randomUsername = loginManager.generateRandomUsername();
            player.setProfile(new GameProfile(
                    UuidUtils.generateOfflinePlayerUuid(player.getUsername()),
                    randomUsername,
                    player.getGameProfileProperties()
            ));
        }
    }

    @Subscribe
    public void onPostConnect(final ServerPostConnectEvent event) {
        // Only handle initial connections
        if (event.getPreviousServer() != null) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();

        // Check if this player was auto-logged in during GameProfileRequestEvent
        AutoLoginCache.AutoLoginData autoLoginData = autoLoginCache.retrieve(playerId);
        
        if (autoLoginData != null) {
            // Player was auto-logged in, complete the login process
            logger.info("Completing auto-login for player {} via {}", 
                    player.getUsername(), autoLoginData.getLoginMethod());
            
            // Complete the login using the PlayerLoginManager
            loginManager.completeAutoLogin(player, autoLoginData.getAccount(), 
                    autoLoginData.getLoginMethod(), autoLoginData.getOriginalUuid());
            return;
        }

        // Player was not auto-logged in, check if already logged in (shouldn't happen but safety check)
        if (loginManager.isLogged(playerId)) {
            return;
        }

        // Transfer pending connection data to permanent storage using actual internal UUID
        String currentUsername = player.getUsername();
        boolean transferred = connectionData.transferFromPending(playerId, currentUsername);

        if (transferred) {
            logger.debug("Transferred connection data for player {} (internal UUID: {})", currentUsername, playerId);
        } else {
            logger.warn("No pending connection data found for player {} (internal UUID: {})", currentUsername, playerId);
        }

        // Proceed with normal login flow
        loginManager.scheduleLoginTasks(player);
        loginManager.showLoginTitle(player);
    }

    @Subscribe
    public void onPlayerKicked(final KickedFromServerEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();

        if (!event.kickedDuringServerConnect()) {
            // Save last connected server for auto reconnect
            if (loginManager.isLogged(playerId)) {
                lastServerCache.put(player.getUniqueId(), event.getServer().getServerInfo().getName());
            }
        }
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();

        // If player was logged in, save session to cache
        if (loginManager.isLogged(playerId)) {
            UUID originalUuid = connectionData.getOriginalUuid(playerId);
            if (originalUuid != null) {
                // Get account info from login manager
                String accountId = loginManager.getAccountId(playerId);
                // After successful login, player.getUsername() returns the logged-in username
                // because the profile was updated in login() method
                String loggedInUsername = player.getUsername();

                if (accountId != null && loggedInUsername != null) {
                    sessionCache.createSession(
                            originalUuid,
                            loggedInUsername,
                            player.getProtocolVersion().getProtocol(),
                            player.getRemoteAddress().getAddress(),
                            accountId,
                            player.isOnlineMode()
                    );
                    sessionCache.markForExpiration(originalUuid);
                    logger.info("Saved session for player {} (will expire in 10 minutes)", loggedInUsername);
                }
            }

            // Save last connected server for auto reconnect
            player.getCurrentServer().ifPresent(server -> lastServerCache.put(player.getUniqueId(), server.getServerInfo().getName()));
        }

        // Clean up player tasks
        loginManager.cleanupPlayer(playerId);

        // Remove connection data mapping
        connectionData.remove(playerId);

        // Remove any pending auto-login data (in case player disconnected before ServerPostConnectEvent)
        autoLoginCache.remove(playerId);

        // Reset login state (unless it's a conflicting login)
        if (event.getLoginStatus() != DisconnectEvent.LoginStatus.CONFLICTING_LOGIN) {
            loginManager.removeLoginState(playerId);
        }
    }

    // Getters
    public Logger getLogger() {
        return logger;
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerLoginManager getLoginManager() {
        return loginManager;
    }

    public MessageProvider getMessageProvider() {
        return messageProvider;
    }

    public boolean isGeyserInitialized() {
        return geyserInitialized;
    }

    // Getter for lastServerCache
    public LastServerCache getLastServerCache() {
        return lastServerCache;
    }

    // Getter for sessionCache
    public LoginSessionCache getSessionCache() {
        return sessionCache;
    }

    // Getter for connectionData
    public PlayerConnectionData getConnectionData() {
        return connectionData;
    }

    // Getter for registrationIpTracker
    public RegistrationIpTracker getRegistrationIpTracker() {
        return registrationIpTracker;
    }

    // Getter for linkCodeManager
    public LinkCodeManager getLinkCodeManager() {
        return linkCodeManager;
    }

    // Getter for linkApiServer
    public LinkApiServer getLinkApiServer() {
        return linkApiServer;
    }

    // Getter for autoLoginCache
    public AutoLoginCache getAutoLoginCache() {
        return autoLoginCache;
    }
}
