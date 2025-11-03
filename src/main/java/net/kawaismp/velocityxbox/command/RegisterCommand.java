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
import static net.kawaismp.velocityxbox.util.SoundUtil.playErrorSound;
import static net.kawaismp.velocityxbox.util.SoundUtil.playProcessingSound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class RegisterCommand {

    public static BrigadierCommand createBrigadierCommand(final VelocityXbox plugin) {
        LiteralArgumentBuilder<CommandSource> registerCommand = BrigadierCommand.literalArgumentBuilder("register")
                .requires(commandSource -> commandSource instanceof Player)
                .executes(context -> {
                    // Show usage when no arguments provided
                    context.getSource().sendMessage(Component.text("Usage: /register <username> <password> <confirmPassword>", NamedTextColor.RED));
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("username", StringArgumentType.string())
                        .executes(context -> {
                            // Show usage when only username provided
                            context.getSource().sendMessage(Component.text("Usage: /register <username> <password> <confirmPassword>", NamedTextColor.RED));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("password", StringArgumentType.string())
                                .executes(context -> {
                                    // Show usage when only username and password provided
                                    context.getSource().sendMessage(Component.text("Usage: /register <username> <password> <confirmPassword>", NamedTextColor.RED));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("confirmPassword", StringArgumentType.string())
                                        .executes(context -> {
                                            String username = context.getArgument("username", String.class);
                                            String password = context.getArgument("password", String.class);
                                            String confirmPassword = context.getArgument("confirmPassword", String.class);
                                            return executeRegisterCommand(context.getSource(), username, password, confirmPassword, plugin);
                                        })
                                )
                        )
                );

        LiteralCommandNode<CommandSource> registerNode = registerCommand.build();
        return new BrigadierCommand(registerNode);
    }

    private static int executeRegisterCommand(CommandSource source, String username, String password, String confirmPassword, VelocityXbox plugin) {
        if (!(source instanceof Player player)) {
            return Command.SINGLE_SUCCESS;
        }

        // Check IP registration limit first
        if (!plugin.getRegistrationIpTracker().canRegister(player.getRemoteAddress().getAddress())) {
            player.sendMessage(plugin.getMessageProvider().getErrorRegistrationLimitReached());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Validate input
        if (username == null || username.trim().isEmpty() ||
                password == null || password.trim().isEmpty() ||
                confirmPassword == null || confirmPassword.trim().isEmpty()) {
            player.sendMessage(plugin.getMessageProvider().getErrorEmptyCredentials());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Check if passwords match
        if (!password.equals(confirmPassword)) {
            player.sendMessage(plugin.getMessageProvider().getErrorPasswordMismatch());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Validate username (alphanumeric and underscores only, 3-16 characters)
        if (!username.matches("^[a-zA-Z0-9_]{3,16}$")) {
            player.sendMessage(plugin.getMessageProvider().getErrorInvalidUsername());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Validate password length
        if (password.length() < 6 || password.length() > 64) {
            player.sendMessage(plugin.getMessageProvider().getErrorInvalidPasswordLength());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Set registering status
        plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), true);

        // Play feedback sound and show message
        playProcessingSound(player);
        player.sendMessage(plugin.getMessageProvider().getRegisteringMessage());

        // Register asynchronously
        registerPlayer(player, username.trim(), password, plugin);

        return Command.SINGLE_SUCCESS;
    }

    private static void registerPlayer(Player player, String username, String password, VelocityXbox plugin) {
        // Check if username already exists
        plugin.getDatabaseManager().getAccountByUsername(username)
                .thenCompose(accountOpt -> {
                    if (accountOpt.isPresent()) {
                        // Username already exists
                        plugin.getProxy().getScheduler()
                                .buildTask(plugin, () -> {
                                    playErrorSound(player);
                                    player.sendMessage(plugin.getMessageProvider().getErrorUsernameExists());
                                    plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                                })
                                .schedule();
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }

                    // Create new account
                    return plugin.getDatabaseManager().createAccount(username, password)
                            .thenAccept(account -> {
                                if (account != null) {
                                    handleSuccessfulRegistration(player, account, plugin);
                                } else {
                                    handleRegistrationFailure(player, plugin);
                                }
                            });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().error("Error during registration for player {}", player.getUsername(), ex);

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

    private static void handleSuccessfulRegistration(Player player, Account account, VelocityXbox plugin) {
        // Record the successful registration
        plugin.getRegistrationIpTracker().recordRegistration(
            player.getRemoteAddress().getAddress(),
            account.getUsername()
        );

        // Schedule on main thread
        plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageProvider().getRegisterSuccess());
                    player.sendMessage(plugin.getMessageProvider().createRegisteredAsMessage(account.getUsername()));

                    plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                    plugin.getLoginManager().login(player, account);
                })
                .schedule();
    }

    private static void handleRegistrationFailure(Player player, VelocityXbox plugin) {
        // Schedule on main thread
        plugin.getProxy().getScheduler()
                .buildTask(plugin, () -> {
                    playErrorSound(player);
                    player.sendMessage(plugin.getMessageProvider().getErrorGeneric());
                    plugin.getLoginManager().setLoggingIn(player.getInternalUniqueId(), false);
                })
                .schedule();
    }
}