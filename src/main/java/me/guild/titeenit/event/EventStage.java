package me.guild.titeenit.event;

import java.util.Optional;

public enum EventStage {
    AINOA("Ainoa (Tapiola)", "timedrace"),
    TURKU("Turku", "timedrace"),
    TAMPERE("Tampere", "timedrace"),
    VAASA("Vaasa", null),
    OULU("Oulu", null),
    JYVASKYLA("Jyväskylä", "findthebutton"),
    LAPPEENRANTA("Lappeenranta", "copythebuild"),
    LAHTI("Lahti", "kingofthehill"),
    OTANIEMI("Otaniemi", "jallupollo");

    private final String displayName;
    private final String minigameKey;

    EventStage(String displayName, String minigameKey) {
        this.displayName = displayName;
        this.minigameKey = minigameKey;
    }

    public String getDisplayName() { return displayName; }
    public String getMinigameKey() { return minigameKey; }
    public boolean hasMinigame() { return minigameKey != null; }

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
