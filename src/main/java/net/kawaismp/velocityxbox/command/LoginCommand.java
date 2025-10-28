package net.kawaismp.velocityxbox.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kawaismp.velocityxbox.database.model.Account;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

import static net.kawaismp.velocityxbox.util.SoundUtil.playErrorSound;
import static net.kawaismp.velocityxbox.util.SoundUtil.playProcessingSound;

public final class LoginCommand {

    public static BrigadierCommand createBrigadierCommand(final VelocityXbox plugin) {
        LiteralArgumentBuilder<CommandSource> loginCommand = BrigadierCommand.literalArgumentBuilder("login")
                .requires(commandSource -> commandSource instanceof Player)
                .executes(context -> {
                    // Show usage when no arguments provided
                    context.getSource().sendMessage(Component.text("Usage: /login <username> <password>", NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.string())
                        .executes(context -> {
                            // Show usage when only username provided
                            context.getSource().sendMessage(Component.text("Usage: /login <username> <password>", NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("password", StringArgumentType.string())
                                .executes(context -> {
                                    String username = context.getArgument("username", String.class);
                                    String password = context.getArgument("password", String.class);
                                    return executeLoginCommand(context.getSource(), username, password, plugin);
                                })
                        )
                );

        LiteralCommandNode<CommandSource> loginNode = loginCommand.build();
        return new BrigadierCommand(loginNode);
    }

    private static int executeLoginCommand(CommandSource source, String username, String rawPassword, VelocityXbox plugin) {
        if (!(source instanceof Player player)) {
            return Command.SINGLE_SUCCESS;
        }

        // Validate input
        if (username == null || username.trim().isEmpty() ||
                rawPassword == null || rawPassword.trim().isEmpty()) {
            player.sendMessage(plugin.getMessageProvider().getErrorEmptyCredentials());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Check max login attempts
        int maxLoginAttempts = plugin.getConfigManager().getMaxLoginAttempts();
        if (maxLoginAttempts >= 1) {
            int loginAttempts = plugin.getLoginManager().getLoginAttempts(player.getInternalUniqueId());
            if (loginAttempts >= maxLoginAttempts) {
                player.disconnect(plugin.getMessageProvider().getErrorTooManyLoginAttempts());
                return Command.SINGLE_SUCCESS;
            }
        }

        // Set logging in status
        plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), true);

        // Play feedback sound and show message
        playProcessingSound(player);
        player.sendMessage(plugin.getMessageProvider().getErrorLoggingIn());

        // Authenticate asynchronously
        authenticatePlayer(player, username.trim(), rawPassword, plugin);

        // Increment login attempts
        plugin.getLoginManager().incrementLoginAttempts(player.getInternalUniqueId());

        return Command.SINGLE_SUCCESS;
    }

    private static void authenticatePlayer(Player player, String username, String rawPassword, VelocityXbox plugin) {
        plugin.getDatabaseManager().getAccountByUsername(username)
                .thenAccept(accountOpt -> {
                    if (accountOpt.isEmpty()) {
                        handleAuthFailure(player, plugin);
                        return;
                    }

                    Account account = accountOpt.get();

                    // Verify password
                    if (!plugin.getDatabaseManager().verifyPassword(rawPassword, account.getPasswordHash())) {
                        handleAuthFailure(player, plugin);
                        return;
                    }

                    // Check if this is a Bedrock player and link Xbox account if needed
                    if (GeyserApi.api().isBedrockPlayer(player.getUniqueId())) {
                        handleBedrockPlayerLink(player, account, plugin);
                    } else {
                        // Java player - just log in
                        handleSuccessfulLogin(player, account, plugin);
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().error("Error during authentication for player {}", player.getUsername(), ex);

                    // Schedule on main thread
                    plugin.getProxy().getScheduler()
                            .buildTask(plugin, () -> {
                                playErrorSound(player);
                                player.sendMessage(plugin.getMessageProvider().getErrorDatabase());
                                plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                            })
                            .schedule();

                    return null;
                });
    }

    private static void handleBedrockPlayerLink(Player player, Account account, VelocityXbox plugin) {
        GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());

        if (connection == null) {
            // No Geyser connection, just log in normally
            handleSuccessfulLogin(player, account, plugin);
            return;
        }

        String xuid = connection.xuid();

        // Check if account already has a linked Xbox account
        plugin.getDatabaseManager().hasLinkedXbox(account.getId())
                .thenCompose(alreadyLinked -> {
                    if (!alreadyLinked) {
                        // Link the Xbox account
                        return plugin.getDatabaseManager().linkXboxAccount(account.getId(), xuid);
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(true);
                })
                .thenAccept(success -> {
                    if (!success) {
                        plugin.getLogger().warn("Failed to link Xbox account for player {}", player.getUsername());
                    }
                    handleSuccessfulLogin(player, account, plugin);
                })
                .exceptionally(ex -> {
                    plugin.getLogger().error("Error linking Xbox account for player {}", player.getUsername(), ex);
                    handleSuccessfulLogin(player, account, plugin);
                    return null;
                });
    }

    private static void handleSuccessfulLogin(Player player, Account account, VelocityXbox plugin) {
        // Schedule on main thread
        plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageProvider().getLoginSuccess());
                    player.sendMessage(plugin.getMessageProvider().createLoginAsMessage(account.getUsername()));

                    plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                    plugin.getLoginManager().login(player, account);
                })
                .schedule();
    }

    private static void handleAuthFailure(Player player, VelocityXbox plugin) {
        // Schedule on main thread
        plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    playErrorSound(player);
                    player.sendMessage(plugin.getMessageProvider().getErrorInvalidCredentials());
                    plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                })
                .schedule();
    }
}