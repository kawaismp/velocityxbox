package net.kawaismp.velocityxbox.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class MessageProvider {
    private static final String WEBSITE_URL = "https://kawaismp.net";
    private static final String REGISTER_URL = "https://kawaismp.net/register";
    private static final String DISCORD_URL = "https://kawaismp.net/discord";
    private static final String DISCORD_DISPLAY = "kawaismp.net/discord";

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
    private final Component errorRegistrationLimitReached;

    // Player transfer message
    private final Component playerTransferredLastServer;

    // Verification reminder messages
    private final Component verificationReminder;

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
        this.errorRegistrationLimitReached = createRegistrationLimitReached();

        // Player transfer message
        this.playerTransferredLastServer = createMessage("Kamu telah dipindahkan secara otomatis ke server terakhir kamu!", NamedTextColor.GREEN);

        // Verification reminder message
        this.verificationReminder = createVerificationReminder();
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

    private Component createRegistrationLimitReached() {
        return Component.text("(", NamedTextColor.GRAY)
                .append(Component.text("!", NamedTextColor.RED))
                .append(Component.text(") » ", NamedTextColor.GRAY))
                .append(Component.text("Akun tidak dapat dibuat! Silahkan membuat akun melalui website: ", NamedTextColor.RED))
                .append(Component.text(REGISTER_URL, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(REGISTER_URL))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk membuka halaman registrasi"))));
    }

    private Component createVerificationReminder() {
        return Component.empty()
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH))
                .append(Component.text("\n\n"))
                .append(Component.text("⚠ ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("VERIFIKASI AKUN DIPERLUKAN", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ⚠", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("\n\n"))
                .append(Component.text("Akun kamu belum terverifikasi! Gunakan perintah ", NamedTextColor.YELLOW))
                .append(Component.text("/link", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/link"))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk menyalin perintah"))))
                .append(Component.text(" untuk memverifikasi akun kamu dengan Discord.", NamedTextColor.YELLOW))
                .append(Component.text("\n\n"))
                .append(Component.text("📌 MENGAPA VERIFIKASI ITU PENTING?", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("\n\n"))
                .append(Component.text("1. ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("Reset Password", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("\n   ", NamedTextColor.GRAY))
                .append(Component.text("Tanpa verifikasi Discord, kamu TIDAK BISA mereset password!", NamedTextColor.RED))
                .append(Component.text("\n   ", NamedTextColor.GRAY))
                .append(Component.text("Jika kamu lupa password, akun kamu akan HILANG selamanya.", NamedTextColor.RED))
                .append(Component.text("\n\n"))
                .append(Component.text("2. ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("Sinkronisasi Stats & Ranks", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("\n   ", NamedTextColor.GRAY))
                .append(Component.text("Verifikasi memungkinkan sinkronisasi otomatis:", NamedTextColor.GRAY))
                .append(Component.text("\n   • ", NamedTextColor.GRAY))
                .append(Component.text("Rank in-game ↔ Role Discord", NamedTextColor.AQUA))
                .append(Component.text("\n   • ", NamedTextColor.GRAY))
                .append(Component.text("Stats, achievements, dan progress", NamedTextColor.AQUA))
                .append(Component.text("\n   • ", NamedTextColor.GRAY))
                .append(Component.text("Akses fitur eksklusif komunitas", NamedTextColor.AQUA))
                .append(Component.text("\n\n"))
                .append(Component.text("🔗 CARA VERIFIKASI (MUDAH!):", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("\n\n"))
                .append(Component.text("Langkah 1: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Ketik ", NamedTextColor.WHITE))
                .append(Component.text("/link", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand("/link"))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk menyalin"))))
                .append(Component.text(" di chat", NamedTextColor.WHITE))
                .append(Component.text("\n"))
                .append(Component.text("Langkah 2: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Kamu akan mendapat kode 6 digit", NamedTextColor.WHITE))
                .append(Component.text("\n"))
                .append(Component.text("Langkah 3: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Join Discord server kami di ", NamedTextColor.WHITE))
                .append(Component.text(DISCORD_DISPLAY, NamedTextColor.DARK_AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(DISCORD_URL))
                        .hoverEvent(HoverEvent.showText(Component.text("Klik untuk join Discord"))))
                .append(Component.text("\n"))
                .append(Component.text("Langkah 4: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Ketik ", NamedTextColor.WHITE))
                .append(Component.text("/verify <kode>", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" di Discord server", NamedTextColor.WHITE))
                .append(Component.text("\n"))
                .append(Component.text("Langkah 5: ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("Selesai! Akun kamu sudah terverifikasi ✓", NamedTextColor.GREEN))
                .append(Component.text("\n\n"))
                .append(Component.text("\n"))
                .append(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GRAY, TextDecoration.STRIKETHROUGH));
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

    public Component getErrorRegistrationLimitReached() {
        return errorRegistrationLimitReached;
    }

    public Component getPlayerTransferredLastServer() {
        return playerTransferredLastServer;
    }

    public Component getVerificationReminder() {
        return verificationReminder;
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