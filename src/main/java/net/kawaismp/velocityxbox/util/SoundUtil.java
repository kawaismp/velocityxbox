package net.kawaismp.velocityxbox.util;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

/**
 * Utility class for playing sounds to players
 */
public final class SoundUtil {

    private SoundUtil() {
        // Utility class
    }

    /**
     * Play error sound (villager no)
     */
    public static void playErrorSound(Player player) {
        player.playSound(
                Sound.sound(
                        Key.key("entity.villager.no"),
                        Sound.Source.AMBIENT,
                        1f,
                        1f
                ),
                Sound.Emitter.self()
        );
    }

    /**
     * Play success sound (level up)
     */
    public static void playSuccessSound(Player player) {
        player.playSound(
                Sound.sound(
                        Key.key("entity.player.levelup"),
                        Sound.Source.AMBIENT,
                        1f,
                        1f
                ),
                Sound.Emitter.self()
        );
    }

    /**
     * Play processing sound (experience orb pickup)
     */
    public static void playProcessingSound(Player player) {
        player.playSound(
                Sound.sound(
                        Key.key("entity.experience_orb.pickup"),
                        Sound.Source.AMBIENT,
                        1f,
                        1f
                ),
                Sound.Emitter.self()
        );
    }

    /**
     * Play a custom sound
     */
    public static void playSound(Player player, String soundKey, float volume, float pitch) {
        player.playSound(
                Sound.sound(
                        Key.key(soundKey),
                        Sound.Source.AMBIENT,
                        volume,
                        pitch
                ),
                Sound.Emitter.self()
        );
    }
}