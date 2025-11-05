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
                        source.sendMessage(Component.text("Perintah ini hanya dapat digunakan oleh pemain.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    // Check if player is logged in
                    if (!plugin.getLoginManager().isLogged(player.getInternalUniqueId())) {
                        player.sendMessage(Component.text("Anda harus login terlebih dahulu untuk menggunakan perintah ini.", NamedTextColor.RED));
                        return Command.SINGLE_SUCCESS;
                    }

                    String username = player.getUsername();

                    // Check if account is already linked to Discord
                    plugin.getDatabaseManager().getAccountByUsername(username).thenAccept(accountOpt -> {
                        if (accountOpt.isEmpty()) {
                            player.sendMessage(Component.text("Akun tidak ditemukan. Silakan hubungi admin.", NamedTextColor.RED));
                            return;
                        }

                        if (accountOpt.get().hasLinkedDiscord()) {
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("Discord Account Linking", NamedTextColor.GOLD, TextDecoration.BOLD));
                            player.sendMessage(Component.text("Akun Anda sudah terhubung dengan Discord!", NamedTextColor.RED));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("Jika Anda perlu memutuskan hubungan akun, silakan hubungi admin.", NamedTextColor.GRAY));
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
                            player.sendMessage(Component.text("Anda memiliki kode verifikasi yang aktif!", NamedTextColor.YELLOW));
                            player.sendMessage(Component.text("Pergi ke Discord KAWAISMP dan gunakan: ", NamedTextColor.GRAY).append(Component.text("/verify " + existingCode.get(), NamedTextColor.AQUA)));
                            player.sendMessage(Component.empty());
                            player.sendMessage(Component.text("Kode akan kadaluarsa dalam " + plugin.getConfigManager().getLinkCodeExpiration() + " menit", NamedTextColor.GRAY));
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
                        player.sendMessage(Component.text("Kode verifikasi Anda adalah: ", NamedTextColor.GRAY).append(Component.text(code, NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("Langkah-langkah menghubungkan akun:", NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("  1. Buka Discord KAWAISMP", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("  2. Jalankan command: ", NamedTextColor.GRAY).append(Component.text("/verify " + code, NamedTextColor.AQUA)));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("⚠ Kode akan kadaluarsa dalam " + plugin.getConfigManager().getLinkCodeExpiration() + " menit", NamedTextColor.GRAY));
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.WHITE));
                        player.sendMessage(Component.empty());

                        plugin.getLogger().debug("Generated link code {} for player {}", code, username);
                    }).exceptionally(throwable -> {
                        plugin.getLogger().error("Error checking Discord link status for player {}", username, throwable);
                        player.sendMessage(Component.text("Terjadi kesalahan saat memproses permintaan Anda. Silakan coba lagi nanti.", NamedTextColor.RED));
                        return null;
                    });

                    return Command.SINGLE_SUCCESS;
                })
                .build();

        return new BrigadierCommand(node);
    }
}