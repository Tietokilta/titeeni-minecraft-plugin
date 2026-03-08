package me.guild.titeenit;

import java.util.Optional;

public enum Guild {
    TIK("TiK"),
    TITE("TiTe"),
    DG("DG"),
    DIGIT("Digit"),
    DATE("DaTe"),
    CLUSTER("Cluster"),
    SOSA("SoSa"),
    TUTTI("Tutti"),
    ALGO("Algo"),
    OTIT("OTiT");

    private final String displayName;

    Guild(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Case-insensitive parse from the enum constant name (e.g. "data_guild" → DATA_GUILD). */
    public static Optional<Guild> fromString(String input) {
        for (Guild g : values()) {
            if (g.name().equalsIgnoreCase(input)) return Optional.of(g);
        }
        return Optional.empty();
    }
}
