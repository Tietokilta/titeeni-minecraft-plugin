package me.guild.titeenit.minigame.timedrace;

import me.guild.titeenit.TiteeniPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
 */
public class TampereRaceGame extends TimedRaceGame implements Listener {

    private static final double FLOOR_OFFSET = 5.0;

    private final Map<UUID, ItemStack> savedBoots = new HashMap<>();
    private final Map<UUID, Location> initialSpawns = new HashMap<>();
    /** Players currently mid-reset — suppresses re-entry for 1 second. */
    private final Set<UUID> resetting = new HashSet<>();

    public TampereRaceGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
    }

    @Override
    public void start() {
        super.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : players) {
            savedBoots.put(p.getUniqueId(), p.getInventory().getBoots());
            Location spawn = p.getLocation().clone();
            initialSpawns.put(p.getUniqueId(), spawn);
            p.setRespawnLocation(spawn, true);
            applyEffects(p);
        }
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);
        for (Player p : getOnlinePlayers()) restorePlayer(p);
        savedBoots.clear();
        initialSpawns.clear();
        resetting.clear();
        super.stop();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Skip if Y hasn't changed — avoids work on horizontal-only movement
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        Player p = event.getPlayer();
        UUID uid = p.getUniqueId();
        if (!isParticipant(p) || resetting.contains(uid)) return;

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
        if (!isParticipant(p) || !savedBoots.containsKey(p.getUniqueId())) return;
        // 1-tick delay — respawn clears potion effects after the event fires
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyEffects(p), 1L);
    }

    @Override
    public void onPlayerBoarded(Player player) {
        restorePlayer(player);
        super.onPlayerBoarded(player);
    }

    @Override
    public void onPlayerQuit(Player player) {
        UUID uid = player.getUniqueId();
        savedBoots.remove(uid);
        initialSpawns.remove(uid);
        super.onPlayerQuit(player);
    }

    private void applyEffects(Player player) {
        player.getInventory().setBoots(new ItemStack(Material.LEATHER_BOOTS));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
    }

    private void restorePlayer(Player player) {
        UUID uid = player.getUniqueId();
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        ItemStack boots = savedBoots.remove(uid);
        player.getInventory().setBoots(boots);
    }
}
