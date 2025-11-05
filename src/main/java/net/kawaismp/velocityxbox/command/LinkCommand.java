package net.kawaismp.velocityxbox.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kawaismp.velocityxbox.VelocityXbox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Optional;

public class LinkCommand {
    public static BrigadierCommand createBrigadierCommand(final VelocityXbox plugin) {
        LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder
                .<CommandSource>literal("link")
                .executes(context -> {
                    CommandSource source = context.getSource();

                    if (!(source instanceof Player player)) {
                        source.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    // Check if player is logged in
                    if (!plugin.getLoginManager().isLogged(player.getInternalUniqueId())) {
                        player.sendMessage(Component.text("You must be logged in to use this command.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    String username = player.getUsername();

                    // Check if account is already linked to Discord
                    plugin.getDatabaseManager().getAccountByUsername(username).thenAccept(accountOpt -> {
                        if (accountOpt.isEmpty()) {
                            player.sendMessage(Component.text("Account not found. Please contact an administrator.", NamedTextColor.RED));
                            return;
                        }

                        if (accountOpt.get().hasLinkedDiscord()) {
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("Discord Account Linking", NamedTextColor.GOLD, TextDecoration.BOLD));
                            player.sendMessage(Component.text("Your account is already linked to Discord!", NamedTextColor.RED));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("If you need to unlink your account, please contact an administrator.", NamedTextColor.GRAY));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                            player.sendMessage(Component.empty());
                            return;
                        }

                        // Check if player already has an active code
                        Optional<String> existingCode = plugin.getLinkCodeManager().getActiveCode(username);
                        if (existingCode.isPresent()) {
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                            player.sendMessage(Component.text("Discord Account Linking", NamedTextColor.GOLD, TextDecoration.BOLD));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("You already have an active verification code!", NamedTextColor.YELLOW));
                            player.sendMessage(Component.text("Go to KAWAISMP Discord and use: ", NamedTextColor.GRAY).append(Component.text("/verify " + existingCode.get(), NamedTextColor.AQUA)));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("Code expires in " + plugin.getConfigManager().getLinkCodeExpiration() + " minutes", NamedTextColor.GRAY));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                            player.sendMessage(Component.empty());
                            return;
                        }

                        // Generate new verification code
                        String code = plugin.getLinkCodeManager().createCode(username);

                        // Display the code to the player
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Discord Account Linking", NamedTextColor.GOLD, TextDecoration.BOLD));
                        player.sendMessage(Component.text("Your verification code is: ", NamedTextColor.GRAY).append(Component.text(code, NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Steps to link your account:", NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("  1. Open Discord KAWAISMP", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("  2. Run the command: ", NamedTextColor.GRAY).append(Component.text("/verify " + code, NamedTextColor.AQUA)));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("⚠ Code expires in " + plugin.getConfigManager().getLinkCodeExpiration() + " minutes", NamedTextColor.GRAY));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                        player.sendMessage(Component.empty());

                        plugin.getLogger().debug("Generated link code {} for player {}", code, username);
                    }).exceptionally(throwable -> {
                        plugin.getLogger().error("Error checking Discord link status for player {}", username, throwable);
                        player.sendMessage(Component.text("An error occurred while processing your request. Please try again later.", NamedTextColor.RED));
                        return null;
                    });

                    return Command.SINGLE_SUCCESS;
                })
                .build();

        return new BrigadierCommand(node);
    }
}

