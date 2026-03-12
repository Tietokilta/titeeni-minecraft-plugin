package me.guild.titeenit.minigame.jallupollo;

/**
 * Tunable constants for Jallupöllö
 */
public final class JallupolloConfig {

    private JallupolloConfig() {}

    // -------------------------------------------------------------------------
    // Drinking
    // -------------------------------------------------------------------------

    /** Points awarded to the hider per second while they are actively drinking. */
    public static final int POINTS_PER_SECOND = 1;

    /** How often the game tick runs, in ticks. */
    public static final long TICK_INTERVAL_TICKS = 5L;

    /**
     * Consecutive non-drinking game-tick checks before the drinking session is
     * considered ended. With the short consume time, we need a larger grace
     * period to bridge the gap between "finish" (cancel) and "start again".
     * 10 checks * 5 ticks/check = 50 ticks = 2.5 seconds.
     */
    public static final int STOP_DRINK_GRACE_CHECKS = 10;

    // -------------------------------------------------------------------------
    // Sound
    //
    // The reveal sound is sent individually to each seeker via player.playSound()
    // positioned at the hider's location (3-D direction preserved).  We
    // calculate the source volume so Minecraft's linear attenuation model
    //   hearLevel = 1 - dist / (16 × sourceVolume)
    // delivers exactly the desired level at every distance:
    //   • 0–SOUND_FULL_RANGE blocks : full volume
    //   • SOUND_FULL_RANGE–SOUND_FADE_END : linear fade to SOUND_MIN_VOLUME
    //   • beyond SOUND_FADE_END        : constant SOUND_MIN_VOLUME (never silent)
    // -------------------------------------------------------------------------

    /** Distance (blocks) within which seekers hear the sound at full volume. */
    public static final float SOUND_FULL_RANGE  = 32f;

    /** Distance (blocks) at which the volume reaches its minimum floor. */
    public static final float SOUND_FADE_END    = 200f;

    /** Minimum hear-level (0–1) that seekers receive regardless of distance. */
    public static final float SOUND_MIN_VOLUME  = 0.2f;

    public static final float SOUND_PITCH = 1.0f;

    // -------------------------------------------------------------------------
    // Catch effects
    // -------------------------------------------------------------------------

    public static final double NEARBY_EFFECT_RADIUS = 20.0;

    // 20 ticks per second
    public static final int DEBUFF_DURATION_TICKS = 20 * 10;
    public static final int SLOWNESS_AMPLIFIER = 4;
    public static final int GLOW_DURATION_TICKS = 100; // 5 seconds
    public static final int HIDER_SPEED_DEGRADATION_TICKS = 200;
}
