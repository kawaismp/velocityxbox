package net.kawaismp.velocityxbox.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kawaismp.velocityxbox.VelocityXbox;

import static net.kawaismp.velocityxbox.util.SoundUtil.playErrorSound;
import static net.kawaismp.velocityxbox.util.SoundUtil.playSuccessSound;

public final class UnlinkCommand {

    public static BrigadierCommand createBrigadierCommand(final VelocityXbox plugin) {
        LiteralArgumentBuilder<CommandSource> unlinkCommand = BrigadierCommand.literalArgumentBuilder("unlink")
                .requires(commandSource -> commandSource instanceof Player)
                .executes(context -> executeUnlinkCommand(context.getSource(), plugin));

        LiteralCommandNode<CommandSource> unlinkNode = unlinkCommand.build();
        return new BrigadierCommand(unlinkNode);
    }

    private static int executeUnlinkCommand(CommandSource source, VelocityXbox plugin) {
        if (!(source instanceof Player player)) {
            return Command.SINGLE_SUCCESS;
        }

        // Check if player is logged in
        if (!plugin.getLoginManager().isLogged(player.getInternalUniqueId())) {
            player.sendMessage(plugin.getMessageProvider().getErrorNotLoggedIn());
            playErrorSound(player);
            return Command.SINGLE_SUCCESS;
        }

        // Get account ID from player's UUID
        String accountId = player.getUniqueId().toString();

        // Check if account has linked Xbox account
        plugin.getDatabaseManager().hasLinkedXbox(accountId)
                .thenCompose(hasLinked -> {
                    if (!hasLinked) {
                        // Schedule on main thread
                        plugin.getProxy().getScheduler()
                                .buildTask(plugin, () -> {
                                    player.sendMessage(plugin.getMessageProvider().getErrorNotLinked());
                                    playErrorSound(player);
                                })
                                .schedule();

                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }

                    // Unlink the Xbox account
                    return plugin.getDatabaseManager().unlinkXboxAccount(accountId);
                })
                .thenAccept(success -> {
                    if (success) {
                        // Schedule on main thread
                        plugin.getProxy().getScheduler()
                                .buildTask(plugin, () -> {
                                    player.sendMessage(plugin.getMessageProvider().getUnlinkSuccess());
                                    playSuccessSound(player);
                                    plugin.getLogger().info("Player {} successfully unlinked their Xbox account",
                                            player.getUsername());
                                })
                                .schedule();
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().error("Error unlinking Xbox account for player {}", player.getUsername(), ex);

                    // Schedule on main thread
                    plugin.getProxy().getScheduler()
                            .buildTask(plugin, () -> {
                                player.sendMessage(plugin.getMessageProvider().getErrorDatabase());
                                playErrorSound(player);
                            })
                            .schedule();

                    return null;
                });

        return Command.SINGLE_SUCCESS;
    }
}