package me.guild.titeenit.minigame.timedrace;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A timed race where each player's clock stops when they board the bus.
 * Lower times are better. Scores are returned as elapsed milliseconds;
 * players who did not finish receive Integer.MAX_VALUE (ranks last).
 *
 * The game ends automatically once all participants have finished,
 * or when stopped manually with /titeeni stop.
 */
public class TimedRaceGame extends Minigame {

    private final Map<UUID, Long> startTimes = new HashMap<>();
    private final Map<UUID, Integer> completionTimes = new HashMap<>(); // milliseconds
    private BukkitTask tickTask;
    private int remainingSeconds = getTimerSeconds();

    public TimedRaceGame(TiteeniPlugin plugin, java.util.List<Player> players) {
        super(plugin, players);
    }

    @Override
    public String getDisplayName() { return "Timed Race"; }

    @Override
    public boolean isLowerScoreBetter() { return true; }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        long now = System.currentTimeMillis();
        for (Player p : getOnlinePlayers()) {
            startTimes.put(p.getUniqueId(), now);
        }

        // Action bar: show each player their elapsed time every second
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (state != MinigameState.RUNNING) return;
            long tick = System.currentTimeMillis();
            for (Player p : getOnlinePlayers()) {
                UUID uid = p.getUniqueId();
                String remaining = " | " + formatRemaining(remainingSeconds);
                if (completionTimes.containsKey(uid)) {
                    p.sendActionBar(Component.text(
                            "Finished: " + formatTime(completionTimes.get(uid)) + remaining,
                            NamedTextColor.GREEN));
                } else {
                    Long start = startTimes.get(uid);
                    if (start == null) continue;
                    int elapsed = (int) (tick - start);
                    p.sendActionBar(Component.text(
                            "Time: " + formatTime(elapsed) + remaining, NamedTextColor.YELLOW));
                }
            }
        }, 1L, 1L);

        broadcast(Component.text(
                "Race started! Make your way to the finish as fast as possible!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        if (state == MinigameState.ENDED) return;
        state = MinigameState.ENDED;
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        broadcastScores();
        broadcast(Component.text("Race ended!", NamedTextColor.GOLD));
    }

    @Override
    public void onPlayerQuit(Player player) {
        players.remove(player);
        if (getOnlinePlayers().isEmpty()) {
            plugin.getMinigameManager().onGameSelfTerminated();
            stop();
        }
    }

    // -------------------------------------------------------------------------
    // Bus boarding = finish line
    // -------------------------------------------------------------------------

    @Override
    public void onPlayerBoarded(Player player) {
        if (!isParticipant(player) || state != MinigameState.RUNNING) return;
        UUID uid = player.getUniqueId();
        if (completionTimes.containsKey(uid)) return; // already finished

        Long start = startTimes.get(uid);
        if (start == null) return;

        int elapsed = (int) (System.currentTimeMillis() - start);
        completionTimes.put(uid, elapsed);

        int place = completionTimes.size();
        broadcast(Component.text(
                player.getName() + " finished in " + formatTime(elapsed)
                        + "! (place #" + place + ")",
                NamedTextColor.AQUA));

        // End when all online participants have finished
        boolean allDone = getOnlinePlayers().stream()
                .allMatch(p -> completionTimes.containsKey(p.getUniqueId()));
        if (allDone) {
            plugin.getMinigameManager().onGameSelfTerminated();
            stop();
        }
    }

    // -------------------------------------------------------------------------
    // Scores: elapsed ms, Integer.MAX_VALUE for DNF
    // -------------------------------------------------------------------------

    @Override
    public Map<UUID, Integer> getScores() {
        Map<UUID, Integer> scores = new HashMap<>();
        for (Player p : players) {
            scores.put(p.getUniqueId(),
                    completionTimes.getOrDefault(p.getUniqueId(), Integer.MAX_VALUE));
        }
        return Collections.unmodifiableMap(scores);
    }

    @Override
    public void onTimerTick(int secondsLeft) {
        remainingSeconds = secondsLeft; // tickTask picks this up on its next run
    }

    // -------------------------------------------------------------------------
    // Scores display
    // -------------------------------------------------------------------------

    @Override
    protected void broadcastScores() {
        showTopPerformersTitle();
        broadcastAll(Component.text("=== " + getDisplayName() + " — Final Scores ===", NamedTextColor.GOLD));
        getScores().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()) // lower time = better
                .forEach(entry -> broadcastAll(Component.text(
                        displayName(entry.getKey()) + ": " + formatTime(entry.getValue()),
                        NamedTextColor.YELLOW)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    protected static String formatTime(int ms) {
        if (ms == Integer.MAX_VALUE) return "DNF";
        int minutes = ms / 60_000;
        int seconds = (ms % 60_000) / 1000;
        int tenths = (ms % 1000) / 100;
        return String.format("%d:%02d.%d", minutes, seconds, tenths);
    }
}
