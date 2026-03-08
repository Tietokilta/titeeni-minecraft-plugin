package me.guild.titeenit.minigame.copythebuild;

public final class CopyTheBuildConfig {

    private CopyTheBuildConfig() {}

    /** Game duration in seconds. */
    public static final int GAME_DURATION_SECONDS = 300;

    /** Side length of each model/build plot (5 → 5×5×5 = 125 blocks). */
    public static final int PLOT_SIZE = 5;

    /** How often (in ticks) to check whether a player's build matches the model. */
    public static final long CHECK_INTERVAL_TICKS = 10L;

    /** Points awarded per completed build. */
    public static final int POINTS_PER_BUILD = 1;

    /**
     * Z-axis offset from a build plot corner to its paired preview plot corner.
     * Preview is placed PLOT_SIZE blocks (the plot itself) + 5-block gap away.
     */
    public static final int PREVIEW_OFFSET_Z = PLOT_SIZE + 5;
}
