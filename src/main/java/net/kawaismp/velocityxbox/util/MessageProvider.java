package net.kawaismp.velocityxbox.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class MessageProvider {
    private static final String WEBSITE_URL = "https://kawaismp.net";
    private static final String REGISTER_URL = "https://kawaismp.net/register";
    private static final String DISCORD_URL = "https://discord.com/invite/PXafvVujt3";
    private static final String DISCORD_DISPLAY = "https://discord.gg/PXafvVujt3";

    // Login messages
    private final Component loginSuccess;
    private final Component autoLoginSuccess;
    private final Component sessionLoginSuccess;
    private final Component commandDenied;
    private final Component alreadyLoggedIn;

    // Registration info
    private final Component registerInfo;
    private final Component registerInfoKicked;
    private final Component helpInfo;
    private final Component loginTimeout;

    // Error messages
    private final Component errorEmptyCredentials;
    private final Component errorInvalidCredentials;
    private final Component errorDatabase;
    private final Component errorGeneric;
    private final Component errorLoggingIn;
    private final Component errorTooManyLoginAttempts;

    // Unlink messages
    private final Component errorNotLoggedIn;
    private final Component errorNotLinked;
    private final Component unlinkSuccess;

    // Register messages
    private final Component registerSuccess;
    private final Component registeringMessage;
    private final Component errorPasswordMismatch;
    private final Component errorInvalidUsername;
    private final Component errorInvalidPasswordLength;
    private final Component errorUsernameExists;

    // Player transfer message
    private final Component playerTransferredLastServer;

    public MessageProvider() {
        // Login messages
        this.loginSuccess = createLoginSuccess();
        this.autoLoginSuccess = createAutoLoginSuccess();
        this.sessionLoginSuccess = createSessionLoginSuccess();
        this.commandDenied = Component.text("You can only use /login command!", NamedTextColor.RED);
        this.alreadyLoggedIn = Component.text("You are already logged in!", NamedTextColor.RED);

        // Registration info
        this.registerInfo = createRegisterInfo();
        this.registerInfoKicked = createRegisterInfoKicked();
        this.helpInfo = createHelpInfo();
        this.loginTimeout = createLoginTimeout();

        // Error messages
        this.errorEmptyCredentials = createMessage("Username atau kata sandi tidak boleh kosong!", NamedTextColor.RED);
        this.errorInvalidCredentials = createMessage("Username atau kata sandi tidak valid!", NamedTextColor.RED);
        this.errorDatabase = createMessage("Koneksi ke database gagal!", NamedTextColor.RED);
        this.errorGeneric = createMessage("Terjadi kesalahan saat mencoba masuk!", NamedTextColor.RED);
        this.errorLoggingIn = createMessage("Sedang masuk...", NamedTextColor.GRAY);
        this.errorTooManyLoginAttempts = createMessage("Terlalu banyak percobaan masuk! Silahkan coba lagi nanti.", NamedTextColor.RED);

        // Unlink messages
        this.errorNotLoggedIn = createMessage("Kamu harus login terlebih dahulu untuk menggunakan command ini!", NamedTextColor.RED);
        this.errorNotLinked = createMessage("Akun kamu tidak terhubung dengan Xbox!", NamedTextColor.YELLOW);
        this.unlinkSuccess = createMessage("Akun Xbox kamu telah berhasil diputuskan! Kamu perlu login manual di lain waktu.", NamedTextColor.GREEN);

        // Register messages
        this.registerSuccess = createMessage("Pendaftaran berhasil! Silahkan masuk ke akun kamu.", NamedTextColor.GREEN);
        this.registeringMessage = createMessage("Sedang mendaftar...", NamedTextColor.GRAY);
        this.errorPasswordMismatch = createMessage("Kata sandi yang dimasukkan tidak sama!", NamedTextColor.RED);
        this.errorInvalidUsername = createMessage("Username hanya boleh mengandung huruf, angka, dan garis bawah!", NamedTextColor.RED);
        this.errorInvalidPasswordLength = createMessage("Kata sandi harus terdiri dari minimal 6 karakter!", NamedTextColor.RED);
        this.errorUsernameExists = createMessage("Username sudah terdaftar! Silahkan pilih username lain.", NamedTextColor.RED);

        // Player transfer message
        this.playerTransferredLastServer = createMessage("Kamu telah dipindahkan secara otomatis ke server terakhir kamu!", NamedTextColor.GREEN);
    }

    private Component createMessage(String text, NamedTextColor color) {
        return Component.text("(", NamedTextColor.GRAY)
                .append(Component.text("!", color))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text(text, color));
    }

    private Component createLoginSuccess() {
        return Component.text("\n".repeat(33), NamedTextColor.WHITE)
                .append(createMessage("Kamu telah berhasil masuk!", NamedTextColor.GRAY));
    }

    private Component createAutoLoginSuccess() {
        return Component.text("\n".repeat(15), NamedTextColor.WHITE)
                .append(Component.text("(", NamedTextColor.GRAY))
                .append(Component.text("!", NamedTextColor.AQUA))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text("Kamu telah berhasil masuk dengan akun yang terhubung! Gunakan ", NamedTextColor.GRAY))
                .append(Component.text("/unlink", NamedTextColor.YELLOW))
                .append(Component.text(" untuk memutuskan tautan akun.", NamedTextColor.GRAY));
    }

    private Component createSessionLoginSuccess() {
        return Component.text("\n".repeat(15), NamedTextColor.WHITE)
                .append(Component.text("(", NamedTextColor.GRAY))
                .append(Component.text("!", NamedTextColor.GREEN))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text("Kamu telah berhasil masuk otomatis dari sesi sebelumnya!", NamedTextColor.GRAY));
    }

    private Component createRegisterInfo() {
        return Component.text("\n".repeat(15), NamedTextColor.WHITE)
                .append(Component.text("[!] Jika anda belum memiliki akun, silahkan daftar menggunakan /register atau di website ", NamedTextColor.YELLOW))
                .append(Component.text(WEBSITE_URL, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(REGISTER_URL))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk membuka website"))));
    }

    private Component createRegisterInfoKicked() {
        return Component.text("\n[!] Jika anda belum memiliki akun, silahkan daftar menggunakan /register atau di website ", NamedTextColor.YELLOW)
                .append(Component.text(REGISTER_URL, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(REGISTER_URL))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk membuka website"))));
    }

    private Component createHelpInfo() {
        return Component.text("\n\n» Perlu bantuan? Join discord kami di ", NamedTextColor.GRAY)
                .append(Component.text(DISCORD_DISPLAY, NamedTextColor.DARK_AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(DISCORD_URL))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk membuka discord"))));
    }

    private Component createLoginTimeout() {
        return createRegisterInfoKicked();
    }

    // Getters
    public Component getLoginSuccess() {
        return loginSuccess;
    }

    public Component getAutoLoginSuccess() {
        return autoLoginSuccess;
    }

    public Component getSessionLoginSuccess() {
        return sessionLoginSuccess;
    }

    public Component getCommandDenied() {
        return commandDenied;
    }

    public Component getAlreadyLoggedIn() {
        return alreadyLoggedIn;
    }

    public Component getRegisterInfo() {
        return registerInfo;
    }

    public Component getRegisterInfoKicked() {
        return registerInfoKicked;
    }

    public Component getHelpInfo() {
        return helpInfo;
    }

    public Component getLoginTimeout() {
        return loginTimeout;
    }

    public Component getErrorEmptyCredentials() {
        return errorEmptyCredentials;
    }

    public Component getErrorInvalidCredentials() {
        return errorInvalidCredentials;
    }

    public Component getErrorDatabase() {
        return errorDatabase;
    }

    public Component getErrorGeneric() {
        return errorGeneric;
    }

    public Component getErrorLoggingIn() {
        return errorLoggingIn;
    }

    public Component getErrorNotLoggedIn() {
        return errorNotLoggedIn;
    }

    public Component getErrorNotLinked() {
        return errorNotLinked;
    }

    public Component getErrorTooManyLoginAttempts() {
        return errorTooManyLoginAttempts;
    }

    public Component getUnlinkSuccess() {
        return unlinkSuccess;
    }

    public Component getRegisterSuccess() {
        return registerSuccess;
    }

    public Component getRegisteringMessage() {
        return registeringMessage;
    }

    public Component getErrorPasswordMismatch() {
        return errorPasswordMismatch;
    }

    public Component getErrorInvalidUsername() {
        return errorInvalidUsername;
    }

    public Component getErrorInvalidPasswordLength() {
        return errorInvalidPasswordLength;
    }

    public Component getErrorUsernameExists() {
        return errorUsernameExists;
    }

    public Component getPlayerTransferredLastServer() {
        return playerTransferredLastServer;
    }

    public Component createLoginAsMessage(String username) {
        return Component.text("(", NamedTextColor.GRAY)
                .append(Component.text("!", NamedTextColor.AQUA))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text("Masuk sebagai ", NamedTextColor.GRAY))
                .append(Component.text(username, NamedTextColor.YELLOW));
    }

    public Component createRegisteredAsMessage(String username) {
        return Component.text("(", NamedTextColor.GRAY)
                .append(Component.text("!", NamedTextColor.GREEN))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text("Terdaftar sebagai ", NamedTextColor.GRAY))
                .append(Component.text(username, NamedTextColor.YELLOW));
    }
}