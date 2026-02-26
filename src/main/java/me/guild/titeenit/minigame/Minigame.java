package me.guild.titeenit.minigame;

import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for all event minigames.
 *
 * Subclasses implement the game-specific logic. The MinigameManager owns the
 * lifecycle (start / stop) and routes player-quit events here.
 *
 * To add a new minigame:
 *   1. Extend Minigame.
 *   2. Implement start(), stop(), onPlayerQuit(), getDisplayName(), and getScores().
 *   3. Register it in TiteeniCommand.handleStart().
 */
public abstract class Minigame {

    protected final TiteeniPlugin plugin;
    protected final List<Player> players;
    protected MinigameState state;

    public Minigame(TiteeniPlugin plugin, List<Player> players) {
        this.plugin = plugin;
        // Mutable defensive copy so callers cannot affect the internal list
        this.players = new ArrayList<>(players);
        this.state = MinigameState.WAITING;
    }

    // -------------------------------------------------------------------------
    // Lifecycle — must be implemented by subclasses
    // -------------------------------------------------------------------------

    /** Called by MinigameManager when the game should begin. */
    public abstract void start();

    /** Called by MinigameManager (or the game itself) to end the game cleanly. */
    public abstract void stop();

    /** Called when a player in the game disconnects. */
    public abstract void onPlayerQuit(Player player);

    /** Human-readable name shown in messages and score headers. */
    public abstract String getDisplayName();

    /**
     * Current scores, keyed by player UUID. Used by /titeeni score.
     */
    public abstract Map<UUID, Integer> getScores();

    // -------------------------------------------------------------------------
    // Helpers available to all subclasses
    // -------------------------------------------------------------------------

    protected List<Player> getOnlinePlayers() {
        return players.stream().filter(Player::isOnline).toList();
    }

    protected void broadcast(Component message) {
        for (Player p : getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    protected void broadcastActionBar(Component message) {
        for (Player p : getOnlinePlayers()) {
            p.sendActionBar(message);
        }
    }

    protected void broadcastScores() {
        broadcast(Component.text("=== " + getDisplayName() + " — Final Scores ===", NamedTextColor.GOLD));
        getScores().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    String name = p != null ? p.getName() : entry.getKey().toString();
                    broadcast(Component.text(name + ": " + entry.getValue() + " points", NamedTextColor.YELLOW));
                });
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public MinigameState getState() {
        return state;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public TiteeniPlugin getPlugin() {
        return plugin;
    }

    public boolean isParticipant(Player player) {
        return players.contains(player);
    }
}
