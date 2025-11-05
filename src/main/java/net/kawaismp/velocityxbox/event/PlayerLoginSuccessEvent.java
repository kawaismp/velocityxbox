package net.kawaismp.velocityxbox.event;

import com.velocitypowered.api.proxy.Player;
import net.kawaismp.velocityxbox.database.model.Account;

/**
 * Called when a player successfully logs in through the VelocityXbox authentication system.
 * This event is fired after the player's credentials have been validated and before they
 * are transferred to the main server.
 */
public class PlayerLoginSuccessEvent {

    private final Player player;
    private final Account account;
    private final LoginMethod loginMethod;
    private final long loginTimestamp;

    /**
     * Creates a new PlayerLoginSuccessEvent
     *
     * @param player The player who logged in
     * @param account The account that was logged into
     * @param loginMethod The method used to login
     */
    public PlayerLoginSuccessEvent(Player player, Account account, LoginMethod loginMethod) {
        this.player = player;
        this.account = account;
        this.loginMethod = loginMethod;
        this.loginTimestamp = System.currentTimeMillis();
    }

    /**
     * Gets the player who logged in
     *
     * @return The player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the account that was logged into
     *
     * @return The account
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Gets the method used to login
     *
     * @return The login method
     */
    public LoginMethod getLoginMethod() {
        return loginMethod;
    }

    /**
     * Gets the timestamp when the login occurred
     *
     * @return The login timestamp in milliseconds
     */
    public long getLoginTimestamp() {
        return loginTimestamp;
    }

    /**
     * Enum representing the different methods a player can use to login
     */
    public enum LoginMethod {
        /**
         * Player logged in using the /login command with password
         */
        PASSWORD,

        /**
         * Bedrock player logged in automatically via Xbox/XUID linking
         */
        XBOX_AUTO_LOGIN,

        /**
         * Player logged in automatically from a cached session
         */
        SESSION_CACHE
    }
}

