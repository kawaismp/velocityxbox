package net.kawaismp.velocityxbox.database.model;

import java.util.Objects;

public class Account {
    private final String id;
    private final String username;
    private final String passwordHash;
    private final String xboxUserId;

    public Account(String id, String username, String passwordHash, String xboxUserId) {
        this.id = Objects.requireNonNull(id, "Account ID cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.passwordHash = passwordHash; // Can be null for auto-login
        this.xboxUserId = xboxUserId; // Can be null if not linked
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getXboxUserId() {
        return xboxUserId;
    }

    public boolean hasPasswordHash() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    public boolean hasLinkedXbox() {
        return xboxUserId != null && !xboxUserId.isEmpty();
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