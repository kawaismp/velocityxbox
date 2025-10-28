package net.kawaismp.velocityxbox.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.command.builtin.CommandMessages;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kawaismp.velocityxbox.database.model.Account;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static net.kawaismp.velocityxbox.util.SoundUtil.playSuccessSound;

public class PlayerLoginManager {
    private static final long LOGIN_TIMEOUT_SECONDS = 180;
    private static final long REMINDER_INTERVAL_SECONDS = 4;
    private static final long LOGIN_SOUND_DELAY_MS = 500;

    private final VelocityXbox plugin;
    private final Set<UUID> loggedInPlayers;
    private final Set<UUID> loggingInPlayers;
    private final Map<UUID, List<ScheduledTask>> playerTasks;
    private final Map<UUID, Integer> loginAttempts;

    public PlayerLoginManager(VelocityXbox plugin) {
        this.plugin = plugin;
        this.loggedInPlayers = ConcurrentHashMap.newKeySet();
        this.loggingInPlayers = ConcurrentHashMap.newKeySet();
        this.playerTasks = new ConcurrentHashMap<>();
        this.loginAttempts = new ConcurrentHashMap<>();
    }

    /**
     * Check if player is logged in
     */
    public boolean isLogged(UUID playerId) {
        return loggedInPlayers.contains(playerId);
    }

    /**
     * Check if player is in the process of logging in
     */
    public boolean isLoggingIn(UUID playerId) {
        return loggingInPlayers.contains(playerId);
    }

    /**
     * Set player logging in status
     */
    public void setLoggingIn(UUID playerId, boolean status) {
        if (status) {
            loggingInPlayers.add(playerId);
        } else {
            loggingInPlayers.remove(playerId);
        }
    }

    /**
     * Log in player and transfer to main server
     */
    public void login(Player player, Account account) {
        UUID playerId = player.getInternalUniqueId();

        plugin.getLogger().info("Logging in player {} as {}", player.getUsername(), account.getUsername());

        // Clean up any existing tasks
        cleanupPlayer(playerId);

        // Add to logged in set
        loggedInPlayers.add(playerId);

        // Update player profile
        GameProfile updatedProfile = new GameProfile(
                UUID.fromString(account.getId()),
                account.getUsername(),
                player.getGameProfile().getProperties()
        );
        player.setProfile(updatedProfile);

        // Play success sound and show messages
        playSuccessSound(player);

        showWelcomeTitle(player, account.getUsername());

        // Transfer to main server after delay
        plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    // On successful login, check for last server cache
                    String lastServer = plugin.getLastServerCache().get(playerId);
                    if (lastServer != null && !lastServer.equalsIgnoreCase(plugin.getConfigManager().getHubServer())) {
                        plugin.getProxy().getServer(lastServer).ifPresent(server -> {
                            player.createConnectionRequest(server).connect();
                        });
                        plugin.getLastServerCache().remove(playerId);
                        return;
                    }

                    // Send player to main server if last server cache is invalid
                    transferToMainServer(player);
                })
                .delay(LOGIN_SOUND_DELAY_MS, TimeUnit.MILLISECONDS)
                .schedule();
    }

    /**
     * Log out player
     */
    public void logout(Player player) {
        UUID playerId = player.getInternalUniqueId();
        plugin.getLogger().info("Logging out player {}", player.getUsername());

        cleanupPlayer(playerId);
        loggedInPlayers.remove(playerId);
    }

    /**
     * Remove login state without cleanup (for disconnect events)
     */
    public void removeLoginState(UUID playerId) {
        loggedInPlayers.remove(playerId);
        loggingInPlayers.remove(playerId);
    }

    /**
     * Get login attempts for a player
     * @param playerId UUID
     * @return int
     */
    public int getLoginAttempts(UUID playerId) {
        return loginAttempts.getOrDefault(playerId, 0);
    }

    /**
     * Increment login attempts for a player
     * @param playerId UUID
     */
    public void incrementLoginAttempts(UUID playerId) {
        loginAttempts.put(playerId, getLoginAttempts(playerId) + 1);
    }

    /**
     * Reset login attempts for a player
     * @param playerId UUID
     */
    public void resetLoginAttempts(UUID playerId) {
        loginAttempts.remove(playerId);
    }

    /**
     * Transfer player to main server
     */
    private void transferToMainServer(Player player) {
        String mainServer = plugin.getConfigManager().getHubServer();

        plugin.getProxy().getServer(mainServer).ifPresentOrElse(
                server -> player.createConnectionRequest(server).fireAndForget(),
                () -> {
                    player.sendMessage(CommandMessages.SERVER_DOES_NOT_EXIST.arguments(Component.text(mainServer)));
                    plugin.getLogger().error("Main server '{}' not found!", mainServer);
                }
        );
    }

    /**
     * Schedule login reminder and timeout tasks
     */
    public void scheduleLoginTasks(Player player) {
        UUID playerId = player.getInternalUniqueId();
        List<ScheduledTask> tasks = new ArrayList<>(2);

        // Reminder task - shows login info periodically
        ScheduledTask reminderTask = plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    if (isLoggingIn(playerId) || isLogged(playerId)) {
                        return;
                    }
                    player.sendMessage(plugin.getMessageProvider().getRegisterInfo());
                    player.sendMessage(plugin.getMessageProvider().getHelpInfo());
                })
                .repeat(REMINDER_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .schedule();
        tasks.add(reminderTask);

        // Timeout task - kick player if not logged in
        ScheduledTask kickTask = plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    if (!isLogged(playerId)) {
                        player.disconnect(plugin.getMessageProvider().getLoginTimeout());
                    }
                })
                .delay(LOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .schedule();
        tasks.add(kickTask);

        playerTasks.put(playerId, tasks);
    }

    /**
     * Cancel the reminder task (when player starts typing)
     */
    public void cancelReminderTask(UUID playerId) {
        List<ScheduledTask> tasks = playerTasks.get(playerId);
        if (tasks != null && !tasks.isEmpty()) {
            ScheduledTask reminderTask = tasks.getFirst();
            if (reminderTask.status() == TaskStatus.SCHEDULED) {
                reminderTask.cancel();
            }
        }
    }

    /**
     * Clean up all tasks for a player
     */
    public void cleanupPlayer(UUID playerId) {
        loginAttempts.remove(playerId);

        List<ScheduledTask> tasks = playerTasks.remove(playerId);
        if (tasks != null) {
            tasks.forEach(task -> {
                if (task.status() == TaskStatus.SCHEDULED) {
                    task.cancel();
                }
            });
        }
    }

    /**
     * Attempt auto-login for Bedrock players with linked accounts
     */
    public void attemptAutoLogin(Player player, String xuid) {
        plugin.getLogger().info("Attempting auto-login for Bedrock player {} with XUID {}",
                player.getUsername(), xuid);

        plugin.getDatabaseManager().getAccountByXuid(xuid)
                .thenAccept(accountOpt -> {
                    if (accountOpt.isPresent()) {
                        Account account = accountOpt.get();
                        plugin.getLogger().info("Found linked account for player {}: {}",
                                player.getUsername(), account.getUsername());

                        // Schedule on main thread
                        plugin.getProxy().getScheduler()
                                .buildTask(plugin, () -> {
                                    player.sendMessage(plugin.getMessageProvider().getAutoLoginSuccess());
                                    player.sendMessage(
                                            Component.text("(", NamedTextColor.GRAY)
                                                    .append(Component.text("!", NamedTextColor.AQUA))
                                                    .append(Component.text(") » ", NamedTextColor.GRAY))
                                                    .append(Component.text("Masuk sebagai ", NamedTextColor.GRAY))
                                                    .append(Component.text(account.getUsername(), NamedTextColor.YELLOW))
                                    );

                                    login(player, account);
                                })
                                .schedule();
                    } else {
                        plugin.getLogger().info("No linked account found for XUID {}", xuid);
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().error("Error during auto-login for player {}", player.getUsername(), ex);
                    return null;
                });
    }

    /**
     * Show login title to player
     */
    public void showLoginTitle(Player player) {
        Title title = Title.title(
                Component.text("LOGIN", NamedTextColor.BLUE, TextDecoration.BOLD),
                Component.text("/l <username> <password>", NamedTextColor.GREEN),
                Title.Times.times(Ticks.duration(0L), Ticks.duration(100L), Ticks.duration(0L))
        );
        player.showTitle(title);
        player.sendActionBar(
                Component.text("Use ", NamedTextColor.RED)
                        .append(Component.text("/register <username> <password> <confirmPassword> ", NamedTextColor.YELLOW))
                        .append(Component.text("to register", NamedTextColor.RED))
        );
    }

    /**
     * Show welcome title to player
     */
    public void showWelcomeTitle(Player player, String username) {
        Title title = Title.title(
                Component.text("Selamat datang", NamedTextColor.GREEN),
                Component.text(username, NamedTextColor.YELLOW)
        );
        player.showTitle(title);
    }

    /**
     * Generate random username for unauthenticated players
     */
    public String generateRandomUsername() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    /**
     * Shutdown manager and cleanup all tasks
     */
    public void shutdown() {
        plugin.getLogger().info("Shutting down PlayerLoginManager...");

        playerTasks.values().forEach(tasks ->
                tasks.forEach(task -> {
                    if (task.status() == TaskStatus.SCHEDULED) {
                        task.cancel();
                    }
                })
        );

        playerTasks.clear();
        loggedInPlayers.clear();
        loggingInPlayers.clear();
        loginAttempts.clear();

        plugin.getLogger().info("PlayerLoginManager shut down successfully");
    }
}