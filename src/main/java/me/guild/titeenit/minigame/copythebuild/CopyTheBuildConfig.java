package me.guild.titeenit.minigame.copythebuild;

public final class CopyTheBuildConfig {

    private CopyTheBuildConfig() {}

    public static final int PLOT_SIZE = 5;
    public static final int PLOT_HEIGHT = 15;

    /** How often (in ticks) to check whether a player's build matches the model. */
    public static final long CHECK_INTERVAL_TICKS = 10L;

    /** Points awarded per completed build. */
    public static final int POINTS_PER_BUILD = 1;

    /**
     * X-axis offset from a build plot corner to its paired preview plot corner.
     * Preview is placed PLOT_SIZE blocks (the plot itself) + 3-block gap away.
     */
    public static final int PREVIEW_OFFSET_X = PLOT_SIZE + 3;
}
