package me.guild.titeenit.minigame;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final Map<UUID, ItemStack[]> savedStorageContents = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmorContents   = new HashMap<>();
    private final Map<UUID, ItemStack>   savedOffhandItem     = new HashMap<>();

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

    /**
     * Returns true if lower scores are better (e.g., timed races).
     * Used by EventManager when converting raw scores to ranked event points.
     */
    public boolean isLowerScoreBetter() { return false; }

    /**
     * Called by EventManager when a player boards the bus during this game.
     * Override in timing-based games to record the player's finish time.
     */
    public void onPlayerBoarded(Player player) {}

    /**
     * Duration of the central game timer in seconds. MinigameManager announces
     * the time remaining at 60 s, 30 s, and 10 s, then auto-boards remaining
     * players and stops the game when it reaches zero. Override to customise.
     */
    public int getTimerSeconds() { return 300; }

    /**
     * Called by MinigameManager every second. Default implementation shows the
     * remaining time in the action bar. Override to combine with other content
     * (e.g. KOTH appends it to its leaderboard bar) or to suppress it entirely.
     */
    public void onTimerTick(int secondsLeft) {
        broadcastActionBar(Component.text(formatRemaining(secondsLeft), NamedTextColor.YELLOW));
    }

    protected static String formatRemaining(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    // -------------------------------------------------------------------------
    // Inventory save / restore helpers
    // -------------------------------------------------------------------------

    /**
     * Saves every online participant's full inventory (storage + armour + offhand)
     * and then clears it. Call at game start before handing out game items.
     */
    protected void saveAndClearInventories() {
        for (Player p : getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            savedStorageContents.put(uid, p.getInventory().getStorageContents().clone());
            savedArmorContents.put(uid, p.getInventory().getArmorContents().clone());
            savedOffhandItem.put(uid, p.getInventory().getItemInOffHand());
            p.getInventory().clear();
            p.getInventory().setArmorContents(new ItemStack[4]);
            p.getInventory().setItemInOffHand(null);
        }
    }

    /**
     * Restores one online player's inventory from the saved snapshot and removes
     * their entry from the saved maps. Safe to call mid-game (e.g. on bus board).
     */
    protected void restoreInventory(Player p) {
        UUID uid = p.getUniqueId();
        ItemStack[] storage = savedStorageContents.remove(uid);
        ItemStack[] armor   = savedArmorContents.remove(uid);
        ItemStack   offhand = savedOffhandItem.remove(uid);
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        if (storage != null) p.getInventory().setStorageContents(storage);
        if (armor   != null) p.getInventory().setArmorContents(armor);
        p.getInventory().setItemInOffHand(offhand); // null clears offhand
    }

    /**
     * Restores inventories for all online participants and clears the saved maps
     * (including data for any players who went offline mid-game). Call in stop().
     */
    protected void restoreInventories() {
        for (Player p : players) {
            if (p.isOnline()) restoreInventory(p);
        }
        savedStorageContents.clear();
        savedArmorContents.clear();
        savedOffhandItem.clear();
    }

    // -------------------------------------------------------------------------
    // Helpers available to all subclasses
    // -------------------------------------------------------------------------

    protected List<Player> getOnlinePlayers() {
        return players.stream().filter(Player::isOnline).toList();
    }

    public void broadcast(Component message) {
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
        var comparator = isLowerScoreBetter()
                ? Map.Entry.<UUID, Integer>comparingByValue()
                : Map.Entry.<UUID, Integer>comparingByValue().reversed();
        getScores().entrySet().stream()
                .sorted(comparator)
                .forEach(entry -> broadcast(Component.text(
                        displayName(entry.getKey()) + ": " + entry.getValue() + " points",
                        NamedTextColor.YELLOW)));
    }

    /** Resolves the display name for a UUID: guild name → player name → UUID string. */
    protected String displayName(UUID uuid) {
        Optional<Guild> guild = plugin.getGuildRegistry().getGuild(uuid);
        if (guild.isPresent()) return guild.get().getDisplayName();
        Player p = plugin.getServer().getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString();
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
