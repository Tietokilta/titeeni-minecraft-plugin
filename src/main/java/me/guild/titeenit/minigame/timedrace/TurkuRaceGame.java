package me.guild.titeenit.minigame.timedrace;

import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Timed race variant for Turku: each participant spawns inside a boat.
 * Dismounting is blocked until the player's Z coordinate exceeds {@code gateZ}
 * (or until they naturally board the finish bus, which skips the gate check).
 *
 * Boats are cleaned up when the game stops or a player quits.
 */
public class TurkuRaceGame extends TimedRaceGame implements Listener {

    /** Z threshold that must be reached before dismounting is allowed.
     *  {@link Double#NaN} means no gate is enforced (allow dismount anywhere). */
    private final double gateZ;

    /** Boats currently paired with a player (still racing). */
    private final Map<UUID, Boat> playerBoats = new HashMap<>();

    /** All boats spawned by this game (for clean-up). */
    private final Set<Boat> allBoats = new HashSet<>();

    private BukkitTask gateTask;

    public TurkuRaceGame(TiteeniPlugin plugin, List<Player> players, double gateZ) {
        super(plugin, players);
        this.gateZ = gateZ;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void start() {
        super.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (Player p : getOnlinePlayers()) {
            Boat boat = p.getWorld().spawn(p.getLocation(), Boat.class);
            boat.addPassenger(p);
            playerBoats.put(p.getUniqueId(), boat);
            allBoats.add(boat);
        }

        if (!Double.isNaN(gateZ)) {
            broadcast(Component.text(
                    "You cannot leave your boat until you reach the bus!",
                    NamedTextColor.YELLOW));

            // Every tick: eject and remove the boat for players that cross the gate
            gateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                List<UUID> passed = new ArrayList<>();
                for (Map.Entry<UUID, Boat> entry : playerBoats.entrySet()) {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    if (p != null && p.getLocation().getZ() >= gateZ) {
                        passed.add(entry.getKey());
                    }
                }
                for (UUID uid : passed) {
                    Boat boat = playerBoats.remove(uid);
                    if (boat != null && boat.isValid()) {
                        boat.eject();
                        boat.remove();
                    }
                }
            }, 5L, 5L);
        }
    }

    @Override
    public void stop() {
        if (gateTask != null) { gateTask.cancel(); gateTask = null; }
        HandlerList.unregisterAll(this);
        for (Boat boat : allBoats) {
            if (boat.isValid()) boat.remove();
        }
        allBoats.clear();
        playerBoats.clear();
        super.stop();
    }

    // -------------------------------------------------------------------------
    // Player events
    // -------------------------------------------------------------------------

    /** Called when a player reaches the finish bus. Remove from tracking so that
     *  the teleport doesn't get blocked by our VehicleExitEvent handler. */
    @Override
    public void onPlayerBoarded(Player player) {
        playerBoats.remove(player.getUniqueId());
        super.onPlayerBoarded(player);
    }

    @Override
    public void onPlayerQuit(Player player) {
        Boat boat = playerBoats.remove(player.getUniqueId());
        if (boat != null && boat.isValid()) boat.remove();
        super.onPlayerQuit(player);
    }

    // -------------------------------------------------------------------------
    // Gate enforcement
    // -------------------------------------------------------------------------

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        UUID uid = player.getUniqueId();

        Boat tracked = playerBoats.get(uid);
        if (tracked == null) return; // player already finished or not in this game
        if (!event.getVehicle().equals(tracked)) return; // different vehicle

        // If no gate configured, allow free dismount
        if (Double.isNaN(gateZ)) return;

        if (player.getLocation().getZ() < gateZ) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "You cannot leave the boat yet! Reach the bus first.",
                    NamedTextColor.RED));
        }
    }
}
