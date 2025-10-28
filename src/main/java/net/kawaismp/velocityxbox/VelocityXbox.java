package net.kawaismp.velocityxbox;

import com.google.inject.Inject;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
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
import net.kawaismp.velocityxbox.command.LoginCommand;
import net.kawaismp.velocityxbox.command.UnlinkCommand;
import net.kawaismp.velocityxbox.command.RegisterCommand;
import net.kawaismp.velocityxbox.config.ConfigManager;
import net.kawaismp.velocityxbox.database.DatabaseManager;
import net.kawaismp.velocityxbox.manager.PlayerLoginManager;
import net.kawaismp.velocityxbox.util.MessageProvider;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

            // Register with Geyser
            GeyserApi.api().eventBus().register(this, this);

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

        logger.info("Commands registered successfully");
    }

    private void startPeriodicTasks() {
        // Login reminder task
        proxy.getScheduler()
                .buildTask(this, () -> {
                    proxy.getAllPlayers().forEach(player -> {
                        UUID playerId = player.getInternalUniqueId();
                        if (!loginManager.isLoggingIn(playerId) && !loginManager.isLogged(playerId)) {
                            loginManager.showLoginTitle(player);
                        }
                    });
                })
                .repeat(4, TimeUnit.SECONDS)
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

        if (loginManager != null) {
            loginManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        logger.info("VelocityXbox plugin shut down successfully");
    }

    @Subscribe
    public void onGameProfileRequest(final GameProfileRequestEvent event) {
        String baseUsername = event.getUsername();
        String randomUsername = loginManager.generateRandomUsername();
        UUID baseUuid = UuidUtils.generateOfflinePlayerUuid(baseUsername);

        // Check if UUID already exists and append number if needed
        String finalUsername = baseUsername;
        UUID finalUuid = baseUuid;
        int suffix = 1;

        while (proxy.getPlayer(finalUuid).isPresent()) {
            finalUsername = baseUsername + suffix;
            finalUuid = UuidUtils.generateOfflinePlayerUuid(finalUsername);
            suffix++;
        }

        event.setGameProfile(new GameProfile(
                finalUuid,
                randomUsername,
                event.getGameProfile().getProperties()
        ));
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

        if (loginManager.isLogged(playerId)) {
            return;
        }

        // Schedule login tasks and show title
        loginManager.scheduleLoginTasks(player);
        loginManager.showLoginTitle(player);

        // Auto-login for linked Bedrock players
        if (GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
            GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());
            if (connection != null) {
                String xuid = connection.xuid();
                loginManager.attemptAutoLogin(player, xuid);
            }
        }
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getInternalUniqueId();

        // Clean up player tasks
        loginManager.cleanupPlayer(playerId);

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
}
