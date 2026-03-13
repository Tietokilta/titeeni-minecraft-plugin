package me.guild.titeenit.minigame.timedrace;

import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Timed race variant for Tampere (parkour): players start invisible and
 * receive leather boots.
 *
 * Reset mechanic: when a player's Y drops more than 5 blocks below their
 * current respawn point (set by pressure plates via /spawnpoint), they are
 * instantly teleported back to that checkpoint. Before any checkpoint is
 * reached, the stage spawn serves as the fallback.
 *
 * The floor tracks the current checkpoint dynamically, so it naturally
 * follows players up and down through the course. For intentional large
 * drops (e.g. a dropper funnel), place a pressure plate at the entry that
 * sets the spawn to the landing zone — the floor then drops with them.
 *
 * Scoring for DNF players: higher respawn Y = better rank (DNF scores are
 * still ranked lower than any finisher).
 */
public class TampereRaceGame extends TimedRaceGame implements Listener {

    private static final double FLOOR_OFFSET = 5.0;

    /**
     * Scores ≥ DNF_THRESHOLD belong to unfinished players (encoded as
     * DNF_BASE − y * 1000 so that higher Y yields a lower/better score).
     */
    private static final int DNF_BASE      = 1_000_000_000;
    private static final int DNF_THRESHOLD =   500_000_000;

    private final Map<UUID, Location> initialSpawns = new HashMap<>();
    /** Players currently mid-reset — suppresses re-entry for 1 second. */
    private final Set<UUID> resetting = new HashSet<>();
    /** Players who have boarded the bus — exempt from the reset mechanic. */
    private final Set<UUID> boarded = new HashSet<>();

    public TampereRaceGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
    }

    @Override public int getTimerSeconds() { return 360; }

    @Override
    public void start() {
        super.start();
        saveAndClearInventories();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : players) {
            Location spawn = p.getLocation().clone();
            initialSpawns.put(p.getUniqueId(), spawn);
            p.setRespawnLocation(spawn, true);
            applyEffects(p);
        }
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);
        for (Player p : getOnlinePlayers()) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        restoreInventories();
        resetting.clear();
        boarded.clear();
        super.stop();           // calls broadcastScores() → getScores() which needs initialSpawns
        initialSpawns.clear();  // safe to clear only after super.stop()
    }

    // -------------------------------------------------------------------------
    // Scoring
    // -------------------------------------------------------------------------

    /**
     * Finishers: their elapsed milliseconds (lower = better).
     * Unfinished online players: DNF_BASE − y*1000 (still > any finish time).
     * Unfinished offline players: Integer.MAX_VALUE (worst rank).
     */
    @Override
    public Map<UUID, Integer> getScores() {
        Map<UUID, Integer> scores = new HashMap<>(super.getScores());
        for (Player p : players) {
            UUID uid = p.getUniqueId();
            if (scores.get(uid) < DNF_THRESHOLD) continue; // already finished
            if (!p.isOnline()) continue;                    // stays at MAX_VALUE
            Location checkpoint = p.getRespawnLocation();
            if (checkpoint == null) checkpoint = initialSpawns.get(uid);
            int y = checkpoint != null ? (int) checkpoint.getY() : 0;
            scores.put(uid, DNF_BASE - y * 1000);
        }
        return java.util.Collections.unmodifiableMap(scores);
    }

    @Override
    protected void broadcastScores() {
        showTopPerformersTitle();
        broadcastAll(Component.text("=== " + getDisplayName() + " — Final Scores ===", NamedTextColor.GOLD));
        getScores().entrySet().stream()
                .sorted(Map.Entry.comparingByValue())  // lower = better
                .forEach(entry -> {
                    int score = entry.getValue();
                    String result;
                    if (score < DNF_THRESHOLD) {
                        result = formatTime(score);
                    } else if (score == Integer.MAX_VALUE) {
                        result = "DNF";
                    } else {
                        int y = (DNF_BASE - score) / 1000;
                        result = "DNF (Y=" + y + ")";
                    }
                    broadcastAll(Component.text(
                            displayName(entry.getKey()) + ": " + result, NamedTextColor.YELLOW));
                });
    }

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Skip if Y hasn't changed — avoids work on horizontal-only movement
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player p = event.getPlayer();
        UUID uid = p.getUniqueId();
        if (!isParticipant(p) || resetting.contains(uid) || boarded.contains(uid)) return;

        // Diamond boots = past the last checkpoint; no reset, fall damage applies normally
        ItemStack boots = p.getInventory().getBoots();
        if (boots != null && boots.getType() == Material.DIAMOND_BOOTS) return;

        // Floor is always 5 below the current checkpoint (or initial spawn if none reached)
        Location checkpoint = p.getRespawnLocation();
        if (checkpoint == null) checkpoint = initialSpawns.get(uid);
        if (checkpoint == null) return;

        if (event.getTo().getY() >= checkpoint.getY() - FLOOR_OFFSET) return;

        resetting.add(uid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> resetting.remove(uid), 20L);
        p.teleport(checkpoint);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (!isParticipant(p)) return;
        // 1-tick delay — respawn clears potion effects after the event fires
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyEffects(p), 1L);
    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!isParticipant(victim)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isParticipant(p)) return;
        // Allow fall damage for diamond boots players (dropper funnel section)
        ItemStack boots = p.getInventory().getBoots();
        if (boots != null && boots.getType() == Material.DIAMOND_BOOTS) return;
        event.setCancelled(true);
    }

    @Override
    public void onPlayerBoarded(Player player) {
        boarded.add(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        restoreInventory(player);
        super.onPlayerBoarded(player);
    }

    @Override
    public void onPlayerQuit(Player player) {
        initialSpawns.remove(player.getUniqueId());
        super.onPlayerQuit(player);
    }

    private void applyEffects(Player player) {
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
    }
}
