package net.kawaismp.velocityxbox.database.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public record Account(String id, String username, String passwordHash, String xboxUserId, String discordId) {
    public Account(String id, String username, String passwordHash, String xboxUserId, String discordId) {
        this.id = Objects.requireNonNull(id, "Account ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.passwordHash = passwordHash; // Can be null for auto-login
        this.xboxUserId = xboxUserId; // Can be null if not linked
        this.discordId = discordId; // Can be null if not linked
    }

    public boolean hasPasswordHash() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean hasLinkedXbox() {
        return xboxUserId != null && !xboxUserId.isEmpty();
    }

    public boolean hasLinkedDiscord() {
        return discordId != null && !discordId.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @NotNull
    @Override
    public String toString() {
        return "Account{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", hasPassword=" + hasPasswordHash() +
                ", hasLinkedXbox=" + hasLinkedXbox() +
                '}';
    }
}