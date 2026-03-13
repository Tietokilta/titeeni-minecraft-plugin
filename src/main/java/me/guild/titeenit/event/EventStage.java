package me.guild.titeenit.event;

import java.util.Optional;

public enum EventStage {
    TURKU(   "Turku",           "Race to the finish",                         "boatrace"),
    TAMPERE( "Tampere",         "Complete the parkour and board the bus",      "tampererace"),
    VAASA(   "Vaasa",           "Trade up the item chain",                     "tradeup"),
    OULU(    "Oulu",            "Shoot the targets",                           "shootinggallery"),
    JYVASKYLA("Jyväskylä",     "Find the hidden buttons to score",            "findthebutton"),
    LAPPEENRANTA("Lappeenranta","Replicate the model structure to score",      "copythebuild"),
    LAHTI(   "Lahti",           "King of the Hill",                      "kingofthehill"),
    OTANIEMI("Otaniemi",        "Play jallupöllö","jallupollo");

    private final String displayName;
    private final String subtitle;
    private final String minigameKey;

    EventStage(String displayName, String subtitle, String minigameKey) {
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.minigameKey = minigameKey;
    }

    public String getDisplayName() { return displayName; }
    public String getSubtitle()    { return subtitle; }
    public String getMinigameKey() { return minigameKey; }
    public boolean hasMinigame()   { return minigameKey != null; }

    /** Returns the next stage, or empty if this is the last. */
    public Optional<EventStage> next() {
        EventStage[] values = values();
        int idx = ordinal() + 1;
        return idx < values.length ? Optional.of(values[idx]) : Optional.empty();
    }

    /** Case-insensitive parse from enum constant name. */
    public static Optional<EventStage> fromString(String input) {
        for (EventStage s : values()) {
            if (s.name().equalsIgnoreCase(input)) return Optional.of(s);
        }
        return Optional.empty();
    }
}
