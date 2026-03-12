package me.guild.titeenit.minigame.timedrace;

import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Timed race variant for Turku: each participant receives one boat at the start.
 * Dismounting is blocked until the player's Z coordinate crosses {@code gateZ}.
 * Players may not enter a boat owned by another participant.
 */
public class TurkuRaceGame extends TimedRaceGame implements Listener {

    private final double gateZ;

    /** Players who have not yet crossed the gate (exit blocked for these). */
    private final Set<UUID> beforeGate = new HashSet<>();

    /** boat entity UUID → owner player UUID, populated on first entry. */
    private final Map<UUID, UUID> boatOwners = new HashMap<>();

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
        saveAndClearInventories();
        super.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        for (Player p : getOnlinePlayers()) {
            beforeGate.add(p.getUniqueId());
            p.getInventory().addItem(new ItemStack(Material.OAK_BOAT));
        }

        if (!Double.isNaN(gateZ) && !players.isEmpty()) {
            broadcast(Component.text(
                    "You cannot leave your boat until you reach the finish!",
                    NamedTextColor.YELLOW));

            gateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                for (UUID uid : new ArrayList<>(beforeGate)) {
                    Player p = plugin.getServer().getPlayer(uid);
                    if (p == null) continue;
                    if (p.getLocation().getZ() >= gateZ) {
                        beforeGate.remove(uid);
                    }
                }
            }, 5L, 5L);
        }
    }

    @Override
    public void stop() {
        if (gateTask != null) { gateTask.cancel(); gateTask = null; }
        HandlerList.unregisterAll(this);
        beforeGate.clear();
        boatOwners.clear();
        restoreInventories();
        super.stop();
    }

    // -------------------------------------------------------------------------
    // Player events
    // -------------------------------------------------------------------------

    @Override
    public void onPlayerBoarded(Player player) {
        beforeGate.remove(player.getUniqueId());
        restoreInventory(player);
        super.onPlayerBoarded(player);
    }

    @Override
    public void onPlayerQuit(Player player) {
        beforeGate.remove(player.getUniqueId());
        super.onPlayerQuit(player);
    }

    // -------------------------------------------------------------------------
    // Vehicle events
    // -------------------------------------------------------------------------

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!(event.getEntered() instanceof Player player)) return;
        if (!isParticipant(player)) return;

        UUID boatId   = boat.getUniqueId();
        UUID playerId = player.getUniqueId();

        UUID owner = boatOwners.get(boatId);
        if (owner == null) {
            boatOwners.put(boatId, playerId); // first entry — claim ownership
        } else if (!owner.equals(playerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        if (!beforeGate.contains(player.getUniqueId())) return;
        if (!(event.getVehicle() instanceof Boat)) return;
        if (Double.isNaN(gateZ)) return;

        event.setCancelled(true);
        player.sendMessage(Component.text(
                "You cannot leave the boat yet! Reach the finish first.",
                NamedTextColor.RED));
    }
}
